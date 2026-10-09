package com.lingion.sleepy.widget.notification

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReminderRefreshContractTest {
    private fun source(path: String): String = File(System.getProperty("sleepy.test.root") ?: ".", path).readText()

    @Test
    fun refresh_action_reconciles_scheduler_dnd_and_fluid_cloud() {
        val app = source("app/src/main/java/com/lingion/sleepy/SleepyApp.kt")
        assertTrue(app.contains("notificationScheduler.scheduleAll()"))
        assertTrue(app.contains("classDndScheduler.reconcileReminderRules()"))
        assertTrue(app.contains("notificationScheduler.reconcileActiveFluidCloud()"))
        val scheduler = source("app/src/main/java/com/lingion/sleepy/widget/notification/CourseNotificationScheduler.kt")
        assertTrue(scheduler.contains("FluidCloudService.requestStop(app)"))
    }

    @Test
    fun fluid_cloud_has_explicit_stop_action_and_cleanup() {
        val service = source("app/src/main/java/com/lingion/sleepy/widget/notification/FluidCloudService.kt")
        assertTrue(service.contains("ACTION_STOP"))
        assertTrue(service.contains("handler.removeCallbacks(updater)"))
        assertTrue(service.contains("NOTIFY_BEFORE_CLASS_BASE"))
        assertTrue(service.contains("STOP_FOREGROUND_REMOVE"))
        assertTrue(service.contains("stopSelf()"))
    }

    @Test
    fun fluid_cloud_creates_notification_channels_before_starting_foreground() {
        val service = source("app/src/main/java/com/lingion/sleepy/widget/notification/FluidCloudService.kt")
        val ensureChannels = service.indexOf("CourseNotificationScheduler.ensureNotificationChannels(this)")
        val firstForegroundCall = service.indexOf("startForeground(")
        assertTrue(ensureChannels >= 0 && firstForegroundCall > ensureChannels)

        val scheduler = source("app/src/main/java/com/lingion/sleepy/widget/notification/CourseNotificationScheduler.kt")
        assertTrue(scheduler.contains("fun ensureNotificationChannels(context: Context)"))
        assertTrue(scheduler.contains("ensureNotificationChannels(context)"))
    }

    @Test
    fun dnd_exposes_refresh_reconciliation_entry_point() {
        val dnd = source("app/src/main/java/com/lingion/sleepy/widget/notification/ClassDndScheduler.kt")
        assertTrue(dnd.contains("fun reconcileReminderRules()"))
        assertTrue(dnd.contains("syncFromPrefs()"))
    }

    @Test
    fun rescheduler_keeps_debounced_single_action() {
        val source = source("app/src/main/java/com/lingion/sleepy/widget/notification/ReminderRescheduler.kt")
        assertTrue(source.contains("pending?.cancel()"))
        assertTrue(source.contains("delay(wait)"))
    }
}
