package com.lingion.sleepy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HolidayMakeupTest {
    private val sunday = LocalDate.of(2026, 10, 11)

    @Test
    fun resolveCourseDay_usesMappedDay() {
        assertEquals(4, HolidayRangeOps.resolveCourseDay(sunday, listOf(MakeupDay(sunday, 4))))
    }

    @Test
    fun resolveCourseDay_keepsNaturalDayWhenUnmapped() {
        assertEquals(7, HolidayRangeOps.resolveCourseDay(sunday, emptyList()))
    }

    @Test
    fun decodeMakeupDays_skipsInvalidRows() {
        val decoded = HolidayRangeOps.decodeMakeupDays(
            """[
                {"date":"2026-10-11","sourceDayOfWeek":4},
                {"date":"bad","sourceDayOfWeek":4},
                {"date":"2026-10-12","sourceDayOfWeek":0},
                {"date":"2026-10-13","sourceDayOfWeek":8}
            ]"""
        )
        assertEquals(listOf(MakeupDay(sunday, 4)), decoded)
    }

    @Test
    fun encodeDecodeMakeupDays_roundTrips() {
        val input = listOf(
            MakeupDay(LocalDate.of(2026, 10, 11), 4),
            MakeupDay(LocalDate.of(2026, 10, 25), 1)
        )
        assertEquals(input, HolidayRangeOps.decodeMakeupDays(HolidayRangeOps.encodeMakeupDays(input)))
    }

    @Test
    fun decodeMakeupDays_duplicateDate_lastRowWins() {
        val decoded = HolidayRangeOps.decodeMakeupDays(
            """[
                {"date":"2026-10-11","sourceDayOfWeek":4},
                {"date":"2026-10-11","sourceDayOfWeek":2}
            ]"""
        )
        assertEquals(listOf(MakeupDay(sunday, 2)), decoded)
    }

    @Test
    fun decodeMakeupDays_badJsonIsEmpty() {
        assertTrue(HolidayRangeOps.decodeMakeupDays("not-json").isEmpty())
    }
}
