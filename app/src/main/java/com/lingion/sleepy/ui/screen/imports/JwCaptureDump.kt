package com.lingion.sleepy.ui.screen.imports

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import com.lingion.sleepy.R
import com.lingion.sleepy.data.jw.JwSchoolInfo
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 排查全量包 dump — 把诊断会话的全量证据打成 zip,落到 Downloads/Sleepy/教务日志/。
 *
 * 设计动机: 学生手头只有"导入失败"反馈, 把诊断取证反推到我们这边。
 * 点「导出排查全量包」→ zip 存盘 → 自动拉系统分享面板 → 学生微信发回。
 *
 * 隐私: 1B 完全不脱敏 — Cookie/学号/表单值/HTML 原文全保留。学生知道他在发什么。
 *
 * 路径分流 (方案 A):
 *   API 29+ → MediaStore.Downloads RELATIVE_PATH = "Download/Sleepy/教务日志/"
 *   API 26-28 → getExternalFilesDir(DOWNLOADS) 应用专属目录,FileProvider 共享
 *
 * 内容:
 *   summary.txt     — 状态/锚点/帧路径/重试/hint/学校/版本
 *   frames/xxx.html — 每个可达 frame 的 outerHTML 原文 (FrameCaptureResult 已含)
 *   dom-inventory.txt — DOM_INVENTORY_JS 现场抓的可点元素清单
 *   netlog.txt      — JwDiagnosticSession 全程网络请求日志
 *   console.txt     — JwDiagnosticSession console 日志
 */
object JwCaptureDump {

    private const val TAG = "JwCaptureDump"
    private const val RELATIVE_DIR = "Sleepy/教务日志"
    private const val ZIP_BASE = "sleepy-jw-dump"
    private val MIME = "application/zip"

    /**
     * 入口 — 由 JwErrorDialog "导出排查全量包" 按钮回调。
     * IO 阻塞, 必须在工作线程调用。
     *
     * 2026-09-18 用户: 排查包信息量对齐/超过桌面 collector (Windows/Mac/Linux) —
     * cookies-full 全量值、storage 全键值、links 导航全集、env 设备环境、INDEX 清单
     * 都是桌面版默认带、Android 端此前缺的。补齐方法签名加重载, 旧调用方零改动。
     */
    fun exportDump(
        ctx: Context,
        school: JwSchoolInfo,
        result: FrameCaptureResult,
        domInventoryJson: String?,
    ): DumpResult = exportDump(ctx, school, result, domInventoryJson, null, null, null)

    fun exportDump(
        ctx: Context,
        school: JwSchoolInfo,
        result: FrameCaptureResult,
        domInventoryJson: String?,
        cookiesFull: String?,
        storageJson: String?,
        linksJson: String?,
    ): DumpResult = exportDump(
        ctx, school, result, domInventoryJson, cookiesFull, storageJson, linksJson, null
    )

    fun exportDump(
        ctx: Context,
        school: JwSchoolInfo,
        result: FrameCaptureResult,
        domInventoryJson: String?,
        cookiesFull: String?,
        storageJson: String?,
        linksJson: String?,
        resourceReplayJson: String?,
        networkLiveJson: String? = null,
    ): DumpResult {
        val stamp = JwDiagnosticSession.currentSessionId()
        val zipName = "$ZIP_BASE-$stamp.zip"
        return try {
            val zipBytes = buildZip(
                ctx, school, result, domInventoryJson,
                cookiesFull, storageJson, linksJson, resourceReplayJson, networkLiveJson
            )
            val uri = writeZip(ctx, zipName, zipBytes)
            if (uri != null) {
                DumpResult.Ok(zipName, uri)
            } else {
                DumpResult.Fail("存储失败")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "exportDump failed", e)
            DumpResult.Fail(e.message ?: e.javaClass.simpleName)
        }
    }

    /** 纯函数 — 把所有证据打成 zip 字节。不触碰 Android API,测试直接传 null ctx。 */
    fun buildZip(
        ctx: Context?,
        school: JwSchoolInfo,
        result: FrameCaptureResult,
        domInventoryJson: String?,
    ): ByteArray = buildZip(ctx, school, result, domInventoryJson, null, null, null)

