package com.lingion.sleepy.ui.component.liquidglass

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.asAndroidRuntimeShader
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.effect
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.shapes.RoundedRectangularShape

/**
 * 将 FLClash 1:1 液态玻璃 AGSL 着色器装配至 [BackdropEffectScope]。
 *
 * 在 Android 13+ (API 33+) 运行 GPU 硬件着色器, 涵盖:
 * - 真实凸透镜/凹透镜折射曲率 (uOptics.x / uCurves)
 * - 色散与色差分离 (uEdge.y)
 * - 边缘发丝高光与反光 (uLight / uRim)
 * - 触控径向光晕 (uGlow / uGlowSpread)
 * - 雾化层与双重采样 (uHaze / uHazeSample / uHazeClamp)
 *
 * 在不支持 RuntimeShader 的设备上, 优雅降级为高保真高斯模糊与鲜艳度滤镜。
 * [inputBlurPx] optionally prefilters the supplied backdrop to a total input sigma;
 * it cannot undo [sampledBlurPx] or bypass an inherited parent material.
 */
fun BackdropEffectScope.liquidGlass(
    context: Context,
    style: LiquidGlassStyle,
    glow: LiquidGlassGlow = LiquidGlassGlow.None,
    outline: Path? = null,
    dualContours: Pair<Path, Path>? = null,
    goo: LiquidGlassShader.Goo? = null,
    rim: LiquidGlassRim? = null,
    veil: Color = Color.Transparent,
    sampledBlurPx: Float = 0f,
    cachedOutlineProfile: LiquidGlassCachedOutlineProfile? = null,
    // Opt-in total input sigma, in texture pixels. Null retains the original path.
    inputBlurPx: Float? = null,
) {
    // Backdrop observes effects on attachment, before its first measured DrawScope.
    // Wait for real geometry; Size.Unspecified would otherwise create NaN corners.
    val currentSize = size
    if (!currentSize.width.isFinite() || !currentSize.height.isFinite() ||
        currentSize.width <= 0f || currentSize.height <= 0f ||
        !density.isFinite() || density <= 0f) return

    if (!isRuntimeShaderSupported()) {
        val effectiveStyle = LiquidGlassStyle.standard(style)
        if (effectiveStyle.blur > 0.0) {
            if (inputBlurPx == null) {
                val requestedPx = effectiveStyle.blur.toFloat() * density
                val remainderPx = kotlin.math.sqrt((requestedPx * requestedPx - sampledBlurPx * sampledBlurPx).coerceAtLeast(0f))
                if (remainderPx > 0f) blur(remainderPx)
            } else {
                val requestedSigmaPx = androidBlurRadiusToSigma(effectiveStyle.blur.toFloat() * density)
                val remainder = liquidGlassInputBlur(sampledBlurPx, requestedSigmaPx)
                if (remainder.additionalBlurPx > 0f) blur(androidBlurSigmaToRadius(remainder.additionalBlurPx))
            }
        }
        vibrancy()
        return
    }

    // A failed opt-in graph must leave neither its input blur nor atlas packing
    // behind when the original uniform-blur fallback is assembled.
    val previousInputEffect = if (inputBlurPx != null) inputRenderEffect else null
    val previousMaterialEffect = if (inputBlurPx != null) renderEffect else null
    val previousPadding = if (inputBlurPx != null) padding else 0f
    try {
        val inputBlur = inputBlurPx?.let { liquidGlassInputBlur(sampledBlurPx, it) }
        val heldBlurPx = inputBlur?.heldBlurPx ?: sampledBlurPx
        val additionalInputBlurPx = inputBlur?.additionalBlurPx ?: 0f
        val shaderSource = LiquidGlassShader.shaderSource(context)
        val actualOutline = shape.createOutline(currentSize, layoutDirection, this)
        val circular = circularGlassCornerRadii(actualOutline, density)
        val exactContours = (shape as? LiquidGlassExactContourShape)?.exactContoursFor(actualOutline)
        val corners = circular ?: extractCornerRadii(currentSize)
        val sizeDp = Size(currentSize.width / density, currentSize.height / density)
        val hazeAtlas = if (style.haze > 0.0) {
            val hazeBlurPx = if (inputBlurPx != null)
                androidBlurRadiusToSigma(style.hazeBlur.toFloat() * density)
            else style.hazeBlur.toFloat() * density
            val atlas = LiquidGlassShader.hazeAtlas(
                sizePx = currentSize,
                effectOriginPx = effectOffset,
                hazeBlurPx = hazeBlurPx,
                sampledBlurPx = heldBlurPx,
                minimumPaddingPx = padding,
            )
            val inputPadding = if (additionalInputBlurPx > 0f)
                checkNotNull(inputBlur).capturePaddingPx(atlas.remainingBlurPx) else 0f
            (if (inputPadding > atlas.paddingPx) LiquidGlassShader.hazeAtlas(
                sizePx = currentSize,
                effectOriginPx = effectOffset,
                hazeBlurPx = hazeBlurPx,
                sampledBlurPx = heldBlurPx,
                minimumPaddingPx = inputPadding,
            ) else atlas).also { padding = it.paddingPx }
        } else null
        val inputPadding = if (hazeAtlas == null && additionalInputBlurPx > 0f) {
            padding = maxOf(padding, checkNotNull(inputBlur).capturePaddingPx())
            padding
        } else 0f
        val inputOrigin = if (inputPadding > 0f) effectOffset + Offset(inputPadding, inputPadding) else effectOffset
        // The legacy path keeps its original pane-sized arithmetic. Opt-in inputs
        // sample the actual complete recording, even without an additional blur pass.
        val inputGeometry = if (inputBlurPx != null && hazeAtlas == null) liquidGlassInputGeometry(
            effectOriginPx = effectOffset,
            captureSizePx = captureSize,
            paddingPx = padding,
            allocationPaddingPx = captureAllocationPadding,
        ) else null
        val paneOrigin = hazeAtlas?.sourceOriginPx ?: inputGeometry?.originPx ?: inputOrigin
        val inputBounds = Rect(inputPadding, inputPadding,
            inputPadding + currentSize.width, inputPadding + currentSize.height)
        val inputSize = if (inputPadding > 0f) Size(
            kotlin.math.ceil(currentSize.width + inputPadding * 2f),
            kotlin.math.ceil(currentSize.height + inputPadding * 2f),
        ) else currentSize

        val sampling = LiquidGlassShader.sampling(
            originPx = if (hazeAtlas != null) Offset.Zero else inputGeometry?.originPx ?: inputOrigin,
            texelsPerDp = density,
            clampPx = hazeAtlas?.clearBoundsPx ?: inputGeometry?.clampPx ?: inputBounds,
        )
        val hazeSampling = hazeAtlas?.let {
            LiquidGlassShader.sampling(
                originPx = it.hazeBoundsPx.topLeft,
                texelsPerDp = density,
                clampPx = it.hazeBoundsPx,
            )
        }

        val uniforms = LiquidGlassShader.build(
            size = sizeDp,
            corners = corners,
            style = style,
            originPx = paneOrigin,
            density = density,
            stretch = 1f,
            atlasSizePx = hazeAtlas?.sizePx ?: inputGeometry?.sizePx ?: inputSize,
            sampling = sampling,
            heldFrostTexelPx = heldBlurPx,
            frostRadiusSampling = inputBlurPx != null,
            hazeSampling = hazeSampling,
            outline = outline,
            dualContours = dualContours,
            goo = goo,
            rim = rim,
            veil = veil,
            glow = glow,
            circularCorners = circular != null,
            exactContours = exactContours,
            cachedOutlineProfile = cachedOutlineProfile,
        )

        val shader = obtainRuntimeShader("LiquidGlassShader", shaderSource)
        uniforms.forEach { (name, values) ->
            shader.setFloatUniform(name, values)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val materialEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(
                shader.asAndroidRuntimeShader(),
                "uTexture",
            )
            val additionalInputRadiusPx = if (additionalInputBlurPx > 0f)
                androidBlurSigmaToRadius(additionalInputBlurPx) else 0f
            val prefilter = if (additionalInputRadiusPx > 0f) android.graphics.RenderEffect.createBlurEffect(
                additionalInputRadiusPx, additionalInputRadiusPx, android.graphics.Shader.TileMode.CLAMP,
            ) else null
            val localInputEffect = if (hazeAtlas != null) {
                createLiquidGlassHazeAtlasEffect(
                    atlas = hazeAtlas,
                    clearShader = obtainRuntimeShader("LiquidGlassClearAtlas", LiquidGlassShader.HAZE_ATLAS_SHADER).asAndroidRuntimeShader(),
                    hazeShader = obtainRuntimeShader("LiquidGlassHazeAtlas", LiquidGlassShader.HAZE_ATLAS_SHADER).asAndroidRuntimeShader(),
                    prefilter = prefilter,
                    sigmaUnits = inputBlurPx != null,
                )
            } else prefilter
            if (inputBlurPx != null) {
                // The backend rasterizes this input graph in its own RenderNode
                // before the material shader reads it. Blur precedes atlas packing.
                inputRenderEffect = if (localInputEffect != null && previousInputEffect != null)
                    android.graphics.RenderEffect.createChainEffect(
                        localInputEffect, previousInputEffect.asAndroidRenderEffect(),
                    ).asComposeRenderEffect()
                else localInputEffect?.asComposeRenderEffect() ?: previousInputEffect
            } else if (localInputEffect != null) {
                inputRenderEffect = localInputEffect.asComposeRenderEffect()
            }
            effect(materialEffect.asComposeRenderEffect())
        }
    } catch (e: Throwable) {
        android.util.Log.w("LiquidGlassEffect", "RuntimeShader fallback to blur+vibrancy", e)
        if (inputBlurPx != null) {
            inputRenderEffect = previousInputEffect
            renderEffect = previousMaterialEffect
            padding = previousPadding
        }
        val effectiveStyle = LiquidGlassStyle.standard(style)
        if (effectiveStyle.blur > 0.0) {
            if (inputBlurPx == null) {
                val requestedPx = effectiveStyle.blur.toFloat() * density
                val remainderPx = kotlin.math.sqrt((requestedPx * requestedPx - sampledBlurPx * sampledBlurPx).coerceAtLeast(0f))
                if (remainderPx > 0f) blur(remainderPx)
            } else {
                val requestedSigmaPx = androidBlurRadiusToSigma(effectiveStyle.blur.toFloat() * density)
                val remainder = liquidGlassInputBlur(sampledBlurPx, requestedSigmaPx)
                if (remainder.additionalBlurPx > 0f) blur(androidBlurSigmaToRadius(remainder.additionalBlurPx))
            }
        }
        vibrancy()
    }
}

