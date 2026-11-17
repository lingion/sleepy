package com.lingion.sleepy.ui.screen.schedule

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.HolidayTransferEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ScheduleWeekCountRegressionTest {
    private fun state(maxWeek: Int, transfers: List<HolidayTransferEntry> = emptyList()) = ScheduleState(
        tables = listOf(TimeTableEntity(id = 1, name = "Existing timetable", startDate = "2026-09-07", maxWeek = maxWeek)),
        selectedTableId = 1,
        courses = listOf(CourseEntity(id = 1, tableId = 1, groupId = "course", courseName = "Course",
            day = 1, startNode = 1, step = 1, startWeek = 1, endWeek = 1, color = "#6750A4")),
        transfers = transfers,
    )

    @Test fun zeroWeekStoredTimetableKeepsAnAccessibleFirstWeek() {
        val state = state(0)
        assertEquals(1, state.displayMaxWeek)
        assertEquals(0, (state.selectedWeek - 1).coerceIn(0, state.displayMaxWeek - 1))
        assertEquals("Course", state.currentWeekCourses.single().courseName)
    }

    @Test fun negativeStoredWeekCountKeepsAnAccessibleFirstWeek() {
        val state = state(-3)
        assertEquals(1, state.displayMaxWeek)
        assertEquals(0, (state.selectedWeek - 1).coerceIn(0, state.displayMaxWeek - 1))
    }

    @Test fun validTimetableAndMakeupWeekRetainTheirOriginalExtent() {
        assertEquals(20, state(20).displayMaxWeek)
        val transfer = HolidayTransferEntry(LocalDate.of(2026, 9, 7), LocalDate.of(2027, 1, 25), "makeup")
        assertEquals(21, state(20, listOf(transfer)).displayMaxWeek)
    }
}
