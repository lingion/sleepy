package com.lingion.sleepy.ui.glass.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.glass.insetShapeFor
import kotlin.math.sqrt

internal fun DrawScope.drawBottomLitGlassTint(
    color: Color,
    topTintFloor: Float = 0.20f,
    expanded: Boolean = false
) {
    drawRect(bottomLitGlassTintBrush(color, size.height, topTintFloor, expanded))
}

internal fun bottomLitGlassTintBrush(
    color: Color, height: Float, topTintFloor: Float = 0.20f,
    expanded: Boolean = false,
    colorTransform: (Color) -> Color = { it }
): Brush {
    val top = topTintFloor.coerceIn(0f, 0.72f)
    fun tint(weight: Float) = colorTransform(color.copy(alpha = color.alpha * weight))
    val firstLiftY = if (expanded) 0.68f else 0.82f
    val brightBandY = if (expanded) 0.82f else 0.90f
    val rimY = if (expanded) 0.92f else 0.96f
    return Brush.verticalGradient(
            colorStops = arrayOf(
                0f to tint(top),
                (if (expanded) 0.48f else 0.62f) to tint(top),
                firstLiftY to tint(maxOf(top, 0.48f)),
                brightBandY to tint(maxOf(top, 0.84f)),
                rimY to tint(maxOf(top, 0.88f)),
                1f to colorTransform(color)
            ),
            endY = height
        )
}

internal fun Modifier.verticalGlassAccent(
    accentColor: Color,
    shape: Shape,
    lightGlass: Boolean,
    intensity: Float = 1f,
    expanded: Boolean = false,
    morphAllocation: com.lingion.sleepy.ui.glass.GlassMorphAllocation? = null,
    surroundingEdgeGlow: Boolean = false
): Modifier {
    val bounds = morphAllocation?.localBounds()
    if (com.lingion.sleepy.ui.glass.performance.AppMaterialPreferences.isSuperPerformance) return this
    val clipShape = morphAllocation?.let { it.envelope.insetShapeFor(it.geometry()) } ?: shape
    val lightWallpaper = surroundingEdgeGlow && lightGlass
    val edgeColor = accentColor
    val whiteStrength = if (lightWallpaper) 0.45f else 1f
    return this
            // The stationary course accent must follow the same draw-time outline as its
            // sampled material. A retained clip layer can keep the last tail translation.
            .then(if (morphAllocation == null) Modifier else Modifier.clip(clipShape))
            .drawWithCache {
                // Sliding changes the sampled wallpaper, not these local gradients. Retain
                // their brushes/shaders until geometry or personalization actually changes.
                val brushes = verticalGlassAccentBrushes(
                    bounds?.height ?: size.height, edgeColor, lightGlass, intensity, expanded, lightWallpaper
                )
                val edgeStrength = sqrt(intensity.coerceIn(0f, 1f)) * if (lightWallpaper) 1.35f else 1f
                val edgeMesh = if (surroundingEdgeGlow) courseEdgeGlowMesh(
                    bounds ?: Rect(Offset.Zero, size), 20.dp.toPx(), edgeColor, edgeStrength
                ) else null
                // Kyant's HighlightStyle uses Plus to add light. Feed it the course RGB from
                // the cached mesh in both views/styles; white here is only a neutral multiplier.
                val edgePaint = Paint().apply {
                    color = Color.White
                    blendMode = BlendMode.Plus
                }
                val accentPath = if (morphAllocation == null) Path().apply {
                    addOutline(clipShape.createOutline(size, layoutDirection, this@drawWithCache))
                } else null
                onDrawBehind {
                    val drawAccent: DrawScope.() -> Unit = {
                        if (bounds == null) {
                            drawVerticalGlassAccent(brushes, BlendMode.Plus, shadeTop = !surroundingEdgeGlow)
                        } else {
                            inset(bounds.left, bounds.top, size.width - bounds.right, size.height - bounds.bottom) {
                                drawVerticalGlassAccent(brushes, BlendMode.Plus, shadeTop = !surroundingEdgeGlow)
                            }
                        }
                        // Vertex interpolation gives a continuous soft falloff in one cached draw;
                        // no per-card blur texture, runtime shader or stacked hard strokes.
                        if (edgeMesh != null) drawContext.canvas.drawVertices(edgeMesh, BlendMode.Modulate, edgePaint)
                    }
                    if (accentPath == null) drawAccent() else clipPath(accentPath) { drawAccent() }
                }
            }
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = (if (lightGlass) 0.28f else 0.16f) * whiteStrength),
                        0.28f to Color.Transparent,
                        (if (expanded) 0.80f else 0.94f) to Color.Transparent,
                        1f to Color.White.copy(
                            alpha = 0.20f * sqrt(intensity.coerceIn(0f, 1f)) * whiteStrength
                        )
                    ),
                    startY = bounds?.top ?: 0f,
                    endY = bounds?.bottom ?: Float.POSITIVE_INFINITY
                ),
                shape = clipShape
            )
}

