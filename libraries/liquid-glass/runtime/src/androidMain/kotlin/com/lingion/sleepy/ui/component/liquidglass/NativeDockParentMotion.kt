package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Separate four-tab parent translation in physical dp. Finger coordinates are
 * stable window X / density, never a logical seat or a clamped lens target.
 *
 * The frozen native65 candidate covers held gestures on a 333 x 62pt parent.
 * Other Android four-tab widths explicitly use this same physical-dp law.
 * Return and interrupted-DOWN continuity are Android lifecycle policies.
 */
internal class NativeDockParentMotion {
    // Preserve tiny candidate responses; this is a numerical stop threshold,
    // not a native-parity tolerance. The legacy seven springs keep their rest.
    val translation = SpringState(0f, rest = 1e-9)

    private sealed interface Input {
        data class Press(val epoch: Long, val fingerXDp: Float?) : Input
        data class Move(val epoch: Long, val fingerXDp: Float?) : Input
        data class Release(val epoch: Long) : Input
        data object Cancel : Input
    }

    private data class Pending(val input: Input, val receivedNanos: Long)
    private val pending = ArrayDeque<Pending>()
    private var pendingCount by mutableIntStateOf(0)
    private var requestedEpoch: Long? = null
    private var activeEpoch: Long? = null
    private var downXDp: Float? = null

    fun press(epoch: Long, fingerXDp: Float?, receivedNanos: Long) {
        requestedEpoch = epoch
        enqueue(Input.Press(epoch, fingerXDp?.takeIf { it.isFinite() }), receivedNanos)
    }

    fun move(epoch: Long, fingerXDp: Float?, receivedNanos: Long) {
        if (requestedEpoch != epoch) return
        enqueue(Input.Move(epoch, fingerXDp?.takeIf { it.isFinite() }), receivedNanos)
    }

    fun release(epoch: Long, receivedNanos: Long) {
        if (requestedEpoch != epoch) return
        requestedEpoch = null
        enqueue(Input.Release(epoch), receivedNanos)
    }

    fun cancel(receivedNanos: Long) {
        requestedEpoch = null
        enqueue(Input.Cancel, receivedNanos)
    }

    fun needsFrame(): Boolean = pendingCount > 0 || translation.isRunning

    private fun enqueue(input: Input, receivedNanos: Long) {
        pending.addLast(Pending(input, receivedNanos))
        pendingCount = pending.size
    }

    fun advanceFrame(frame: ActiveFrame, scaleFactor: Float) {
        val end = frame.frameTimeNanos
        // This state has its own receipt origin. The seven original Dock springs
        // still establish Press at frame end through their unchanged input clock.
        // A cold wake starts at the first eligible receipt, never at idle time.
        var cursor = frame.previousFrameTimeNanos?.coerceAtMost(end)
            ?: pending.firstOrNull()?.receivedNanos?.coerceAtMost(end) ?: end
        while (pending.isNotEmpty() && pending.first().receivedNanos <= end) {
            val command = pending.removeFirst()
            pendingCount = pending.size
            val at = command.receivedNanos.coerceIn(cursor, end)
            advance((at - cursor) / 1_000_000_000.0, scaleFactor)
            cursor = at
            apply(command.input)
            advance(0.0, scaleFactor)
        }
        advance((end - cursor) / 1_000_000_000.0, scaleFactor)
    }

    private fun apply(input: Input) {
        when (input) {
            is Input.Press -> {
                activeEpoch = input.epoch
                downXDp = input.fingerXDp
                // Keep current x/v when a press interrupts motion, but establish
                // a fresh physical anchor and zero-displacement target.
                translation.springTo(0f, FollowSpring)
            }
            is Input.Move -> {
                if (activeEpoch != input.epoch) return
                val down = downXDp ?: return
                val finger = input.fingerXDp ?: return
                translation.springTo((GAIN * (finger - down)).toFloat(), FollowSpring)
            }
            is Input.Release -> {
                if (activeEpoch != input.epoch) return
                endInteraction()
            }
            Input.Cancel -> endInteraction()
        }
    }

    private fun endInteraction() {
        activeEpoch = null
        downXDp = null
        translation.springTo(0f, FlClashSprings.PressSpring)
    }

    private fun advance(seconds: Double, scaleFactor: Float) {
        if (scaleFactor <= 0f) translation.jumpTo(translation.target)
        else translation.tickDelta(seconds / scaleFactor)
    }

    private companion object {
        const val GAIN = .010287932530511721
        val FollowSpring = SpringDescription.withDampingRatio(
            mass = 1.0,
            stiffness = 90.0275176686287,
            ratio = .6099720458279732,
        )
    }
}
