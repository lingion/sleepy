package com.lingion.sleepy.ui.component.liquidglass

import kotlin.math.abs

/**
 * Single-Slider held endpoint position, separate from its snapped business value.
 *
 * Native70's stable public positions at 335pt width/298pt travel and Native68's
 * held center-minus-m41 observations agree with this retrospectively selected
 * rubber family. This is a held rule, not a release-velocity model. At other
 * widths our responsive policy keeps the same physical dp response and 13.62dp
 * bound; those widths have not been independently measured on native Slider.
 */
internal fun nativeSliderVisualPosition(
    rawFraction: Float,
    businessFraction: Float,
    controlWidthDp: Float,
    thumbTravelDp: Float,
): Float {
    if (!businessFraction.isFinite()) return 0f
    if (!rawFraction.isFinite() || !controlWidthDp.isFinite() || controlWidthDp <= 0f ||
        !thumbTravelDp.isFinite() || thumbTravelDp <= 0f) return businessFraction
    val overflow = when {
        rawFraction < 0f -> rawFraction
        rawFraction > 1f -> rawFraction - 1f
        else -> return businessFraction
    }
    val excessDp = abs(overflow) * controlWidthDp
    // This form stays finite even when the positive multiplication overflows.
    val shiftDp = 13.62f * (1f - 1f / (1f + .62425f * excessDp / 13.62f))
    val logicalShift = shiftDp / thumbTravelDp
    return businessFraction + if (overflow < 0f) -logicalShift else logicalShift
}
