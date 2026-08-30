package com.lingion.sleepy.data.jw

import com.lingion.sleepy.data.jw.JwParseDiagnostics.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T9 诊断分类单测 — 覆盖六类失败语义 + 兜底。
 *
 * Fixture 从 /tmp/jw_fixtures/ 同步到 src/test/resources 的副本读取 (jw_fixtures/),
 * 字段断言：category、courseCount、matchedFeatures、userMessage 不含敏感数据。
 */
class JwParseDiagnosticsTest {

    private val testSchoolLinyi = JwSchoolInfo(
        sortKey = "L", name = "临沂大学", url = "http://jwgl.lyu.edu.cn/jwglxt",
        type = JwProtocol.TYPE_ZF_NEW, aliases = emptyList(), sortKeyFull = "linyidaxue"
    )
    private val testSchoolQz = JwSchoolInfo(
        sortKey = "H", name = "哈工程", url = "http://jw.hrbeu.edu.cn/jsxsd/",
        type = JwProtocol.TYPE_QZ_CRAZY, aliases = emptyList(), sortKeyFull = "haerbin"
    )

    private fun fixture(name: String): String =
        File("src/test/resources/jw_fixtures/detection-pages/$name").readText()

    private fun advFixture(group: String, name: String): String =
        File("src/test/resources/jw_fixtures/adversarial/$group/$name").readText()

    @Test
    fun `session expired 502 login page detected as SESSION_EXPIRED`() {
        val html = advFixture("login-expired", "zf_new_fetch_expired_returns_login.html")
        val diag = JwParseDiagnostics.classify(html, "https://jw.example.edu.cn/jwglxt/", testSchoolLinyi, emptyList())
        assertEquals("会话过期页应判为 SESSION_EXPIRED", Category.SESSION_EXPIRED, diag.category)
        assertEquals("0 课", 0, diag.courseCount)
        assertTrue("应命中登录页指纹", diag.matchedFeatures.any { it in listOf("login_slogin", "登录", "captcha", "viewstate") })
        assertFalse("userMessage 不得含学号/cookie", diag.userMessage.contains("JSESSIONID", ignoreCase = true))
        assertFalse("userMessage 不得含完整 HTML", diag.userMessage.contains("<html", ignoreCase = true))
        assertTrue("userMessage 应给出登录提示", diag.userMessage.contains("登录") || diag.userMessage.contains("会话"))
    }

    @Test
    fun `qiangzhi login page Logon_do classified as SESSION_EXPIRED`() {
        val html = advFixture("login-expired", "qz_sdust_logon_login.html")
        val diag = JwParseDiagnostics.classify(html, "http://jwgl.sdust.edu.cn/", testSchoolQz, emptyList())
        assertEquals(Category.SESSION_EXPIRED, diag.category)
        assertTrue("logon 路径应被命中", diag.matchedFeatures.contains("logon"))
    }

    @Test
    fun `blank html with no table container classified as NO_TABLE_CONTAINER`() {
        val html = advFixture("image-table", "blank_no_table.html")
        val diag = JwParseDiagnostics.classify(html, "https://jw.example.edu.cn/", testSchoolLinyi, emptyList())
        assertEquals("无任何容器 → NO_TABLE_CONTAINER", Category.NO_TABLE_CONTAINER, diag.category)
        assertTrue("matchedFeatures 应含 no_container", diag.matchedFeatures.contains("no_container"))
    }

    @Test
    fun `image cell zf table with img only classified as IMAGE_OR_EMPTY_CELLS`() {
        val html = advFixture("image-table", "zf_image_cell.html")
        val diag = JwParseDiagnostics.classify(html, "https://jw.example.edu.cn/xskbcx.aspx", testSchoolLinyi, emptyList())
        assertEquals("图片课表应判 IMAGE_OR_EMPTY_CELLS", Category.IMAGE_OR_EMPTY_CELLS, diag.category)
        assertTrue("应含 img_in_table 标签", diag.matchedFeatures.contains("img_in_table"))
    }

