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

    @Test fun `sibling undergraduate and graduate entries never use first-match fallback`() {
        val undergraduate = JwSchoolInfo("T", "本科", "https://jw.example.edu.cn/ug", "qz")
        val graduate = undergraduate.copy(name = "研究生", url = "https://grad.example.edu.cn/")
        val catalog = listOf(undergraduate, graduate)
        assertEquals(graduate, match(graduate.url, catalog))
        assertEquals(undergraduate, match("https://jw.example.edu.cn/other", catalog))
        assertNull(match("https://portal.example.edu.cn/", catalog))
        val sameHost = graduate.copy(url = "https://jw.example.edu.cn/grad")
        assertEquals(sameHost, match(sameHost.url, listOf(undergraduate, sameHost)))
        assertNull(match("https://jw.example.edu.cn/other", listOf(undergraduate, sameHost)))
    }

    @Test fun `unverified sibling cannot redirect to supported undergraduate entry`() {
        val undergraduate = JwSchoolInfo("T", "本科", "https://jw.example.edu.cn/", "qz")
        val graduate = undergraduate.copy(name = "研究生", url = "https://grad.example.edu.cn/",
            status = JwSchoolInfo.STATUS_GRAD_UNVERIFIED)
        assertNull(match(graduate.url, listOf(undergraduate, graduate)))
        assertNull(match("https://portal.example.edu.cn/", listOf(undergraduate, graduate)))
        assertEquals(undergraduate, match(undergraduate.url, listOf(undergraduate, graduate)))
        assertNull(match(graduate.url, listOf(graduate)))
    }

    @Test fun `non-web and credential-bearing urls never map`() {
        assertNull(match("file://jw.hfut.edu.cn/timetable"))
        assertNull(match("https://person@one.hfut.edu.cn/"))
    }

    private fun select(url: String, catalog: List<JwSchoolInfo>) =
        SchoolDomainMatch.selectionsForUrl(url, catalog, "zf_new", "Custom")

    @Test fun `typed exact unverified URL retains admission status and identity`() {
        val school = JwSchoolInfo("T", "测试大学", "https://jw.example.edu/login", "qz",
            status = JwSchoolInfo.STATUS_UNVERIFIED, catalogId = "test:one")
        val selected = select(school.url, listOf(school)).single()
        assertEquals(school, selected)
        assertEquals("qz", selected.type)
        org.junit.Assert.assertTrue(selected.isUnverified)
        org.junit.Assert.assertFalse(selected.isSupported)
        assertEquals(school, select("https://JW.EXAMPLE.EDU:443/login", listOf(school)).single())
        val pending = school.copy(status = JwSchoolInfo.STATUS_PENDING)
        org.junit.Assert.assertFalse(select(pending.url, listOf(pending)).single().canImport)
    }

    @Test fun `shared exact URL offers distinct identities without selecting a winner`() {
        val first = JwSchoolInfo("T", "本科", "https://jw.example.edu/", "qz", catalogId = "one")
        val second = first.copy(name = "研究生", catalogId = "two",
            status = JwSchoolInfo.STATUS_GRAD_UNVERIFIED)
        assertEquals(listOf(first, second), select(first.url, listOf(first, second)))
        assertEquals(listOf(second, first), select(first.url, listOf(second, first)))
    }

    @Test fun `unverified identity matching never expands to host path query or fragment`() {
        val school = JwSchoolInfo("T", "测试大学", "https://jw.example.edu/login?app=one#student", "qz",
            status = JwSchoolInfo.STATUS_UNVERIFIED, catalogId = "one")
        for (url in listOf("https://jw.example.edu/other", "https://portal.example.edu/",
            "https://jw.example.edu/login?app=two#student", "https://jw.example.edu/login?app=one#teacher")) {
            val selected = select(url, listOf(school)).single()
            assertEquals("Custom", selected.name)
            assertEquals(url, selected.url)
            org.junit.Assert.assertTrue(selected.canImport)
        }
    }

    @Test fun `typed custom unknown URL and supported portal fallback remain available`() {
        val custom = select("https://unknown.example.edu/", emptyList()).single()
        assertEquals("Custom", custom.name)
        org.junit.Assert.assertTrue(custom.canImport)
        val known = JwSchoolInfo("T", "大学", "https://jw.example.edu.cn/", "qz")
        assertEquals(known, select("https://portal.example.edu.cn/", listOf(known)).single())
    }

    @Test fun `URL row uses the same tested selection helper for display and click`() {
        val file = sequenceOf(
            File("src/main/java/com/lingion/sleepy/ui/screen/imports/SchoolSelectScreen.kt"),
            File("app/src/main/java/com/lingion/sleepy/ui/screen/imports/SchoolSelectScreen.kt"),
        ).first { it.isFile }
        val block = file.readText().substringAfter("if (isUrl) {").substringBefore("if (filtered.isEmpty()")
        org.junit.Assert.assertTrue("SchoolDomainMatch.selectionsForUrl(" in block)
        org.junit.Assert.assertTrue("val school = urlSelections.single()" in block)
        org.junit.Assert.assertTrue("items(urlSelections, key = { it.stableKey })" in block)
        org.junit.Assert.assertTrue("onSchoolSelected(school)" in block)
        org.junit.Assert.assertFalse("JwSchoolInfo(" in block)
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
