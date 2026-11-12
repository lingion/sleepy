package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.ui.geometry.Offset

/** Fits the iOS 26.5 button presentation trace; rubber-band tracking stays independent. */
internal class GlassPressMotion(prominent: Boolean = false) {
    private val rise = SpringDescription.withDampingRatio(
        mass = 1.0, stiffness = if (prominent) 427.0 else 570.0, ratio = .625,
    )
    private val fall = SpringDescription.withDampingRatio(mass = 1.0, stiffness = 289.0, ratio = .565)
    val lift = SpringState(0f)
    val pullX = SpringState(0f)
    val pullY = SpringState(0f)
    private var held = false

    val isRunning: Boolean get() = lift.isRunning || pullX.isRunning || pullY.isRunning

    fun press() {
        held = true
        lift.springTo(1f, rise)
    }

    fun drag(delta: Offset, shortestSide: Float) {
        if (!held) return
        val limit = shortestSide * (7f / 32f)
        fun rubber(value: Float) = if (limit > 0f) FlClashDockPhysics.rubberBand(value, limit) else 0f
        pullX.springTo(rubber(delta.x), FlClashSprings.TrackSpring)
        pullY.springTo(rubber(delta.y), FlClashSprings.TrackSpring)
    }

    fun release() {
        if (!held) return
        held = false
        pullX.springTo(0f, FlClashSprings.PressSpring)
        pullY.springTo(0f, FlClashSprings.PressSpring)
        // A clean 67 ms native tap peaks below its held geometry. Retarget now,
        // retaining velocity, so the rise and fall overlap without a full-rise gate.
        lift.springTo(0f, fall)
    }

    fun tick(dt: Double) {
        lift.tickDelta(dt)
        pullX.tickDelta(dt)
        pullY.tickDelta(dt)
    }

    fun finish() {
        lift.jumpTo(if (held) 1f else 0f)
        pullX.jumpTo(if (held) pullX.target else 0f)
        pullY.jumpTo(if (held) pullY.target else 0f)
    }

    fun reset() {
        held = false
        finish()
    }
}
