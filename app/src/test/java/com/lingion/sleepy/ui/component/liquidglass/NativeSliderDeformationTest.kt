package com.lingion.sleepy.ui.component.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/** Native observations are independent of the production model; see fixture provenance. */
class NativeSliderDeformationTest {
    private data class Row(
        val gesture: Int, val split: String, val kind: String, val time: Double,
        val phase: Int?, val input: Float, val scaleX: Float, val scaleY: Float,
        val boundsWidth: Float, val boundsHeight: Float,
    )

    private data class Sample(val row: Row, val x: Float, val y: Float)
    private data class Draw(val time: Double, val x: Float, val y: Float)
    private data class Event(val time: Double, val input: Float?)
    private data class Error(val width: Double, val height: Double)

    private fun recordedGestures(): List<List<Row>> {
        val stream = checkNotNull(javaClass.getResourceAsStream("/native-glass/slider-endpoint-65.csv"))
        return stream.bufferedReader().useLines { lines ->
            lines.drop(1).filter { it.isNotBlank() }.map { line ->
                val c = line.split(',')
                Row(c[0].toInt(), c[1], c[2], c[3].toDouble(), c[5].toIntOrNull(),
                    c[8].toFloatOrNull() ?: 0f, c[9].toFloatOrNull() ?: 1f,
                    c[10].toFloatOrNull() ?: 1f, c[11].toFloatOrNull() ?: 0f,
                    c[12].toFloatOrNull() ?: 0f)
            }.toList().groupBy { it.gesture }.toSortedMap().values.toList()
        }
    }

    private fun replayNativeFrames(rows: List<Row>): List<Sample> {
        val motion = NativeGlassDragDeformation(NativeGlassDragDeformation.Spec.Slider)
        var target = 0f
        var time = 0.0
        return buildList {
            rows.forEach { row ->
                if (row.time >= 0.0) {
                    motion.advance(row.time - time, target)
                    time = row.time
                }
                if (row.kind == "input") target = row.input
                else add(Sample(row, motion.x, motion.y))
            }
        }
    }

    private fun error(samples: List<Sample>, isotropic: Boolean = false): Error {
        fun rmse(values: List<Double>) = sqrt(values.map { it * it }.average())
        return Error(
            rmse(samples.map { ((if (isotropic) 0f else it.x) - (it.row.scaleX - 1f)) * it.row.boundsWidth }.map { it.toDouble() }),
            rmse(samples.map { ((if (isotropic) 0f else it.y) - (it.row.scaleY - 1f)) * it.row.boundsHeight }.map { it.toDouble() }),
        )
    }

    private fun assertError(name: String, error: Error, width: Double, height: Double) {
        assertTrue("$name width RMSE ${error.width}pt", error.width < width)
        assertTrue("$name height RMSE ${error.height}pt", error.height < height)
    }

    @Test fun lockedForwardTrainingAndCompleteReverseHoldoutsImproveTheNativeCarrier() {
        val gestures = recordedGestures()
        assertEquals(listOf(0, 1, 2, 3), gestures.map { it.first().gesture })
        assertEquals(setOf(0, 2), gestures.filter { it.first().split == "train" }.map { it.first().gesture }.toSet())
        assertEquals(setOf(1, 3), gestures.filter { it.first().split == "held_out" }.map { it.first().gesture }.toSet())
        assertEquals("all observed phases retained", 907, gestures.sumOf { rows -> rows.count { it.kind == "frame" } })
        gestures.forEach { rows ->
            val i = rows.first().gesture
            val samples = replayNativeFrames(rows)
            assertTrue("prepress observations survive", samples.first().row.time < 0.0)
            val up = rows.single { it.phase == 3 }.time
            assertTrue("complete release tail survives", samples.last().row.time > up + 1.15)
            val caps = when (i) {
                0 -> .09 to .06
                1 -> .12 to .08
                2 -> .43 to .29
                else -> .52 to .35
            }
            val actual = error(samples)
            assertError("gesture $i ${rows.first().split}", actual, caps.first, caps.second)
            val baseline = error(samples, isotropic = true)
            val ratio = if (i < 2) .55 else .20
            assertTrue("gesture $i improves isotropic width", actual.width < baseline.width * ratio)
            assertTrue("gesture $i improves isotropic height", actual.height < baseline.height * ratio)
            if (i >= 2) {
                assertTrue("signed fast expansion remains", samples.any { it.x > .10f && it.y < -.10f })
                assertTrue("signed fast contraction remains", samples.any { it.x < -.10f && it.y > .10f })
                assertTrue("deformation survives UP while lift falls", abs(samples.first { it.row.time > up }.x) > .10f)
            } else assertTrue("slow input stays near its held silhouette", samples.all { abs(it.x) < .025f })
            assertEquals(0f, samples.last().x, .001f)
            assertEquals(0f, samples.last().y, .001f)
        }
    }

