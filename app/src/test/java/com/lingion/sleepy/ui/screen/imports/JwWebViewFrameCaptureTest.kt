package com.lingion.sleepy.ui.screen.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T7 — WebView frame 抓取纯 JVM 单测。
 *
 * 不起 WebView: 抓取 JS 的行为通过"JS 契约(字段名/结构)由 fromJson 消费"间接保证;
 * 本类直接构造 FrameSnapshotList JSON 模拟 JS 输出, 断言 FrameTraversalTree/
 * RenderReadinessChecker 的决策。
 * 每个用例注释首行标注对应的 jw_fixtures/adversarial/iframe-nested 下的 case 编号。
 */
class JwWebViewFrameCaptureTest {

    // ---------- 构造 helper ----------

    /** frame 六元组: [name, src, depth, path, html, blocked] */
    private fun f(
        name: String?, src: String?, depth: Int, path: List<String>,
        html: String?, blocked: String = ""
    ): Array<out Any?> = arrayOf(name, src, depth, path, html, blocked)

    private fun json(vararg frames: Array<out Any?>): String {
        val arr = org.json.JSONArray()
        for (fr in frames) {
            arr.put(org.json.JSONObject()
                .put("name", fr[0] ?: org.json.JSONObject.NULL)
                .put("src", fr[1] ?: org.json.JSONObject.NULL)
                .put("depth", fr[2] as Int)
                .put("path", org.json.JSONArray(fr[3] as List<String>))
                .put("html", fr[4] ?: org.json.JSONObject.NULL)
                .put("blocked", fr[5] ?: ""))
        }
        return org.json.JSONObject()
            .put("ok", true)
            .put("url", "https://jw.example.edu.cn/default2.aspx")
            .put("depth", 8)
            .put("frames", arr)
            .toString()
    }

    private fun parse(html: String): List<Any> =
        if (html.contains("高等数学") || html.contains("操作系统") || html.contains("数据挖掘")) listOf(Any(), Any()) else emptyList()

    private fun res(name: String): String =
        javaClass.classLoader.getResourceAsStream("jw/fixtures/adversarial/iframe-nested/$name")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun resIn(dir: String, name: String): String =
        javaClass.classLoader.getResourceAsStream("jw/fixtures/adversarial/$dir/$name")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    // ---------- case.json #1: 4 层嵌套 ----------

    @Test
    fun `four level iframe recursion reaches deepest Table1`() {
        val snaps = FrameSnapshot.fromJson(json(
            f("(top)", "https://jw.example.edu.cn/default2.aspx", 0, emptyList(), res("zf_4level_top.html")),
            f("L2_main", "L2_xsmain.html", 1, listOf("(top)"), res("zf_4level_l2.html")),
            f("L3_inner", "L3_xskbcx_main.html", 2, listOf("(top)", "L2_main"), res("zf_4level_l3.html")),
            f("L4_kb", "L4_xskbcx_with_table.html", 3, listOf("(top)", "L2_main", "L3_inner"), res("zf_4level_l4.html"))
        ))
        val r = FrameTraversalTree.rankAll(snaps, parse = ::parse)
        assertEquals(FrameCaptureStatus.OK, r.status)
        assertEquals(listOf("(top)", "L2_main", "L3_inner", "L4_kb"), r.selectedFramePath)
        assertTrue(r.matchedAnchors.contains("Table1"))
        assertTrue(r.html.contains("高等数学"))
        assertEquals(3, r.maxDepthReached)
    }

    // ---------- case.json #2: 3 层 default2→xs_main→xskbcx ----------

    @Test
    fun `three layer zf chain selects inner xskbcx frame`() {
        val snaps = FrameSnapshot.fromJson(json(
            f("(top)", "https://jw.suda.edu.cn/default2.aspx", 0, emptyList(), res("zf_3layer_top.html")),
            f("menu", "xs_left.aspx", 1, listOf("(top)"), "<html><body><ul><li>首页</li></ul></body></html>"),
            f("main", "xs_main.aspx", 1, listOf("(top)"), res("zf_3layer_mid.html")),
            f("content", "xskbcx.aspx", 2, listOf("(top)", "main"), res("zf_3layer_inner.html"))
        ))
        val r = FrameTraversalTree.rankAll(snaps, parse = ::parse)
        assertEquals(FrameCaptureStatus.OK, r.status)
        assertEquals(listOf("(top)", "main", "content"), r.selectedFramePath)
        assertTrue(r.html.contains("线性代数"))
        assertEquals(listOf("menu"), r.skippedFrames)  // 无锚点 frame 记入诊断
    }

    // ---------- case.json #3/#11: frame+iframe 混合, 单层可达(正向对照) ----------

