package com.lingion.sleepy.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.lingion.sleepy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads the newest release from GitHub, falling back to gh.qdp.qzz.io. */
object UpdateManager {
    private const val GITHUB_API = "https://api.github.com/repos/lingion/sleepy/releases/latest"
    private const val MIRROR_RELEASE = "https://gh.qdp.qzz.io/lingion/sleepy/releases/latest"
    private const val MIRROR_PREFIX = "https://gh.qdp.qzz.io/lingion/sleepy/releases/download/"

    data class UpdateResult(val version: String, val file: File)

    suspend fun downloadLatest(context: Context): UpdateResult = withContext(Dispatchers.IO) {
        val assetName = when {
            Build.SUPPORTED_ABIS.any { it == "arm64-v8a" } -> "app-arm64-v8a-release.apk"
            Build.SUPPORTED_ABIS.any { it == "armeabi-v7a" } -> "app-armeabi-v7a-release.apk"
            Build.SUPPORTED_ABIS.any { it == "x86_64" } -> "app-x86_64-release.apk"
            else -> throw IllegalStateException("不支持的手机架构")
        }

        var version: String? = null
        var downloadUrl: String? = null
        runCatching {
            val release = JSONObject(readText(GITHUB_API))
            version = release.optString("tag_name").removePrefix("v")
            downloadUrl = release.optJSONArray("assets")?.let { assets ->
                (0 until assets.length()).map { assets.getJSONObject(it) }
                    .firstOrNull { it.optString("name") == assetName }
                    ?.optString("browser_download_url")
            }
        }

        // The mirror serves the release page at /owner/repo/releases/latest.
        // Its asset URLs use the same tag/name layout under gh.qdp.qzz.io.
        if (downloadUrl.isNullOrBlank()) {
            val page = readText(MIRROR_RELEASE)
            val tag = Regex("/lingion/sleepy/releases/tag/(v[0-9A-Za-z.+_-]+)")
                .find(page)?.groupValues?.get(1)
                ?: Regex("/releases/tag/(v[0-9A-Za-z.+_-]+)").find(page)?.groupValues?.get(1)
                ?: throw IllegalStateException("主站和镜像都没有找到最新版本")
            version = tag.removePrefix("v")
            downloadUrl = "$MIRROR_PREFIX$tag/$assetName"
        }

        val remoteVersion = version ?: throw IllegalStateException("远端版本号为空")
        if (VersionUtils.compare(remoteVersion, BuildConfig.VERSION_NAME) <= 0) {
            throw NoUpdateAvailableException(remoteVersion)
        }

        val target = File(context.cacheDir, "sleepy-update-$assetName")
        download(downloadUrl!!, target)
        if (!target.isFile || target.length() == 0L) throw IllegalStateException("下载文件为空")
        UpdateResult(remoteVersion, target)
    }

    class NoUpdateAvailableException(val version: String) : Exception("当前已是最新版本 v$version")

    fun install(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun readText(url: String): String {
        val connection = request(url)
        return try { connection.inputStream.bufferedReader().use { it.readText() } }
        finally { connection.disconnect() }
    }

    private fun download(url: String, target: File) {
        val connection = request(url)
        try {
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun request(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Sleepy/${BuildConfig.VERSION_NAME}")
        connection.setRequestProperty("Accept", "application/json,text/html,*/*")
        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            throw IllegalStateException("HTTP ${connection.responseCode}")
        }
        return connection
    }
}
