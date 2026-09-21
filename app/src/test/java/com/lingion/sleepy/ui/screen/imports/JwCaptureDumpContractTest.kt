package com.lingion.sleepy.ui.screen.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 排查全量包 dump 契约 — zip 组装 + 落 MediaStore Downloads。
 *
 * 触发: 教务导入报错弹窗点"导出排查全量包"
 * 隐私: 1B 完全不脱敏 —— Cookie/学号/表单值全保留,排查价值最大
 * 动线: 2A 存完自动弹分享面板
 * 路径: Downloads/Sleepy/教务日志/ (API 29+), < cache/ 给 API 26-28
 */
class JwCaptureDumpContractTest {

    private val source: String = sequenceOf(
        File("app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwCaptureDump.kt"),
        File("src/main/java/com/lingion/sleepy/ui/screen/imports/JwCaptureDump.kt"),
    ).firstOrNull { it.isFile }?.readText() ?: error("Unable to load JwCaptureDump.kt source")

    private val loginScreen: String = sequenceOf(
        File("app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt"),
        File("src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt"),
    ).firstOrNull { it.isFile }?.readText() ?: error("Unable to load JwWebViewLoginScreen.kt source")

    @Test
    fun cqu_fetch_script_is_wrapped_as_executable_iife() {
        val start = loginScreen.indexOf("private const val CQU_FETCH_JS")
        val end = loginScreen.indexOf("\n\"\"\"", start)
        assertTrue("CQU_FETCH_JS 常量缺失", start >= 0 && end > start)
        val script = loginScreen.substring(start, end)
        assertTrue(script.contains("(function(){"))
        assertTrue(script.contains("try {"))
        assertTrue(script.trimEnd().endsWith("})();"))
    }

    @Test
    fun dump_creates_zip_with_five_files() {
        // zip 必须含: summary.txt, frames/, dom-inventory.txt, netlog.txt, console.txt
        assertTrue("必须有生成 zip 的方法", Regex("""fun\s+(createDump|exportDump|generateDump|buildDump)\s*\(""").containsMatchIn(source))
        assertTrue("zip 必须含 summary.txt", source.contains("summary.txt"))
        assertTrue("zip 必须含 dom-inventory.txt", source.contains("dom-inventory.txt"))
        assertTrue("zip 必须含 netlog.txt", source.contains("netlog.txt"))
        assertTrue("zip 必须含 console.txt", source.contains("console.txt"))
        assertTrue("zip 必须含 frames/ 目录", source.contains("frames") || source.contains("FrameSnapshot"))
    }

    @Test
    fun dump_uses_media_store_downloads_path() {
        // API 29+: MediaStore.Downloads RELATIVE_PATH = "Download/Sleepy/教务日志"
        //           API 26-28: getExternalFilesDir(Download) 应用专属回退 (方案 A)
        assertTrue("必须用 MediaStore Downloads API (29+)", source.contains("MediaStore.Downloads"))
        assertTrue("必须用 RELATIVE_PATH 存到 Downloads/Sleepy/", source.contains("RELATIVE_PATH"))
        assertTrue("API 26-28 必须有 getExternalFilesDir 回退 (方案 A)", source.contains("getExternalFilesDir"))
        assertTrue("必须判 SDK 版本分流通路", source.contains("VERSION_CODES.Q") || source.contains("Build.VERSION.SDK_INT"))
    }

    @Test
    fun dump_includes_frame_html_files() {
        // frames/ 目录必须含选中 frame 的 outerHTML 原文
        assertTrue("必须写 frames/ 前缀路径", source.contains("frames/"))
        assertTrue("必须落 outerHTML 原文 (r.html)", source.contains("r.html"))
    }

