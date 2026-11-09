package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwSourcePeriodRowsTest {
    @Test fun completeSourcePeriodsSurviveWhenCoursesEndAtFourthPeriod() {
        val declared = listOf("08:30-09:15", "09:25-10:10", "10:30-11:15", "11:25-12:10",
            "12:20-13:05", "13:05-13:50", "14:00-14:45", "14:55-15:40", "16:00-16:45",
            "16:55-17:40", "19:00-19:45", "19:55-20:40", "20:40-21:25")
            .mapIndexed { index, time -> Triple(index + 1, time.substringBefore('-'), time.substringAfter('-')) }

        val rows = JwSourcePeriods.confirmationRows(4, declared)

        assertEquals(13, rows.size)
        assertEquals(declared, rows)
        assertEquals(Triple(13, "20:40", "21:25"), rows.last())
    }

    @Test fun capturedTailUsesItsNodeNumberRatherThanNumberOfCapturedRows() {
        val captured = listOf(Triple(1, "08:00", "08:45"), Triple(13, "20:40", "21:25"))

        val rows = JwSourcePeriods.confirmationRows(2, captured)

        assertEquals((1..13).toList(), rows.map { it.first })
        assertEquals(Triple(2, "", ""), rows[1])
        assertEquals(captured.last(), rows.last())
    }

    @Test fun laterCourseStillGetsAnEditableRowWithoutFabricatedTime() {
        val rows = JwSourcePeriods.confirmationRows(4, listOf(Triple(1, "08:00", "08:45")))

        assertEquals((1..4).toList(), rows.map { it.first })
        assertEquals(Triple(1, "08:00", "08:45"), rows.first())
        assertEquals(Triple(4, "", ""), rows.last())
    }

    @Test fun noSourceClocksRetainOnlyCourseRange() {
        assertEquals(listOf(Triple(1, "", ""), Triple(2, "", "")),
            JwSourcePeriods.confirmationRows(2, emptyList()))
        assertTrue(JwSourcePeriods.confirmationRows(0, emptyList()).isEmpty())
    }

    @Test fun sourceOrderCannotChangePrintedNodeOrder() {
        val rows = JwSourcePeriods.confirmationRows(1,
            listOf(Triple(3, "10:00", "10:45"), Triple(1, "08:00", "08:45")))

        assertEquals(listOf(Triple(1, "08:00", "08:45"), Triple(2, "", ""), Triple(3, "10:00", "10:45")), rows)
    }
}