@Composable
internal fun VerticalGlassAccentOverlay(
    accentColor: Color,
    shape: Shape,
    lightGlass: Boolean,
    intensity: Float = 1f,
    expanded: Boolean = false,
    modifier: Modifier = Modifier,
    morphAllocation: com.lingion.sleepy.ui.glass.GlassMorphAllocation? = null,
    surroundingEdgeGlow: Boolean = false
) {
    Box(
        modifier.verticalGlassAccent(
            accentColor = accentColor,
            shape = shape,
            lightGlass = lightGlass,
            intensity = intensity,
            expanded = expanded,
            morphAllocation = morphAllocation,
            surroundingEdgeGlow = surroundingEdgeGlow
        )
    )
}

private fun verticalGlassAccentBrushes(
    height: Float,
    accentColor: Color,
    lightGlass: Boolean,
    intensity: Float,
    expanded: Boolean,
    lightWallpaper: Boolean
): List<Brush> {
    // Perceptual response keeps the middle of the slider useful without allowing the
    // sampled wallpaper brightness to dictate the light-source strength.
    val lightStrength = sqrt(intensity.coerceIn(0f, 1f))
    val colorStrength = lightStrength * if (lightWallpaper) 1.25f else 1f
    val whiteStrength = lightStrength * if (lightWallpaper) 0.45f else 1f
    val colorStartY = if (expanded) 0.66f else 0.86f
    val colorLiftY = if (expanded) 0.80f else 0.92f
    val colorRimY = if (expanded) 0.92f else 0.97f
    val whiteStartY = if (expanded) 0.72f else 0.89f
    val whiteLiftY = if (expanded) 0.88f else 0.96f
    return listOf(
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.Black.copy(alpha = if (lightGlass) 0.014f else 0.022f),
                0.18f to Color.Black.copy(alpha = if (lightGlass) 0.010f else 0.016f),
                0.32f to Color.Black.copy(alpha = if (lightGlass) 0.006f else 0.009f),
                0.46f to Color.Transparent,
                1f to Color.Transparent
            ),
            endY = height
        ),
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                colorStartY to Color.Transparent,
                colorLiftY to accentColor.copy(alpha = 0.040f * colorStrength),
                colorRimY to accentColor.copy(
                    alpha = (if (lightGlass) 0.18f else 0.21f) * colorStrength
                ),
                1f to accentColor.copy(
                    alpha = (if (lightGlass) 0.30f else 0.34f) * colorStrength
                )
            ),
            endY = height
        ),
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                whiteStartY to Color.Transparent,
                whiteLiftY to Color.White.copy(alpha = 0.042f * whiteStrength),
                1f to Color.White.copy(alpha = 0.082f * whiteStrength)
            ),
            endY = height
        )
    )
}

private fun DrawScope.drawVerticalGlassAccent(brushes: List<Brush>, colorBlend: BlendMode, shadeTop: Boolean) {
    if (shadeTop) drawRect(brushes[0])
    drawRect(brushes[1], blendMode = colorBlend)
    drawRect(brushes[2])
}
