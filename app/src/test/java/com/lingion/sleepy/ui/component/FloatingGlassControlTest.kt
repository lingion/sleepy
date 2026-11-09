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
import com.lingion.sleepy.ui.component.liquidglass.LiquidGlassStyle
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

    @Test fun controlLensRetainsDockRainbowDispersionInBothPasses() {
        val colors = androidx.compose.material3.lightColorScheme()
        assertEquals(6.5, LiquidGlassStyle.controlLens(colors, false).dispersion, 0.0)
        assertEquals(3.6, LiquidGlassStyle.controlLensContent(colors, false).dispersion, 0.0)
    }

    @Test fun tabRisesProgressivelyAndStartsLoweringWithoutAFixedReleaseDelay() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        Box(Modifier.padding(40.dp)) {
                            SegmentedSwitcher(listOf(0 to "For You", 1 to "Library"), 0, {},
                                Modifier.width(240.dp).testTag("tabs"))
                        }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        val tabs = compose.onNodeWithTag("tabs")
        val thumb = compose.onNodeWithTag("segment-thumb", true)
        fun height() = thumb.getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
        tabs.performTouchInput { down(Offset(width / 4f, centerY)) }
        fun advance(ms: Int) {
            repeat(ms / 16) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
        }
        advance(160)
        val rising = height()
        assertTrue("the lens must still be rising at 160ms, not pop to full size: $rising", rising > 40f && rising < 51.5f)
        advance(800)
        assertEquals(52f, height(), 1f)
        tabs.performTouchInput { up() }
        advance(80)
        val lowering = height()
        assertTrue("release must lower continuously rather than freeze at full height: $lowering", lowering > 34f && lowering < 51f)
        advance(1504)
        assertEquals(34f, height(), 1f)
    }

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

    @Test fun precisionSliderHasSmallColoredVerticalThumb() {
        assertTrue("precision thumb must be small", GlassSliderThumbMetrics.RESTING_WIDTH <= 20f)
        assertTrue("thumb must be circular or vertical", GlassSliderThumbMetrics.RESTING_HEIGHT >= GlassSliderThumbMetrics.RESTING_WIDTH)
        val color = GlassSliderThumbMetrics.restingColor(Color(0xFF267BEF))
        assertTrue("thumb must retain visible color instead of whitening it away", color.blue - color.red > .3f)
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
