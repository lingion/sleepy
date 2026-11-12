package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max

internal data class GlassControlMotionSpec(
    val rise: SpringDescription,
    val fall: SpringDescription,
    val release: Release = Release.NearDestination,
    val activationRelease: Release = release,
) {
    enum class Release { NearDestination, FirstPeak, OnRelease }

    companion object {
        val Dock = GlassControlMotionSpec(FlClashSprings.LiftSpring, FlClashSprings.LiftSpring)

        // Fit to the iOS 26.5 native presentation trace: a short, slightly
        // overshooting rise followed by about 250 ms of contraction. A released
        // lens contracts while travelling instead of waiting for its destination.
        val Native = GlassControlMotionSpec(
            SpringDescription.withDampingRatio(mass = 1.0, stiffness = 841.0, ratio = .56),
            FlClashSprings.LiftSpring,
            release = Release.FirstPeak,
        )

        // Native segmented pickers rise monotonically, unlike switches.
        val NativeSegmented = GlassControlMotionSpec(
            SpringDescription.withDampingRatio(mass = 1.0, stiffness = 635.0, ratio = 1.0),
            FlClashSprings.LiftSpring,
            release = Release.OnRelease,
            activationRelease = Release.NearDestination,
        )

        // Clean SwiftUI Slider held/release trace, 2026-11-12. Separate fits:
        // rise k=713, zeta=.547; fall k=216, zeta=.779 (mass normalized to one).
        val NativeSlider = GlassControlMotionSpec(
            SpringDescription.withDampingRatio(mass = 1.0, stiffness = 713.0, ratio = .547),
            SpringDescription.withDampingRatio(mass = 1.0, stiffness = 216.0, ratio = .779),
            release = Release.OnRelease,
        )
    }
}

