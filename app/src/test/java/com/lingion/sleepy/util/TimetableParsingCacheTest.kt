package com.lingion.sleepy.util

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors

class TimetableParsingCacheTest {
    private fun json(start: String) = """[{"node":1,"start":"$start","end":"10:00"}]"""

    @Test fun repeatedReadsReuseParsedTimetable() {
        assertSame(TimeTableUtils.parseNodes(json("08:00")), TimeTableUtils.parseNodes(json("08:00")))
        assertSame(TimeTableUtils.parseTimeSlotRows(json("08:00")), TimeTableUtils.parseTimeSlotRows(json("08:00")))
    }

    @Test fun editedTimetableAndDifferentTablesNeverShareStaleTimes() {
        repeat(50) {
            assertEquals("08:00", TimeTableUtils.courseTimeParts(1, 1, json("08:00"))!!.first)
            assertEquals("09:00", TimeTableUtils.courseTimeParts(1, 1, json("09:00"))!!.first)
            assertNull(TimeTableUtils.courseTimeParts(1, 1, "malformed"))
        }
    }

    @Test fun evictionDoesNotChangeResults() {
        val original = TimeTableUtils.parseNodes(json("08:00"))
        repeat(100) { TimeTableUtils.parseNodes(json("09:00") + " ".repeat(it)) }
        assertEquals(original, TimeTableUtils.parseNodes(json("08:00")))
        assertNotSame("缓存容量必须受限", original, TimeTableUtils.parseNodes(json("08:00")))
    }

    @Test fun callersCannotMutateSharedParsedNodes() {
        val parsed = TimeTableUtils.parseNodes(json("08:00"))
        try { (parsed as MutableList).clear() } catch (_: UnsupportedOperationException) { }
        assertEquals(1, TimeTableUtils.parseNodes(json("08:00")).size)
    }

    @Test fun editingRowsDoesNotCorruptOtherConsumers() {
        val rows = TimeTableUtils.parseTimeSlotRows(json("08:00"))
        try { (rows as MutableList).clear() } catch (_: UnsupportedOperationException) { }
        assertEquals("08:00" to "10:00", TimeTableUtils.effectiveCourseTime(false, "", "", 1, 1, json("08:00")))
        assertEquals(12, TimeTableUtils.parseTimeSlotRows("malformed").size)
        assertEquals(emptyList<TimeTableUtils.TimeSlotRow>(), TimeTableUtils.parseTimeSlotRows("[]"))
    }

    @Test fun uiWidgetAndReminderThreadsCanReadDifferentTablesConcurrently() {
        val pool = Executors.newFixedThreadPool(4)
        try {
            val jobs = (0..39).map { index ->
                pool.submit<Boolean> {
                    val start = if (index % 2 == 0) "08:00" else "09:00"
                    (1..100).all { TimeTableUtils.courseTimeParts(1, 1, json(start))!!.first == start }
                }
            }
            assertTrue(jobs.all { it.get() })
        } finally { pool.shutdownNow() }
    }
}
