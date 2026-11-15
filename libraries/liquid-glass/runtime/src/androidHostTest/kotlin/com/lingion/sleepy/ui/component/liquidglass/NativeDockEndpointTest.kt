package com.lingion.sleepy.ui.component.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeDockEndpointTest {
    private val nativePitch = 236.75f / 3f

    @Test fun fourTabForwardHeldTargetMatchesTheNativeLocalEndpoint() {
        // Native batch65: finger362, last resting seat305.875; held local lens+4.
        val target = nativeDockDragPosition(3f + 56.125f / nativePitch, 4, nativePitch)
        assertEquals(4f, (target - 3f) * nativePitch, .001f)
    }

    @Test fun fourTabReverseHeldTargetMatchesTheNativeLocalEndpoint() {
        // Native batch65: finger13, first resting seat69.125; held local lens-4.
        val target = nativeDockDragPosition(-56.125f / nativePitch, 4, nativePitch)
        assertEquals(-4f, target * nativePitch, .001f)
    }

    @Test fun aBoundedTargetKeepsTheContinuingSpringOutsideFourDp() {
        // Native moving frames538/997 are outside±4 before converging on the held target.
        for ((seat, observedDp, raw) in listOf(
            Triple(3f, 4.354282f, 3f + 56.125f / nativePitch),
            Triple(0f, -4.352728f, -56.125f / nativePitch),
        )) {
            val spring = SpringState(seat + observedDp / nativePitch)
            val target = nativeDockDragPosition(raw, 4, nativePitch)
            spring.springTo(target, NativeDockPosition.FollowSpring)
            spring.tickDelta(.016)
            val continuedDp = (spring.value - seat) * nativePitch
            assertEquals("physical target", kotlin.math.sign(observedDp) * 4f,
                (spring.target - seat) * nativePitch, .001f)
            assertTrue("the output retains its transient beyond the target", kotlin.math.abs(continuedDp) > 4f)
            assertTrue("the continuing spring approaches the target", kotlin.math.abs(continuedDp) < kotlin.math.abs(observedDp))
        }
    }

    @Test fun thePhysicalBoundUsesPitchMagnitudeForEitherLayoutDirection() {
        for (pitch in listOf(60f, -60f, 120f, -120f)) {
            val magnitude = kotlin.math.abs(pitch)
            val target = nativeDockDragPosition(3f + 20f / magnitude, 4, pitch)
            assertEquals("pitch $pitch", 4f, (target - 3f) * magnitude, .001f)
        }
    }

    @Test fun inRangePositionsAndSingleSeatGuardsRemainBounded() {
        for (position in listOf(0f, .25f, 1.5f, 3f)) {
            assertEquals(position, nativeDockDragPosition(position, 4, nativePitch), 0f)
        }
        assertEquals(0f, nativeDockDragPosition(-1f, 1, nativePitch), 0f)
        assertEquals(0f, nativeDockDragPosition(2f, 0, nativePitch), 0f)
    }

    @Test fun unmeasuredTabCountsRetainTheirExistingLogicalRubberBand() {
        assertEquals(2.154f, nativeDockDragPosition(2.5f, 3, nativePitch), .000001f)
        assertEquals(-.154f, nativeDockDragPosition(-.5f, 3, nativePitch), .000001f)
        assertEquals(1.154f, nativeDockDragPosition(1.5f, 2, nativePitch), .000001f)
    }

    @Test fun anUnmeasuredFourTabPitchDoesNotProduceOverpull() {
        assertEquals(3f, nativeDockDragPosition(4f, 4, 0f), 0f)
    }
}
