package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.component.sleepyPopupEnter
import com.lingion.sleepy.ui.component.sleepyPopupExit
import kotlin.math.abs
import kotlin.math.roundToInt

/** App-wide DeviceHub-style slider geometry, independent of theme and input mechanics. */
object GlassSliderThumbMetrics {
    const val SINGLE_DIAMETER = 32f
    const val RANGE_DIAMETER = 24f
    const val TRACK_HEIGHT = 8f
    const val TICK_DIAMETER = 2f
    const val TICK_OFFSET = 14f
    const val TOUCH_HEIGHT = 48f
    const val LIFT_SCALE = 1.55f

    fun diameter(range: Boolean): Float = if (range) RANGE_DIAMETER else SINGLE_DIAMETER
}

/** Linear shared geometry; discrete snapping belongs to the existing slider state. */
internal fun sliderVisualX(width: Float, fraction: Float, inset: Float, rtl: Boolean): Float =
    inset + (width - 2f * inset).coerceAtLeast(0f) *
        if (rtl) 1f - fraction.coerceIn(0f, 1f) else fraction.coerceIn(0f, 1f)

/**
 * A continuous rounded rail, white circular original-value ghosts and dots below the rail.
 * Material passes its already-inset track width; glass passes the full control plus inset.
 * Neither path adds a Material thumb gap, stop marker or in-rail tick.
 */
@Composable
internal fun AppSliderTrack(
    startFraction: () -> Float,
    endFraction: () -> Float,
    tickCount: Int,
    modifier: Modifier = Modifier,
    inset: Dp = 0.dp,
    range: Boolean = false,
    enabled: Boolean = true,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    trackBrush: Brush? = null,
    ghost: ClosedFloatingPointRange<Float>? = null,
    ghostTag: String = "slider-ghost",
) {
    val primary = activeColor
    val inactive = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)
    val ticks = MaterialTheme.colorScheme.onSurface.copy(alpha = .18f)
    var lastGhost by remember { mutableStateOf<ClosedFloatingPointRange<Float>?>(null) }
    ghost?.let { lastGhost = it }
    Box(modifier.fillMaxWidth().height(GlassSliderThumbMetrics.TOUCH_HEIGHT.dp)) {
        Canvas(Modifier.fillMaxWidth().padding(horizontal = inset)
            .height(GlassSliderThumbMetrics.TRACK_HEIGHT.dp).align(Alignment.Center).testTag("slider-track")) {
            val rtl = layoutDirection == LayoutDirection.Rtl
            fun x(fraction: Float) = sliderVisualX(size.width, fraction, 0f, rtl)
            val corner = CornerRadius(size.height / 2f)
            val alpha = if (enabled) 1f else .38f
            if (trackBrush != null) {
                // Logical hue direction follows the same RTL transform as the thumb.
                if (rtl) {
                    withTransform({ scale(-1f, 1f, center) }) {
                        drawRoundRect(trackBrush, cornerRadius = corner, alpha = alpha)
                    }
                } else drawRoundRect(trackBrush, cornerRadius = corner, alpha = alpha)
            } else {
                drawRoundRect(inactive.copy(alpha = inactive.alpha * alpha), cornerRadius = corner)
                val first = x(startFraction())
                val last = x(endFraction())
                drawRoundRect(primary.copy(alpha = primary.alpha * alpha), Offset(minOf(first, last), 0f),
                    Size(abs(last - first), size.height), corner)
            }
        }
        Canvas(Modifier.fillMaxWidth().padding(horizontal = inset)
            .height(GlassSliderThumbMetrics.TICK_DIAMETER.dp).align(Alignment.Center)
            .offset(y = GlassSliderThumbMetrics.TICK_OFFSET.dp).testTag("slider-ticks")) {
            val rtl = layoutDirection == LayoutDirection.Rtl
            val alpha = if (enabled) 1f else .38f
            // Drawing density is independent of snapping precision and bounded by actual width.
            val fractions = sliderTickFractions(tickCount, size.width, 12.dp.toPx())
            fractions.forEach { fraction ->
                drawCircle(ticks.copy(alpha = ticks.alpha * alpha), radius = size.height / 2f,
                    center = Offset(sliderVisualX(size.width, fraction, 0f, rtl), size.height / 2f))
            }
        }
        AnimatedVisibility(
            visible = ghost != null,
            enter = sleepyPopupEnter(),
            exit = sleepyPopupExit(),
            modifier = Modifier.fillMaxSize().testTag(ghostTag),
        ) {
            val retained = lastGhost
            if (retained != null) Canvas(Modifier.fillMaxSize().testTag("$ghostTag-canvas")) {
                val padding = inset.toPx()
                val rtl = layoutDirection == LayoutDirection.Rtl
                fun x(fraction: Float) = sliderVisualX(size.width, fraction, padding, rtl)
                if (range) {
                    val first = x(retained.start)
                    val last = x(retained.endInclusive)
                    val height = GlassSliderThumbMetrics.TRACK_HEIGHT.dp.toPx()
                    drawRoundRect(primary.copy(alpha = .25f), Offset(minOf(first, last), (size.height - height) / 2f),
                        Size(abs(last - first), height), CornerRadius(height / 2f))
                }
                val diameter = GlassSliderThumbMetrics.diameter(range).dp.toPx()
                val values = if (range) listOf(retained.start, retained.endInclusive) else listOf(retained.endInclusive)
                values.forEach { drawSliderGhost(Offset(x(it), size.height / 2f), diameter, primary) }
            }
        }
    }
}

internal fun DrawScope.drawSliderGhost(center: Offset, diameter: Float, primary: Color) {
    drawCircle(Color.White.copy(alpha = .4f), diameter / 2f, center)
    drawCircle(primary.copy(alpha = .3f), diameter / 2f, center, style = Stroke(1.dp.toPx()))
}

/** Subsample real stops evenly while retaining both ends; never allocate per business step. */
internal fun sliderTickFractions(requestedCount: Int, width: Float, minimumSpacing: Float): List<Float> {
    val requested = requestedCount.coerceAtLeast(2)
    val capacity = (width.coerceAtLeast(0f) / minimumSpacing.coerceAtLeast(1f)).toInt().coerceAtLeast(1) + 1
    val count = minOf(requested, capacity)
    return List(count) { index ->
        (index.toDouble() * (requested - 1) / (count - 1)).roundToInt().toFloat() / (requested - 1)
    }
}
