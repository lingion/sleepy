package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-mdpi")
class GlassIconLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun adjacentIconsKeepAVisualGutterInsideFullTouchTargets() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        Row {
                            repeat(2) { index ->
                                GlassIconButton({}, Modifier.testTag("target$index")) {
                                    Box(Modifier.fillMaxSize().testTag("pane$index"))
                                }
                            }
                        }
                    }
                }
            }
        }
        val first = compose.onNodeWithTag("pane0", true).fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("pane1", true).fetchSemanticsNode().boundsInRoot
        assertEquals("glass visual disc", 36f, first.width, .1f)
        compose.onNodeWithTag("target0").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        assertTrue("at least 12dp of visible space between adjacent discs", second.left - first.right >= 11.9f)
    }
    @Test fun leavingClickBoundsKeepsThePaneRaisedUntilTheFingerReleases() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        GlassIconButton({}, Modifier.testTag("target")) {
                            Box(Modifier.fillMaxSize().testTag("pane"))
                        }
                    }
                }
            }
        }
        val pane = compose.onNodeWithTag("pane", true)
        val resting = pane.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("target").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(500)
        assertTrue("effective press must raise the glass", pane.fetchSemanticsNode().boundsInRoot.width > resting.width)
        compose.onNodeWithTag("target").performTouchInput { moveBy(Offset(120f, 0f), 100) }
        compose.mainClock.advanceTimeBy(1000)
        val held = pane.fetchSemanticsNode().boundsInRoot
        assertTrue("click cancellation must not lower the held pane", held.width > resting.width)
        assertTrue("the pane follows the held finger beyond its touch target", held.center.x > resting.center.x + 3f)
        compose.onNodeWithTag("target").performTouchInput { up() }
        repeat(94) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        val released = pane.fetchSemanticsNode().boundsInRoot
        assertEquals(resting.width, released.width, .2f)
        assertEquals(resting.left, released.left, .2f)
    }

}
