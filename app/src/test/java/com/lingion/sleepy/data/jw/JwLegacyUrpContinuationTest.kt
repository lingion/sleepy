package com.lingion.sleepy.data.jw

import org.junit.Assert.*
import org.junit.Test

class JwLegacyUrpContinuationTest {
    private fun routes(type: String, source: String): List<JwCourse> {
        val courses = JwParserRegistry.parserFor(type, source).generateCourseList()
        assertEquals(courses, JwParserRegistry.selectBest(source, type).first)
        assertEquals(courses, JwImportViewModel.parseHtmlStatic(source, type).first)
        if (source.trimStart().startsWith("<")) assertDeclaredHtmlFrame(type, source, courses)
        val draft = JwImportDraftSnapshot(JwSchoolInfo("T", "显式课次回归", type=type), courses, emptyList())
        assertEquals(courses, JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!.courses)
        return courses
    }
    private fun expected(first: Int = 1, last: Int = 2, from: Int = 1, to: Int = 2) =
        listOf(JwCourse("课程甲", "A101", "甲老师", 1, first, last, from, to))

    private fun urp(first: String = "1", length: String = "2", credit: String = "2.5", continuation: String = "") =
        "<table class='displayTag'><thead><tr>" + listOf("课程名称","教师","学分","周次","星期","节次","节数","教学楼","教室")
            .joinToString("") { "<th>$it</th>" } + "</tr></thead><tbody><tr>" +
            listOf("课程甲","甲老师",credit,"1-2周","星期一",first,length,"A1","01").joinToString("") { "<td>$it</td>" } +
            "</tr>$continuation</tbody></table>"
    @Test fun `legacy URP explicit start above thirty retains course fields`() {
        assertEquals(expected(31,32).map { it.copy(credit=2.5f) }, routes("urp", urp(first="31")))
    }
    @Test fun `legacy URP explicit duration above thirty retains course fields`() {
        assertEquals(expected(1,31).map { it.copy(credit=2.5f) }, routes("urp", urp(length="31")))
    }
    @Test fun `legacy URP full row preserves credit`() {
        assertEquals(expected().map { it.copy(credit=2.5f) }, routes("urp", urp()))
    }
    @Test fun `legacy URP compressed continuation inherits only declared course metadata`() {
        val continuation = "<tr>" + listOf("5周","星期四","7","2","B2","02").joinToString("") { "<td>$it</td>" } + "</tr>"
        assertEquals(expected().map { it.copy(credit=2.5f) } + JwCourse("课程甲","B202","甲老师",4,7,8,5,5,credit=2.5f),
            routes("urp", urp(continuation=continuation)))
    }
    @Test fun `legacy URP cannot populate compressed rows without a preceding full course`() {
        val html = urp().replace(Regex("<tbody>.*</tbody>"), "<tbody><tr>" +
            listOf("5周","星期四","7","2","B2","02").joinToString("") { "<td>$it</td>" } + "</tr></tbody>")
        assertTrue(routes("urp", html).isEmpty())
    }
}
