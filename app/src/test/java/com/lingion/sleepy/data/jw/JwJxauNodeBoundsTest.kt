package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwJxauNodeBoundsTest {
    @Test fun preservesHighColumnsAfterBothSeparatorsAndEmptyCells() {
        for (group in listOf(16, 183)) assertEquals(
            listOf(JwCourse("数学", "A", "王老师", 2, group * 2 - 1, group * 2, 1, 8)), parse(group))
    }

    @Test fun rejectsColumnsBeyondResourceBoundary() = assertEquals(emptyList<JwCourse>(), parse(184))

    private fun parse(group: Int): List<JwCourse> {
        val cells = (0 until group + 2).joinToString("") { index ->
            if (index == group + 1) "<td><dl><a>数学</a><dd>王老师</dd><dd>1-8(周)</dd><b>A</b></dl></td>" else "<td></td>"
        }
        return JwJxauParser("<table id='KebiaoTable1'><tr></tr><tr></tr><tr><td class='left1'>星期二</td>" +
            cells + "</tr></table>").generateCourseList()
    }
}
