package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import com.kyant.backdrop.Backdrop
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.unit.Density

internal val LocalGlassHost = compositionLocalOf<GlassHostState?> { null }

/**
 * A scene's source never contains its consumers. Surface nodes record their foreground into
 * separate layers without painting them into the source; the host composites them afterwards.
 * The registration is structural, not a temporary capture flag which retained layers can miss.
 */
class GlassHostState internal constructor(internal val sourceLayer: GraphicsLayer) {
    internal var coordinates: LayoutCoordinates? = null
    internal val panes = linkedSetOf<GlassPaneNode>()
    internal var recording = false
    internal var revision by mutableIntStateOf(0)
    internal val backdrop: Backdrop = object : Backdrop {
        override val isCoordinatesDependent = true
        override fun DrawScope.drawBackdrop(
            density: Density,
            coordinates: LayoutCoordinates?,
            layerBlock: (GraphicsLayerScope.() -> Unit)?,
        ) {
            val target = coordinates?.takeIf { it.isAttached } ?: return
            val source = this@GlassHostState.coordinates?.takeIf { it.isAttached } ?: return
            val matrix = Matrix()
            if (!transformBetween(source, target, matrix)) return
            withTransform({ transform(matrix) }) { drawLayer(sourceLayer) }
        }
    }

    internal fun requestDraw() {
        if (!recording) Snapshot.withoutReadObservation { revision++ }
    }
}

@Composable
fun rememberGlassHostState(): GlassHostState {
    val source = rememberGraphicsLayer()
    return remember(source) { GlassHostState(source) }
}

@Composable
fun GlassHost(
    modifier: Modifier = Modifier,
    state: GlassHostState = rememberGlassHostState(),
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(LocalGlassHost provides if (enabled) state else null) {
        Box(
            modifier.onGloballyPositioned {
                state.coordinates = it
                state.requestDraw()
            }.drawWithContent {
                if (!enabled) {
                    drawContent()
                    return@drawWithContent
                }
                state.revision // Subscribe to separately invalidating retained foregrounds.
                state.recording = true
                try {
                    state.sourceLayer.record { this@drawWithContent.drawContent() }
                } finally {
                    state.recording = false
                }
                drawLayer(state.sourceLayer)
                val root = state.coordinates
                if (root?.isAttached == true) {
                    state.panes.toList().forEach { pane -> pane.drawInHost(this, root) }
                }
            },
            content = content,
        )
    }
}

/** Map across popup roots in screen coordinates; do not confuse window-local origins. */
internal fun transformBetween(
    producer: LayoutCoordinates,
    consumer: LayoutCoordinates,
    output: Matrix,
): Boolean {
    output.reset()
    return try {
        consumer.transformFrom(producer, output)
        true
    } catch (_: IllegalArgumentException) {
        val origin = consumer.screenToLocal(producer.localToScreen(Offset.Zero))
        val x = consumer.screenToLocal(producer.localToScreen(Offset(1f, 0f)))
        val y = consumer.screenToLocal(producer.localToScreen(Offset(0f, 1f)))
        if (!origin.x.isFinite() || !origin.y.isFinite() || !x.x.isFinite() ||
            !x.y.isFinite() || !y.x.isFinite() || !y.y.isFinite()) return false
        output[0, 0] = x.x - origin.x
        output[0, 1] = x.y - origin.y
        output[1, 0] = y.x - origin.x
        output[1, 1] = y.y - origin.y
        output[3, 0] = origin.x
        output[3, 1] = origin.y
        true
    }
}