    @Test
    fun dump_includes_dom_inventory() {
        // 可点元素清单: a/button/input/select/[onclick] — DOM_INVENTORY_JS 采集,
        // 点导出时在 WebView 上 evaluateJavascript 现抓 (页面还在, 失败弹窗不关页)
        assertTrue(
            "LoginScreen 必须有 DOM_INVENTORY_JS 常量",
            loginScreen.contains("DOM_INVENTORY_JS")
        )
        val jsStart = loginScreen.indexOf("const val DOM_INVENTORY_JS")
        assertTrue("DOM_INVENTORY_JS 常量缺失", jsStart >= 0)
        val js = loginScreen.substring(jsStart, jsStart + 4000)
        for (tag in listOf("a", "button", "input", "select")) {
            assertTrue("inventory 必须遍历 <$tag>", js.contains("'$tag'") || js.contains("\"$tag\""))
        }
    }

    @Test
    fun dump_triggers_share_intent_after_save() {
        // 存完后自动弹分享面板
        assertTrue("必须触发分享 Intent", source.contains("ACTION_SEND") || source.contains("share"))
    }

    @Test
    fun dump_no_redaction_1b() {
        // 1B 完全不脱敏: Cookie/学号/表单值全保留
        assertFalse("禁止脱敏函数(1B 不脱敏)", Regex("""fun\s+sanitize|REDACTED""").containsMatchIn(source))
    }

    @Test
    fun dump_summary_includes_capture_result_fields() {
        // summary.txt 必须含 FrameCaptureResult 关键字段: 状态/锚点/帧路径/重试/hint
        assertTrue("summary 必须含 status", source.contains("status"))
        assertTrue("summary 必须含 matchedAnchors", source.contains("matchedAnchors"))
        assertTrue("summary 必须含 selectedFramePath", source.contains("selectedFramePath"))
        assertTrue("summary 必须含 diagnosticHint", source.contains("diagnosticHint"))
        assertTrue("summary 必须含 retryCount", source.contains("retryCount"))
    }

    /** 2026-09-18 用户: 排查包必须与桌面 collector 同级或更全面 — 不是几 KB 的薄包。 */
    @Test
    fun dump_matches_desktop_collector_parity() {
        assertTrue("必须有 INDEX.txt 清单(桌面 collector 对标)", source.contains("INDEX.txt"))
        assertTrue("必须有 cookies-full.txt 全量 Cookie", source.contains("cookies-full.txt"))
        assertTrue("必须有 5-storage/ Web Storage", source.contains("5-storage/"))
        assertTrue("必须有 2-inline/links.json 链接+下拉枚举", source.contains("2-inline/links.json"))
        assertTrue("必须有 env/device.txt 设备环境", source.contains("env/device.txt"))
        assertTrue("必须有全帧落盘 allFrames", Regex("""allFrames""").containsMatchIn(source))
        assertTrue("LoginScreen 必须有 STORAGE_JS 常量", loginScreen.contains("STORAGE_JS"))
        assertTrue("LoginScreen 必须有 LINKS_JS 常量", loginScreen.contains("LINKS_JS"))
        assertTrue("LoginScreen 必须有 RESOURCE_REPLAY_JS 常量", loginScreen.contains("RESOURCE_REPLAY_JS"))
        assertTrue("资源重取只能使用 GET", loginScreen.contains("method:'GET'"))
        assertTrue("资源重取必须携带当前 Cookie", loginScreen.contains("credentials:'include'"))
        assertTrue("资源重取必须限制同源", loginScreen.contains("sameOrigin"))
        assertTrue("单资源重取必须有超时", loginScreen.contains("controller.abort()") && loginScreen.contains("5000"))
        assertTrue("总资源重取必须有超时", loginScreen.contains("totalTimer") && loginScreen.contains("15000"))
        assertTrue("Promise 结果必须经 bridge 回传", loginScreen.contains("__sleepyDiagBridge.onReplayResult"))
        assertTrue("导出完成后必须移除一次性 bridge", activitySource().contains("removeJavascriptInterface(\"__sleepyDiagBridge\")"))
        assertTrue("Kotlin 侧必须给 bridge 等待设置上限", activitySource().contains("withTimeoutOrNull(16_000L)"))
    }

    private fun activitySource(): String = sequenceOf(
        File("app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwImportActivity.kt"),
        File("src/main/java/com/lingion/sleepy/ui/screen/imports/JwImportActivity.kt"),
    ).firstOrNull { it.isFile }?.readText() ?: error("Unable to load JwImportActivity.kt source")

