package com.lingion.sleepy.ui.glass.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

/** A thin light follows the real capsule, fading inward and around both lower corners. */
internal fun Modifier.statusPillContourLight(
    accentColor: Color,
    shape: Shape,
    lightGlass: Boolean
): Modifier = drawWithCache {
    if (size.width <= 0f || size.height <= 0f) return@drawWithCache onDrawBehind { }
    val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val measure = PathMeasure().apply { setPath(outline, forceClosed = true) }
    val spread = minOf(5.2.dp.toPx(), size.minDimension * 0.20f)
    val segments = 80
    val bands = 12
    val positions = ArrayList<Offset>((segments + 1) * (bands + 1))
    val colors = ArrayList<Color>((segments + 1) * (bands + 1))
    val indices = ArrayList<Int>(segments * bands * 6)
    for (segment in 0..segments) {
        val distance = measure.length * (segment % segments) / segments
        val point = measure.getPosition(distance)
        val tangent = measure.getTangent(distance)
        var inward = Offset(-tangent.y, tangent.x) / tangent.getDistance().coerceAtLeast(0.0001f)
        val toCenter = Offset(size.width / 2f, size.height / 2f) - point
        if (inward.x * toCenter.x + inward.y * toCenter.y < 0f) inward = -inward
        val lowerHalf = ((point.y / size.height.coerceAtLeast(1f) - 0.46f) / 0.54f).coerceIn(0f, 1f)
        val direction = lowerHalf * lowerHalf * (3f - 2f * lowerHalf)
        for (band in 0..bands) {
            val t = band.toFloat() / bands
            val feather = (1f - t) * (1f - t) * (1f + 2f * t)
            positions += point + inward * (spread * t)
            colors += accentColor.copy(alpha = (if (lightGlass) 0.34f else 0.38f) * direction * feather)
        }
    }
    for (segment in 0 until segments) {
        for (band in 0 until bands) {
            val a = segment * (bands + 1) + band
            val b = a + bands + 1
            indices.addAll(listOf(a, b, a + 1, a + 1, b, b + 1))
        }
    }
    val light = Vertices(VertexMode.Triangles, positions, positions, colors, indices)
    val paint = Paint().apply { color = Color.White; blendMode = BlendMode.Plus }
    val rim = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (lightGlass) 0.28f else 0.16f),
        0.26f to Color.Transparent,
        0.54f to Color.Transparent,
        0.78f to Color.White.copy(alpha = 0.045f),
        1f to Color.White.copy(alpha = 0.16f),
        endY = size.height
    )
    // The outer half is clipped, leaving a 0.65dp rim. All geometry is cached with the size;
    // the diffuse fill needs one mesh draw, without another blur texture or offscreen layer.
    val rimStroke = Stroke(1.3.dp.toPx())
    onDrawBehind {
        clipPath(outline) {
            drawContext.canvas.drawVertices(light, BlendMode.Modulate, paint)
            drawPath(outline, rim, style = rimStroke)
        }
    }
}
