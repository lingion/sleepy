package com.lingion.sleepy.ui.component.liquidglass

import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Receipt-time parent translation; native measurements cover a four-tab 333 x 62pt platter. */
class NativeDockParentMotionTest {
    private fun ms(value: Long) = value * 1_000_000L
    private fun frame(atMs: Long, previousMs: Long? = null) = ActiveFrame(ms(atMs), previousMs?.let(::ms))

    @Test fun firstEligibleColdFrameIncludesMotionAfterTheActualMoveReceipt() {
        val motion = NativeDockParentMotion()
        motion.press(1, 69f, ms(0))
        motion.move(1, 190.5f, ms(8))

        motion.advanceFrame(frame(20), 1f)

        // Independently evaluated frozen second-order step: 121.5dp for 12ms.
        assertEquals(1.249983802457174f, motion.translation.target, 1e-6f)
        assertEquals(.007731619774142f, motion.translation.value, 1e-6f)
        assertEquals(1.258083043905091f, motion.translation.velocity, 1e-5f)
        assertTrue(motion.needsFrame())
    }

    @Test fun actualNegativeFingerDisplacementDrivesNegativeInBoundsTranslation() {
        val motion = NativeDockParentMotion()
        motion.press(1, 306f, ms(0))
        motion.move(1, 167.5f, ms(0))

        motion.advanceFrame(frame(100), 1f)

        assertEquals(-1.424878655475873f, motion.translation.target, 1e-6f)
        assertEquals(-.421485451521314f, motion.translation.value, 1e-6f)
        assertEquals(-6.532549370469026f, motion.translation.velocity, 1e-5f)
    }

    @Test fun sameAbsoluteFingerCoordinateRetainsThePhysicalDownAnchor() {
        val forward = NativeDockParentMotion()
        val reverse = NativeDockParentMotion()
        forward.press(1, 69f, ms(0))
        reverse.press(1, 306f, ms(0))
        forward.move(1, 186.5f, ms(10))
        reverse.move(1, 186.5f, ms(10))

        forward.advanceFrame(frame(20), 0f)
        reverse.advanceFrame(frame(20), 0f)

        assertEquals(1.208832072335127f, forward.translation.value, 1e-6f)
        assertEquals(-1.229407937396151f, reverse.translation.value, 1e-6f)
    }

    @Test fun fixedFingerHoldKeepsAdvancingTheCausalState() {
        val motion = NativeDockParentMotion()
        motion.press(1, 69f, ms(0))
        motion.move(1, 187.5f, ms(0))
        motion.advanceFrame(frame(40), 1f)
        assertEquals(.074810750232941f, motion.translation.value, 1e-6f)
        val target = motion.translation.target

        motion.advanceFrame(frame(300, 40), 1f)

        assertEquals(target, motion.translation.target, 0f)
        assertEquals(1.226924435724488f, motion.translation.value, 1e-6f)
        assertEquals(1.991867559290062f, motion.translation.velocity, 1e-5f)
    }

    @Test fun futureReceiptCannotApplyOnAnEarlierFrame() {
        val motion = NativeDockParentMotion()
        motion.press(1, 69f, ms(10))
        motion.move(1, 190.5f, ms(50))

        motion.advanceFrame(frame(20), 1f)

        assertEquals(0f, motion.translation.target, 0f)
        assertEquals(0f, motion.translation.value, 0f)
        assertTrue(motion.needsFrame())
        motion.advanceFrame(frame(62, 20), 1f)
        assertEquals(.007731619774142f, motion.translation.value, 1e-6f)
    }

    @Test fun multipleReceiptsWithinOneFrameKeepTheirOrderAndElapsedSegments() {
        val whole = NativeDockParentMotion()
        val split = NativeDockParentMotion()
        listOf(whole, split).forEach {
            it.press(1, 69f, ms(0))
            it.move(1, 190.5f, ms(8))
            it.move(1, 30f, ms(15))
        }

        whole.advanceFrame(frame(30), 1f)
        split.advanceFrame(frame(8), 1f)
        split.advanceFrame(frame(15, 8), 1f)
        split.advanceFrame(frame(30, 15), 1f)

        assertTrue(abs(whole.translation.value) > 1e-4f)
        assertEquals(split.translation.target, whole.translation.target, 0f)
        assertEquals(split.translation.value, whole.translation.value, 1e-6f)
        assertEquals(split.translation.velocity, whole.translation.velocity, 1e-5f)
    }

    @Test fun directDownWithNoFingerMovementDoesNotTranslateOrKeepAnIdleLoop() {
        val motion = NativeDockParentMotion()
        assertFalse(motion.needsFrame())
        motion.press(1, 250f, ms(10))
        assertTrue(motion.needsFrame())

        motion.advanceFrame(frame(100), 1f)

        assertEquals(0f, motion.translation.value, 0f)
        assertEquals(0f, motion.translation.velocity, 0f)
        assertFalse(motion.needsFrame())
    }

