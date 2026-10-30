package com.lingion.sleepy.ui.glass.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.xiaomanjun.sleepdownschedule.core.ui.text.CourseTextBackground
import com.xiaomanjun.sleepdownschedule.core.ui.text.LocalCourseTextMotionFrozen
import com.xiaomanjun.sleepdownschedule.core.ui.text.LocalHomeBackgroundFrozen
import com.xiaomanjun.sleepdownschedule.core.ui.text.LocalHomeTextContrastFrozen
import com.xiaomanjun.sleepdownschedule.core.wallpaper.LocalHomeReadability
import com.xiaomanjun.sleepdownschedule.core.wallpaper.sampleVisibleWallpaperColors

/** Estimate the tinted, blurred surface from the cached wallpaper; never read back the GPU. */
@Composable
internal fun rememberCourseTextBackground(
    base: Color,
    tintAlpha: Float,
    blurred: Boolean,
    blurPx: Float,
    outline: Boolean,
    expanded: Boolean,
    flatBackground: Color? = null,
    ready: Boolean,
    cardBounds: () -> Rect?,
    surfaceDimAlpha: Float = 0f
): CourseTextBackground {
    val wallpaper = LocalHomeReadability.current
    val backgroundFrozen = LocalHomeBackgroundFrozen.current
    val cardMotionFrozen = LocalCourseTextMotionFrozen.current
    val textContrastFrozen = LocalHomeTextContrastFrozen.current
    // Motion pauses sampling; it does not change the wallpaper or tint. Keep the same
    // background identity and label caches across freeze/resume instead of recomposing
    // all course text twice per switch and resampling unchanged resting bounds.
    return remember(wallpaper, base, tintAlpha, blurred, blurPx, outline, expanded, ready,
        flatBackground, surfaceDimAlpha, backgroundFrozen, cardMotionFrozen, textContrastFrozen) {
        val surfaceDim = LiquidCourseCardSurfaceTone.copy(alpha = surfaceDimAlpha.coerceIn(0f, 1f))
        fun surfaceLuminance(color: Color) = surfaceDim.compositeOver(color).luminance()
        val flatLuminance = flatBackground?.let {
            surfaceLuminance(base.copy(alpha = tintAlpha.coerceIn(0f, 1f)).compositeOver(it))
        }
        CourseTextBackground({ backgroundFrozen.value || cardMotionFrozen.value || textContrastFrozen.value }, flatLuminance) { windowBounds ->
            // Flat cards have an exact composite; no wallpaper/position read is needed, even
            // when transparency is low or the card has not received its first layout callback.
            if (flatLuminance != null) return@CourseTextBackground floatArrayOf(flatLuminance)
            if (!ready) return@CourseTextBackground null
            val bounds = windowBounds.translate(-wallpaper.rootOffsetInWindow)
            val colors = sampleVisibleWallpaperColors(wallpaper,
                if (blurred) bounds.inflate(blurPx * 0.5f) else bounds)
                ?: return@CourseTextBackground if (tintAlpha >= 0.85f) floatArrayOf(surfaceLuminance(base)) else null
            val mean = Color(colors.map { it.red }.average().toFloat(),
                colors.map { it.green }.average().toFloat(), colors.map { it.blue }.average().toFloat())
            val card = cardBounds()
            val fraction = if (card != null && card.height > 0f)
                ((windowBounds.center.y - card.top) / card.height).coerceIn(0f, 1f) else 0.5f
            val tintWeight = if (outline) {
                val stops = if (expanded) listOf(0f to 0.2f, 0.48f to 0.2f, 0.68f to 0.48f,
                    0.82f to 0.84f, 0.92f to 0.88f, 1f to 1f)
                else listOf(0f to 0.2f, 0.62f to 0.2f, 0.82f to 0.48f,
                    0.90f to 0.84f, 0.96f to 0.88f, 1f to 1f)
                val end = stops.indexOfFirst { it.first >= fraction }.coerceAtLeast(1)
                val (a, av) = stops[end - 1]
                val (b, bv) = stops[end]
                av + (bv - av) * ((fraction - a) / (b - a))
            } else 1f
            val tint = base.copy(alpha = (tintAlpha * tintWeight).coerceIn(0f, 1f))
            colors.map { source ->
                val softened = if (blurred) Color(
                    source.red * 0.3f + mean.red * 0.7f,
                    source.green * 0.3f + mean.green * 0.7f,
                    source.blue * 0.3f + mean.blue * 0.7f
                ) else source
                surfaceLuminance(tint.compositeOver(softened))
            }.toFloatArray()
        }
    }
}
