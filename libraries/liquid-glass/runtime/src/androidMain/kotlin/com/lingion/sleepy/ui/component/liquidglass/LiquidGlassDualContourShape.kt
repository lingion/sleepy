package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Two convex optical contours, each in this node's local pixels. Their union may
 * disconnect during a morph, so it cannot share one center/radial profile.
 * [Shape.createOutline] remains responsible for the exact combined CPU clip.
 */
interface LiquidGlassDualContourShape : Shape {
    fun createOpticalContours(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Pair<Path, Path>
}
