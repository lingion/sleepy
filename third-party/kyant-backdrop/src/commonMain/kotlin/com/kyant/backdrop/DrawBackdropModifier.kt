// Modified for SleepDown on 2026-09-08; upstream 2.0.0, Apache-2.0 (see README.md).
package com.kyant.backdrop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.node.requireLayoutDirection
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.SharedBlurBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightElement
import com.kyant.backdrop.internal.ShapeProvider
import com.kyant.backdrop.internal.recordLayer
import com.kyant.backdrop.internal.BackdropRecordingCache
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.InnerShadowElement
import com.kyant.backdrop.shadow.Shadow
import com.kyant.backdrop.shadow.ShadowElement
import androidx.compose.ui.unit.Density
import kotlin.math.ceil

private val DefaultHighlight = { Highlight.Default }
private val DefaultShadow = { Shadow.Default }
private val DefaultOnDrawBackdrop: DrawScope.(DrawScope.() -> Unit) -> Unit = { it() }

fun Modifier.drawPlainBackdrop(
    backdrop: Backdrop,
    shape: () -> Shape,
    effects: BackdropEffectScope.() -> Unit,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    exportedBackdrop: LayerBackdrop? = null,
    onDrawBehind: (DrawScope.() -> Unit)? = null,
    onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = DefaultOnDrawBackdrop,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
    onDrawFront: (DrawScope.() -> Unit)? = null,
    renderOptions: BackdropRenderOptions = BackdropRenderOptions.Default
): Modifier {
    val shapeProvider = ShapeProvider(shape, renderOptions)
    return this
        .then(
            if (layerBlock != null) {
                Modifier.graphicsLayer(layerBlock)
            } else {
                Modifier
            }
        )
        .then(
            DrawBackdropElement(
                backdrop = backdrop,
                shapeProvider = shapeProvider,
                effects = effects,
                layerBlock = layerBlock,
                exportedBackdrop = exportedBackdrop,
                onDrawBehind = onDrawBehind,
                onDrawBackdrop = onDrawBackdrop,
                onDrawSurface = onDrawSurface,
                onDrawFront = onDrawFront
            )
        )
}

fun Modifier.drawBackdrop(
    backdrop: Backdrop,
    shape: () -> Shape,
    effects: BackdropEffectScope.() -> Unit,
    highlight: (() -> Highlight?)? = DefaultHighlight,
    shadow: (() -> Shadow?)? = DefaultShadow,
    innerShadow: (() -> InnerShadow?)? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    exportedBackdrop: LayerBackdrop? = null,
    onDrawBehind: (DrawScope.() -> Unit)? = null,
    onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = DefaultOnDrawBackdrop,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
    onDrawFront: (DrawScope.() -> Unit)? = null,
    renderOptions: BackdropRenderOptions = BackdropRenderOptions.Default
): Modifier {
    val shapeProvider = ShapeProvider(shape, renderOptions)
    return this
        .then(
            if (layerBlock != null) {
                Modifier.graphicsLayer(layerBlock)
            } else {
                Modifier
            }
        )
        .then(
            if (innerShadow != null) {
                InnerShadowElement(
                    shapeProvider = shapeProvider,
                    shadow = innerShadow
                )
            } else {
                Modifier
            }
        )
        .then(
            if (shadow != null) {
                ShadowElement(
                    shapeProvider = shapeProvider,
                    shadow = shadow
                )
            } else {
                Modifier
            }
        )
        .then(
            if (highlight != null) {
                HighlightElement(
                    shapeProvider = shapeProvider,
                    highlight = highlight
                )
            } else {
                Modifier
            }
        )
        .then(
            DrawBackdropElement(
                backdrop = backdrop,
                shapeProvider = shapeProvider,
                effects = effects,
                layerBlock = layerBlock,
                exportedBackdrop = exportedBackdrop,
                onDrawBehind = onDrawBehind,
                onDrawBackdrop = onDrawBackdrop,
                onDrawSurface = onDrawSurface,
                onDrawFront = onDrawFront
            )
        )
}

