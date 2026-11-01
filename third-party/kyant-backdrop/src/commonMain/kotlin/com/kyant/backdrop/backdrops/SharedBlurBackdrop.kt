// SleepDown implementation on the Apache-2.0 Kyant 2.0.0 API; see README.md.
package com.kyant.backdrop.backdrops

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toIntSize
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScopeImpl
import com.kyant.backdrop.BackdropLayerDiagnostics
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.internal.BackdropRecordingCache

/**
 * Full-resolution wallpaper prefix shared by course lenses. The filtering node is separate
 * from the published node, which has no RenderEffect of its own. Both retain display lists:
 * record { drawLayer(blurLayer) } alone is NOT a pixel snapshot. The published node explicitly
 * uses Offscreen so HWUI rasterizes its unchanged display list into one retained shared surface,
 * instead of replaying the inner filter with each card's different clip. GPU work still occurs
 * when that surface is dirty; there is no CPU readback or per-card fullscreen blur request.
 */
@Stable
class SharedBlurBackdrop(val source: LayerBackdrop, radiusPx: Float, vibrant: Boolean) : Backdrop {
    var radiusPx: Float by mutableFloatStateOf(radiusPx)
    var vibrant: Boolean by mutableStateOf(vibrant)
    internal var layer: GraphicsLayer? by mutableStateOf(null)
    internal var contentRevision: Int by mutableIntStateOf(0)
    val ready: Boolean get() = layer != null && source.layerCoordinates?.isAttached == true
    override val isCoordinatesDependent = true

    fun preRenderModifier(sourceKey: () -> Any?): Modifier = RecorderElement(this, sourceKey)

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        // Raw and filtered wallpaper use the same full-resolution coordinate transform.
        with(source) { drawLayerAtSource(layer ?: graphicsLayer, density, coordinates, layerBlock) }
    }
}

private data class RecorderElement(val backdrop: SharedBlurBackdrop, val sourceKey: () -> Any?) : ModifierNodeElement<RecorderNode>() {
    override fun create() = RecorderNode(backdrop, sourceKey)
    override fun update(node: RecorderNode) {
        if (node.backdrop !== backdrop) {
            node.backdrop.layer = null
            node.cache.clear()
            node.resetEffectTracking()
            node.backdrop = backdrop
        }
        node.sourceKey = sourceKey
    }
    override fun InspectorInfo.inspectableProperties() { name = "sharedWallpaperBlur" }
}

private class RecorderNode(var backdrop: SharedBlurBackdrop, var sourceKey: () -> Any?) : Modifier.Node(), DrawModifierNode {
    val cache = BackdropRecordingCache()
    private var blurLayer: GraphicsLayer? = null
    private var bakedLayer: GraphicsLayer? = null
    private val diagnostics = BackdropLayerDiagnostics("SharedWallpaper")
    private val effects = object : BackdropEffectScopeImpl() { override val shape = RectangleShape }
    private var lastRadius = Float.NaN
    private var lastVibrant: Boolean? = null

    fun resetEffectTracking() {
        lastRadius = Float.NaN
        lastVibrant = null
        effects.reset()
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val filtered = blurLayer ?: return
        val published = bakedLayer ?: return
        val dimensions = size.toIntSize()
        if (dimensions.width <= 0 || dimensions.height <= 0 ||
            backdrop.source.layerCoordinates?.isAttached != true) return
        val key = sourceKey()?.let { it to backdrop.source.contentVersion }
        val sourceDirty = cache.needsRecord(key, dimensions, density, fontScale, layoutDirection)
        val geometryDirty = effects.update(this)
        val effectDirty = geometryDirty || lastRadius != backdrop.radiusPx || lastVibrant != backdrop.vibrant
        if (effectDirty) {
            effects.apply {
                if (backdrop.vibrant) vibrancy()
                blur(backdrop.radiusPx)
            }
            filtered.renderEffect = effects.renderEffect
            lastRadius = backdrop.radiusPx
            lastVibrant = backdrop.vibrant
        }
        if (sourceDirty) {
            filtered.record(dimensions) { drawLayer(backdrop.source.graphicsLayer) }
            cache.recorded(key, dimensions, density, fontScale, layoutDirection)
            diagnostics.recorded(dimensions)
        }
        if (sourceDirty || effectDirty) {
            published.record(dimensions) { drawLayer(filtered) }
            diagnostics.recorded(dimensions)
            backdrop.layer = published
            Snapshot.withoutReadObservation { backdrop.contentRevision++ }
        }
    }

    override fun onAttach() {
        val context = requireGraphicsContext()
        blurLayer = context.createGraphicsLayer()
        bakedLayer = context.createGraphicsLayer().apply {
            // A null effect with Auto only forwards a display list. This single shared raster
            // boundary is essential: the filtered node must not inherit every card's clip.
            compositingStrategy = CompositingStrategy.Offscreen
        }
        repeat(2) { diagnostics.created() }
    }

    override fun onDetach() {
        backdrop.layer = null
        val context = requireGraphicsContext()
        blurLayer?.let { context.releaseGraphicsLayer(it); diagnostics.released() }
        bakedLayer?.let { context.releaseGraphicsLayer(it); diagnostics.released() }
        blurLayer = null
        bakedLayer = null
        cache.clear()
        resetEffectTracking()
    }
}
