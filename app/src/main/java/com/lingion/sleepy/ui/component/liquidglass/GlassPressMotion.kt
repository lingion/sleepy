package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/** FLClash ElasticPress: independent lift and two-dimensional rubber-band springs. */
internal class GlassPressMotion {
    val lift = SpringState(0f)
    val pullX = SpringState(0f)
    val pullY = SpringState(0f)
    private var held = false
    private var dropping by mutableStateOf(false)

    val isRunning: Boolean get() = dropping || lift.isRunning || pullX.isRunning || pullY.isRunning

    fun press() {
        held = true
        dropping = false
        lift.springTo(1f, FlClashSprings.PressSpring)
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
        dropping = true
        settle()
    }

    private fun settle() {
        // Even a short tap completes the rise before the pane springs down.
        if (dropping && lift.value >= .975f) {
            dropping = false
            lift.springTo(0f, FlClashSprings.PressSpring)
        }
    }

    fun tick(dt: Double) {
        lift.tickDelta(dt)
        pullX.tickDelta(dt)
        pullY.tickDelta(dt)
        settle()
    }

    fun finish() {
        lift.jumpTo(if (held) 1f else 0f)
        pullX.jumpTo(if (held) pullX.target else 0f)
        pullY.jumpTo(if (held) pullY.target else 0f)
        dropping = false
    }

    fun reset() {
        held = false
        finish()
    }
}
