package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.component.SegmentedSwitcher
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GlassGestureOwnershipTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var scroll: ScrollState
    private val visible = mutableStateOf(true)
    private val enabled = mutableStateOf(true)
    private val checked = mutableStateOf(false)
    private val value = mutableFloatStateOf(.3f)
    private val selected = mutableIntStateOf(0)

    private var style = UiStyle.LiquidGlass
    private var direction = androidx.compose.ui.unit.LayoutDirection.Ltr
    private var steps = 0
    private var finished = 0

    private fun show(kind: String) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides style, androidx.compose.ui.platform.LocalLayoutDirection provides direction) {
                    GlassHost {
                        scroll = rememberScrollState()
                        Column(Modifier.size(320.dp, 400.dp).testTag("page").verticalScroll(scroll)) {
                            Spacer(Modifier.height(100.dp))
                            if (visible.value) {
                                val tag = Modifier.width(280.dp).testTag("control")
                                when (kind) {
                                    "switch" -> GlassSwitch(checked.value, { checked.value = it }, tag, enabled = enabled.value)
                                    "hue" -> com.lingion.sleepy.ui.component.HueSlider(value.floatValue * 360f, { value.floatValue = it / 360f }, tag.height(48.dp))
                                    "slider" -> GlassSlider(value.floatValue, { value.floatValue = it }, tag, enabled = enabled.value, steps = steps, onValueChangeFinished = { finished++ })
                                    "range" -> GlassRangeSlider(.2f..value.floatValue, { value.floatValue = it.endInclusive }, tag, 0f..1f, 0)
                                    else -> SegmentedSwitcher(listOf(0 to "First", 1 to "Second"), selected.intValue, { selected.intValue = it }, tag)
                                }
                            }
                            Spacer(Modifier.height(1200.dp))
                        }
                    }
                }
            }
        }
    }

    private fun dragAndRelease(kind: String, cancelGesture: Boolean = false) {
        show(kind)
        val control = compose.onNodeWithTag("control")
        control.performTouchInput {
            down(Offset(width * (if (kind == "range") .35f else .25f), centerY))
            advanceEventTime(300)
            moveBy(Offset(0f, -100f))
            moveBy(Offset(width * .65f, -80f))
            moveBy(Offset(0f, 40f))
        }
        compose.runOnIdle { assertEquals("active control must own vertical/diagonal movement outside its bounds", 0, scroll.value) }
        control.performTouchInput { if (cancelGesture) cancel() else up() }
        compose.runOnIdle {
            assertEquals(0, scroll.value)
            if (!cancelGesture && (style == UiStyle.LiquidGlass || kind == "slider" || kind == "hue")) when (kind) {
                "switch" -> assertTrue(checked.value)
                "slider", "range", "hue" -> assertTrue(value.floatValue > .5f)
                else -> assertEquals(1, selected.intValue)
            }
        }
        assertPageScrolls()
    }

    private fun assertPageScrolls() {
        compose.onNodeWithTag("page").performTouchInput {
            swipe(Offset(centerX, height - 30f), Offset(centerX, 40f), 400)
        }
        compose.runOnIdle { assertTrue("page must scroll after release", scroll.value > 0) }
    }

    @Test fun classicSliderVerticalDetourPreservesValueThenMovesHorizontally() {
        style = UiStyle.Classic
        show("slider")
        val control = compose.onNodeWithTag("control")
        control.performTouchInput {
            down(Offset(width * .3f, centerY))
            repeat(30) { moveBy(Offset(0f, -3f)) }
        }
        compose.runOnIdle {
            assertEquals(0, scroll.value)
            assertEquals(.3f, value.floatValue, .001f)
        }
        control.performTouchInput { moveBy(Offset(50f, 0f)) }
        val first = compose.runOnIdle {
            value.floatValue.also { assertTrue("first horizontal movement must update value: $it", it > .3f) }
        }
        control.performTouchInput { moveBy(Offset(50f, 80f)) }
        compose.runOnIdle {
            assertEquals(0, scroll.value)
            assertTrue(value.floatValue > first)
        }
        control.performTouchInput { up() }
        assertPageScrolls()
    }

    @Test fun classicSliderRtlDragAndTrackTap() {
        style = UiStyle.Classic
        direction = androidx.compose.ui.unit.LayoutDirection.Rtl
        show("slider")
        val control = compose.onNodeWithTag("control")
        control.performTouchInput { down(center); moveBy(Offset(-50f, -100f)) }
        compose.runOnIdle { assertTrue(value.floatValue > .3f); assertEquals(0, scroll.value) }
        control.performTouchInput { up(); click(Offset(width * .1f, centerY)) }
        compose.runOnIdle { assertTrue(value.floatValue > .8f); assertEquals(2, finished) }
        assertPageScrolls()
    }

    @Test fun classicSteppedSliderAccumulatesSmallMovementsAcrossGestures() {
        style = UiStyle.Classic
        steps = 3
        value.floatValue = .25f
        show("slider")
        val control = compose.onNodeWithTag("control")
        val railWidth = compose.onNodeWithTag("slider-track", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.width
        // Fifty individually sub-step deltas must accumulate to one quarter of the domain.
        val smallDelta = railWidth * .25f / 50f
        control.performTouchInput { down(center); repeat(50) { moveBy(Offset(smallDelta, 0f)) } }
        val first = compose.runOnIdle {
            assertEquals(.5f, value.floatValue, .001f)
            assertEquals(0, scroll.value)
            value.floatValue
        }
        control.performTouchInput { up(); down(center); repeat(50) { moveBy(Offset(smallDelta, 0f)) }; up() }
        compose.runOnIdle { assertEquals(first + .25f, value.floatValue, .001f); assertEquals(2, finished) }
        assertPageScrolls()
    }

    @Test fun classicSliderSecondFingerContinuesWithoutValueJump() {
        style = UiStyle.Classic
        show("slider")
        val control = compose.onNodeWithTag("control")
        control.performTouchInput { down(0, center); moveBy(0, Offset(40f, -80f)) }
        val first = compose.runOnIdle { value.floatValue }
        control.performTouchInput { down(1, center + Offset(90f, 0f)); up(0) }
        compose.runOnIdle { assertEquals(first, value.floatValue, .001f); assertEquals(0, finished) }
        control.performTouchInput { moveBy(1, Offset(40f, -80f)) }
        compose.runOnIdle { assertTrue(value.floatValue > first); assertEquals(0, scroll.value) }
        control.performTouchInput { up(1) }
        compose.runOnIdle { assertEquals(1, finished) }
        assertPageScrolls()
    }

    @Test fun classicSliderCancellationDoesNotFinishOrRetainLock() {
        style = UiStyle.Classic
        show("slider")
        val control = compose.onNodeWithTag("control")
        control.performTouchInput { down(center); moveBy(Offset(40f, -100f)); cancel() }
        compose.runOnIdle { assertEquals(0, finished); assertEquals(0, scroll.value) }
        assertPageScrolls()
    }

    @Test fun classicDisabledSliderAllowsScrollingWithoutValueChanges() {
        style = UiStyle.Classic
        enabled.value = false
        show("slider")
        compose.onNodeWithTag("control").performTouchInput { swipe(center, center + Offset(0f, -120f), 400) }
        compose.runOnIdle { assertTrue(scroll.value > 0); assertEquals(.3f, value.floatValue, .001f); assertEquals(0, finished) }
    }

    @Test fun classicSliderTapUpdatesValue() {
        style = UiStyle.Classic
        show("slider")
        compose.onNodeWithTag("control").performTouchInput { click(Offset(width * .8f, centerY)) }
        compose.runOnIdle { assertTrue("tap must update slider", value.floatValue > .6f) }
    }

    @Test fun classicSliderFineDragKeepsItsValueOnReleaseBelowTouchSlop() {
        style = UiStyle.Classic
        value.floatValue = .4f
        show("slider")
        val control = compose.onNodeWithTag("control")
        val thumbWidth = compose.onNodeWithTag("glass-slider-end", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.width
        control.performTouchInput {
            down(Offset(thumbWidth / 2f + (width - thumbWidth) * .4f, centerY))
            moveBy(Offset(1f, 0f))
        }
        val dragged = compose.runOnIdle {
            value.floatValue.also { assertTrue("a fine horizontal drag updates immediately", it > .4f) }
        }
        control.performTouchInput { up() }
        compose.runOnIdle {
            assertEquals("release must not replace the fine drag with its down position", dragged, value.floatValue, .00001f)
            assertEquals(1, finished)
            assertEquals(0, scroll.value)
        }
    }

    @Test fun classicSwitchTapStillWorks() {
        style = UiStyle.Classic
        show("switch")
        compose.onNodeWithTag("control").performTouchInput { click(center) }
        compose.runOnIdle { assertTrue(checked.value) }
    }

    @Test fun classicTabTapStillWorks() {
        style = UiStyle.Classic
        show("tabs")
        compose.onNodeWithText("Second").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, selected.intValue) }
    }

    @Test fun classicHueOwnsDrag() { style = UiStyle.Classic; dragAndRelease("hue") }
    @Test fun classicSliderOwnsDrag() { style = UiStyle.Classic; dragAndRelease("slider") }
    @Test fun classicSwitchOwnsDrag() { style = UiStyle.Classic; dragAndRelease("switch") }
    @Test fun classicTabsOwnDrag() { style = UiStyle.Classic; dragAndRelease("tabs") }
    @Test fun classicSliderCancelReleasesPage() { style = UiStyle.Classic; dragAndRelease("slider", true) }
    @Test fun classicSwitchCancelReleasesPage() { style = UiStyle.Classic; dragAndRelease("switch", true) }
    @Test fun classicTabsCancelReleasesPage() { style = UiStyle.Classic; dragAndRelease("tabs", true) }

    @Test fun switchOwnsVerticalAndDiagonalDrag() = dragAndRelease("switch")
    @Test fun sliderOwnsVerticalAndDiagonalDrag() = dragAndRelease("slider")
    @Test fun rangeOwnsVerticalAndDiagonalDrag() = dragAndRelease("range")
    @Test fun tabsOwnVerticalAndDiagonalDrag() = dragAndRelease("tabs")
    @Test fun cancelReleasesSwitch() = dragAndRelease("switch", true)
    @Test fun cancelReleasesSlider() = dragAndRelease("slider", true)
    @Test fun cancelReleasesTabs() = dragAndRelease("tabs", true)

    @Test fun secondaryFingerKeepsOwnershipUntilLastUp() {
        show("switch")
        val control = compose.onNodeWithTag("control")
        control.performTouchInput {
            down(0, center)
            down(1, center + Offset(5f, 0f))
            up(0)
            moveBy(1, Offset(0f, -120f))
        }
        compose.runOnIdle { assertEquals(0, scroll.value) }
        control.performTouchInput { up(1) }
        assertPageScrolls()
    }

    @Test fun disablingDuringGestureReleasesOwnership() {
        show("switch")
        compose.onNodeWithTag("control").performTouchInput { down(center) }
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithTag("page").performTouchInput { up() }
        compose.runOnIdle { assertFalse(checked.value) }
        assertPageScrolls()
    }

    @Test fun removingControlDoesNotLockPage() {
        show("switch")
        compose.onNodeWithTag("control").performTouchInput { down(center) }
        compose.runOnIdle { visible.value = false }
        compose.onNodeWithTag("page").performTouchInput { up() }
        assertPageScrolls()
    }

    @Test fun disabledControlAllowsScrolling() {
        enabled.value = false
        show("slider")
        compose.onNodeWithTag("control").performTouchInput { swipe(center, center + Offset(0f, -120f), 400) }
        compose.runOnIdle { assertTrue(scroll.value > 0) }
    }
}
