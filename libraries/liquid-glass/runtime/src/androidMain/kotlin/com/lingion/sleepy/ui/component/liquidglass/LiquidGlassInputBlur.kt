package com.lingion.sleepy.ui.component.liquidglass

import kotlin.math.ceil
import kotlin.math.sqrt

// AOSP android-16.0.0_r1: libs/hwui/utils/Blur.cpp, convertRadiusToSigma.
// RenderEffect.cpp applies this conversion before SkImageFilters::Blur.
private const val ANDROID_BLUR_SIGMA_SCALE = 0.57735f

/** Android RenderEffect/Compose BlurEffect radius, in pixels, to Skia Gaussian sigma. */
fun androidBlurRadiusToSigma(radiusPx: Float): Float {
    require(radiusPx.isFinite() && radiusPx >= 0f)
    return if (radiusPx > 0f) ANDROID_BLUR_SIGMA_SCALE * radiusPx + 0.5f else 0f
}

/**
 * Inverse of Android's radius conversion. A positive radius cannot express sigma in (0,.5].
 * Reject that interval instead of reporting blur which the input graph never applies.
 */
fun androidBlurSigmaToRadius(sigmaPx: Float): Float {
    require(sigmaPx.isFinite() && (sigmaPx == 0f || sigmaPx > 0.5f))
    if (sigmaPx == 0f) return 0f
    val radius = (sigmaPx.toDouble() - 0.5) / ANDROID_BLUR_SIGMA_SCALE.toDouble()
    require(radius <= Float.MAX_VALUE.toDouble()) { "Android blur radius overflows" }
    return radius.toFloat()
}

/** A requested input sigma can add blur, never remove blur already in the backdrop. */
internal data class LiquidGlassInputBlur(
    val additionalBlurPx: Float,
    val heldBlurPx: Float,
) {
    /** Reserve surrounding source for the input and following Gaussian passes. */
    fun capturePaddingPx(followingBlurPx: Float = 0f): Float {
        require(followingBlurPx.isFinite() && followingBlurPx >= 0f)
        val required = ceil(3.0 * (additionalBlurPx.toDouble() + followingBlurPx.toDouble()))
        require(required <= Float.MAX_VALUE.toDouble()) { "Input blur padding overflows" }
        return required.toFloat()
    }
}

internal fun liquidGlassInputBlur(sampledBlurPx: Float, inputBlurPx: Float): LiquidGlassInputBlur {
    require(sampledBlurPx.isFinite() && sampledBlurPx >= 0f)
    require(inputBlurPx.isFinite() && inputBlurPx >= 0f)
    val sampled = sampledBlurPx.toDouble()
    val requested = inputBlurPx.toDouble()
    val additional = sqrt((requested * requested - sampled * sampled).coerceAtLeast(0.0)).toFloat()
    // Android's radius API has no exact positive kernel at these sigmas. Skip a
    // sub-half-pixel remainder and report the unchanged, real input sigma.
    if (additional <= 0.5f) return LiquidGlassInputBlur(0f, sampledBlurPx)
    return LiquidGlassInputBlur(additional, inputBlurPx)
}
