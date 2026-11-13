package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/** Fit to all eight recorded three-tab drags; four-tab drags are held-out evidence. */
object NativeDockPosition {
    const val FOLLOW_STIFFNESS = 325.36
    const val FOLLOW_DAMPING_RATIO = .78158
    val FollowSpring = SpringDescription.withDampingRatio(
        mass = 1.0,
        stiffness = FOLLOW_STIFFNESS,
        ratio = FOLLOW_DAMPING_RATIO,
    )
}

/** The Dock's seven springs share one animation scale and release gate. */
class GlassDockMotion(initialPosition: Float) {
    val position = SpringState(initialPosition)
    val lift = SpringState(0f)
    val swell = SpringState(0f)
    val pull = SpringState(0f)
    val jelly = SpringState(0f, rest = FlClashDockPhysics.JELLY_REST)
    val glow = SpringState(0f)
    val glowSpread = SpringState(0f)
    private val parentMotion = NativeDockParentMotion()
    val parentTranslation: SpringState get() = parentMotion.translation
    var dropping by mutableStateOf(false)
    private val dragDeformation = NativeDockDeformation()
    private var deformationInteraction = false
    private var lastDeformationTarget = initialPosition
    private var previouslyHeld = false
    private sealed interface Input {
        data class Press(val epoch: Long, val target: Float, val glowAt: Offset) : Input
        data class Drag(val epoch: Long, val target: Float, val pull: Float, val glowAt: Offset) : Input
        data class Glow(val epoch: Long, val at: Offset) : Input
        data class Release(val epoch: Long, val target: Float) : Input
        data class Cancel(val target: Float) : Input
        data class Selection(val target: Float) : Input
    }
    private val inputClock = DockInputClock<Input> { it is Input.Press }
    private var nextEpoch = 0L
    private var requestedEpoch: Long? = null
    private var activeEpoch: Long? = null
    private var inputHeld = false
    private var inputDragging = false
    var requestedPosition by mutableFloatStateOf(initialPosition)
        private set
    var glowAt by mutableStateOf(Offset.Zero)
        private set
    val appliedPressEpoch: Long? get() = activeEpoch

    fun press(target: Float, receivedNanos: Long, sampleUptimeMillis: Long, glowAt: Offset = Offset.Zero,
        fingerXDp: Float? = null): Long {
        val epoch = ++nextEpoch
        requestedEpoch = epoch
        requestedPosition = target
        inputClock.enqueue(Input.Press(epoch, target, glowAt), receivedNanos, sampleUptimeMillis)
        parentMotion.press(epoch, fingerXDp, receivedNanos)
        return epoch
    }

    fun drag(epoch: Long, target: Float, pull: Float, receivedNanos: Long, sampleUptimeMillis: Long,
        glowAt: Offset = Offset.Zero, fingerXDp: Float? = null) {
        if (requestedEpoch != epoch) return
        requestedPosition = target
        inputClock.enqueue(Input.Drag(epoch, target, pull, glowAt), receivedNanos, sampleUptimeMillis)
        parentMotion.move(epoch, fingerXDp, receivedNanos)
    }

    fun trackGlow(epoch: Long, at: Offset, receivedNanos: Long, sampleUptimeMillis: Long,
        fingerXDp: Float? = null) {
        if (requestedEpoch != epoch) return
        inputClock.enqueue(Input.Glow(epoch, at), receivedNanos, sampleUptimeMillis)
        parentMotion.move(epoch, fingerXDp, receivedNanos)
    }

    fun release(epoch: Long, target: Float, receivedNanos: Long, sampleUptimeMillis: Long) {
        if (requestedEpoch != epoch) return
        requestedEpoch = null
        requestedPosition = target
        inputClock.enqueue(Input.Release(epoch, target), receivedNanos, sampleUptimeMillis)
        parentMotion.release(epoch, receivedNanos)
    }

    fun cancel(target: Float, receivedNanos: Long, sampleUptimeMillis: Long? = null) {
        requestedEpoch = null
        requestedPosition = target
        inputClock.enqueue(Input.Cancel(target), receivedNanos, sampleUptimeMillis)
        parentMotion.cancel(receivedNanos)
    }

