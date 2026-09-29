package com.lingion.sleepy.widget.notification

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BootReceiverRecoveryContractTest {

    private fun source(relativePath: String): String {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val file = File(dir, relativePath)
            if (file.isFile) return file.readText()
            dir = dir.parentFile
        }
        error("source not found: $relativePath")
    }

    @Test
    fun `boot receiver reschedules notifications after boot and package replacement`() {
        val scheduler = source(
            "app/src/main/java/com/lingion/sleepy/widget/notification/CourseNotificationScheduler.kt"
        )

        assertTrue(scheduler.contains("intent.action == Intent.ACTION_BOOT_COMPLETED"))
        assertTrue(scheduler.contains("intent.action == Intent.ACTION_MY_PACKAGE_REPLACED"))
        assertTrue(scheduler.contains("SleepyApp.get().notificationScheduler.scheduleAll()"))
    }

    @Test
    fun `boot recovery is gated by the master reminder switch`() {
        val scheduler = source(
            "app/src/main/java/com/lingion/sleepy/widget/notification/CourseNotificationScheduler.kt"
        )
        val recovery = scheduler.substringAfter("if (intent.action == Intent.ACTION_BOOT_COMPLETED")
            .substringBefore("// 课程边界闹钟")

        assertTrue(recovery.contains("if (AppPrefs.isReminderEnabled(context))"))
        assertTrue(recovery.contains("scheduleAll()"))
    }

    @Test
    fun `manifest exposes only the persistent recovery broadcasts`() {
        val manifest = source("app/src/main/AndroidManifest.xml")

        assertTrue(manifest.contains("android.intent.action.BOOT_COMPLETED"))
        assertTrue(manifest.contains("android.intent.action.MY_PACKAGE_REPLACED"))
    }
}
