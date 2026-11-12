package com.lingion.sleepy.ui.component.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class NativeGlassDragDeformationTest {
    @Test fun zeroTimeFrameRetainsTheNewTargetWithoutAdvancingTheLens() {
        val motion = NativeGlassDragDeformation()
        motion.advance(0.0, 110f)
        assertEquals(0f, motion.x, 0f)
        assertEquals(0f, motion.velocity, 0f)
        assertTrue("a new target is pending even before time advances", motion.isRunning)
        repeat(12) { motion.advance(1.0 / 120.0, 110f) }
        assertTrue("the zero-time origin cannot discard the later movement deformation", motion.x > .01f)
    }

    private data class InputRow(
        val control: String, val touch: Int, val kind: String, val seconds: Double,
        val input: Float, val scaleX: Float, val scaleY: Float,
    )

    private data class Frame(
        val time: Double, val input: Float, val nativeX: Float, val nativeY: Float,
        val actualX: Float, val actualY: Float,
    )

    private fun recordedGestures(): List<List<InputRow>> {
        val stream = checkNotNull(javaClass.getResourceAsStream("/native-glass/drag-matrix.csv"))
        return stream.bufferedReader().useLines { lines ->
            lines.drop(1).filter { it.isNotBlank() }.map { line ->
                val cells = line.split(',')
                InputRow(cells[0], cells[1].toInt(), cells[2], cells[3].toDouble(),
                    cells[4].toFloat(), cells[5].toFloat(), cells[6].toFloat())
            }.toList().groupBy { it.control to it.touch }.values.toList()
        }
    }

    private fun replay(rows: List<InputRow>): List<Frame> {
        val spec = if (rows.first().control == "dock") NativeGlassDragDeformation.Spec.Dock
        else NativeGlassDragDeformation.Spec.Segment
        val motion = NativeGlassDragDeformation(spec)
        var input = 0f
        var time = 0.0
        return buildList {
            rows.forEach { row ->
                motion.advance(row.seconds - time, input)
                time = row.seconds
                if (row.kind == "input") input = row.input
                else add(Frame(time, input, row.scaleX - 1f, row.scaleY - 1f, motion.x, motion.y))
            }
        }
    }

    @Test fun replaysBothDirectionsAtBothNativeSpeedsIncludingTheHeldStopAndRelease() {
        val gestures = recordedGestures()
        assertEquals("two controls, four velocity/direction cases, two repetitions", 16, gestures.size)
        gestures.forEach { rows ->
            val frames = replay(rows)
            val name = "${rows.first().control} touch ${rows.first().touch}"
            val rmseX = sqrt(frames.map { (it.actualX - it.nativeX).toDouble().let { e -> e * e } }.average())
            val rmseY = sqrt(frames.map { (it.actualY - it.nativeY).toDouble().let { e -> e * e } }.average())
            assertTrue("$name horizontal scale RMSE $rmseX", rmseX < .02)
            assertTrue("$name vertical scale RMSE $rmseY", rmseY < .035)

            val destination = rows.last { it.kind == "input" }.input
            val stop = rows.first { it.kind == "input" && it.input == destination }.seconds
            val direction = if (destination > 0f) 1f else -1f
            val middle = frames.filter { it.input / destination in .3f.. .7f }
            assertTrue("$name must include recorded middle movement", middle.isNotEmpty())
            assertTrue("$name stays near unit scale during middle movement",
                middle.all { abs(it.actualX) < .045f })

            val fast = (rows.first().touch - 2) % 8 >= 4
            val afterStop = frames.filter { it.time >= stop }
            if (fast) {
                val peak = afterStop.maxBy { it.actualX * direction }
                val nativePeak = afterStop.maxBy { it.nativeX * direction }
                val rebound = afterStop.minBy { it.actualX * direction }
                assertTrue("$name has its signed peak after the finger stops while still held",
                    peak.time - stop in .045.. .17)
                assertTrue("$name peak timing residual ${peak.time - nativePeak.time}",
                    abs(peak.time - nativePeak.time) < .06)
                assertTrue("$name native-sized first peak ${peak.actualX}", peak.actualX * direction in .12f.. .20f)
                assertTrue("$name rebounds without requiring a new input", rebound.actualX * direction < -.10f)
                assertTrue("$name rebound follows the observed 350ms region", rebound.time - stop in .29.. .44)
            } else {
                assertTrue("$name slow motion must not reuse the fast 15% deformation",
                    frames.all { abs(it.actualX) < .035f })
            }
        }
    }

    @Test fun physicalStateSurvivesDirectionChangesAndSettlesAfterReset() {
        val motion = NativeGlassDragDeformation()
        repeat(20) { motion.advance(1.0 / 120, it * 8f) }
        val before = motion.x
        motion.advance(0.0, -160f)
        assertEquals("changing input never teleports the lens deformation", before, motion.x, 0f)
        repeat(600) { motion.advance(1.0 / 120, -160f) }
        assertFalse(motion.isRunning)
        assertEquals(0f, motion.x, 0f)
        motion.reset(42f)
        assertEquals(0f, motion.y, 0f)
        assertFalse(motion.isRunning)
    }

    @Test fun externalSelectionChangesStayUndeformedUntilAnInteractionStarts() {
        val control = GlassControlMotion(0f, GlassControlMotionSpec.NativeSegmented, travelDistanceDp = 112f)
        control.update(2f, pressed = false, dragging = false)
        repeat(180) {
            control.tick(1.0 / 120)
            assertEquals(0f, control.stretch, 0f)
            assertEquals(0f, control.stretchY, 0f)
        }
        val dock = GlassDockMotion(0f)
        dock.distancePerPosition = 86f
        dock.position.springTo(2f, FlClashSprings.TravelSpring)
        repeat(180) {
            dock.advance(1.0 / 120, 1f, held = false, dragging = false)
            assertEquals(0f, dock.jelly.value, 0f)
            assertEquals(0f, dock.stretchY, 0f)
        }
    }

    @Test fun rightToLeftLogicalSelectionUsesTheActualLeftwardPhysicalDirection() {
        val right = GlassControlMotion(0f, GlassControlMotionSpec.NativeSegmented, travelDistanceDp = 112f)
        val left = GlassControlMotion(0f, GlassControlMotionSpec.NativeSegmented, travelDistanceDp = -112f)
        right.update(0f, pressed = true, dragging = false)
        left.update(0f, pressed = true, dragging = false)
        repeat(60) { right.tick(1.0 / 120); left.tick(1.0 / 120) }
        repeat(30) { frame ->
            val target = (frame + 1) / 15f
            right.update(target, pressed = true, dragging = true)
            left.update(target, pressed = true, dragging = true)
            right.tick(1.0 / 120)
            left.tick(1.0 / 120)
        }
        var greatest = 0f
        repeat(60) {
            right.tick(1.0 / 120)
            left.tick(1.0 / 120)
            if (right.stretch > greatest) {
                greatest = right.stretch
                assertTrue("the same logical target moves in the opposite physical direction in RTL", left.stretch < 0f)
                assertTrue("a leftward lens compresses horizontally and grows vertically", left.stretchY > 0f)
            }
        }
        assertTrue("exercise a visible stopping deformation", greatest > .10f)
    }

    @Test fun ninetyAndOneHundredTwentyHzDoNotChangeThePhysicalEnvelope() {
        fun trajectory(hz: Int): FloatArray {
            val motion = NativeGlassDragDeformation(NativeGlassDragDeformation.Spec.Dock)
            val values = FloatArray(12)
            var nextCheckpoint = 1
            repeat(hz) { frame ->
                val t = (frame + 1.0) / hz
                motion.advance(1.0 / hz, (minOf(t, .2) * 900).toFloat())
                if (nextCheckpoint <= 12 && t >= nextCheckpoint / 12.0) {
                    values[nextCheckpoint - 1] = motion.x
                    nextCheckpoint++
                }
            }
            return values
        }
        val normal = trajectory(120)
        val highRefresh = trajectory(90)
        normal.zip(highRefresh).forEach { (first, second) -> assertEquals(first, second, .012f) }
    }
}
