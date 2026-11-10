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
        )
    }
}

/** Velocity-continuous lift, follow and braking in logical travel units. */
internal class GlassControlMotion(
    initialPosition: Float,
    private val spec: GlassControlMotionSpec = GlassControlMotionSpec.Dock,
) {
    val position = SpringState(initialPosition)
    val elevation = SpringState(0f)
    val jelly = SpringState(0f, rest = FlClashDockPhysics.JELLY_REST)
    private var pressed = false
    private var dragging = false
    private var activation = 0
    private var riseHasPeaked = false
    private var dropping by mutableStateOf(false)

    val lift: Float get() = elevation.value.coerceIn(0f, 1f)
    val geometryLift: Float get() = elevation.value.coerceIn(0f, 1.14f)
    val stretch: Float get() = jelly.value.coerceIn(-.18f, .3f) * lift
    val isRunning: Boolean get() = dropping || position.isRunning || elevation.isRunning || jelly.isRunning

    fun update(target: Float, pressed: Boolean, dragging: Boolean, activation: Int = 0) {
        val activated = activation > this.activation
        this.activation = activation
        if (pressed && !this.pressed || activated) {
            if (elevation.target != 1f) riseHasPeaked = false
            elevation.springTo(1f, spec.rise)
            dropping = !pressed
        } else if (!pressed && this.pressed) {
            dropping = true
        }
        if (spec.release == GlassControlMotionSpec.Release.OnRelease && !pressed && (this.pressed || activated)) {
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
        jelly.tickDelta(dt)
        if (elevation.target == 1f && lift >= FlClashDockPhysics.LIFT_SNAP && elevation.velocity <= 0f) {
            riseHasPeaked = true
        }
        val wobble = when {
            dragging -> FlClashDockPhysics.JELLY_STRETCH *
                FlClashDockPhysics.saturate(abs(position.velocity) / FlClashDockPhysics.JELLY_SPEED)
            position.isRunning -> FlClashDockPhysics.wobbleTarget(position.velocity, position.acceleration)
            else -> 0f
        }
        if (abs(wobble - jelly.target) > 1e-3f) jelly.springTo(wobble, FlClashSprings.JellySpring)
        val canLower = when (spec.release) {
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
): GlassControlMotion {
    val motion = remember(spec) { GlassControlMotion(target, spec) }
    LaunchedEffect(target, pressed, dragging, activation, enabled) {
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
