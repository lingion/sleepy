package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w500dp-h600dp-mdpi")
class GlassFreeDragTest {
    @get:Rule val compose = createComposeRule()

    private fun advance(ms: Int) {
        repeat(ms / 16) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
    }

    private fun drag(direction: Offset, textButton: Boolean = false) {
        var calls = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        Box(Modifier.size(400.dp).padding(100.dp)) {
                            if (textButton) Box(Modifier.testTag("slot")) {
                                GlassButton({ calls++ }, Modifier.testTag("target")) {
                                    Box(Modifier.size(20.dp).testTag("pane"))
                                }
                            } else GlassIconButton({ calls++ }, Modifier.testTag("target")) {
                                Box(Modifier.fillMaxSize().testTag("pane"))
                            }
                        }
                    }
                }
            }
        }
        val target = compose.onNodeWithTag("target")
        val pane = compose.onNodeWithTag("pane", true)
        val rest = pane.fetchSemanticsNode().boundsInRoot
        val slotNode = if (textButton) compose.onNodeWithTag("slot", true) else target
        val slot = slotNode.fetchSemanticsNode().boundsInRoot
        target.performTouchInput { down(center) }
        advance(500)
        target.performTouchInput { moveBy(direction, 200) }
        advance(500)
        val pulled = pane.fetchSemanticsNode().boundsInRoot
        if (direction.x != 0f) assertTrue("glass must follow horizontal pull outside the click bounds: $rest -> $pulled", (pulled.center.x - rest.center.x) * direction.x > 3f * kotlin.math.abs(direction.x))
        if (direction.y != 0f) assertTrue("glass must follow vertical pull outside the click bounds: $rest -> $pulled", (pulled.center.y - rest.center.y) * direction.y > 3f * kotlin.math.abs(direction.y))
        assertTrue("held glass must remain raised after the click cancels", pulled.width > rest.width)
        assertEquals("layout slot stays fixed", slot.width, slotNode.fetchSemanticsNode().boundsInRoot.width, .1f)
        target.performTouchInput { cancel() }
        advance(1500)
        val released = pane.fetchSemanticsNode().boundsInRoot
        assertEquals(rest.center.x, released.center.x, .2f)
        assertEquals(rest.center.y, released.center.y, .2f)
        assertEquals(rest.width, released.width, .2f)
        assertEquals("dragging must not invoke the cancelled click", 0, calls)
        target.performClick()
        assertEquals(1, calls)
    }

    @Test fun iconPullsRightPastTheTouchTarget() = drag(Offset(80f, 0f))
    @Test fun iconPullsLeftPastTheTouchTarget() = drag(Offset(-80f, 0f))
    @Test fun iconPullsDownPastTheTouchTarget() = drag(Offset(0f, 80f))
    @Test fun iconPullsUpPastTheTouchTarget() = drag(Offset(0f, -80f))
    @Test fun longActionPullsDiagonallyDespiteItsDenseGutter() = drag(Offset(80f, 80f), textButton = true)
}
