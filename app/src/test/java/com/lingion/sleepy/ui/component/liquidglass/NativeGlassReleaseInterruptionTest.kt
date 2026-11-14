package com.lingion.sleepy.ui.component.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeGlassReleaseInterruptionTest {
    @Test fun releasingAfterTheFirstPeakDoesNotWaitForAnotherOscillation() {
        for (releaseMs in listOf(248, 272, 288, 320)) {
            val motion = GlassControlMotion(0f, GlassControlMotionSpec.Native)
            motion.update(0f, pressed = true, dragging = false)
            repeat(releaseMs / 8) { motion.tick(.008) }
            val before = motion.geometryLift
            motion.update(1f, pressed = false, dragging = false, activation = 1)
            repeat(10) { motion.tick(.008) }
            assertTrue("release at ${releaseMs}ms must contract within 80ms", motion.geometryLift < before - .15f)
        }
    }

    @Test fun pressingAgainDuringContractionKeepsPositionAndVelocityContinuous() {
        val motion = GlassControlMotion(0f, GlassControlMotionSpec.Native)
        motion.update(0f, pressed = true, dragging = false)
        repeat(80) { motion.tick(.008) }
        motion.update(1f, pressed = false, dragging = false, activation = 1)
        repeat(10) { motion.tick(.008) }
        val position = motion.elevation.value
        val velocity = motion.elevation.velocity
        motion.update(0f, pressed = true, dragging = false, activation = 1)
        assertEquals(position, motion.elevation.value, 0f)
        assertEquals(velocity, motion.elevation.velocity, 0f)
        repeat(80) { motion.tick(.008) }
        motion.update(0f, pressed = false, dragging = false, activation = 1)
        // The calibrated k160/ζ.70 release retains a small tail past 800ms.
        // Keep exact continuity above, and require exact rest within 1.2 seconds.
        repeat(150) { motion.tick(.008) }
        assertEquals(0f, motion.lift, 0f)
    }
}
