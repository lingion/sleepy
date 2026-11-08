package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwScauIndexedFallbackTest {
    private fun oldTable(rows: String, border: String = "#000000") =
        "<table border='1' bordercolor='$border'><tr><th>课程表</th></tr>" +
            "<tr><th>星期一</th><th>星期二</th></tr>$rows</table>"

    private fun assertRoutes(html: String, expected: List<JwCourse>) {
        assertEquals(expected, JwScauParser(html).generateCourseList())
        assertEquals(expected, JwParserRegistry.parserFor("scau", html).generateCourseList())
        assertEquals(expected, JwParserRegistry.selectBest(html, "scau").first)
    }

    @Test fun `indexed old table reads room before weeks and retains empty rows`() {
        val html = oldTable("<tr><td valign='top'></td><td valign='top'></td></tr>" +
            "<tr><td>下午</td><td valign='top'></td><td valign='top'>数学：李老师<br>A101<br>1-3,5周</td></tr>")
        assertRoutes(html, listOf(
            JwCourse("数学", "A101", "李老师", 2, 3, 4, 1, 3),
            JwCourse("数学", "A101", "李老师", 2, 3, 4, 5, 5),
        ))
    }

    @Test fun `indexed old table name suffix overrides weeks while third line supplies parity`() {
        val html = oldTable("<tr><td valign='top'>机器学习 1-5,9-11：王老师<br>B202<br>1-20周单</td></tr>")
        assertRoutes(html, listOf(
            JwCourse("机器学习", "B202", "王老师", 1, 1, 2, 1, 5, 1),
            JwCourse("机器学习", "B202", "王老师", 1, 1, 2, 9, 11, 1),
        ))
    }

    @Test fun `indexed old table reads parity after the week suffix without a name override`() {
        val html = oldTable("<tr><td valign=top>数学：李老师<br>A101<br>1-5,9-11周单</td>" +
            "<td valign=top>物理：王老师<br>B202<br>1-6周双</td></tr>")
        assertRoutes(html, listOf(
            JwCourse("数学", "A101", "李老师", 1, 1, 2, 1, 5, 1),
            JwCourse("数学", "A101", "李老师", 1, 1, 2, 9, 11, 1),
            JwCourse("物理", "B202", "王老师", 2, 1, 2, 2, 6, 2),
        ))
    }

    @Test fun `indexed fallback does not take over paired section layout`() {
        val html = "<table border=1 bordercolor='#000000'><tr><td valign=top>2节)</td></tr>" +
            "<tr><td valign=top>数学：李老师<br>1-3,5周<br>A101</td></tr></table>"
        assertRoutes(html, listOf(
            JwCourse("数学", "A101", "李老师", 1, 3, 4, 1, 3),
            JwCourse("数学", "A101", "李老师", 1, 3, 4, 5, 5),
        ))
    }

    @Test fun `indexed fallback rejects malformed weeks and other border tables`() {
        for (html in listOf(
            oldTable("<tr><td valign=top>错误课：教师<br>A101<br>3-1周</td></tr>"),
            oldTable("<tr><td valign=top>错误课：教师<br>A101<br>1-5周</td></tr>", "#ffffff"),
        )) assertTrue(JwScauParser(html).generateCourseList().isEmpty())
    }
}
