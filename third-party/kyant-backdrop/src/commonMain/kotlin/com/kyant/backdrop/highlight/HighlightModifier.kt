// Modified for SleepDown on 2026-09-08; upstream 2.0.0, Apache-2.0 (see README.md).
package com.kyant.backdrop.highlight

import com.kyant.backdrop.BackdropLayerDiagnostics
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastCoerceAtMost
import com.kyant.backdrop.RuntimeShaderCacheImpl
import com.kyant.backdrop.internal.ShapeProvider
import com.kyant.backdrop.internal.blur
import com.kyant.backdrop.internal.clipOutline
import com.kyant.backdrop.internal.setRuntimeShader
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlin.math.ceil

internal class HighlightElement(
    val shapeProvider: ShapeProvider,
    val highlight: () -> Highlight?
) : ModifierNodeElement<HighlightNode>() {

    override fun create(): HighlightNode {
        return HighlightNode(shapeProvider, highlight)
    }

    override fun update(node: HighlightNode) {
        if (node.shapeProvider != shapeProvider) node.shapeProvider = shapeProvider
        node.highlight = highlight
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "highlight"
        properties["shapeProvider"] = shapeProvider
        properties["highlight"] = highlight
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HighlightElement) return false

        if (shapeProvider != other.shapeProvider) return false
        if (highlight != other.highlight) return false

        return true
    }

    override fun hashCode(): Int {
        var result = shapeProvider.hashCode()
        result = 31 * result + highlight.hashCode()
        return result
    }
}

internal class HighlightNode(
    var shapeProvider: ShapeProvider,
    var highlight: () -> Highlight?
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var highlightLayer: GraphicsLayer? = null

    private val paint =
        Paint().apply {
            style = PaintingStyle.Stroke
        }
    private var clipPath: Path? = null

    private val runtimeShaderCache = RuntimeShaderCacheImpl()

    private var prevStyle: HighlightStyle? = null

    private val layerDiagnostics = BackdropLayerDiagnostics("Highlight")

    private var recordedMaterial: List<Any?>? = null

    override fun ContentDrawScope.draw() {
        if (!shapeProvider.options.enabled()) return drawContent()
        val highlight = highlight()
        if (highlight == null || highlight.width.value <= 0f) {
            return drawContent()
        }

        drawContent()

        val highlightLayer = highlightLayer
        if (highlightLayer != null) {
            val size = size
            val density: Density = this
            val layoutDirection = layoutDirection

            val safeSize =
                IntSize(
                    ceil(size.width).toInt() + 2,
                    ceil(size.height).toInt() + 2
                )

            val bounds = shapeProvider.options.bounds()
            val outline = if (bounds == null) {
                shapeProvider.shape.createOutline(size, layoutDirection, density)
            } else {
                shapeProvider.innerShape.createOutline(bounds.size, layoutDirection, density)
            }
            val clipPath =
                if (outline is Outline.Rounded) {
                    clipPath ?: Path().also { clipPath = it }
                } else {
                    null
                }

            val materialKey = listOf(size, this.density, fontScale, layoutDirection, outline, highlight, bounds)
            val canReuse = shapeProvider.options.cacheDecorations && (highlight.style is HighlightStyle.Default || highlight.style is HighlightStyle.Ambient || highlight.style is HighlightStyle.Plain)
            if (!canReuse || recordedMaterial != materialKey) {
                if (bounds == null) configurePaint(highlight) else {
                    inset(bounds.left, bounds.top, size.width - bounds.right, size.height - bounds.bottom) {
                        configurePaint(highlight)
                    }
                }
    
                highlightLayer.alpha = highlight.alpha
                highlightLayer.blendMode = highlight.style.blendMode
                highlightLayer.record(safeSize) {
                    translate(1f + (bounds?.left ?: 0f), 1f + (bounds?.top ?: 0f)) {
                        val canvas = drawContext.canvas
                        canvas.save()
                        canvas.clipOutline(outline, clipPath)
                        canvas.drawOutline(outline, paint)
                        canvas.restore()
                    }
                }
    
                layerDiagnostics.recorded(highlightLayer.size)
                recordedMaterial = materialKey
            } else {
                com.kyant.backdrop.BackdropDiagnostics.event("Decoration.Reused")
            }
            translate(-1f, -1f) {
                drawLayer(highlightLayer)
            }
        }
    }

    override fun onAttach() {
        layerDiagnostics.created()
        val graphicsContext = requireGraphicsContext()
        highlightLayer = graphicsContext.createGraphicsLayer()
    }

    override fun onDetach() {
        recordedMaterial = null
        val graphicsContext = requireGraphicsContext()
        highlightLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            layerDiagnostics.released()
            highlightLayer = null
        }
        clipPath = null
        runtimeShaderCache.clear()
        prevStyle = null
    }

    private fun DrawScope.configurePaint(highlight: Highlight) {
        paint.color = highlight.style.color
        paint.strokeWidth = ceil(highlight.width.toPx().fastCoerceAtMost(size.minDimension / 2f)) * 2f
        paint.blur(highlight.blurRadius.toPx())
        if (isRuntimeShaderSupported()) {
            val shader =
                with(highlight.style) {
                    createShader(
                        shape = shapeProvider.shape,
                        runtimeShaderCache = runtimeShaderCache
                    )
                }
            paint.setRuntimeShader(shader)
        }
    }
}
