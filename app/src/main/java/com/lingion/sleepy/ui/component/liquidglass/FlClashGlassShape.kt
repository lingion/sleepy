package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Reference AppRadius/RSuperellipse for menu and alert carriers, not an ordinary rounded box. */
internal data class FlClashGlassShape(val radiusDp: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = (radiusDp * density.density).coerceIn(0f, size.minDimension / 2f)
        val path = android.graphics.Path()
        LiquidGlassSuperellipse.buildPath(
            LiquidGlassSuperellipse.Rect2(0f, 0f, size.width, size.height),
            LiquidGlassSuperellipse.Radii.makeRadius(radius),
        ).toAndroidPath(path)
        return Outline.Generic(path.asComposePath())
    }
}
