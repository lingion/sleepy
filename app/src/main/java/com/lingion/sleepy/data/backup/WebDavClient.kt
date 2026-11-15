package com.lingion.sleepy.data.backup

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

data class WebDavConfig(
    val serverUrl: String,
    val directory: String,
    val username: String,
)

data class WebDavRemoteBackup(
    val fileName: String,
    val sizeBytes: Long,
    val lastModified: String,
)

enum class WebDavFailureKind {
    INVALID_CONFIGURATION,
    HTTPS_REQUIRED,
    AUTHENTICATION,
    FORBIDDEN,
    HTTP_STATUS,
    FILE_TOO_LARGE,
    INVALID_RESPONSE,
    NETWORK,
}

class WebDavFailure(
    val kind: WebDavFailureKind,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : IOException("WebDAV request failed: $kind${statusCode?.let { " ($it)" } ?: ""}", cause)

/** User-triggered WebDAV transport for complete .sleepybackup archives. */
class WebDavClient(
    private val httpClient: OkHttpClient = defaultHttpClient,
) {
    fun testConnection(config: WebDavConfig, password: String) {
        val target = target(config, password)
        val initial = execute(request(target.directoryUrl, "PROPFIND", propfindBody, depth = "0", target = target))
        if (initial.code == 207) {
            val entries = initial.use { parseMultiStatus(it.bodyText(MAX_XML_BYTES), target.directoryUrl) }
            if (entries.none { it.collection && samePath(it.url, target.directoryUrl) }) {
                throw WebDavFailure(WebDavFailureKind.INVALID_RESPONSE)
            }
            return
        }
        if (initial.code != 404) {
            initial.use { requireStatus(it, setOf(207)) }
        }
        initial.close()

        for (folderUrl in target.directoryPrefixes) {
            val response = execute(request(folderUrl, "MKCOL", body = null, target = target))
            response.use { requireStatus(it, setOf(201, 405)) }
        }

        val verify = execute(request(target.directoryUrl, "PROPFIND", propfindBody, depth = "0", target = target))
        verify.use {
            requireStatus(it, setOf(207))
            val entries = parseMultiStatus(it.bodyText(MAX_XML_BYTES), target.directoryUrl)
            if (entries.none { entry -> entry.collection && samePath(entry.url, target.directoryUrl) }) {
                throw WebDavFailure(WebDavFailureKind.INVALID_RESPONSE)
            }
        }
    }

    fun upload(config: WebDavConfig, password: String, fileName: String, bytes: ByteArray) {
        validateFileName(fileName)
        if (bytes.size > MAX_BACKUP_BYTES) throw WebDavFailure(WebDavFailureKind.FILE_TOO_LARGE)
        val target = target(config, password)
        val fileUrl = target.directoryUrl.newBuilder().addPathSegment(fileName).build()
        val body = bytes.toRequestBody(OCTET_STREAM)
        val response = execute(request(fileUrl, "PUT", body, target = target))
        response.use { requireStatus(it, setOf(200, 201, 204)) }
    }

    fun listBackups(config: WebDavConfig, password: String): List<WebDavRemoteBackup> {
        val target = target(config, password)
        val response = execute(request(target.directoryUrl, "PROPFIND", propfindBody, depth = "1", target = target))
        response.use {
            requireStatus(it, setOf(207))
            return parseMultiStatus(it.bodyText(MAX_XML_BYTES), target.directoryUrl)
                .asSequence()
                .filter { entry ->
                    !entry.collection && isChildOf(entry.url, target.directoryUrl)
                }
                .mapNotNull { entry ->
                    val name = entry.url.pathSegments.lastOrNull().orEmpty()
                    if (!isSafeBackupName(name)) return@mapNotNull null
                    WebDavRemoteBackup(name, entry.sizeBytes, entry.lastModified)
                }
                .sortedByDescending { parseTimestamp(it.lastModified) }
                .toList()
        }
    }

    fun download(config: WebDavConfig, password: String, fileName: String): ByteArray {
        validateFileName(fileName)
        val target = target(config, password)
        val fileUrl = target.directoryUrl.newBuilder().addPathSegment(fileName).build()
        val response = execute(request(fileUrl, "GET", body = null, target = target))
        response.use {
            requireStatus(it, setOf(200))
            val body = it.body
            if (body.contentLength() > MAX_BACKUP_BYTES) {
                throw WebDavFailure(WebDavFailureKind.FILE_TOO_LARGE)
            }
            return body.readLimited(MAX_BACKUP_BYTES)
        }
    }

    private fun request(
        url: HttpUrl,
        method: String,
        body: okhttp3.RequestBody?,
        depth: String? = null,
        target: Target,
    ): Request {
        val builder = Request.Builder().url(url)
            .header("Accept", if (method == "PROPFIND") "application/xml" else "*/*")
        if (target.config.username.isNotEmpty()) {
            builder.header("Authorization", Credentials.basic(target.config.username, target.password, Charsets.UTF_8))
        }
        if (depth != null) builder.header("Depth", depth)
        if (method == "PROPFIND") builder.header("Content-Type", "application/xml; charset=utf-8")
        if (method == "PUT") builder.header("Content-Type", "application/octet-stream")
        builder.method(method, body)
        return builder.build()
    }

    private fun execute(request: Request): Response = try {
        httpClient.newCall(request).execute()
    } catch (error: IOException) {
        if (error is WebDavFailure) throw error
        throw WebDavFailure(WebDavFailureKind.NETWORK, cause = error)
    }

    private fun requireStatus(response: Response, accepted: Set<Int>) {
        if (response.code in accepted) return
        val kind = when (response.code) {
            401 -> WebDavFailureKind.AUTHENTICATION
            403 -> WebDavFailureKind.FORBIDDEN
            413 -> WebDavFailureKind.FILE_TOO_LARGE
            else -> WebDavFailureKind.HTTP_STATUS
        }
        throw WebDavFailure(kind, statusCode = response.code)
    }

    private data class Target(
        val config: WebDavConfig,
        val password: String,
        val baseUrl: HttpUrl,
        val directoryUrl: HttpUrl,
        val directoryPrefixes: List<HttpUrl>,
    )

    private fun target(config: WebDavConfig, password: String): Target {
        val parsed = config.serverUrl.trim().toHttpUrlOrNull()
            ?: throw WebDavFailure(WebDavFailureKind.INVALID_CONFIGURATION)
        if (!parsed.isHttps) throw WebDavFailure(WebDavFailureKind.HTTPS_REQUIRED)
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty() || parsed.query != null || parsed.fragment != null) {
            throw WebDavFailure(WebDavFailureKind.INVALID_CONFIGURATION)
        }
        val username = config.username.trim()
        if (username.contains(':') || username.any(Char::isISOControl) || (username.isEmpty() && password.isNotEmpty())) {
            throw WebDavFailure(WebDavFailureKind.INVALID_CONFIGURATION)
        }
        val directory = config.directory.trim().trim('/')
        val segments = directory.split('/')
        if (directory.isEmpty() || segments.any { segment ->
                segment.isEmpty() || segment == "." || segment == ".." || segment.any(Char::isISOControl)
            }
        ) {
            throw WebDavFailure(WebDavFailureKind.INVALID_CONFIGURATION)
        }

        val normalizedPath = parsed.encodedPath.trimEnd('/').ifEmpty { "/" }
        val base = parsed.newBuilder().encodedPath(normalizedPath).build()
        val prefixes = ArrayList<HttpUrl>(segments.size)
        var prefix = base
        for (segment in segments) {
            prefix = prefix.newBuilder().addPathSegment(segment).build()
            prefixes += prefix
        }
        return Target(config.copy(username = username), password, base, prefix, prefixes)
    }

    private data class DavEntry(
        val url: HttpUrl,
        val collection: Boolean,
        val sizeBytes: Long,
        val lastModified: String,
    )

    private fun parseMultiStatus(xml: String, directoryUrl: HttpUrl): List<DavEntry> {
        val document = try {
            Jsoup.parse(xml, "", Parser.xmlParser())
        } catch (error: Exception) {
            throw WebDavFailure(WebDavFailureKind.INVALID_RESPONSE, cause = error)
        }
        val responses = document.getAllElements().filter { it.localName() == "response" }
        return responses.mapNotNull { response ->
            val href = response.children().firstOrNull { it.localName() == "href" }?.text()?.trim()
                ?: return@mapNotNull null
            val url = directoryUrl.resolve(href) ?: return@mapNotNull null
            if (url.scheme != directoryUrl.scheme || url.host != directoryUrl.host || url.port != directoryUrl.port) {
                return@mapNotNull null
            }
            val prop = response.getAllElements().firstOrNull { it.localName() == "prop" } ?: response
            val resourceType = prop.getAllElements().firstOrNull { it.localName() == "resourcetype" }
            val collection = resourceType?.getAllElements()?.any { it.localName() == "collection" } == true
            val size = prop.getAllElements().firstOrNull { it.localName() == "getcontentlength" }
                ?.text()?.trim()?.toLongOrNull() ?: 0L
            val modified = prop.getAllElements().firstOrNull { it.localName() == "getlastmodified" }
                ?.text()?.trim().orEmpty()
            DavEntry(url, collection, size, modified)
        }
    }

    private fun Element.localName(): String = tagName().substringAfterLast(':').lowercase(Locale.ROOT)

    private fun samePath(first: HttpUrl, second: HttpUrl): Boolean =
        first.encodedPath.trimEnd('/') == second.encodedPath.trimEnd('/')

    private fun isChildOf(url: HttpUrl, directoryUrl: HttpUrl): Boolean {
        val prefix = directoryUrl.encodedPath.trimEnd('/') + "/"
        return url.encodedPath.startsWith(prefix)
    }

    private fun validateFileName(fileName: String) {
        if (!isSafeBackupName(fileName)) throw WebDavFailure(WebDavFailureKind.INVALID_CONFIGURATION)
    }

    private fun isSafeBackupName(fileName: String): Boolean =
        fileName.endsWith(".sleepybackup", ignoreCase = true) &&
            fileName.matches(Regex("[A-Za-z0-9._-]{1,180}\\.sleepybackup", RegexOption.IGNORE_CASE)) &&
            fileName != "." && fileName != ".."

    private fun parseTimestamp(value: String): Long = try {
        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    } catch (_: Exception) {
        0L
    }

    private fun Response.bodyText(maxBytes: Int): String {
        val responseBody = body
        if (responseBody.contentLength() > maxBytes) throw WebDavFailure(WebDavFailureKind.INVALID_RESPONSE)
        return responseBody.readLimited(maxBytes).toString(Charsets.UTF_8)
    }

    private fun ResponseBody.readLimited(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        byteStream().use { input ->
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) throw WebDavFailure(WebDavFailureKind.FILE_TOO_LARGE)
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    companion object {
        const val MAX_BACKUP_BYTES = 100 * 1024 * 1024
        private const val MAX_XML_BYTES = 2 * 1024 * 1024
        private val OCTET_STREAM = "application/octet-stream".toMediaType()
        private val propfindBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getlastmodified/><d:getcontentlength/></d:prop></d:propfind>
        """.trimIndent().toRequestBody("application/xml; charset=utf-8".toMediaType())
        private val defaultHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}
