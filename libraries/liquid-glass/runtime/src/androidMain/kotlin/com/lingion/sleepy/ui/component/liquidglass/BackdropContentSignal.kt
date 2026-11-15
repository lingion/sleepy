package com.lingion.sleepy.ui.component.liquidglass

import android.os.Looper

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop

/**
 * Explicit notification for an external source whose retained child layers can change without
 * recording its parent again. Share one signal only among the source leaves of that scene.
 *
 * This does not record pixels, detect arbitrary descendants, or represent presentation time.
 * Notify on the UI thread after a source change is available to draw. Never notify from its
 * optical consumers: their redraws must not feed back into the source. Native drawing and
 * graphicsLayer-only property changes need an explicit notification from their owning source.
 */
class BackdropContentSignal {
    private var version by mutableIntStateOf(0)

    fun notifyContentChanged() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Notify source changes on the UI thread" }
        Snapshot.withoutReadObservation { version++ }
    }

    internal fun observeChanges() { version }
}

/**
 * Retain the returned wrapper alongside [signal]. A sampler observes the signal while drawing
 * this delegate, even when its parent recording still references a changed child RenderNode.
 * The signal is a request to resample, not the delegate's completed recording revision.
 */
fun Backdrop.withContentSignal(signal: BackdropContentSignal): Backdrop = object : Backdrop {
    override val isCoordinatesDependent: Boolean get() = this@withContentSignal.isCoordinatesDependent

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        signal.observeChanges()
        with(this@withContentSignal) { drawBackdrop(density, coordinates, layerBlock) }
    }
}

/**
 * Publishes completed source draw invocations. Place inside each independently retained,
 * pane-free leaf's own graphicsLayer/clip boundary, before its drawing modifiers. A parent
 * containing additional retained children cannot observe those children's later redraws.
 * It neither observes layer-property-only changes nor polls for them. Sources and consumers
 * must already have independent retained layers; this modifier does not create isolation.
 */
fun Modifier.notifyBackdropDraw(signal: BackdropContentSignal): Modifier =
    this then BackdropDrawSignalElement(signal)

private data class BackdropDrawSignalElement(val signal: BackdropContentSignal) : ModifierNodeElement<BackdropDrawSignalNode>() {
    override fun create() = BackdropDrawSignalNode(signal)
    override fun update(node: BackdropDrawSignalNode) { node.signal = signal }
    override fun InspectorInfo.inspectableProperties() { name = "notifyBackdropDraw" }
}

private class BackdropDrawSignalNode(var signal: BackdropContentSignal) : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() {
        drawContent()
        signal.notifyContentChanged()
    }
}
