package com.lingion.sleepy.ui.component.liquidglass

import android.content.Context
import android.os.Build
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
    goo: LiquidGlassShader.Goo? = null,
    rim: LiquidGlassRim? = null,
    veil: Color = Color.Transparent,
) {
    if (!isRuntimeShaderSupported()) {
        val effectiveStyle = LiquidGlassStyle.standard(style)
        if (effectiveStyle.blur > 0.0) {
            blur(effectiveStyle.blur.toFloat() * density)
        }
        vibrancy()
        return
    }

    try {
        val shaderSource = LiquidGlassShader.shaderSource(context)
        val shader = obtainRuntimeShader("LiquidGlassShader", shaderSource)

        val currentSize = size
        if (currentSize.width <= 0f || currentSize.height <= 0f) return

        val corners = extractCornerRadii(currentSize)
        val sizeDp = Size(currentSize.width / density, currentSize.height / density)

        val sampling = LiquidGlassShader.sampling(
            originPx = effectOffset,
            texelsPerDp = density,
            clampPx = Rect(0f, 0f, currentSize.width, currentSize.height),
        )
        val hazeSampling = if (style.haze > 0.0) sampling else null

        val uniforms = LiquidGlassShader.build(
            size = sizeDp,
            corners = corners,
            style = style,
            originPx = effectOffset,
            density = density,
            stretch = 1f,
            atlasSizePx = currentSize,
            sampling = sampling,
            hazeSampling = hazeSampling,
            outline = outline,
            goo = goo,
            rim = rim,
            veil = veil,
            glow = glow,
        )

        uniforms.forEach { (name, values) ->
            shader.setFloatUniform(name, values)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val composeRenderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(
                shader.asAndroidRuntimeShader(),
                "uTexture",
            ).asComposeRenderEffect()
            effect(composeRenderEffect)
        }
    } catch (e: Throwable) {
        android.util.Log.w("LiquidGlassEffect", "RuntimeShader fallback to blur+vibrancy", e)
        val effectiveStyle = LiquidGlassStyle.standard(style)
        if (effectiveStyle.blur > 0.0) {
            blur(effectiveStyle.blur.toFloat() * density)
        }
        vibrancy()
    }
}

private fun BackdropEffectScope.extractCornerRadii(size: Size): LiquidGlassCornerRadii {
    val maxRadius = size.minDimension / 2f
    return when (val sh = shape) {
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
