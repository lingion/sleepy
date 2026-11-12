package com.lingion.sleepy.ui.component.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSegmentedCoalescedTapTest {
    @Test fun activationOnlyTapHasAFullSelectionPulseWithoutAnInputJump() {
        checkTap(pressObserved = false)
    }

    @Test fun pressAndReleaseBeforeTheFirstFrameHaveAFullSelectionPulseWithoutAnInputJump() {
        checkTap(pressObserved = true)
    }

    private fun checkTap(pressObserved: Boolean) {
        val motion = GlassControlMotion(0f, GlassControlMotionSpec.NativeSegmented)
        if (pressObserved) {
            // Both input updates can arrive before the first animation frame.
            motion.update(0f, pressed = true, dragging = false)
        }
        val elevation = motion.elevation.value
        val elevationVelocity = motion.elevation.velocity
        val position = motion.position.value
        val positionVelocity = motion.position.velocity

        motion.update(1f, pressed = false, dragging = false, activation = 1)

        assertEquals("release does not jump the lens height", elevation, motion.elevation.value, 0f)
        assertEquals("release preserves the current lift velocity", elevationVelocity, motion.elevation.velocity, 0f)
        assertEquals("activation does not jump the selection position", position, motion.position.value, 0f)
        assertEquals("activation preserves the current travel velocity", positionVelocity, motion.position.velocity, 0f)

        var peak = motion.geometryLift
        repeat(160) {
            motion.tick(.008)
            peak = maxOf(peak, motion.geometryLift)
        }

        assertTrue("a tap without a pressed frame still visibly lifts the lens: $peak", peak > .1f)
        // Direct B selection on iOS 26.5 reaches local 44 pt held bounds even
        // for a 66.7 ms tap. This differs from releasing an already selected A.
        assertTrue("a committed B selection completes the native pulse: $peak", peak >= .975f)
        assertEquals("the lens contracts fully after the tap", 0f, motion.lift, .001f)
        assertEquals("selection still reaches its destination", 1f, motion.position.value, .001f)
        assertFalse("the tap leaves no active animation", motion.isRunning)
    }
}
