package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwHnjmNodeBoundsTest {
    @Test fun keepsExplicitPeriodsBeyondDefaultClockTableWithoutInventingClocks() {
        for ((first, last) in listOf(11 to 12, 9 to 12, 31 to 32, 366 to 366)) assertEquals(
            listOf(JwCourse("数学", "101", "王老师", 2, first, last, 1, 8)), parse(first, last))
    }

    @Test fun keepsCompleteDefaultClocksAndRejectsInvalidNodes() {
        assertEquals(listOf(JwCourse("数学", "101", "王老师", 2, 1, 2, 1, 8,
            startTime = "08:10", endTime = "09:50")), parse(1, 2))
        for ((first, last) in listOf(0 to 1, 2 to 1, 367 to 367, 366 to 367))
            assertEquals(emptyList<JwCourse>(), parse(first, last))
    }

    private fun parse(first: Int, last: Int) = JwSchoolTableParser(
        "<div class='Sub-kcbt'>星期二</div><table><tr><td>$first-$last 节</td>" +
            "<td>数学 1-8周 (101)</td><td>王老师</td></tr></table>", "hnjm").generateCourseList()
}