private class DrawBackdropElement(
    val backdrop: Backdrop,
    val shapeProvider: ShapeProvider,
    val effects: BackdropEffectScope.() -> Unit,
    val layerBlock: (GraphicsLayerScope.() -> Unit)?,
    val exportedBackdrop: LayerBackdrop?,
    val onDrawBehind: (DrawScope.() -> Unit)?,
    val onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit,
    val onDrawSurface: (DrawScope.() -> Unit)?,
    val onDrawFront: (DrawScope.() -> Unit)?
) : ModifierNodeElement<DrawBackdropNode>() {

    override fun create(): DrawBackdropNode {
        return DrawBackdropNode(
            backdrop = backdrop,
            shapeProvider = shapeProvider,
            effects = effects,
            layerBlock = layerBlock,
            exportedBackdrop = exportedBackdrop,
            onDrawBehind = onDrawBehind,
            onDrawBackdrop = onDrawBackdrop,
            onDrawSurface = onDrawSurface,
            onDrawFront = onDrawFront
        )
    }

    override fun update(node: DrawBackdropNode) {
        node.invalidateSampleRecording()
        val effectsChanged = node.shapeProvider != shapeProvider || node.effects != effects
        node.backdrop = backdrop
        if (node.shapeProvider != shapeProvider) node.shapeProvider = shapeProvider
        node.effects = effects
        node.layerBlock = layerBlock
        if (node.exportedBackdrop != exportedBackdrop) {
            node.exportedBackdrop?.layerCoordinates = null
            node.exportedBackdrop = exportedBackdrop
        }
        node.onDrawBehind = onDrawBehind
        node.onDrawBackdrop = onDrawBackdrop
        node.onDrawSurface = onDrawSurface
        node.onDrawFront = onDrawFront
        node.synchronizeSampleLayer()
        if (effectsChanged) node.invalidateDrawCache() else node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "drawBackdrop"
        properties["backdrop"] = backdrop
        properties["shapeProvider"] = shapeProvider
        properties["effects"] = effects
        properties["layerBlock"] = layerBlock
        properties["exportedBackdrop"] = exportedBackdrop
        properties["onDrawBehind"] = onDrawBehind
        properties["onDrawBackdrop"] = onDrawBackdrop
        properties["onDrawSurface"] = onDrawSurface
        properties["onDrawFront"] = onDrawFront
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DrawBackdropElement) return false

        if (backdrop != other.backdrop) return false
        if (shapeProvider != other.shapeProvider) return false
        if (effects != other.effects) return false
        if (layerBlock != other.layerBlock) return false
        if (exportedBackdrop != other.exportedBackdrop) return false
        if (onDrawBehind != other.onDrawBehind) return false
        if (onDrawBackdrop != other.onDrawBackdrop) return false
        if (onDrawSurface != other.onDrawSurface) return false
        if (onDrawFront != other.onDrawFront) return false

        return true
    }

    override fun hashCode(): Int {
        var result = backdrop.hashCode()
        result = 31 * result + shapeProvider.hashCode()
        result = 31 * result + effects.hashCode()
        result = 31 * result + (layerBlock?.hashCode() ?: 0)
        result = 31 * result + (exportedBackdrop?.hashCode() ?: 0)
        result = 31 * result + (onDrawBehind?.hashCode() ?: 0)
        result = 31 * result + onDrawBackdrop.hashCode()
        result = 31 * result + (onDrawSurface?.hashCode() ?: 0)
        result = 31 * result + (onDrawFront?.hashCode() ?: 0)
        return result
    }
}

