package com.kyant.backdrop

import androidx.compose.ui.geometry.Rect

/** SleepDown extension. Pausing retains node ownership without recording material layers. */
class BackdropRenderOptions(
    val enabled: () -> Boolean = { true },
    val sampleBackdrop: Boolean = true,
    val bounds: () -> Rect? = { null },
    val allocationPadding: Float? = null,
    /** Non-null only when the caller can describe ALL effect inputs with an immutable value. */
    val effectKey: () -> Any? = { null },
    val cacheDecorations: Boolean = false,
    /** A moving course lens uses its placement outline without a second draw-time clip. */
    val clipGenericOutlineInDraw: Boolean = true,
    /** Sample-only course nodes clip in draw without retaining an extra placement layer. */
    val placementLayer: Boolean = true,
    /** Sampling resolution only; layout, clipping and decorations remain at full resolution. */
    val sampleScale: Float = 1f,
    /** A retained scene may suppress position-only invalidation while its outer layer moves. */
    val coordinatesFrozen: () -> Boolean = { false },
    /** Complete scene identity; only a frozen, matching scene may reuse a sampled recording. */
    val sampleRecordKey: () -> Any? = { null },
    /** Skip background recording for fully clipped course cards; never changes node lifetime. */
    val viewport: BackdropViewport? = null
) {
    companion object {
        val Default = BackdropRenderOptions()
    }
}
