package com.lingion.sleepy.ui.screen.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp

internal enum class WeekControlDensity { Full, NumberOnly, Grouped }
internal data class ToolbarSideWidths(val normal: Dp, val compact: Dp)

/** Try full words, then just the week number, then one capsule; wrap only after all fail. */
@Composable
internal fun ScheduleToolbarLayout(
    modifier: Modifier = Modifier,
    centerWidths: Map<WeekControlDensity, Dp>,
    startWidths: ToolbarSideWidths,
    endWidths: ToolbarSideWidths,
    start: @Composable (compact: Boolean) -> Unit,
    center: @Composable (WeekControlDensity) -> Unit,
    end: @Composable (compact: Boolean) -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        var compact = false
        var inset = 12.dp.roundToPx()
        var gap = 8.dp.roundToPx()
        fun sideWidth(widths: ToolbarSideWidths) = (if (compact) widths.compact else widths.normal).roundToPx()
        fun available() = (width - 2 * inset - sideWidth(startWidths) - sideWidth(endWidths) - 2 * gap).coerceAtLeast(0)
        var mode = WeekControlDensity.Full.takeIf { centerWidths.getValue(it).roundToPx() <= available() }
        if (mode == null) {
            inset = 8.dp.roundToPx()
            gap = 4.dp.roundToPx()
            mode = listOf(WeekControlDensity.Full, WeekControlDensity.NumberOnly).firstOrNull {
                centerWidths.getValue(it).roundToPx() <= available()
            }
        }
        if (mode == null) {
            compact = true
            mode = WeekControlDensity.Grouped.takeIf { centerWidths.getValue(it).roundToPx() <= available() }
        }
        // Compose each live control once. Unplaced trial compositions still expose semantics.
        val left = subcompose("start") { Box { start(compact) } }.single().measure(loose)
        val right = subcompose("end") { Box { end(compact) } }.single().measure(loose)
        val oneRow = mode != null
        val chosen = mode ?: WeekControlDensity.Full
        val innerWidth = (width - 2 * inset).coerceAtLeast(0)
        val middle = subcompose("center-$chosen") { Box { center(chosen) } }.single().measure(
            loose.copy(maxWidth = if (oneRow) available() else innerWidth)
        )
        val actionsFit = left.width + right.width + gap <= innerWidth
        val actionsHeight = if (actionsFit) maxOf(left.height, right.height) else left.height + gap + right.height
        val height = if (oneRow) maxOf(actionsHeight, middle.height) else actionsHeight + 8.dp.roundToPx() + middle.height
        layout(width, constraints.constrainHeight(height)) {
            if (oneRow) {
                left.placeRelative(inset, (height - left.height) / 2)
                right.placeRelative(width - inset - right.width, (height - right.height) / 2)
                val minX = inset + left.width + gap
                val maxX = width - inset - right.width - gap - middle.width
                middle.placeRelative(((width - middle.width) / 2).coerceIn(minX, maxX), (height - middle.height) / 2)
            } else {
                left.placeRelative(inset, if (actionsFit) (actionsHeight - left.height) / 2 else 0)
                right.placeRelative(width - inset - right.width, if (actionsFit) (actionsHeight - right.height) / 2 else left.height + gap)
                middle.placeRelative((width - middle.width) / 2, actionsHeight + 8.dp.roundToPx())
            }
        }
    }
}
