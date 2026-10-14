package com.lingion.sleepy.widget.notification

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.util.AppPrefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class FluidTestReconciliationTest {
    @Test fun openingAppWithoutRealCourseDoesNotStopTestNotification() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val enabled = AppPrefs.isReminderEnabled(context)
        instrumentation.uiAutomation.executeShellCommand(
            "cmd deviceidle whitelist +${context.packageName}"
        ).close()
        try {
            AppPrefs.setReminderEnabled(context, false)
            context.startForegroundService(Intent(context, FluidCloudService::class.java)
                .setAction(FluidCloudService.ACTION_TEST))
            val deadline = System.currentTimeMillis() + 5000
            while (!FluidCastState.casting && System.currentTimeMillis() < deadline) Thread.sleep(25)
            assertTrue("Test notification must start", FluidCastState.casting)
            runBlocking { CourseNotificationScheduler(context).reconcileActiveFluidCloud() }
            Thread.sleep(500)
            instrumentation.waitForIdleSync()
            assertTrue("Returning to app must preserve an unexpired test", FluidCastState.casting)
            // A stop already queued before the test started must also be ignored.
            context.startForegroundService(Intent(context, FluidCloudService::class.java)
                .setAction(FluidCloudService.ACTION_STOP)
                .putExtra(FluidCloudService.EXTRA_RECONCILE, true))
            Thread.sleep(300)
            assertTrue("Stale reconciliation must not cancel a test", FluidCastState.casting)
            context.startForegroundService(Intent(context, FluidCloudService::class.java)
                .putExtra(FluidCloudService.EXTRA_RECONCILE, true)
                .putExtra("courseName", "Stale scheduled course")
                .putExtra("notifyEpoch", System.currentTimeMillis())
                .putExtra("classEpoch", System.currentTimeMillis() + 60_000))
            Thread.sleep(300)
            assertTrue("Stale course lookup must not replace the test", FluidCastState.testCasting)
            // An explicit user stop still takes effect.
            FluidCloudService.requestStop(context)
            val stopDeadline = System.currentTimeMillis() + 5000
            while (FluidCastState.casting && System.currentTimeMillis() < stopDeadline) Thread.sleep(25)
            assertFalse("Explicit stop must remain effective", FluidCastState.casting)
        } finally {
            FluidCloudService.requestStop(context)
            AppPrefs.setReminderEnabled(context, enabled)
            instrumentation.uiAutomation.executeShellCommand(
                "cmd deviceidle whitelist -${context.packageName}"
            ).close()
        }
    }
}
