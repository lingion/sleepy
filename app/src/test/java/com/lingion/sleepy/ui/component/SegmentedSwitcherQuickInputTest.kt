package com.lingion.sleepy.ui.component

import com.lingion.sleepy.ui.component.liquidglass.LocalDockInputTimeNanos
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SegmentedSwitcherQuickInputTest {
    @get:Rule val compose = createComposeRule()
    private val selected = mutableIntStateOf(0)
    private val changes = mutableListOf<Int>()

    private fun show(initial: Int = 0) {
        selected.intValue = initial
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L }, LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        SegmentedSwitcher(
                            options = listOf(0 to "First", 1 to "Second"),
                            selected = selected.intValue,
                            onSelect = { changes += it; selected.intValue = it },
                            modifier = Modifier.width(240.dp).testTag("tabs"),
                        )
                    }
                }
            }
        }
    }

    private fun quickTap(index: Int) {
        compose.onNodeWithTag("tabs").performTouchInput {
            down(Offset(width * (if (index == 0) .25f else .75f), centerY))
            advanceEventTime(16)
            up()
        }
    }

    private fun quickDrag(from: Int, to: Int) {
        compose.onNodeWithTag("tabs").performTouchInput {
            val start = Offset(width * (if (from == 0) .25f else .75f), centerY)
            val end = Offset(width * (if (to == 0) .25f else .75f), centerY)
            // Move immediately after down; no held press or lens-rise delay.
            swipe(start, end, 64)
        }
    }

    private fun tapThenDrag(initial: Int) {
        show(initial)
        val other = 1 - initial
        quickTap(other)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(other, selected.intValue) }

        quickDrag(other, initial)
        compose.runOnIdle {
            assertEquals("the newly selected thumb must accept an immediate horizontal drag", initial, selected.intValue)
            assertEquals(listOf(other, initial), changes)
        }
    }

    @Test fun tappedRightThumbCanImmediatelyDragLeft() = tapThenDrag(initial = 0)

    @Test fun tappedLeftThumbCanImmediatelyDragRight() = tapThenDrag(initial = 1)

    @Test fun quickDragsCanSwitchBackAndForthWithoutLongPress() {
        show()
        quickDrag(0, 1)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, selected.intValue) }

        quickDrag(1, 0)
        compose.runOnIdle {
            assertEquals(0, selected.intValue)
            assertEquals(listOf(1, 0), changes)
        }
    }

    @Test fun newlySelectedSegmentCanDragBeforeItsLensArrives() {
        show()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        try {
            quickTap(1)
            compose.runOnIdle { assertEquals(1, selected.intValue) }
            // Make the changed selection visible to composition, but leave the
            // spring in flight. Input must not wait for its decorative lens.
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { }

            quickDrag(1, 0)
            compose.runOnIdle {
                assertEquals(0, selected.intValue)
                assertEquals(listOf(1, 0), changes)
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun quickTapsSelectWhileThePreviousLensAnimationIsRunning() {
        show()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        try {
            for (target in listOf(1, 0, 1, 0)) {
                quickTap(target)
                compose.runOnIdle { assertEquals(target, selected.intValue) }
                // Deliver the new selection to the pointer handler without waiting
                // for the travel and lens springs to finish before the next tap.
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle { }
            }
            compose.runOnIdle { assertEquals(listOf(1, 0, 1, 0), changes) }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }
}
