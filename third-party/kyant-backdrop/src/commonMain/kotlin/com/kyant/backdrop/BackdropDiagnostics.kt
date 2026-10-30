package com.kyant.backdrop

import androidx.compose.ui.unit.IntSize

/** Optional diagnostic sink. Recorded pixel area is NOT a measurement of GPU allocation. */
object BackdropDiagnostics {
    var observer: ((String, Long) -> Unit)? = null
    internal fun event(name: String, value: Long = 1L) { observer?.invoke(name, value) }
}

internal class BackdropLayerDiagnostics(private val kind: String) {
    private var previousSize = IntSize.Zero
    fun created() = BackdropDiagnostics.event("$kind.Created")
    fun released() {
        BackdropDiagnostics.event("$kind.Released")
        previousSize = IntSize.Zero
    }
    fun recorded(size: IntSize) {
        BackdropDiagnostics.event("$kind.Recorded")
        if (size != previousSize) {
            BackdropDiagnostics.event("$kind.SizeChanged")
            previousSize = size
        }
        BackdropDiagnostics.event("$kind.RecordedPixelArea", size.width.toLong() * size.height)
    }
}