    @Test fun callersWithoutPhysicalCoordinatesKeepTheLegacyTranslationPath() {
        val motion = NativeDockParentMotion()
        motion.press(1, null, ms(0))
        motion.move(1, null, ms(10))
        motion.move(1, 100f, ms(20))

        motion.advanceFrame(frame(100), 1f)

        assertEquals(0f, motion.translation.value, 0f)
        assertEquals(0f, motion.translation.target, 0f)
        assertFalse(motion.needsFrame())
    }

    @Test fun cancelledEpochRejectsLaterMoveAndRelease() {
        val motion = NativeDockParentMotion()
        motion.press(1, 69f, ms(0))
        motion.cancel(ms(1))
        motion.move(1, 362f, ms(2))
        motion.release(1, ms(3))

        motion.advanceFrame(frame(100), 0f)

        assertEquals(0f, motion.translation.target, 0f)
        assertEquals(0f, motion.translation.value, 0f)
        assertFalse(motion.needsFrame())
    }

    @Test fun repeatedDownPreservesStateAndVelocityAndStartsANewPhysicalAnchor() {
        val motion = NativeDockParentMotion()
        motion.press(1, 69f, ms(0))
        motion.move(1, 190.5f, ms(0))
        motion.advanceFrame(frame(100), 1f)
        val value = motion.translation.value
        val velocity = motion.translation.velocity
        assertTrue(value > 0f && velocity > 0f)
        motion.press(2, 200f, ms(100))

        motion.advanceFrame(frame(100, 100), 1f)

        // Interrupted DOWN continuity is an Android policy, outside the resting native fit.
        assertEquals(value, motion.translation.value, 0f)
        assertEquals(velocity, motion.translation.velocity, 0f)
        assertEquals(0f, motion.translation.target, 0f)
        motion.move(1, 362f, ms(101))
        motion.release(1, ms(102))
        motion.move(2, 180f, ms(103))
        motion.advanceFrame(frame(110, 100), 0f)
        assertEquals(-.205758650610234f, motion.translation.value, 1e-6f)
    }

    @Test fun disabledAnimationsSettleHeldInputsAndReturnTransactionsWithoutAnIdleLoop() {
        val motion = NativeDockParentMotion()
        motion.press(1, 69f, ms(0))
        motion.move(1, 362f, ms(10))
        motion.advanceFrame(frame(20), 0f)
        assertEquals(3.014364231439934f, motion.translation.value, 1e-6f)
        assertEquals(0f, motion.translation.velocity, 0f)
        assertFalse(motion.needsFrame())
        motion.move(1, 20f, ms(30))
        assertTrue(motion.needsFrame())
        motion.advanceFrame(frame(40), 0f)
        assertEquals(-.504108693995074f, motion.translation.value, 1e-6f)
        assertFalse(motion.needsFrame())
        motion.release(1, ms(50))
        motion.advanceFrame(frame(60), 0f)
        assertEquals(0f, motion.translation.value, 0f)
        assertFalse(motion.needsFrame())
        motion.press(2, 306f, ms(70))
        motion.move(2, 167.5f, ms(71))
        motion.cancel(ms(72))
        motion.advanceFrame(frame(80), 0f)
        assertEquals(0f, motion.translation.value, 0f)
        assertFalse(motion.needsFrame())
    }

    @Test fun releaseAndCancelUseTheExistingAndroidPressSpringReturnPolicy() {
        listOf(false, true).forEach { cancel ->
            val motion = NativeDockParentMotion()
            motion.press(1, 69f, ms(0))
            motion.move(1, 362f, ms(0))
            motion.advanceFrame(frame(120), 1f)
            assertEquals(1.172249302500834f, motion.translation.value, 1e-6f)
            val expected = SpringState(motion.translation.value)
            expected.velocity = motion.translation.velocity
            expected.springTo(0f, FlClashSprings.PressSpring)
            expected.tickDelta(.02)
            if (cancel) motion.cancel(ms(120)) else motion.release(1, ms(120))

            motion.advanceFrame(frame(140, 120), 1f)

            assertEquals(0f, motion.translation.target, 0f)
            assertEquals(expected.value, motion.translation.value, 1e-6f)
            assertEquals(expected.velocity, motion.translation.velocity, 1e-5f)
            motion.advanceFrame(frame(3_000, 140), 1f)
            assertEquals(0f, motion.translation.value, 0f)
            assertFalse(motion.needsFrame())
        }
    }

    @Test fun durationScaleIsAppliedOnceIncludingFirstFrameReceiptElapsedTime() {
        val normal = NativeDockParentMotion()
        val slowed = NativeDockParentMotion()
        normal.press(1, 69f, ms(100))
        slowed.press(1, 69f, ms(200))
        normal.move(1, 190.5f, ms(108))
        slowed.move(1, 190.5f, ms(216))
        normal.advanceFrame(frame(120), 1f)
        slowed.advanceFrame(frame(240), 2f)
        assertEquals(.007731619774142f, normal.translation.value, 1e-6f)
        assertEquals(normal.translation.value, slowed.translation.value, 1e-6f)
        assertEquals(normal.translation.velocity, slowed.translation.velocity, 1e-5f)
        normal.move(1, 167.5f, ms(125))
        slowed.move(1, 167.5f, ms(250))
        normal.advanceFrame(frame(140, 120), 1f)
        slowed.advanceFrame(frame(280, 240), 2f)
        assertEquals(normal.translation.value, slowed.translation.value, 1e-6f)
        assertEquals(normal.translation.velocity, slowed.translation.velocity, 1e-5f)
    }

