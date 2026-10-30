package com.lingion.sleepy.ui.glass.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop
import kotlin.math.roundToInt

/** Blend source pixels before the consumer applies its one blur/lens chain. */
@Composable
internal fun rememberCrossfadeBackdrop(
    source: Backdrop?,
    destination: Backdrop?,
    progress: () -> Float
): Backdrop? {
    if (source == null) return destination
    if (destination == null || source === destination) return source
    val currentProgress = rememberUpdatedState(progress)
    return remember(source, destination) {
        object : Backdrop {
            override val isCoordinatesDependent = true

            override fun DrawScope.drawBackdrop(
                density: Density,
                coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?
            ) {
                val fraction = currentProgress.value().coerceIn(0f, 1f)
                if (fraction < 1f) {
                    with(source) { drawBackdrop(density, coordinates, layerBlock) }
                }
                if (fraction <= 0f) return
                if (fraction >= 1f) {
                    with(destination) { drawBackdrop(density, coordinates, layerBlock) }
                    return
                }
                // Preserve the consumer's full sampling clip, including effect padding. A
                // DrawScope-size bound here would crop a downsampled consumer a second time.
                val canvas = drawContext.canvas.nativeCanvas
                val checkpoint = canvas.saveLayerAlpha(null, (fraction * 255f).roundToInt())
                try {
                    with(destination) { drawBackdrop(density, coordinates, layerBlock) }
                } finally {
                    canvas.restoreToCount(checkpoint)
                }
            }
        }.also { com.lingion.sleepy.ui.glass.GlassSourceDemand.link(it, source, destination) }
    }
}
