package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.component.SegmentedSwitcher
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

/** Native iOS 26.5 presentation traces measured on November 11–12, 2026. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-mdpi")
class NativeGlassControlPhaseTest {
    @get:Rule val compose = createComposeRule()

    private fun show(style: UiStyle = UiStyle.LiquidGlass, segmentHeight: Int? = null, selection: (Int) -> Unit = {}) {
        val checked = mutableStateOf(false)
        val selected = mutableStateOf(0)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides style) {
                    GlassHost(Modifier.fillMaxSize()) {
                        Column {
                            GlassSwitch(
                                checked.value, { checked.value = it }, Modifier.testTag("switch"),
                                thumbContent = { Box(Modifier.fillMaxSize().testTag("thumb")) },
                            )
                            SegmentedSwitcher(
                                options = listOf(0 to "First", 1 to "Second", 2 to "Third"),
                                selected = selected.value, onSelect = { selected.value = it; selection(it) },
                                modifier = Modifier.width(332.dp)
                                    .then(segmentHeight?.let { Modifier.height(it.dp) } ?: Modifier)
                                    .testTag("segments"),
                            )
                        }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot

    @Test fun classicStillUsesTheCallersLargerTrackHeight() {
        show(UiStyle.Classic, segmentHeight = 60)
        assertEquals(60f, bounds("segments").height, 1f)
        assertEquals(52f, bounds("segment-thumb").height, 1f)
    }

    @Test fun switchRisesThroughIntermediateGeometryAndSettlesAtTheNativeHeldSize() {
        show()
        assertEquals(37f, bounds("thumb").width, 1f)
        val control = compose.onNodeWithTag("switch")
        control.performTouchInput { down(Offset(centerX, centerY)) }
        compose.runOnIdle { }
        compose.mainClock.advanceTimeBy(64)
        assertTrue("rise has an intermediate size: ${bounds("thumb")}", bounds("thumb").width in 40f..57f)
        compose.mainClock.advanceTimeBy(112)
        val peak = bounds("thumb")
        assertTrue("native rise briefly overshoots its held size", peak.width in 59f..62f)
        assertTrue("native rise reaches about 40 pt tall", peak.height in 39f..41f)
        compose.mainClock.advanceTimeBy(500)
        val held = bounds("thumb")
        assertEquals(58f, held.width, 1f)
        assertEquals(38.333f, held.height, 1f)
        control.performTouchInput { cancel() }
        compose.runOnIdle { }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(37f, bounds("thumb").width, 1f)
    }

    @Test fun releasedSwitchContractsWhileItIsStillTravelling() {
        show()
        val control = compose.onNodeWithTag("switch")
        control.performTouchInput { down(Offset(centerX, centerY)) }
        compose.runOnIdle { }
        compose.mainClock.advanceTimeBy(500)
        val held = bounds("thumb")
        control.performTouchInput { up() }
        compose.runOnIdle { }
        compose.mainClock.advanceTimeBy(80)
        val falling = bounds("thumb")
        assertTrue("release restores the solid geometry before travel finishes", falling.width < held.width - 3f)
        assertTrue("fall and travel overlap", falling.center.x > held.center.x + 1f)
        compose.mainClock.advanceTimeBy(1_000)
        val settled = bounds("thumb")
        assertEquals(37f, settled.width, 1f)
        assertTrue("travel continues after contraction begins", settled.center.x > falling.center.x + 1f)
    }

    @Test fun segmentedHeldLensAddsTheNativeTwentyFourBySixteenPointGrowth() {
        show()
        val rest = bounds("segment-thumb")
        assertEquals("native resting lens height", 28f, rest.height, 1f)
        assertEquals("interaction height stays accessible", 48f, bounds("segments").height, 1f)
        assertEquals("native seat spans one third of the complete track", 332f / 6f,
            rest.center.x - bounds("segments").left, 1f)
        assertEquals("each lens has a 2 pt inset within its seat", 332f / 3f - 4f, rest.width, 1f)
        val control = compose.onNodeWithTag("segments")
        control.performTouchInput { down(Offset(width / 6f, centerY)) }
        compose.runOnIdle { }
        compose.mainClock.advanceTimeBy(700)
        val held = bounds("segment-thumb")
        assertEquals("native held lens height", 44f, held.height, 1f)
        assertEquals(24f, held.width - rest.width, 1f)
        assertEquals(16f, held.height - rest.height, 1f)
        control.performTouchInput { cancel() }
        compose.runOnIdle { }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(rest.width, bounds("segment-thumb").width, 1f)
        assertEquals(rest.height, bounds("segment-thumb").height, 1f)
    }

    @Test fun unselectedSegmentStaysAtRestUntilReleaseThenMakesAFullSelectionPulse() {
        val changes = mutableListOf<Int>()
        show(selection = { changes += it })
        val rest = bounds("segment-thumb")
        val control = compose.onNodeWithTag("segments")
        control.performTouchInput { down(Offset(width / 2f, centerY)) }
        compose.runOnIdle { }
        compose.mainClock.advanceTimeBy(700)
        assertEquals(rest, bounds("segment-thumb"))
        assertTrue(changes.isEmpty())
        control.performTouchInput { up() }
        compose.runOnIdle { }
        assertEquals(listOf(1), changes)
        var maximumHeight = 0f
        var maximumWidth = 0f
        repeat(30) {
            compose.mainClock.advanceTimeByFrame()
            maximumHeight = maxOf(maximumHeight, bounds("segment-thumb").height)
            maximumWidth = maxOf(maximumWidth, bounds("segment-thumb").width)
        }
        // Native bounds reach 44 pt, but movement deformation can compress the
        // painted height (39.3 pt in the measured UIKit B transition).
        assertTrue("committed B visibly lifts and deforms: $maximumWidth x $maximumHeight",
            maximumHeight >= 38f && maximumWidth >= rest.width + 24f)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(28f, bounds("segment-thumb").height, 1f)
        assertTrue(bounds("segment-thumb").center.x > rest.center.x + 70f)
    }

    @Test fun leavingAnUnselectedSegmentDoesNotActivateItOrLiftTheSelectedLens() {
        val changes = mutableListOf<Int>()
        show(selection = { changes += it })
        val rest = bounds("segment-thumb")
        val control = compose.onNodeWithTag("segments")
        control.performTouchInput { down(Offset(width / 2f, centerY)); moveBy(Offset(0f, 100f)) }
        compose.runOnIdle { }
        compose.mainClock.advanceTimeBy(300)
        assertEquals(rest, bounds("segment-thumb"))
        control.performTouchInput { up() }
        compose.runOnIdle { }
        compose.mainClock.advanceTimeBy(700)
        assertEquals(rest, bounds("segment-thumb"))
        assertTrue(changes.isEmpty())
    }
}
