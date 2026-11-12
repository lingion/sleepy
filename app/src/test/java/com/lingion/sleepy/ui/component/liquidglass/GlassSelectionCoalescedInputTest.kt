package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.component.PillNavItemSpec
import com.lingion.sleepy.ui.component.SegmentedSwitcher
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Consecutive requests must not depend on a parent recomposition between taps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-mdpi")
class GlassSelectionCoalescedInputTest {
    @get:Rule val compose = createComposeRule()

    private enum class Control(val firstCenter: Float) { segment(1f / 6f), dock(.23f) }
    private val selected = mutableIntStateOf(0)
    private val callbacks = mutableListOf<Int>()

    private fun show(control: Control, acceptChanges: Boolean) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        val onSelect: (Int) -> Unit = {
                            callbacks += it
                            if (acceptChanges) selected.intValue = it
                        }
                        when (control) {
                            Control.segment -> SegmentedSwitcher(
                                listOf(0 to "First", 1 to "Second", 2 to "Third"),
                                selected.intValue, onSelect,
                                Modifier.width(316.dp).testTag("control"),
                            )
                            Control.dock -> LiquidGlassNavigationBar(
                                listOf(PillNavItemSpec(Icons.Filled.Home, "Home"),
                                    PillNavItemSpec(Icons.Filled.Search, "Explore"),
                                    PillNavItemSpec(Icons.Filled.Settings, "Settings")),
                                selected.intValue, onSelect,
                                Modifier.width(316.dp).testTag("control"),
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun tapBThenAWithoutAFrame(control: Control) {
        compose.onNodeWithTag("control").performTouchInput {
            down(Offset(width * .5f, centerY))
            advanceEventTime(16)
            up()
            advanceEventTime(16)
            down(Offset(width * control.firstCenter, centerY))
            advanceEventTime(16)
            up()
        }
    }

    private fun acceptedReversal(control: Control) {
        show(control, acceptChanges = true)
        try {
            tapBThenAWithoutAFrame(control)
            compose.runOnIdle {
                assertEquals("both accepted requests must be delivered before parent recomposition", listOf(1, 0), callbacks)
                assertEquals("the final request returns selection to A", 0, selected.intValue)
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    private fun rejectedReversal(control: Control) {
        show(control, acceptChanges = false)
        try {
            tapBThenAWithoutAFrame(control)
            compose.runOnIdle {
                assertEquals("input requests remain ordered even if the controller rejects them", listOf(1, 0), callbacks)
                assertEquals("external selection remains authoritative", 0, selected.intValue)
            }
            // Allow composition to confirm the rejected external A, without
            // waiting for the lens animation, then request B once more.
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { }
            compose.onNodeWithTag("control").performTouchInput {
                down(Offset(width * .5f, centerY))
                advanceEventTime(16)
                up()
            }
            compose.runOnIdle {
                assertEquals("a rejected request must not poison subsequent input", listOf(1, 0, 1), callbacks)
                assertEquals(0, selected.intValue)
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    private fun rejectedSelectionReconciles(control: Control) {
        show(control, acceptChanges = false)
        try {
            compose.onNodeWithTag("control").performTouchInput {
                down(Offset(width * .5f, centerY))
                advanceEventTime(16)
                up()
            }
            compose.runOnIdle {
                assertEquals(listOf(1), callbacks)
                assertEquals(0, selected.intValue)
            }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { }
            compose.onNodeWithTag("control").performTouchInput {
                down(Offset(width * control.firstCenter, centerY))
                advanceEventTime(16)
                up()
                advanceEventTime(16)
                down(Offset(width * .5f, centerY))
                advanceEventTime(16)
                up()
            }
            compose.runOnIdle {
                assertEquals("after rejection A is selected again; only the new B requests a change",
                    listOf(1, 1), callbacks)
                assertEquals(0, selected.intValue)
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun segmentedAcceptsBThenAWithinOneInputBatch() = acceptedReversal(Control.segment)
    @Test fun dockAcceptsBThenAWithinOneInputBatch() = acceptedReversal(Control.dock)
    @Test fun segmentedReconcilesRejectedRequestsBeforeTheNextGesture() = rejectedReversal(Control.segment)
    @Test fun dockReconcilesRejectedRequestsBeforeTheNextGesture() = rejectedReversal(Control.dock)
    @Test fun segmentedRejectsARequestWithoutKeepingAnOptimisticSelection() = rejectedSelectionReconciles(Control.segment)
    @Test fun dockRejectsARequestWithoutKeepingAnOptimisticSelection() = rejectedSelectionReconciles(Control.dock)
}