/** Exact circular capsules/discs; other contours retain their separate optical path. */
fun circularGlassCornerRadii(outline: Outline, density: Float): LiquidGlassCornerRadii? {
    val rect = (outline as? Outline.Rounded)?.roundRect ?: return null
    val corners = listOf(rect.topLeftCornerRadius, rect.topRightCornerRadius,
        rect.bottomRightCornerRadius, rect.bottomLeftCornerRadius)
    val radius = corners.first().x
    if (density <= 0f || !density.isFinite() || rect.width <= 0f || rect.height <= 0f ||
        corners.any { !it.x.isFinite() || !it.y.isFinite() || it.x < 0f || it.x != it.y || it.x != radius }) return null
    // The circular SDF represents uniform arcs exactly. An arbitrary asymmetric
    // RoundRect can cross the center axes, and needs a different distance model.
    val actual = minOf(radius, rect.width / 2f, rect.height / 2f) / density
    return LiquidGlassCornerRadii(actual, actual, actual, actual)
}

/** Two GPU branches over the same retained backdrop; no Bitmap readback or CPU blur. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun createLiquidGlassHazeAtlasEffect(
    atlas: LiquidGlassShader.HazeAtlas,
    clearShader: android.graphics.RuntimeShader,
    hazeShader: android.graphics.RuntimeShader,
    prefilter: android.graphics.RenderEffect? = null,
    // Opt-in only: the original atlas path supplies Android radius units.
    sigmaUnits: Boolean = false,
): android.graphics.RenderEffect {
    fun packed(
        shader: android.graphics.RuntimeShader,
        slot: Rect,
        input: android.graphics.RenderEffect? = null,
    ): android.graphics.RenderEffect {
        shader.setFloatUniform("uSlot", slot.left, slot.top, slot.width, slot.height)
        val dx = slot.left - atlas.sourceOriginPx.x
        val dy = slot.top - atlas.sourceOriginPx.y
        // Let the native Offset imagefilter track input bounds. A shader-only coordinate
        // translation would hide the required source region from Skia's filter graph.
        val positioned = if (input != null) android.graphics.RenderEffect.createOffsetEffect(dx, dy, input)
            else android.graphics.RenderEffect.createOffsetEffect(dx, dy)
        return android.graphics.RenderEffect.createChainEffect(
            android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "uTexture"), positioned,
        )
    }
    val clear = packed(clearShader, atlas.clearBoundsPx, prefilter)
    val hazeRadiusPx = if (sigmaUnits) {
        // As for the input prefilter, do not replace a tiny remainder with the
        // radius API's minimum positive sigma (which would overshoot the target).
        if (atlas.remainingBlurPx > 0.5f) androidBlurSigmaToRadius(atlas.remainingBlurPx) else 0f
    } else atlas.remainingBlurPx
    val gaussian = if (hazeRadiusPx > 0f) android.graphics.RenderEffect.createBlurEffect(
        hazeRadiusPx, hazeRadiusPx, android.graphics.Shader.TileMode.CLAMP,
    ) else null
    val hazeInput = if (gaussian != null && prefilter != null)
        android.graphics.RenderEffect.createChainEffect(gaussian, prefilter) else gaussian ?: prefilter
    val haze = packed(hazeShader, atlas.hazeBoundsPx, hazeInput)
    // The slots never overlap, so SRC_OVER preserves the clear texels without color mixing.
    return android.graphics.RenderEffect.createBlendModeEffect(clear, haze, android.graphics.BlendMode.SRC_OVER)
}

private fun BackdropEffectScope.extractCornerRadii(size: Size): LiquidGlassCornerRadii {
    val maxRadius = size.minDimension / 2f
    return when (val sh = shape) {
        is FlClashGlassShape -> {
            val r = sh.radiusDp.coerceAtMost(maxRadius / density)
            LiquidGlassCornerRadii(r, r, r, r)
        }
        is RoundedRectangularShape -> {
            val c = sh.corners(size, layoutDirection, this)
            LiquidGlassCornerRadii(
                c.topLeft / density,
                c.topRight / density,
                c.bottomRight / density,
                c.bottomLeft / density,
            )
        }
        is AbsoluteRoundedCornerShape -> {
            val tl = sh.topStart.toPx(size, this).coerceAtMost(maxRadius) / density
            val tr = sh.topEnd.toPx(size, this).coerceAtMost(maxRadius) / density
            val br = sh.bottomEnd.toPx(size, this).coerceAtMost(maxRadius) / density
            val bl = sh.bottomStart.toPx(size, this).coerceAtMost(maxRadius) / density
            LiquidGlassCornerRadii(tl, tr, br, bl)
        }
        is CornerBasedShape -> {
            val isLtr = layoutDirection == LayoutDirection.Ltr
            val tl = (if (isLtr) sh.topStart else sh.topEnd).toPx(size, this).coerceAtMost(maxRadius) / density
            val tr = (if (isLtr) sh.topEnd else sh.topStart).toPx(size, this).coerceAtMost(maxRadius) / density
            val br = (if (isLtr) sh.bottomEnd else sh.bottomStart).toPx(size, this).coerceAtMost(maxRadius) / density
            val bl = (if (isLtr) sh.bottomStart else sh.bottomEnd).toPx(size, this).coerceAtMost(maxRadius) / density
            LiquidGlassCornerRadii(tl, tr, br, bl)
        }
        else -> {
            val r = maxRadius / density
            LiquidGlassCornerRadii(r, r, r, r)
        }
    }
}
