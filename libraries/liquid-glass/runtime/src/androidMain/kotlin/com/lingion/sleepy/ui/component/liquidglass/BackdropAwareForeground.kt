package com.lingion.sleepy.ui.component.liquidglass

import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.Backdrop
import kotlin.math.ceil

/**
 * Explicit empirical response, not a native appearance classifier. Luma refers to the shader's
 * working RGB space. Endpoint colors are converted to sRGB Android color uniforms.
 * [transparentBackground] composes transparent source pixels before measuring their luma.
 */
@Immutable
data class BackdropForegroundResponse(
    val lowBackground: Color,
    val highBackground: Color,
    val lowerLuma: Float,
    val upperLuma: Float,
    val transparentBackground: Color,
) {
    init {
        require(lowerLuma.isFinite() && upperLuma.isFinite() &&
            lowerLuma >= 0f && lowerLuma < upperLuma && upperLuma <= 1f)
        require(transparentBackground.alpha == 1f)
    }
}

/**
 * Repaints this content's alpha mask using a same-frame, all-GPU backdrop response. The caller
 * supplies the original external scene, not a backing that already contains this foreground or
 * its own adaptive material. Badges and other accent pixels must be painted outside this mask.
 * Source-to-consumer transform handling belongs to the supplied [Backdrop]; relative scale or
 * rotation with a LayerBackdrop requires its withTransformCompensation() wrapper. This primitive
 * is intended for small foreground regions, not full-screen offscreen processing.
 *
 * [sampleBoundsPx] is the explicit pane in this modifier's local physical pixels. Its center
 * plus (20%,30%), (80%,30%), (20%,70%) and (80%,70%) determine the response. No sampling radius,
 * threshold, animation, selected color or native defaults are implicit in this primitive.
 * API below 33, unavailable consumer coordinates and invalid sample geometry draw the original
 * content. An empty source is measured against [BackdropForegroundResponse.transparentBackground].
 */
fun Modifier.backdropAwareForeground(
    backdrop: Backdrop,
    response: BackdropForegroundResponse,
    sampleBoundsPx: () -> Rect,
): Modifier = this then BackdropAwareForegroundElement(backdrop, response, sampleBoundsPx)

/** Two disjoint, unscaled integer-pixel slots, packed along the short axis. */
internal data class BackdropForegroundAtlas(
    val contentSizePx: IntSize,
    val atlasSizePx: IntSize,
    val sceneSlot: Rect,
    val maskSlot: Rect,
) {
    companion object {
        fun create(sizePx: Size): BackdropForegroundAtlas {
            require(sizePx.width.isFinite() && sizePx.height.isFinite() && sizePx.width > 0f && sizePx.height > 0f)
            val width = ceil(sizePx.width).toLong()
            val height = ceil(sizePx.height).toLong()
            require(width <= Int.MAX_VALUE / 2 && height <= Int.MAX_VALUE / 2)
            val w = width.toInt()
            val h = height.toInt()
            val horizontal = w <= h
            return BackdropForegroundAtlas(
                contentSizePx = IntSize(w, h),
                atlasSizePx = if (horizontal) IntSize(w * 2, h) else IntSize(w, h * 2),
                sceneSlot = Rect(0f, 0f, w.toFloat(), h.toFloat()),
                maskSlot = if (horizontal) Rect(w.toFloat(), 0f, w * 2f, h.toFloat())
                    else Rect(0f, h.toFloat(), w.toFloat(), h * 2f),
            )
        }
    }
}

internal fun isBackdropForegroundSampleValid(bounds: Rect, sizePx: Size): Boolean =
    bounds.left.isFinite() && bounds.top.isFinite() && bounds.right.isFinite() && bounds.bottom.isFinite() &&
        bounds.width > 0f && bounds.height > 0f && bounds.left >= 0f && bounds.top >= 0f &&
        bounds.right <= sizePx.width && bounds.bottom <= sizePx.height

private data class BackdropAwareForegroundElement(
    val backdrop: Backdrop,
    val response: BackdropForegroundResponse,
    val sampleBoundsPx: () -> Rect,
) : ModifierNodeElement<BackdropAwareForegroundNode>() {
    override fun create() = BackdropAwareForegroundNode(backdrop, response, sampleBoundsPx)

    override fun update(node: BackdropAwareForegroundNode) {
        node.backdrop = backdrop
        node.response = response
        node.sampleBoundsPx = sampleBoundsPx
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "backdropAwareForeground"
        properties["backdrop"] = backdrop
        properties["response"] = response
    }
}

