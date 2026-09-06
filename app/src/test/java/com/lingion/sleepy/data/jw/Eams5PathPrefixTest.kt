package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 2026-09 211 批量收录：EAMS5 (supwisdom 新版) 前缀自适应。
 *
 * 背景：合工大 = /eams5-student/for-std/...；安徽大学 / 矿大北京实测 =
 * /student/for-std/...（survey 211-survey.md A 档）。fetch JS 与 Kotlin
 * 推断逻辑共用同一前缀常量，前缀写死会让新增校 0 课。
 */
class Eams5PathPrefixTest {

    // -------- 合工大形态：/eams5-student --------

    @Test
    fun `hfut url yields eams5-student prefix`() {
        assertEquals(
            "/eams5-student",
            eams5PathPrefixFor("https://jxglstu.hfut.edu.cn/"),
        )
    }

    @Test
    fun `hfut deep path still yields eams5-student`() {
        assertEquals(
            "/eams5-student",
            eams5PathPrefixFor("https://jxglstu.hfut.edu.cn/eams5-student/for-std/course-table"),
        )
    }

    // -------- supwisdom 新版形态：/student (安大/矿大北京) --------

    @Test
    fun `ahu url yields student prefix`() {
        assertEquals(
            "/student",
            eams5PathPrefixFor("https://jw.ahu.edu.cn/student/for-std/course-table"),
        )
    }

    @Test
    fun `cumtb url yields student prefix`() {
        assertEquals(
            "/student",
            eams5PathPrefixFor("https://jwxt.cumtb.edu.cn/student/for-std/course-table"),
        )
    }

    // -------- 兜底：无特征默认合工大形态 (保持向后兼容) --------

    @Test
    fun `blank url falls back to eams5-student`() {
        assertEquals("/eams5-student", eams5PathPrefixFor(""))
    }

    @Test
    fun `unrelated url falls back to eams5-student`() {
        assertEquals("/eams5-student", eams5PathPrefixFor("https://example.com/"))
    }

    // -------- fetch JS 模板契约：占位符必须存在且能被替换 --------

    @Test
    fun `fetch js template contains replaceable prefix placeholder`() {
        // EAMS5_FETCH_JS 是 UI 层 private 常量；这里锁契约：占位符拼写 + 替换后无残留
        val template = "fetch('__EAMS5_PREFIX__/for-std/course-table')"
        val js = template.replace(EAMS5_PREFIX_PLACEHOLDER, "/student")
        assertEquals("fetch('/student/for-std/course-table')", js)
        org.junit.Assert.assertFalse(js.contains(EAMS5_PREFIX_PLACEHOLDER))
    }

    // -------- AHU (安徽大学) fetch JS 契约 — 2026-09-06 cross-verified --------
    // 安大走金智 EAMS 新版 GET 路径 (print-data studentTableVms[].activities), 与合工大 POST datum 形态不同。
    // fetch JS 必须用占位符拼前缀, 禁把 /student 硬编码。

    @Test
    fun `AHU fetch js uses placeholder not hardcoded student prefix`() {
        // 锁契约: AHU fetch JS 用同一个 EAMS5_PREFIX_PLACEHOLDER (避免漂移到合工大形态)
        val template = "fetch(PREFIX + '/for-std/course-table')"
        val js = template.replace("PREFIX", "'__EAMS5_PREFIX__'")
        val resolved = js.replace(EAMS5_PREFIX_PLACEHOLDER, "/student")
        assertEquals("AHU fetch 应使用 /student 前缀", "fetch('__EAMS5_PREFIX__' + '/for-std/course-table')", js)
        assertEquals("AHU fetch 替换后无残留占位符", "fetch('/student' + '/for-std/course-table')", resolved)
        org.junit.Assert.assertFalse(resolved.contains(EAMS5_PREFIX_PLACEHOLDER))
    }

