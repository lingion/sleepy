package com.lingion.sleepy.ui.component.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeSegmentedEndpointTest {
    private val nativePitch = 111.75f

    @Test fun slowForwardHoldPreservesTheMeasuredEndpointOverdrive() {
        // Native batch65: DOWN75.5, last resting seat299, held finger367.
        val rawLogicalIndex = 2f + 68f / nativePitch
        val lensPosition = nativeSegmentedDragPosition(rawLogicalIndex, 3, nativePitch)
        assertEquals(9.085020f, (lensPosition - 2f) * nativePitch, .001f)
    }

    @Test fun slowReverseHoldPreservesTheMeasuredEndpointOverdrive() {
        // Native batch65: DOWN299, first resting seat75.5, held finger8.
        val rawLogicalIndex = -67.5f / nativePitch
        val lensPosition = nativeSegmentedDragPosition(rawLogicalIndex, 3, nativePitch)
        assertEquals(-9.068702f, lensPosition * nativePitch, .001f)
    }

    @Test fun lateForwardMovementMatchesTheRetainedNativeCenters() {
        // Native frames494,500,507. These are measured centers, not formula outputs.
        val cases = listOf(
            53.5f to 8.581555f,
            60.5f to 8.841474f,
            67.5f to 9.063488f,
        )
        for ((overshootDp, expectedDp) in cases) {
            val position = nativeSegmentedDragPosition(2f + overshootDp / nativePitch, 3, nativePitch)
            assertEquals("forward overdrive $overshootDp", expectedDp, (position - 2f) * nativePitch, .12f)
        }
    }

    @Test fun lateReverseMovementMatchesTheRetainedNativeCenters() {
        // Native frames887,894,900; the moving lens retains small spring transients.
        val cases = listOf(
            -53.5f to -8.549393f,
            -60.5f to -8.885778f,
            -67f to -9.029914f,
        )
        for ((overshootDp, expectedDp) in cases) {
            val position = nativeSegmentedDragPosition(overshootDp / nativePitch, 3, nativePitch)
            assertEquals("reverse overdrive $overshootDp", expectedDp, position * nativePitch, .12f)
        }
    }

    @Test fun positionsInsideTheTrackKeepTheirLogicalCoordinates() {
        for (position in listOf(0f, .25f, 1f, 1.75f, 2f)) {
            assertEquals(position, nativeSegmentedDragPosition(position, 3, nativePitch), 0f)
        }
    }

    @Test fun physicalOverdriveDoesNotDependOnSeatPitchOrItsSign() {
        for (pitch in listOf(80f, -80f, 160f, -160f)) {
            val magnitude = kotlin.math.abs(pitch)
            val position = nativeSegmentedDragPosition(1f + 68f / magnitude, 2, pitch)
            assertEquals("pitch $pitch", 9.085020f, (position - 1f) * magnitude, .001f)
        }
    }

    @Test fun aSingleSeatAndAnUnmeasuredPitchKeepTheBoundedPosition() {
        assertEquals(0f, nativeSegmentedDragPosition(4f, 1, nativePitch), 0f)
        assertEquals(2f, nativeSegmentedDragPosition(4f, 3, 0f), 0f)
    }
}
