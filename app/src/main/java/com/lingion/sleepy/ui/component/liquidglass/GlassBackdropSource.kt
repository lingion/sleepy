package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo

/**
 * Explicit ordinary leaf source. Apply only to a known pane-free content node, outside its own
 * clip/opacity modifiers. Its owner supplies depth; this never infers order from callback timing.
 * Do not apply to a parent containing buttons, dialogs or another registered source.
 */
@Composable
fun Modifier.glassBackdropSource(depth: Float): Modifier {
    require(depth.isFinite()) { "Source depth must be finite" }
    val host = LocalGlassHost.current ?: return this
    return then(GlassSourceElement(host, depth))
}

private data class GlassSourceElement(val host: GlassHostState, val depth: Float) : ModifierNodeElement<GlassSourceNode>() {
    override fun create() = GlassSourceNode(host, depth)
    override fun update(node: GlassSourceNode) { node.updateSource(host, depth) }
    override fun InspectorInfo.inspectableProperties() { name = "orderedGlassSource" }
}

private class GlassSourceNode(private var host: GlassHostState, private var depth: Float) :
    Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    private var segment: GlassSourceSegment? = null
    private var coordinates: LayoutCoordinates? = null

    fun updateSource(next: GlassHostState, nextDepth: Float) {
        if (next === host && nextDepth == depth) return
        segment?.let { host.sources.remove(it) }
        host.changed()
        host = next; depth = nextDepth
        segment = segment?.let { GlassSourceSegment(depth, it.layer).also { fresh -> fresh.coordinates = it.coordinates; host.sources.add(fresh) } }
        host.changed()
    }

    override fun ContentDrawScope.draw() {
        val current = segment ?: GlassSourceSegment(depth, requireGraphicsContext().createGraphicsLayer()).also {
            it.coordinates = coordinates
            segment = it; host.sources.add(it)
        }
        current.nativeGeneration
        recordGlassLayer(current.layer) { this@draw.drawContent() }
        drawLayer(current.layer)
        host.changed()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        segment?.coordinates = coordinates
        host.changed()
    }

    override fun onDetach() {
        segment?.let {
            host.sources.remove(it)
            requireGraphicsContext().releaseGraphicsLayer(it.layer)
        }
        segment = null
        host.changed()
    }
}