    private fun replayFrames(rows: List<Row>, hz: Int, phase: Double, drop: String? = null): List<Sample> {
        val period = 1.0 / hz
        val up = rows.single { it.phase == 3 }.time
        val end = rows.last().time + 2 * period
        val frames = buildList {
            add(0.0)
            repeat(ceil(end / period).toInt() + 2) { frame ->
                val t = (frame + phase) * period
                val omit = when (drop) {
                    "motion" -> t in .65.. .75
                    "release" -> t in (up + .02).. (up + .14)
                    "alternate" -> frame % 2 == 1
                    else -> false
                }
                if (t <= end && !omit) add(t)
            }
        }.distinct()
        val events = (rows.filter { it.kind == "input" }.map { Event(it.time, it.input) } +
            frames.map { Event(it, null) }).sortedWith(compareBy<Event> { it.time }.thenBy { it.input == null })
        val motion = NativeGlassDragDeformation(NativeGlassDragDeformation.Spec.Slider)
        var target = 0f
        var time = 0.0
        val draws = buildList {
            events.forEach { event ->
                motion.advance(event.time - time, target)
                time = event.time
                if (event.input != null) target = event.input
                else add(Draw(time, motion.x, motion.y))
            }
        }
        var cursor = 0
        return rows.filter { it.kind == "frame" }.map { row ->
            if (row.time < 0) Sample(row, 0f, 0f)
            else {
                while (cursor + 1 < draws.size && draws[cursor + 1].time <= row.time) cursor++
                val a = draws[cursor]
                if (drop != null) Sample(row, a.x, a.y) // Actual draw-hold lag is part of the drop check.
                else {
                    val b = draws[minOf(cursor + 1, draws.lastIndex)]
                    // Interpolate the model only; native observations remain untouched.
                    val f = if (a.time == b.time) 0f else ((row.time - a.time) / (b.time - a.time)).toFloat()
                    Sample(row, a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
                }
            }
        }
    }

    @Test fun sixtyAndOneHundredTwentyHzAndDroppedDrawsRetainHeldOutImprovement() {
        recordedGestures().filter { it.first().split == "held_out" }.forEach { rows ->
            val fast = rows.first().gesture == 3
            val baseline = error(replayNativeFrames(rows), isotropic = true)
            for (hz in listOf(60, 120)) for (phase in listOf(0.0, .25, .5, .75)) {
                val actual = error(replayFrames(rows, hz, phase))
                assertError("${rows.first().gesture} $hz Hz phase $phase", actual,
                    if (fast) .56 else .13, if (fast) .37 else .09)
                assertTrue(actual.width < baseline.width * if (fast) .20 else .60)
                assertTrue(actual.height < baseline.height * if (fast) .20 else .60)
            }
            for (drop in listOf("motion", "release", "alternate")) {
                val actual = error(replayFrames(rows, 60, 0.0, drop))
                assertError("${rows.first().gesture} dropped $drop actual draw hold", actual,
                    if (fast) .80 else .13, if (fast) .55 else .09)
                assertTrue(actual.width < baseline.width * if (fast) .25 else .60)
                assertTrue(actual.height < baseline.height * if (fast) .25 else .60)
            }
        }
    }

    @Test fun logicalRtlMovementUsesOppositePhysicalDeformationAndKeepsTheVerticalSign() {
        val right = NativeGlassDragDeformation(NativeGlassDragDeformation.Spec.Slider)
        val left = NativeGlassDragDeformation(NativeGlassDragDeformation.Spec.Slider)
        var visible = false
        repeat(180) { frame ->
            val input = if (frame < 24) frame * 4f else 92f
            right.advance(1.0 / 120, input)
            left.advance(1.0 / 120, -input)
            if (abs(right.x) > .025f) {
                visible = true
                assertTrue("physical X sign reverses", right.x * left.x < 0)
                assertTrue("vertical sign opposes physical X", left.x * left.y < 0 && right.x * right.y < 0)
                val spec = NativeGlassDragDeformation.Spec.Slider
                val rightGain = if (right.x >= 0) spec.positiveGain else spec.negativeGain
                val leftGain = if (left.x >= 0) spec.positiveGain else spec.negativeGain
                assertEquals("sign gains do not change the mirrored physical state", right.x / rightGain, -left.x / leftGain, 1e-5)
            }
        }
        assertTrue(visible)
    }

    @Test fun cancelLowersTheExistingSliderWithoutResettingItsDeformationOrCreatingAPulse() {
        val control = GlassControlMotion(0f, GlassControlMotionSpec.NativeSlider, travelDistanceDp = 298f)
        control.update(0f, pressed = true, dragging = true)
        repeat(60) { control.tick(1.0 / 120) }
        control.update(.8f, pressed = true, dragging = true)
        repeat(10) { control.tick(1.0 / 120) }
        assertTrue("cancel interrupts a visible deformation", abs(control.stretch) > .01f)
        val stretch = control.stretch
        val velocity = control.jelly.velocity
        control.cancel(.8f)
        assertEquals(stretch, control.stretch, 0f)
        assertEquals(velocity, control.jelly.velocity, 0f)
        assertEquals(0f, control.elevation.target, 0f)
        repeat(720) { control.tick(1.0 / 120) }
        assertEquals(.8f, control.position.value, .001f)
        assertEquals(0f, control.stretch, 0f)
        assertEquals(0f, control.stretchY, 0f)
        assertEquals(0f, control.lift, 0f)
        assertFalse(control.isRunning)
    }

    @Test fun extremeFullTravelStepsStayBoundedAndResetWithoutStaleVelocity() {
        val motion = NativeGlassDragDeformation(NativeGlassDragDeformation.Spec.Slider)
        var reachedClamp = false
        repeat(1200) { frame ->
            val input = when {
                frame < 27 -> 0f
                frame < 45 -> 298f
                frame < 600 -> 0f
                else -> if (frame % 4 < 2) 298f else 0f
            }
            motion.advance(1.0 / 60, input)
            assertTrue(motion.x.isFinite() && motion.y.isFinite() && motion.velocity.isFinite())
            assertTrue(motion.x in -.22f.. .22f)
            assertTrue(motion.y in -.2213f.. .2203f)
            reachedClamp = reachedClamp || abs(motion.x) >= .2199f
        }
        assertTrue("exercise the documented uncalibrated extreme-input clamp", reachedClamp)
        motion.reset(42f)
        assertEquals(0f, motion.x, 0f)
        assertEquals(0f, motion.y, 0f)
        assertEquals(0f, motion.velocity, 0f)
        assertFalse(motion.isRunning)
        motion.advance(1.1, 42f)
        assertFalse("existing long-gap reset policy", motion.isRunning)
    }
}
