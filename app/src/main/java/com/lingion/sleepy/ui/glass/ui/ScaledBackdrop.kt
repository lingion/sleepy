package com.lingion.sleepy.ui.glass.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop

/**
 * Makes a backdrop sample the same pixels as a full-screen surface that is
 * visually scaled around its centre, without scaling the glass consumer.
 *
 * LayerBackdrop currently compensates translations only. When its recorded
 * layer is displayed through an outer graphicsLayer scale, a Dialog/overlay
 * therefore keeps sampling the unscaled coordinate space. Applying that same
 * scale around the root's window-space centre fixes the sample while leaving
 * the card/dialog geometry untouched.
 */
@Composable
internal fun rememberScreenScaledBackdrop(
    backdrop: Backdrop?,
    scale: () -> Float,
    rootPositionOnScreen: () -> Offset,
    rootSize: () -> IntSize
): Backdrop? {
    if (backdrop == null) return null
    return remember(backdrop) {
        ScreenScaledBackdrop(
            delegate = backdrop,
            scale = scale,
            rootPositionOnScreen = rootPositionOnScreen,
            rootSize = rootSize
        ).also { com.lingion.sleepy.ui.glass.GlassSourceDemand.link(it, backdrop) }
    }
}

private class ScreenScaledBackdrop(
    private val delegate: Backdrop,
    private val scale: () -> Float,
    private val rootPositionOnScreen: () -> Offset,
    private val rootSize: () -> IntSize
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val coordinates = coordinates
        val zoom = scale()
        val size = rootSize()
        if (coordinates == null || size.width <= 0 || size.height <= 0) {
            with(delegate) {
                drawBackdrop(density, coordinates, layerBlock)
            }
            return
        }

        /*
         * The Agent consumer lives in a separate Android Dialog window. positionInWindow() has a
         * different origin there (notably on ColorOS), so combining it with the Activity root
         * shifts the sample by the status/decor inset. Screen coordinates are the common space.
         */
        val rootOrigin = rootPositionOnScreen()
        val rootCenterOnScreen = Offset(
            x = rootOrigin.x + size.width / 2f,
            y = rootOrigin.y + size.height / 2f
        )
        val consumerOriginOnScreen = coordinates.positionOnScreen()
        val pivotInConsumer = rootCenterOnScreen - consumerOriginOnScreen
        val providerOriginInConsumer = rootOrigin - consumerOriginOnScreen

        /*
         * LayerBackdrop normally derives its translation through localPositionOf(). That is
         * correct while provider and consumer share an untransformed tree, but an outer home
         * scale makes that value contain the visual transform already. Wrapping it in another
         * screen-centred scale then applies the translation twice: the sampled image has the
         * right size but visibly drifts away from the frozen/background layer.
         *
         * Draw the recorded full-screen layer in window coordinates instead. The provider is
         * first placed at its root origin in this consumer, then that complete coordinate space
         * receives exactly the same centre scale as the visible home.
         */
        if (delegate is LayerBackdrop) {
            /*
             * GlassSurface always supplies a layerBlock, even for a non-interactive shell whose
             * current transform is identity. Delegating in that case re-enters LayerBackdrop's
             * localPositionOf() path; a Dialog belongs to another Compose root, so the fallback
             * mixes two window origins and shifts the sampled texture on ColorOS.
             *
             * The screen-scaled wrapper is only used by the non-interactive Morph glass shells.
             * Their geometry/press transform is already applied by drawBackdrop's outer
             * graphicsLayer modifier. Draw the source layer directly here so sampling stays in
             * the common screen coordinate space and is not inversely translated a second time.
             */
            withTransform({
                scale(
                    scaleX = zoom,
                    scaleY = zoom,
                    pivot = pivotInConsumer
                )
                translate(
                    left = providerOriginInConsumer.x,
                    top = providerOriginInConsumer.y
                )
            }) {
                drawLayer(delegate.graphicsLayer)
            }
        } else {
            if (kotlin.math.abs(zoom - 1f) < 0.0001f) {
                with(delegate) {
                    drawBackdrop(density, coordinates, layerBlock)
                }
            } else {
                withTransform({
                    scale(
                        scaleX = zoom,
                        scaleY = zoom,
                        pivot = pivotInConsumer
                    )
                }) {
                    with(delegate) {
                        drawBackdrop(density, coordinates, layerBlock)
                    }
                }
            }
        }
    }
}
