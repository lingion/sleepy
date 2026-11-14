package com.lingion.sleepy.ui.component.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSegmentedMotionTest {
    @Test fun heldRiseIsMonotoneAndMatchesTheNativeIntermediateSizes() {
        val motion = GlassControlMotion(0f, GlassControlMotionSpec.NativeSegmented)
        motion.update(0f, pressed = true, dragging = false)
        var previous = 0f
        repeat(80) {
            motion.tick(.008)
            assertTrue("segmented rise does not overshoot or recoil", motion.geometryLift in previous..1f)
            previous = motion.geometryLift
            // The physical clock starts at the first frame; the native trace's
            // approximately 20ms delivery latency is excluded from this fit.
            if (it == 7) assertEquals(12f, 24f * motion.geometryLift, 2f)
            if (it == 15) assertEquals(20f, 24f * motion.geometryLift, 2f)
        }
        assertEquals(24f, 24f * motion.geometryLift, .1f)
    }

    @Test fun quickTapContractsAfterTheLensIsRaisedWhileSelectionStillTravels() {
        val motion = GlassControlMotion(0f, GlassControlMotionSpec.NativeSegmented)
        motion.update(0f, pressed = true, dragging = false)
        repeat(6) { motion.tick(.008) }
        motion.update(1f, pressed = false, dragging = false, activation = 1)
        repeat(44) { motion.tick(.008) }
        assertTrue("native short tap is already contracting by 400ms", motion.lift < .4f)
    }

    @Test fun shortHoldOnTheSelectedSegmentContractsBeforeTheFullHeldSize() {
        val motion = GlassControlMotion(0f, GlassControlMotionSpec.NativeSegmented)
        motion.update(0f, pressed = true, dragging = false)
        repeat(6) { motion.tick(.008) }
        motion.update(0f, pressed = false, dragging = false)
        var peak = motion.geometryLift
        repeat(80) {
            motion.tick(.008)
            peak = maxOf(peak, motion.geometryLift)
        }
        assertTrue("a selected-segment short hold only partly expands the lens", peak > .1f && peak < .85f)
        assertEquals(0f, motion.lift, .001f)
    }
}
