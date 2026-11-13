package com.lingion.sleepy.ui.component.liquidglass

import kotlin.math.abs

/**
 * The measured four-tab Dock settles at a local lens overdrive of 4 dp.
 * Bound only its spring target: the spring output retains its transient.
 * Parent pull/scaling is separate, and unmeasured tab counts keep their policy.
 */
internal fun nativeDockDragPosition(rawLogicalIndex: Float, count: Int, pitchDp: Float): Float {
    if (count <= 1) return 0f
    val lastIndex = (count - 1).toFloat()
    if (count == 4) {
        val pitch = abs(pitchDp)
        if (pitch <= 0f || !pitch.isFinite()) return rawLogicalIndex.coerceIn(0f, lastIndex)
        val endpointOverdrive = 4f / pitch
        return rawLogicalIndex.coerceIn(-endpointOverdrive, lastIndex + endpointOverdrive)
    }
    return when {
        rawLogicalIndex < 0f -> FlClashDockPhysics.rubberBand(rawLogicalIndex, FlClashDockPhysics.OVERDRAG)
        rawLogicalIndex > lastIndex -> lastIndex + FlClashDockPhysics.rubberBand(rawLogicalIndex - lastIndex, FlClashDockPhysics.OVERDRAG)
        else -> rawLogicalIndex
    }
}
