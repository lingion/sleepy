package com.lingion.sleepy.ui.component.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassDockMotionTest {
    private fun springs(motion: GlassDockMotion) = listOf(
        motion.position, motion.lift, motion.swell, motion.pull,
        motion.jelly, motion.glow, motion.glowSpread,
    )

    private fun press(motion: GlassDockMotion, target: Float = 2f) {
        motion.distancePerPosition = 86f
        motion.position.springTo(target, FlClashSprings.TravelSpring)
        motion.lift.springTo(1f, FlClashSprings.LiftSpring)
        motion.swell.springTo(1f, FlClashSprings.PressSpring)
        motion.pull.springTo(12f, FlClashSprings.TrackSpring)
        motion.glow.springTo(1f, FlClashSprings.GlowInSpring)
        motion.glowSpread.jumpTo(0f)
        motion.dropping = false
    }

    private fun release(motion: GlassDockMotion, target: Float = 1f) {
        motion.position.springTo(target, FlClashSprings.TravelSpring)
        motion.pull.springTo(0f, FlClashSprings.PressSpring)
        motion.glow.springTo(0f, FlClashSprings.GlowOutSpring)
        motion.glowSpread.springTo(1f, FlClashSprings.GlowOutSpring)
        motion.swell.springTo(0f, FlClashSprings.PressSpring)
        motion.dropping = true
    }

    private fun assertStopped(motion: GlassDockMotion, dragging: Boolean = false) {
        springs(motion).forEach {
            assertEquals(it.target, it.value, 0f)
            assertEquals(0f, it.velocity, 0f)
            assertFalse(it.isRunning)
        }
        assertFalse(motion.dropping)
        assertFalse(motion.isRunning)
        assertFalse(motion.needsFrame(dragging, scaleFactor = 0f))
    }

    @Test fun disablingAnimationMidDragSettlesAllSevenSpringsWithoutAnIdleFrameLoop() {
        val motion = GlassDockMotion(0f)
        press(motion)
        repeat(4) { motion.advance(.016, 1f, held = true, dragging = true) }
        assertTrue(motion.position.velocity > 0f)
        assertTrue(motion.jelly.isRunning)
        motion.glowSpread.springTo(.4f, FlClashSprings.GlowOutSpring)

        motion.advance(.016, 0f, held = true, dragging = true)

        assertEquals(2f, motion.position.value, 0f)
        assertEquals(1f, motion.lift.value, 0f)
        assertEquals(1f, motion.swell.value, 0f)
        assertEquals(12f, motion.pull.value, 0f)
        assertEquals(0f, motion.jelly.value, 0f)
        assertEquals(1f, motion.glow.value, 0f)
        assertEquals(.4f, motion.glowSpread.value, 0f)
        assertStopped(motion, dragging = true)
    }

    @Test fun disabledAnimationFollowsNewHeldTargetsAndReturnsToRestOnRelease() {
        val motion = GlassDockMotion(0f)
        press(motion)
        motion.advance(.016, 0f, held = true, dragging = true)
        motion.position.springTo(.7f, FlClashSprings.FollowSpring)
        motion.pull.springTo(-8f, FlClashSprings.TrackSpring)
        assertTrue(motion.needsFrame(dragging = true, scaleFactor = 0f))

        motion.advance(.016, 0f, held = true, dragging = true)
        assertEquals(.7f, motion.position.value, 0f)
        assertEquals(-8f, motion.pull.value, 0f)
        assertEquals(1f, motion.lift.value, 0f)
        assertStopped(motion, dragging = true)

        release(motion)
        motion.advance(.016, 0f, held = false, dragging = false)
        assertEquals(1f, motion.position.value, 0f)
        assertEquals(0f, motion.lift.value, 0f)
        assertEquals(0f, motion.swell.value, 0f)
        assertEquals(0f, motion.pull.value, 0f)
        assertEquals(0f, motion.glow.value, 0f)
        assertEquals(1f, motion.glowSpread.value, 0f)
        assertStopped(motion)
    }

    @Test fun disablingAnimationDuringAQuickReleasedRiseClearsThePendingDrop() {
        val motion = GlassDockMotion(0f)
        press(motion)
        motion.advance(.016, 1f, held = true, dragging = false)
        release(motion)
        assertEquals(1f, motion.lift.target, 0f)
        assertTrue(motion.dropping)

        motion.advance(.016, 0f, held = false, dragging = false)

        assertEquals(0f, motion.lift.value, 0f)
        assertStopped(motion)
    }

    @Test fun positiveAnimationScalePreservesTheSameSpringTrajectoryAtEquivalentTimes() {
        val normal = GlassDockMotion(0f)
        val slowed = GlassDockMotion(0f)
        press(normal)
        press(slowed)
        repeat(30) {
            normal.advance(.016, 1f, held = true, dragging = true)
            slowed.advance(.032, 2f, held = true, dragging = true)
            assertEquivalent(normal, slowed)
        }
        release(normal)
        release(slowed)
        repeat(100) {
            normal.advance(.016, 1f, held = false, dragging = false)
            slowed.advance(.032, 2f, held = false, dragging = false)
            assertEquivalent(normal, slowed)
        }
    }

    @Test fun normalMotionRetainsTheNearDestinationReleaseGate() {
        val motion = GlassDockMotion(0f)
        motion.lift.jumpTo(1f)
        release(motion, target = 2f)
        motion.advance(.016, 1f, held = false, dragging = false)

        assertTrue(motion.dropping)
        assertEquals(1f, motion.lift.target, 0f)
        var lowered = false
        repeat(200) {
            motion.advance(.016, 1f, held = false, dragging = false)
            lowered = lowered || motion.lift.target == 0f
        }
        assertTrue(lowered)
        assertFalse(motion.isRunning)
        assertEquals(0f, motion.lift.value, 0f)
    }

    private fun assertEquivalent(expected: GlassDockMotion, actual: GlassDockMotion) {
        springs(expected).zip(springs(actual)).forEach { (first, second) ->
            assertEquals(first.value, second.value, 1e-6f)
            assertEquals(first.velocity, second.velocity, 1e-6f)
            assertEquals(first.isRunning, second.isRunning)
        }
        assertEquals(expected.dropping, actual.dropping)
    }
}
