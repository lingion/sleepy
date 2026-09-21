package com.lingion.sleepy

import com.lingion.sleepy.util.AppPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TomorrowReminderPrefsTest {

    @Test
    fun tomorrow_reminder_uses_dedicated_non_colliding_keys() {
        assertEquals("tomorrow_reminder", AppPrefs.KEY_TOMORROW_REMINDER_ENABLED)
        assertEquals("tomorrow_reminder_time", AppPrefs.KEY_TOMORROW_REMINDER_TIME)
        assertNotEquals(AppPrefs.KEY_DAILY_ENABLED, AppPrefs.KEY_TOMORROW_REMINDER_ENABLED)
        assertNotEquals(AppPrefs.KEY_DAILY_TIME, AppPrefs.KEY_TOMORROW_REMINDER_TIME)
    }

    @Test
    fun tomorrow_reminder_is_opt_in_at_ten_pm() {
        assertEquals("today_reminder", AppPrefs.KEY_TODAY_REMINDER_ENABLED)
        assertTrue("老用户的当天每日提醒行为必须保持开启", AppPrefs.DEFAULT_TODAY_REMINDER_ENABLED)
        assertFalse(AppPrefs.DEFAULT_TOMORROW_REMINDER_ENABLED)
        assertEquals("22:00", AppPrefs.DEFAULT_TOMORROW_REMINDER_TIME)
    }
}
