package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class GlassSliderEndpointInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val value = mutableFloatStateOf(.75f)
    private val rangeValue = mutableStateOf(.25f.. .75f)
    private val receiptNanos = AtomicLong(0)
    private val changes = mutableListOf<Float>()
    private var finishes = 0

    private fun show(range: Boolean = false, rtl: Boolean = false) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalUiStyle provides UiStyle.LiquidGlass,
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalDockInputTimeNanos provides receiptNanos::get,
                ) {
                    GlassHost {
                        Box(Modifier.padding(20.dp)) {
                            val modifier = Modifier.width(335.dp).testTag("slider")
                            if (range) GlassRangeSlider(rangeValue.value, {
                                rangeValue.value = it
                                changes += it.endInclusive
                            }, modifier, valueRange = 0f..1f, steps = 0)
                            else GlassSlider(value.floatValue, {
                                value.floatValue = it
                                changes += it
                            }, modifier, onValueChangeFinished = { finishes++ })
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun settleHeld() {
        repeat(4) {
            compose.mainClock.advanceTimeBy(400)
            compose.waitForIdle()
        }
    }

    private fun thumbCenterDp(): Float {
        val bounds = compose.onNodeWithTag("glass-slider-end").getUnclippedBoundsInRoot()
        return ((bounds.left + bounds.right) / 2).value
    }

    private fun nextReceipt() {
        receiptNanos.set((compose.mainClock.currentTime + 1) * 1_000_000L)
    }

    private fun exerciseSingleThumb(rtl: Boolean) {
        show(rtl = rtl)
        val slider = compose.onNodeWithTag("slider")
        val sliderPixels = slider.fetchSemanticsNode().boundsInRoot
        val sliderDp = slider.getUnclippedBoundsInRoot()
        val rest = compose.onNodeWithTag("glass-slider-end").getUnclippedBoundsInRoot()
        val thumbPixels = compose.onNodeWithTag("glass-slider-end").fetchSemanticsNode().boundsInRoot
        val pxPerDp = sliderPixels.width / (sliderDp.right-sliderDp.left).value
        val downX = thumbPixels.center.x-sliderPixels.left
        val physicalSign = if (rtl) -1f else 1f
        val businessThreshold = downX+physicalSign*(10f*pxPerDp+.25f*sliderPixels.width)
        val endpoint = if (rtl) sliderDp.left.value+(rest.right-rest.left).value/2
            else sliderDp.right.value-(rest.right-rest.left).value/2
        nextReceipt()
        slider.performTouchInput { down(Offset(downX, centerY)) }
        settleHeld()
        nextReceipt()
        slider.performTouchInput { moveTo(Offset(businessThreshold+physicalSign*10.25f*pxPerDp, centerY), 16) }
        settleHeld()

        assertEquals("A held thumb may pass its business endpoint", 4.353380581148f,
            physicalSign*(thumbCenterDp()-endpoint), .75f)
        var publications = 0
        compose.runOnIdle {
            assertEquals(1f, value.floatValue, 0f)
            assertTrue(changes.isNotEmpty())
            assertTrue("Published values remain clamped", changes.all { it in 0f..1f })
            publications = changes.size
        }
        val beforeFurtherPull = thumbCenterDp()
        nextReceipt()
        slider.performTouchInput { moveTo(Offset(businessThreshold+physicalSign*20f*pxPerDp, centerY), 16) }
        settleHeld()

        assertTrue("Matching endpoint echoes retain a pointer that can pull farther",
            physicalSign*(thumbCenterDp()-beforeFurtherPull) > 1f)
        compose.runOnIdle { assertEquals("Extra visual pull does not republish endpoint values", publications, changes.size) }
        nextReceipt()
        slider.performTouchInput { up() }
        settleHeld()
        assertEquals(endpoint, thumbCenterDp(), .75f)
        compose.runOnIdle { assertEquals(1, finishes) }
    }

    @Test fun singleThumbEndpointPullPublishesOnlyClampedBusinessValuesInLtr() = exerciseSingleThumb(false)

    @Test fun singleThumbEndpointPullMirrorsItsPhysicalDirectionInRtl() = exerciseSingleThumb(true)

    @Test fun aTrackTapDraggedBeyondTheRailRetainsClampedAbsoluteMapping() {
        show()
        val slider = compose.onNodeWithTag("slider")
        val sliderDp = slider.getUnclippedBoundsInRoot()
        val rest = compose.onNodeWithTag("glass-slider-end").getUnclippedBoundsInRoot()
        val endpoint = sliderDp.right.value-(rest.right-rest.left).value/2
        nextReceipt()
        slider.performTouchInput { down(Offset(width*.1f, centerY)) }
        settleHeld()
        nextReceipt()
        slider.performTouchInput { moveTo(Offset(width*1.15f, centerY), 16) }
        settleHeld()

        assertEquals("Absolute track input has no thumb-drag overdrive", endpoint, thumbCenterDp(), .75f)
        compose.runOnIdle { assertEquals(1f, value.floatValue, 0f) }
        nextReceipt()
        slider.performTouchInput { up() }
    }

    @Test fun rangeThumbEndpointDragKeepsItsExistingClampedGeometry() {
        show(range = true)
        val slider = compose.onNodeWithTag("slider")
        val sliderPixels = slider.fetchSemanticsNode().boundsInRoot
        val sliderDp = slider.getUnclippedBoundsInRoot()
        val rest = compose.onNodeWithTag("glass-slider-end").getUnclippedBoundsInRoot()
        val thumbPixels = compose.onNodeWithTag("glass-slider-end").fetchSemanticsNode().boundsInRoot
        val pxPerDp = sliderPixels.width/(sliderDp.right-sliderDp.left).value
        val downX = thumbPixels.center.x-sliderPixels.left
        val target = downX+10f*pxPerDp+.25f*sliderPixels.width+20f*pxPerDp
        val endpoint = sliderDp.right.value-(rest.right-rest.left).value/2
        nextReceipt()
        slider.performTouchInput { down(Offset(downX, centerY)) }
        settleHeld()
        nextReceipt()
        slider.performTouchInput { moveTo(Offset(target, centerY), 16) }
        settleHeld()

        assertEquals("RangeSlider remains outside the single-thumb endpoint policy", endpoint, thumbCenterDp(), .75f)
        compose.runOnIdle {
            assertEquals(.25f, rangeValue.value.start, 0f)
            assertEquals(1f, rangeValue.value.endInclusive, 0f)
        }
        nextReceipt()
        slider.performTouchInput { up() }
    }
}
