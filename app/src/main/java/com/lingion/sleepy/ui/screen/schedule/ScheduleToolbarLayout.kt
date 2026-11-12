package com.lingion.sleepy.ui.screen.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp

internal enum class WeekControlDensity { Full, Grouped }
/** Full spacing and zero internal spacing, before uniform scaling. */
internal data class ToolbarSideWidths(val normal: Dp, val tight: Dp)
internal data class ToolbarSideDensity(val gap: Dp = 8.dp, val scale: Float = 1f)

/** The week navigator always owns the toolbar's exact horizontal center. */
@Composable
internal fun ScheduleToolbarLayout(
    modifier: Modifier = Modifier,
    centerWidths: Map<WeekControlDensity, Dp>,
    startWidths: ToolbarSideWidths,
    endWidths: ToolbarSideWidths,
    start: @Composable (ToolbarSideDensity) -> Unit,
    center: @Composable (WeekControlDensity) -> Unit,
    end: @Composable (ToolbarSideDensity) -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxWidth = Constraints.Infinity)
        var side = ToolbarSideDensity()
        var inset = 12.dp.roundToPx()
        var gap = 8.dp.roundToPx()
        fun sideWidth(widths: ToolbarSideWidths): Int =
            ((widths.tight + (widths.normal - widths.tight) * (side.gap / 8.dp)) * side.scale).roundToPx()
        // Reserve the larger side on BOTH sides, rather than moving the center
        // towards whichever group of actions happens to be shorter.
        fun available() = (width - 2 * (inset + gap + maxOf(sideWidth(startWidths), sideWidth(endWidths)))).coerceAtLeast(0)
        fun fits(mode: WeekControlDensity) = centerWidths.getValue(mode).roundToPx() + 2 <= available()
        var mode = WeekControlDensity.Full.takeIf { fits(it) }
        if (mode == null) {
            // Exhaust margins before changing any control's size or week text.
            for ((edge, between, internal) in listOf(Triple(8, 4, 4), Triple(4, 0, 0), Triple(0, 0, 0))) {
                inset = edge.dp.roundToPx()
                gap = between.dp.roundToPx()
                side = ToolbarSideDensity(gap = internal.dp)
                if (fits(WeekControlDensity.Full)) {
                    mode = WeekControlDensity.Full
                    break
                }
            }
        }
        if (mode == null) {
            // Scale the current appearance's actual action seats together,
            // including undo/redo. Material retains 48dp; compact glass uses 36dp.
            for (scale in listOf(.95f, .9f, .85f, .8f, .75f, .7f, 2f / 3f)) {
                side = ToolbarSideDensity(gap = 0.dp, scale = scale)
                if (fits(WeekControlDensity.Full)) {
                    mode = WeekControlDensity.Full
                    break
                }
            }
        }
        if (mode == null) {
            mode = WeekControlDensity.Grouped
        }
        val left = subcompose("start") { Box { start(side) } }.single().measure(loose)
        val right = subcompose("end") { Box { end(side) } }.single().measure(loose)
        val chosen = mode
        val middle = subcompose("center-$chosen") { Box { center(chosen) } }.single().measure(
            loose.copy(maxWidth = centerWidths.getValue(chosen).roundToPx())
        )
        // Even an exceptionally narrow window stays on one row. Measure whole
        // controls first, then uniformly fit their visual bounds; never squeeze
        // individual labels into wrapping or let the side groups cross center.
        val requiredWidth = middle.width + 2 * maxOf(left.width, right.width) + 2 * gap + 2
        val fitScale = minOf(1f, (width - 2 * inset).coerceAtLeast(0).toFloat() / requiredWidth)
        val height = constraints.constrainHeight(ceil(maxOf(left.height, right.height, middle.height) * fitScale).toInt())
        layout(width, height) {
            fun placeAction(child: androidx.compose.ui.layout.Placeable, leading: Boolean) {
                val onLeft = leading == (layoutDirection == LayoutDirection.Ltr)
                val x = if (onLeft) inset.toFloat() else width - inset - child.width * fitScale
                child.placeWithLayer(0, 0) {
                    translationX = x
                    translationY = (height - child.height * fitScale) / 2
                    scaleX = fitScale
                    scaleY = fitScale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
            placeAction(left, leading = true)
            placeAction(right, leading = false)
            middle.placeWithLayer(0, 0) {
                translationX = (width - middle.width * fitScale) / 2
                translationY = (height - middle.height * fitScale) / 2
                scaleX = fitScale
                scaleY = fitScale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}
