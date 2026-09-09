package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * issue #25: 学校域名映射 — 把统一信息门户等"同校非教务" URL 映射回学校目录条目。
 *
 * 采集包实锤的失败链: 报告者把 https://one.hfut.edu.cn/ (统一信息门户) 当教务 URL
 * 输入 → 自定义条目 (name=自定义教务, type=null) → 通用抓取 → 0 课。
 * 正确行为: 输入 URL 的注册域与目录条目同域 → 直接选中目录条目 (拿到该校的
 * 权威教务 URL + 协议 type), WebView 打开 jxglstu.hfut.edu.cn。
 */
class SchoolDomainMatchTest {

    /** 与 SchoolsJsonConsistencyTest 同一加载路径 (Gradle 测试工作目录 = app/ 模块根) */
    private fun loadCatalog(): List<JwSchoolInfo> {
        val f = sequenceOf(
            File("src/main/assets/schools.json"),
            File("app/src/main/assets/schools.json"),
        ).firstOrNull { it.isFile }
            ?: error("schools.json 应存在")
        return JwImportViewModel.parseSchoolsJson(f.readText(Charsets.UTF_8))
    }

    private fun match(url: String, catalog: List<JwSchoolInfo> = loadCatalog()): JwSchoolInfo? =
        SchoolDomainMatch.matchSchool(url, catalog)

    // -------- issue #25 主形态 --------

    @Test
    fun `hfut portal url maps to hfut catalog entry`() {
        val hfut = match("https://one.hfut.edu.cn/")
        assertNotNull("one.hfut.edu.cn 应映射到合肥工业大学目录条目", hfut)
        assertEquals("合肥工业大学", hfut!!.name)
        assertEquals("eams5", hfut.type)
        assertEquals("https://jxglstu.hfut.edu.cn/eams5-student/for-std/course-table", hfut.url)
    }

    @Test
    fun `subdomain forms all map to hfut`() {
        for (host in listOf(
            "https://cas.hfut.edu.cn/cas/login",
            "https://jxglstu.hfut.edu.cn",
            "https://www.hfut.edu.cn",
        )) {
            assertEquals("合肥工业大学", match(host)!!.name)
        }
    }

    @Test
    fun `ahu portal subdomain maps to ahu catalog entry`() {
        val ahu = match("https://one.ahu.edu.cn/")
        assertNotNull("one.ahu.edu.cn 应映射到安徽大学目录条目 (同类失败形态预防)", ahu)
        assertEquals("安徽大学", ahu!!.name)
        assertEquals("eams5", ahu.type)
    }

    // -------- WebVPN / 重写 URL 不映射 --------

    @Test
    fun `webvpn rewritten urls are excluded from domain mapping`() {
        val catalog = loadCatalog()
        assertNull(
            "WebVPN 路径重写 host 不可见, 禁映射",
            match("https://webvpn.hfut.edu.cn/http/77726476706e697374/eams5-student/for-std/course-table", catalog)
        )
        assertNull(
            match("https://webvpn.hfut.edu.cn/webvpn/jxglstu.hfut.edu.cn/eams5-student", catalog)
        )
        assertNull(
            match("https://jxglstu.webvpn.whatever.edu.cn/eams5-student/for-std/course-table", catalog)
        )
    }

    // -------- 目录条目资格: 仅 supported+有 URL 的条目参与映射 --------

    @Test
    fun `pending or legacy entries are not matched`() {
        val catalog = listOf(
            JwSchoolInfo(
                sortKey = "X", name = "某待适配大学", url = "",
                type = null, status = JwSchoolInfo.STATUS_PENDING
            ),
        )
        assertNull(match("https://portal.example.edu.cn/", catalog))
    }

    @Test
    fun `supported entry without url is not matched`() {
        val catalog = listOf(
            JwSchoolInfo(sortKey = "X", name = "无URL大学", url = "", type = null),
        )
        assertNull(match("https://portal.example.edu.cn/", catalog))
    }

    // -------- 未知域名 --------

    @Test
    fun `unknown domain returns null`() {
        assertNull(match("https://portal.someother-university.edu.cn/", loadCatalog()))
    }

    @Test
    fun `blank host returns null`() {
        assertNull(match("", loadCatalog()))
        assertNull(match("not a url at all", loadCatalog()))
    }

    // -------- registrableDomain 启发式 (与 WebView SSL 豁免同一实现) --------

    @Test
    fun `registrable domain heuristic matches webview ssl registry semantics`() {
        assertEquals("hfut.edu.cn", SchoolDomainMatch.registrableDomain("one.hfut.edu.cn"))
        assertEquals("hfut.edu.cn", SchoolDomainMatch.registrableDomain("jxglstu.hfut.edu.cn"))
        assertEquals("ahu.edu.cn", SchoolDomainMatch.registrableDomain("jw.ahu.edu.cn"))
        assertEquals("cumtb.edu.cn", SchoolDomainMatch.registrableDomain("jwxt.cumtb.edu.cn"))
        assertEquals("example.com", SchoolDomainMatch.registrableDomain("a.b.example.com"))
        assertEquals("localhost", SchoolDomainMatch.registrableDomain("localhost"))
        // 多段公共后缀 (edu.cn 等) 取末三段
        assertEquals("edu.cn", SchoolDomainMatch.registrableDomain("edu.cn"))
    }

    @Test
    fun `registrable domain is case and dot tolerant`() {
        // Implementation lowercases input; result must be canonical lowercase
        assertEquals(
            "hfut.edu.cn",
            SchoolDomainMatch.registrableDomain(" ONE.HFUT.EDU.CN. ")
        )
    }
}
