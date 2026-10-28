package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T6 关键边界直断言(独立文件,便于 gradle 单跑)
 */
class JwProtocolDetectionUnitTest {

    // ============ URL 层边界 ============

    @Test
    fun `URL 含 qz 子串但 jwglxt 路径命中时不误判强智`() {
        // qzu.example.edu.cn 是"泉州/黔南/庆阳"等高校常用子域,不能因 qz 误判
        assertEquals(JwProtocol.TYPE_ZF_NEW,
            JwImportViewModel.detectProtocolFromUrlForTest("https://qzu.example.edu.cn/jwglxt/xtgl/login_slogin.html"))
        // 更极端: qz + jsxsd 同时存在,jwglxt 优先(jwglxt 在 when 链前段)
        assertEquals(JwProtocol.TYPE_ZF_NEW,
            JwImportViewModel.detectProtocolForTest(html = "", url = "https://qz.example.edu.cn/jwglxt/"))
    }

    @Test
    fun `iEAS kbcx URL wins over generic zf_new kbcx marker`() {
        assertEquals(JwProtocol.TYPE_QZ_IEAS,
            JwImportViewModel.detectProtocolFromUrlForTest(
                "https://jwxt.buaa.edu.cn:7001/ieas2.1/kbcx/queryGrkb"))
    }

    @Test
    fun `byxt BUAA 新本研教务路由到 NEU 协议族`() {
        // 2026-09-19: byxt.buaa.edu.cn = 金智 jwapp homeapp (9 仓 cross-verified),
        // 协议与东北大学同族; 旧 jwxt.buaa.edu.cn iEAS 走 ②b 不受影响
        assertEquals(JwProtocol.TYPE_NEU,
            JwImportViewModel.detectProtocolFromUrlForTest(
                "https://byxt.buaa.edu.cn/jwapp/sys/homeapp/index.do"))
        assertEquals(JwProtocol.TYPE_NEU,
            JwImportViewModel.detectProtocolFromUrlForTest(
                "https://byxt.buaa.edu.cn/jwapp/sys/homeapp/home/index.html"))
        // 不带 /jwapp/ 路径的裸入口也命中 (host 锚点)
        assertEquals(JwProtocol.TYPE_NEU,
            JwImportViewModel.detectProtocolFromUrlForTest("https://byxt.buaa.edu.cn/"))
    }

    @Test
    fun `jwxt BUAA 旧 iEAS 入口不受 byxt 新分支影响`() {
        assertEquals(JwProtocol.TYPE_QZ_IEAS,
            JwImportViewModel.detectProtocolFromUrlForTest(
                "https://jwxt.buaa.edu.cn:7001/ieas2.1"))
        assertEquals(JwProtocol.TYPE_QZ_IEAS,
            JwImportViewModel.detectProtocolFromUrlForTest(
                "https://jwxt-7001.e2.buaa.edu.cn/ieas2.1/kbcx/queryGrkb"))
    }

    @Test fun `UCAS personSchedule URL selects UCAS parser`() {
        assertEquals(JwProtocol.TYPE_UCAS,
            JwImportViewModel.detectProtocolFromUrlForTest("https://xkgo.ucas.ac.cn:3000/course/personSchedule"))
    }

    @Test
    fun `xtgl 无尾斜杠必须命中 zf_new`() {
        assertEquals(JwProtocol.TYPE_ZF_NEW,
            JwImportViewModel.detectProtocolFromUrlForTest("https://jwgl.example.edu.cn/xtgl"))
        assertEquals(JwProtocol.TYPE_ZF_NEW,
            JwImportViewModel.detectProtocolFromUrlForTest("http://115.236.84.158/xtgl"))
        assertEquals(JwProtocol.TYPE_ZF_NEW,
            JwImportViewModel.detectProtocolFromUrlForTest("https://jwgl.example.edu.cn/xtgl/"))
    }

    @Test
    fun `裸 urp host 不再钉死 URP_NEW`() {
        // 修复前: u.contains("urp") → URP_NEW。修复后: null(让 HTML/tryAllParsers 接管)
        assertNull(JwImportViewModel.detectProtocolFromUrlForTest("https://urp.cqupt.edu.cn/courseTable/show"))
        assertNull(JwImportViewModel.detectProtocolFromUrlForTest("https://urpjw.example.edu.cn/some/path"))
    }

