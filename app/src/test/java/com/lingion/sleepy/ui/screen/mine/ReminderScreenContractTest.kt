package com.lingion.sleepy.ui.screen.mine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReminderScreenContractTest {
    private val root = File(System.getProperty("sleepy.test.root") ?: ".")
    private fun source(path: String): String = File(root, path).readText()

    @Test
    fun date_rule_screen_reads_all_preferences_and_persists_each_change() {
        val source = source("app/src/main/java/com/lingion/sleepy/ui/screen/mine/ReminderScreen.kt")
        listOf(
            "isHolidayReminderRulesEnabled(context)",
            "isHolidayReminderPublicHolidayEnabled(context)",
            "isHolidayReminderTransferHolidayEnabled(context)",
            "isHolidayReminderMakeupWorkdayEnabled(context)",
            "isHolidayReminderOrdinaryWeekendEnabled(context)",
            "setHolidayReminderRulesEnabled(context, it)",
            "setHolidayReminderPublicHolidayEnabled(context, it)",
            "setHolidayReminderTransferHolidayEnabled(context, it)",
            "setHolidayReminderMakeupWorkdayEnabled(context, it)",
            "setHolidayReminderOrdinaryWeekendEnabled(context, it)",
        ).forEach { assertTrue("ReminderScreen missing $it", source.contains(it)) }
        assertTrue(source.contains("ReminderRescheduler.request()"))
        assertTrue(source.contains("enabled = dateRulesEnabled"))
    }

    @Test
    fun screen_exposes_advanced_holiday_callback_and_nav_host_wires_it() {
        val screen = source("app/src/main/java/com/lingion/sleepy/ui/screen/mine/ReminderScreen.kt")
        val nav = source("app/src/main/java/com/lingion/sleepy/ui/nav/SleepyNavHost.kt")
        assertTrue(screen.contains("onOpenHoliday: () -> Unit"))
        assertTrue(screen.contains("onClick = onOpenHoliday"))
        assertTrue(nav.contains("onOpenHoliday = { navigator.openHoliday() }"))
    }

    @Test
    fun date_rule_copy_names_all_four_consumers() {
        val source = source("app/src/main/java/com/lingion/sleepy/ui/screen/mine/ReminderScreen.kt")
        assertTrue(source.contains("reminder_date_rules_subtitle"))
        assertTrue(source.contains("reminder_date_rules_inactive"))
        assertTrue(source.contains("reminder_date_rules_advanced_subtitle"))
    }
}
