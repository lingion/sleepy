package com.lingion.sleepy.ui.screen.schedule

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun ScheduleWeekPager(
    state: PagerState,
    draggingCourse: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable PagerScope.(Int) -> Unit,
) {
    HorizontalPager(
        state = state,
        // Extra pages are needed for edge dragging, not before showing the selected week.
        // Ordinary swipes use the pager's directional prefetch and visible-page layout.
        beyondViewportPageCount = if (draggingCourse) 1 else 0,
        modifier = modifier,
        pageContent = content,
    )
}
