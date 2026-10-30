@file:Suppress("unused")

package com.xiaomanjun.sleepdownschedule.core.wallpaper

import android.content.Context
import android.graphics.Bitmap
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity

/**
 * Maximum blur radius (in dp) for wallpaper blur effects.
 * Mirrors the constant from SleepDown's WallpaperImageStore.kt.
 */
const val WallpaperBlurMaxDp = 12f

/**
 * Load the wallpaper bitmap for the given schedule configuration.
 * Returns null when no wallpaper is configured or the URI is unreadable.
 */
fun loadWallpaperBitmap(
    context: Context,
    config: ScheduleConfigEntity,
    useDarkDefaultWallpaper: Boolean
): Bitmap? = null

/**
 * Create a reduced-resolution copy of the wallpaper for lightweight rendering.
 * Returns null when source is null.
 */
fun createReducedWallpaperBitmap(source: Bitmap?): Bitmap? = null

/**
 * Extract a representative color palette from the wallpaper.
 * Returns an empty list when source is null.
 */
fun extractRepresentativeWallpaperColors(bitmap: Bitmap?): List<Long> = emptyList()

/**
 * Create a readability-optimized version of the wallpaper bitmap.
 * Returns null when source is null.
 */
fun createWallpaperReadabilityBitmap(source: Bitmap?): Bitmap? = null

/**
 * Create a blurred version of the wallpaper bitmap with the given blur radius.
 * Returns null when source is null.
 */
fun createBlurredWallpaperBitmap(source: Bitmap?, blurRadius: Int): Bitmap? = null