    /** 导出时必须现场抓 Cookie 全量值(CookieManager.getCookie) — 1B 不脱敏。 */
    @Test
    fun export_flow_grabs_cookies_storage_links() {
        val activity: String = sequenceOf(
            File("app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwImportActivity.kt"),
            File("src/main/java/com/lingion/sleepy/ui/screen/imports/JwImportActivity.kt"),
        ).firstOrNull { it.isFile }?.readText() ?: error("Unable to load JwImportActivity.kt source")
        assertTrue("导出必须抓 CookieManager.getCookie 全量值", activity.contains("CookieManager"))
        assertTrue("导出必须现抓 STORAGE_JS", activity.contains("STORAGE_JS"))
        assertTrue("导出必须现抓 LINKS_JS", activity.contains("LINKS_JS"))
        assertTrue("导出必须传 cookiesFull/storageJson/linksJson 给 exportDump", Regex("""exportDump\s*\([^)]*cookiesFull""", RegexOption.DOT_MATCHES_ALL).containsMatchIn(activity))
    }

    /**
     * iframe recorder: 桌面 collector / F12 collect.js 都对同源 iframe 重 instrument,
     * 教务 frameset (XJU PageFrame) 内 fetch/XHR 是 dgData 来源, 漏录 = 静默丢半张图。
     */
    @Test
    fun install_js_instruments_iframes_repeatedly() {
        // 安装脚本里要遍历 window.frames 调用 instrument(win), 并用 setInterval / MutationObserver
        // 兜底迟加载的子帧。
        assertTrue(
            "DIAGNOSTIC_NETWORK_INSTALL_JS must walk window.frames",
            loginScreen.contains("window.frames") || loginScreen.contains("frames[")
        )
        assertTrue(
            "must re-instrument late frames (setInterval / MutationObserver)",
            loginScreen.contains("setInterval") || loginScreen.contains("MutationObserver")
        )
    }

    /**
     * DIAGNOSTIC_NETWORK_EXPORT_JS 周参数重放：桌面 collector 与 F12 都是串行(避免把
     * 校务服务器打爆 / 触发风控)，Android 当前用 Promise.all 并发 250。这是真行为差。
     */
    @Test
    fun export_js_serializes_week_replay_chained_promises() {
        val start = loginScreen.indexOf("internal const val DIAGNOSTIC_NETWORK_EXPORT_JS")
        assertTrue(start >= 0)
        val js = loginScreen.substring(start, start + 6000)
        // 串行: 用 .reduce((p,fn)=>p.then(fn)) / chain / forEach await
        val serialized = js.contains(".reduce(") || js.contains("await ") || js.contains("then(function(prev")
        assertTrue("week replay must be serialized (chain/reduce/await), not Promise.all", serialized)
        // 反向断言: 不允许把所有 weekJobs push 进 Promise.all 一次性等
        val block = "weekJobs.push(call(weekUrl,weekMethod,weekBody,r.requestHeaders||{}).then"
        val allBlock = "Promise.all(weekJobs)"
        assertTrue(
            "week replay should NOT be a single Promise.all(weekJobs) — must chain",
            !(block in js && allBlock in js && js.indexOf(block) < js.indexOf(allBlock))
        )
    }

    /**
     * JwImportActivity invokeOnCancellation 必须把 WebView 调用投递到主线程 —
     * removeJavascriptInterface 在 chromium 是 UI-thread-only API。
     */
    @Test
    fun invoke_on_cancellation_dispatches_webview_calls_to_main() {
        val activity = activitySource()
        // 至少有一段 invokeOnCancellation 体里把 removeJavascriptInterface 用 main.post 包住
        val cancel = Regex("invokeOnCancellation\\s*\\{([\\s\\S]*?)\\}\\s*\\n[\\s]*\\}\\)")
        val matches = cancel.findAll(activity).toList()
        assertTrue("must have at least one invokeOnCancellation block", matches.isNotEmpty())
        val anySafe = matches.any { m ->
            val body = m.groupValues[1]
            body.contains("main.post") && body.contains("removeJavascriptInterface")
        }
        assertTrue("invokeOnCancellation must post WebView calls to main thread", anySafe)
    }

