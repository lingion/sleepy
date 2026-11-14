package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.component.IntRangeSlider
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the production IntRangeSlider wrapper, including its ancestor input modifier. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GlassProductionSliderGestureTest {
    @get:Rule val compose = createComposeRule()

    @Test fun integerRangeKeepsItsPointerAfterVerticalDetourAndControlledEchoes() {
        val start = mutableIntStateOf(2)
        val end = mutableIntStateOf(8)
        lateinit var scroll: ScrollState
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalUiStyle provides UiStyle.LiquidGlass,
                    LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L },
                ) {
                    GlassHost {
                        scroll = rememberScrollState()
                        Column(Modifier.size(320.dp, 400.dp).testTag("page").verticalScroll(scroll)) {
                            Spacer(Modifier.height(80.dp))
                            IntRangeSlider(
                                summary = "第${start.intValue}–${end.intValue}周",
                                minValue = 1, maxValue = 20,
                                startValue = start.intValue, endValue = end.intValue,
                                onValueChange = { first, last -> start.intValue = first; end.intValue = last },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            Spacer(Modifier.height(1000.dp))
                        }
                    }
                }
            }
        }
        val page = compose.onNodeWithTag("page")
        val origin = page.fetchSemanticsNode().boundsInRoot.topLeft
        val thumb = compose.onNodeWithTag("glass-slider-end").fetchSemanticsNode().boundsInRoot.center - origin
        val pxPerDp = compose.density.density
        page.performTouchInput { down(thumb) }
        page.performTouchInput { moveBy(Offset(0f, -32f * pxPerDp)) }
        compose.runOnIdle {
            assertEquals("A vertical detour keeps the range unchanged", 8, end.intValue)
            assertEquals("The slider owns the page during the detour", 0, scroll.value)
        }
        page.performTouchInput { moveBy(Offset(48f * pxPerDp, 0f)) }
        val first = compose.runOnIdle {
            end.intValue.also { assertTrue("The original pointer must still move the end handle", it > 8) }
        }
        page.performTouchInput { moveBy(Offset(48f * pxPerDp, 20f * pxPerDp)) }
        compose.runOnIdle {
            assertTrue("An integer callback echo must preserve the next MOVE", end.intValue > first)
            assertEquals(2, start.intValue)
            assertEquals(0, scroll.value)
        }
        page.performTouchInput { up() }
        page.performTouchInput { swipe(Offset(centerX, height - 20f), Offset(centerX, 20f), 300) }
        compose.runOnIdle { assertTrue("The page scrolls again after release", scroll.value > 0) }
    }
}
