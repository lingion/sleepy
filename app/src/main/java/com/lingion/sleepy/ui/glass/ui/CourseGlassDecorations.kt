package com.lingion.sleepy.ui.glass.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** White Screen is equivalent to white SrcOver; flatten the neutral fills into the tint. */
internal fun courseGlassSurfaceColor(tint: Color, lightGlass: Boolean): Color =
    Color.Black.copy(alpha = if (lightGlass) 0.004f else 0.014f).compositeOver(
        Color.White.copy(alpha = if (lightGlass) 0.012f else 0.008f).compositeOver(tint)
    )

/** Compose caches the shadow masks; these decorations do not own card-sized GraphicsLayers. */
internal fun Modifier.courseGlassOuterShadow(
    shape: Shape,
    alpha: Float,
    enabled: () -> Boolean
): Modifier = drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    onDrawWithContent {
        // Kyant's outer shadow excludes the card interior, including translucent surfaces.
        if (enabled()) clipPath(path, ClipOp.Difference) { this@onDrawWithContent.drawContent() }
    }
}.dropShadow(shape, Shadow(
    radius = 24.dp,
    offset = DpOffset(0.dp, 4.dp),
    color = Color.Black.copy(alpha = 0.1f),
    alpha = alpha
))

internal fun Modifier.courseGlassInnerShadow(
    shape: Shape,
    radius: Dp,
    alpha: Float,
    enabled: () -> Boolean
): Modifier = drawWithCache {
    onDrawWithContent { if (enabled()) this@onDrawWithContent.drawContent() }
}.innerShadow(shape, Shadow(
    radius = radius,
    offset = DpOffset(0.dp, radius),
    color = Color.Black.copy(alpha = 0.15f),
    alpha = alpha
))