    fun buildZip(
        ctx: Context?,
        school: JwSchoolInfo,
        result: FrameCaptureResult,
        domInventoryJson: String?,
        cookiesFull: String?,
        storageJson: String?,
        linksJson: String?,
    ): ByteArray = buildZip(
        ctx, school, result, domInventoryJson, cookiesFull, storageJson, linksJson, null
    )

    fun buildZip(
        ctx: Context?,
        school: JwSchoolInfo,
        result: FrameCaptureResult,
        domInventoryJson: String?,
        cookiesFull: String?,
        storageJson: String?,
        linksJson: String?,
        resourceReplayJson: String?,
        networkLiveJson: String? = null,
    ): ByteArray {
        val manifest = mutableListOf<Pair<String, String>>()  // (path, description)
        val out = java.io.ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            writeSummary(zos, manifest, school, result)
            writeFrames(zos, manifest, result)
            writeInventory(zos, manifest, domInventoryJson)
            writeText(zos, manifest, "netlog.txt", JwDiagnosticSession.exportNetlog(), "全程网络请求(方法/状态/MIME/请求头/响应头/重定向)")
            writeText(zos, manifest, "console.txt", JwDiagnosticSession.exportConsole(), "WebView console 输出")
            writeText(zos, manifest, "cookies-full.txt", cookiesFull ?: "(no cookies captured)", "Cookie 全量值(1B 不脱敏, 排查登录态)")
            writeText(zos, manifest, "5-storage/storage.json", storageJson ?: "{\"sessionStorage\":{},\"localStorage\":{}}", "Web Storage 全量键值(sessionStorage + localStorage)")
            writeText(zos, manifest, "2-inline/links.json", linksJson ?: "{\"links\":[],\"selects\":[]}", "页面链接全集 + select 下拉枚举(学期码)")
            writeResourceReplay(zos, manifest, resourceReplayJson)
            writeNetworkLive(zos, manifest, networkLiveJson ?: JwDiagnosticSession.exportJsNetwork())
            writeEnvironment(zos, manifest, ctx)
            // 桌面 collector 6-logs/collect-log.txt 对标 — 逐文件采集记录 (时间序)。
            writeText(
                zos, manifest, "6-logs/collect-log.txt",
                buildCollectLog(manifest),
                "采集过程逐文件日志(时间序, 对标桌面 collector)"
            )
            writeIndex(zos, manifest)
        }
        return out.toByteArray()
    }

    private fun writeSummary(
        zos: ZipOutputStream,
        manifest: MutableList<Pair<String, String>>,
        school: JwSchoolInfo,
        r: FrameCaptureResult
    ) {
        val sb = StringBuilder()
        sb.appendLine("# Sleepy JW Diagnostic Summary")
        sb.appendLine("session=${JwDiagnosticSession.currentSessionId()}")
        sb.appendLine("appVersion=${com.lingion.sleepy.BuildConfig.VERSION_NAME} (${com.lingion.sleepy.BuildConfig.VERSION_CODE})")
        sb.appendLine("debug=${com.lingion.sleepy.BuildConfig.DEBUG}")
        sb.appendLine("school=${school.name} type=${school.type} url=${school.url}")
        sb.appendLine("status=${r.status}")
        sb.appendLine("courseCount=${r.courseCount}")
        sb.appendLine("selectedFramePath=${r.selectedFramePath?.joinToString("/") ?: "<none>"}")
        sb.appendLine("matchedAnchors=${r.matchedAnchors.joinToString(",")}")
        sb.appendLine("retryCount=${r.retryCount} maxDepthReached=${r.maxDepthReached}")
        sb.appendLine("blockedFrames=${r.blockedFrames.joinToString(";")}")
        sb.appendLine("skippedFrames=${r.skippedFrames.joinToString(",")}")
        sb.appendLine("diagnosticHint=${r.diagnosticHint}")
        writeText(zos, manifest, "summary.txt", sb.toString(), "状态/锚点/帧路径/重试/hint/学校/版本")
    }

    private fun writeFrames(
        zos: ZipOutputStream,
        manifest: MutableList<Pair<String, String>>,
        r: FrameCaptureResult
    ) {
        // 全帧落盘 (对齐桌面 collector 1-dom/): 每个可达 frame 一个 html; 选中帧标 0- 前缀。
        val seen = HashSet<String>()
        var idx = 1
        for ((framePath, html) in r.allFrames) {
            if (html.isBlank()) continue
            val safe = framePath.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80)
            if (!seen.add(safe)) continue
            val isSel = r.selectedFramePath != null &&
                framePath.endsWith(r.selectedFramePath.joinToString("_").replace(Regex("[^A-Za-z0-9_-]"), "_"))
            val path = if (isSel) "frames/0-$safe.html" else "frames/$idx-$safe.html"
            writeText(zos, manifest, path, html, if (isSel) "选中 frame 的 outerHTML 原文" else "可达 frame outerHTML 原文")
            idx++
        }
        if (manifest.none { it.first.startsWith("frames/") } && r.html.isNotBlank()) {
            val path = "frames/0-${(r.selectedFramePath?.lastOrNull() ?: "selected").replace(Regex("[^A-Za-z0-9_-]"), "_")}.html"
            writeText(zos, manifest, path, r.html, "选中 frame 的 outerHTML 原文(无全帧快照兜底)")
        }
    }

    private fun writeInventory(
        zos: ZipOutputStream,
        manifest: MutableList<Pair<String, String>>,
        json: String?
    ) {
        val body = json?.takeIf { it.isNotBlank() } ?: "{\"url\":\"(no document)\",\"total\":0,\"items\":[]}"
        writeText(zos, manifest, "dom-inventory.txt", body, "DOM 可点元素清单(a/button/input/select/textarea/label)")
    }

    /** 设备/WebView 环境 — 适配者一眼看到 SDK/包名/UA/WebView 实现。 */
    /**
     * Writes the real same-origin resource responses collected in the WebView page.
     * Entries with an error remain metadata-only; no synthetic body is generated.
     */
    private fun writeResourceReplay(
        zos: ZipOutputStream,
        manifest: MutableList<Pair<String, String>>,
        json: String?,
    ) {
        val raw = json?.takeIf { it.isNotBlank() } ?: return
        val rows = runCatching { org.json.JSONArray(raw) }.getOrNull() ?: return
        val manifestJson = rows.toString()
        writeText(zos, manifest, "4-net-replay/manifest.json", manifestJson, "资源重取结果与失败原因")
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val body = row.optString("body", "")
            if (body.isBlank() || row.has("error")) continue
            val path = "4-net-replay/${i + 1}.body"
            writeText(zos, manifest, path, body, "同源资源真实响应体")
        }
    }

    /**
     * 审计修复: runtime 网络数据有三种到达形态, 全部归一成 {live,replay,weeks} 对象 —
     *  1. 双引号包裹的 eval 原样回传 (DIAGNOSTIC_NETWORK_SNAPSHOT_JS 快照路径:
     *     evaluateJavascript 把 JS 返回值编码成 JSON 字符串字面量, 调用方没走 JSONTokener 解包)
     *  2. 裸数组 (exportJsNetwork() 默认值是 JSONArray)
     *  3. 正常对象 {live,replay,weeks}
     * 解析失败 = 空对象兜底, 但绝不静默丢已捕获证据。
     */
    private fun parseNetworkLiveRoot(json: String?): org.json.JSONObject {
        var raw = json?.takeIf { it.isNotBlank() } ?: return org.json.JSONObject()
        // 形态1: 双引号包裹 → 解开外层字符串字面量
        if (raw.startsWith("\"")) {
            raw = runCatching { org.json.JSONTokener(raw).nextValue() as? String ?: raw }.getOrNull() ?: raw
        }
        // 形态2: 裸数组 → 归一为 {live: [...]}
        if (raw.startsWith("[")) {
            val arr = runCatching { org.json.JSONArray(raw) }.getOrNull()
                ?: return org.json.JSONObject()
            return org.json.JSONObject().put("live", arr)
        }
        return runCatching { org.json.JSONObject(raw) }.getOrNull() ?: org.json.JSONObject()
    }

    private fun writeNetworkLive(
        zos: ZipOutputStream,
        manifest: MutableList<Pair<String, String>>,
        json: String?,
    ) {
        val root = parseNetworkLiveRoot(json)
        val rows = root.optJSONArray("live") ?: org.json.JSONArray()
        val replay = root.optJSONArray("replay") ?: org.json.JSONArray()
        val weeks = root.optJSONArray("weeks") ?: org.json.JSONArray()
        writeText(zos, manifest, "4-net-live/manifest.json", rows.toString(), "页面运行时真实 fetch/XHR 请求与响应")
        val headers = StringBuilder()
        val urls = LinkedHashSet<String>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val url = row.optString("url", "")
            if (url.isNotBlank()) urls += url
            val responseHeaders = row.opt("responseHeaders")
            if (responseHeaders != null && responseHeaders.toString().isNotBlank()) {
                headers.append("== ").append(row.optString("method", "GET"))
                    .append(" ").append(url).append(" ==\n")
                    .append(responseHeaders).append("\n\n")
            }
            val body = row.optString("responseBody", "")
            if (body.isNotBlank()) writeText(zos, manifest, "4-net-live/${i + 1}.body", body, "真实响应正文")
        }
        if (replay.length() > 0) {
            writeText(zos, manifest, "4-net-replay-withparam/manifest.json", replay.toString(), "基于已观察 POST 请求的重放结果")
        }
        if (weeks.length() > 0) {
            writeText(zos, manifest, "4-net-replay-weeks/manifest.json", weeks.toString(), "基于已观察周参数的 1..25 周重放计划")
        }
        writeText(zos, manifest, "6-logs/network-live.json", rows.toString(), "机器可读运行时网络记录")
        writeText(zos, manifest, "6-logs/all-urls.txt", urls.joinToString("\n"), "运行时发现的 URL")
        if (headers.isNotEmpty()) writeText(zos, manifest, "6-logs/response-headers.txt", headers.toString(), "运行时响应头汇总")
        val downloadText = JwDiagnosticSession.exportDownloads()
        if (downloadText.lines().size > 2) writeText(zos, manifest, "4-downloads/manifest.txt", downloadText, "WebView 下载元数据")
        // 下载实体 — 桌面 collector 4-downloads/ 对标: 实际文件字节落包
        JwDiagnosticSession.exportDownloadBodies().forEach { (idx, body) ->
            zos.putNextEntry(ZipEntry("4-downloads/${idx + 1}.body"))
            zos.write(body)
            zos.closeEntry()
            manifest += "4-downloads/${idx + 1}.body" to "真实下载文件字节(前 2MB)"
        }
        val har = buildHar(rows)
        if (har != null) writeText(zos, manifest, "6-logs/capture.har", har, "HAR 1.2 网络回放文件")
        val collectionSummary = org.json.JSONObject()
            .put("networkRecords", rows.length())
            .put("replayRecords", replay.length())
            .put("weekPlans", weeks.length())
            .put("responseBodies", rows.countBodies())
        writeText(
            zos,
            manifest,
            "6-logs/collection-summary.json",
            collectionSummary.toString(2) + "\n",
            "机器可读采集汇总"
        )
    }

    /** Build a HAR 1.2 representation from runtime records. Bodies are bounded to 64 KiB
     *  each to keep the file usable in browsers; full bodies live under 4-net-live. */
    private fun buildHar(rows: org.json.JSONArray): String? {
        if (rows.length() == 0) return null
        val builder = org.json.JSONObject()
        builder.put("log", org.json.JSONObject().apply {
            put("version", "1.2")
            put("creator", org.json.JSONObject().put("name", "sleepy-android").put("version", "android"))
            val entries = org.json.JSONArray()
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val method = row.optString("method", "GET")
                val url = row.optString("url", "")
                val requestHeaders = toHarHeaders(row.optJSONObject("requestHeaders"))
                val request = org.json.JSONObject()
                    .put("method", method)
                    .put("url", url)
                    .put("httpVersion", "HTTP/1.1")
                    .put("cookies", org.json.JSONArray())
                    .put("headers", requestHeaders)
                    .put("queryString", harQueryString(url))
                    .put("headersSize", -1)
                    .put("bodySize", row.optString("requestBody", "").toByteArray(Charsets.UTF_8).size)
                val requestBody = row.optString("requestBody", "")
                if (requestBody.isNotEmpty()) {
                    request.put(
                        "postData",
                        org.json.JSONObject()
                            .put("mimeType", row.optJSONObject("requestHeaders")?.optString("Content-Type", "") ?: "")
                            .put("text", requestBody)
                    )
                }
                val status = row.optInt("status", -1)
                val responseHeaders = toHarHeaders(row.optJSONObject("responseHeaders"))
                val raw = row.optString("responseBody", "")
                val truncated = if (raw.length > 65536) raw.substring(0, 65536) else raw
                val response = org.json.JSONObject()
                    .put("status", if (status < 0) 0 else status)
                    .put("statusText", if (status < 0) "unknown" else "")
                    .put("httpVersion", "HTTP/1.1")
                    .put("cookies", org.json.JSONArray())
                    .put("headers", responseHeaders)
                    .put("content", org.json.JSONObject()
                        // HAR 1.2 spec: content.size is REQUIRED — full body size even when text is truncated.
                        .put("size", raw.toByteArray(Charsets.UTF_8).size.toLong())
                        .put("text", truncated)
                        .put("mimeType", row.optJSONObject("responseHeaders")?.optString("Content-Type", "application/octet-stream") ?: "application/octet-stream"))
                    // HAR 1.2: redirectURL is the redirect target; only meaningful on 3xx.
                    .put("redirectURL", if (status in 300..399) row.optString("finalUrl", "") else "")
                    .put("headersSize", -1)
                    .put("bodySize", raw.toByteArray(Charsets.UTF_8).size)
                val entry = org.json.JSONObject()
                    .put("startedDateTime", "1970-01-01T00:00:00.000Z")
                    .put("time", 0)
                    .put("request", request)
                    .put("response", response)
                    .put("cache", org.json.JSONObject())
                    .put("timings", org.json.JSONObject()
                        .put("send", 0)
                        .put("wait", 0)
                        .put("receive", 0))
                entries.put(entry)
            }
            put("entries", entries)
        })
        return builder.toString()
    }

    private fun harQueryString(url: String): org.json.JSONArray {
        val query = runCatching { android.net.Uri.parse(url).query }.getOrNull().orEmpty()
        val out = org.json.JSONArray()
        if (query.isBlank()) return out
        query.split('&').forEach { item ->
            val parts = item.split('=', limit = 2)
            out.put(org.json.JSONObject()
                .put("name", parts.firstOrNull().orEmpty())
                .put("value", parts.getOrNull(1).orEmpty()))
        }
        return out
    }

    /** collect-log.txt — 每个已导出文件一行; 对标桌面 collector 的采集过程日志。 */
    private fun buildCollectLog(manifest: List<Pair<String, String>>): String = buildString {
        appendLine("# Session: ${JwDiagnosticSession.currentSessionId()}")
        appendLine("# Collected ${manifest.size} entries")
        appendLine()
        for ((path, _) in manifest) {
            appendLine("+ collected $path")
        }
    }

    private fun toHarHeaders(map: org.json.JSONObject?): org.json.JSONArray {
        val out = org.json.JSONArray()
        if (map == null) return out
        val keys = map.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out.put(org.json.JSONObject().put("name", k).put("value", map.optString(k, "")))
        }
        return out
    }

    private fun org.json.JSONArray.countBodies(): Int {
        var count = 0
        for (i in 0 until length()) if (optJSONObject(i)?.optString("responseBody", "").orEmpty().isNotBlank()) count++
        return count
    }

    private fun writeEnvironment(
        zos: ZipOutputStream,
        manifest: MutableList<Pair<String, String>>,
        ctx: Context?
    ) {
        val sb = StringBuilder()
        sb.appendLine("# Device / WebView Environment")
        sb.appendLine("sdk=${Build.VERSION.SDK_INT} release=${Build.VERSION.RELEASE} device=${Build.DEVICE} model=${Build.MODEL} manufacturer=${Build.MANUFACTURER}")
        sb.appendLine("appPackage=${ctx?.packageName ?: "(no ctx)"}")
        // WebView 版本 — chromium 内核版本号, 排协议时常用 (94/100/110 ...)
        runCatching {
            val pi = android.webkit.WebView.getCurrentWebViewPackage()
            sb.appendLine("webviewPackage=${pi?.packageName ?: "(none)"} webviewVersion=${pi?.versionName ?: "(none)"}")
        }.onFailure { sb.appendLine("webviewPackage=<get failed: ${it.message}>") }
        writeText(zos, manifest, "env/device.txt", sb.toString(), "设备/Android SDK/WebView 实现版本")
    }

    /** INDEX.txt — 桌面 collector 7 段目录说明 + 文件清单(对标)。 */
    private fun writeIndex(
        zos: ZipOutputStream,
        manifest: List<Pair<String, String>>
    ) {
        val sb = StringBuilder()
        sb.appendLine("Sleepy JW Diagnostic Package (sleepy-android)")
        sb.appendLine("session=${JwDiagnosticSession.currentSessionId()}")
        sb.appendLine()
        sb.appendLine("== 目录说明 ==")
        sb.appendLine("summary.txt  状态/锚点/帧路径/重试/hint/学校/版本")
        sb.appendLine("frames/      所有可达 frame 的 outerHTML 原文")
        sb.appendLine("dom-inventory.txt  DOM 可点元素清单(失败时反循救命)")
        sb.appendLine("netlog.txt   全程网络请求 + 请求头/响应头/重定向标记")
        sb.appendLine("console.txt  WebView console 输出")
        sb.appendLine("cookies-full.txt  Cookie 全量值(1B 不脱敏,排查登录态)")
        sb.appendLine("5-storage/  Web Storage(sessionStorage + localStorage)全键值")
        sb.appendLine("2-inline/links.json  页面链接全集 + select 下拉枚举(学期码)")
        sb.appendLine("env/device.txt  SDK/包名/WebView 实现版本")
        sb.appendLine()
        sb.appendLine("== 文件清单 (path | 说明) ==")
        for ((path, desc) in manifest) {
            sb.appendLine("$path  |  $desc")
        }
        sb.appendLine()
        sb.appendLine("== 自查提示 ==")
        sb.appendLine("接口响应/页面可能含你的姓名/学号;提交前搜索改成 XXX 即可,不影响适配。")
        zos.putNextEntry(ZipEntry("INDEX.txt"))
        zos.write(sb.toString().toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun writeText(
        zos: ZipOutputStream,
        manifest: MutableList<Pair<String, String>>,
        name: String,
        content: String,
        description: String
    ) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
        manifest += name to description
    }

    /** API 29+: MediaStore.Downloads RELATIVE_PATH = "Download/Sleepy/教务日志" */
    private fun writeZip(ctx: Context, name: String, bytes: ByteArray): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeToMediaStoreDownloads(ctx, name, bytes)
        } else {
            writeToAppExternalDownloads(ctx, name, bytes)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeToMediaStoreDownloads(ctx: Context, name: String, bytes: ByteArray): Uri? {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, MIME)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$RELATIVE_DIR")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val resolver = ctx.contentResolver
            val uri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            Log.e(TAG, "writeToMediaStoreDownloads failed", e)
            null
        }
    }

    /** API 26-28: getExternalFilesDir(Download) 应用专属目录,走 FileProvider 共享 */
    private fun writeToAppExternalDownloads(ctx: Context, name: String, bytes: ByteArray): Uri? {
        return try {
            val baseDir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.cacheDir
            val dir = File(baseDir, RELATIVE_DIR).apply { mkdirs() }
            val file = File(dir, name)
            FileOutputStream(file).use { it.write(bytes) }
            androidx.core.content.FileProvider.getUriForFile(
                ctx, "${ctx.packageName}.fileprovider", file
            )
        } catch (e: Exception) {
            Log.e(TAG, "writeToAppExternalDownloads failed", e)
            null
        }
    }

    /** 落盘成功后由 Activity 调 — 拉系统分享面板。 */
    fun share(ctx: Context, zipName: String, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, zipName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(
            Intent.createChooser(intent, ctx.getString(R.string.jw_diag_export_chooser))
        )
    }

    sealed class DumpResult {
        data class Ok(val zipName: String, val uri: Uri) : DumpResult()
        data class Fail(val reason: String) : DumpResult()
    }
}