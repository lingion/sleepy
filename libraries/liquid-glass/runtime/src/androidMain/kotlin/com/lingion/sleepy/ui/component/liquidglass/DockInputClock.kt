package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Tests provide the same virtual time domain as their MonotonicFrameClock. */
val LocalDockInputTimeNanos = staticCompositionLocalOf<() -> Long> { System::nanoTime }

/** Orders Dock input against actual frame timestamps, including late callbacks. */
class DockInputClock<Command>(private val establishesOrigin: (Command) -> Boolean) {
    private data class Pending<Command>(
        val command: Command,
        val receivedNanos: Long,
        // A delivered event can contain an older hardware sample. Keep both;
        // only receipt can gate when the app first knew the command.
        val sampleUptimeMillis: Long?,
    )

    private val pending = ArrayDeque<Pending<Command>>()
    private var pendingCount by mutableIntStateOf(0)
    val hasPending: Boolean get() = pendingCount > 0

    fun enqueue(command: Command, receivedNanos: Long, sampleUptimeMillis: Long? = null) {
        pending.addLast(Pending(command, receivedNanos, sampleUptimeMillis))
        pendingCount = pending.size
    }

    fun advance(frame: ActiveFrame, integrate: (Double) -> Unit, apply: (Command) -> Unit) {
        val end = frame.frameTimeNanos
        // A wake establishes a new baseline; time spent asleep is never caught up.
        var cursor = frame.previousFrameTimeNanos?.coerceAtMost(end) ?: end
        while (pending.isNotEmpty() && pending.first().receivedNanos <= end) {
            val input = pending.removeFirst()
            pendingCount = pending.size
            // A press takes effect atomically at its first eligible frame.
            // Other events split the live trajectory at their actual receipt.
            // Events after a same-frame press stay ordered at that same origin.
            val at = if (establishesOrigin(input.command)) end
                else input.receivedNanos.coerceIn(cursor, end)
            if (at > cursor) integrate((at - cursor) / 1_000_000_000.0)
            cursor = at
            apply(input.command)
        }
        if (end > cursor) integrate((end - cursor) / 1_000_000_000.0)
        else integrate(0.0)
    }
}
