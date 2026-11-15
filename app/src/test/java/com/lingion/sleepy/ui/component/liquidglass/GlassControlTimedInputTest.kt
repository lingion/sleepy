package com.lingion.sleepy.ui.component.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassControlTimedInputTest {
    private fun frame(ms: Long, previousMs: Long?) = ActiveFrame(ms * 1_000_000L, previousMs?.times(1_000_000L))
    private fun driver(spec: GlassControlMotionSpec = GlassControlMotionSpec.Native) =
        TimedGlassControlMotion(0f, spec, travelDistanceDp = 112f)

    @Test fun aFrameOlderThanReceiptKeepsTheOldTrajectoryAndAPressStartsAtItsEligibleFrame() {
        val driver = driver()
        val expected = SpringState(0f)
        driver.motion.update(1f, pressed = false, dragging = false)
        expected.springTo(1f, FlClashSprings.TravelSpring)
        driver.advanceFrame(frame(100, null), 1f)
        val epoch = driver.begin(true, false, 110_000_000L, sampleUptimeMillis = 80)

        driver.advanceFrame(frame(108, 100), 1f)
        expected.tickDelta(.008)
        assertNull(driver.appliedPressEpoch)
        assertEquals(expected.value, driver.motion.position.value, 1e-6f)
        assertEquals(expected.velocity, driver.motion.position.velocity, 1e-6f)
        assertEquals(0f, driver.motion.lift, 0f)

        driver.advanceFrame(frame(116, 108), 1f)
        expected.tickDelta(.008)
        assertEquals(epoch, driver.appliedPressEpoch)
        assertEquals(expected.value, driver.motion.position.value, 1e-6f)
        assertEquals(expected.velocity, driver.motion.position.velocity, 1e-6f)
        assertEquals(0f, driver.motion.lift, 0f)
        driver.advanceFrame(frame(124, 116), 1f)
        assertTrue(driver.motion.lift > 0f)
    }

    @Test fun aMoveSplitsOldAndNewTargetsAtReceiptRatherThanSpendingTheWholeFrameOnTheNewTarget() {
        val driver = driver()
        val epoch = driver.begin(true, false, 100_000_000L, 90)!!
        driver.advanceFrame(frame(108, null), 1f)
        driver.move(epoch, 1f, true, 111_000_000L, 101)
        val expected = SpringState(0f)
        expected.tickDelta(.003)
        expected.springTo(1f, FlClashSprings.FollowSpring)
        expected.tickDelta(.005)

        driver.advanceFrame(frame(116, 108), 1f)

        assertEquals(expected.value, driver.motion.position.value, 1e-6f)
        assertEquals(expected.velocity, driver.motion.position.velocity, 1e-6f)
        assertEquals(0f, driver.requestedPosition, 0f)
    }

    @Test fun downAndUpBeforeOneFrameKeepTheTapPulseAndCommittedDestination() {
        val driver = driver()
        val epoch = driver.begin(true, true, 102_000_000L, 92)!!
        assertTrue(driver.release(epoch, 1f, true, 103_000_000L, 93))

        driver.advanceFrame(frame(108, 100), 1f)

        assertNull(driver.appliedPressEpoch)
        assertEquals(1f, driver.motion.position.target, 0f)
        assertEquals(0f, driver.motion.position.value, 0f)
        assertEquals(0f, driver.motion.lift, 0f)
        assertEquals(1f, driver.motion.elevation.target, 0f)
        driver.advanceFrame(frame(116, 108), 1f)
        assertTrue(driver.motion.position.value > 0f)
        assertTrue(driver.motion.lift > 0f)
    }

    @Test fun holdingAnUnselectedSegmentKeepsTheExistingLensAtRestUntilValidUp() {
        val driver = driver(GlassControlMotionSpec.NativeSegmented)
        val epoch = driver.begin(false, false, 100_000_000L, 90)!!
        driver.advanceFrame(frame(108, null), 1f)
        driver.advanceFrame(frame(800, 108), 1f)
        assertEquals(0f, driver.motion.position.value, 0f)
        assertEquals(0f, driver.motion.lift, 0f)
        assertFalse(driver.needsFrame())

        assertTrue(driver.release(epoch, 2f, true, 810_000_000L, 800))
        driver.advanceFrame(frame(816, null), 1f)
        assertEquals(2f, driver.motion.position.target, 0f)
        assertEquals(1f, driver.motion.elevation.target, 0f)
        driver.advanceFrame(frame(824, 816), 1f)
        assertTrue(driver.motion.lift > 0f)
    }

    @Test fun cancellingAnUnselectedSeatDoesNotTruncateThePreviousValidSelectionPulse() {
        val actual = driver(GlassControlMotionSpec.NativeSegmented)
        val uninterrupted = driver(GlassControlMotionSpec.NativeSegmented)
        for (candidate in listOf(actual, uninterrupted)) {
            candidate.select(1f, 100_000_000L)
            candidate.advanceFrame(frame(108, null), 1f)
            candidate.advanceFrame(frame(116, 108), 1f)
        }
        val epoch = actual.begin(false, false, 120_000_000L, 110)!!
        actual.advanceFrame(frame(124, 116), 1f)
        uninterrupted.advanceFrame(frame(124, 116), 1f)
        actual.cancel(epoch, 128_000_000L, 118)
        actual.advanceFrame(frame(132, 124), 1f)
        uninterrupted.advanceFrame(frame(132, 124), 1f)

        assertNull(actual.appliedPressEpoch)
        assertEquals(1f, actual.motion.elevation.target, 0f)
        assertEquals(uninterrupted.motion.position.value, actual.motion.position.value, 1e-6f)
        assertEquals(uninterrupted.motion.elevation.value, actual.motion.elevation.value, 1e-6f)
        assertEquals(uninterrupted.motion.elevation.velocity, actual.motion.elevation.velocity, 1e-6f)
    }

    @Test fun aMovingCancelRestoresTheCommittedValueAndRejectsItsLaterRelease() {
        val driver = driver()
        val epoch = driver.begin(true, true, 100_000_000L, 90)!!
        driver.advanceFrame(frame(108, null), 1f)
        driver.move(epoch, 1f, true, 110_000_000L, 100)
        driver.advanceFrame(frame(116, 108), 1f)
        assertTrue(driver.motion.position.value > 0f)
        driver.cancel(epoch, 118_000_000L, 108)
        assertFalse(driver.release(epoch, 1f, true, 119_000_000L, 109))
        driver.advanceFrame(frame(124, 116), 1f)

        assertNull(driver.appliedPressEpoch)
        assertEquals(0f, driver.requestedPosition, 0f)
        assertEquals(0f, driver.motion.position.target, 0f)
        assertEquals(0f, driver.motion.elevation.target, 0f)
    }

    @Test fun cancelBeforeTheFirstFrameDoesNotGenerateAReleasedTapPulse() {
        val driver = driver()
        val epoch = driver.begin(true, true, 100_000_000L, 90)!!
        driver.cancel(epoch, 101_000_000L, 91)
        driver.advanceFrame(frame(108, null), 1f)
        driver.advanceFrame(frame(116, 108), 1f)
        assertEquals(0f, driver.motion.lift, 0f)
        assertEquals(0f, driver.motion.position.value, 0f)
        assertFalse(driver.needsFrame())
    }

    @Test fun semanticOrKeyboardSelectionInvalidatesThePointerAndKeepsItsOwnPulse() {
        val driver = driver(GlassControlMotionSpec.NativeSegmented)
        val epoch = driver.begin(true, false, 100_000_000L, 90)!!
        assertTrue(driver.select(2f, 104_000_000L))
        driver.move(epoch, 1f, true, 105_000_000L, 95)
        assertFalse(driver.release(epoch, 1f, true, 106_000_000L, 96))
        driver.advanceFrame(frame(108, null), 1f)
        assertNull(driver.appliedPressEpoch)
        assertEquals(2f, driver.requestedPosition, 0f)
        assertEquals(2f, driver.motion.position.target, 0f)
        assertEquals(1f, driver.motion.elevation.target, 0f)
        driver.advanceFrame(frame(116, 108), 1f)
        assertTrue(driver.motion.lift > 0f)
    }

    @Test fun semanticSelectionOfTheCurrentValueStillSupersedesAHeldPointerWithoutATapPulse() {
        val driver = driver(GlassControlMotionSpec.NativeSegmented)
        val epoch = driver.begin(true, false, 100_000_000L, 90)!!
        assertFalse(driver.select(0f, 104_000_000L))
        assertFalse(driver.isCurrent(epoch))
        assertFalse(driver.release(epoch, 2f, true, 105_000_000L, 95))
        driver.advanceFrame(frame(108, null), 1f)
        driver.advanceFrame(frame(116, 108), 1f)
        assertNull(driver.appliedPressEpoch)
        assertEquals(0f, driver.requestedPosition, 0f)
        assertEquals(0f, driver.motion.position.value, 0f)
        assertEquals(0f, driver.motion.lift, 0f)
        assertFalse(driver.needsFrame())
    }

    @Test fun rapidUpDownBetweenFramesKeepsOldMomentumAndAppliesTheLatestEpochAtOneOrigin() {
        val driver = driver()
        driver.select(1f, 100_000_000L)
        driver.advanceFrame(frame(108, null), 1f)
        driver.advanceFrame(frame(116, 108), 1f)
        val first = driver.begin(true, true, 121_000_000L, 111)!!
        driver.release(first, 0f, true, 122_000_000L, 112)
        val next = driver.begin(true, true, 123_000_000L, 113)!!
        val expected = SpringState(0f)
        expected.springTo(1f, FlClashSprings.TravelSpring)
        expected.tickDelta(.016)

        driver.advanceFrame(frame(124, 116), 1f)

        assertEquals(next, driver.appliedPressEpoch)
        assertEquals(0f, driver.motion.position.target, 0f)
        assertEquals(expected.value, driver.motion.position.value, 1e-6f)
        assertEquals(expected.velocity, driver.motion.position.velocity, 1e-6f)
        assertTrue(driver.motion.position.velocity > 0f)
    }

    @Test fun aMatchingControlledEchoDoesNotCancelTheNextGestureBeforeItsLensArrives() {
        val driver = driver()
        val first = driver.begin(true, true, 100_000_000L, 90)!!
        driver.release(first, 1f, true, 101_000_000L, 91)
        val next = driver.begin(true, true, 102_000_000L, 92)!!
        driver.synchronize(1f, true, 103_000_000L)
        assertTrue(driver.isCurrent(next))
        driver.move(next, 0f, true, 104_000_000L, 94)
        assertTrue(driver.release(next, 0f, true, 105_000_000L, 95))
        driver.advanceFrame(frame(108, null), 0f)
        assertEquals(0f, driver.motion.position.value, 0f)
        assertEquals(0f, driver.motion.lift, 0f)
    }

    @Test fun aRejectedControlledRequestRollsBackButAnOlderCompositionCannotRejectANewerRequest() {
        val driver = driver()
        val oldRevision = driver.requestRevision
        driver.select(1f, 100_000_000L)
        driver.synchronize(0f, true, 101_000_000L, observedRevision = oldRevision)
        assertEquals(1f, driver.requestedPosition, 0f)
        driver.synchronize(0f, true, 102_000_000L)
        driver.advanceFrame(frame(108, null), 0f)
        assertEquals(0f, driver.requestedPosition, 0f)
        assertEquals(0f, driver.motion.position.value, 0f)
        assertEquals(0f, driver.motion.lift, 0f)
    }

    @Test fun anExternalValueChangeInvalidatesTheGestureBeforeItsNextFrame() {
        val driver = driver()
        val epoch = driver.begin(true, true, 100_000_000L, 90)!!
        driver.move(epoch, .7f, true, 101_000_000L, 91)
        driver.synchronize(1f, true, 102_000_000L)
        assertFalse(driver.isCurrent(epoch))
        assertFalse(driver.release(epoch, 0f, true, 103_000_000L, 93))
        driver.advanceFrame(frame(108, null), 0f)
        assertEquals(1f, driver.motion.position.value, 0f)
        assertEquals(0f, driver.motion.lift, 0f)
    }

    @Test fun disablingRejectsNewAndStaleInputAndImmediatelySettlesTheEligibleDisabledTransaction() {
        val driver = driver()
        val epoch = driver.begin(true, true, 100_000_000L, 90)!!
        driver.move(epoch, .7f, true, 101_000_000L, 91)
        driver.synchronize(0f, false, 102_000_000L)
        assertNull(driver.begin(true, true, 103_000_000L, 93))
        assertFalse(driver.release(epoch, 1f, true, 104_000_000L, 94))
        assertFalse(driver.select(1f, 105_000_000L))
        driver.advanceFrame(frame(108, null), 1f)
        assertEquals(0f, driver.motion.position.value, 0f)
        assertEquals(0f, driver.motion.lift, 0f)
        assertFalse(driver.needsFrame())
    }

    @Test fun animationScaleZeroProcessesHeldMovesAndReleasesWithoutKeepingAnIdleLoopAlive() {
        val driver = driver()
        val epoch = driver.begin(true, true, 100_000_000L, 90)!!
        driver.move(epoch, .6f, true, 101_000_000L, 91)
        driver.advanceFrame(frame(108, null), 0f)
        assertEquals(.6f, driver.motion.position.value, 0f)
        assertEquals(1f, driver.motion.lift, 0f)
        assertFalse(driver.needsFrame())
        driver.move(epoch, .8f, true, 200_000_000L, 190)
        assertTrue(driver.needsFrame())
        driver.advanceFrame(frame(208, null), 0f)
        assertEquals(.8f, driver.motion.position.value, 0f)
        driver.release(epoch, 1f, true, 300_000_000L, 290)
        driver.advanceFrame(frame(308, null), 0f)
        assertEquals(1f, driver.motion.position.value, 0f)
        assertEquals(0f, driver.motion.lift, 0f)
        assertFalse(driver.needsFrame())
    }

    @Test fun durationScaleIsAppliedOnceToPositionLiftAndDeformation() {
        val normal = driver()
        val slow = driver()
        val a = normal.begin(true, true, 100_000_000L, 90)!!
        val b = slow.begin(true, true, 200_000_000L, 180)!!
        normal.advanceFrame(frame(108, null), 1f)
        slow.advanceFrame(frame(216, null), 2f)
        normal.move(a, .8f, true, 111_000_000L, 101)
        slow.move(b, .8f, true, 222_000_000L, 202)
        normal.advanceFrame(frame(116, 108), 1f)
        slow.advanceFrame(frame(232, 216), 2f)
        listOf(normal.motion.position, normal.motion.elevation, normal.motion.jelly)
            .zip(listOf(slow.motion.position, slow.motion.elevation, slow.motion.jelly)).forEach { (x, y) ->
                assertEquals(x.value, y.value, 1e-6f)
                assertEquals(x.velocity, y.velocity, 1e-6f)
            }
        assertEquals(normal.motion.stretchY, slow.motion.stretchY, 1e-6f)
    }
}
