package com.lingion.sleepy.ui.glass.ui

import com.xiaomanjun.sleepdownschedule.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.util.lerp
import kotlin.math.sign

data class AdaptiveGlassState(
    val luminance: Float,
    val lightGlass: Boolean,
    val contentColor: Color,
    val surfaceColor: Color,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val blurMultiplier: Float,
    val tintAlpha: Float
)

val LocalAdaptiveGlass = compositionLocalOf {
    adaptiveGlassStateFromLuminance(1f, preferLightGlass = true)
}

@Composable
fun rememberFallbackAdaptiveGlassState(config: ScheduleConfigEntity): AdaptiveGlassState {
    val fallbackLightGlass = glassUsesLightStyle(config)
    val fallbackLuminance = when {
        fallbackLightGlass -> config.wallpaperBrightness.coerceIn(0.56f, 1f)
        else -> config.wallpaperBrightness.coerceIn(0f, 0.46f)
    }
    return adaptiveGlassStateFromLuminance(
        luminance = fallbackLuminance,
        preferLightGlass = fallbackLightGlass
    )
}

fun adaptiveGlassStateFromLuminance(
    luminance: Float,
    preferLightGlass: Boolean? = null,
    quality: Float = 1f
): AdaptiveGlassState {
    val clamped = luminance.coerceIn(0f, 1f)
    val lightGlass = when (preferLightGlass) {
        true -> clamped >= 0.46f
        false -> clamped > 0.56f
        null -> clamped > 0.52f
    }
    val signed = clamped * 2f - 1f
    val l = sign(signed) * signed * signed
    val brightness: Float
    val contrast: Float
    val blurMultiplier: Float
    val tintAlpha: Float
    if (l > 0f) {
        brightness = lerp(0.08f, 0.32f, l)
        contrast = lerp(1.0f, 0.78f, l)
        blurMultiplier = lerp(1.0f, 1.18f, l)
        tintAlpha = lerp(0.14f, 0.22f, l)
    } else {
        brightness = lerp(0.06f, -0.14f, -l)
        contrast = lerp(1.0f, 1.08f, -l)
        blurMultiplier = lerp(1.0f, 0.82f, -l)
        tintAlpha = lerp(0.18f, 0.30f, -l)
    }
    val scaledQuality = quality.coerceIn(0.45f, 1f)
    val contentColor = if (clamped > 0.5f) Color.Black else Color.White
    val surfaceColor =
        if (lightGlass) Color.White.copy(alpha = tintAlpha * scaledQuality)
        else Color(0xFF050505).copy(alpha = tintAlpha * scaledQuality)
    return AdaptiveGlassState(
        luminance = clamped,
        lightGlass = lightGlass,
        contentColor = contentColor,
        surfaceColor = surfaceColor,
        brightness = brightness,
        contrast = contrast,
        saturation = 1.5f,
        blurMultiplier = blurMultiplier,
        tintAlpha = tintAlpha
    )
}