    /**
     * 下载处理：WebView setDownloadListener 触发后必须保存实体文件到 4-downloads/ —
     * 桌面 collector 把实际文件字节落盘（xls/ics 课表导出常常靠它取证），Android
     * 当前仅记元数据 = 静默丢文件。
     */
    @Test
    fun download_listener_persists_file_bytes_to_4_downloads() {
        // 1) JwDiagnosticSession 必须支持保存文件字节
        val sessionSrc = sequenceOf(
            File("app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwDiagnosticSession.kt"),
            File("src/main/java/com/lingion/sleepy/ui/screen/imports/JwDiagnosticSession.kt"),
        ).firstOrNull { it.isFile }?.readText() ?: error("Unable to load JwDiagnosticSession.kt source")
        assertTrue(
            "JwDiagnosticSession must record download bytes (not just metadata)",
            sessionSrc.contains("body: ByteArray?") || sessionSrc.contains("recordDownloadBody")
        )
        // 2) JwCaptureDump 写 4-downloads/ 目录时必须包含实际 .body 文件
        assertTrue(
            "buildZip must write 4-downloads/<n>.body for download captures",
            source.contains("4-downloads/") && (source.contains(".body") || source.contains("downloadBytes"))
        )
    }

    /** 审计#3: 下载抓取线程池必须有界且 daemon, 否则每条下载泄漏一个核心线程。 */
    @Test
    fun download_listener_uses_bounded_executor() {
        val usesBoundedPool = loginScreen.contains("Executors.newFixedThreadPool") ||
            loginScreen.contains("Executors.newCachedThreadPool") ||
            loginScreen.contains("Executors.newScheduledThreadPool") ||
            loginScreen.contains("Executors.newWorkStealingPool") ||
            loginScreen.contains(".shutdown()") ||
            loginScreen.contains("shutdownNow()")
        assertTrue(
            "download fetch must use a bounded/daemon executor (Fixed/Cached/Scheduled/WorkStealing) or shutdown",
            usesBoundedPool
        )
        // 反向断言: 不能使用 newSingleThreadExecutor 永不关闭
        val leaks = Regex("newSingleThreadExecutor\\(\\)").findAll(loginScreen).count()
        assertTrue("download executor must NOT be unbounded single-thread (leaks one per download)", leaks == 0)
        // 线程必须 daemon — 退出不挂
        assertTrue("executor threads must be daemon", loginScreen.contains("isDaemon = true"))
    }

    /** 审计#4: 同一下载必须只记录一次(元数据 + 字节合并到一条), 否则 4-downloads manifest 重复 / body 索引错位。 */
    @Test
    fun download_listener_records_one_entry_per_download() {
        // setDownloadListener 回调体内 recordDownload 只允许调用一次 (6-arg 合并记录)
        val cbStart = loginScreen.indexOf("setDownloadListener")
        assertTrue(cbStart >= 0)
        // 提取回调 lambda 到闭合大括号 (手工扫描平衡)
        var depth = 0
        var end = -1
        for (i in loginScreen.indexOf('{', cbStart) until loginScreen.length) {
            when (loginScreen[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) { end = i; break } }
            }
        }
        assertTrue("setDownloadListener lambda must close", end > cbStart)
        val cbBlock = loginScreen.substring(cbStart, end)
        val cbRecordCount = Regex("recordDownload\\(").findAll(cbBlock).count()
        assertTrue(
            "setDownloadListener callback should record the download exactly once (not metadata+body twice)",
            cbRecordCount == 1,
        )
    }

    /** 审计#1: 快照 JS 返回的 raw 是双引号包裹的 JSON 字符串, writeNetworkLive 必须解包。 */
    @Test
    fun writeNetworkLive_unwraps_double_quoted_eval_result() {
        // JwCaptureDump.kt: 必须尝试 JSONTokener(raw) 解开外层字符串
        assertTrue(source.contains("JSONTokener") || source.contains("optJSONObject") && source.contains("JSONObject"))
    }
}
