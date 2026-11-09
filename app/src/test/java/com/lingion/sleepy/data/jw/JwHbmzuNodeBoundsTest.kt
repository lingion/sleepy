package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwHbmzuNodeBoundsTest {
    @Test fun preservesHighPhysicalRowsAndEmptyRows() {
        for (group in listOf(16, 183)) assertEquals(
            listOf(JwCourse("数学", "A", "王老师", 1, group * 2 - 1, group * 2, 1, 8)), parse(group))
    }

    @Test fun rejectsRowsBeyondResourceBoundary() = assertEquals(emptyList<JwCourse>(), parse(184))

    private fun parse(group: Int): List<JwCourse> = JwHbmzuParser("<table class='list_table tac'>" +
        "<tr><td></td></tr>".repeat(group - 1) +
        "<tr><td><p><span>数学</span><span>1-8周</span><span>A</span><span>王老师</span></p></td></tr></table>")
        .generateCourseList()
}
