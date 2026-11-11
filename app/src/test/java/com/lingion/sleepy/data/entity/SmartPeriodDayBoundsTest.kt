package com.lingion.sleepy.data.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPeriodDayBoundsTest {
    @Test
    fun `adding periods cannot generate a 26 hour clock`() {
        val config = SmartPeriodConfig(startTime = "08:00", periodMinutes = 60, totalPeriods = 18)

        assertEquals(SmartPeriodValidationError.ExceedsDay, config.validationError())
        assertTrue(config.derive().isEmpty())
    }

    @Test
    fun `last supported minute is valid but midnight is rejected`() {
        val latest = SmartPeriodConfig(startTime = "23:00", periodMinutes = 59, totalPeriods = 1)
        assertNull(latest.validationError())
        assertEquals("23:59", latest.derive().single().end)
        assertEquals(SmartPeriodValidationError.ExceedsDay, latest.copy(periodMinutes = 60).validationError())
    }

    @Test
    fun `assigned long durations and breaks count towards the day limit`() {
        val config = SmartPeriodConfig(
            startTime = "22:00", periodMinutes = 30, totalPeriods = 2,
            durations = listOf(DurationOption(60)), periodAssignments = listOf(null, 0),
            breaks = listOf(BreakOption(30)), transitionAssignments = listOf(0)
        )

        assertEquals(SmartPeriodValidationError.ExceedsDay, config.validationError())
        assertTrue(config.derive().isEmpty())
        assertNull(config.copy(breaks = listOf(BreakOption(29))).validationError())
        assertEquals("23:59", config.copy(breaks = listOf(BreakOption(29))).derive().last().end)
    }

    @Test
    fun `corrupt counts clocks and durations are rejected without throwing`() {
        for (count in listOf(0, -1, Int.MIN_VALUE, Int.MAX_VALUE)) {
            val config = SmartPeriodConfig(totalPeriods = count)
            assertEquals(SmartPeriodValidationError.PeriodCount, config.validationError())
            assertTrue(config.derive().isEmpty())
            assertTrue(config.effectivePeriodMinutes().size <= SmartPeriodConfig.MAX_PERIODS_PER_DAY)
            assertTrue(config.effectiveTransitionMinutes().size < SmartPeriodConfig.MAX_PERIODS_PER_DAY)
        }
        for (start in listOf("bad", "24:00", "08:60", "8:00", "08:00:01")) {
            assertEquals(SmartPeriodValidationError.StartTime, SmartPeriodConfig(startTime = start).validationError())
            assertTrue(SmartPeriodConfig(startTime = start).derive().isEmpty())
        }
        assertEquals(SmartPeriodValidationError.Duration, SmartPeriodConfig(periodMinutes = 0).validationError())
        assertEquals(SmartPeriodValidationError.Duration,
            SmartPeriodConfig(breaks = listOf(BreakOption(-1))).validationError())
        assertEquals(SmartPeriodValidationError.ExceedsDay,
            SmartPeriodConfig(periodMinutes = Int.MAX_VALUE).validationError())
    }

    @Test
    fun `invalid optional assignment indexes retain their established defaults`() {
        val config = SmartPeriodConfig(
            startTime = "08:00", periodMinutes = 45, totalPeriods = 2,
            durations = listOf(DurationOption(60)), periodAssignments = listOf(-1, 5),
            breaks = listOf(BreakOption(10)), transitionAssignments = listOf(5)
        )

        assertNull(config.validationError())
        assertEquals(listOf("08:00" to "08:45", "08:45" to "09:30"), config.derive().map { it.start to it.end })
    }
}