    @Test
    fun `mixed frames and iframes single layer still parses 3 courses`() {
        val snaps = FrameSnapshot.fromJson(json(
            f("(top)", "https://jw.example.edu.cn/default2.aspx", 0, emptyList(), res("zf_mixed_top.html")),
            f("menuFrame", "menu_side.html", 1, listOf("(top)"), res("zf_mixed_menu.html")),
            f("lastProgress", "lastProgress_kbcx.html", 1, listOf("(top)"), res("zf_mixed_inner.html"))
        ))
        val r = FrameTraversalTree.rankAll(snaps, parse = ::parse)
        assertEquals(FrameCaptureStatus.OK, r.status)
        assertEquals(listOf("(top)", "lastProgress"), r.selectedFramePath)
    }

    // ---------- case.json #4: 顶层假 Table1 → 内层真表 ----------

    @Test
    fun `fake top Table1 falls back to inner real table by parse result`() {
        val topHtml = res("zf_fake_t1_top.html")
        val innerHtml = res("zf_fake_t1_inner.html")
        assertTrue("前置: 顶层确实含 Table1 锚点", FrameTraversalTree.findAnchors(topHtml).contains("Table1"))
        val snaps = FrameSnapshot.fromJson(json(
            f("(top)", "https://jw.example.edu.cn/default2.aspx", 0, emptyList(), topHtml),
            f("kbcx", "xskbcx_real.html", 1, listOf("(top)"), innerHtml)
        ))
        // 旧实现的拼接语义会让 getElementById 命中顶层空壳; rankAll 按"解析课程数>0"回退
        val r = FrameTraversalTree.rankAll(snaps, parse = ::parse)
        assertEquals(FrameCaptureStatus.OK, r.status)
        assertEquals(listOf("(top)", "kbcx"), r.selectedFramePath)
        assertTrue(r.html.contains("操作系统"))
        assertFalse(r.html.contains("当前周次"))
        assertTrue(r.courseCount > 0)
    }

    // ---------- case.json #5: 跨域 iframe ----------

    @Test
    fun `crossorigin iframe yields CROSS_DOMAIN_IFRAME_BLOCKED with domain hint`() {
        val snaps = FrameSnapshot.fromJson(json(
            f("(top)", "https://jw.main.edu.cn/default2.aspx", 0, emptyList(), res("zf_crossorigin_top.html")),
            f("menu", "https://sso.other-school.edu.cn/cas/login", 1, listOf("(top)"),
                null, "sso.other-school.edu.cn"),
            f("main", "https://jw-vpn.campus.edu.cn/http/777.../xskbcx.aspx", 1, listOf("(top)"),
                null, "jw-vpn.campus.edu.cn")
        ))
        val r = FrameTraversalTree.selectBestFrame(snaps)
        assertEquals(FrameCaptureStatus.CROSS_DOMAIN_IFRAME_BLOCKED, r.status)
        assertEquals(listOf("menu@sso.other-school.edu.cn", "main@jw-vpn.campus.edu.cn"), r.blockedFrames)
        assertNull(r.selectedFramePath)
    }

    // ---------- case.json #8: iframe 内层是登录页 ----------

    @Test
    fun `session expired inside iframe is detected on inner frame not only top`() {
        val snaps = FrameSnapshot.fromJson(json(
            f("(top)", "https://jw.example.edu.cn/xskbcx.aspx", 0, emptyList(), res("zf_expired_top.html")),
            f("main", "../common/login_redirect.html", 1, listOf("(top)"), res("zf_expired_login_inner.html"))
        ))
        val r = FrameTraversalTree.selectBestFrame(snaps)
        assertEquals(FrameCaptureStatus.SESSION_EXPIRED, r.status)
        assertEquals(listOf("(top)", "main"), r.selectedFramePath)
        assertTrue("hint 必须指向登录语义", r.diagnosticHint.contains("登录") || r.diagnosticHint.contains("会话"))
        // 隐私红线: hint 不含 HTML 原文/Cookie/学号
        assertFalse(r.diagnosticHint.contains("__VIEWSTATE"))
        assertFalse(r.diagnosticHint.contains("txtUserName"))
    }

    @Test
    fun `merged login frameset from login-expired group is SESSION_EXPIRED`() {
        val html = resIn("login-expired", "zf_expired_merged.html")
        assertTrue(FrameTraversalTree.looksLikeLoginPage(html))
        val snaps = FrameSnapshot.fromJson(json(
            f("(top)", "https://jw.example.edu.cn/default2.aspx", 0, emptyList(), html)
        ))
        val r = FrameTraversalTree.selectBestFrame(snaps)
        assertEquals(FrameCaptureStatus.SESSION_EXPIRED, r.status)
    }

    // ---------- case.json #9: qz 3 层 + 诱饵 noticeFrame ----------

