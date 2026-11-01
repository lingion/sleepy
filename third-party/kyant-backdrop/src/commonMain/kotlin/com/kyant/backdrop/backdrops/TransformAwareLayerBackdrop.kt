// SleepDown extension to Kyant Backdrop, Apache-2.0.
package com.kyant.backdrop.backdrops

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop

/** Opt-in for popups whose ancestors scale/stretch while the sampled page stays still. */
fun LayerBackdrop.withTransformCompensation(): Backdrop = TransformAwareLayerBackdrop(this)

private class TransformAwareLayerBackdrop(private val source: LayerBackdrop) : Backdrop {
    override val isCoordinatesDependent = true
    private val sourceToConsumer = Matrix()

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val consumer = coordinates?.takeIf { it.isAttached } ?: return
        val producer = source.layerCoordinates?.takeIf { it.isAttached } ?: return
        sourceToConsumer.reset()
        try {
            consumer.transformFrom(producer, sourceToConsumer)
        } catch (_: IllegalArgumentException) {
            // Separate popup windows have different roots and window origins. Map the affine
            // basis through screen space; translating window positions alone loses both scale
            // and the decor inset. This path intentionally excludes perspective transforms.
            val origin = consumer.screenToLocal(producer.localToScreen(Offset.Zero))
            val x = consumer.screenToLocal(producer.localToScreen(Offset(1f, 0f)))
            val y = consumer.screenToLocal(producer.localToScreen(Offset(0f, 1f)))
            if (!origin.isSpecified || !x.isSpecified || !y.isSpecified) return
            sourceToConsumer[0, 0] = x.x - origin.x
            sourceToConsumer[0, 1] = x.y - origin.y
            sourceToConsumer[1, 0] = y.x - origin.x
            sourceToConsumer[1, 1] = y.y - origin.y
            sourceToConsumer[3, 0] = origin.x
            sourceToConsumer[3, 1] = origin.y
        }
        // LayoutCoordinates already includes the popup's graphics layers. Applying Kyant's
        // explicit layerBlock inverse as well would compensate the same transform twice.
        withTransform({ transform(sourceToConsumer) }) { drawLayer(source.graphicsLayer) }
    }
}
