package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.lingion.sleepy.ui.component.sliderFreezePageScroll
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun liquidGlassControlsEnabled(): Boolean =
    LocalUiStyle.current == UiStyle.LiquidGlass && LocalGlassHost.current != null

/** Precision slider: an unbroken thin track and a colored vertical lens. */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    initialValue: Float? = null,
    ghostEpsilon: Float = 0f,
    ghostTag: String = "slider-ghost",
    trackBrush: Brush? = null,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (!liquidGlassControlsEnabled()) {
        androidx.compose.material3.Slider(value, onValueChange, modifier, enabled,
            valueRange = valueRange, steps = steps, onValueChangeFinished = onValueChangeFinished)
        return
    }
    GlassSliderBody(
        valueRange.start..value, { onValueChange(it.endInclusive) }, modifier, enabled,
        valueRange, steps, onValueChangeFinished, false,
        initialValue?.let { valueRange.start..it }, ghostEpsilon, ghostTag, trackBrush, thumbColor,
    )
}

@Composable
internal fun GlassRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    initialValue: ClosedFloatingPointRange<Float>? = null,
) = GlassSliderBody(value, onValueChange, modifier, true, valueRange, steps, null,
    true, initialValue, 0f, "slider-ghost", null, MaterialTheme.colorScheme.primary)

