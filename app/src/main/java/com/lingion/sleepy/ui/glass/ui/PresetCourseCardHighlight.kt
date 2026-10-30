package com.lingion.sleepy.ui.glass.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/** Fixed vector highlight: no backdrop, RuntimeShader, blur or additional graphics layer.
 * Cache ownership follows the card; position/background changes are not cache inputs.
 */
internal fun Modifier.presetCourseCardHighlight(
    shape: Shape,
    alpha: Float,
    enabled: () -> Boolean,
    bounds: () -> Rect? = { null },
    strokeWidth: Dp = 0.5.dp
): Modifier = drawWithCache {
    val rect = bounds() ?: Rect(Offset.Zero, size)
    val path = Path().apply {
        addOutline(shape.createOutline(rect.size, layoutDirection, this@drawWithCache))
        translate(rect.topLeft)
    }
    // Fixed opposing glints follow card-local geometry, never wallpaper luminance or position.
    val brush = Brush.linearGradient(
        0f to Color.White.copy(alpha = alpha),
        0.38f to Color.White.copy(alpha = alpha * 0.24f),
        0.62f to Color.White.copy(alpha = alpha * 0.24f),
        1f to Color.White.copy(alpha = alpha),
        start = rect.topLeft,
        end = rect.bottomRight
    )
    val stroke = Stroke(ceil(strokeWidth.toPx().coerceAtMost(rect.size.minDimension / 2f)) * 2f)
    onDrawWithContent {
        drawContent()
        if (enabled() && rect.width > 0f && rect.height > 0f) {
            clipPath(path) {
                drawPath(path, brush, style = stroke, blendMode = BlendMode.SrcOver)
            }
        }
    }
}
