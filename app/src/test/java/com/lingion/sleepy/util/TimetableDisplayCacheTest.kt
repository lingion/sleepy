package com.lingion.sleepy.util

import org.junit.Assert.*
import org.junit.Test

class TimetableDisplayCacheTest {
    private fun timetable(start: String) =
        """[{"node":1,"start":"$start","end":"09:00"}]"""

    @Test fun repeatedDisplayReadsReuseResults() {
        val json = timetable(" 08:00 ")
        assertSame(TimeTableUtils.timeSlotsFor(json), TimeTableUtils.timeSlotsFor(json))
        assertSame(TimeTableUtils.sanitizeTimeJsonForDisplay(json), TimeTableUtils.sanitizeTimeJsonForDisplay(json))
        val invalid = timetable("10:00")
        assertSame(TimeTableUtils.validateTimeJson(invalid), TimeTableUtils.validateTimeJson(invalid))
    }

    @Test fun editingClockInvalidatesDisplayAndValidation() {
        val first = timetable("08:00")
        val changed = timetable("08:30")
        assertNotEquals(TimeTableUtils.timeSlotsFor(first), TimeTableUtils.timeSlotsFor(changed))
        assertEquals("08:30", TimeTableUtils.timeSlotsFor(changed).single().displayStart)
        assertTrue(TimeTableUtils.validateTimeJson(first) is TimeTableUtils.PeriodValidationResult.Valid)
        assertFalse(TimeTableUtils.validateTimeJson(timetable("10:00")) is TimeTableUtils.PeriodValidationResult.Valid)
    }

    @Test fun displaySlotsCannotBeMutatedByOneConsumer() {
        val slots = TimeTableUtils.timeSlotsFor(timetable("08:00"))
        assertThrows(UnsupportedOperationException::class.java) { (slots as MutableList).clear() }
        assertEquals(1, TimeTableUtils.timeSlotsFor(timetable("08:00")).size)
    }

    @Test fun minuteFormattingPreservesMidnightNoonAndLeadingZeros() {
        for (hour in 0..23) {
            for (minute in 0..59) {
                val clock = String.format(java.util.Locale.ROOT, "%02d:%02d", hour, minute)
                val json = """[{"node":1,"start":"$clock","end":"$clock"}]"""
                assertEquals(clock to clock, TimeTableUtils.courseTimeParts(1, 1, json))
            }
        }
    }
}
