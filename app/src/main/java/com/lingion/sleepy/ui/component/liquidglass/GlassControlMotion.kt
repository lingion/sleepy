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

/** The Dock's lift, follow and braking springs, expressed in logical travel units. */
internal class GlassControlMotion(initialPosition: Float) {
    val position = SpringState(initialPosition)
    val elevation = SpringState(0f)
    val jelly = SpringState(0f, rest = FlClashDockPhysics.JELLY_REST)
    private var pressed = false
    private var dragging = false
    private var activation = 0
    private var dropping by mutableStateOf(false)

    val lift: Float get() = elevation.value.coerceIn(0f, 1f)
    val stretch: Float get() = jelly.value.coerceIn(-.18f, .3f) * lift
    val isRunning: Boolean get() = dropping || position.isRunning || elevation.isRunning || jelly.isRunning

    fun update(target: Float, pressed: Boolean, dragging: Boolean, activation: Int = 0) {
        val activated = activation > this.activation
        this.activation = activation
        if (pressed && !this.pressed || activated) {
            elevation.springTo(1f, FlClashSprings.LiftSpring)
            dropping = !pressed
        } else if (!pressed && this.pressed) {
            dropping = true
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
        val wobble = when {
            dragging -> FlClashDockPhysics.JELLY_STRETCH *
                FlClashDockPhysics.saturate(abs(position.velocity) / FlClashDockPhysics.JELLY_SPEED)
            position.isRunning -> FlClashDockPhysics.wobbleTarget(position.velocity, position.acceleration)
            else -> 0f
        }
        if (abs(wobble - jelly.target) > 1e-3f) jelly.springTo(wobble, FlClashSprings.JellySpring)
        if (dropping && !pressed && lift >= FlClashDockPhysics.LIFT_SNAP &&
            abs(position.target - position.value) <= FlClashDockPhysics.LIFT_RELEASE) {
            dropping = false
            elevation.springTo(0f, FlClashSprings.LiftSpring)
        }
    }

    fun finish() {
        position.jumpTo(position.target)
        elevation.jumpTo(if (pressed) 1f else 0f)
        jelly.jumpTo(0f)
        dropping = false
    }
}

@Composable
internal fun rememberGlassControlMotion(
    target: Float,
    pressed: Boolean,
    dragging: Boolean = false,
    activation: Int = 0,
    enabled: Boolean = true,
): GlassControlMotion {
    val motion = remember { GlassControlMotion(target) }
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
