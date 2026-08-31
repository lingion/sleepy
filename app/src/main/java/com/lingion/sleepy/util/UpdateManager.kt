package com.lingion.sleepy.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.lingion.sleepy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** 拉 GitHub/镜像 release 信息、下载 APK、清理旧 APK。不含 UI 状态。 */
object UpdateManager {
    private const val GITHUB_API = "https://api.github.com/repos/lingion/sleepy/releases/latest"
    private const val MIRROR_RELEASE = "https://gh.qdp.qzz.io/lingion/sleepy/releases/latest"
    private const val MIRROR_PREFIX = "https://gh.qdp.qzz.io/lingion/sleepy/releases/download/"

    private fun currentAbiAsset(): String = when {
        Build.SUPPORTED_ABIS.any { it == "arm64-v8a" } -> "app-arm64-v8a-release.apk"
        Build.SUPPORTED_ABIS.any { it == "armeabi-v7a" } -> "app-armeabi-v7a-release.apk"
        Build.SUPPORTED_ABIS.any { it == "x86_64" } -> "app-x86_64-release.apk"
        else -> "app-arm64-v8a-release.apk"
    }

    private fun currentAbi(): String = currentAbiAsset()
        .removePrefix("app-").removeSuffix("-release.apk")

    /** 只拉 release 信息,不下载。GitHub 不通回退镜像。 */
    suspend fun fetchUpdateInfo(context: Context): UpdateInfo = withContext(Dispatchers.IO) {
        val abi = currentAbi()
        val abiAsset = currentAbiAsset()
        runCatching {
            val json = readText(GITHUB_API)
            return@withContext parseReleaseJson(json, BuildConfig.VERSION_NAME, abi)
        }
        // 镜像回退:正则取 tag,changelog 从页面 markdown-body 块提取
        val page = readText(MIRROR_RELEASE)
        val tag = Regex("/lingion/sleepy/releases/tag/(v[0-9A-Za-z.+_-]+)").find(page)
            ?.groupValues?.get(1)
            ?: throw IllegalStateException(context.getString(com.lingion.sleepy.R.string.error_no_version_found))
        val version = tag.removePrefix("v")
        val url = "$MIRROR_PREFIX$tag/$abiAsset"
        val isUpdate = VersionUtils.compare(version, BuildConfig.VERSION_NAME) > 0
        UpdateInfo(version, parseMirrorPage(page, tag), url, isUpdate)
    }

    /** 下载 APK 到 cacheDir,带进度回调(0-100)。协程 cancel 时删半截文件。 */
    suspend fun downloadApk(
        context: Context, info: UpdateInfo, onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, "sleepy-update-${currentAbiAsset()}")
        val conn = request(info.downloadUrl)
        val total = conn.contentLengthLong.coerceAtLeast(1L)
        try {
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(8 * 1024)
                    var downloaded = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        downloaded += n
                        onProgress((downloaded * 100 / total).toInt().coerceIn(0, 100))
                    }
                }
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            conn.disconnect()
        }
        if (!target.isFile || target.length() == 0L)
            throw IllegalStateException(context.getString(com.lingion.sleepy.R.string.error_empty_download))
        target
    }

    /** 启动时清理 cacheDir 中旧安装包。 */
    fun cleanOldApk(context: Context) {
        context.cacheDir.listFiles { it.name.startsWith("sleepy-update-") }
            ?.forEach { it.delete() }
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun readText(url: String): String {
        val conn = request(url)
        return try { conn.inputStream.bufferedReader().use { it.readText() } }
        finally { conn.disconnect() }
    }

    private fun request(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Sleepy/${BuildConfig.VERSION_NAME}")
        conn.setRequestProperty("Accept", "application/json,text/html,*/*")
        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            throw IllegalStateException("HTTP ${conn.responseCode}")
        }
        return conn
    }
}
