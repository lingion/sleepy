package com.lingion.sleepy.ui.screen.mine

import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks the experimental notification control to its existing reminders section. */
class ReminderExperimentalTagContractTest {
    private val source: String by lazy {
        sequenceOf(
            System.getProperty("sleepy.test.root")?.let { java.io.File(it, "app/src/main/java/com/lingion/sleepy/ui/screen/mine/ReminderScreen.kt") },
            java.io.File("src/main/java/com/lingion/sleepy/ui/screen/mine/ReminderScreen.kt"),
            java.io.File("app/src/main/java/com/lingion/sleepy/ui/screen/mine/ReminderScreen.kt")
        ).filterNotNull().firstOrNull { it.isFile }?.readText()
            ?: error("Unable to load ReminderScreen.kt source")
    }

    @Test
    fun `fluid notification row keeps experimental tag in reminders screen`() {
        val fluidRow = source.substringAfter(
            "title = stringResource(R.string.reminder_fluid_title)",
            ""
        ).substringBefore("if (fluidEnabled)")
        assertTrue(fluidRow.contains("tag = stringResource(R.string.reminder_experimental_tag)"))
        assertTrue(source.contains("stringResource(R.string.reminder_fluid_title)"))
    }
}
