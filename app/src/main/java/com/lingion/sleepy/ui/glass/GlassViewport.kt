package com.lingion.sleepy.ui.glass

import androidx.compose.ui.geometry.Rect
import kotlin.math.max

/** Result of one viewport check. Layout/content remain composed when [mountMaterial] is false. */
internal data class CourseGlassViewportMaterialDecision(
    val mountMaterial: Boolean,
    val distanceOutsidePx: Float
)

/**
 * Resolution-aware look-ahead band. It is deliberately measured in physical pixels: a larger
 * window gives an approaching card more travel time to build its GPU material before entry,
 * while clamps keep phones and high-density tablets within a bounded amount of hidden work.
 */
internal fun adaptiveCourseGlassPrewarmDistancePx(
    viewport: Rect,
    density: Float
): Float {
    val safeDensity = density.coerceAtLeast(0.5f)
    val shortEdge = minOf(viewport.width, viewport.height).coerceAtLeast(0f)
    return (shortEdge * 0.12f).coerceIn(
        minimumValue = 72f * safeDensity,
        maximumValue = 160f * safeDensity
    )
}

/**
 * Removes only the expensive material node once a course card is wholly outside the actual
 * window. When an unmounted card reverses toward the window, it is mounted inside a small
 * resolution-aware look-ahead band so allocation is distributed across the swipe/scroll rather
 * than landing on the first visible frame.
 */
internal fun decideCourseGlassViewportMaterial(
    enabled: Boolean,
    currentlyMounted: Boolean,
    previousDistanceOutsidePx: Float?,
    boundsInWindow: Rect,
    viewport: Rect,
    prewarmDistancePx: Float,
    cullHorizontal: Boolean = true,
    cullVertical: Boolean = true
): CourseGlassViewportMaterialDecision {
    if (viewport.width <= 0f || viewport.height <= 0f ||
        boundsInWindow.width <= 0f || boundsInWindow.height <= 0f
    ) {
        return CourseGlassViewportMaterialDecision(
            mountMaterial = true,
            distanceOutsidePx = 0f
        )
    }

    val horizontalDistance = when {
        !cullHorizontal -> 0f
        boundsInWindow.right <= viewport.left -> viewport.left - boundsInWindow.right
        boundsInWindow.left >= viewport.right -> boundsInWindow.left - viewport.right
        else -> 0f
    }
    val verticalDistance = when {
        !cullVertical -> 0f
        boundsInWindow.bottom <= viewport.top -> viewport.top - boundsInWindow.bottom
        boundsInWindow.top >= viewport.bottom -> boundsInWindow.top - viewport.bottom
        else -> 0f
    }
    val distanceOutsidePx = max(horizontalDistance, verticalDistance).coerceAtLeast(0f)
    if (!enabled) {
        return CourseGlassViewportMaterialDecision(
            mountMaterial = true,
            distanceOutsidePx = distanceOutsidePx
        )
    }

    val intersectsWindow =
        (!cullHorizontal ||
            (boundsInWindow.left < viewport.right && boundsInWindow.right > viewport.left)) &&
            (!cullVertical ||
                (boundsInWindow.top < viewport.bottom && boundsInWindow.bottom > viewport.top))
    if (intersectsWindow) {
        return CourseGlassViewportMaterialDecision(
            mountMaterial = true,
            distanceOutsidePx = 0f
        )
    }

    val previousDistance = previousDistanceOutsidePx
    val movingTowardWindow = previousDistance != null &&
        distanceOutsidePx < previousDistance - 0.5f
    // Retain through a wider exit band. Crossing the visible edge is not a reason to
    // destroy a material which may reverse direction on the very next scroll frame.
    val alreadyPrewarmedOutside = currentlyMounted &&
        distanceOutsidePx <= prewarmDistancePx * 2f
    val enterPrewarmBand = !currentlyMounted &&
        movingTowardWindow &&
        distanceOutsidePx <= prewarmDistancePx

    return CourseGlassViewportMaterialDecision(
        mountMaterial = alreadyPrewarmedOutside || enterPrewarmBand,
        distanceOutsidePx = distanceOutsidePx
    )
}
