package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwSduNodeBoundsTest {
    @Test fun keepsDeclaredSinglePeriodsAboveThirty() {
        for (node in listOf(31, 366)) {
            assertEquals(listOf(JwCourse("数学", "A", "王老师", 2, node, node, 1, 8)), parse(node))
        }
    }

    @Test fun rejectsInvalidAndExcessivePeriods() {
        for (node in listOf(0, -1, 367, Int.MAX_VALUE)) assertEquals(emptyList<JwCourse>(), parse(node))
    }

    private fun parse(node: Int): List<JwCourse> {
        val cells = listOf("序号", "课程号", "数学", "", "", "", "", "王老师", "1-8周", "2", "$node", "A")
        return JwSduParser("<table id='ysjddDataTableId'><tr><th>表头</th></tr><tr>" +
            cells.joinToString("") { "<td>$it</td>" } + "</tr></table>").generateCourseList()
    }
}
