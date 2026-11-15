package com.lingion.sleepy.ui.component.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropAppearanceHysteresisTest {
    private fun candidate(dark: Boolean = true) = BackdropAppearanceHysteresis(
        dark, enterLightLuma = 223.5f / 255f, enterDarkLuma = 153.5f / 255f,
    )

    @Test fun sameGrayRetainsDifferentStatesOnTheMeasuredRiseAndReturnPaths() {
        // Native 93 establishes 217 dark / 230 light and 179 dark on the rise,
        // 179 light on return. The thresholds themselves remain engineering choices.
        val state = candidate()
        val gray = listOf(179, 204, 217, 230, 204, 179, 128, 77, 26)
        val expected = listOf(true, true, true, false, false, false, true, true, true)
        assertEquals(expected, gray.map { state.accept(it / 255f) })
    }

    @Test fun deadBandRetainsHistoryAndInvalidSamplesCannotChangeIt() {
        val dark = candidate()
        val light = candidate(false)
        listOf(.61f, .7f, .8f, .87f, Float.NaN, Float.POSITIVE_INFINITY, -1f, 2f).forEach {
            assertTrue(dark.accept(it))
            assertFalse(light.accept(it))
        }
        assertFalse(dark.accept(1f))
        assertTrue(light.accept(0f))
    }

    @Test fun transitionsIncludeTheirExplicitBoundaryAndNewOwnerResetsHistory() {
        val state = candidate()
        assertFalse(state.accept(state.enterLightLuma))
        assertTrue(state.accept(state.enterDarkLuma))
        state.accept(1f)
        assertTrue(candidate().dark)
    }

    @Test fun thresholdsMustBeFiniteOrderedAndNormalized() {
        listOf(0f to 0f, .7f to .6f, -.1f to .8f, .2f to 1.1f,
            Float.NaN to .8f).forEach { (low, high) ->
            var rejected = false
            try { BackdropAppearanceHysteresis(true, high, low) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        }
    }
}
