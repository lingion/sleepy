package com.lingion.sleepy.util

/**
 * 远端 release 信息(纯数据,不含 Android 依赖,可单测)。
 *
 * [isUpdateAvailable] 由 [parseReleaseJson] 根据版本比较 + force flag 算出。
 * 调用方(UpdateManager)据它决定弹窗 vs Toast。
 */
data class UpdateInfo(
    val version: String,
    val changelog: String,
    val downloadUrl: String,
    val isUpdateAvailable: Boolean
)

private const val FORCE_FLAG = "SLEEPY_FORCE_UPDATE=true"

/**
 * 解析 GitHub releases/latest 的 JSON 为 [UpdateInfo](纯函数,无 IO)。
 *
 * [abi] 形如 "arm64-v8a" / "armeabi-v7a" / "x86_64",用于挑对应 asset。
 * 找不到对应 asset 时 downloadUrl 返回空串(调用方走镜像回退)。
 */
fun parseReleaseJson(json: String, currentVersion: String, abi: String): UpdateInfo {
    val release = org.json.JSONObject(json)
    val version = release.optString("tag_name").removePrefix("v")
    val body = release.optString("body")
    val assetName = "app-$abi-release.apk"
    val downloadUrl = release.optJSONArray("assets")?.let { assets ->
        (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name") == assetName }
            ?.optString("browser_download_url")
    } ?: ""
    val force = body.contains(FORCE_FLAG)
    val isUpdateAvailable = force ||
        VersionUtils.compare(version.ifBlank { "0" }, currentVersion) > 0
    return UpdateInfo(version, body, downloadUrl, isUpdateAvailable)
}
