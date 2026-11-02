package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo

/** A structural foreground pane. Optical material is added only after capture is verified. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val host = LocalGlassHost.current
    Box(if (host == null) modifier else modifier.then(GlassPaneElement(host)), content = content)
}

internal data class GlassPaneElement(val host: GlassHostState) : ModifierNodeElement<GlassPaneNode>() {
    override fun create() = GlassPaneNode(host)
    override fun update(node: GlassPaneNode) { node.setHost(host) }
    override fun InspectorInfo.inspectableProperties() { name = "glassForeground" }
}

internal class GlassPaneNode(private var host: GlassHostState) :
    Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    private var foreground: GraphicsLayer? = null
    private var coordinates: LayoutCoordinates? = null
    private val toHost = Matrix()

    fun setHost(next: GlassHostState) {
        if (next === host) return
        if (isAttached) host.panes.remove(this)
        host.requestDraw()
        host = next
        if (isAttached) host.panes.add(this)
        host.requestDraw()
    }

    override fun onAttach() {
        host.panes.add(this)
        host.requestDraw()
    }

    override fun ContentDrawScope.draw() {
        val layer = foreground ?: requireGraphicsContext().createGraphicsLayer().also { foreground = it }
        layer.record { this@draw.drawContent() }
        // Deliberately DO NOT draw this layer here. A captured source can never reference it.
        host.requestDraw()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        host.requestDraw()
    }

    fun drawInHost(scope: DrawScope, root: LayoutCoordinates) {
        val local = coordinates?.takeIf { it.isAttached } ?: return
        val content = foreground ?: return
        if (!transformBetween(local, root, toHost)) return
        // Respect clipping imposed by scroll containers rather than lifting offscreen rows.
        val visible = try { root.localBoundingBoxOf(local, clipBounds = true) }
        catch (_: IllegalArgumentException) { return }
        if (visible.isEmpty) return
        with(scope) {
            clipRect(visible.left, visible.top, visible.right, visible.bottom) {
                withTransform({ transform(toHost) }) { drawLayer(content) }
            }
        }
    }

    override fun onDetach() {
        host.panes.remove(this)
        host.requestDraw()
        foreground?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        foreground = null
        coordinates = null
    }
}
