package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic vectors from 6.6.2 o00000O0, including its date floor division and HHmm conversion. */
class JwEcuplOccurrenceParityTest {
    private fun page(date: String = "2026-09-07", bitmap: String = "42", metadata: String = "") = """
        $metadata<script language="JavaScript">
        new CourseTable('2026-09-07',[[800,845],[900,945],[1900,1945],[1955,2040]]);
        var activity=newActivity("T1","甲老师","C01","法学(实验)(C01)","R1","A101","$date",$bitmap);
        addActivityByTime(activity,2,1900,2040);
        addActivityByTime(activity,5,800,945);
        </script>
    """.trimIndent()

    private fun assertRoutes(source: String, expected: List<JwCourse>) {
        assertEquals(expected, JwEcuplScriptParser(source).generateCourseList())
        assertEquals(expected, JwClassicEamsParser(source).generateCourseList())
        assertEquals(expected, JwParserRegistry.selectBest(source).first)
        assertEquals(expected, JwImportViewModel.parseHtmlStatic(source, JwProtocol.TYPE_CLASSIC_EAMS).first)
        assertEquals(expected, JwImportViewModel.parseHtmlStatic(source, JwProtocol.TYPE_ECUPL).first)
    }

    @Test fun `HHmm evening times and zero based decimal weeks agree across every import route`() {
        assertRoutes(page(), listOf(
            JwCourse("法学(实验)", "A101", "甲老师", 2, 3, 4, 1, 5, 1, startTime="19:00", endTime="20:40"),
            JwCourse("法学(实验)", "A101", "甲老师", 5, 1, 2, 1, 5, 1, startTime="08:00", endTime="09:45"),
        ))
    }

    @Test fun `date offset floors partial negative weeks`() {
        val expected = listOf(
            JwCourse("法学(实验)", "A101", "甲老师", 2, 3, 4, 1, 3, 1, startTime="19:00", endTime="20:40"),
            JwCourse("法学(实验)", "A101", "甲老师", 5, 1, 2, 1, 3, 1, startTime="08:00", endTime="09:45"),
        )
        assertRoutes(page("2026-09-06", "20"), expected)
    }

    @Test fun `metadata headers preserve name credit teacher and location note`() {
        val metadata = """<table class="listTable"><tr><td>学分</td><td>备注</td><td>课程序号</td><td>教师</td><td>课程名称</td></tr>
            <tr><td>2.5</td><td>地点调整为B楼</td><td>C01</td><td>乙老师</td><td>法学实训</td></tr></table>"""
        val courses = JwEcuplScriptParser(page(metadata=metadata)).generateCourseList()
        assertEquals(listOf(
            JwCourse("法学实训", "A101 (地点调整为B楼)", "甲老师 (乙老师)", 2, 3, 4, 1, 5, 1,
                startTime="19:00", endTime="20:40", credit=2.5f),
            JwCourse("法学实训", "A101 (地点调整为B楼)", "甲老师 (乙老师)", 5, 1, 2, 1, 5, 1,
                startTime="08:00", endTime="09:45", credit=2.5f),
        ), courses)
    }

    @Test fun `invalid HHmm minutes and unbound period do not invent lessons`() {
        assertEquals(emptyList<JwCourse>(), JwEcuplScriptParser(page().replace("845", "899")).generateCourseList())
        assertEquals(emptyList<JwCourse>(), JwClassicEamsParser(page().replace("[[800,845],[900,945],[1900,1945],[1955,2040]]", "[]")).generateCourseList())
    }

    @Test fun `complete object method page without language attribute preserves schedule`() {
        val source = page().replace(" language=\"JavaScript\"", "")
            .replace("var activity=newActivity", "var activity=table0.newActivity")
            .replace("addActivityByTime(", "table0.addActivityByTime(")
        assertRoutes(source, listOf(
            JwCourse("法学(实验)", "A101", "甲老师", 2, 3, 4, 1, 5, 1, startTime="19:00", endTime="20:40"),
            JwCourse("法学(实验)", "A101", "甲老师", 5, 1, 2, 1, 5, 1, startTime="08:00", endTime="09:45"),
        ))
    }
}
