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
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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

    private fun show(enabled: Boolean = true, rtl: Boolean = false) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass,
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

    @Test fun heldThumbGrowsAroundItsCenterAndSettlesBack() {
        show()
        val thumb = compose.onNodeWithTag("glass-slider-end")
        val rest = thumb.getUnclippedBoundsInRoot()
        thumb.performTouchInput { down(center) }
        compose.waitForIdle()
        val held = thumb.getUnclippedBoundsInRoot()
        assertTrue(held.right - held.left > rest.right - rest.left && held.bottom - held.top > rest.bottom - rest.top)
        assertEquals((rest.left.value + rest.right.value) / 2, (held.left.value + held.right.value) / 2, 1f)
        thumb.performTouchInput { up() }
        compose.waitForIdle()
        val settled = thumb.getUnclippedBoundsInRoot()
        assertEquals((rest.right - rest.left).value, (settled.right - settled.left).value, 1f)
    }

    @Test fun tapSnapsAndCommitsOnceAndCreatesGhost() {
        show()
        compose.onNodeWithTag("slider").performTouchInput { click(Offset(width * .8f, centerY)) }
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
        compose.onNodeWithTag("slider").performTouchInput { click(Offset(width * .2f, centerY)) }
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
}
