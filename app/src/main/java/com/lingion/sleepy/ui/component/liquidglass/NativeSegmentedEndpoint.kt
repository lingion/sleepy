package com.lingion.sleepy.ui.component.liquidglass

import kotlin.math.abs

/**
 * Empirical lens target from retained native slow endpoint drags. The fixed
 * 12 dp limit matches held and late moving centers, not the whole transient
 * response or UIKit's internal algorithm. Selection remains bounded separately.
 */
internal fun nativeSegmentedDragPosition(rawLogicalIndex: Float, count: Int, pitchDp: Float): Float {
    val bounded = rawLogicalIndex.coerceIn(0f, (count - 1).coerceAtLeast(0).toFloat())
    val pitch = abs(pitchDp)
    if (count < 2 || pitch <= 0f || !pitch.isFinite() || rawLogicalIndex == bounded) return bounded
    val overshootDp = (rawLogicalIndex - bounded) * pitch
    // Reuse only the shared 0.55 rubber-band equation; this is a measured
    // segmented-control candidate, independent of the Dock's pull geometry.
    return bounded + FlClashDockPhysics.rubberBand(overshootDp, 12f) / pitch
}
