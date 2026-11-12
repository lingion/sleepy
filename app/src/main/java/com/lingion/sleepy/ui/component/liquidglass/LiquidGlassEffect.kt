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
 */
internal fun BackdropEffectScope.liquidGlass(
    context: Context,
    style: LiquidGlassStyle,
    glow: LiquidGlassGlow = LiquidGlassGlow.None,
    outline: Path? = null,
    dualContours: Pair<Path, Path>? = null,
    goo: LiquidGlassShader.Goo? = null,
    rim: LiquidGlassRim? = null,
    veil: Color = Color.Transparent,
    sampledBlurPx: Float = 0f,
) {
    if (!isRuntimeShaderSupported()) {
        val effectiveStyle = LiquidGlassStyle.standard(style)
        if (effectiveStyle.blur > 0.0) {
            val requestedPx = effectiveStyle.blur.toFloat() * density
            val remainderPx = kotlin.math.sqrt((requestedPx * requestedPx - sampledBlurPx * sampledBlurPx).coerceAtLeast(0f))
            if (remainderPx > 0f) blur(remainderPx)
        }
        vibrancy()
        return
    }

    try {
        val shaderSource = LiquidGlassShader.shaderSource(context)
        val currentSize = size
        if (currentSize.width <= 0f || currentSize.height <= 0f) return

        val corners = extractCornerRadii(currentSize)
        val sizeDp = Size(currentSize.width / density, currentSize.height / density)
        val hazeAtlas = if (style.haze > 0.0) {
            LiquidGlassShader.hazeAtlas(
                sizePx = currentSize,
                effectOriginPx = effectOffset,
                hazeBlurPx = style.hazeBlur.toFloat() * density,
                sampledBlurPx = sampledBlurPx,
                minimumPaddingPx = padding,
            ).also { padding = it.paddingPx }
        } else null
        val paneOrigin = hazeAtlas?.sourceOriginPx ?: effectOffset

        val sampling = LiquidGlassShader.sampling(
            originPx = if (hazeAtlas != null) Offset.Zero else effectOffset,
            texelsPerDp = density,
            clampPx = hazeAtlas?.clearBoundsPx ?: Rect(0f, 0f, currentSize.width, currentSize.height),
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
            atlasSizePx = hazeAtlas?.sizePx ?: currentSize,
            sampling = sampling,
            heldFrostTexelPx = sampledBlurPx,
            hazeSampling = hazeSampling,
            outline = outline,
            dualContours = dualContours,
            goo = goo,
            rim = rim,
            veil = veil,
            glow = glow,
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
            if (hazeAtlas != null) {
                inputRenderEffect = createLiquidGlassHazeAtlasEffect(
                    atlas = hazeAtlas,
                    clearShader = obtainRuntimeShader("LiquidGlassClearAtlas", LiquidGlassShader.HAZE_ATLAS_SHADER).asAndroidRuntimeShader(),
                    hazeShader = obtainRuntimeShader("LiquidGlassHazeAtlas", LiquidGlassShader.HAZE_ATLAS_SHADER).asAndroidRuntimeShader(),
                ).asComposeRenderEffect()
            }
            effect(materialEffect.asComposeRenderEffect())
        }
    } catch (e: Throwable) {
        android.util.Log.w("LiquidGlassEffect", "RuntimeShader fallback to blur+vibrancy", e)
        val effectiveStyle = LiquidGlassStyle.standard(style)
        if (effectiveStyle.blur > 0.0) {
            val requestedPx = effectiveStyle.blur.toFloat() * density
            val remainderPx = kotlin.math.sqrt((requestedPx * requestedPx - sampledBlurPx * sampledBlurPx).coerceAtLeast(0f))
            if (remainderPx > 0f) blur(remainderPx)
        }
        vibrancy()
    }
}

/** Two GPU branches over the same retained backdrop; no Bitmap readback or CPU blur. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun createLiquidGlassHazeAtlasEffect(
    atlas: LiquidGlassShader.HazeAtlas,
    clearShader: android.graphics.RuntimeShader,
    hazeShader: android.graphics.RuntimeShader,
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
    val clear = packed(clearShader, atlas.clearBoundsPx)
    val gaussian = if (atlas.remainingBlurPx > 0f) android.graphics.RenderEffect.createBlurEffect(
        atlas.remainingBlurPx, atlas.remainingBlurPx, android.graphics.Shader.TileMode.CLAMP,
    ) else null
    val haze = packed(hazeShader, atlas.hazeBoundsPx, gaussian)
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
