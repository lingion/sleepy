package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Check the ordinary pixels that the transparent production thumb samples. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GlassSliderTrackCoverageTest {
    @get:Rule val compose = createComposeRule()

    @Test fun maximumSingleHasActiveRailUnderBothHalvesOfTheEndpointThumb() {
        track(0f, 1f)
        assertRail(Color.Red, 2, 18, 140, 261, 277)
    }

    @Test fun maximumSingleAlsoCoversTheMirroredEndpoint() {
        track(0f, 1f, rtl = true)
        assertRail(Color.Red, 2, 18, 140, 261, 277)
    }

    @Test fun fullRangeCoversBothOuterThumbHalves() {
        track(0f, 1f, range = true)
        assertRail(Color.Red, 2, 13, 140, 266, 277)
    }

    @Test fun intermediateFillStillEndsAtItsExistingThumbCenter() {
        track(0f, .25f)
        assertRail(Color.Red, 24, 77)
        val pixels = compose.onNodeWithTag("rail-source").captureToImage().toPixelMap()
        // Quarter center is 18.5 + (280 - 37) / 4 = 79.25, unchanged.
        assertEquals(pixels[90, 24], pixels[270, 24])
        assertEquals("The newly covered outer half is the same inactive rail", pixels[270, 24], pixels[277, 24])
    }

    @Test fun gradientRetainsItsValueAnchorsAndExtendsItsEndpointColors() {
        track(0f, .5f, brush = Brush.horizontalGradient(listOf(Color.Red, Color.Green, Color.Blue)))
        assertRail(Color.Red, 2, 18)
        assertRail(Color.Blue, 262, 277)
        val pixels = compose.onNodeWithTag("rail-source").captureToImage().toPixelMap()
        assertEquals(1f, pixels[140, 24].green, .02f)
    }

    private fun track(start: Float, end: Float, range: Boolean = false, rtl: Boolean = false, brush: Brush? = null) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                MaterialTheme {
                    Box(Modifier.background(Color.White).testTag("rail-source")) {
                        AppSliderTrack({ start }, { end }, tickCount = 11,
                            modifier = Modifier.width(280.dp),
                            inset = (GlassSliderThumbMetrics.width(range) / 2f).dp,
                            range = range, activeColor = Color.Red, trackBrush = brush)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertRail(expected: Color, vararg xs: Int) {
        val pixels = compose.onNodeWithTag("rail-source").captureToImage().toPixelMap()
        xs.forEach { x ->
            val actual = pixels[x, 24]
            assertEquals("rail R at x=$x", expected.red, actual.red, .02f)
            assertEquals("rail G at x=$x", expected.green, actual.green, .02f)
            assertEquals("rail B at x=$x", expected.blue, actual.blue, .02f)
        }
    }
}
