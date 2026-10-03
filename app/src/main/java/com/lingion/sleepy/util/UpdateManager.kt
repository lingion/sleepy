package com.lingion.sleepy.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
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
    private const val TAG = "UpdateManager"
    private const val GITHUB_API = "https://api.github.com/repos/lingion/sleepy/releases/latest"
    private const val MIRROR_RELEASE = "https://gh.qdp.qzz.io/lingion/sleepy/releases/latest"
    private const val MIRROR_PREFIX = "https://gh.qdp.qzz.io/lingion/sleepy/releases/download/"

    private fun currentAbiAsset(): String = when {
        Build.SUPPORTED_ABIS.any { it == "arm64-v8a" } -> "arm64-v8a"
        Build.SUPPORTED_ABIS.any { it == "armeabi-v7a" } -> "armeabi-v7a"
        Build.SUPPORTED_ABIS.any { it == "x86_64" } -> "x86_64"
        else -> "arm64-v8a"
    }

    private fun currentAbi(): String = currentAbiAsset()

    /**
     * Accept both legacy (app-<abi>-release.apk) and new (Sleepy-v<ver>-<abi>.apk) asset names.
     * Hand-uploaded v1.0.58 assets used the new naming while shipped clients still expected the
     * old, leaving downloadUrl empty and crashing URL("") with MalformedURLException("no protocol: ").
     * The legacy suffix is kept as the first fallback so the rename fix on the server side
     * (POST PATCH assets to old names) is always honored when present.
     */
    private fun candidatesFor(abi: String): List<String> = listOf(
        "app-$abi-release.apk",
        "app-$abi.apk",
        "Sleepy-v${BuildConfig.VERSION_NAME}-$abi.apk",
    )

    /** 只拉 release 信息,不下载。GitHub 不通回退镜像。 */
    suspend fun fetchUpdateInfo(context: Context): UpdateInfo = withContext(Dispatchers.IO) {
        val abi = currentAbi()
        val candidates = candidatesFor(abi)
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
        // 镜像 HTML 不暴露 asset 列表 → 沿用候选名按顺序拼,找到能 200 的那条;全失败抛空串触发 no-protocol 错误而非 silently 404
        val url = candidates.firstNotNullOfOrNull { candidate ->
            val u = "$MIRROR_PREFIX$tag/$candidate"
            if (headOk(u)) u else null
        } ?: ""
        val isUpdate = VersionUtils.compare(version, BuildConfig.VERSION_NAME) > 0
        UpdateInfo(version, parseMirrorPage(page, tag), url, isUpdate)
    }

    private fun headOk(url: String): Boolean = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
        }
        val ok = conn.responseCode in 200..299
        conn.disconnect()
        ok
    } catch (_: Exception) { false }

    /** 下载 APK 到 cacheDir,带进度回调(0-100)。协程 cancel 时删半截文件。 */
    suspend fun downloadApk(
        context: Context, info: UpdateInfo, onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, "sleepy-update-${currentAbiAsset()}")
        try {
            downloadOnce(info.downloadUrl, target, onProgress)
        } catch (primary: Exception) {
            if (primary is kotlinx.coroutines.CancellationException) throw primary
            // 镜像下载失败 → GitHub 直连回退 (信息源/下载源各回退一次, 用户 2026-09-05 令)
            val direct = toDirectGithubUrl(info.downloadUrl)
            if (direct == info.downloadUrl) throw primary
            Log.w(TAG, "mirror download failed, falling back to github direct", primary)
            target.delete()
            downloadOnce(direct, target, onProgress)
        }
        if (!target.isFile || target.length() == 0L)
            throw IllegalStateException(context.getString(com.lingion.sleepy.R.string.error_empty_download))
        target
    }

    private suspend fun downloadOnce(
        url: String, target: File, onProgress: (Int) -> Unit
    ) {
        val conn = request(url)
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
