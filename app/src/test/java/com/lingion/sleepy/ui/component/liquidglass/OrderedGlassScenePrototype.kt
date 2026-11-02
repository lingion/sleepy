package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Explicit ordering prototype. Only declared, pane-free background segments are sampled.
 * The owning layout supplies stable depth values matching its actual drawing order. A segment
 * must own its clipping/opacity inside the recorded modifier; unknown ancestor compositing
 * cannot be recovered from coordinates. This API is not yet connected to production pages.
 */
internal class OrderedGlassSceneState {
    internal var coordinates: LayoutCoordinates? = null
    internal val segments = linkedSetOf<OrderedGlassSegment>()

    internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPrefix(
        beforeDepth: Float,
        background: Color,
    ) {
        drawRect(background)
        val root = coordinates?.takeIf { it.isAttached } ?: return
        segments.filter { it.depth < beforeDepth }.sortedBy { it.depth }.forEach { segment ->
            val local = segment.coordinates?.takeIf { it.isAttached } ?: return@forEach
            val matrix = Matrix()
            if (transformBetween(local, root, matrix)) {
                withTransform({ transform(matrix) }) { drawLayer(segment.layer) }
            }
        }
    }
}

internal class OrderedGlassSegment(val depth: Float, val layer: GraphicsLayer) {
    var coordinates: LayoutCoordinates? = null
}

private val LocalOrderedScene = compositionLocalOf<OrderedGlassSceneState?> { null }
private val LocalInsideBackgroundSegment = compositionLocalOf { false }

@Composable
internal fun rememberOrderedGlassSceneState(): OrderedGlassSceneState = remember { OrderedGlassSceneState() }

@Composable
internal fun OrderedGlassScene(
    modifier: Modifier = Modifier,
    state: OrderedGlassSceneState = rememberOrderedGlassSceneState(),
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(LocalOrderedScene provides state) {
        Box(modifier.onGloballyPositioned { state.coordinates = it }, content = content)
    }
}

/** A source segment contains ordinary drawing only; nested sources and glass are rejected. */
@Composable
internal fun OrderedGlassBackground(
    depth: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    check(!LocalInsideBackgroundSegment.current) { "Background segments cannot contain other source segments" }
    val scene = checkNotNull(LocalOrderedScene.current) { "Background requires OrderedGlassScene" }
    val layer = rememberGraphicsLayer()
    val segment = remember(scene, depth, layer) { OrderedGlassSegment(depth, layer) }
    DisposableEffect(scene, segment) {
        check(scene.segments.none { it.depth == depth }) { "Background depth must be unique" }
        scene.segments.add(segment)
        onDispose { scene.segments.remove(segment) }
    }
    CompositionLocalProvider(LocalInsideBackgroundSegment provides true) {
        Box(modifier.onGloballyPositioned { segment.coordinates = it }.drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }, content = content)
    }
}

/** Test-only consumer exposes its actual prefix, without sampling its own or later segments. */
@Composable
internal fun OrderedGlassPrefixProbe(
    depth: Float,
    destination: GraphicsLayer,
    background: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    check(!LocalInsideBackgroundSegment.current) { "A source segment must not contain a glass consumer" }
    val scene = checkNotNull(LocalOrderedScene.current)
    Box(modifier.drawWithContent {
        destination.record { with(scene) { drawPrefix(depth, background) } }
        drawContent()
    }, content = content)
}
