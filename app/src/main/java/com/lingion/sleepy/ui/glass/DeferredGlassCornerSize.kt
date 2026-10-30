package com.lingion.sleepy.ui.glass

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle

/** Read motion in the outline/effect phase, keeping lens and clipping radii in sync. */
internal class DeferredGlassCornerSize(
    private val sourceDensity: Float,
    private val radiusPx: () -> Float
) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float =
        radiusPx().coerceAtLeast(0f) * density.density / sourceDensity
}

internal class DeferredGlassRoundedRectangle(
    private val radiusPx: () -> Float,
    private val sourceDensity: Float,
    topStart: CornerSize = DeferredGlassCornerSize(sourceDensity, radiusPx),
    topEnd: CornerSize = topStart,
    bottomEnd: CornerSize = topStart,
    bottomStart: CornerSize = topStart
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {
    override fun createOutline(
        size: Size, topStart: Float, topEnd: Float, bottomEnd: Float, bottomStart: Float,
        layoutDirection: LayoutDirection
    ): Outline = RoundedRectangle(topStart.dp).createOutline(size, layoutDirection, Density(1f))

    override fun copy(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize): CornerBasedShape =
        DeferredGlassRoundedRectangle(radiusPx, sourceDensity, topStart, topEnd, bottomEnd, bottomStart)
}
