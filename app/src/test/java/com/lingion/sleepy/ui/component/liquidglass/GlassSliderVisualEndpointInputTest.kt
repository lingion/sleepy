package com.lingion.sleepy.ui.component.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassSliderVisualEndpointInputTest {
    private fun driver(initial: Float = .75f) =
        TimedGlassControlMotion(initial, GlassControlMotionSpec.NativeSlider, travelDistanceDp = 298f)
    private fun frame(ms: Long, previous: Long?) =
        ActiveFrame(ms * 1_000_000L, previous?.times(1_000_000L))
    private val overdrive = 1f + 4.353380581148f / 298f

    @Test fun heldEndpointVisualTargetSurvivesMatchingBusinessEchoAndFurtherPull() {
        val driver = driver()
        val epoch = driver.begin(true, true, 100_000_000L, 90)!!
        assertTrue(driver.moveContinuously(epoch, 1f, 110_000_000L, 100, visualTarget = overdrive))
        assertEquals(1f, driver.requestedPosition, 0f)
        driver.synchronize(1f, true, 111_000_000L)
        assertTrue(driver.isCurrent(epoch))
        driver.advanceFrame(frame(116, null), 0f)
        assertEquals(overdrive, driver.motion.position.value, 0f)
        val revision = driver.requestRevision

        driver.moveContinuously(epoch, 1f, 120_000_000L, 110, visualTarget = overdrive + .005f)
        driver.synchronize(1f, true, 121_000_000L)
        driver.advanceFrame(frame(124, 116), 0f)

        assertEquals(revision, driver.requestRevision)
        assertTrue(driver.isCurrent(epoch))
        assertEquals(1f, driver.requestedPosition, 0f)
        assertEquals(overdrive + .005f, driver.motion.position.value, 0f)
    }

    @Test fun visualOverdriveDoesNotEnterTheFrozenBusinessDeformationForcing() {
        val overdriven = driver()
        val ordinary = driver()
        val a = overdriven.begin(true, true, 100_000_000L, 90)!!
        val b = ordinary.begin(true, true, 100_000_000L, 90)!!
        overdriven.advanceFrame(frame(108, null), 1f)
        ordinary.advanceFrame(frame(108, null), 1f)
        overdriven.moveContinuously(a, 1f, 110_000_000L, 100, visualTarget = overdrive)
        ordinary.moveContinuously(b, 1f, 110_000_000L, 100)
        for (ms in 116L..1516L step 8) {
            val previous = if (ms == 116L) 108L else ms - 8
            overdriven.advanceFrame(frame(ms, previous), 1f)
            ordinary.advanceFrame(frame(ms, previous), 1f)
            assertEquals(ordinary.motion.stretch, overdriven.motion.stretch, 0f)
            assertEquals(ordinary.motion.stretchY, overdriven.motion.stretchY, 0f)
        }
        assertTrue(overdriven.motion.position.value > 1f)
        assertEquals(1f, overdriven.requestedPosition, 0f)
    }

    @Test fun releaseRetractsTheVisualTargetToThePublishedBusinessEndpoint() {
        val driver = driver()
        val epoch = driver.begin(true, true, 100_000_000L, 90)!!
        driver.moveContinuously(epoch, 1f, 110_000_000L, 100, visualTarget = overdrive)
        driver.advanceFrame(frame(116, null), 0f)
        assertTrue(driver.release(epoch, driver.requestedPosition, true, 120_000_000L, 110))
        driver.advanceFrame(frame(124, 116), 1f)

        assertNull(driver.appliedPressEpoch)
        assertEquals(1f, driver.requestedPosition, 0f)
        assertEquals(1f, driver.motion.position.target, 0f)
        assertEquals(0f, driver.motion.elevation.target, 0f)
        assertFalse(driver.release(epoch, 1f, true, 125_000_000L, 115))
    }

    @Test fun cancellationDropsOverdriveWithoutChangingBusinessOrCreatingAPulse() {
        val driver = driver()
        val epoch = driver.begin(true, true, 100_000_000L, 90)!!
        driver.moveContinuously(epoch, 1f, 110_000_000L, 100, visualTarget = overdrive)
        driver.cancel(epoch, 120_000_000L, 110)
        driver.advanceFrame(frame(124, null), 0f)

        assertNull(driver.appliedPressEpoch)
        assertEquals(1f, driver.requestedPosition, 0f)
        assertEquals(1f, driver.motion.position.value, 0f)
        assertEquals(0f, driver.motion.lift, 0f)
        assertFalse(driver.release(epoch, 1f, true, 125_000_000L, 115))
    }

    @Test fun parentRejectionSupersedesVisualOverdriveAndRejectsStaleMoves() {
        val driver = driver()
        val epoch = driver.begin(true, true, 100_000_000L, 90)!!
        driver.moveContinuously(epoch, 1f, 110_000_000L, 100, visualTarget = overdrive)
        driver.synchronize(.5f, true, 120_000_000L)
        assertFalse(driver.isCurrent(epoch))
        assertFalse(driver.moveContinuously(epoch, 1f, 121_000_000L, 111, visualTarget = overdrive))
        driver.advanceFrame(frame(124, null), 0f)
        assertEquals(.5f, driver.requestedPosition, 0f)
        assertEquals(.5f, driver.motion.position.value, 0f)
        assertEquals(0f, driver.motion.lift, 0f)
    }

    @Test fun leavingReducedMotionDoesNotTurnTheHeldVisualOffsetIntoDeformationInput() {
        val overdriven = driver()
        val ordinary = driver()
        val a = overdriven.begin(true, true, 100_000_000L, 90)!!
        val b = ordinary.begin(true, true, 100_000_000L, 90)!!
        overdriven.moveContinuously(a, 1f, 110_000_000L, 100, visualTarget = overdrive)
        ordinary.moveContinuously(b, 1f, 110_000_000L, 100)
        overdriven.advanceFrame(frame(116, null), 0f)
        ordinary.advanceFrame(frame(116, null), 0f)
        overdriven.release(a, 1f, true, 120_000_000L, 110)
        ordinary.release(b, 1f, true, 120_000_000L, 110)
        for (ms in 124L..524L step 8) {
            val previous = if (ms == 124L) 116L else ms - 8
            overdriven.advanceFrame(frame(ms, previous), 1f)
            ordinary.advanceFrame(frame(ms, previous), 1f)
            assertEquals(ordinary.motion.stretch, overdriven.motion.stretch, 0f)
            assertEquals(ordinary.motion.stretchY, overdriven.motion.stretchY, 0f)
        }
    }

    @Test fun resizingAndDisablingAnOverdrivenLensRetainItsPublishedBusinessValue() {
        val driver = driver()
        val epoch = driver.begin(true, true, 100_000_000L, 90)!!
        driver.moveContinuously(epoch, 1f, 110_000_000L, 100, visualTarget = overdrive)
        driver.advanceFrame(frame(116, null), 0f)
        driver.motion.updateMotionDistance(600f)
        driver.advanceFrame(frame(124, 116), 1f)
        assertEquals(0f, driver.motion.stretch, 0f)
        assertEquals(0f, driver.motion.stretchY, 0f)
        driver.synchronize(1f, false, 128_000_000L)
        driver.advanceFrame(frame(132, 124), 1f)
        assertFalse(driver.isCurrent(epoch))
        assertEquals(1f, driver.requestedPosition, 0f)
        assertEquals(1f, driver.motion.position.value, 0f)
        assertEquals(0f, driver.motion.lift, 0f)
    }
}
