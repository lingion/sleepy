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
    fun `effective entries preserve network rows removed by user overrides`() {
        val effectiveEntry = HolidayEntry(holiday, "New Year", HolidayManager.TYPE_PUBLIC_HOLIDAY)
        val result = HolidayManager.yearData(listOf(effectiveEntry), fetchFailed = false, hasCachedData = true)

        assertEquals(listOf(effectiveEntry), result.entries)
        assertTrue(result.available)
    }

    @Test
    fun `merged user override participates in policy classification`() {
        val override = HolidayRange(
            id = "user-holiday",
            name = "User holiday",
            startDate = holiday,
            endDate = holiday,
            type = HolidayManager.TYPE_PUBLIC_HOLIDAY,
            sourceKey = null,
        )
        val merged = HolidayRangeOps.mergeSegments(emptyList(), listOf(override))
        val (holidays, workdays) = HolidayRangeOps.toSets(merged.active)
        val result = HolidayReminderPolicy.decide(
            date = holiday,
            publicHolidays = holidays,
            transferWorkdays = workdays,
            transfers = emptyList(),
            rulesEnabled = true,
            publicHolidayReminder = false,
            transferHolidayReminder = true,
            makeupWorkdayReminder = true,
            ordinaryWeekendReminder = true,
            dataAvailable = true,
        )

        assertTrue(holiday in holidays)
        assertEquals(HolidayReminderPolicy.Category.PUBLIC_HOLIDAY, result.category)
        assertFalse(result.allowReminder)
    }

    @Test
    fun `unavailable empty fetch is distinct from successful empty year`() {
        val unavailable = HolidayManager.yearData(emptyList(), fetchFailed = true, hasCachedData = false)
        val successfulEmpty = HolidayManager.yearData(emptyList(), fetchFailed = false, hasCachedData = true)

        assertFalse(unavailable.available)
        assertTrue(unavailable.entries.isEmpty())
        val unavailableDecision = HolidayReminderPolicy.decide(
            date = holiday,
            publicHolidays = emptySet(),
            transferWorkdays = emptySet(),
            transfers = emptyList(),
            rulesEnabled = true,
            publicHolidayReminder = false,
            transferHolidayReminder = false,
            makeupWorkdayReminder = false,
            ordinaryWeekendReminder = false,
            dataAvailable = unavailable.available,
        )
        assertEquals(HolidayReminderPolicy.Category.DATA_UNAVAILABLE, unavailableDecision.category)
        assertTrue(unavailableDecision.allowReminder)
        assertTrue(successfulEmpty.available)
        assertTrue(successfulEmpty.entries.isEmpty())
    }

    @Test
    fun `override entries do not make failed network data available`() {
        val override = listOf(HolidayEntry(holiday, "User holiday", HolidayManager.TYPE_PUBLIC_HOLIDAY))
        val result = HolidayManager.yearData(override, fetchFailed = true, hasCachedData = false)

        assertFalse(result.available)
        val decision = HolidayReminderPolicy.decide(
            date = holiday,
            publicHolidays = setOf(holiday),
            transferWorkdays = emptySet(),
            transfers = emptyList(),
            rulesEnabled = true,
            publicHolidayReminder = false,
            transferHolidayReminder = true,
            makeupWorkdayReminder = true,
            ordinaryWeekendReminder = true,
            dataAvailable = result.available,
        )
        assertEquals(HolidayReminderPolicy.Category.DATA_UNAVAILABLE, decision.category)
        assertTrue(decision.allowReminder)
    }
    @Test
    fun `failed refresh with cache retains cached entries`() {
        val cached = listOf(HolidayEntry(holiday, "New Year", HolidayManager.TYPE_PUBLIC_HOLIDAY))
        val result = HolidayManager.refreshedEntries(cached, emptyList(), fetchFailed = true)

        assertTrue(HolidayManager.yearData(result, fetchFailed = true, hasCachedData = true).available)
        assertEquals(cached, result)
    }

    @Test
    fun `failed refresh without cache returns unavailable empty data`() {
        val result = HolidayManager.refreshedEntries(null, emptyList(), fetchFailed = true)

        assertEquals(emptyList<HolidayEntry>(), result)
        assertFalse(HolidayManager.yearData(result, fetchFailed = true, hasCachedData = false).available)
    }

    @Test
    fun `successful empty refresh replaces cached data and remains available`() {
        val cached = listOf(HolidayEntry(holiday, "New Year", HolidayManager.TYPE_PUBLIC_HOLIDAY))
        val result = HolidayManager.refreshedEntries(cached, emptyList(), fetchFailed = false)

        assertTrue(result.isEmpty())
        assertTrue(HolidayManager.yearData(result, fetchFailed = false, hasCachedData = true).available)
    }

    @Test
    fun `parseEntries sorts and keeps supported types`() {
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
