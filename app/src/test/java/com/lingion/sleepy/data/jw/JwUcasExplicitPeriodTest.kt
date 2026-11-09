package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwUcasExplicitPeriodTest {
    private fun detail(nodes: String) = "<div class='mc-body'><p>课程名称：专题</p><table>" +
        "<tr><th>上课时间</th><td>星期一： 第${nodes}节。</td></tr>" +
        "<tr><th>上课地点</th><td>A101</td></tr><tr><th>上课周次</th><td>1、3</td></tr>" +
        "</table></div>"

    private fun grid(nodes: List<Int>) = "<table><thead><tr><th>节次/星期</th></tr></thead><tbody>" +
        nodes.joinToString("") { "<tr><th>$it</th><td><a href='/course/coursetime/1'>专题</a></td></tr>" } +
        "</tbody></table>"

    private fun combined(nodes: List<Int>, declaredNodes: String) = grid(nodes) +
        JwUcasParser.DETAIL_MARKER_OPEN + JwUcasParser.DETAIL_HOST + "/course/coursetime/1-->" +
        detail(declaredNodes) + JwUcasParser.DETAIL_MARKER_CLOSE

    @Test fun `standalone details preserve sparse high lessons without filling holes`() {
        assertEquals(listOf(35, 37).map { JwCourse("专题", "A101", "", 1, it, it, 1, 3, 1) },
            JwUcasParser(detail("35、37")).generateCourseList())
    }

    @Test fun `grid retains only its explicit high lessons within detail range`() {
        assertEquals(listOf(35, 37).map { JwCourse("专题", "A101", "", 1, it, it, 1, 3, 1) },
            JwUcasParser(combined(listOf(35, 37), "35-37")).generateCourseList())
    }

    @Test fun `both HTML paths accept resource boundary`() {
        val expected = listOf(JwCourse("专题", "A101", "", 1, 366, 366, 1, 3, 1))
        assertEquals(expected, JwUcasParser(detail("366")).generateCourseList())
        assertEquals(expected, JwUcasParser(combined(listOf(366), "366")).generateCourseList())
    }

    @Test fun `invalid details cannot supply provisional grid weeks`() {
        for (nodes in listOf("0", "-1", "367", "35-34", "2147483648", "35、待定")) {
            assertEquals(nodes, emptyList<JwCourse>(), JwUcasParser(detail(nodes)).generateCourseList())
            assertEquals(nodes, emptyList<JwCourse>(), JwUcasParser(combined(listOf(35), nodes)).generateCourseList())
        }
    }
}
