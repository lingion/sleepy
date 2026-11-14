package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.ui.graphics.Path

/** A retained shape entry owns this dp path; callers must not mutate it. */
class LiquidGlassCachedOutlineProfile(
    val path: Path,
    private val exactContour: () -> LiquidGlassExactContour?,
) {
    private val profile by lazy { liquidGlassContourProfile(path) }

    // Descriptor identity keeps a cached profile tied to the actual CPU outline that produced it.
    fun matchingProfile(outline: Path, contour: LiquidGlassExactContour?): LiquidGlassContourProfile? =
        if (outline === path && contour != null && contour === exactContour()) profile else null
}
