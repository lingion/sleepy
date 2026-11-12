package com.lingion.sleepy.ui.component.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SliderVisualsTest {
    @Test fun denseBusinessStopsAreSubsampledWithoutDroppingEitherEndpoint() {
        val ticks = sliderTickFractions(10_001, width = 240f, minimumSpacing = 12f)
        assertEquals(21, ticks.size)
        assertEquals(0f, ticks.first(), 0f)
        assertEquals(1f, ticks.last(), 0f)
        assertTrue(ticks.zipWithNext().all { (a, b) -> b > a })
        // The visual sampler cannot change the caller's 10,001 selectable values.
        assertTrue(ticks.all { kotlin.math.abs(it * 10_000 - kotlin.math.round(it * 10_000)) < .001f })
    }

    @Test fun sparseStopsStayExactAndNarrowTracksStillRetainEndpoints() {
        assertEquals(listOf(0f, .25f, .5f, .75f, 1f), sliderTickFractions(5, 240f, 12f))
        assertEquals(listOf(0f, 1f), sliderTickFractions(100, 1f, 12f))
    }

    @Test fun circleCenterAndGhostFollowTheSameMirroredPath() {
        assertEquals(16f, sliderVisualX(280f, 0f, 16f, false), 0f)
        assertEquals(264f, sliderVisualX(280f, 1f, 16f, false), 0f)
        assertEquals(sliderVisualX(280f, .25f, 16f, false), sliderVisualX(280f, .75f, 16f, true), 0f)
        assertEquals(12f, sliderVisualX(280f, -1f, 12f, false), 0f)
    }
}
