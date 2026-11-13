package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GlassSwitchQuickInputTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var scroll: ScrollState
    private lateinit var scope: CoroutineScope
    private lateinit var flingBehavior: FlingBehavior
    private val checked = mutableStateOf(false)
    private val changes = mutableListOf<Boolean>()

    private fun show() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L }, LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        scroll = rememberScrollState(initial = 300)
                        scope = rememberCoroutineScope()
                        flingBehavior = ScrollableDefaults.flingBehavior()
                        Column(Modifier.size(320.dp, 400.dp).verticalScroll(scroll)) {
                            Spacer(Modifier.height(400.dp))
                            GlassSwitch(
                                checked.value,
                                { changes += it; checked.value = it },
                                Modifier.testTag("switch"),
                            )
                            Spacer(Modifier.height(1200.dp))
                        }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
    }

    @Test fun repeatedQuickTapsToggleWithoutWaitingForTheLens() {
        show()
        val control = compose.onNodeWithTag("switch")
        repeat(3) { index ->
            control.performTouchInput {
                down(center)
                advanceEventTime(16)
                up()
            }
            compose.mainClock.advanceTimeBy(32)
            compose.runOnIdle {
                assertEquals("quick tap $index must toggle once", index % 2 == 0, checked.value)
                assertEquals(index + 1, changes.size)
                assertEquals(300, scroll.value)
            }
        }
        compose.runOnIdle { assertEquals(listOf(true, false, true), changes) }
    }

    @Test fun quickHorizontalDragsToggleInBothDirectionsWithoutHolding() {
        show()
        val control = compose.onNodeWithTag("switch")
        repeat(2) { index ->
            control.performTouchInput {
                val from = if (index == 0) .2f else .8f
                val to = if (index == 0) .8f else .2f
                down(Offset(width * from, centerY))
                moveTo(Offset(width * to, centerY), 16)
                advanceEventTime(16)
                up()
            }
            compose.mainClock.advanceTimeBy(32)
            compose.runOnIdle {
                assertEquals("quick drag $index must commit", index == 0, checked.value)
                assertEquals(index + 1, changes.size)
                assertEquals(300, scroll.value)
            }
        }
        compose.runOnIdle { assertEquals(listOf(true, false), changes) }
    }

    private fun operateWhileFlinging(drag: Boolean) {
        show()
        compose.runOnIdle {
            scope.launch {
                scroll.scroll(MutatePriority.Default) {
                    with(flingBehavior) { performFling(800f) }
                }
            }
        }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {
            assertTrue("fixture must have moved", scroll.value > 300)
            assertTrue("press must arrive during an active fling", scroll.isScrollInProgress)
        }

        val control = compose.onNodeWithTag("switch")
        control.performTouchInput { down(Offset(width * .2f, centerY)) }
        compose.mainClock.advanceTimeByFrame()
        val frozen = compose.runOnIdle { scroll.value }
        control.performTouchInput {
            if (drag) moveTo(Offset(width * .8f, centerY), 16)
            else advanceEventTime(16)
            up()
        }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle {
            assertTrue("the press that stops the fling must also operate the switch", checked.value)
            assertEquals("gesture must commit exactly once", listOf(true), changes)
            assertEquals("the page must remain where the press stopped it", frozen, scroll.value)
        }
    }

    @Test fun quickTapStopsFlingAndTogglesWithTheSamePress() = operateWhileFlinging(drag = false)
    @Test fun quickDragStopsFlingAndTogglesWithTheSamePress() = operateWhileFlinging(drag = true)
}
