package com.lingion.sleepy.ui.component.liquidglass

import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeDockDirectDeformationTest {
    private data class NativeFrame(
        val case: String, val split: String, val travel: Float, val seconds: Double,
        val scaleX: Float, val scaleY: Float, val lift: Float,
        val deliveredSeconds: Double, val observedOrigin: Double,
    )

    /**
     * Native-only fixture from preserved UIKit presentation observations:
     * FourTabFirstLastMotion/3215FC96 and DirectSelectionPhases/E8B6AA04,
     * 2026-11-13, natural 62 pt platter / 54 pt lens height.
     * Frame time is the capture interval midpoint. Each observed origin is the
     * last still-resting presentation before the rise, not an imposed delay.
     * Both delivered-input and relative-origin times remain in the fixture.
     */
    private fun recordings(): List<List<NativeFrame>> {
        val stream = checkNotNull(javaClass.getResourceAsStream("/native-glass/dock-direct-selection.csv"))
        return stream.bufferedReader().useLines { lines ->
            lines.drop(1).filter(String::isNotBlank).map { line ->
                val c = line.split(',')
                NativeFrame(c[0], c[1], c[2].toFloat(), c[3].toDouble(), c[4].toFloat(),
                    c[5].toFloat(), c[6].toFloat(), c[7].toDouble(), c[8].toDouble())
            }.toList().groupBy { it.case }.values.toList()
        }
    }

    @Test fun naturalDirectFitPredictsHoldoutPeaksReboundsAndCompleteSettlingTail() {
        val cases = recordings()
        assertEquals(5, cases.count { it.first().split == "train" })
        assertEquals(6, cases.count { it.first().split == "holdout" })
        cases.forEach { frames ->
            val motion = NativeDockDeformation()
            motion.beginDirect()
            var previous = 0.0
            val xErrors = ArrayList<Double>()
            val yErrors = ArrayList<Double>()
            val predicted = ArrayList<Float>()
            frames.forEach { frame ->
                assertEquals("Observed clock provenance", frame.deliveredSeconds - frame.observedOrigin, frame.seconds, 2e-9)
                val time = frame.seconds.coerceAtLeast(0.0)
                motion.advance(time - previous, frame.travel, dragging = false, lift = frame.lift)
                previous = time
                predicted += motion.x
                xErrors += (1f + motion.x - frame.scaleX).toDouble()
                yErrors += (1f + motion.verticalDeformation(motion.x) - frame.scaleY).toDouble()
            }
            val name = "${frames.first().split} ${frames.first().case}"
            val rmsX = sqrt(xErrors.map { it * it }.average())
            val rmsY = sqrt(yErrors.map { it * it }.average())
            assertTrue("$name horizontal-scale RMSE $rmsX", rmsX < .009)
            assertTrue("$name maximum horizontal residual", xErrors.all { abs(it) < .030 })
            assertTrue("$name vertical-scale RMSE $rmsY", rmsY < .019)
            // Four-item hold Y retains a larger local residual than released
            // taps. Keep that limit explicit rather than hiding it in RMSE.
            val yMaximum = if (frames.first().case in setOf("four-3", "four-4")) .046 else .033
            assertTrue("$name maximum vertical residual", yErrors.all { abs(it) < yMaximum })
            val direction = if (frames.first().travel > 0f) 1 else -1
            val firstLobe = frames.indices.filter { frames[it].seconds < .4 }
            val peak = firstLobe.maxBy { predicted[it] * direction }
            val nativePeak = firstLobe.maxBy { (frames[it].scaleX - 1f) * direction }
            assertTrue("$name first signed peak follows native observation", abs(frames[peak].seconds - frames[nativePeak].seconds) < .025)
            assertTrue("$name first signed peak amplitude", abs(predicted[peak] - (frames[nativePeak].scaleX - 1f)) < .012)
            val secondLobe = frames.indices.filter { frames[it].seconds > .38 && frames[it].seconds < .75 }
            val rebound = secondLobe.minBy { predicted[it] * direction }
            val nativeRebound = secondLobe.minBy { (frames[it].scaleX - 1f) * direction }
            assertTrue("$name rebound follows native observation", abs(frames[rebound].seconds - frames[nativeRebound].seconds) < .025)
            assertTrue("$name rebound amplitude", abs(predicted[rebound] - (frames[nativeRebound].scaleX - 1f)) < .018)
            assertTrue("$name includes the full settling tail", frames.last().deliveredSeconds > 1.48)
            val tail = frames.indices.filter { frames[it].deliveredSeconds >= 1.0 }
            assertTrue("$name has at least 29 tail observations", tail.size >= 29)
            val tailX = sqrt(tail.map { xErrors[it] * xErrors[it] }.average())
            val tailY = sqrt(tail.map { yErrors[it] * yErrors[it] }.average())
            assertTrue("$name tail horizontal RMSE $tailX", tailX < .0026)
            assertTrue("$name tail maximum horizontal residual", tail.all { abs(xErrors[it]) < .0042 })
            assertTrue("$name tail vertical RMSE $tailY", tailY < .0037)
            assertTrue("$name tail maximum vertical residual", tail.all { abs(yErrors[it]) < .0065 })
        }
    }

    @Test fun dragOnlyTrajectoriesRetainTheExistingEquationsAtEveryFrame() {
        for (hz in listOf(60, 90, 120)) for (direction in listOf(-1f, 1f)) {
            val old = NativeGlassDragDeformation(NativeGlassDragDeformation.Spec.Dock)
            val dock = NativeDockDeformation()
            repeat(hz * 2) { frame ->
                val t = (frame + 1.0) / hz
                val target = (if (t < .3) t * 690 else if (t < .6) (.6 - t) * 690 else 0.0).toFloat() * direction
                old.advance(1.0 / hz, target)
                dock.advance(1.0 / hz, target, dragging = true, lift = 1f)
                assertEquals("$hz Hz frame $frame x", old.x, dock.x, 1e-6f)
                assertEquals("$hz Hz frame $frame velocity", old.velocity, dock.velocity, 1e-5f)
                assertEquals("$hz Hz frame $frame y", old.y, dock.verticalDeformation(dock.x), 1e-6f)
            }
        }
    }

    @Test fun releaseResponseDependsOnTravelRatherThanAbsoluteTabCoordinates() {
        val local = NativeDockDeformation()
        val translated = NativeDockDeformation(69f)
        repeat(180) { frame ->
            val lift = when {
                frame < 30 -> frame / 30f
                frame < 45 -> 1f
                frame < 75 -> (75 - frame) / 30f
                else -> 0f
            }
            local.advance(1.0 / 120, 207f, dragging = false, lift = lift)
            translated.advance(1.0 / 120, 276f, dragging = false, lift = lift)
            assertEquals("Translation at frame $frame x", local.x, translated.x, 1e-6f)
            assertEquals("Translation at frame $frame velocity", local.velocity, translated.velocity, 1e-6f)
            assertEquals("Translation at frame $frame y", local.verticalDeformation(local.x),
                translated.verticalDeformation(translated.x), 1e-6f)
        }
    }

    @Test fun aNativeTapSettlesBeforeTheNextRecordedSelectionWithoutRestartingMomentum() {
        val frames = recordings().first { it.first().case == "four-23" }
        val dock = NativeDockDeformation()
        var previous = 0.0
        frames.forEach { frame ->
            val time = frame.seconds.coerceAtLeast(0.0)
            dock.advance(time - previous, frame.travel, dragging = false, lift = frame.lift)
            previous = time
        }
        assertTrue("The previous tap has no visible residual at 1.5 seconds", abs(dock.x) < .001f)
        val settledX = dock.x
        val settledVelocity = dock.velocity
        dock.beginDirect()
        dock.advance(0.0, 0f, dragging = false, lift = 0f)
        assertEquals("The reverse selection retains existing displacement", settledX, dock.x, 0f)
        assertEquals("The reverse selection retains existing velocity", settledVelocity, dock.velocity, 0f)
        dock.advance(1.0 / 120, 0f, dragging = false, lift = .02f)
        assertTrue("The reverse command is pending", dock.isRunning)
    }

    @Test fun zeroTimeOriginRetainsDirectAndDragCommandsWithoutAdvancingThem() {
        for (dragging in listOf(false, true)) {
            val dock = NativeDockDeformation()
            dock.advance(0.0, 207f, dragging, lift = 0f)
            assertEquals(0f, dock.x, 0f)
            assertEquals(0f, dock.velocity, 0f)
            assertTrue("A zero-time frame retains the pending command", dock.isRunning)
            dock.advance(1.0 / 120, 207f, dragging, lift = 0f)
            assertTrue(dock.x > 0f)
        }
    }

    @Test fun modeHandoffsKeepBothAxesAndTheirVelocitiesContinuous() {
        val dock = NativeDockDeformation()
        repeat(16) { dock.advance(1.0 / 120, 207f, dragging = false, lift = 1f) }
        val directX = dock.x
        val directVelocity = dock.velocity
        val directY = dock.verticalDeformation(dock.x)
        val directYVelocity = dock.verticalVelocity
        assertTrue("The handoff exercises visible deformation", abs(directY) > .03f)
        dock.advance(0.0, 207f, dragging = true, lift = 1f)
        assertEquals(directX, dock.x, 1e-6f)
        assertEquals(directVelocity, dock.velocity, 1e-6f)
        assertEquals("Direct to drag Y", directY, dock.verticalDeformation(dock.x), 1e-6f)
        assertEquals("Direct to drag Y velocity", directYVelocity, dock.verticalVelocity, 1e-5f)
        repeat(10) { dock.advance(1.0 / 120, 207f - it * 8f, dragging = true, lift = 1f) }
        val draggedX = dock.x
        val draggedVelocity = dock.velocity
        val draggedY = dock.verticalDeformation(dock.x)
        val draggedYVelocity = dock.verticalVelocity
        dock.beginDirect()
        assertEquals(draggedX, dock.x, 1e-6f)
        assertEquals(draggedVelocity, dock.velocity, 1e-6f)
        assertEquals("Drag to direct Y", draggedY, dock.verticalDeformation(dock.x), 1e-6f)
        assertEquals("Drag to direct Y velocity", draggedYVelocity, dock.verticalVelocity, 1e-5f)
        repeat(1_200) { dock.advance(1.0 / 120, 0f, dragging = false, lift = 0f) }
        assertFalse("The handoff residual settles with the physical state", dock.isRunning)
        assertEquals(0f, dock.verticalDeformation(dock.x), 0f)
        assertEquals(0f, dock.verticalVelocity, 0f)
    }

    @Test fun rapidReversalDoesNotEraseMomentumAndSettlesAfterTheLastCommand() {
        val dock = NativeDockDeformation()
        repeat(8) { dock.advance(1.0 / 120, 207f, dragging = false, lift = 1f) }
        val x = dock.x
        val velocity = dock.velocity
        assertTrue(abs(velocity) > .001f)
        dock.beginDirect()
        dock.advance(0.0, 0f, dragging = false, lift = 1f)
        assertEquals(x, dock.x, 0f)
        assertEquals(velocity, dock.velocity, 0f)
        repeat(6) { tap ->
            dock.beginDirect()
            repeat(13) { dock.advance(1.0 / 120, if (tap % 2 == 0) 0f else 207f, dragging = false, lift = 1f) }
        }
        repeat(1_200) { dock.advance(1.0 / 120, 0f, dragging = false, lift = 0f) }
        assertFalse(dock.isRunning)
        assertEquals(0f, dock.x, 0f)
        assertEquals(0f, dock.velocity, 0f)
    }

    @Test fun cancellingADockPreviewKeepsItsJellyDisplacementAndVelocity() {
        val dock = GlassDockMotion(0f)
        dock.distancePerPosition = 69f
        dock.position.springTo(3f, FlClashSprings.TravelSpring)
        dock.lift.springTo(1f, FlClashSprings.LiftSpring)
        repeat(16) { dock.advance(1.0 / 120, 1f, held = true, dragging = false) }
        val x = dock.jelly.value
        val velocity = dock.jelly.velocity
        dock.dropping = false
        dock.position.springTo(0f, FlClashSprings.TravelSpring)
        dock.lift.springTo(0f, FlClashSprings.LiftSpring)
        dock.advance(0.0, 1f, held = false, dragging = false)
        assertEquals(x, dock.jelly.value, 0f)
        assertEquals(velocity, dock.jelly.velocity, 0f)
    }
}