    @Test
    fun `xkAction_do 命中老 URP`() {
        assertEquals(JwProtocol.TYPE_URP,
            JwImportViewModel.detectProtocolFromUrlForTest("https://222.194.15.1/xkAction.do?actionType=6"))
        // 大小写归一化
        assertEquals(JwProtocol.TYPE_URP,
            JwImportViewModel.detectProtocolFromUrlForTest("https://BKJW.EXAMPLE.EDU.CN/XKACTION.DO"))
    }

    @Test
    fun `jxd 无尾斜杠也命中强智`() {
        assertEquals(JwProtocol.TYPE_QZ,
            JwImportViewModel.detectProtocolFromUrlForTest("http://jwxt.lyu.edu.cn/jxd"))
        assertEquals(JwProtocol.TYPE_QZ,
            JwImportViewModel.detectProtocolFromUrlForTest("http://jwxt.lyu.edu.cn/jxd/"))
    }

    @Test
    fun `jxd 不能被其他子串误命中`() {
        assertNull(JwImportViewModel.detectProtocolFromUrlForTest("https://example.edu.cn/jxdvanced"))
        assertNull(JwImportViewModel.detectProtocolFromUrlForTest("https://myjxd.example.edu.cn/"))
    }

    @Test
    fun `xskbcx_aspx 不被 xskbcx_cx 误吸`() {
        assertEquals(JwProtocol.TYPE_ZF,
            JwImportViewModel.detectProtocolFromUrlForTest("https://jw.usts.edu.cn/xskbcx.aspx"))
        assertEquals(JwProtocol.TYPE_ZF_NEW,
            JwImportViewModel.detectProtocolFromUrlForTest("https://jwgl.example.edu.cn/jwglxt/kbcx/xskbcx_cxXsKb.html"))
    }

    @Test
    fun `CAS 网关 URL 返回 null`() {
        assertNull(JwImportViewModel.detectProtocolFromUrlForTest(
            "https://cas.example.edu.cn/authserver/login?service=https%3A%2F%2Fjwgl.example.edu.cn%2Fjwglxt"))
        assertNull(JwImportViewModel.detectProtocolFromUrlForTest("https://sso.example.edu.cn/cas/login"))
    }

    @Test
    fun `空 URL 返回 null`() {
        assertNull(JwImportViewModel.detectProtocolFromUrlForTest(""))
        assertNull(JwImportViewModel.detectProtocolFromUrlForTest("   "))
    }

    @Test
    fun `纯域名不含路径的学校 URL 多数返回 null`() {
        // 这些必须返回 null 走 HTML/tryAllParsers 兜底,不能凭空猜协议
        assertNull(JwImportViewModel.detectProtocolFromUrlForTest("http://jwgl.sdust.edu.cn/"))
        assertNull(JwImportViewModel.detectProtocolFromUrlForTest("https://jwc.lyu.edu.cn/"))
    }

    @Test
    fun `超星按校租户域名判 chaoxing`() {
        // 2026-10-28 WakeUP 6.6.2 目录调查: *.jw.chaoxing.com = 超星泛雅教务按校租户生产入口
        // (GitHub 交叉验证: hbut.jw.chaoxing.com 等 9+ 学生仓库同端点; dump 实测 125 行 jw 租户
        //  + 1 行 fanya 租户, 全部带学校前缀子域, 无裸域聚合形态)
        assertEquals(JwProtocol.TYPE_CHAOXING,
            JwImportViewModel.detectProtocolFromUrlForTest("https://hbut.jw.chaoxing.com/admin"))
        assertEquals(JwProtocol.TYPE_CHAOXING,
            JwImportViewModel.detectProtocolFromUrlForTest("http://ynzi.fanya.chaoxing.com/portal/index!utp"))
        // webvpn /http/<hex>/ 重写把原 URL 原样留在 path 里 → 内层同锚
        assertEquals(JwProtocol.TYPE_CHAOXING,
            JwImportViewModel.detectProtocolFromUrlForTest(
                "https://tju.webvpn.tju.edu.cn/http/68747470732f2f/hbut.jw.chaoxing.com/admin"))
    }

    @Test
    fun `学习通聚合域与非租户拼接不误判 chaoxing`() {
        // 裸 chaoxing.com 是学习通聚合域(mooc/考试/继教), 不是教务判型依据;
        // 反向拼接(notjw/mirrorjw 无点前导、.com.cn/.example.org 换尾)不得命中
        assertNull(JwImportViewModel.detectProtocolFromUrlForTest("https://chaoxing.com/mooc-enc/video"))
        assertNull(JwImportViewModel.detectProtocolFromUrlForTest("https://jw.chaoxing.example.org/login"))
        assertNull(JwImportViewModel.detectProtocolFromUrlForTest("https://notjw.chaoxing.com.cn/x"))
        assertNull(JwImportViewModel.detectProtocolFromUrlForTest("https://mirrorjw.chaoxing.com.net/portal"))
    }

