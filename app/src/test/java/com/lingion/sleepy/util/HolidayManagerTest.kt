package com.lingion.sleepy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HolidayManagerTest {
    private val holiday = LocalDate.of(2025, 1, 1)
    private val saturday = LocalDate.of(2025, 1, 4)
    private val sundayWorkday = LocalDate.of(2025, 1, 26)
    private val weekday = LocalDate.of(2025, 1, 6)

    @Test
    fun decideGrey_respects_holiday_toggle() {
        assertTrue(HolidayManager.decideGrey(holiday, setOf(holiday), emptySet(), true, false, false))
        assertFalse(HolidayManager.decideGrey(holiday, setOf(holiday), emptySet(), false, false, false))
    }

    @Test
    fun decideGrey_respects_weekend_toggle() {
        assertTrue(HolidayManager.decideGrey(saturday, emptySet(), emptySet(), false, true, false))
        assertFalse(HolidayManager.decideGrey(saturday, emptySet(), emptySet(), false, false, false))
    }

    @Test
    fun decideGrey_can_ignore_makeup_workday() {
        val workdays = setOf(sundayWorkday)
        assertFalse(HolidayManager.decideGrey(sundayWorkday, emptySet(), workdays, false, true, true))
        assertTrue(HolidayManager.decideGrey(sundayWorkday, emptySet(), workdays, false, true, false))
    }

    @Test
    fun decideGrey_never_greys_normal_weekday_without_holiday() {
        assertFalse(HolidayManager.decideGrey(weekday, emptySet(), emptySet(), true, true, false))
    }

    @Test
    fun parseEntries_sorts_and_keeps_supported_types() {
        val json = """{"year":2025,"dates":[
            {"date":"2025-01-26","name":"春节","type":"transfer_workday"},
            {"date":"2025-01-01","name":"元旦","type":"public_holiday"}]}"""
        val entries = HolidayManager.parseEntries(json)
        assertEquals(listOf(LocalDate.of(2025, 1, 1), sundayWorkday), entries.map { it.date })
        assertEquals(listOf(HolidayManager.TYPE_PUBLIC_HOLIDAY, HolidayManager.TYPE_TRANSFER_WORKDAY), entries.map { it.type })
    }

    @Test
    fun parseEntries_skips_bad_rows_and_malformed_documents() {
        val json = """{"dates":[
            {"date":"bad","name":"x","type":"public_holiday"},
            {"date":"2025-01-06","name":"y","type":"other"}]}"""
        assertEquals(1, HolidayManager.parseEntries(json).size)
        assertTrue(HolidayManager.parseEntries("{not json").isEmpty())
        assertTrue(HolidayManager.parseEntries("{}").isEmpty())
    }
}
