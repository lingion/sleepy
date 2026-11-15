package com.lingion.sleepy.ui.component.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSliderEndpointTest {
    private fun visual(raw: Float, business: Float = raw.coerceIn(0f, 1f), width: Float = 335f) =
        nativeSliderVisualPosition(raw, business, controlWidthDp = width, thumbTravelDp = width - 37f)

    @Test fun measuredHeldEndpointsUseActualExcessAfterTheBusinessBoundary() {
        // Native70 last-300ms public positions. The family was selected
        // retrospectively, with +.25pt and +10.25pt as its two training labels.
        for ((excess, expected) in listOf(
            .25f to .154294541709f,
            2.25f to 1.273257790368f,
            5.25f to 2.641662468514f,
            10.25f to 4.353380581148f,
        )) {
            assertEquals(expected, (visual(1f + excess / 335f) - 1f) * 298f, .0001f)
        }
        for ((excess, expected) in listOf(
            1.75f to -1.011321118611f,
            4.75f to -2.435055603080f,
            9.75f to -4.206609071274f,
        )) {
            assertEquals(expected, visual(-excess / 335f) * 298f, .0001f)
        }
    }

    @Test fun theUnsaturatedZeroReverseCaseRetainsOrdinaryBusinessTravel() {
        val fraction = .25f / 335f
        assertEquals(fraction, visual(fraction), 0f)
        assertEquals(.222388061462f, visual(fraction) * 298f, .000001f)
    }

    @Test fun inRangeSnappingAndExactEndpointBusinessTargetsHaveNoAddedOffset() {
        assertEquals(.25f, visual(.23f, business = .25f), 0f)
        assertEquals(0f, visual(0f), 0f)
        assertEquals(1f, visual(1f), 0f)
    }

    @Test fun responsivePolicyKeepsTheSameDpPullForTheSamePhysicalExcess() {
        // Only 335pt native width was measured. Other widths deliberately
        // retain the same physical dp response and bound, rather than fitting
        // a new native width law or normalizing raw excess by width.
        val at335 = (visual(1f + 10.25f / 335f) - 1f) * 298f
        val at670 = (visual(1f + 10.25f / 670f, width = 670f) - 1f) * 633f
        assertEquals(at335, at670, .0001f)
    }

    @Test fun extremePullIsFiniteAndBoundedAndInvalidGeometryAddsNoPull() {
        val huge = visual(Float.MAX_VALUE)
        assertTrue(huge.isFinite())
        assertTrue((huge - 1f) * 298f <= 13.621f)
        assertTrue(visual(-Float.MAX_VALUE) * 298f >= -13.621f)
        assertEquals(.8f, nativeSliderVisualPosition(Float.NaN, .8f, 335f, 298f), 0f)
        assertEquals(1f, nativeSliderVisualPosition(1.1f, 1f, 0f, 0f), 0f)
    }
}