private class DrawBackdropNode(
    var backdrop: Backdrop,
    shapeProvider: ShapeProvider,
    var effects: BackdropEffectScope.() -> Unit,
    var layerBlock: (GraphicsLayerScope.() -> Unit)?,
    var exportedBackdrop: LayerBackdrop?,
    var onDrawBehind: (DrawScope.() -> Unit)?,
    var onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit,
    var onDrawSurface: (DrawScope.() -> Unit)?,
    var onDrawFront: (DrawScope.() -> Unit)?
) : LayoutModifierNode, DrawModifierNode, GlobalPositionAwareModifierNode, ObserverModifierNode, Modifier.Node() {

    // The placement layer retains its callback after a pager reuses a card host. Observe a
    // replacement provider so its clip follows the current outline at unchanged size.
    var shapeProvider: ShapeProvider by mutableStateOf(shapeProvider)

    private val effectScope =
        object : BackdropEffectScopeImpl() {

            override val shape: Shape get() = shapeProvider.innerShape
        }

    private var graphicsLayer: GraphicsLayer? = null
    private val layerDiagnostics = BackdropLayerDiagnostics("Sample")
    private val sampleRecordingCache = BackdropRecordingCache()
    private var lastEffectKey: Any? = null
    private var lastEffectShape: Shape? = null
    private val drawClipPath = Path()
    private var clippedRoundRect: RoundRect? = null

    private val layoutLayerBlock: GraphicsLayerScope.() -> Unit = {
        clip = true
        shape = shapeProvider.snapshot(size, requireLayoutDirection(), this)
        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
    }

    // This layout/draw node places its content in an inner layer. invalidateDraw() alone can
    // dirty the outer coordinator while that layer keeps replaying its old sample. Observe
    // coordinates from draw so movement invalidates the layer that actually records the glass.
    // LayoutCoordinates mutates in place, so assigning the same instance must still notify it.
    private var layoutCoordinates: LayoutCoordinates? by mutableStateOf(null, neverEqualPolicy())
    private var padding by mutableFloatStateOf(0f)

    private val recordBackdropBlock: (DrawScope.() -> Unit) = {
        val canvas = drawContext.canvas
        val padding = padding
        val sampleScale = shapeProvider.options.sampleScale

        canvas.save()
        canvas.scale(sampleScale, sampleScale)
        if (padding != 0f) {
            canvas.translate(padding / sampleScale, padding / sampleScale)
        }
        onDrawBackdrop {
            with(backdrop) {
                drawBackdrop(
                    density = Density(effectScope.density / sampleScale, effectScope.fontScale),
                    coordinates = layoutCoordinates,
                    layerBlock = layerBlock
                )
            }
        }
        canvas.restore()
    }

    private val drawBackdropLayer: DrawScope.() -> Unit = drawBackdropLayer@{
        if (!shapeProvider.options.sampleBackdrop) return@drawBackdropLayer
        val viewport = shapeProvider.options.viewport
        val coordinates = layoutCoordinates
        if (viewport != null && coordinates?.isAttached == true &&
            !viewport.intersects(coordinates)) {
            BackdropDiagnostics.event("Sample.OutsideViewport")
            return@drawBackdropLayer
        }

        val layer = graphicsLayer
        if (layer == null) {
            val canvas = drawContext.canvas
            canvas.save()
            try {
                canvas.clipRect(0f, 0f, size.width, size.height)
                onDrawBackdrop {
                    with(backdrop) { drawBackdrop(effectScope, coordinates, layerBlock) }
                }
            } finally {
                canvas.restore()
            }
            if (backdrop is SharedBlurBackdrop) BackdropDiagnostics.event("Sample.SharedBlit")
            return@drawBackdropLayer
        }

        val sampleScale = shapeProvider.options.sampleScale
        val allocationPadding = shapeProvider.options.allocationPadding ?: padding
        require(allocationPadding >= padding) { "Fixed allocation must cover effect padding" }
        val recordingSize = IntSize(
            ceil(size.width * sampleScale + allocationPadding * 2).toInt().coerceAtLeast(1),
            ceil(size.height * sampleScale + allocationPadding * 2).toInt().coerceAtLeast(1)
        )
        // Only retained, frozen scenes need a completed local recording. Ordinary course cards
        // follow the reference path: one local Lens layer sampling the shared wallpaper node.
        val shared = backdrop as? SharedBlurBackdrop
        val frozen = shapeProvider.options.coordinatesFrozen()
        val key = if (frozen) shapeProvider.options.sampleRecordKey()?.let {
            if (shared != null) it to shared.contentRevision else it
        } else null
        if (!frozen || sampleRecordingCache.needsRecord(key, recordingSize, density, fontScale, layoutDirection)) {
            recordLayer(layer, size = recordingSize, block = recordBackdropBlock)
            layerDiagnostics.recorded(layer.size)
            sampleRecordingCache.recorded(key, recordingSize, density, fontScale, layoutDirection)
            if (shared != null) BackdropDiagnostics.event("Sample.SharedDirect")
        } else {
            BackdropDiagnostics.event("Sample.FrozenReuse")
        }
        layer.topLeft = IntOffset.Zero
        val canvas = drawContext.canvas
        canvas.save()
        try {
            canvas.scale(1f / sampleScale, 1f / sampleScale)
            canvas.translate(-padding, -padding)
            drawLayer(layer)
        } finally {
            canvas.restore()
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            if (shapeProvider.options.placementLayer) {
                placeable.placeWithLayer(IntOffset.Zero, layerBlock = layoutLayerBlock)
            } else {
                placeable.place(IntOffset.Zero)
            }
        }
    }

    override fun ContentDrawScope.draw() {
        // Disabled material still draws its local content. Observe only its enable flag so
        // wallpaper revisions and coordinate changes cannot invalidate an invisible sample.
        if (!shapeProvider.options.enabled()) return drawContent()
        // Observe freeze/resume here, so resume refreshes the existing sample once even if no
        // layout callback follows. The retained GraphicsLayer still references child RenderNodes:
        // suppressing only the parent's drawContent does not stop their own invalidations.
        shapeProvider.options.coordinatesFrozen()
        // Keep the draw observation even when a frozen sample skips the recording block.
        layoutCoordinates
        // Reads made inside GraphicsLayer.record are not owned by this modifier's draw
        // observer. Observe the shared wallpaper origin here so its first placement refreshes
        // cards that drew before the producer reported coordinates. Also observe rerecordings:
        // the producer reuses one GraphicsLayer, so its content can change without its identity.
        (backdrop as? SharedBlurBackdrop)?.let { shared ->
            shared.source.layerCoordinates
            shared.contentRevision
        }
        val bounds = shapeProvider.options.bounds()
        val sampleScale = shapeProvider.options.sampleScale
        require(sampleScale > 0f && sampleScale <= 1f)
        check(sampleScale == 1f || (bounds == null && exportedBackdrop == null)) {
            "Downsampling requires ordinary geometry without an exported backdrop"
        }
        check(bounds == null || exportedBackdrop == null) {
            "Fixed geometry does not support exported backdrops"
        }
        if (effectScope.update(this, (bounds?.size ?: size) * sampleScale,
                (bounds?.topLeft ?: Offset.Zero) * sampleScale, sampleScale)) {
            updateEffects(geometryChanged = true)
        }

        // A retained placement layer may replay rectangular pixels after a card is reused.
        // Clip the material recording to the current rounded or path outline as well; the
        // separate highlight and shadow nodes keep their original out-of-bounds light.
        val outline = if (shapeProvider.options.clipGenericOutlineInDraw) {
            shapeProvider.shape.createOutline(size, layoutDirection, this)
        } else null
        val canvas = drawContext.canvas
        val clipMaterial = outline != null && outline !is Outline.Rectangle
        if (clipMaterial) {
            canvas.save()
            when (val current = checkNotNull(outline)) {
                is Outline.Rounded -> {
                    if (clippedRoundRect != current.roundRect) {
                        drawClipPath.rewind()
                        drawClipPath.addRoundRect(current.roundRect)
                        clippedRoundRect = current.roundRect
                    }
                    canvas.clipPath(drawClipPath)
                }
                is Outline.Generic -> canvas.clipPath(current.path)
                is Outline.Rectangle -> canvas.clipRect(current.rect)
            }
        }
        try {
            drawSurfaceCallback(onDrawBehind)
            drawBackdropLayer()
            drawSurfaceCallback(onDrawSurface)
            drawContent()
            drawSurfaceCallback(onDrawFront)
        } finally {
            if (clipMaterial) canvas.restore()
        }

        exportedBackdrop?.graphicsLayer?.let { layer ->
            recordLayer(layer) {
                onDrawBehind?.invoke(this)
                drawBackdropLayer()
                onDrawSurface?.invoke(this)
                onDrawFront?.invoke(this)
            }
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            if (backdrop.isCoordinatesDependent) {
                // Always accept a new node; retained scene contents keep their recorded position.
                // Moving foreground cards still resample the live wallpaper at every position.
                if (!shapeProvider.options.coordinatesFrozen() || layoutCoordinates !== coordinates) {
                    invalidateSampleRecording()
                    layoutCoordinates = coordinates
                }
            } else {
                if (layoutCoordinates != null) {
                    invalidateSampleRecording()
                    layoutCoordinates = null
                }
            }
            exportedBackdrop?.layerCoordinates = coordinates
        }
    }

    private fun DrawScope.drawSurfaceCallback(block: (DrawScope.() -> Unit)?) {
        if (block == null) return
        val bounds = shapeProvider.options.bounds()
        if (bounds == null) block() else {
            inset(bounds.left, bounds.top, size.width - bounds.right, size.height - bounds.bottom) {
                block()
            }
        }
    }

    override fun onObservedReadsChanged() {
        invalidateDrawCache()
    }

    fun invalidateDrawCache() {
        observeEffects()
    }

    private fun observeEffects() {
        observeReads { updateEffects() }
    }

    private fun updateEffects(geometryChanged: Boolean = false) {
        if (geometryChanged) invalidateSampleRecording()
        if (!shapeProvider.options.enabled() || !shapeProvider.options.sampleBackdrop || !isRenderEffectSupported()) return

        val effectKey = shapeProvider.options.effectKey()
        val effectShape = shapeProvider.innerShape
        if (!geometryChanged && effectKey != null && effectKey == lastEffectKey && effectShape == lastEffectShape) return

        effectScope.apply(effects)
        lastEffectKey = effectKey
        lastEffectShape = effectShape
        BackdropDiagnostics.event("Sample.EffectRebuilt")
        val effect = effectScope.renderEffect
        if (effect != null || effectScope.padding != 0f) {
            val layer = graphicsLayer ?: requireGraphicsContext().createGraphicsLayer().also {
                graphicsLayer = it
                layerDiagnostics.created()
            }
            if (layer.renderEffect != effect) layer.renderEffect = effect
        } else {
            releaseSampleLayer()
        }
        padding = effectScope.padding
    }

    private fun releaseSampleLayer() {
        graphicsLayer?.let {
            requireGraphicsContext().releaseGraphicsLayer(it)
            layerDiagnostics.released()
        }
        graphicsLayer = null
        invalidateSampleRecording()
    }

    fun synchronizeSampleLayer() {
        if (isAttached && !shapeProvider.options.sampleBackdrop) releaseSampleLayer()
    }

    override fun onAttach() {
        synchronizeSampleLayer()
        observeEffects()
    }

    override fun onDetach() {
        invalidateSampleRecording()
        val graphicsContext = requireGraphicsContext()
        graphicsLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            layerDiagnostics.released()
            graphicsLayer = null
        }

        effectScope.reset()
        lastEffectKey = null
        lastEffectShape = null
        layoutCoordinates = null
        clippedRoundRect = null
        drawClipPath.rewind()
        exportedBackdrop?.layerCoordinates = null
    }

    fun invalidateSampleRecording() {
        sampleRecordingCache.clear()
    }
}
