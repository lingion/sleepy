package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DockTimedInputTest {
    private fun frame(ms: Long, previousMs: Long?) = ActiveFrame(ms * 1_000_000L, previousMs?.times(1_000_000L))
    private fun motion() = GlassDockMotion(0f).also { it.distancePerPosition = 69f }

    @Test fun staleFramePreservesOldTargetAndMomentumUntilTheFirstEligiblePressOrigin() {
        val motion = motion()
        val oldPosition = SpringState(0f)
        motion.position.springTo(1f, FlClashSprings.TravelSpring)
        oldPosition.springTo(1f, FlClashSprings.TravelSpring)
        motion.advanceFrame(frame(100, null), 1f)
        val epoch = motion.press(3f, 110_000_000L, sampleUptimeMillis = 80, glowAt = Offset(12f, 24f))

        motion.advanceFrame(frame(108, 100), 1f)
        oldPosition.tickDelta(.008)
        assertNull(motion.appliedPressEpoch)
        assertEquals(1f, motion.position.target, 0f)
        assertEquals(oldPosition.value, motion.position.value, 1e-6f)
        assertEquals(oldPosition.velocity, motion.position.velocity, 1e-6f)
        assertEquals(0f, motion.lift.value, 0f)
        assertEquals(Offset.Zero, motion.glowAt)

        motion.advanceFrame(frame(116, 108), 1f)
        oldPosition.tickDelta(.008)
        assertEquals(epoch, motion.appliedPressEpoch)
        assertEquals(3f, motion.position.target, 0f)
        assertEquals(oldPosition.value, motion.position.value, 1e-6f)
        assertEquals(oldPosition.velocity, motion.position.velocity, 1e-6f)
        assertTrue(motion.position.velocity > 0f)
        assertEquals(0f, motion.lift.value, 0f)
        assertEquals(0f, motion.jelly.value, 0f)
        assertEquals(0f, motion.stretchY, 0f)
        assertEquals(Offset(12f, 24f), motion.glowAt)

        oldPosition.springTo(3f, FlClashSprings.TravelSpring)
        oldPosition.tickDelta(.008)
        motion.advanceFrame(frame(124, 116), 1f)
        assertEquals(oldPosition.value, motion.position.value, 1e-6f)
        assertEquals(oldPosition.velocity, motion.position.velocity, 1e-6f)
        assertTrue(motion.lift.value > 0f)
        assertTrue(motion.jelly.value > 0f)
    }

    @Test fun downAndUpBeforeTheFirstFrameRetainTheShortTapRisePulse() {
        val motion = motion()
        val epoch = motion.press(3f, 102_000_000L, 90)
        motion.release(epoch, 3f, 103_000_000L, 91)

        motion.advanceFrame(frame(108, 100), 1f)

        assertNull(motion.appliedPressEpoch)
        assertEquals(3f, motion.position.target, 0f)
        assertEquals(0f, motion.position.value, 0f)
        assertEquals(0f, motion.lift.value, 0f)
        assertEquals(1f, motion.lift.target, 0f)
        assertTrue(motion.dropping)
        motion.advanceFrame(frame(116, 108), 1f)
        assertTrue(motion.position.value > 0f)
        assertTrue(motion.lift.value > 0f)
        assertTrue(motion.jelly.value > 0f)
    }

    @Test fun upThenDownBetweenFramesActivatesANewEpochWithoutDiscardingOldVelocity() {
        val motion = motion()
        val first = motion.press(3f, 100_000_000L, 90)
        motion.advanceFrame(frame(108, null), 1f)
        motion.advanceFrame(frame(116, 108), 1f)
        motion.advanceFrame(frame(124, 116), 1f)
        val expected = SpringState(0f)
        expected.springTo(3f, FlClashSprings.TravelSpring)
        expected.tickDelta(.024)
        motion.release(first, 3f, 130_000_000L, 120)
        val next = motion.press(0f, 131_000_000L, 121)

        motion.advanceFrame(frame(132, 124), 1f)

        assertTrue(next > first)
        assertEquals(next, motion.appliedPressEpoch)
        assertEquals(0f, motion.position.target, 0f)
        assertEquals(expected.value, motion.position.value, 1e-6f)
        assertEquals(expected.velocity, motion.position.velocity, 1e-6f)
        assertTrue(motion.position.velocity > 0f)
        expected.springTo(0f, FlClashSprings.TravelSpring)
        expected.tickDelta(.008)
        motion.advanceFrame(frame(140, 132), 1f)
        assertEquals(expected.value, motion.position.value, 1e-6f)
        assertEquals(expected.velocity, motion.position.velocity, 1e-6f)
    }

    @Test fun continuousDragCommandsIntegrateBothSidesOfTheirActualReceipt() {
        val motion = motion()
        val epoch = motion.press(3f, 100_000_000L, 90)
        motion.advanceFrame(frame(108, null), 1f)
        val expected = SpringState(0f)
        expected.springTo(3f, FlClashSprings.TravelSpring)
        motion.drag(epoch, 1.4f, 4f, 111_000_000L, 101)
        expected.tickDelta(.003)
        expected.springTo(1.4f, NativeDockPosition.FollowSpring)
        expected.tickDelta(.005)

        motion.advanceFrame(frame(116, 108), 1f)

        assertEquals(expected.value, motion.position.value, 1e-6f)
        assertEquals(expected.velocity, motion.position.velocity, 1e-6f)
        motion.drag(epoch, 1.7f, 2f, 117_000_000L, 107)
        expected.tickDelta(.001)
        expected.springTo(1.7f, NativeDockPosition.FollowSpring)
        expected.tickDelta(.007)
        motion.advanceFrame(frame(124, 116), 1f)
        assertEquals(expected.value, motion.position.value, 1e-6f)
        assertEquals(expected.velocity, motion.position.velocity, 1e-6f)
        assertTrue(motion.lift.value > 0f)
    }

    @Test fun cancelBeforeTheFirstFrameRemovesThePreviewAndItsReleasePulse() {
        val motion = motion()
        val epoch = motion.press(3f, 100_000_000L, 90)
        motion.cancel(0f, 101_000_000L, 91)
        motion.release(epoch, 3f, 102_000_000L, 92)
        motion.advanceFrame(frame(108, null), 1f)

        assertNull(motion.appliedPressEpoch)
        assertEquals(0f, motion.position.target, 0f)
        assertEquals(0f, motion.lift.target, 0f)
        assertFalse(motion.dropping)
        motion.advanceFrame(frame(116, 108), 1f)
        assertEquals(0f, motion.position.value, 0f)
        assertEquals(0f, motion.lift.value, 0f)
    }

    @Test fun semanticSelectionSupersedesPendingPointerEpochAndRejectsItsLaterEvents() {
        val motion = motion()
        val epoch = motion.press(3f, 100_000_000L, 90)
        motion.select(1f, 105_000_000L)
        motion.drag(epoch, 2f, 10f, 106_000_000L, 96)
        motion.release(epoch, 2f, 107_000_000L, 97)
        motion.advanceFrame(frame(108, null), 1f)

        assertNull(motion.appliedPressEpoch)
        assertEquals(1f, motion.requestedPosition, 0f)
        assertEquals(1f, motion.position.target, 0f)
        assertEquals(0f, motion.lift.target, 0f)
        assertFalse(motion.dropping)
    }

    @Test fun disabledAnimationsSettleTheLatestTransactionAndWakeWithoutCatchingUp() {
        val motion = motion()
        val epoch = motion.press(3f, 100_000_000L, 90)
        motion.release(epoch, 3f, 101_000_000L, 91)
        motion.advanceFrame(frame(108, null), 0f)
        assertEquals(3f, motion.position.value, 0f)
        assertEquals(0f, motion.lift.value, 0f)
        assertFalse(motion.needsFrame(0f))
        motion.press(0f, 90_000_000_000L, 89_990)
        assertTrue(motion.needsFrame(1f))

        motion.advanceFrame(ActiveFrame(90_008_000_000L, null), 1f)

        assertEquals(3f, motion.position.value, 0f)
        assertEquals(0f, motion.position.velocity, 0f)
        assertEquals(0f, motion.lift.value, 0f)
        motion.advanceFrame(ActiveFrame(90_016_000_000L, 90_008_000_000L), 1f)
        assertTrue(motion.position.value < 3f)
        assertTrue(motion.lift.value > 0f)
    }

    @Test fun durationScaleIsAppliedOnceToEveryTimedMotionAxis() {
        val normal = motion()
        val slowed = motion()
        val normalEpoch = normal.press(3f, 100_000_000L, 90)
        val slowedEpoch = slowed.press(3f, 200_000_000L, 180)
        normal.advanceFrame(frame(108, null), 1f)
        slowed.advanceFrame(frame(216, null), 2f)
        normal.drag(normalEpoch, 1.5f, 4f, 111_000_000L, 101)
        slowed.drag(slowedEpoch, 1.5f, 4f, 222_000_000L, 202)
        normal.advanceFrame(frame(116, 108), 1f)
        slowed.advanceFrame(frame(232, 216), 2f)
        listOf(normal.position, normal.lift, normal.swell, normal.pull, normal.jelly, normal.glow, normal.glowSpread)
            .zip(listOf(slowed.position, slowed.lift, slowed.swell, slowed.pull, slowed.jelly, slowed.glow, slowed.glowSpread))
            .forEach { (a, b) ->
                assertEquals(a.value, b.value, 1e-6f)
                assertEquals(a.velocity, b.velocity, 1e-6f)
            }
        assertEquals(normal.stretchY, slowed.stretchY, 1e-6f)
    }

    @Test fun disabledAnimationTracksEachHeldDragTransactionWithoutAnIdleLoop() {
        val motion = motion()
        val epoch = motion.press(0f, 100_000_000L, 90)
        motion.drag(epoch, 1.5f, 6f, 101_000_000L, 91)
        motion.advanceFrame(frame(108, null), 0f)
        assertEquals(1.5f, motion.position.value, 0f)
        assertEquals(6f, motion.pull.value, 0f)
        assertEquals(1f, motion.lift.value, 0f)
        assertFalse(motion.needsFrame(0f))
        motion.drag(epoch, 2f, -4f, 200_000_000L, 190)
        motion.advanceFrame(frame(208, null), 0f)
        assertEquals(2f, motion.position.value, 0f)
        assertEquals(-4f, motion.pull.value, 0f)
        assertFalse(motion.needsFrame(0f))
        motion.release(epoch, 2f, 300_000_000L, 290)
        motion.advanceFrame(frame(308, null), 0f)
        assertEquals(2f, motion.position.value, 0f)
        assertEquals(0f, motion.pull.value, 0f)
        assertEquals(0f, motion.lift.value, 0f)
        assertFalse(motion.needsFrame(0f))
    }

    @Test fun aNewPressKeepsMovingDragDeformationAndLiftContinuousAtItsFrameOrigin() {
        fun movingDrag(): GlassDockMotion {
            val motion = motion()
            val epoch = motion.press(0f, 100_000_000L, 90)
            motion.advanceFrame(frame(108, null), 1f)
            motion.drag(epoch, 2.4f, 5f, 110_000_000L, 100)
            repeat(6) { motion.advanceFrame(frame(116L + it * 8, 108L + it * 8), 1f) }
            motion.release(epoch, 3f, 158_000_000L, 148)
            return motion
        }
        val actual = movingDrag()
        val continuingOldMotion = movingDrag()
        actual.press(0f, 159_000_000L, 149)

        actual.advanceFrame(frame(164, 156), 1f)
        continuingOldMotion.advanceFrame(frame(164, 156), 1f)

        listOf(actual.position, actual.lift, actual.jelly)
            .zip(listOf(continuingOldMotion.position, continuingOldMotion.lift, continuingOldMotion.jelly))
            .forEach { (a, b) ->
                assertEquals(b.value, a.value, 1e-6f)
                assertEquals(b.velocity, a.velocity, 1e-6f)
            }
        assertTrue(actual.position.velocity != 0f)
        assertTrue(actual.lift.velocity != 0f)
        assertTrue(actual.jelly.velocity != 0f)
        assertEquals(continuingOldMotion.stretchY, actual.stretchY, 1e-6f)
    }
}