@Composable
private fun GlassSliderBody(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onFinished: (() -> Unit)?,
    range: Boolean,
    initialValue: ClosedFloatingPointRange<Float>?,
    ghostEpsilon: Float,
    ghostTag: String,
    trackBrush: Brush?,
    thumbColor: Color,
) {
    val start = value.start.coerceIn(valueRange.start, valueRange.endInclusive)
    val end = value.endInclusive.coerceIn(start, valueRange.endInclusive)
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(.0001f)
    fun fraction(v: Float) = ((v - valueRange.start) / span).coerceIn(0f, 1f)
    val currentValue by rememberUpdatedState(start..end)
    val update by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onFinished)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val view = LocalView.current
    val backdrop = rememberLayerBackdrop()
    var active by remember { mutableIntStateOf(-1) }
    var startActivation by remember { mutableIntStateOf(0) }
    var endActivation by remember { mutableIntStateOf(0) }
    val startMotion = rememberGlassControlMotion(fraction(start), active == 0, active == 0, startActivation, enabled)
    val endMotion = rememberGlassControlMotion(fraction(end), active == 1, active == 1, endActivation, enabled)
    val primary = thumbColor
    val inactive = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)
    val inset = with(density) { (GlassSliderThumbMetrics.RESTING_WIDTH / 2).dp.toPx() }
    val baseline = initialValue?.takeIf { it.start.isFinite() && it.endInclusive.isFinite() }?.let {
        val first = if (range) it.start.coerceIn(valueRange.start, valueRange.endInclusive) else valueRange.start
        first..it.endInclusive.coerceIn(first, valueRange.endInclusive)
    }
    val ghost = baseline?.takeIf { abs(it.start - start) > ghostEpsilon || abs(it.endInclusive - end) > ghostEpsilon }

    fun snap(v: Float): Float {
        val f = fraction(v)
        return valueRange.start + (if (steps > 0) (f * (steps + 1)).roundToInt().toFloat() / (steps + 1) else f) * span
    }
    fun change(index: Int, v: Float) {
        val now = currentValue
        val next = snap(v)
        update(if (index == 0) next.coerceAtMost(now.endInclusive)..now.endInclusive
            else now.start..next.coerceAtLeast(if (range) now.start else valueRange.start))
    }
    Layout(
        modifier = modifier.fillMaxWidth().height(48.dp).alpha(if (enabled) 1f else .38f)
            .sliderFreezePageScroll()
            .pointerInput(enabled, rtl, valueRange, steps, range) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    fun at(x: Float): Float {
                        val physical = ((x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        return valueRange.start + (if (rtl) 1f - physical else physical) * span
                    }
                    val downValue = at(down.position.x)
                    val now = currentValue
                    active = if (range && abs(downValue - now.start) < abs(downValue - now.endInclusive)) 0 else 1
                    if (active == 0) startActivation++ else endActivation++
                    var lastTick = (fraction(if (active == 0) now.start else now.endInclusive) * (if (steps > 0) steps + 1 else 40)).roundToInt()
                    LiquidGlassHaptics.selectionClick(view)
                    // A touch on the thumb keeps its center under the same finger offset.
                    val selectedValue = if (active == 0) now.start else now.endInclusive
                    val grabOffset = if (abs(downValue - selectedValue) / span * size.width <= 24.dp.toPx()) downValue - selectedValue else 0f
                    change(active, downValue - grabOffset)
                    var released = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!pointer.pressed) { released = true; break }
                            val target = at(pointer.position.x) - grabOffset
                            if (range && now.start == now.endInclusive && target != now.start) {
                                active = if (target < now.start) 0 else 1
                            }
                            change(active, target)
                            val tick = (fraction(snap(target)) * (if (steps > 0) steps + 1 else 40)).roundToInt()
                            if (tick != lastTick) { LiquidGlassHaptics.selectionClick(view); lastTick = tick }
                            pointer.consume()
                        }
                    } finally {
                        active = -1
                        if (released) finish?.invoke()
                    }
                }
            },
        content = {
            // This sibling records only ordinary pixels; neither thumb is inside its source.
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                Canvas(Modifier.fillMaxSize()) {
                    fun x(position: Float): Float {
                        val f = position.coerceIn(0f, 1f)
                        return inset + (size.width - inset * 2) * if (rtl) 1f - f else f
                    }
                    val trackHeight = 4.dp.toPx()
                    val top = (size.height - trackHeight) / 2
                    drawRoundRect(inactive, Offset(inset, top), Size(size.width - 2 * inset, trackHeight), CornerRadius(trackHeight / 2))
                    if (trackBrush != null) {
                        drawRoundRect(trackBrush, Offset(inset, top), Size(size.width - 2 * inset, trackHeight), CornerRadius(trackHeight / 2))
                    } else {
                        val x1 = x(if (range) startMotion.position.value else 0f)
                        val x2 = x(endMotion.position.value)
                        drawRoundRect(primary, Offset(minOf(x1, x2), top), Size(abs(x2 - x1), trackHeight), CornerRadius(trackHeight / 2))
                    }
                }
                if (ghost != null) {
                    Canvas(Modifier.fillMaxSize().testTag(ghostTag)) {
                        val w = GlassSliderThumbMetrics.RESTING_WIDTH.dp.toPx()
                        val h = GlassSliderThumbMetrics.RESTING_HEIGHT.dp.toPx()
                        fun gx(v: Float): Float {
                            val f = fraction(v)
                            return inset + (size.width - 2 * inset) * if (rtl) 1f - f else f
                        }
                        if (range) {
                            val x1 = gx(ghost.start)
                            val x2 = gx(ghost.endInclusive)
                            val trackHeight = 4.dp.toPx()
                            drawRoundRect(primary.copy(alpha = .25f), Offset(minOf(x1, x2), (size.height - trackHeight) / 2),
                                Size(abs(x2 - x1), trackHeight), CornerRadius(trackHeight / 2))
                        }
                        val values = if (range) listOf(ghost.start, ghost.endInclusive) else listOf(ghost.endInclusive)
                        for (v in values) drawRoundRect(primary.copy(alpha = .3f), Offset(gx(v) - w / 2, (size.height - h) / 2), Size(w, h), CornerRadius(w / 2, h / 2))
                    }
                }
            }
            for (index in (if (range) 0 else 1)..1) {
                val current = if (index == 0) start else end
                val accessibleRange = if (index == 0) valueRange.start..end else (if (range) start else valueRange.start)..valueRange.endInclusive
                GlassSliderThumb(
                    trackBackdrop = backdrop,
                    pressedOverride = active == index,
                    colorOverride = primary,
                    enabled = enabled,
                    motionOverride = if (index == 0) startMotion else endMotion,
                    modifier = Modifier.testTag(if (index == 0) "glass-slider-start" else "glass-slider-end")
                        .semantics {
                            if (!enabled) disabled()
                            progressBarRangeInfo = ProgressBarRangeInfo(current, accessibleRange, steps)
                            setProgress { target ->
                                if (!enabled) false else { change(index, target); finish?.invoke(); true }
                            }
                        }
                        .onKeyEvent {
                            if (!enabled || it.type != KeyEventType.KeyDown) false else {
                                val direction = when (it.key) {
                                    Key.DirectionRight -> if (rtl) -1 else 1
                                    Key.DirectionLeft -> if (rtl) 1 else -1
                                    Key.DirectionUp -> 1
                                    Key.DirectionDown -> -1
                                    else -> 0
                                }
                                if (direction == 0) false else {
                                    change(index, current + direction * span / (if (steps > 0) steps + 1 else 100)); finish?.invoke(); true
                                }
                            }
                        }.focusable(enabled),
                )
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val track = measurables[0].measure(Constraints.fixed(width, height))
        val thumbs = measurables.drop(1).map { it.measure(Constraints()) }
        layout(width, height) {
            track.place(0, 0)
            thumbs.forEachIndexed { i, thumb ->
                val index = if (range) i else 1
                val f = (if (index == 0) startMotion else endMotion).position.value
                val x = inset + (width - 2 * inset) * (if (rtl) 1f - f else f)
                thumb.placeWithLayer((x - thumb.width / 2f).roundToInt(), (height - thumb.height) / 2)
            }
        }
    }
}
