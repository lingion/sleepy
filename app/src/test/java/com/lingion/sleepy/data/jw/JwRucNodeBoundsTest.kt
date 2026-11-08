package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwRucNodeBoundsTest {
    @Test fun keepsExplicitPeriodsAboveThirtyAndCrossingThirty() {
        for ((first, length, last) in listOf(Triple(31, 2, 32), Triple(30, 3, 32), Triple(2, 31, 32))) {
            assertEquals(listOf(JwCourse("数学", "A", "王老师", 2, first, last, 1, 8)), parse(first, length))
        }
    }

    @Test fun resourceBoundaryPreservesLastPeriodAndRejectsInvalidSpans() {
        assertEquals(listOf(JwCourse("数学", "A", "王老师", 2, 366, 366, 1, 8)), parse(366, 1))
        for ((first, length) in listOf(367 to 1, 366 to 2, 1 to 367, 0 to 1, 1 to 0, -1 to 1,
            Int.MAX_VALUE to 2, 1 to Int.MAX_VALUE)) {
            assertEquals("first=$first length=$length", emptyList<JwCourse>(), parse(first, length))
        }
    }

    private fun parse(first: Int, length: Int) = JwRucParser("""{
        "schoolHours": [], "course": [{"title":"数学","place":"地点：A","teacher":"教师：王老师",
        "week":"2","start":"$first","quittingTime":"$length","weekly":"1-8周"}]
    }""").generateCourseList()
}
