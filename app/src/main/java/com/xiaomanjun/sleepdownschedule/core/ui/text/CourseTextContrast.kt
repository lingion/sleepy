@file:Suppress("unused")

package com.xiaomanjun.sleepdownschedule.core.ui.text

import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color

private val HomeNotFrozen: State<Boolean> = mutableStateOf(false)
private val CourseTextAtRest: State<Boolean> = mutableStateOf(false)

internal val LocalHomeBackgroundFrozen = compositionLocalOf<State<Boolean>> { HomeNotFrozen }

internal val LocalHomeTextContrastFrozen = compositionLocalOf<State<Boolean>> { HomeNotFrozen }

internal class CourseTextBackground(
    private val isFrozen: () -> Boolean = { false },
    val flatLuminance: Float? = null,
    val sample: (Rect) -> FloatArray? = { null }
) {
    val frozen: Boolean get() = isFrozen()
}

internal val LocalCourseTextBackground = compositionLocalOf<CourseTextBackground?> { null }

internal val LocalCourseTextMotionFrozen = compositionLocalOf<State<Boolean>> { CourseTextAtRest }

internal fun softTextShadowStrength(
    luminances: FloatArray,
    textLuminance: Float,
    textAlpha: Float = 1f,
    currentStrength: Float = 0f
): Float = currentStrength

internal fun courseTextColorForPage(seed: Color, hasWallpaper: Boolean, lightText: Boolean): Color = seed

internal fun courseTextNeedsDarkShadow(color: Color): Boolean = false

internal fun coloredCourseShadowIsWhite(luminances: FloatArray): Boolean = false
