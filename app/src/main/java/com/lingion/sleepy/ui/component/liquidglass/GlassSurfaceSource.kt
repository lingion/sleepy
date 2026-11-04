package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Register only a container's explicitly declared ordinary backing, never its child controls.
 * This records the same color/shape painted here, not a screenshot or a reconstructed child tree.
 * Overlapping backings must receive ordered depths from the owning layout.
 */
@Composable
fun Modifier.glassSurfaceBackground(color: Color, shape: Shape, depth: Float = -1f): Modifier {
    require(depth.isFinite()) { "Source depth must be finite" }
    val host = LocalGlassHost.current
    val layer = rememberGraphicsLayer()
    val segment = remember(host, layer, depth) { GlassSourceSegment(depth, layer) }
    DisposableEffect(host, segment) {
        host?.sources?.add(segment)
        host?.changed()
        onDispose { host?.sources?.remove(segment); host?.changed() }
    }
    return this.onGloballyPositioned { segment.coordinates = it }.drawWithContent {
        fun androidx.compose.ui.graphics.drawscope.DrawScope.paintBacking() {
            when (val outline = shape.createOutline(size, layoutDirection, this)) {
                is Outline.Rectangle -> drawRect(color)
                is Outline.Rounded -> drawPath(Path().apply { addRoundRect(outline.roundRect) }, color)
                is Outline.Generic -> drawPath(outline.path, color)
            }
        }
        if (host != null) {
            recordGlassLayer(layer) { paintBacking() }
            drawLayer(layer)
            host.changed()
        } else {
            paintBacking()
        }
        drawContent()
    }
}