    fun select(target: Float, receivedNanos: Long) {
        requestedEpoch = null
        requestedPosition = target
        inputClock.enqueue(Input.Selection(target), receivedNanos)
        parentMotion.cancel(receivedNanos)
    }

    fun needsFrame(scaleFactor: Float): Boolean =
        inputClock.hasPending || parentMotion.needsFrame() || needsFrame(inputDragging, scaleFactor)

    fun advanceFrame(frame: ActiveFrame, scaleFactor: Float) {
        parentMotion.advanceFrame(frame, scaleFactor)
        inputClock.advance(frame,
            integrate = { advance(it, scaleFactor, inputHeld, inputDragging) },
            apply = { applyInput(it, scaleFactor) },
        )
    }

    private fun applyInput(input: Input, scaleFactor: Float) {
        when (input) {
            is Input.Press -> {
                activeEpoch = input.epoch
                inputHeld = true
                inputDragging = false
                dropping = false
                // The clock already established this epoch's common origin.
                previouslyHeld = true
                dragDeformation.beginDirect()
                glowAt = input.glowAt
                position.springTo(input.target, FlClashSprings.TravelSpring)
                lift.springTo(1f, FlClashSprings.LiftSpring)
                swell.springTo(1f, FlClashSprings.PressSpring)
                glowSpread.jumpTo(0f)
                glow.springTo(1f, FlClashSprings.GlowInSpring)
            }
            is Input.Drag -> {
                if (activeEpoch != input.epoch) return
                inputDragging = true
                glowAt = input.glowAt
                pull.springTo(input.pull, FlClashSprings.TrackSpring)
                position.springTo(input.target, NativeDockPosition.FollowSpring)
            }
            is Input.Glow -> {
                if (activeEpoch != input.epoch) return
                glowAt = input.at
            }
            is Input.Release -> {
                if (activeEpoch != input.epoch) return
                activeEpoch = null
                inputHeld = false
                inputDragging = false
                dropping = true
                position.springTo(input.target, FlClashSprings.TravelSpring)
                lowerAccessories()
            }
            is Input.Cancel -> cancelPhysicalInput(input.target)
            is Input.Selection -> cancelPhysicalInput(input.target)
        }
        // Register zero-duration DOWN/UP and mode switches before any next input.
        // This also settles each complete transaction when animations are off.
        advance(0.0, scaleFactor, inputHeld, inputDragging)
    }

    private fun lowerAccessories() {
        pull.springTo(0f, FlClashSprings.PressSpring)
        glow.springTo(0f, FlClashSprings.GlowOutSpring)
        glowSpread.springTo(1f, FlClashSprings.GlowOutSpring)
        swell.springTo(0f, FlClashSprings.PressSpring)
    }

    private fun cancelPhysicalInput(target: Float) {
        activeEpoch = null
        inputHeld = false
        inputDragging = false
        dropping = false
        position.springTo(target, FlClashSprings.TravelSpring)
        lift.springTo(0f, FlClashSprings.LiftSpring)
        lowerAccessories()
    }

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
            pull.isRunning || jelly.isRunning || glow.isRunning || glowSpread.isRunning || parentTranslation.isRunning

    fun needsFrame(dragging: Boolean, scaleFactor: Float): Boolean =
        isRunning || dragging && scaleFactor > 0f

    /** Delta-only spring probe; runtime inputs use [advanceFrame] and explicit epochs. */
    fun advance(dt: Double, scaleFactor: Float, held: Boolean, dragging: Boolean) {
        if (scaleFactor <= 0f) {
            finish(held)
            return
        }
        val newDirectPress = held && !previouslyHeld && !dragging
        // A warm frame's delta starts before this press. Establish the command's
        // origin without spending that time on any spring or deformation axis.
        // Recording held even for dt = 0 keeps the next frame advancing normally.
        val scaledDelta = if (newDirectPress) 0.0 else dt / scaleFactor
        if (newDirectPress) dragDeformation.beginDirect()
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
