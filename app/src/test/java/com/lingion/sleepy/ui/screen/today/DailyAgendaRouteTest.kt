package com.lingion.sleepy.ui.screen.today

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DailyAgendaRouteTest {
    @Test fun todayAndTomorrowDoNotSharePendingIntentIdentity() {
        val date = LocalDate.of(2026, 12, 31)
        assertNotEquals(DailyAgendaRoute.identity(date), DailyAgendaRoute.identity(date.plusDays(1)))
    }
    @Test fun oldNotificationsRetainTheirAbsoluteDateAcrossMonthAndYearBoundaries() {
        val date = LocalDate.of(2026, 12, 31)
        assertEquals(date, DailyAgendaRoute.parse(date.toString()))
        assertEquals("sleepy://daily-agenda/2026-12-31", DailyAgendaRoute.identity(date))
    }
    @Test fun malformedAndMissingDatesDoNotSilentlyOpenToday() {
        listOf(null, "", "tomorrow", "2026-02-30", "2026-13-01").forEach {
            assertNull(DailyAgendaRoute.parse(it))
        }
    }
}