    @Test
    fun `empty semester page with explicit empty marker classified as EMPTY_SEMESTER`() {
        val html = advFixture("empty-semester", "unknown_protocol_all_parsers_empty.html")
        val diag = JwParseDiagnostics.classify(html, "https://jw.example.edu.cn/", testSchoolLinyi, emptyList())
        assertEquals("含'尚未产生课表数据'应判 EMPTY_SEMESTER", Category.EMPTY_SEMESTER, diag.category)
    }

    @Test
    fun `header without per-row node classified as HEADER_NO_NODE`() {
        // 构造: 容器在(Table1)但无任何 "第N节" 行头, 无 img, 无空课声明
        val html = """<html><body><table id="Table1"><tr><td>课程</td></tr></table></body></html>"""
        val diag = JwParseDiagnostics.classify(html, "https://jw.example.edu.cn/xskbcx.aspx", testSchoolLinyi, emptyList())
        assertEquals("容器在但无行头 → HEADER_NO_NODE", Category.HEADER_NO_NODE, diag.category)
    }

    @Test
    fun `pku elective datagrid classified correctly for PKU school`() {
        val html = fixture("pku-elective-datagrid.html")
        val pkuSchool = JwSchoolInfo(
            sortKey = "B", name = "北京大学", url = "https://elective.pku.edu.cn",
            type = JwProtocol.TYPE_PKU, aliases = emptyList(), sortKeyFull = "beida"
        )
        val diag = JwParseDiagnostics.classify(html, "https://elective.pku.edu.cn/", pkuSchool, emptyList())
        assertTrue("PKU datagrid 容器命中", diag.matchedFeatures.contains("datagrid"))
        assertFalse("PKU 学校不应判 WRONG_PROTOCOL", diag.category == Category.WRONG_PROTOCOL)
    }

    @Test
    fun `wrong protocol detected when school type qz but page has Table1`() {
        // 学校标 qz, 页面有 Table1 (zf) 而无 kbtable → WRONG_PROTOCOL
        val html = advFixture("cross-fp-parsers", "zf_old_table1_into_newzf.html")
        val diag = JwParseDiagnostics.classify(html, "https://jw.example.edu.cn/xskbcx.aspx", testSchoolQz, emptyList())
        assertEquals("qz 学校看到 zf 容器 → WRONG_PROTOCOL", Category.WRONG_PROTOCOL, diag.category)
        assertTrue("userMessage 应提及协议不一致", diag.userMessage.contains("不一致") || diag.userMessage.contains("切换"))
    }

    @Test
    fun `userMessage never contains sensitive data across all fixtures`() {
        val fixtures = listOf(
            advFixture("login-expired", "wisedu_cas_login_expired.html") to testSchoolLinyi,
            advFixture("login-expired", "bnuz_es_default_login.html") to
                JwSchoolInfo("B", "北师珠", "http://es.bnuz.edu.cn", JwProtocol.TYPE_BNUZ, aliases = emptyList(), sortKeyFull = "beishizhu"),
            advFixture("login-expired", "pku_iaaa_login_redirect.html") to
                JwSchoolInfo("B", "北大", "https://elective.pku.edu.cn", JwProtocol.TYPE_PKU, aliases = emptyList(), sortKeyFull = "beida"),
            advFixture("image-table", "qz_image_cell.html") to testSchoolQz,
        )
        val sensitivePatterns = listOf(
            Regex("""(?i)(sessionid|csrf|token|cookie)"""),
            Regex("""(?i)<html|<body|<script"""),
            Regex("""(?i)password"""),
        )
        for ((html, school) in fixtures) {
            val diag = JwParseDiagnostics.classify(html, school.url, school, emptyList())
            for (p in sensitivePatterns) {
                assertFalse("[$school] userMessage 不应含敏感数据: ${diag.userMessage.take(50)}", p.containsMatchIn(diag.userMessage))
            }
        }
    }
}
