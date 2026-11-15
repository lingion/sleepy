package com.lingion.sleepy.ui.screen.mine

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Verifies the real LazyColumn screen's composition acknowledges the same observed list. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TableReminderScreenAcknowledgementTest {
    @get:Rule val compose = createComposeRule()
    private val off = TimeTableEntity(id = 7L, name = "Reminder test table", startDate = "2026-09-01", reminderEnabled = false)
    private val on = off.copy(reminderEnabled = true)
    private val source = MutableStateFlow(listOf(off))
    private val saved = CompletableDeferred<Unit>()
    private var failures = 0
    private val vm = TableReminderViewModel(source) { _, _ -> saved.await() }

    private fun show() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.Classic) {
                    TableReminderScreen(onBack = {}, vm = vm)
                }
            }
        }
        compose.onNodeWithText(off.name).assertIsOff().assertIsEnabled()
    }

    @Test fun echoBeforeWriteReturnIsAcknowledgedWhenOnlyTheWritePhaseChanges() {
        show()
        compose.runOnIdle { vm.setEnabled(off.id, true) { failures++ } }
        compose.onNodeWithText(off.name).assertIsNotEnabled()
        compose.runOnIdle { source.value = listOf(on) }
        compose.onNodeWithText(off.name).assertIsOn().assertIsNotEnabled()
        compose.runOnIdle { assertTrue(off.id in vm.pending) }

        // The list no longer changes. The enclosing Screen, not just its lazy
        // item, must observe this write-return phase and run its UI ack.
        compose.runOnIdle { saved.complete(Unit) }
        compose.onNodeWithText(off.name).assertIsOn().assertIsEnabled()
        compose.runOnIdle { assertTrue(vm.pending.isEmpty()); assertEquals(0, failures) }
    }

    @Test fun writerReturningFirstKeepsTheActualRowDisabledUntilItsLateEchoIsRendered() {
        show()
        compose.runOnIdle { vm.setEnabled(off.id, true) { failures++ }; saved.complete(Unit) }
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithText(off.name).assertIsOff().assertIsNotEnabled()
            compose.runOnIdle { assertTrue(off.id in vm.pending) }
        }
        compose.runOnIdle { source.value = listOf(on) }
        compose.onNodeWithText(off.name).assertIsOn().assertIsEnabled()
        compose.runOnIdle { assertTrue(vm.pending.isEmpty()); assertEquals(0, failures) }
    }
}
