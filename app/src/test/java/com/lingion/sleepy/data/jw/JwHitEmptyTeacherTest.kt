package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwHitEmptyTeacherTest {
    @Test fun preservesSourceArrangementWithoutTeacherOrRoom() {
        assertEquals(listOf(JwCourse("数学", "A", "", 1, 1, 2, 1, 8)), parse("数学<br>[1-8]周A"))
        assertEquals(listOf(JwCourse("数学", "", "", 1, 1, 2, 1, 8)), parse("数学<br>[1-8]周"))
    }

    @Test fun keepsTeachersScopedToEachArrangementAndRequiresCourseName() {
        assertEquals(listOf(JwCourse("数学", "A", "", 1, 1, 2, 1, 8),
            JwCourse("数学", "B", "李老师", 1, 1, 2, 9, 16)),
            parse("数学<br>[1-8]周A，李老师[9-16]周B"))
        assertEquals(emptyList<JwCourse>(), parse("[1-8]周A"))
        assertEquals(emptyList<JwCourse>(), parse("数学<br>[8-1]周A"))
    }

    private fun parse(card: String) = JwHitParser("<table class='xfyq_con'><tr><td>上午</td><td>第1-2节</td>" +
        "<td>$card</td>" + "<td></td>".repeat(6) + "</tr></table>").generateCourseList()
}
