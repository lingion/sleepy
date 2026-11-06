package com.kyant.backdrop.internal

import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

/** Tracks a completed recording, never a scheduled or transparent draw. Owns no GPU resource. */
internal class BackdropRecordingCache {
    private var key: Any? = null
    private var size = IntSize.Zero
    private var density = Float.NaN
    private var fontScale = Float.NaN
    private var direction: LayoutDirection? = null

    fun needsRecord(key: Any?, size: IntSize, density: Float, fontScale: Float, direction: LayoutDirection): Boolean =
        key == null || this.key != key || this.size != size || this.density != density ||
            this.fontScale != fontScale || this.direction != direction

    fun recorded(key: Any?, size: IntSize, density: Float, fontScale: Float, direction: LayoutDirection) {
        this.key = key
        this.size = size
        this.density = density
        this.fontScale = fontScale
        this.direction = direction
    }

    fun clear() {
        key = null
    }
}
