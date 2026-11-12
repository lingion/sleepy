package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

/** Fit to all eight recorded three-tab drags; four-tab drags are held-out evidence. */
internal object NativeDockPosition {
    const val FOLLOW_STIFFNESS = 325.36
    const val FOLLOW_DAMPING_RATIO = .78158
    val FollowSpring = SpringDescription.withDampingRatio(
        mass = 1.0,
        stiffness = FOLLOW_STIFFNESS,
        ratio = FOLLOW_DAMPING_RATIO,
    )
}

/** The Dock's seven springs share one animation scale and release gate. */
internal class GlassDockMotion(initialPosition: Float) {
    val position = SpringState(initialPosition)
    val lift = SpringState(0f)
    val swell = SpringState(0f)
    val pull = SpringState(0f)
    val jelly = SpringState(0f, rest = FlClashDockPhysics.JELLY_REST)
    val glow = SpringState(0f)
    val glowSpread = SpringState(0f)
    var dropping by mutableStateOf(false)
    private val dragDeformation = NativeDockDeformation()
    private var deformationInteraction = false
    private var lastDeformationTarget = initialPosition
    private var previouslyHeld = false

    /** Seat pitch in dp, supplied after the bar has been measured. */
    var distancePerPosition: Float = 0f
        set(value) {
            val measured = value.takeIf { it.isFinite() && it != 0f } ?: 0f
            if (field == measured) return
            field = measured
            dragDeformation.reset(position.target * measured)
            jelly.jumpTo(0f)
        }

    val stretchY: Float get() = dragDeformation.verticalDeformation(jelly.value)

    val isRunning: Boolean
        get() = dropping || position.isRunning || lift.isRunning || swell.isRunning ||
            pull.isRunning || jelly.isRunning || glow.isRunning || glowSpread.isRunning

    fun needsFrame(dragging: Boolean, scaleFactor: Float): Boolean =
        isRunning || dragging && scaleFactor > 0f

    fun advance(dt: Double, scaleFactor: Float, held: Boolean, dragging: Boolean) {
        if (scaleFactor <= 0f) {
            finish(held)
            return
        }
        val scaledDelta = dt / scaleFactor
        if (held && !previouslyHeld && !dragging) dragDeformation.beginDirect()
        previouslyHeld = held
        if (position.target != lastDeformationTarget && !held && !dragging && !dropping && !deformationInteraction) {
            deformationInteraction = false
            dragDeformation.reset(position.target * distancePerPosition)
            jelly.jumpTo(0f)
        }
        lastDeformationTarget = position.target
        if ((held || dragging || dropping) && !deformationInteraction) {
            dragDeformation.reset(position.value * distancePerPosition)
            deformationInteraction = true
        }
        position.tickDelta(scaledDelta)
        lift.tickDelta(scaledDelta)
        swell.tickDelta(scaledDelta)
        pull.tickDelta(scaledDelta)
        glow.tickDelta(scaledDelta)
        glowSpread.tickDelta(scaledDelta)

        if (distancePerPosition != 0f && deformationInteraction) {
            dragDeformation.advance(scaledDelta, position.target * distancePerPosition, dragging, lift.value)
            jelly.jumpTo(dragDeformation.x)
            jelly.velocity = dragDeformation.velocity
            jelly.isRunning = dragDeformation.isRunning
            if (!dragDeformation.isRunning && !held && !dragging && !dropping) deformationInteraction = false
        } else if (!deformationInteraction) {
            dragDeformation.reset(position.target * distancePerPosition)
            jelly.jumpTo(0f)
        }

        // Normal motion still lowers only after rising and approaching its destination.
        if (dropping && !held) {
            val nearTarget = abs(position.target - position.value) <= FlClashDockPhysics.LIFT_RELEASE
            if (nearTarget && (lift.value >= FlClashDockPhysics.LIFT_SNAP || !lift.isRunning && lift.value > 0f)) {
                dropping = false
                lift.springTo(0f, FlClashSprings.LiftSpring)
            }
        }
    }

    private fun finish(held: Boolean) {
        previouslyHeld = held
        position.jumpTo(position.target)
        lift.jumpTo(if (held) 1f else 0f)
        swell.jumpTo(if (held) 1f else 0f)
        pull.jumpTo(if (held) pull.target else 0f)
        jelly.jumpTo(0f)
        dragDeformation.reset(position.target * distancePerPosition)
        deformationInteraction = false
        lastDeformationTarget = position.target
        glow.jumpTo(if (held) 1f else 0f)
        glowSpread.jumpTo(if (held) glowSpread.target else 1f)
        dropping = false
    }
}
