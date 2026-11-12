package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageMenuMotionTest {
    private val anchor = Rect(20f, 208f, 382f, 208f + 154f / 3f)
    private val menu = Rect(76f, 208f, 326f, 438f)

    @Test fun captureStartClockPreservesTheNativeIrregularIntervals() {
        samples().groupBy { it.scene }.values.forEach { rows ->
            val first = rows.first()
            rows.forEach { row ->
                assertEquals(
                    "capture cost changes the relative completion clock, frame ${row.frame}",
                    row.observedSeconds - (row.captureCostMs - first.captureCostMs) / 1000.0,
                    row.captureStartSeconds,
                    .0000002,
                )
            }
        }
        val opening = samples().filter { it.scene == "open1" }
        assertEquals(.0329086, opening[1].captureStartSeconds, .0000001)
        assertEquals(.0144743, opening[1].observedSeconds, .0000001)
        assertTrue(opening[1].captureStartSeconds - opening[1].observedSeconds > .018)
    }

    @Test fun springAndBothSilhouettesFollowMeasuredNativeFrames() {
        samples().groupBy { it.scene }.forEach { (scene, rows) ->
            val motion = LanguageMenuMotion()
            if (scene == "close3") {
                motion.finish()
                motion.close()
            }
            // These origins are fitted capture timing, not activation delays in
            // the implementation. The fixture retains the original clocks.
            val origin = if (scene == "open1") .0016105962277476254 else .009337874256840751
            var previous = 0.0
            rows.forEach { row ->
                val time = (row.captureStartSeconds - origin).coerceAtLeast(0.0)
                motion.tick(time - previous)
                previous = time
                val geometry = motion.geometry(anchor, menu)
                val message = "$scene frame ${row.frame} at $time s"
                assertEquals("progress $message", row.progress, motion.progress.value.toDouble(), .0015)
                assertEquals("menu width $message", row.menuWidth, geometry.material.width.toDouble(), .33)
                // One SDF sample (frame 1421) differs from its portal-derived
                // geometry by about 1 pt; retain it rather than erase the residual.
                assertEquals("material height $message", row.materialHeight, geometry.material.height.toDouble(), 1.1)
                assertEquals("content height $message", row.contentHeight, geometry.content.height.toDouble(), .30)
                assertEquals("menu Y $message", row.menuY, geometry.material.top.toDouble(), .06)
                assertEquals("anchor width $message", row.anchorWidth, geometry.anchor.width.toDouble(), .50)
                assertEquals("anchor height $message", row.anchorHeight, geometry.anchor.height.toDouble(), .06)
                assertEquals("anchor Y $message", row.anchorY, geometry.anchor.top.toDouble(), .065)
                assertEquals("anchor text alpha $message", row.anchorContentAlpha, motion.anchorContentAlpha.toDouble(), .0025)
            }
        }
    }

    @Test fun overshootAffectsGeometryButRenderingAlphaStaysBounded() {
        val motion = LanguageMenuMotion()
        motion.tick(.265)
        assertTrue(motion.progress.value > 1.02f)
        assertTrue(motion.geometry(anchor, menu).material.width > menu.width)
        assertEquals(1f, motion.alpha, 0f)
        assertEquals(0f, motion.anchorContentAlpha, 0f)
        motion.finish()
        motion.close()
        motion.tick(.41)
        assertTrue(motion.progress.value < -.01f)
        assertTrue(motion.geometry(anchor, menu).material.width < anchor.height / 2f)
        assertEquals(0f, motion.alpha, 0f)
        assertEquals(1f, motion.anchorContentAlpha, 0f)
    }

    @Test fun closeDuringOpeningKeepsPositionAndVelocity() {
        val step = .00001
        listOf(0.0, .02, .07, .15, .27, .45, .8).forEach { age ->
            val motion = LanguageMenuMotion().apply { tick(age) }
            val before = coordinates(motion.geometry(anchor, menu))
            val prior = LanguageMenuMotion().apply { tick((age - step).coerceAtLeast(0.0)) }
            val expectedVelocity = if (age == 0.0) DoubleArray(before.size) else {
                val after = LanguageMenuMotion().apply { tick(age + step) }
                val left = coordinates(prior.geometry(anchor, menu))
                val right = coordinates(after.geometry(anchor, menu))
                DoubleArray(before.size) { (right[it] - left[it]) / (2.0 * step) }
            }
            val progress = motion.progress.value
            val velocity = motion.progress.velocity
            motion.close()
            assertEquals(progress, motion.progress.value, 0f)
            assertEquals(velocity, motion.progress.velocity, 0f)
            coordinates(motion.geometry(anchor, menu)).forEachIndexed { i, value ->
                assertEquals("position at opening age $age, coordinate $i", before[i], value, .0001)
            }
            motion.tick(step)
            coordinates(motion.geometry(anchor, menu)).forEachIndexed { i, value ->
                // Compare tiny displacement, avoiding Float-to-velocity
                // amplification when checking an interrupted trajectory.
                assertEquals("velocity at opening age $age, coordinate $i", before[i] + expectedVelocity[i] * step, value, .00012)
            }
        }
    }

    @Test fun removalWaitsForTheReturningAnchorToSettle() {
        val motion = LanguageMenuMotion().apply { finish(); close() }
        motion.tick(.65)
        assertTrue(motion.isRunning)
        assertFalse(motion.canRemove)
        assertTrue(motion.geometry(anchor, menu).anchor.width - anchor.width > .6f)
        motion.tick(.20)
        assertFalse(motion.canRemove)
        motion.tick(.15)
        assertTrue(motion.canRemove)
        assertFalse(motion.isRunning)
        val settled = motion.geometry(anchor, menu).anchor
        assertEquals(anchor.left, settled.left, .0001f)
        assertEquals(anchor.top, settled.top, .0001f)
        assertEquals(anchor.width, settled.width, .0001f)
        assertEquals(anchor.height, settled.height, .0001f)
    }

    @Test fun nonuniformPathIsC1IncludingItsHeldEndpoints() {
        val points = doubleArrayOf(0.0, 0.0, .007, .067, .024, .433, .074, .96, .85, 0.0)
        for (i in points.indices step 2) {
            assertEquals(points[i + 1], LanguageMenuMotion.samplePath(points, points[i]), 1e-12)
            val left = LanguageMenuMotion.samplePathVelocity(points, points[i] - 1e-8)
            val right = LanguageMenuMotion.samplePathVelocity(points, points[i] + 1e-8)
            assertEquals("slope at ${points[i]}", left, right, .0002)
        }
        assertEquals(0.0, LanguageMenuMotion.samplePathVelocity(points, 0.0), 0.0)
        assertEquals(0.0, LanguageMenuMotion.samplePathVelocity(points, .85), 0.0)
    }

    @Test fun finishAndInvalidFrameDeltasKeepStableGeometry() {
        val motion = LanguageMenuMotion()
        val initial = coordinates(motion.geometry(anchor, menu))
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -.1, 0.0).forEach(motion::tick)
        assertTrue(initial.contentEquals(coordinates(motion.geometry(anchor, menu))))
        motion.finish()
        assertFalse(motion.isRunning)
        assertEquals(menu, motion.geometry(anchor, menu).material)
        motion.close()
        motion.finish()
        assertTrue(motion.canRemove)
        assertEquals(anchor, motion.geometry(anchor, menu).anchor)
    }

    private fun coordinates(geometry: LanguageMenuGeometry): DoubleArray = with(geometry) {
        doubleArrayOf(material.left.toDouble(), material.top.toDouble(), material.width.toDouble(), material.height.toDouble(),
            content.height.toDouble(), anchor.left.toDouble(), anchor.top.toDouble(), anchor.width.toDouble(), anchor.height.toDouble())
    }

    private fun samples(): List<Sample> = checkNotNull(javaClass.classLoader?.getResourceAsStream("native-glass/language-menu-ios26.5.csv"))
        .bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith('#') }.drop(1).map { line ->
                val v = line.split(',')
                Sample(v[0], v[1].toInt(), *v.drop(2).map(String::toDouble).toDoubleArray())
            }.toList()
        }

    private class Sample(val scene: String, val frame: Int, vararg values: Double) {
        val captureStartSeconds = values[0]
        val observedSeconds = values[2]
        val captureCostMs = values[3]
        val progress = values[4]
        val menuY = values[5]
        val menuWidth = values[6]
        val materialHeight = values[7]
        val contentHeight = values[8]
        val anchorY = values[9]
        val anchorWidth = values[10]
        val anchorHeight = values[11]
        val anchorContentAlpha = values[12]
    }
}