    @Test fun native65TrainAndThreeHoldoutsReproduceTheFrozenCandidateNumerically() {
        val inputs = csv("dock-parent-input-65.csv").groupBy { it.getValue("gesture_index").toInt() }
        val references = csv("dock-parent-reference-65.csv").groupBy { it.getValue("gesture_index").toInt() }
        assertEquals(listOf(178, 142, 20, 22), (0..3).map { inputs.getValue(it).size })
        assertEquals(listOf(209, 214, 57, 61), (0..3).map { references.getValue(it).size })
        (0..3).forEach { gesture ->
            val motion = NativeDockParentMotion()
            inputs.getValue(gesture).forEach { row ->
                assertEquals("native65", row.getValue("source_id"))
                val time = nanos(row.getValue("seconds_from_down_delivery"))
                val finger = row.getValue("finger_x_pt").toFloat()
                when (row.getValue("phase").toInt()) {
                    0 -> motion.press(1, finger, time)
                    1, 2 -> motion.move(1, finger, time)
                    3 -> motion.release(1, time)
                    4 -> motion.cancel(time)
                }
            }
            var previous: Long? = null
            references.getValue(gesture).forEach { row ->
                val time = nanos(row.getValue("seconds_from_down_delivery"))
                motion.advanceFrame(ActiveFrame(time, previous), 1f)
                previous = time
                val expected = row.getValue("frozen_linear_tx_pt").toDouble()
                val label = "gesture $gesture frame ${row.getValue("frame")}"
                // 1e-4dp is Float/clock numerical reproduction tolerance, never native parity.
                assertEquals(label, expected, motion.translation.value.toDouble(), 1e-4)
                val observed = row.getValue("observed_tx_pt").toDouble()
                assertEquals("$label retains observed residual", row.getValue("frozen_linear_error_pt").toDouble(),
                    motion.translation.value - observed, 1e-4)
            }
        }
    }

    @Test fun frozenNativeFixtureKeepsKnownHeldAndFastResidualsVisible() {
        val groups = csv("dock-parent-reference-65.csv").groupBy { it.getValue("gesture_index").toInt() }
        val allRmse = listOf(.044699369692490366, .044642325675478385, .2914153156828792, .2943403232841374)
        val allMax = listOf(.15653039497697074, .15611918242936706, .5865579707677511, .5986688361816579)
        val heldRmse = listOf(.13043664992248605, .13333555998870658, .4062835536146022, .4164299998588933)
        val lastRmse = listOf(.15033952838654607, .14856129358225434, .21231831461104456, .24982456735493663)
        (0..3).forEach { gesture ->
            val rows = groups.getValue(gesture)
            assertTrue(rows.all { it.getValue("split") == if (gesture == 0) "train" else "holdout" })
            rows.forEach { row ->
                val error = row.getValue("frozen_linear_tx_pt").toDouble() - row.getValue("observed_tx_pt").toDouble()
                assertEquals(error, row.getValue("frozen_linear_error_pt").toDouble(), 1e-12)
                assertTrue(row.getValue("raw_line").toInt() > 0)
                val start = row.getValue("getter_start_t").toDouble()
                val end = row.getValue("getter_end_t").toDouble()
                assertEquals((start + end) / 2, row.getValue("getter_mid_t").toDouble(), 0.0)
            }
            val errors = rows.map { it.getValue("frozen_linear_error_pt").toDouble() }
            // Serialization checks against recorded native residuals, not fitted acceptance limits.
            assertEquals(allRmse[gesture], rmse(errors), 1e-12)
            assertEquals(allMax[gesture], errors.maxOf { abs(it) }, 1e-12)
            assertEquals(heldRmse[gesture], rmse(rows.filter { it.getValue("is_final_fixed_finger") == "1" }
                .map { it.getValue("frozen_linear_error_pt").toDouble() }), 1e-12)
            assertEquals(lastRmse[gesture], rmse(rows.filter { it.getValue("is_last_150ms_pre_up") == "1" }
                .map { it.getValue("frozen_linear_error_pt").toDouble() }), 1e-12)
        }
    }

    private fun nanos(seconds: String) = (seconds.toDouble() * 1_000_000_000).roundToLong()
    private fun rmse(errors: List<Double>) = sqrt(errors.map { it * it }.average())
    private fun csv(name: String): List<Map<String, String>> {
        val stream = checkNotNull(javaClass.getResourceAsStream("/native-glass/$name"))
        return stream.bufferedReader().use { reader ->
            val header = checkNotNull(reader.readLine()).split(',')
            reader.lineSequence().filter { it.isNotBlank() }.map { header.zip(it.split(',')).toMap() }.toList()
        }
    }
}
