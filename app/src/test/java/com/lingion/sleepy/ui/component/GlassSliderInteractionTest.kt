package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.component.liquidglass.*
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
class GlassSliderInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val value = mutableFloatStateOf(.4f)
    private var commits = 0

    private fun show(enabled: Boolean = true, rtl: Boolean = false, style: UiStyle = UiStyle.LiquidGlass) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides style,
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    GlassHost {
                        Box(Modifier.padding(32.dp)) {
                            GlassSlider(value.floatValue, { value.floatValue = it },
                                Modifier.width(280.dp).testTag("slider"), enabled,
                                steps = 9, onValueChangeFinished = { commits++ },
                                initialValue = .4f, ghostTag = "ghost")
                        }
                    }
                }
            }
        }
    }

    @Test fun classicSliderTracksExternalUpdatesAndCommitsUserChanges() {
        show(style = UiStyle.Classic)
        val slider = compose.onNodeWithTag("slider")
        compose.runOnIdle { value.floatValue = .8f }
        slider.assert(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(.8f, 0f..1f, 9),
        ))
        compose.runOnIdle { assertEquals(0, commits) }
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(.2f) }
        compose.runOnIdle {
            assertEquals(.2f, value.floatValue, .001f)
            assertEquals(1, commits)
        }
        slider.assert(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(.2f, 0f..1f, 9),
        ))
    }

    @Test fun heldThumbGrowsAroundItsCenterAndSettlesBack() {
        show()
        val thumb = compose.onNodeWithTag("glass-slider-end")
        val rest = thumb.getUnclippedBoundsInRoot()
        assertEquals(37f, (rest.right - rest.left).value, 1f)
        assertEquals(24f, (rest.bottom - rest.top).value, 1f)
        thumb.performTouchInput { down(center) }
        compose.waitForIdle()
        val held = thumb.getUnclippedBoundsInRoot()
        assertEquals(57.35f, (held.right - held.left).value, 1f)
        assertEquals(37.2f, (held.bottom - held.top).value, 1f)
        assertTrue(held.right - held.left > rest.right - rest.left && held.bottom - held.top > rest.bottom - rest.top)
        assertEquals((rest.left.value + rest.right.value) / 2, (held.left.value + held.right.value) / 2, 1f)
        thumb.performTouchInput { up() }
        compose.waitForIdle()
        val settled = thumb.getUnclippedBoundsInRoot()
        assertEquals((rest.right - rest.left).value, (settled.right - settled.left).value, 1f)
        assertEquals((rest.bottom - rest.top).value, (settled.bottom - settled.top).value, 1f)
    }

    @Test fun tapSnapsAndCommitsOnceAndCreatesGhost() {
        show()
        val halfThumb = compose.onNodeWithTag("glass-slider-end").fetchSemanticsNode().boundsInRoot.width / 2f
        compose.onNodeWithTag("slider").performTouchInput { click(Offset(halfThumb + (width - halfThumb * 2) * .8f, centerY)) }
        compose.runOnIdle { assertEquals(.8f, value.floatValue, .001f); assertEquals(1, commits) }
        compose.onNodeWithTag("ghost").assertExists()
    }

    @Test fun accessibilityCanReturnToBaselineAndRemoveGhost() {
        show()
        val thumb = compose.onNodeWithTag("glass-slider-end")
        thumb.performSemanticsAction(SemanticsActions.SetProgress) { it(.8f) }
        compose.onNodeWithTag("ghost").assertExists()
        thumb.performSemanticsAction(SemanticsActions.SetProgress) { it(.4f) }
        compose.onNodeWithTag("ghost").assertDoesNotExist()
        compose.runOnIdle { assertEquals(.4f, value.floatValue, .001f); assertEquals(2, commits) }
    }

    @Test fun disabledSliderRejectsTouchAndAccessibilityChanges() {
        show(enabled = false)
        compose.onNodeWithTag("slider").performTouchInput { click(Offset(width * .8f, centerY)) }
        compose.onNodeWithTag("glass-slider-end").assertIsNotEnabled()
            .performSemanticsAction(SemanticsActions.SetProgress) { assertFalse(it(.8f)) }
        compose.runOnIdle { assertEquals(.4f, value.floatValue, .001f); assertEquals(0, commits) }
    }

    @Test fun rtlTapUsesLogicalValueDirection() {
        show(rtl = true)
        val halfThumb = compose.onNodeWithTag("glass-slider-end").fetchSemanticsNode().boundsInRoot.width / 2f
        compose.onNodeWithTag("slider").performTouchInput { click(Offset(halfThumb + (width - halfThumb * 2) * .2f, centerY)) }
        compose.runOnIdle { assertEquals(.8f, value.floatValue, .001f) }
    }

    @Test fun rangeThumbsCannotCrossAndExposeIndependentProgress() {
        val range = mutableStateOf(2f..7f)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        GlassRangeSlider(range.value, { range.value = it }, Modifier.width(280.dp), 1f..10f, 8)
                    }
                }
            }
        }
        compose.onNodeWithTag("glass-slider-start").performSemanticsAction(SemanticsActions.SetProgress) { it(9f) }
        compose.runOnIdle { assertEquals(7f, range.value.start); assertEquals(7f, range.value.endInclusive) }
        compose.onNodeWithTag("glass-slider-end").performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        compose.runOnIdle { assertEquals(7f, range.value.start); assertEquals(7f, range.value.endInclusive) }
    }

    @Test fun rangeAccessibilityUsesFullDomainStopSpacingAndRejectsUnchangedProgress() {
        val range = mutableStateOf(2f..7f)
        var updates = 0
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        GlassRangeSlider(range.value, { range.value = it; updates++ },
                            Modifier.width(280.dp), 1f..20f, 18)
                    }
                }
            }
        }
        val startThumb = compose.onNodeWithTag("glass-slider-start")
        val startInfo = startThumb.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(ProgressBarRangeInfo(2f, 1f..7f, 5), startInfo)
        val startStep = (startInfo.range.endInclusive - startInfo.range.start) / (startInfo.steps + 1)
        startThumb.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(startInfo.current + startStep)) }
        compose.runOnIdle { assertEquals(3f, range.value.start, 0f); assertEquals(1, updates) }

        val endThumb = compose.onNodeWithTag("glass-slider-end")
        val endInfo = endThumb.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(ProgressBarRangeInfo(7f, 3f..20f, 16), endInfo)
        val endStep = (endInfo.range.endInclusive - endInfo.range.start) / (endInfo.steps + 1)
        endThumb.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(endInfo.current - endStep)) }
        compose.runOnIdle { assertEquals(6f, range.value.endInclusive, 0f); assertEquals(2, updates) }
        endThumb.performSemanticsAction(SemanticsActions.SetProgress) { assertFalse(it(6f)) }
        startThumb.performSemanticsAction(SemanticsActions.SetProgress) { assertFalse(it(3.1f)) }
        compose.runOnIdle { assertEquals(2, updates) }
    }

    @Test fun hueTapCommitsTheValueDeliveredBeforeRecomposition() {
        var delivered = 0f
        var committed = -1f
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        HueSlider(0f, { delivered = it }, Modifier.width(280.dp).testTag("hue"),
                            onHueCommit = { committed = it })
                    }
                }
            }
        }
        compose.onNodeWithTag("hue").performTouchInput { click(Offset(width * .8f, centerY)) }
        compose.runOnIdle {
            assertTrue(delivered > 250f)
            assertEquals("release must commit the delivered hue, even before a parent redraw", delivered, committed, .001f)
        }
    }

    @Test fun coincidentRangeThumbsCanBeSeparatedInEitherDirection() {
        val range = mutableStateOf(5f..5f)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        GlassRangeSlider(range.value, { range.value = it },
                            Modifier.width(280.dp).testTag("range"), 1f..10f, 8)
                    }
                }
            }
        }
        val slider = compose.onNodeWithTag("range")
        slider.performTouchInput {
            down(Offset(width * 4 / 9f, centerY))
            moveTo(Offset(width * .1f, centerY), 200)
            up()
        }
        compose.runOnIdle { assertTrue(range.value.start <= 2f); assertEquals(5f, range.value.endInclusive) }
        slider.performTouchInput {
            down(Offset(width * 4 / 9f, centerY))
            moveTo(Offset(width * .9f, centerY), 200)
            up()
        }
        compose.runOnIdle { assertTrue(range.value.start <= 2f); assertTrue(range.value.endInclusive >= 9f) }
    }

    @Test fun coincidentRangeThumbsCanBeSeparatedByRailTaps() = verifyCoincidentRailTaps(rtl = false)

    @Test fun coincidentRangeThumbsCanBeSeparatedByRailTapsInRtl() = verifyCoincidentRailTaps(rtl = true)

    private fun verifyCoincidentRailTaps(rtl: Boolean) {
        val range = mutableStateOf(5f..5f)
        var updates = 0
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass,
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    GlassHost {
                        GlassRangeSlider(range.value, { range.value = it; updates++ },
                            Modifier.width(280.dp).testTag("range"), 0f..10f, 0)
                    }
                }
            }
        }
        val slider = compose.onNodeWithTag("range")
        slider.performTouchInput { click(Offset(width * (if (rtl) .9f else .1f), centerY)) }
        compose.runOnIdle {
            assertTrue("A lower rail tap reopens the start thumb", range.value.start < 2f)
            assertEquals(5f, range.value.endInclusive, 0f)
            assertEquals("The rail tap emits one changed range", 1, updates)
            range.value = 5f..5f
        }
        slider.performTouchInput { click(Offset(width * (if (rtl) .1f else .9f), centerY)) }
        compose.runOnIdle {
            assertEquals(5f, range.value.start, 0f)
            assertTrue("A higher rail tap reopens the end thumb", range.value.endInclusive > 8f)
            assertEquals(2, updates)
        }
    }
}