    // ============ HTML 层边界 ============

    @Test
    fun `BNUZ 登录页 __VIEWSTATE 不被老正方误吸`() {
        val html = File("src/test/resources/jw_fixtures/detection-pages/bnuz-es-login.html").readText()
        // bnuz 登录页 form action="default.aspx" + __VIEWSTATE + 无 default2.aspx
        assertEquals(JwProtocol.TYPE_BNUZ,
            JwImportViewModel.detectProtocolFromHtmlForTest(html))
    }

    @Test
    fun `老正方登录页 __VIEWSTATE 判 zf`() {
        val html = File("src/test/resources/jw_fixtures/detection-pages/zf-old-login.html").readText()
        assertEquals(JwProtocol.TYPE_ZF,
            JwImportViewModel.detectProtocolFromHtmlForTest(html))
    }

    @Test
    fun `BNUZ + default2 同页不被误判`() {
        // 混合页: __VIEWSTATE + action="default.aspx" + default2.aspx 提示
        // 走 zf(因为 default2.aspx 是老正方专属)
        val html = """<html><head><title>欢迎使用正方教务管理系统</title></head>
            <body><form action="default.aspx"><input name="__VIEWSTATE"/></form>
            <!-- deprecated: default2.aspx --></body></html>"""
        assertEquals(JwProtocol.TYPE_ZF,
            JwImportViewModel.detectProtocolFromHtmlForTest(html))
    }

    @Test
    fun `URL 优先于 HTML 判型`() {
        val html = """
            <html><head><title>混合特征</title>
            <link rel="stylesheet" href="/zftal-ui-v5-1.0.2/assets/css/zftal-ui.css">
            <input type="hidden" name="__VIEWSTATE" value="..."/></head>
            <body>正方软件股份有限公司 版本 V-8.0.0</body></html>
        """.trimIndent()
        // URL 明确 jwglxt → zf_new 优先,不查 HTML 的 __VIEWSTATE
        assertEquals(JwProtocol.TYPE_ZF_NEW,
            JwImportViewModel.detectProtocolForTest(html, "https://example.edu.cn/jwglxt/xtgl/login_slogin.html"))
        // URL 是老正方 → zf 优先,不查 HTML 的 zftal-ui
        assertEquals(JwProtocol.TYPE_ZF,
            JwImportViewModel.detectProtocolForTest(html, "https://jw.example.edu.cn/xskbcx.aspx"))
    }

    @Test
    fun `CAS 网关 HTML 返回 null`() {
        val html = File("src/test/resources/jw_fixtures/detection-pages/cas-gateway.html").readText()
        assertNull(JwImportViewModel.detectProtocolFromHtmlForTest(html))
    }

    @Test
    fun `空 HTML 返回 null`() {
        assertNull(JwImportViewModel.detectProtocolFromHtmlForTest(""))
        assertNull(JwImportViewModel.detectProtocolFromHtmlForTest("   "))
        assertNull(JwImportViewModel.detectProtocolFromHtmlForTest("<html></html>"))
    }

    @Test
    fun `组合判型 双层都 null 时返回 null`() {
        val html = File("src/test/resources/jw_fixtures/detection-pages/cas-gateway.html").readText()
        assertNull(JwImportViewModel.detectProtocolForTest(html,
            "https://cas.example.edu.cn/authserver/login?service=foo"))
    }

    // ============ 诊断 API ============

    @Test
    fun `hitFeatures 返回所有命中的指纹`() {
        val html = """
            <html><head><title>教学管理信息服务平台</title>
            <link href="/zftal-ui-v5-1.0.2/x.css"/></head>
            <body>__VIEWSTATE __VIEWSTATE 正方软件股份有限公司</body></html>
        """.trimIndent()
        val hits = JwImportViewModel.detectProtocolHitFeaturesForTest(html)
        assertTrue(hits.contains("zftal-ui-"))
        assertTrue(hits.contains("title:教学管理信息服务平台"))
        assertTrue(hits.contains("__VIEWSTATE"))
        assertTrue(hits.size >= 3)
    }

    @Test
    fun `hitFeatures 对无指纹 HTML 返回空列表`() {
        assertTrue(JwImportViewModel.detectProtocolHitFeaturesForTest("<html></html>").isEmpty())
    }
}
