package com.lingion.sleepy.ui.glass.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.effect
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.lingion.sleepy.ui.glass.GlassBackdropDomain
import com.lingion.sleepy.ui.glass.GlassMaterialRole
import com.lingion.sleepy.ui.glass.GlassMaterialSpec
import com.lingion.sleepy.ui.glass.rememberGlassSurfaceDescriptor
import com.lingion.sleepy.ui.glass.sleepDownPlainGlassSurface

// On devices without the native operator, Chromium retains blur + alpha-mask composition.
internal val LocalLegacyProgressiveBlur = staticCompositionLocalOf { false }

enum class ProgressiveBlurDirection {
    TopToBottom,
    BottomToTop,
    LeftToRight,
    RightToLeft,
    RailThreeWay
}

@Composable
fun ProgressiveBackdropBlur(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    tintColor: Color,
    height: Dp,
    blurRadius: Dp = 12.dp,
    tintIntensity: Float = 0.18f,
    domain: GlassBackdropDomain = GlassBackdropDomain.Content,
    direction: ProgressiveBlurDirection = ProgressiveBlurDirection.TopToBottom,
    topMaskFadeStart: Float = 0.45f,
    topMaskFadeEnd: Float = 1f,
    topTintFadeStart: Float = 0.35f,
    topTintFadeEnd: Float = 1f,
    radiusFadeStart: Float = 0f,
    fallbackTintStops: List<Pair<Float, Color>>
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .progressiveBackdropBlur(
                backdrop = backdrop,
                tintColor = tintColor,
                blurRadius = blurRadius,
                tintIntensity = tintIntensity,
                domain = domain,
                direction = direction,
                topMaskFadeStart = topMaskFadeStart,
                topMaskFadeEnd = topMaskFadeEnd,
                topTintFadeStart = topTintFadeStart,
                topTintFadeEnd = topTintFadeEnd,
                radiusFadeStart = radiusFadeStart,
                fallbackTintStops = fallbackTintStops
            )
    )
}

@Composable
fun Modifier.progressiveBackdropBlur(
    backdrop: Backdrop?,
    tintColor: Color,
    blurRadius: Dp = 12.dp,
    tintIntensity: Float = 0.18f,
    domain: GlassBackdropDomain = GlassBackdropDomain.Content,
    direction: ProgressiveBlurDirection = ProgressiveBlurDirection.TopToBottom,
    topMaskFadeStart: Float = 0.45f,
    topMaskFadeEnd: Float = 1f,
    topTintFadeStart: Float = 0.35f,
    topTintFadeEnd: Float = 1f,
    radiusFadeStart: Float = 0f,
    fallbackTintStops: List<Pair<Float, Color>>
): Modifier {
    // Nexio's performance path uses a single Gaussian blur plus a gradient mask instead
    // of the variable-radius sampling and denoise shaders. Keep the same tint/fade inputs.
    val useLegacyBlur = LocalLegacyProgressiveBlur.current ||
        com.lingion.sleepy.ui.glass.performance.effectiveAppMaterialPolicy().simplifiedProgressiveBlur
    val nativeBlur = com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeProgressiveBlur.current
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backdrop != null) {
        val material = remember(blurRadius, tintIntensity) {
            // Pure progressive blur: strip the default highlight/shadow/inner-shadow decorations
            // so the gradient never adds a whitish/bright layer over the sampled content.
            GlassMaterialSpec.simpleBlur(blurRadius).copy(
                surfaceAlpha = tintIntensity.coerceIn(0f, 1f),
                borderAlpha = 0f,
                highlightAlpha = 0f,
                shadowAlpha = 0f,
                innerShadowAlpha = 0f
            )
        }
        val descriptor = rememberGlassSurfaceDescriptor(
            debugLabel = "ProgressiveBackdropBlur",
            domain = domain,
            materialRole = GlassMaterialRole.SimpleBlur,
            sceneKey = "progressive-backdrop-blur"
        )
        val blurShapeBlock: () -> Shape = remember { { RectangleShape } }
        val blurEffects: BackdropEffectScope.() -> Unit = remember(
            blurRadius, direction, tintColor, tintIntensity,
            topMaskFadeStart, topMaskFadeEnd, topTintFadeStart, topTintFadeEnd, radiusFadeStart, useLegacyBlur, nativeBlur
        ) {
            {
                if (nativeBlur == null && direction == ProgressiveBlurDirection.TopToBottom && !useLegacyBlur) {
                    // True variable-radius blur, sampled at full resolution. Backdrop 2 owns
                    // the shader cache per node, so there is no process-global shader registry.
                    nexioProgressiveBlur(blurRadius.toPx(), tintColor, tintIntensity, radiusFadeStart)
                } else {
                if (nativeBlur != null) {
                    val vertical = direction == ProgressiveBlurDirection.TopToBottom || direction == ProgressiveBlurDirection.BottomToTop
                    val increasing = direction == ProgressiveBlurDirection.BottomToTop ||
                        direction == ProgressiveBlurDirection.LeftToRight || direction == ProgressiveBlurDirection.RailThreeWay
                    val radius = blurRadius.toPx()
                    nativeBlur.create(if (increasing) 0f else radius, if (increasing) radius else 0f, vertical)
                        ?.let { effect(it) }
                } else blur(blurRadius.toPx())
                runtimeShaderEffect(
                    "ProgressiveBackdropBlur_${direction.name}",
                    progressiveBlurShaderSource(direction),
                    "content"
                ) {
                    setFloatUniform("size", size.width, size.height)
                    setFloatUniform("contentOrigin", padding, padding)
                    setColorUniform("tint", tintColor)
                    setFloatUniform("tintIntensity", tintIntensity.coerceIn(0f, 1f))
                    setFloatUniform("maskFadeStart", topMaskFadeStart.coerceIn(0f, 1f))
                    setFloatUniform("maskFadeEnd", topMaskFadeEnd.coerceAtLeast(topMaskFadeStart + 0.01f))
                    setFloatUniform("tintFadeStart", topTintFadeStart.coerceIn(0f, 1f))
                    setFloatUniform("tintFadeEnd", topTintFadeEnd.coerceAtLeast(topTintFadeStart + 0.01f))
                }
                }
            }
        }
        sleepDownPlainGlassSurface(
            usage = com.lingion.sleepy.ui.glass.performance.MaterialUsage.SCENE_BLUR,
            backdrop = backdrop,
            descriptor = descriptor,
            material = material,
            shape = blurShapeBlock,
            effects = blurEffects
        )
    } else {
        if (direction == ProgressiveBlurDirection.RailThreeWay) {
            return drawWithCache {
                val radius = maxOf(size.width, size.height * 0.58f)
                val brush = Brush.radialGradient(
                    *fallbackTintStops.toTypedArray(),
                    center = Offset(size.width, size.height / 2f),
                    radius = radius
                )
                onDrawBehind { drawRect(brush) }
            }
        }
        val brush = when (direction) {
            ProgressiveBlurDirection.TopToBottom -> Brush.verticalGradient(*fallbackTintStops.toTypedArray())
            ProgressiveBlurDirection.BottomToTop -> Brush.verticalGradient(
                *fallbackTintStops.map { (stop, color) -> 1f - stop to color }.reversed().toTypedArray()
            )
            ProgressiveBlurDirection.LeftToRight -> Brush.horizontalGradient(*fallbackTintStops.toTypedArray())
            ProgressiveBlurDirection.RightToLeft -> Brush.horizontalGradient(
                *fallbackTintStops.map { (stop, color) -> 1f - stop to color }.reversed().toTypedArray()
            )
            ProgressiveBlurDirection.RailThreeWay -> error("Handled above")
        }
        background(brush)
    }
}

