package com.lingion.sleepy.ui.screen.imports

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 闽江师范高等专科学校 (edums.app.fzmjtc.cn) 超星教务适配契约锁。
 *
 * 用户动线: 选闽江师范 → WebView 打开 edums.app.fzmjtc.cn → 登录后停在门户壳页
 * (pathname = '/admin', 无尾斜杠) → 点导入 → 旧实现 indexOf('/admin/') === 0 判 false
 * → base='' → 三个 fetch 全部裸路径 404 返回 HTML → r.json() 抛 Unexpected token '<'
 *
 * 契约 (CHAOXING_FETCH_JS 必须):
 *  - base 推断必须匹配 '/admin' 和 '/admin/' 两种形态 (正则 /^\/admin(\/|$)/)
 *  - queryKbForGrdb 返回 HTML 时必须回退到 sdpkkbList (Syswin 变体)
 *  - sdpkkbList 请求必须带 xhid 参数
 */
class JwChaoxingWebViewContractTest {

    private val source: String = sequenceOf(
        System.getProperty("sleepy.test.root")?.let { java.io.File(it, "app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt") },
        java.io.File("src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt"),
        java.io.File("app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt"),
        java.io.File(System.getProperty("user.dir"), "sleepy/app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt")
    ).filterNotNull().firstOrNull { it.isFile }
        ?.readText()
        ?: error("Unable to load JwWebViewLoginScreen.kt source")

    private val fetchJs: String
        get() {
            val start = source.indexOf("const val CHAOXING_FETCH_JS")
            val end = source.indexOf("const val QZ_APP_FETCH_JS")
            assertTrue("CHAOXING_FETCH_JS constant missing or after QZ_APP_FETCH_JS", start >= 0 && end > start)
            return source.substring(start, end)
        }

    @Test
    fun chaoxingWebView_dispatch_selects_a_dedicated_fetch_branch() {
        assertTrue(
            "Capturing CHAOXING must use the dedicated fetch JS, not HTML frame capture",
            Regex("""school\.type\s*==\s*JwProtocol\.TYPE_CHAOXING""").containsMatchIn(source)
        )
    }

    @Test
    fun chaoxingFetchJs_admin_prefix_matches_both_slash_and_no_slash() {
        val js = fetchJs
        assertTrue(
            "base 推断必须匹配 '/admin' 和 '/admin/' 两种形态",
            js.contains("/^\\/admin(\\/|$)/.test(location.pathname)")
        )
    }

    @Test
    fun chaoxingFetchJs_falls_back_to_sdpkkbList_when_queryKbForGrdb_returns_html() {
        val js = fetchJs
        assertTrue(
            "queryKbForGrdb 返回 HTML 时必须回退到 sdpkkbList",
            js.contains("sdpkkbList")
        )
        assertTrue(
            "回退逻辑必须检测 HTML 响应 (startsWith('<'))",
            js.contains("startsWith('<')")
        )
    }

    @Test
    fun chaoxingFetchJs_sdpkkbList_includes_xhid_parameter() {
        val js = fetchJs
        assertTrue(
            "sdpkkbList 请求必须带 xhid 参数",
            js.contains("xhid=")
        )
    }
}
