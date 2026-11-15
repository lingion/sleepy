package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toSize
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle

/** A completed parent material, never the parent's foreground or its descendants. */
internal val LocalGlassMaterialBackdrop = compositionLocalOf<Backdrop?> { null }

@Composable
internal fun rememberGlassSamplingBackdrop(): Backdrop? {
    LocalGlassMaterialBackdrop.current?.let { return it }
    val host = LocalGlassHost.current ?: return null
    val overlay = LocalGlassOverlay.current
    val samplingHost = if (overlay) LocalGlassOverlaySamplingHost.current ?: host else host
    return if (overlay) samplingHost.overlayBackdrop else samplingHost.backdropAt(LocalGlassDepth.current)
}

/**
 * A prefix plus one explicitly exported backing. The producer records only the
 * optical material (Backdrop's exportedBackdrop API) or one ordinary fill.
 * Child controls never occur in this layer, so nested materials form an acyclic
 * parent chain rather than sampling a completed tree that contains themselves.
 */
internal class GlassMaterialScope(
    val exported: LayerBackdrop,
    private val prefix: Backdrop,
    private val shape: Shape,
) : Backdrop {
    var coordinates: LayoutCoordinates? by mutableStateOf(null)
    override val isCoordinatesDependent = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        with(prefix) { drawBackdrop(density, coordinates, layerBlock) }
        val source = this@GlassMaterialScope.coordinates?.takeIf { it.isAttached } ?: return
        val target = coordinates?.takeIf { it.isAttached } ?: return
        val matrix = Matrix()
        if (!transformBetween(source, target, matrix)) return
        // The layout already has its size when a child first draws. The export
        // itself is completed after drawContent; using its initial 0x0 recording
        // size here would omit the retained layer reference from that first draw.
        val size = source.size.toSize()
        if (size.width <= 0f || size.height <= 0f) return
        withTransform({ transform(matrix) }) {
            // exportedBackdrop excludes drawContent but does not export the
            // outer shape clip. Restore that clip at the material's own origin.
            when (val outline = shape.createOutline(size, layoutDirection, this)) {
                is Outline.Rectangle -> clipRect(outline.rect.left, outline.rect.top, outline.rect.right, outline.rect.bottom) {
                    drawLayer(exported.graphicsLayer)
                }
                is Outline.Rounded -> clipPath(Path().apply { addRoundRect(outline.roundRect) }) {
                    drawLayer(exported.graphicsLayer)
                }
                is Outline.Generic -> clipPath(outline.path) { drawLayer(exported.graphicsLayer) }
            }
        }
    }
}

@Composable
internal fun rememberGlassMaterialScope(
    shape: Shape,
    prefix: Backdrop? = rememberGlassSamplingBackdrop(),
): GlassMaterialScope? {
    if (LocalUiStyle.current != UiStyle.LiquidGlass || LocalGlassHost.current == null || prefix == null) return null
    val exported = rememberLayerBackdrop()
    return remember(exported, prefix, shape) { GlassMaterialScope(exported, prefix, shape) }
}

internal fun Modifier.trackGlassMaterial(scope: GlassMaterialScope?): Modifier =
    if (scope == null) this else onGloballyPositioned { scope.coordinates = it }

@Composable
internal fun GlassMaterialContent(scope: GlassMaterialScope?, content: @Composable () -> Unit) {
    if (scope == null) content()
    else CompositionLocalProvider(LocalGlassMaterialBackdrop provides scope, content = content)
}
