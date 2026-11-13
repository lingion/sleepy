package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Reference AppRadius/RSuperellipse for menu and alert carriers, not an ordinary rounded box. */
internal data class FlClashGlassShape(val radiusDp: Float) : Shape, LiquidGlassExactContourShape {
    private val contours = LiquidGlassContourCache()

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = (radiusDp * density.density).coerceIn(0f, size.minDimension / 2f)
        return contours.obtain(
            LiquidGlassSuperellipse.Rect2(0f, 0f, size.width, size.height),
            LiquidGlassSuperellipse.Radii.makeRadius(radius),
        ).outline
    }

    override fun exactContoursFor(outline: Outline): List<LiquidGlassExactContour>? = contours.exactContoursFor(outline)
}
