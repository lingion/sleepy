package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toIntSize

/**
 * A retained LayoutNodeDrawScope delegates density to its mutable CanvasDrawScope. Passing that
 * scope itself as the recording density can point the delegate back to itself during nested
 * popup recording. Snapshot primitive density/fontScale before entering any new layer.
 * Same isolation contract as vendored backdrop/internal/LayerRecorder.kt.
 */
internal fun DrawScope.recordGlassLayer(layer: GraphicsLayer, block: DrawScope.() -> Unit) {
    val recordingDensity = Density(density, fontScale)
    val dimensions = size.toIntSize()
    if (dimensions.width <= 0 || dimensions.height <= 0) return
    // Use DrawScope's record extension, not GraphicsLayer's member: the extension retargets
    // this content draw scope to the recording canvas and preserves its current draw node.
    layer.record(dimensions) {
        val oldDensity = drawContext.density
        drawContext.density = recordingDensity
        try { block() } finally { drawContext.density = oldDensity }
    }
}
