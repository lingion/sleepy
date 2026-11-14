package com.lingion.sleepy.ui.component.liquidglass

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class GlassControlMotionTest {
    private fun advance(motion: GlassControlMotion, milliseconds: Int) {
        repeat(milliseconds / 8) { motion.tick(.008) }
    }

    @Test fun riseAndFallFollowTheDockSpringRatherThanAnImmediateFullLens() {
        val motion = GlassControlMotion(0f)
        motion.update(0f, pressed = true, dragging = false)
        advance(motion, 80)
        assertTrue(motion.lift in .4f.. .7f)
        advance(motion, 80)
        assertTrue(motion.lift in .85f.. .95f)
        advance(motion, 640)
        assertEquals(1f, motion.lift, .001f)
        motion.update(0f, pressed = false, dragging = false)
        advance(motion, 80)
        assertTrue("release must already be lowering", motion.lift in .3f.. .7f)
        advance(motion, 800)
        assertEquals(0f, motion.lift, 0f)
        assertFalse(motion.isRunning)
    }

    @Test fun quickTapCompletesItsRiseBeforeItStartsLowering() {
        val motion = GlassControlMotion(0f)
        motion.update(1f, pressed = false, dragging = false, activation = 1)
        var peak = 0f
        var firstFall = 0f
        var previous = 0f
        repeat(220) {
            motion.tick(.008)
            peak = maxOf(peak, motion.lift)
            if (firstFall == 0f && motion.lift < previous) firstFall = previous
            previous = motion.lift
        }
        assertTrue(peak >= FlClashDockPhysics.LIFT_SNAP)
        assertTrue(firstFall >= FlClashDockPhysics.LIFT_SNAP)
        assertEquals(1f, motion.position.value, .001f)
        assertEquals(0f, motion.lift, 0f)
    }

    @Test fun nativeShortTapKeepsTheFullLensThroughMostOfItsTravel() {
        val motion = GlassControlMotion(0f, GlassControlMotionSpec.Native, 22f)
        motion.update(0f, pressed = true, dragging = true)
        repeat(67) { motion.tick(.001) }
        motion.update(1f, pressed = false, dragging = false, activation = 1)
        repeat(167) { motion.tick(.001) }
        // iOS 26.5 ordinary 66.7 ms taps: DOWN+234 ms local width ~58.83 pt.
        // Its held-release trace contracts earlier and is a separate behavior.
        assertTrue("short tap must not collapse at the rise's first peak", motion.geometryLift >= .98f)
        assertTrue("the complete lens is still travelling", motion.position.value in .75f.. .95f)
        repeat(368) { motion.tick(.001) }
        assertEquals(0f, motion.lift, .005f)
        assertEquals(1f, motion.position.value, .005f)
    }

    @Test fun nativeHeldReleaseUsesTheSlowerLocalContourContraction() {
        val motion = GlassControlMotion(0f, GlassControlMotionSpec.Native, 22f)
        motion.update(0f, pressed = true, dragging = true)
        repeat(700) { motion.tick(.001) }
        motion.update(1f, pressed = false, dragging = false, activation = 1)
        var elapsed = 0
        // Local bounds from iOS 26.5; deformation of the world box is separate.
        for ((millis, expectedWidth) in listOf(84 to 51.25f, 150 to 43.60f, 200 to 39.61f, 251 to 37.29f)) {
            repeat(millis - elapsed) { motion.tick(.001) }
            elapsed = millis
            assertEquals("release +$millis ms", expectedWidth, 37f + 21f * motion.geometryLift, 1f)
        }
    }

    @Test fun movementStretchesAndBrakingSquashesBeforeReturningToRest() {
        val motion = GlassControlMotion(0f, travelDistanceDp = 112f)
        motion.update(0f, pressed = true, dragging = false)
        advance(motion, 640)
        var maximum = 0f
        repeat(20) { frame ->
            motion.update((frame + 1) / 20f, pressed = true, dragging = true)
            motion.tick(.016)
            maximum = maxOf(maximum, motion.stretch)
        }
        assertTrue("moving lens must stretch with speed", maximum > .05f)
        motion.update(1f, pressed = true, dragging = false)
        var minimum = 0f
        repeat(100) { motion.tick(.016); minimum = minOf(minimum, motion.stretch) }
        assertTrue("braking must visibly squash and rebound", minimum < -.005f)
        assertEquals(0f, motion.stretch, .004f)
    }

    @Test fun reversingADragKeepsVelocityContinuousAndCanBeInterrupted() {
        val motion = GlassControlMotion(0f)
        motion.update(1f, pressed = true, dragging = true)
        advance(motion, 64)
        val velocity = motion.position.velocity
        val position = motion.position.value
        assertTrue(velocity > 0f)
        motion.update(0f, pressed = true, dragging = true)
        assertEquals(velocity, motion.position.velocity, 0f)
        assertEquals(position, motion.position.value, 0f)
        advance(motion, 80)
        assertTrue(motion.position.velocity < 0f)
    }

    @Test fun reducedMotionSettlesWithoutLeavingAnActiveFrameLoop() {
        val motion = GlassControlMotion(0f)
        motion.update(1f, pressed = false, dragging = false, activation = 1)
        advance(motion, 32)
        motion.finish()
        assertEquals(1f, motion.position.value, 0f)
        assertEquals(0f, motion.lift, 0f)
        assertFalse(motion.isRunning)
    }

    @Test fun selectingAnUnselectedNativeSegmentCompletesThePulseAfterTouchUp() {
        val motion = GlassControlMotion(0f, GlassControlMotionSpec.NativeSegmented)
        motion.update(1f, pressed = false, dragging = false, activation = 1)
        var peak = 0f
        repeat(200) { motion.tick(.008); peak = maxOf(peak, motion.geometryLift) }
        assertTrue("native B commit reaches held geometry before lowering: $peak", peak >= .975f)
        assertEquals(0f, motion.geometryLift, .001f)
        assertEquals(1f, motion.position.value, .001f)
        assertFalse(motion.isRunning)
    }

    @Test fun dynamicMeasuredGeometryKeepsBothTrackEdgesOutsideTheLens() {
        val height = glassLensHeight(52f, .24f, minimum = 44f)
        assertEquals(44f, height, 0f)
        assertTrue(glassLensWidth(120f, .24f) > 120f)
        assertTrue(abs((42f - height) / 2f) >= 1f)
    }
}
