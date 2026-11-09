package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.component.liquidglass.GlassSliderThumbMetrics
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingGlassControlTest {
    @get:Rule val compose = createComposeRule()

    @Test fun heldTabOverflowsBothTrackEdgesAndKeepsItsCenter() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        Box(Modifier.padding(40.dp)) {
                            SegmentedSwitcher(
                                options = listOf(0 to "For You", 1 to "Library"),
                                selected = 0, onSelect = {},
                                modifier = Modifier.width(240.dp).testTag("tabs"),
                            )
                        }
                    }
                }
            }
        }
        val tabs = compose.onNodeWithTag("tabs")
        val thumb = compose.onNodeWithTag("segment-thumb", useUnmergedTree = true)
        val resting = thumb.getUnclippedBoundsInRoot()
        tabs.performTouchInput { down(Offset(width / 4f, centerY)) }
        compose.waitForIdle()
        val track = tabs.getUnclippedBoundsInRoot()
        val held = thumb.getUnclippedBoundsInRoot()
        assertTrue("held glass must extend above track: $held / $track", held.top < track.top)
        assertTrue("held glass must extend below track: $held / $track", held.bottom > track.bottom)
        assertEquals("growth must stay centered horizontally", (resting.left.value + resting.right.value) / 2, (held.left.value + held.right.value) / 2, 1f)
        assertEquals("growth must stay centered vertically", (resting.top.value + resting.bottom.value) / 2, (held.top.value + held.bottom.value) / 2, 1f)
        tabs.performTouchInput { up() }
    }

    @Test fun tabTicksWhenDraggingAcrossSelectionBoundaries() {
        val ticks = mutableListOf<Int>()
        compose.setContent {
            val context = LocalContext.current
            val feedbackView = androidx.compose.runtime.remember {
                object : android.view.View(context) {
                    override fun performHapticFeedback(feedbackConstant: Int, flags: Int): Boolean {
                        ticks += feedbackConstant
                        return true
                    }
                }
            }
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass, LocalView provides feedbackView) {
                    GlassHost {
                        SegmentedSwitcher(listOf(0 to "One", 1 to "Two", 2 to "Three"), 0, {},
                            Modifier.width(300.dp).testTag("tabs"))
                    }
                }
            }
        }
        compose.onNodeWithTag("tabs").performTouchInput {
            down(Offset(width / 6f, centerY))
            moveTo(Offset(width / 2f, centerY), 200)
            moveTo(Offset(width * 5f / 6f, centerY), 200)
        }
        compose.runOnIdle { assertTrue("press plus crossing both selection boundaries must tick", ticks.size >= 3) }
        compose.onNodeWithTag("tabs").performTouchInput { up() }
    }
}
