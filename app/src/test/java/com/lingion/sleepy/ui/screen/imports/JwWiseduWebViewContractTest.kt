package com.lingion.sleepy.ui.screen.imports

import org.junit.Assert.assertTrue
import org.junit.Test

/** Wisedu WebView transport contract for stale/expired CAS sessions. */
class JwWiseduWebViewContractTest {
    private val source = sequenceOf(
        java.io.File("app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt"),
        java.io.File("src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt"),
        java.io.File(System.getProperty("user.dir"), "sleepy/app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt"),
    ).firstOrNull { it.isFile }?.readText()
        ?: error("Unable to load JwWebViewLoginScreen.kt source")

    private val js: String
        get() {
            val start = source.indexOf("private const val WISEDU_FETCH_JS")
            val end = source.indexOf("private const val NUIT_FETCH_JS")
            check(start >= 0 && end > start) { "WISEDU_FETCH_JS block not found" }
            return source.substring(start, end)
        }

    @Test
    fun wiseduFetch_reports_403_before_parsing_html_as_json() {
        assertTrue("入口响应必须检查 HTTP 状态", js.contains("response.ok") || js.contains("response.status"))
        assertTrue("403 必须走明确的会话失效错误", js.contains("403"))
        assertTrue("403 必须通过统一 bridge 错误信封返回", js.contains("ok:false") && js.contains("__sleepyBridge.onWiseduResult"))
    }

    @Test
    fun wiseduFetch_checks_both_entry_and_term_api_responses() {
        assertTrue("入口请求必须经过状态检查", js.contains("index.do") && js.contains("response.ok"))
        assertTrue("学期接口必须经过状态检查", js.contains("dqxnxq.do") && js.contains("response.ok"))
        assertTrue("不能把 403 HTML 直接当 JSON 解析", !js.contains(".then(function(r){ return r.json(); })"))
    }

    @Test
    fun seu_root_portal_uses_shared_wisedu_capture_and_checks_schedule_response() {
        assertTrue(source.contains("school.type == JwProtocol.TYPE_SEU") &&
            source.contains("evaluateFetchWithTimeout(wv, WISEDU_FETCH_JS.replace("))
        assertTrue("SEU 门户根路径必须允许抓取", source.contains("location.hostname.toLowerCase() ==="))
        assertTrue("SEU 学期可由课表页控件选中", source.contains("document.querySelector('#dqxnxq2')"))
        assertTrue("课表接口 HTTP 状态必须检查", js.contains("WISEDU_HTTP_"))
        assertTrue("登录页 HTML 不能当作成功数据", js.contains("课表接口返回非 JSON"))
    }
}
