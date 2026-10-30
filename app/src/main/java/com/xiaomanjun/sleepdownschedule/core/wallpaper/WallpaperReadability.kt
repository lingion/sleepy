@file:Suppress("unused")

package com.xiaomanjun.sleepdownschedule.core.wallpaper

import android.graphics.Bitmap
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.IntSize
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.model.DefaultWallpaperStyle

data class HomeReadabilityContext(
    val bitmap: Bitmap? = null,
    val config: ScheduleConfigEntity? = null,
    val rootSize: IntSize = IntSize.Zero,
    val rootOffsetInWindow: Offset = Offset.Zero
)

val LocalHomeReadability = compositionLocalOf { HomeReadabilityContext() }

internal fun sampleVisibleWallpaperColors(
    context: HomeReadabilityContext,
    bounds: Rect,
    columns: Int = 7,
    rows: Int = 5
): List<ComposeColor>? = null

internal fun ScheduleConfigEntity.hasAnyWallpaper(): Boolean =
    wallpaperUri != null || defaultWallpaperStyle != DefaultWallpaperStyle.NONE

internal fun homeFlatBackgroundColor(dark: Boolean): ComposeColor =
    if (dark) ComposeColor(0xFF121212) else ComposeColor(0xFFF5F5F5)