internal fun progressiveBlurShaderSource(direction: ProgressiveBlurDirection): String {
    val maskExpression = when (direction) {
        ProgressiveBlurDirection.TopToBottom -> """
            float mask = 1.0 - smoothstep(maskFadeStart, maskFadeEnd, y);
            float tintMask = 1.0 - smoothstep(tintFadeStart, tintFadeEnd, y);
        """.trimIndent()

        ProgressiveBlurDirection.BottomToTop -> """
            float mask = smoothstep(0.0, 0.55, y);
            float tintMask = smoothstep(0.0, 0.65, y);
        """.trimIndent()

        ProgressiveBlurDirection.LeftToRight -> """
            float mask = smoothstep(maskFadeStart, maskFadeEnd, x);
            float tintMask = smoothstep(tintFadeStart, tintFadeEnd, x);
        """.trimIndent()

        ProgressiveBlurDirection.RightToLeft -> """
            float mask = 1.0 - smoothstep(maskFadeStart, maskFadeEnd, x);
            float tintMask = 1.0 - smoothstep(tintFadeStart, tintFadeEnd, x);
        """.trimIndent()

        ProgressiveBlurDirection.RailThreeWay -> """
            float horizontalMask = smoothstep(maskFadeStart, maskFadeEnd, x);
            float topMask = smoothstep(0.0, 0.14, y);
            float bottomMask = 1.0 - smoothstep(0.86, 1.0, y);
            float mask = horizontalMask * topMask * bottomMask;

            float tintHorizontalMask = smoothstep(tintFadeStart, tintFadeEnd, x);
            float tintTopMask = smoothstep(0.02, 0.18, y);
            float tintBottomMask = 1.0 - smoothstep(0.82, 0.98, y);
            float tintMask = tintHorizontalMask * tintTopMask * tintBottomMask;
        """.trimIndent()
    }
    return """
        uniform shader content;
        uniform float2 size;
        uniform float2 contentOrigin;
        layout(color) uniform half4 tint;
        uniform float tintIntensity;
        uniform float maskFadeStart;
        uniform float maskFadeEnd;
        uniform float tintFadeStart;
        uniform float tintFadeEnd;

        half4 main(float2 coord) {
            float x = (coord.x - contentOrigin.x) / size.x;
            float y = (coord.y - contentOrigin.y) / size.y;
            $maskExpression
            half4 tintPremul = half4(tint.rgb * tint.a, tint.a);
            half4 color = content.eval(coord);
            color = tintPremul * (tintIntensity * tintMask) + color * (1.0 - tint.a * tintIntensity * tintMask);
            return color * mask;
        }
    """.trimIndent()
}
