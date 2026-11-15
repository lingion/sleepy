package com.lingion.sleepy.ui.component.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LiquidGlassPhysicsTest {

    @Test
    fun springDescriptionWithDurationAndBounceCriticallyDamped() {
        val spring = SpringDescription.withDurationAndBounce(durationMs = 500L, bounce = 0.0)
        assertEquals(1.0, spring.mass, 1e-6)
        // k = (4 * pi^2) / (0.5)^2 = 16 * pi^2 ≈ 157.91367041742973
        val expectedK = 16.0 * Math.PI * Math.PI
        assertEquals(expectedK, spring.stiffness, 1e-5)
        // damping = 1.0 * 2.0 * sqrt(k) = 8 * pi ≈ 25.132741228718345
        val expectedC = 8.0 * Math.PI
        assertEquals(expectedC, spring.damping, 1e-5)
    }

    @Test
    fun springDescriptionWithDurationAndBounceBouncy() {
        val spring = SpringDescription.withDurationAndBounce(durationMs = 400L, bounce = 0.15)
        assertEquals(1.0, spring.mass, 1e-6)
        val durationSec = 0.4
        val expectedK = (4.0 * Math.PI * Math.PI) / (durationSec * durationSec)
        assertEquals(expectedK, spring.stiffness, 1e-5)
        val dampingRatio = 1.0 - 0.15
        val expectedC = dampingRatio * 2.0 * kotlin.math.sqrt(expectedK)
        assertEquals(expectedC, spring.damping, 1e-5)
    }

    @Test
    fun springSimulationCriticallyDampedTrajectory() {
        val spring = SpringDescription.withDurationAndBounce(durationMs = 500L, bounce = 0.0)
        val sim = SpringSimulation(spring, start = 0.0, end = 100.0, initialVelocity = 0.0)

        // t = 0
        assertEquals(0.0, sim.x(0.0), 1e-6)
        assertEquals(0.0, sim.dx(0.0), 1e-6)
        assertFalse(sim.isDone(0.0))

        // t = 0.5s (duration)
        val xAt500ms = sim.x(0.5)
        assertTrue("At 500ms should be near 100, got $xAt500ms", xAt500ms > 90.0)

        // t = 1.5s
        assertTrue(sim.isDone(1.5))
        assertEquals(100.0, sim.x(1.5), 1e-3)
    }

    @Test
    fun springSimulationUnderdampedOscillatesAndConverges() {
        val spring = SpringDescription.withDurationAndBounce(durationMs = 400L, bounce = 0.15)
        val sim = SpringSimulation(spring, start = 0.0, end = 1.0, initialVelocity = 0.0)

        assertEquals(0.0, sim.x(0.0), 1e-6)
        assertEquals(0.0, sim.dx(0.0), 1e-6)

        // Underdamped with bounce > 0 should overshoot 1.0
        var overshot = false
        for (step in 1..200) {
            val t = step * 0.005
            if (sim.x(t) > 1.0) {
                overshot = true
                break
            }
        }
        assertTrue("Bouncy spring must overshoot target", overshot)

        // Eventually converges
        assertTrue(sim.isDone(2.0))
        assertEquals(1.0, sim.x(2.0), 1e-3)
    }

    @Test
    fun springSimulationWithInitialVelocity() {
        val spring = SpringDescription.withDampingRatio(mass = 1.0, stiffness = 1024.0, ratio = 0.9)
        val sim = SpringSimulation(spring, start = 10.0, end = 50.0, initialVelocity = 120.0)

        assertEquals(10.0, sim.x(0.0), 1e-6)
        assertEquals(120.0, sim.dx(0.0), 1e-5)
    }

    @Test
    fun rubberBandSymmetryAndBounding() {
        assertEquals(0f, FlClashDockPhysics.rubberBand(0f, 20f), 1e-6f)
        val positive = FlClashDockPhysics.rubberBand(50f, 20f)
        val negative = FlClashDockPhysics.rubberBand(-50f, 20f)
        assertEquals(positive, -negative, 1e-6f)
        assertTrue("Must be less than limit", positive < 20f)
        assertTrue("Must be positive for positive overshoot", positive > 0f)
    }

    @Test
    fun dockLayoutDistributionMatchesFlClash() {
        // 4 tabs on mobile (360dp span)
        val layout = FlClashDockPhysics.dockLayout(360f, 4)
        // (360 - 8) / 4 = 88 -> lens = 88 + 8 = 96, inset = 4
        assertEquals(88f, layout.pitch, 1e-4f)
        assertEquals(96f, layout.lens, 1e-4f)
        assertEquals(4f, layout.inset, 1e-4f)

        // Constrained span (240dp for 4 tabs)
        val constrained = FlClashDockPhysics.dockLayout(240f, 4)
        // The native lens keeps its width while the seats overlap.
        assertEquals(146f / 3f, constrained.pitch, 1e-4f)
        assertEquals(94f, constrained.lens, 1e-4f)
        assertEquals((94f - 146f / 3f) / 2f, constrained.inset, 1e-4f)
    }

    @Test
    fun dockMatchesNativeThreeAndFourDestinationFittingGeometry() {
        // Actual UIKit presentation bounds on iOS 26.5, in a 351pt outer bar.
        val three = FlClashDockPhysics.dockLayout(274f - 8f, 3)
        assertEquals(86f, three.pitch, 1e-4f)
        assertEquals(94f, three.lens, 1e-4f)
        val four = FlClashDockPhysics.dockLayout(309f - 8f, 4)
        assertEquals(69f, four.pitch, 1e-4f)
        assertEquals(94f, four.lens, 1e-4f)
    }

    @Test
    fun lensRectGeometryScalesWithStretchAndLift() {
        val base = FlClashDockPhysics.lensRect(
            position = 1f,
            stretch = 0f,
            pitch = 88f,
            width = 96f,
            height = 62f,
            lift = 0f,
        )
        // center = (1 + 0.5) * 88 = 132
        // left = 132 - 48 = 84, right = 132 + 48 = 180
        assertEquals(84f, base.left, 1e-4f)
        assertEquals(180f, base.right, 1e-4f)
        assertEquals(0f, base.top, 1e-4f)
        assertEquals(62f, base.bottom, 1e-4f)

        // Lift = 1: growth = 8 * 2 = 16 -> width = 112, height = 78
        val lifted = FlClashDockPhysics.lensRect(
            position = 1f,
            stretch = 0f,
            pitch = 88f,
            width = 96f,
            height = 62f,
            lift = 1f,
        )
        assertEquals(112f, lifted.width, 1e-4f)
        assertEquals(78f, lifted.height, 1e-4f)
    }
}
