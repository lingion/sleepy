package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwZtvtitExplicitPeriodTest {
    private fun parse(node: String) = JwZtvtitParser(
        "<table class='scheduleTable'><tr week='fri'><td lesson='$node'>" +
            "<div class='courseInfo'><span>专题</span><span class='place'>A101</span>" +
            "<span class='teacher'>王</span><span class='WeekDetail'>第1-3单周</span>" +
            "</div></td></tr></table>"
    ).generateCourseList()

    @Test fun `explicit high lesson preserves course fields`() {
        assertEquals(listOf(JwCourse("专题", "A101", "王", 5, 35, 35, 1, 3, 1)), parse("35"))
    }

    @Test fun `resource boundary remains a valid explicit lesson`() {
        assertEquals(listOf(JwCourse("专题", "A101", "王", 5, 366, 366, 1, 3, 1)), parse("366"))
    }

    @Test fun `invalid lesson cannot become a default course`() {
        for (node in listOf("0", "-1", "367", "2147483648", "待定", "")) {
            assertEquals(node, emptyList<JwCourse>(), parse(node))
        }
    }
}
