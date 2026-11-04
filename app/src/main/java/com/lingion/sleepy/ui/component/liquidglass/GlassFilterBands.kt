package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop

/** One host/prefix/sigma resource, never a per-button fullscreen blur. */
internal class GlassFilterBand(
    val radiusDp: Float,
    val overlay: Boolean,
    private val filtered: GraphicsLayer,
    private val published: GraphicsLayer,
) {
    var requested: Boolean = false
    var ready by mutableStateOf(false)
        private set
    private var key: Any? = null

    fun update(scope: DrawScope, host: GlassHostState) {
        if (!requested || android.os.Build.VERSION.SDK_INT < 31 || scope.size.width <= 0f || scope.size.height <= 0f) return
        val nextKey = listOf(
            if (overlay) host.sceneVersion else host.version,
            host.background, scope.size, scope.density, scope.layoutDirection,
        )
        if (nextKey == key) return
        val source = if (overlay) host.sceneLayer else host.sourceLayer
        with(scope) {
            filtered.renderEffect = BlurEffect(
                radiusX = radiusDp * density, radiusY = radiusDp * density,
                edgeTreatment = TileMode.Clamp,
            )
            recordGlassLayer(filtered) { drawLayer(source) }
            // A retained raster boundary shares the Gaussian result across consumer clips.
            published.compositingStrategy = CompositingStrategy.Offscreen
            recordGlassLayer(published) { drawLayer(filtered) }
        }
        key = nextKey
        ready = true
    }

    fun backdrop(host: GlassHostState): Backdrop = object : Backdrop {
        override val isCoordinatesDependent = true
        override fun DrawScope.drawBackdrop(
            density: Density, coordinates: LayoutCoordinates?,
            layerBlock: (GraphicsLayerScope.() -> Unit)?,
        ) {
            if (!ready) {
                val raw = if (overlay) host.overlayBackdrop else host.backdropAt(1f)
                with(raw) { drawBackdrop(density, coordinates, layerBlock) }
                return
            }
            if (overlay) host.sceneVersion else host.version
            val root = host.coordinates?.takeIf { it.isAttached } ?: return
            val target = coordinates?.takeIf { it.isAttached } ?: return
            val matrix = Matrix()
            if (transformBetween(root, target, matrix)) {
                withTransform({ transform(matrix) }) { drawLayer(published) }
            }
        }
    }
}
