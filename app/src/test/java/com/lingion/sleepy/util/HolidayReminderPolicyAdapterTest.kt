package com.lingion.sleepy.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HolidayReminderPolicyAdapterTest {
    private fun source(path: String): String = File(
        System.getProperty("sleepy.test.root") ?: ".",
        "app/src/main/java/com/lingion/sleepy/util/$path",
    ).readText()

    private val adapterSource: String
        get() = source("HolidayReminderPolicyAdapter.kt")

    @Test
    fun `adapter reads reminder preferences rather than grey holiday preferences`() {
        val source = adapterSource

        listOf(
            "isHolidayReminderRulesEnabled(ctx)",
            "isHolidayReminderPublicHolidayEnabled(ctx)",
            "isHolidayReminderTransferHolidayEnabled(ctx)",
            "isHolidayReminderMakeupWorkdayEnabled(ctx)",
            "isHolidayReminderOrdinaryWeekendEnabled(ctx)",
        ).forEach { assertTrue("Missing reminder preference read: $it", source.contains(it)) }

        assertFalse(source.contains("KEY_HOLIDAY_GREY_HOLIDAY"))
        assertFalse(source.contains("KEY_HOLIDAY_GREY_WEEKEND"))
        assertFalse(source.contains("KEY_HOLIDAY_IGNORE_WORKDAY"))
        assertFalse(source.contains("isHolidayGreyHoliday(ctx)"))
        assertFalse(source.contains("isHolidayGreyWeekend(ctx)"))
        assertFalse(source.contains("isHolidayIgnoreWorkday(ctx)"))
    }

    @Test
    fun `adapter reads effective entries merged by manager without merging twice`() {
        val source = adapterSource
        val managerSource = source("HolidayManager.kt")

        assertTrue(managerSource.contains("HolidayRangeOps.mergeSegments(entries, AppPrefs.getHolidayRanges(ctx))"))
        assertTrue(source.contains("yearData.entries.asSequence()"))
        assertTrue(source.contains("HolidayManager.TYPE_PUBLIC_HOLIDAY"))
        assertTrue(source.contains("HolidayManager.TYPE_TRANSFER_WORKDAY"))
        assertFalse(source.contains("HolidayRangeOps.mergeSegments"))
        assertFalse(source.contains("HolidayRangeOps.toSets"))
        assertTrue(source.contains("dataAvailable = yearData.available"))
    }

    @Test
    fun `adapter scopes transfer lookup to the supplied table id`() {
        assertTrue(adapterSource.contains("AppPrefs.getHolidayTransfers(ctx, tableId)"))
    }
}
