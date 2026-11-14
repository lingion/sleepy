package com.lingion.sleepy.ui.component.liquidglass

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Actual UIKit input/presentation times, including four-tab cases held out of the fit. */
class NativeDockPositionReferenceTest {
    private data class Row(
        val count: Int, val touch: Int, val pitch: Float, val kind: String,
        val seconds: Double, val phase: Int, val position: Float,
    )

    @Test fun nativeThreeAndFourTabCentersFollowSlowAndFastDragsInBothDirections() {
        val stream = checkNotNull(javaClass.getResourceAsStream("/native-glass/dock-position-matrix.csv"))
        val gestures = stream.bufferedReader().useLines { lines ->
            lines.drop(1).filter { it.isNotBlank() }.map { line ->
                val c = line.split(',')
                Row(c[0].toInt(), c[1].toInt(), c[2].toFloat(), c[3], c[4].toDouble(), c[5].toInt(), c[6].toFloat())
            }.toList().groupBy { it.count to it.touch }.values
        }
        assertEquals("eight fitted three-tab drags and eight held-out four-tab drags", 16, gestures.size)
        gestures.forEach { rows ->
            val initial = rows.first { it.kind == "input" }.position
            val motion = GlassDockMotion(initial)
            motion.distancePerPosition = rows.first().pitch
            var held = false
            var dragging = false
            var previousFrame: Double? = null
            val errors = mutableListOf<Double>()
            rows.forEach { row ->
                if (row.kind == "input") {
                    when (row.phase) {
                        0 -> {
                            held = true
                            dragging = false
                            motion.dropping = false
                            motion.position.springTo(initial, FlClashSprings.TravelSpring)
                            motion.lift.springTo(1f, FlClashSprings.LiftSpring)
                        }
                        1 -> {
                            if (abs(row.position - initial) * row.pitch > 4f) dragging = true
                            if (dragging) motion.position.springTo(row.position, NativeDockPosition.FollowSpring)
                        }
                        3 -> {
                            held = false
                            dragging = false
                            motion.dropping = true
                            motion.position.springTo(row.position.roundToInt().toFloat(), FlClashSprings.TravelSpring)
                            if (motion.lift.value >= FlClashDockPhysics.LIFT_SNAP &&
                                abs(motion.position.target - motion.position.value) <= FlClashDockPhysics.LIFT_RELEASE) {
                                motion.dropping = false
                                motion.lift.springTo(0f, FlClashSprings.LiftSpring)
                            }
                        }
                    }
                } else {
                    val delta = previousFrame?.let { (row.seconds - it).coerceIn(.001, .05) } ?: .016
                    previousFrame = row.seconds
                    motion.advance(delta, 1f, held, dragging)
                    if (row.seconds >= 0) errors += ((motion.position.value - row.position) * row.pitch).toDouble()
                }
            }
            val case = "${rows.first().count} tabs touch ${rows.first().touch}"
            val rmse = sqrt(errors.map { it * it }.average())
            assertTrue("$case local-center RMSE $rmse dp", rmse < 2.3)
            assertTrue("$case maximum local-center residual ${errors.maxOf { abs(it) }} dp",
                errors.all { abs(it) < 8.0 })
        }
    }

    @Test fun firstToLastRetargetKeepsPositionAndVelocityContinuous() {
        val motion = GlassDockMotion(0f)
        motion.distancePerPosition = 69f
        motion.lift.springTo(1f, FlClashSprings.LiftSpring)
        motion.position.springTo(3f, NativeDockPosition.FollowSpring)
        repeat(8) { motion.advance(1.0 / 120, 1f, held = true, dragging = true) }
        val position = motion.position.value
        val velocity = motion.position.velocity
        motion.position.springTo(0f, NativeDockPosition.FollowSpring)
        assertEquals(position, motion.position.value, 0f)
        assertEquals(velocity, motion.position.velocity, 0f)
        repeat(300) { motion.advance(1.0 / 120, 1f, held = true, dragging = true) }
        assertEquals(0f, motion.position.value, .001f)
    }
}