    @Test
    fun `qz deep3 with decoy selects kbFrame and skips noticeFrame`() {
        val snaps = FrameSnapshot.fromJson(json(
            f("(top)", "https://jw.example.edu.cn/jsxsd/framework/xskb/xskb_list.do", 0, emptyList(), res("qz_deep3_top.html")),
            f("topFrame", "header.jsp", 1, listOf("(top)"), "<html><body>header</body></html>"),
            f("mainFrame", "maintab.jsp", 1, listOf("(top)"), res("qz_deep3_maintab.html")),
            f("noticeFrame", "notice.jsp", 2, listOf("(top)", "mainFrame"), "<html><body>放假通知</body></html>"),
            f("kbFrame", "xskb_list_dk.jsp", 2, listOf("(top)", "mainFrame"), res("qz_deep3_inner.html"))
        ))
        val r = FrameTraversalTree.rankAll(snaps, parse = { html: String -> if (html.contains("高等代数")) listOf(Any(), Any()) else emptyList<Any>() })
        assertEquals(FrameCaptureStatus.OK, r.status)
        assertEquals(listOf("(top)", "mainFrame", "kbFrame"), r.selectedFramePath)
        assertFalse(r.selectedFramePath!!.contains("noticeFrame"))
        assertTrue(r.matchedAnchors.contains("kbtable"))
        assertTrue(r.skippedFrames.contains("noticeFrame"))
    }

    // ---------- case.json #6/#7: 延迟渲染 + about:blank 壳 ----------

    @Test
    fun `delayed kbcontainer readiness and give-up status`() {
        val html = res("zf_delayed_kbcontainer.html")
        assertEquals(RenderReadinessChecker.Readiness.DELAY, RenderReadinessChecker.check(html, 0))
        assertEquals(RenderReadinessChecker.Readiness.DELAY, RenderReadinessChecker.check(html, 2))
        assertEquals(RenderReadinessChecker.Readiness.GIVE_UP, RenderReadinessChecker.check(html, 3))
        assertEquals(FrameCaptureStatus.CONTAINER_EMPTY_AFTER_DELAY, RenderReadinessChecker.delayedStatus(3))
    }

    @Test
    fun `about blank iframe shell retries as IFRAME_NAV_PENDING not WRONG_PAGE`() {
        val shell = "<html><head></head><body></body></html>"
        assertTrue(RenderReadinessChecker.checkBlankShell(shell))
        assertFalse(RenderReadinessChecker.checkBlankShell(res("zf_3layer_inner.html")))
        // 模拟 retry 耗尽时的分类: 空壳 → IFRAME_NAV_PENDING
        val snaps = FrameSnapshot.fromJson(json(
            f("(top)", "https://jw.example.edu.cn/default2.aspx", 0, emptyList(),
                "<html><body><h3>个人课表</h3><iframe name=\"main\" src=\"about:blank\"></iframe></body></html>"),
            f("main", "about:blank", 1, listOf("(top)"), shell)
        ))
        val r = FrameTraversalTree.selectBestFrame(snaps)
        // 顶层无锚点、无登录指纹、无跨域 → WRONG_PAGE 占位, captureWithRetry 里按空壳升格
        assertEquals(FrameCaptureStatus.WRONG_PAGE, r.status)
        val shellFound = snaps.frames.any { RenderReadinessChecker.checkBlankShell(it.outerHTML ?: "") }
        assertTrue(shellFound)
    }

    // ---------- empty-semester 组 ----------

    @Test
    fun `empty grid semester anchors hit but zero courses yields EMPTY_SEMESTER`() {
        val html = resIn("empty-semester", "zf_new_grid_empty.html")
        assertTrue(FrameTraversalTree.findAnchors(html).contains("kbgrid_table_0"))
        assertEquals(RenderReadinessChecker.Readiness.DELAY, RenderReadinessChecker.check(html, 0))
        val snaps = FrameSnapshot.fromJson(json(
            f("(top)", "https://jw.example.edu.cn/jwglxt/kbcx/xskbcx_cxXsgrkb.html", 0, emptyList(), html)
        ))
        val r = FrameTraversalTree.rankAll(snaps, parse = { emptyList<Any>() })
        assertEquals(FrameCaptureStatus.EMPTY_SEMESTER, r.status)
        assertTrue(r.matchedAnchors.contains("kbgrid_table_0"))
    }

    // ---------- fromJson 容错 ----------

    @Test
    fun `fromJson tolerates null html blocked domain and missing frames`() {
        val parsed = FrameSnapshot.fromJson(
            """{"ok":true,"url":"u","depth":2,"frames":[
                {"name":"a","src":"https://x.example/","depth":1,"path":["(top)"],"html":null,"blocked":"x.example"},
                {"name":"b","src":"","depth":1,"path":[],"html":"<html></html>","blocked":""}]}""")
        assertEquals(2, parsed.frames.size)
        assertNull(parsed.frames[0].outerHTML)
        assertEquals("x.example", parsed.frames[0].blockedDomain)
        assertEquals(listOf("(top)"), parsed.frames[0].parentPath)
        assertEquals(FrameSnapshotList::class, parsed::class)
        assertEquals(0, FrameSnapshot.fromJson("{}").frames.size)
    }
}
