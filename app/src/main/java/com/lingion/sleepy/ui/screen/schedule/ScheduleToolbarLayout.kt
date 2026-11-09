package com.lingion.sleepy.ui.screen.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp

/** Keep the week navigator centered; reserve space for both sides before sharing a row. */
@Composable
internal fun ScheduleToolbarLayout(
    modifier: Modifier = Modifier,
    start: @Composable () -> Unit,
    center: @Composable () -> Unit,
    end: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = { Box { start() }; Box { center() }; Box { end() } },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val (left, middle, right) = measurables.map { it.measure(loose) }
        val gap = 8.dp.roundToPx()
        val width = constraints.maxWidth
        val oneRow = middle.width + 2 * (maxOf(left.width, right.width) + gap) <= width
        val actionsFit = left.width + right.width + gap <= width
        val actionHeight = if (actionsFit) maxOf(left.height, right.height) else left.height + gap + right.height
        val height = if (oneRow) maxOf(actionHeight, middle.height) else actionHeight + gap + middle.height
        layout(width, constraints.constrainHeight(height)) {
            if (oneRow) {
                left.placeRelative(0, (height - left.height) / 2)
                middle.placeRelative((width - middle.width) / 2, (height - middle.height) / 2)
                right.placeRelative(width - right.width, (height - right.height) / 2)
            } else {
                left.placeRelative(0, if (actionsFit) (actionHeight - left.height) / 2 else 0)
                right.placeRelative(width - right.width, if (actionsFit) (actionHeight - right.height) / 2 else left.height + gap)
                middle.placeRelative((width - middle.width) / 2, actionHeight + gap)
            }
        }
    }
}
