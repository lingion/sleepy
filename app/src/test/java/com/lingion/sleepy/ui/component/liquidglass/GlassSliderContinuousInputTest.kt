package com.lingion.sleepy.ui.component.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassSliderContinuousInputTest {
    private fun driver() = TimedGlassControlMotion(.25f, GlassControlMotionSpec.NativeSlider)
    private fun frame(ms: Long, previous: Long?) =
        ActiveFrame(ms * 1_000_000L, previous?.times(1_000_000L))

    @Test fun continuousMovesPublishTheirTargetsBeforeTheControlledEchoWithoutCancellingThePress() {
        val driver = driver()
        val epoch = driver.begin(true, true, 100_000_000L, 90)!!
        val previousRevision = driver.requestRevision

        assertTrue(driver.moveContinuously(epoch, .5f, 110_000_000L, 100))
        assertEquals(.5f, driver.requestedPosition, 0f)
        assertTrue(driver.requestRevision > previousRevision)
        driver.synchronize(.5f, true, 111_000_000L)
        assertTrue(driver.isCurrent(epoch))

        assertTrue(driver.moveContinuously(epoch, .75f, 112_000_000L, 102))
        driver.synchronize(.75f, true, 113_000_000L)
        assertTrue(driver.isCurrent(epoch))
        driver.advanceFrame(frame(116, null), 0f)
        assertEquals(epoch, driver.appliedPressEpoch)
        assertEquals(.75f, driver.motion.position.value, 0f)
        assertEquals(1f, driver.motion.lift, 0f)
    }

    @Test fun continuousTargetsSplitTheTrajectoryAtReceiptEvenWhenHardwareSamplesAreOlder() {
        val driver = driver()
        val epoch = driver.begin(true, true, 100_000_000L, 80)!!
        driver.advanceFrame(frame(108, null), 1f)
        driver.moveContinuously(epoch, .5f, 110_000_000L, 90)
        driver.moveContinuously(epoch, .8f, 120_000_000L, 95)
        val expected = SpringState(.25f)
        expected.tickDelta(.002)
        expected.springTo(.5f, FlClashSprings.FollowSpring)
        expected.tickDelta(.010)
        expected.springTo(.8f, FlClashSprings.FollowSpring)
        expected.tickDelta(.004)

        driver.advanceFrame(frame(124, 108), 1f)

        assertEquals(expected.value, driver.motion.position.value, 1e-6f)
        assertEquals(expected.velocity, driver.motion.position.velocity, 1e-6f)
    }

    @Test fun cancellationKeepsTheLastPublishedContinuousValueWithoutCreatingAReleasePulse() {
        val driver = driver()
        val epoch = driver.begin(true, true, 100_000_000L, 90)!!
        driver.moveContinuously(epoch, .7f, 101_000_000L, 91)
        driver.cancel(epoch, 102_000_000L, 92)

        assertFalse(driver.release(epoch, 1f, true, 103_000_000L, 93))
        driver.advanceFrame(frame(108, null), 1f)

        assertNull(driver.appliedPressEpoch)
        assertEquals(.7f, driver.requestedPosition, 0f)
        assertEquals(.7f, driver.motion.position.target, 0f)
        assertEquals(0f, driver.motion.elevation.target, 0f)
    }

    @Test fun aRejectedContinuousRequestInvalidatesTheGestureButAnOlderCompositionCannotRejectIt() {
        val driver = driver()
        val epoch = driver.begin(true, true, 100_000_000L, 90)!!
        val previousRevision = driver.requestRevision
        driver.moveContinuously(epoch, .7f, 101_000_000L, 91)
        driver.synchronize(.25f, true, 102_000_000L, observedRevision = previousRevision)
        assertTrue(driver.isCurrent(epoch))

        driver.synchronize(.25f, true, 103_000_000L)

        assertFalse(driver.isCurrent(epoch))
        assertFalse(driver.moveContinuously(epoch, .8f, 104_000_000L, 94))
        assertFalse(driver.release(epoch, .8f, true, 105_000_000L, 95))
        driver.advanceFrame(frame(108, null), 0f)
        assertEquals(.25f, driver.motion.position.value, 0f)
        assertEquals(0f, driver.motion.lift, 0f)
    }

    @Test fun continuousDownMoveAndUpBeforeOneFramePreserveOnePulseAndThePublishedDestination() {
        val driver = driver()
        val epoch = driver.begin(true, true, 100_000_000L, 90)!!
        driver.moveContinuously(epoch, .8f, 101_000_000L, 91)
        assertTrue(driver.release(epoch, .8f, true, 102_000_000L, 92))
        assertFalse(driver.release(epoch, .8f, true, 103_000_000L, 93))
        driver.advanceFrame(frame(108, null), 1f)
        assertEquals(.8f, driver.motion.position.target, 0f)
        assertEquals(1f, driver.motion.elevation.target, 0f)

        var peak = 0f
        for (time in 116L..1708L step 8) {
            driver.advanceFrame(frame(time, time - 8), 1f)
            peak = maxOf(peak, driver.motion.lift)
        }
        assertTrue(peak >= .35f)
        assertEquals(.8f, driver.motion.position.value, .001f)
        assertEquals(0f, driver.motion.lift, 0f)
        assertFalse(driver.needsFrame())
    }

    @Test fun disablingAfterAContinuousMoveRejectsStaleInputAndSettlesToTheControlledValue() {
        val driver = driver()
        val epoch = driver.begin(true, true, 100_000_000L, 90)!!
        driver.moveContinuously(epoch, .7f, 101_000_000L, 91)
        driver.synchronize(.4f, false, 102_000_000L)
        assertFalse(driver.moveContinuously(epoch, .8f, 103_000_000L, 93))
        assertNull(driver.begin(true, true, 104_000_000L, 94))
        driver.advanceFrame(frame(108, null), 1f)
        assertEquals(.4f, driver.motion.position.value, 0f)
        assertEquals(0f, driver.motion.lift, 0f)
        assertFalse(driver.needsFrame())
    }
}