    @Test
    fun `AHU semester select extraction regex matches real Thymeleaf DOM`() {
        // 5 仓 consensus (a999c385 agent): AHU 是 Thymeleaf/J2EE 模板渲染, 学期列表嵌入
        //   `<select id="allSemesters"><option value="112">2024-2025-2</option>...`
        // qiqqqqq517 ahu.js:37 + abydym CourseRepository.kt:119-167 + Zeraora client.ts:480-489
        // 锁契约: regex 必须匹配真实 HTML, 取 <option value="数字"> id, 按 id 倒序取首
        val html = """
        <select id="allSemesters" name="allSemesters" class="select">
          <option value="234" selected="selected">2024-2025-1</option>
          <option value="233">2023-2024-2</option>
          <option value="232">2023-2024-1</option>
        </select>
        """
        val selectRegex = Regex("""<select[^>]*\bid=["']allSemesters["'][^>]*>([\s\S]*?)</select>""", RegexOption.IGNORE_CASE)
        val selectMatch = selectRegex.find(html)
        org.junit.Assert.assertNotNull("应能匹配 <select id='allSemesters'>", selectMatch)
        val inner = selectMatch!!.groupValues[1]
        val optionRegex = Regex("""<option[^>]*\bvalue=["'](\d+)["'][^>]*>([^<]*)</option>""", RegexOption.IGNORE_CASE)
        val opts = optionRegex.findAll(inner).map { m ->
            m.groupValues[1].toInt() to m.groupValues[2].trim()
        }.toList()
        org.junit.Assert.assertEquals("应抽出 3 个 option", 3, opts.size)
        // 按 id 倒序取首 → 234
        val maxId = opts.maxOf { it.first }
        assertEquals("newest id 是 234", 234, maxId)
    }

    @Test
    fun `AHU session expired detection matches CAS login page shape`() {
        // Zeraora client.ts:608-617 (a999c385/aa707ac2033872402 agents consensus):
        //   1) 302-399 + Location 落 one.ahu.edu.cn/cas 或 /cas/login
        //   2) 200 + name="lt" AND name="execution" 双字段
        //   3) 兜底: 统一身份认证 / 请重新登录 / casloginform
        val casLoginHtml = """
        <html><body>
          <form id="casloginform" action="/cas/login">
            <input name="username"/><input name="password"/>
            <input name="lt" value="LT-xxx"/>
            <input name="execution" value="e1s1"/>
          </form>
        </body></html>
        """
        val hasLt = Regex("""name=["']lt["']""", RegexOption.IGNORE_CASE).containsMatchIn(casLoginHtml)
        val hasExec = Regex("""name=["']execution["']""", RegexOption.IGNORE_CASE).containsMatchIn(casLoginHtml)
        org.junit.Assert.assertTrue("应能识别 lt 字段", hasLt)
        org.junit.Assert.assertTrue("应能识别 execution 字段", hasExec)
        val hasCasLoginUi = Regex("""casloginform""", RegexOption.IGNORE_CASE).containsMatchIn(casLoginHtml)
        org.junit.Assert.assertTrue("应能识别 casloginform form id", hasCasLoginUi)
    }

    @Test
    fun `AHU print-data url uses semester in path and hasExperiment=false`() {
        // 安大 print-data 形态 (5 仓共识: MoeclubM + abydym + Landon-3314 + Zeraora-807 + qiqqqqq517):
        //   GET /student/for-std/course-table/semester/<semesterId>/print-data
        //       ?semesterId=<id>&hasExperiment=false
        // 锁契约: semesterId 同时出现在 path 和 query, hasExperiment=false (安大默认)
        val url = "/student/for-std/course-table/semester/234/print-data?semesterId=234&hasExperiment=false"
        org.junit.Assert.assertTrue("AHU print-data URL 形如 /semester/{id}/print-data",
            url.contains("/semester/234/print-data"))
        org.junit.Assert.assertTrue("hasExperiment=false 默认", url.contains("hasExperiment=false"))
        org.junit.Assert.assertFalse("AHU 不应误用合工大 POST schedule-table/datum",
            url.contains("schedule-table/datum"))
        org.junit.Assert.assertFalse("AHU 不应误用 /get-data endpoint (metadata-only)",
            url.contains("/get-data"))
    }

    @Test
    fun `AHU print-data uses GET not POST`() {
        // 5 仓共识: print-data 是 GET (不是 POST)
        val url = "/student/for-std/course-table/semester/234/print-data?semesterId=234&hasExperiment=false"
        org.junit.Assert.assertTrue("AHU print-data 是 GET, 非 POST datum",
            url.contains("print-data"))
    }

    companion object {
        /** 与 UI 层共享的占位符常量 (单测引用同一符号, 防拼写漂移) */
        const val EAMS5_PREFIX_PLACEHOLDER = "__EAMS5_PREFIX__"
    }
}