private class BackdropAwareForegroundNode(
    var backdrop: Backdrop,
    var response: BackdropForegroundResponse,
    var sampleBoundsPx: () -> Rect,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode, ObserverModifierNode {
    private var coordinates: LayoutCoordinates? = null
    private var maskLayer: GraphicsLayer? = null
    private var atlasLayer: GraphicsLayer? = null
    private var outputLayer: GraphicsLayer? = null
    private var shader: android.graphics.RuntimeShader? = null

    override fun ContentDrawScope.draw() {
        val target = coordinates?.takeIf { it.isAttached }
        val bounds = sampleBoundsPx()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || target == null ||
            size.width <= 0f || size.height <= 0f || !isBackdropForegroundSampleValid(bounds, size)) {
            drawContent()
            return
        }
        val atlas = BackdropForegroundAtlas.create(size)
        val recordingDensity = Density(density, fontScale)
        val mask = maskLayer ?: newLayer().also { maskLayer = it }
        val input = atlasLayer ?: newLayer().also { atlasLayer = it }
        val output = outputLayer ?: newLayer().also { outputLayer = it }
        val runtimeShader = shader ?: android.graphics.RuntimeShader(FOREGROUND_SHADER).also { shader = it }

        // Keep child drawing at its original local size, not the atlas's enlarged size.
        recordAt(mask, atlas.contentSizePx, recordingDensity) { this@draw.drawContent() }
        // Explicit observation includes a source's mutable origin/revision even when those
        // reads occur inside the independent GraphicsLayer recording.
        observeReads {
            recordAt(input, atlas.atlasSizePx, recordingDensity) {
                clipRect(atlas.sceneSlot.left, atlas.sceneSlot.top, atlas.sceneSlot.right, atlas.sceneSlot.bottom) {
                    with(backdrop) { drawBackdrop(recordingDensity, target, null) }
                }
                clipRect(atlas.maskSlot.left, atlas.maskSlot.top, atlas.maskSlot.right, atlas.maskSlot.bottom) {
                    translate(atlas.maskSlot.left, atlas.maskSlot.top) { drawLayer(mask) }
                }
            }
        }
        runtimeShader.setFloatUniform("uOutputSize", size.width, size.height)
        runtimeShader.setFloatUniform("uMaskOffset", atlas.maskSlot.left, atlas.maskSlot.top)
        runtimeShader.setFloatUniform("uSampleBounds", bounds.left, bounds.top, bounds.width, bounds.height)
        runtimeShader.setFloatUniform("uResponse", response.lowerLuma, response.upperLuma)
        runtimeShader.setColorUniform("uLow", response.lowBackground.toArgb())
        runtimeShader.setColorUniform("uHigh", response.highBackground.toArgb())
        runtimeShader.setColorUniform("uBackground", response.transparentBackground.toArgb())
        output.renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(runtimeShader, "uTexture")
            .asComposeRenderEffect()
        // Complete a separate input RenderNode before arbitrary-slot reads. Applying the
        // effect directly to a clipped recording can shift/cull the second slot.
        recordAt(output, atlas.atlasSizePx, recordingDensity) { drawLayer(input) }
        clipRect(0f, 0f, size.width, size.height) { drawLayer(output) }
    }

    private fun newLayer(): GraphicsLayer = requireGraphicsContext().createGraphicsLayer().apply {
        compositingStrategy = CompositingStrategy.Offscreen
    }

    private fun DrawScope.recordAt(
        layer: GraphicsLayer,
        dimensions: IntSize,
        recordingDensity: Density,
        block: DrawScope.() -> Unit,
    ) {
        layer.record(dimensions) {
            val previousDensity = drawContext.density
            drawContext.density = recordingDensity
            try { block() } finally { drawContext.density = previousDensity }
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        invalidateDraw()
    }

    override fun onObservedReadsChanged() = invalidateDraw()

    override fun onDetach() {
        coordinates = null
        val context = requireGraphicsContext()
        maskLayer?.let(context::releaseGraphicsLayer)
        atlasLayer?.let(context::releaseGraphicsLayer)
        outputLayer?.let(context::releaseGraphicsLayer)
        maskLayer = null
        atlasLayer = null
        outputLayer = null
        shader = null
    }
}

private const val FOREGROUND_SHADER = """
    uniform shader uTexture;
    uniform float2 uOutputSize;
    uniform float2 uMaskOffset;
    uniform float4 uSampleBounds;
    uniform float2 uResponse;
    layout(color) uniform half4 uLow;
    layout(color) uniform half4 uHigh;
    layout(color) uniform half4 uBackground;

    float4 sceneAt(float2 relative) {
        float4 scene = uTexture.eval(uSampleBounds.xy + uSampleBounds.zw * relative);
        return scene + float4(uBackground.rgb, 1.0) * (1.0 - scene.a);
    }

    half4 main(float2 point) {
        if (point.x < 0.0 || point.y < 0.0 ||
            point.x >= uOutputSize.x || point.y >= uOutputSize.y) return half4(0.0);
        float mask = uTexture.eval(point + uMaskOffset).a;
        float4 scene = 0.2 * (sceneAt(float2(0.5, 0.5)) +
            sceneAt(float2(0.2, 0.3)) + sceneAt(float2(0.8, 0.3)) +
            sceneAt(float2(0.2, 0.7)) + sceneAt(float2(0.8, 0.7)));
        float luma = dot(scene.rgb, float3(0.2126, 0.7152, 0.0722));
        half4 color = mix(uLow, uHigh, smoothstep(uResponse.x, uResponse.y, luma));
        half alpha = color.a * mask;
        return half4(color.rgb * alpha, alpha);
    }
"""
