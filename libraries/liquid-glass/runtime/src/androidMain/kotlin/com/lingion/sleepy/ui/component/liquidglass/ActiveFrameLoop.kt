package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

suspend fun runActiveFrameLoop(
    needsFrame: () -> Boolean,
    onFrame: (Double) -> Unit,
) = runTimestampedActiveFrameLoop(needsFrame) { frame ->
    val previous = frame.previousFrameTimeNanos
    // Preserve the existing delta-only contract for the other controls.
    onFrame(if (previous == null) 0.0
        else ((frame.frameTimeNanos - previous) / 1_000_000_000.0).coerceIn(0.001, 0.05))
}

data class ActiveFrame(
    val frameTimeNanos: Long,
    val previousFrameTimeNanos: Long?,
)

suspend fun runTimestampedActiveFrameLoop(
    needsFrame: () -> Boolean,
    onFrame: (ActiveFrame) -> Unit,
) {
    var lastFrameNanos: Long? = null
    while (currentCoroutineContext().isActive) {
        if (!needsFrame()) {
            lastFrameNanos = null
            snapshotFlow(needsFrame).first { it }
        }
        withFrameNanos { frameTimeNanos ->
            val frame = ActiveFrame(frameTimeNanos, lastFrameNanos)
            lastFrameNanos = frameTimeNanos
            onFrame(frame)
        }
    }
}
