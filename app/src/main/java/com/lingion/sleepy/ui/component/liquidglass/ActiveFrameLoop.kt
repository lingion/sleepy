package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

internal suspend fun runActiveFrameLoop(
    needsFrame: () -> Boolean,
    onFrame: (Double) -> Unit,
) {
    var lastFrameNanos = 0L
    while (currentCoroutineContext().isActive) {
        if (!needsFrame()) {
            lastFrameNanos = 0L
            snapshotFlow(needsFrame).first { it }
        }
        withFrameNanos { frameTimeNanos ->
            // The first delivered frame establishes this animation's origin.
            // Advancing a fabricated 16 ms here makes every cold interaction
            // start partway through its spring, including on 90/120 Hz displays.
            val delta = if (lastFrameNanos == 0L) 0.0
                else ((frameTimeNanos - lastFrameNanos) / 1_000_000_000.0).coerceIn(0.001, 0.05)
            lastFrameNanos = frameTimeNanos
            onFrame(delta)
        }
    }
}