/** Velocity-continuous lift, follow and braking in logical travel units. */
internal class GlassControlMotion(
    initialPosition: Float,
    private val spec: GlassControlMotionSpec = GlassControlMotionSpec.Dock,
    travelDistanceDp: Float = 0f,
) {
    val position = SpringState(initialPosition)
    val elevation = SpringState(0f)
    val jelly = SpringState(0f, rest = FlClashDockPhysics.JELLY_REST)
    private var pressed = false
    private var dragging = false
    private var activation = 0
    private var riseHasPeaked = false
    private var releasePhase = spec.release
    private var dropping by mutableStateOf(false)
    private var deformationInteraction = false
    private var distancePerPositionDp = travelDistanceDp.takeIf { it.isFinite() && it != 0f } ?: 0f
    private val dragDeformation = NativeGlassDragDeformation(
        if (spec == GlassControlMotionSpec.Dock) NativeGlassDragDeformation.Spec.Dock
        else NativeGlassDragDeformation.Spec.Segment,
        initialPosition * distancePerPositionDp,
    )

    val lift: Float get() = elevation.value.coerceIn(0f, 1f)
    val geometryLift: Float get() = elevation.value.coerceIn(-.14f, 1.14f)
    // Native deformation rebounds across touch-up while elevation contracts;
    // multiplying by lift would erase the measured release-side rebound.
    val stretch: Float get() = jelly.value
    val stretchY: Float get() = dragDeformation.verticalDeformation(jelly.value)
    val isRunning: Boolean get() = dropping || position.isRunning || elevation.isRunning || jelly.isRunning

    /** dp per logical position unit: full travel for 0..1, seat pitch for indices. */
    fun updateMotionDistance(distanceDp: Float) {
        val measured = distanceDp.takeIf { it.isFinite() && it != 0f } ?: 0f
        if (measured == distancePerPositionDp) return
        distancePerPositionDp = measured
        dragDeformation.reset(position.target * measured)
        jelly.jumpTo(0f)
    }

    fun update(target: Float, pressed: Boolean, dragging: Boolean, activation: Int = 0) {
        val activated = activation > this.activation
        val interactiveInput = pressed || dragging || activated
        val silentExternalChange = target != position.target && !interactiveInput && !this.pressed && !this.dragging
        if (silentExternalChange) {
            deformationInteraction = false
            dragDeformation.reset(target * distancePerPositionDp)
            jelly.jumpTo(0f)
        } else if (interactiveInput && !deformationInteraction) {
            dragDeformation.reset(position.value * distancePerPositionDp)
            deformationInteraction = true
        }
        this.activation = activation
        if (pressed && !this.pressed || activated) {
            releasePhase = if (activated) spec.activationRelease else spec.release
            if (elevation.target != 1f) riseHasPeaked = false
            elevation.springTo(1f, spec.rise)
            dropping = !pressed
        } else if (!pressed && this.pressed) {
            dropping = true
        }
        if (releasePhase == GlassControlMotionSpec.Release.OnRelease && !pressed && (this.pressed || activated)) {
            // A down/up pair may coalesce before its first visual frame. Keep
            // a partial pulse in that case instead of cancelling it at zero.
            val coalescedTap = activated && elevation.value == 0f && elevation.velocity == 0f
            dropping = coalescedTap
            if (!coalescedTap) elevation.springTo(0f, spec.fall)
        }
        if (position.target != target || this.dragging != dragging && position.isRunning) {
            position.springTo(target, if (dragging) FlClashSprings.FollowSpring else FlClashSprings.TravelSpring)
        }
        this.pressed = pressed
        this.dragging = dragging
    }

    fun tick(dt: Double) {
        position.tickDelta(dt)
        elevation.tickDelta(dt)
        if (distancePerPositionDp != 0f && deformationInteraction) {
            dragDeformation.advance(dt, position.target * distancePerPositionDp)
            // Preserve the existing observable drawing state without another
            // spring filtering an already integrated deformation trajectory.
            jelly.jumpTo(dragDeformation.x)
            jelly.velocity = dragDeformation.velocity
            jelly.isRunning = dragDeformation.isRunning
            if (!dragDeformation.isRunning && !pressed && !dragging) deformationInteraction = false
        } else if (!deformationInteraction) {
            dragDeformation.reset(position.target * distancePerPositionDp)
            jelly.jumpTo(0f)
        }
        if (elevation.target == 1f && lift >= FlClashDockPhysics.LIFT_SNAP && elevation.velocity <= 0f) {
            riseHasPeaked = true
        }
        val canLower = when (releasePhase) {
            GlassControlMotionSpec.Release.FirstPeak -> riseHasPeaked
            GlassControlMotionSpec.Release.OnRelease -> lift >= .35f
            GlassControlMotionSpec.Release.NearDestination ->
                lift >= FlClashDockPhysics.LIFT_SNAP &&
                    abs(position.target - position.value) <= FlClashDockPhysics.LIFT_RELEASE
        }
        if (dropping && !pressed && canLower) {
            dropping = false
            elevation.springTo(0f, spec.fall)
        }
    }

    fun finish() {
        position.jumpTo(position.target)
        elevation.jumpTo(if (pressed) 1f else 0f)
        jelly.jumpTo(0f)
        dragDeformation.reset(position.target * distancePerPositionDp)
        deformationInteraction = false
        dropping = false
        riseHasPeaked = pressed
    }
}

@Composable
internal fun rememberGlassControlMotion(
    target: Float,
    pressed: Boolean,
    dragging: Boolean = false,
    activation: Int = 0,
    enabled: Boolean = true,
    spec: GlassControlMotionSpec = GlassControlMotionSpec.Dock,
    travelDistanceDp: Float = 0f,
): GlassControlMotion {
    val motion = remember(spec) { GlassControlMotion(target, spec, travelDistanceDp) }
    LaunchedEffect(motion, travelDistanceDp) { motion.updateMotionDistance(travelDistanceDp) }
    LaunchedEffect(motion, target, pressed, dragging, activation, enabled) {
        motion.update(target, pressed && enabled, dragging && enabled, activation)
        if (!enabled) motion.finish()
    }
    LaunchedEffect(motion) {
        val durationScale = coroutineContext[MotionDurationScale]
        runActiveFrameLoop({ motion.isRunning }) { dt ->
            val scale = durationScale?.scaleFactor ?: 1f
            if (scale <= 0f) motion.finish() else motion.tick(dt / scale)
        }
    }
    return motion
}

/** Deform the measured lens, keeping its sampling texture at the scene's original scale. */
internal fun glassLensWidth(width: Float, stretch: Float): Float = width * (1f + stretch)

internal fun glassLensHeight(height: Float, stretch: Float, minimum: Float = 0f): Float =
    max(minimum, height * (1f - stretch * FlClashDockPhysics.JELLY_ASPECT))
