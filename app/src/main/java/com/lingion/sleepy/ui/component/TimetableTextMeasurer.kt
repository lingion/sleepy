package com.lingion.sleepy.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer

// A timetable measures labels and start/end times several times for column and cell geometry.
// Keep a bounded cache across tab disposal; rememberTextMeasurer recreates it when the font
// resolver, density/font scale or layout direction changes.
internal const val TimetableTextCacheSize = 128
internal val LocalTimetableTextMeasurer = compositionLocalOf<TextMeasurer?> { null }

@Composable
internal fun rememberTimetableTextMeasurer(): TextMeasurer =
    LocalTimetableTextMeasurer.current ?: rememberTextMeasurer(cacheSize = TimetableTextCacheSize)
