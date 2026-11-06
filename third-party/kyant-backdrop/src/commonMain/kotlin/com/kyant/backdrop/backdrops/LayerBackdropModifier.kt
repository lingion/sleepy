// Modified for SleepDown on 2026-09-08; upstream 2.0.0, Apache-2.0 (see README.md).
package com.kyant.backdrop.backdrops

import androidx.compose.runtime.snapshots.Snapshot

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import com.kyant.backdrop.internal.recordLayer
import com.kyant.backdrop.BackdropLayerDiagnostics
import com.kyant.backdrop.BackdropDiagnostics
import androidx.compose.ui.unit.toIntSize
import com.kyant.backdrop.internal.BackdropRecordingCache

/**
 * A non-null key promises that all recorded pixels are unchanged. Return null for dynamic
 * content. The caller must include asynchronous image changes and animation state in the key.
 * Size, density, direction, source replacement and detach are invalidated by the node itself.
 */
fun Modifier.layerBackdrop(backdrop: LayerBackdrop, recordKey: (() -> Any?)? = null): Modifier =
    this then LayerBackdropElement(backdrop, recordKey)

private class LayerBackdropElement(
    val backdrop: LayerBackdrop,
    val recordKey: (() -> Any?)?
) : ModifierNodeElement<LayerBackdropNode>() {

    override fun create(): LayerBackdropNode {
        return LayerBackdropNode(backdrop, recordKey)
    }

    override fun update(node: LayerBackdropNode) {
        if (node.backdrop != backdrop) {
            node.backdrop.layerCoordinates = null
            node.backdrop = backdrop
            node.markNeedsRecord()
        }
        node.recordKey = recordKey
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "layerBackdrop"
        properties["backdrop"] = backdrop
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LayerBackdropElement) return false

        if (backdrop != other.backdrop) return false
        if (recordKey != other.recordKey) return false

        return true
    }

    override fun hashCode(): Int {
        return 31 * backdrop.hashCode() + (recordKey?.hashCode() ?: 0)
    }
}

private class LayerBackdropNode(
    var backdrop: LayerBackdrop,
    var recordKey: (() -> Any?)?
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {
    private val layerDiagnostics = BackdropLayerDiagnostics("ProducerRecord")
    private val recordingCache = BackdropRecordingCache()

    fun markNeedsRecord() = recordingCache.clear()

    override fun ContentDrawScope.draw() {
        drawContent()
        val key = recordKey?.invoke()
        val dimensions = size.toIntSize()
        if (recordingCache.needsRecord(key, dimensions, density, fontScale, layoutDirection)) {
            recordLayer(backdrop.graphicsLayer) { backdrop.onDraw(this@draw) }
            recordingCache.recorded(key, dimensions, density, fontScale, layoutDirection)
            layerDiagnostics.recorded(backdrop.graphicsLayer.size)
            // Publishing a recording must not subscribe this producer to its own revision.
            // A read/write in draw otherwise keeps invalidating retained home RenderNodes,
            // including while the course editor is sampling the frozen scene.
            Snapshot.withoutReadObservation { backdrop.contentVersion++ }
        } else {
            BackdropDiagnostics.event("ProducerRecord.Reused")
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            backdrop.layerCoordinates = coordinates
        }
    }

    override fun onDetach() {
        markNeedsRecord()
        backdrop.layerCoordinates = null
    }
}
