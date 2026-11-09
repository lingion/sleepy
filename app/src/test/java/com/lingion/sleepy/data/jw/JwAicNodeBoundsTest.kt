package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwAicNodeBoundsTest {
    private fun table(node: Int) = buildString {
        append("<table id=\"table\"><tr><td>节次</td><td>星期一</td></tr>")
        for (index in 1..node) {
            append("<tr><td>$index</td><td>")
            if (index == node) append("<div class=\"courseInfo\"><b>实验</b><span class=\"weekDetail\">第2-3周</span>" +
                "<span class=\"teacher\">张老师</span><span class=\"place\">实训楼</span></div>")
            append("</td></tr>")
        }
        append("</table><script>getScheduleNew.do</script>")
    }

    @Test fun `semester grid preserves actual row thirty one after empty rows`() {
        assertEquals(listOf(JwCourse("实验", "实训楼", "张老师", 1, 31, 31, 2, 3)),
            JwAicParser(table(31)).generateCourseList())
    }

    @Test fun `last bounded row is preserved and oversized row is rejected`() {
        assertEquals(listOf(JwCourse("实验", "实训楼", "张老师", 1, 366, 366, 2, 3)),
            JwAicParser(table(366)).generateCourseList())
        assertEquals(emptyList<JwCourse>(), JwAicParser(table(367)).generateCourseList())
    }
}
