package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop

internal val LocalGlassHost = compositionLocalOf<GlassHostState?> { null }
internal val LocalGlassDepth = compositionLocalOf { 1f }
internal val LocalGlassSourceContent = compositionLocalOf { false }

/**
 * Explicit source graph: ordinary source segments may be sampled only by later consumers.
 * No whole-scene capture, readback, mutable visibility flag, or replay of live glass nodes.
 * The owning layout must declare depth at a boundary which owns its clip and transforms.
 */
class GlassHostState internal constructor(internal val sourceLayer: GraphicsLayer, internal val sceneLayer: GraphicsLayer) {
    internal var coordinates: LayoutCoordinates? = null
    internal var background: Color = Color.Transparent
    internal val sources = linkedSetOf<GlassSourceSegment>()
    internal var version by mutableIntStateOf(0)
    internal var sceneVersion by mutableIntStateOf(0)
    internal val overlayBackdrop: Backdrop = object : Backdrop {
        override val isCoordinatesDependent = true
        override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?, layerBlock: (GraphicsLayerScope.() -> Unit)?) {
            sceneVersion
            val target = coordinates?.takeIf { it.isAttached } ?: return
            val root = this@GlassHostState.coordinates?.takeIf { it.isAttached } ?: return
            val matrix = Matrix()
            if (transformBetween(root, target, matrix)) {
                withTransform({ transform(matrix) }) { drawLayer(sceneLayer) }
            }
        }
    }
    private val backdrops = mutableMapOf<Float, Backdrop>()
    internal var filterBands: List<GlassFilterBand> = emptyList()
    internal var filterDemand by mutableIntStateOf(0)
    internal fun filterBand(depth: Float, overlay: Boolean, radiusDp: Float): GlassFilterBand? =
        filterBands.firstOrNull { (depth == 1f || overlay) && it.overlay == overlay && it.radiusDp == radiusDp }
            ?.also { band ->
                if (!band.requested) {
                    band.requested = true
                    Snapshot.withoutReadObservation { filterDemand++ }
                }
            }

    internal fun changed() { Snapshot.withoutReadObservation { version++ } }

    internal fun DrawScope.drawPrefix(depth: Float, target: LayoutCoordinates) {
        version
        drawRect(background)
        sources.filter { it.depth < depth }.sortedBy { it.depth }.forEach { segment ->
            val source = segment.coordinates?.takeIf { it.isAttached } ?: return@forEach
            val matrix = Matrix()
            if (transformBetween(source, target, matrix)) {
                val visible = try { target.localBoundingBoxOf(source, clipBounds = true) }
                    catch (_: IllegalArgumentException) { null }
                if (visible != null) {
                    clipRect(visible.left, visible.top, visible.right, visible.bottom) {
                        withTransform({ transform(matrix) }) { drawLayer(segment.layer) }
                    }
                } else {
                    // Cross-window coordinates: source owns its recorded clipping boundary.
                    withTransform({ transform(matrix) }) { drawLayer(segment.layer) }
                }
            }
        }
    }

    internal fun backdropAt(depth: Float): Backdrop = backdrops.getOrPut(depth) {
        object : Backdrop {
            override val isCoordinatesDependent = true
            override fun DrawScope.drawBackdrop(
                density: Density, coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?,
            ) {
                val target = coordinates?.takeIf { it.isAttached } ?: return
                with(this@GlassHostState) { drawPrefix(depth, target) }
            }
        }
    }
}

internal class GlassSourceSegment(val depth: Float, val layer: GraphicsLayer) {
    var nativeGeneration by mutableIntStateOf(0)
    var coordinates: LayoutCoordinates? = null
}

@Composable
fun rememberGlassHostState(): GlassHostState {
    val source = rememberGraphicsLayer()
    val scene = rememberGraphicsLayer()
    val bands = mutableListOf<GlassFilterBand>()
    for (overlay in listOf(false, true)) {
        for (sigma in listOf(2.5f, 12f, 16f)) {
            val filtered = rememberGraphicsLayer()
            val published = rememberGraphicsLayer()
            bands += remember(filtered, published, sigma, overlay) { GlassFilterBand(sigma, overlay, filtered, published) }
        }
    }
    return remember(source, scene) { GlassHostState(source, scene) }.also { it.filterBands = bands }
}

