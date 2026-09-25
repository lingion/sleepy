package com.lingion.sleepy.ui.screen.mine

import org.junit.Assert.assertTrue
import org.junit.Test
import com.lingion.sleepy.testutil.readProjectSource

/** Locks the experimental notification control to its existing reminders section. */
class ReminderExperimentalTagContractTest {
    private val source: String by lazy {
        readProjectSource("app/src/main/java/com/lingion/sleepy/ui/screen/mine/ReminderScreen.kt")
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
