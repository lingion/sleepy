package com.lingion.sleepy.widget

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WeekGridWideWindowCoreTest {
    private val periods = (1..12).map { WidePeriod(it, 480 + (it - 1) * 60, 525 + (it - 1) * 60) }

    @Test fun defaultsAreVerticalAndLeft() {
        assertEquals(WeekGridWideSettings(WeekGridWideMode.VERTICAL, WeekGridWideLabelSide.LEFT), WeekGridWideSettings())
    }

    @Test fun threeDatesRetainChronologicalOrderAcrossWeekMonthAndYear() {
        for (today in listOf(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31))) {
            assertEquals((0L..2L).map(today::plusDays), WeekGridWideWindowCore.visibleDates(today, WeekGridWideMode.HORIZONTAL))
        }
    }

    @Test fun sevenColumnsAlwaysStartMondayEvenOnSunday() {
        val days = WeekGridWideWindowCore.visibleDates(LocalDate.of(2026, 11, 1), WeekGridWideMode.VERTICAL)
        assertEquals((1..7).toList(), days.map { it.dayOfWeek.value })
        assertEquals(LocalDate.of(2026, 10, 26), days.first())
    }

    @Test fun advancesAtEachIndividualEndIncludingBreaks() {
        assertEquals(PeriodWindow(0, 4), window(524))
        assertEquals(PeriodWindow(1, 4), window(525))
        assertEquals(PeriodWindow(1, 4), window(539))
        assertEquals(PeriodWindow(2, 4), window(585))
        assertEquals(PeriodWindow(3, 4), window(645))
    }

    @Test fun endOfDayClampsToLastFourRealPeriods() {
        assertEquals(PeriodWindow(8, 4), window(23 * 60))
        assertEquals(PeriodWindow(6, 6), WeekGridWideWindowCore.visiblePeriods(periods, 23 * 60, 6))
        assertEquals(PeriodWindow(0, 4), WeekGridWideWindowCore.visiblePeriods(periods, 0, 1))
    }

    @Test fun longCourseRemainsOneClippedSpanWithOneRowAtEnd() {
        assertEquals(0..3, WeekGridWideWindowCore.intersectPeriodSpan(0, 4, window(0)))
        assertEquals(0..2, WeekGridWideWindowCore.intersectPeriodSpan(0, 4, window(525)))
        assertEquals(0..1, WeekGridWideWindowCore.intersectPeriodSpan(0, 4, window(585)))
        assertEquals(0..0, WeekGridWideWindowCore.intersectPeriodSpan(0, 4, window(645)))
        assertNull(WeekGridWideWindowCore.intersectPeriodSpan(0, 4, window(705)))
    }

    @Test fun fourSeparateClassesKeepFourSeparateSpans() {
        assertEquals(listOf(0..0, 1..1, 2..2, 3..3), (0..3).map {
            WeekGridWideWindowCore.intersectPeriodSpan(it, it + 1, window(0))
        })
    }

    @Test fun emptyShortAndMissingTimeDoNotInventPeriods() {
        assertEquals(PeriodWindow(0, 0), WeekGridWideWindowCore.visiblePeriods(emptyList(), 900, 4))
        assertEquals(PeriodWindow(0, 2), WeekGridWideWindowCore.visiblePeriods(periods.take(2), 900, 4))
        val missing = periods.toMutableList().apply { this[1] = WidePeriod(2, null, null) }
        assertEquals(PeriodWindow(1, 4), WeekGridWideWindowCore.visiblePeriods(missing, 900, 4))
    }

    @Test fun nonContiguousNodesStillUsePhysicalRowIndices() {
        val sparse = periods.mapIndexed { i, p -> p.copy(node = 3 + i * 2) }
        assertEquals(PeriodWindow(1, 4), WeekGridWideWindowCore.visiblePeriods(sparse, 525, 4))
    }

    private fun window(now: Int) = WeekGridWideWindowCore.visiblePeriods(periods, now, 4)
}