@Composable
fun GlassHost(
    modifier: Modifier = Modifier,
    state: GlassHostState = rememberGlassHostState(),
    enabled: Boolean = true,
    background: Color = androidx.compose.material3.MaterialTheme.colorScheme.background,
    content: @Composable BoxScope.() -> Unit,
) {
    state.background = background
    CompositionLocalProvider(
        LocalGlassHost provides if (enabled) state else null,
        LocalGlassMaterialBackdrop provides null,
    ) {
        Box(modifier.onGloballyPositioned { state.coordinates = it }.drawWithContent {
            state.filterDemand
            if (enabled) {
                recordGlassLayer(state.sceneLayer) { this@drawWithContent.drawContent() }
                drawLayer(state.sceneLayer)
                Snapshot.withoutReadObservation { state.sceneVersion++ }
            } else {
                drawContent()
            }
            val root = state.coordinates
            if (enabled && root?.isAttached == true) {
                // Diagnostic export only. Consumers read their own strict prefix directly.
                recordGlassLayer(state.sourceLayer) {
                    Snapshot.withoutReadObservation { with(state) { drawPrefix(1f, root) } }
                }
                Snapshot.withoutReadObservation { state.filterBands.forEach { it.update(this@drawWithContent, state) } }
            }
        }, content = content)
    }
}

/**
 * Declare pane-free ordinary drawing. Place this boundary outside its own clip/opacity so the
 * recorded segment retains that compositing. Sources must never contain optical consumers.
 */
@Composable
fun GlassBackground(
    depth: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    require(depth.isFinite()) { "Source depth must be finite" }
    check(!LocalGlassSourceContent.current) { "Nested glass sources would record content twice" }
    val host = LocalGlassHost.current
    val layer = rememberGraphicsLayer()
    val segment = remember(host, depth, layer) { GlassSourceSegment(depth, layer) }
    DisposableEffect(host, segment) {
        if (host != null) {
            // Disjoint leaves may share a depth. Overlapping sources must declare distinct
            // depths matching their owning container's paint order, never registration order.
            host.sources.add(segment)
            host.changed()
        }
        onDispose { host?.sources?.remove(segment); host?.changed() }
    }
    CompositionLocalProvider(LocalGlassSourceContent provides (host != null)) {
        Box(modifier.onGloballyPositioned { segment.coordinates = it }.drawWithContent {
            segment.nativeGeneration
            recordGlassLayer(layer) { this@drawWithContent.drawContent() }
            drawLayer(layer)
            host?.changed()
        }, content = content)
    }
}

/** Controls inside this layer sample only explicitly declared sources with a smaller depth. */
@Composable
fun GlassLayer(depth: Float, content: @Composable () -> Unit) {
    require(depth.isFinite())
    CompositionLocalProvider(LocalGlassDepth provides depth, content = content)
}

internal fun transformBetween(producer: LayoutCoordinates, consumer: LayoutCoordinates, output: Matrix): Boolean {
    output.reset()
    return try {
        consumer.transformFrom(producer, output)
        true
    } catch (_: IllegalArgumentException) {
        val origin = consumer.screenToLocal(producer.localToScreen(Offset.Zero))
        val x = consumer.screenToLocal(producer.localToScreen(Offset(1f, 0f)))
        val y = consumer.screenToLocal(producer.localToScreen(Offset(0f, 1f)))
        if (listOf(origin.x, origin.y, x.x, x.y, y.x, y.y).any { !it.isFinite() }) return false
        output[0, 0] = x.x - origin.x; output[0, 1] = x.y - origin.y
        output[1, 0] = y.x - origin.x; output[1, 1] = y.y - origin.y
        output[3, 0] = origin.x; output[3, 1] = origin.y
        true
    }
}

/** A popup publishes its complete scene for later popup windows, never for itself. */
internal val LocalGlassPopupScene = compositionLocalOf<GlassHostState?> { null }
internal val LocalGlassOverlaySamplingHost = compositionLocalOf<GlassHostState?> { null }

internal fun Modifier.recordGlassPopupScene(state: GlassHostState): Modifier =
    onGloballyPositioned { state.coordinates = it }.drawWithContent {
        state.filterDemand
        recordGlassLayer(state.sceneLayer) { this@drawWithContent.drawContent() }
        drawLayer(state.sceneLayer)
        Snapshot.withoutReadObservation { state.sceneVersion++ }
        Snapshot.withoutReadObservation {
            state.filterBands.filter { it.overlay }.forEach { it.update(this@drawWithContent, state) }
        }
    }
