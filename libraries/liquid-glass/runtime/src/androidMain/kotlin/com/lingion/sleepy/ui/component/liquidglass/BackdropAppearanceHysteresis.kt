package com.lingion.sleepy.ui.component.liquidglass

/**
 * Retains a caller-chosen appearance between two explicit source-luma thresholds.
 * This is an engineering policy, not a UIKit classifier or a native timing model.
 * The owner must reset this object when replacing the source or its appearance context.
 */
class BackdropAppearanceHysteresis(
    initialDark: Boolean,
    val enterLightLuma: Float,
    val enterDarkLuma: Float,
) {
    init {
        require(enterDarkLuma.isFinite() && enterLightLuma.isFinite() &&
            enterDarkLuma >= 0f && enterDarkLuma < enterLightLuma && enterLightLuma <= 1f)
    }

    var dark: Boolean = initialDark
        private set

    /** Invalid or failed samples retain the last accepted state. */
    fun accept(luma: Float): Boolean {
        if (!luma.isFinite() || luma !in 0f..1f) return dark
        dark = if (dark) luma < enterLightLuma else luma <= enterDarkLuma
        return dark
    }
}
