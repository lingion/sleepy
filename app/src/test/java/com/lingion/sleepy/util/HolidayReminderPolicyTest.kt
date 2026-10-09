package com.lingion.sleepy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HolidayReminderPolicyTest {
    private val publicHoliday = LocalDate.of(2026, 10, 1)
    private val saturday = LocalDate.of(2026, 10, 10)
    private val sunday = LocalDate.of(2026, 10, 11)
    private val monday = LocalDate.of(2026, 10, 12)

    private fun decide(
        date: LocalDate,
        publicHolidays: Set<LocalDate> = emptySet(),
        transferWorkdays: Set<LocalDate> = emptySet(),
        transfers: List<HolidayTransferEntry> = emptyList(),
        rulesEnabled: Boolean = true,
        publicHolidayReminder: Boolean = true,
        transferHolidayReminder: Boolean = true,
        makeupWorkdayReminder: Boolean = true,
        ordinaryWeekendReminder: Boolean = true,
        dataAvailable: Boolean = true,
    ) = HolidayReminderPolicy.decide(
        date,
        publicHolidays,
        transferWorkdays,
        transfers,
        rulesEnabled,
        publicHolidayReminder,
        transferHolidayReminder,
        makeupWorkdayReminder,
        ordinaryWeekendReminder,
        dataAvailable,
    )

    @Test
    fun `disabled master allows every date without reading child settings`() {
        val holidayResult = decide(
            date = saturday,
            publicHolidays = setOf(saturday),
            rulesEnabled = false,
            publicHolidayReminder = false,
            transferHolidayReminder = false,
            makeupWorkdayReminder = false,
            ordinaryWeekendReminder = false,
        )

        assertEquals(HolidayReminderPolicy.Category.PUBLIC_HOLIDAY, holidayResult.category)
        assertTrue(holidayResult.allowReminder)

        val ordinaryWeekendResult = decide(
            date = saturday,
            rulesEnabled = false,
            publicHolidayReminder = false,
            transferHolidayReminder = false,
            makeupWorkdayReminder = false,
            ordinaryWeekendReminder = false,
        )

        assertEquals(HolidayReminderPolicy.Category.ORDINARY_WEEKEND, ordinaryWeekendResult.category)
        assertTrue(ordinaryWeekendResult.allowReminder)
    }

    @Test
    fun `disabled master allows ordinary weekend without holiday`() {
        val result = decide(
            date = saturday,
            rulesEnabled = false,
            publicHolidayReminder = false,
            transferHolidayReminder = false,
            makeupWorkdayReminder = false,
            ordinaryWeekendReminder = false,
        )

        assertEquals(HolidayReminderPolicy.Category.ORDINARY_WEEKEND, result.category)
        assertTrue(result.allowReminder)
    }

    @Test
    fun `mapped public holiday uses transfer holiday child setting`() {
        val result = decide(
            date = publicHoliday,
            publicHolidays = setOf(publicHoliday),
            transfers = listOf(
                HolidayTransferEntry(publicHoliday, saturday, "segment")
            ),
            publicHolidayReminder = true,
            transferHolidayReminder = false,
        )

        assertEquals(HolidayReminderPolicy.Category.TRANSFER_HOLIDAY, result.category)
        assertFalse(result.allowReminder)
    }

    @Test
    fun `unavailable data remains conservative even when supplied sets contain the date`() {
        val result = decide(
            date = saturday,
            publicHolidays = setOf(saturday),
            transferWorkdays = setOf(saturday),
            transfers = listOf(HolidayTransferEntry(saturday, monday, "segment")),
            publicHolidayReminder = false,
            transferHolidayReminder = false,
            makeupWorkdayReminder = false,
            ordinaryWeekendReminder = false,
            dataAvailable = false,
        )

        assertEquals(HolidayReminderPolicy.Category.DATA_UNAVAILABLE, result.category)
        assertTrue(result.allowReminder)
    }

    @Test
    fun `all child settings true allow each gated category`() {
        assertTrue(decide(publicHoliday, publicHolidays = setOf(publicHoliday)).allowReminder)
        assertTrue(
            decide(
                publicHoliday,
                publicHolidays = setOf(publicHoliday),
                transfers = listOf(HolidayTransferEntry(publicHoliday, saturday, "segment")),
            ).allowReminder
        )
        assertTrue(decide(saturday, transferWorkdays = setOf(saturday)).allowReminder)
        assertTrue(decide(saturday).allowReminder)
    }

    @Test
    fun `each child setting false blocks only its selected category`() {
        val publicHolidayResult = decide(
            publicHoliday,
            publicHolidays = setOf(publicHoliday),
            publicHolidayReminder = false,
        )
        assertEquals(HolidayReminderPolicy.Category.PUBLIC_HOLIDAY, publicHolidayResult.category)
        assertFalse(publicHolidayResult.allowReminder)
        val publicGateDoesNotBlockMakeup = decide(
            saturday,
            transferWorkdays = setOf(saturday),
            publicHolidayReminder = false,
            makeupWorkdayReminder = true,
        )
        assertEquals(HolidayReminderPolicy.Category.MAKEUP_WORKDAY, publicGateDoesNotBlockMakeup.category)
        assertTrue(publicGateDoesNotBlockMakeup.allowReminder)

        val transferHolidayResult = decide(
            publicHoliday,
            publicHolidays = setOf(publicHoliday),
            transfers = listOf(HolidayTransferEntry(publicHoliday, saturday, "segment")),
            transferHolidayReminder = false,
        )
        assertEquals(HolidayReminderPolicy.Category.TRANSFER_HOLIDAY, transferHolidayResult.category)
        assertFalse(transferHolidayResult.allowReminder)

        val makeupWorkdayResult = decide(
            saturday,
            transferWorkdays = setOf(saturday),
            makeupWorkdayReminder = false,
        )
        assertEquals(HolidayReminderPolicy.Category.MAKEUP_WORKDAY, makeupWorkdayResult.category)
        assertFalse(makeupWorkdayResult.allowReminder)

        val ordinaryWeekendResult = decide(
            saturday,
            ordinaryWeekendReminder = false,
        )
        assertEquals(HolidayReminderPolicy.Category.ORDINARY_WEEKEND, ordinaryWeekendResult.category)
        assertFalse(ordinaryWeekendResult.allowReminder)
    }

    @Test
    fun `normal weekday always allows reminder`() {
        val result = decide(monday, publicHolidayReminder = false, ordinaryWeekendReminder = false)

        assertEquals(HolidayReminderPolicy.Category.NORMAL_WEEKDAY, result.category)
        assertTrue(result.allowReminder)
    }

    @Test
    fun `holiday on Saturday and Sunday uses public holiday category`() {
        assertEquals(
            HolidayReminderPolicy.Category.PUBLIC_HOLIDAY,
            decide(saturday, publicHolidays = setOf(saturday)).category,
        )
        assertEquals(
            HolidayReminderPolicy.Category.PUBLIC_HOLIDAY,
            decide(sunday, publicHolidays = setOf(sunday)).category,
        )
    }

    @Test
    fun `makeup workday on Saturday and Sunday has priority`() {
        assertEquals(
            HolidayReminderPolicy.Category.MAKEUP_WORKDAY,
            decide(saturday, transferWorkdays = setOf(saturday), ordinaryWeekendReminder = false).category,
        )
        assertEquals(
            HolidayReminderPolicy.Category.MAKEUP_WORKDAY,
            decide(sunday, transferWorkdays = setOf(sunday), ordinaryWeekendReminder = false).category,
        )
    }

    @Test
    fun `same date matching multiple sets follows exact priority`() {
        val result = decide(
            saturday,
            publicHolidays = setOf(saturday),
            transferWorkdays = setOf(saturday),
            transfers = listOf(HolidayTransferEntry(saturday, monday, "segment")),
            makeupWorkdayReminder = false,
            publicHolidayReminder = true,
            transferHolidayReminder = true,
            ordinaryWeekendReminder = true,
        )

        assertEquals(HolidayReminderPolicy.Category.MAKEUP_WORKDAY, result.category)
        assertFalse(result.allowReminder)
    }

    @Test
    fun `mapping without a public holiday does not change classification`() {
        val result = decide(
            saturday,
            transfers = listOf(HolidayTransferEntry(saturday, monday, "segment")),
            transferHolidayReminder = false,
            ordinaryWeekendReminder = true,
        )

        assertEquals(HolidayReminderPolicy.Category.ORDINARY_WEEKEND, result.category)
        assertTrue(result.allowReminder)
    }

    @Test
    fun `transfer mapping is isolated to the supplied timetable list`() {
        val mappingForOtherTable = listOf(HolidayTransferEntry(publicHoliday, saturday, "other-table"))
        val mappingForCurrentTable = listOf(HolidayTransferEntry(publicHoliday, sunday, "current-table"))

        val otherResult = decide(
            publicHoliday,
            publicHolidays = setOf(publicHoliday),
            transfers = mappingForOtherTable,
            transferHolidayReminder = false,
        )
        val currentResult = decide(
            publicHoliday,
            publicHolidays = setOf(publicHoliday),
            transfers = mappingForCurrentTable,
            transferHolidayReminder = false,
        )
        val unmappedResult = decide(
            publicHoliday,
            publicHolidays = setOf(publicHoliday),
            transfers = emptyList(),
            transferHolidayReminder = true,
            publicHolidayReminder = true,
        )

        assertEquals(HolidayReminderPolicy.Category.TRANSFER_HOLIDAY, otherResult.category)
        assertEquals(HolidayReminderPolicy.Category.TRANSFER_HOLIDAY, currentResult.category)
        assertEquals(HolidayReminderPolicy.Category.PUBLIC_HOLIDAY, unmappedResult.category)
        assertFalse(otherResult.allowReminder)
        assertFalse(currentResult.allowReminder)
        assertTrue(unmappedResult.allowReminder)
        assertEquals(emptyList<HolidayTransferEntry>(), mappingForOtherTable.filter { it.segmentId == "current-table" })
        assertEquals(emptyList<HolidayTransferEntry>(), mappingForCurrentTable.filter { it.segmentId == "other-table" })
    }
}
