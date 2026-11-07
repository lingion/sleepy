package com.lingion.sleepy.data.jw

import org.junit.Assert.*
import org.junit.Test

class JwLegacyUrpXjuOccurrenceTest {
    private fun urp(weeks: String = "1,3,7-9周(单)", day: String = "星期三", nodes: String = "第十二小节", step: String = "二") =
        "<table id='tb'><thead><tr>" + listOf("课程名称", "代课教师", "周次", "星期", "节次", "节数", "教学楼", "教室")
            .joinToString("") { "<th>$it</th>" } + "</tr></thead><tbody><tr>" +
            listOf("数值分析", "张", weeks, day, nodes, step, "主楼", "301").joinToString("") { "<td>$it</td>" } +
            "</tr></tbody></table>"
    private fun xju(raw: String, span: String = "1") = "<table id='ctl00_contentParent_dgData'>" +
        "<tr><th>节次</th><th>星期日</th><th>星期一</th></tr><tr><td align='center'>1</td>" +
        "<td rowspan='$span'>$raw</td><td></td></tr></table>"
    private fun occurrences(courses: List<JwCourse>) = courses.flatMap { course ->
        (course.startWeek..course.endWeek).filter { course.type == 0 || it % 2 == course.type % 2 }.flatMap { week ->
            (course.startNode..course.endNode).map { node ->
                "${course.name}|${course.teacher}|${course.room}|${course.day}|$node|$week"
            }
        }
    }.sorted()

    @Test fun `legacy URP recognizes tb long headers Chinese nodes and sparse parity`() {
        assertEquals(listOf(1,3,7,9).flatMap { week -> listOf(12,13).map { "数值分析|张|主楼301|3|$it|$week" } }.sorted(),
            occurrences(JwUrpParser(urp()).generateCourseList()))
    }
    @Test fun `legacy URP big periods convert only their explicit node unit`() {
        assertEquals(listOf(JwCourse("数值分析","主楼301","张",1,5,6,4,4)),
            JwUrpParser(urp("4周","星期一","第三大节","2")).generateCourseList())
    }
    @Test fun `legacy URP unknown values never fall back to first day first node or twenty weeks`() {
        for (html in listOf(urp(""),urp("待定"),urp("1.5-3周"),urp(day="待定"),urp(nodes="待定"),urp(step="待定"))) {
            assertTrue(html,JwUrpParser(html).generateCourseList().isEmpty())
        }
    }
    @Test fun `legacy URP physical rowspans retain course and teacher without shifting arrangements`() {
        val html = urp("3周","星期四","1-2节","2").replace("<td>数值分析</td><td>张</td>",
            "<td rowspan='2'>数值分析</td><td rowspan='2'>张</td>").replace("</tbody>",
            "<tr><td>8周</td><td>星期日</td><td>5-6节</td><td>2</td><td>实验楼</td><td>201</td></tr></tbody>")
        assertEquals(listOf(JwCourse("数值分析","主楼301","张",4,1,2,3,3),
            JwCourse("数值分析","实验楼201","张",7,5,6,8,8)),JwUrpParser(html).generateCourseList())
    }
    @Test fun `XJU independent bracket segments preserve teacher room and parity`() {
        val html = xju("数学｛1-5周单[教师:甲,地点:A]、6-8周双[教师:乙,地点:B]｝")
        assertEquals(listOf(1,3,5).map { "数学|甲|A|7|1|$it" }.plus(listOf(6,8).map {
            "数学|乙|B|7|1|$it"
        }).sorted(),occurrences(JwXjuParser(html).generateCourseList()))
    }
    @Test fun `XJU invalid rowspan rejects instead of indexing beyond schedule rows`() {
        for (span in listOf("0","-1","1.5","5","999999")) {
            val parsed = runCatching { JwXjuParser(xju("数学｛3周[教师:甲,地点:A]｝",span)).generateCourseList() }
            assertTrue(span,parsed.isSuccess)
            assertTrue(span,parsed.getOrThrow().isEmpty())
        }
    }
    @Test fun `XJU unidentified plain text is not a default twenty week course`() {
        assertTrue(JwXjuParser(xju("数学 待定")).generateCourseList().isEmpty())
    }
    @Test fun `XJU malformed week segment never borrows fields or extracts embedded digits`() {
        assertTrue(JwXjuParser(xju("数学｛1.5-3周[教师:甲,地点:A]｝")).generateCourseList().isEmpty())
    }
}
