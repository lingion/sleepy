package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
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
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import com.lingion.sleepy.ui.component.materialSliderGhostX
import com.lingion.sleepy.ui.component.sleepyPopupEnter
import com.lingion.sleepy.ui.component.sleepyPopupExit
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun liquidGlassControlsEnabled(): Boolean =
    LocalUiStyle.current == UiStyle.LiquidGlass && LocalGlassHost.current != null

/** Relative thumb tracking, separate from the shorter visual center travel.
 * UIKit's recorded 335pt slider consumes 10pt once, then changes value by dx / 335.
 * This is a measured threshold, not Android's platform touchSlop.
 */
internal class GlassSliderDragMapping(
    private val downX: Float,
    private val downValue: Float,
    private val controlWidth: Float,
    private val valueSpan: Float,
    private val slop: Float,
    private val rtl: Boolean,
) {
    private var anchorX: Float? = null

    fun valueAt(x: Float): Float {
        val anchor = anchorX ?: run {
            val delta = x - downX
            if (abs(delta) <= slop) return downValue
            (downX + if (delta > 0f) slop else -slop).also { anchorX = it }
        }
        val delta = (x - anchor) / controlWidth.coerceAtLeast(1f) * valueSpan
        return downValue + if (rtl) -delta else delta
    }

    companion object {
        const val START_SLOP_DP = 10f
    }
}

/** The glass appearance has its own rendering; Material retains platform defaults. */
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
    // Existing callers may tint the rail; the shared thumb itself is always white.
    thumbColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (!liquidGlassControlsEnabled()) {
        val state = remember(steps, valueRange) {
            androidx.compose.material3.SliderState(value = value, steps = steps, trackRange = valueRange)
        }
        state.value = value
        val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(.0001f)
        val baseline = initialValue?.takeIf { it.isFinite() }?.coerceIn(valueRange.start, valueRange.endInclusive)
        val ghost = baseline?.takeIf { abs(it - value) > ghostEpsilon }
            ?.let { 0f..((it - valueRange.start) / span).coerceIn(0f, 1f) }
        Box {
            androidx.compose.material3.Slider(
                state = state, onValueChange = onValueChange,
                modifier = modifier
                    .materialSliderGestureOwnership(state, enabled, onValueChange, onValueChangeFinished),
                enabled = enabled,
                onValueChangeFinished = onValueChangeFinished,
            )
            MaterialSliderGhost(ghost, steps, range = false, tag = ghostTag)
        }
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
) {
    if (!liquidGlassControlsEnabled()) {
        val state = remember(steps, valueRange) {
            androidx.compose.material3.RangeSliderState(
                startValue = value.start, endValue = value.endInclusive,
                steps = steps, trackRange = valueRange,
            )
        }
        state.startValue = value.start
        state.endValue = value.endInclusive
        val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(.0001f)
        fun fraction(v: Float) = ((v - valueRange.start) / span).coerceIn(0f, 1f)
        val baseline = initialValue?.takeIf { it.start.isFinite() && it.endInclusive.isFinite() }?.let {
            val first = it.start.coerceIn(valueRange.start, valueRange.endInclusive)
            first..it.endInclusive.coerceIn(first, valueRange.endInclusive)
        }
        val ghost = baseline?.takeIf { it.start != state.startValue || it.endInclusive != state.endValue }
            ?.let { fraction(it.start)..fraction(it.endInclusive) }
        Box {
            androidx.compose.material3.RangeSlider(
                state = state, onValueChange = onValueChange,
                modifier = modifier.fillMaxWidth(),
            )
            MaterialSliderGhost(ghost, steps, range = true, tag = "slider-ghost")
        }
        return
    }
    GlassSliderBody(value, onValueChange, modifier, true, valueRange, steps, null,
        true, initialValue, 0f, "slider-ghost", null, MaterialTheme.colorScheme.primary)
}

/** Baseline decoration is separate so it cannot replace Material's track or handles. */
@Composable
private fun BoxScope.MaterialSliderGhost(
    value: ClosedFloatingPointRange<Float>?,
    steps: Int,
    range: Boolean,
    tag: String,
) {
    var retained by remember { mutableStateOf<ClosedFloatingPointRange<Float>?>(null) }
    if (value != null) retained = value
    val primary = androidx.compose.material3.MaterialTheme.colorScheme.primary
    AnimatedVisibility(
        visible = value != null, enter = sleepyPopupEnter(), exit = sleepyPopupExit(),
        modifier = Modifier.matchParentSize().testTag(tag),
    ) {
        val ghost = retained
        if (ghost != null) Canvas(Modifier.fillMaxSize().testTag("$tag-canvas")) {
            fun x(frac: Float) = materialSliderGhostX(
                size.width, if (layoutDirection == LayoutDirection.Rtl) 1f - frac else frac,
                steps, 4.dp.toPx(), 16.dp.toPx(),
            )
            val startX = x(ghost.start)
            val endX = x(ghost.endInclusive)
            val trackHeight = 16.dp.toPx()
            if (range && abs(endX - startX) >= 1f) drawRoundRect(
                primary.copy(alpha = .25f), Offset(minOf(startX, endX), (size.height - trackHeight) / 2),
                androidx.compose.ui.geometry.Size(abs(endX - startX), trackHeight), CornerRadius(trackHeight / 2),
            )
            val handleWidth = 4.dp.toPx()
            val handleHeight = 44.dp.toPx()
            for (center in if (range) floatArrayOf(startX, endX) else floatArrayOf(endX)) drawRoundRect(
                primary.copy(alpha = .40f), Offset(center - handleWidth / 2, (size.height - handleHeight) / 2),
                androidx.compose.ui.geometry.Size(handleWidth, handleHeight), CornerRadius(handleWidth / 2),
            )
        }
    }
}

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
    // Both thumb sizes share the same silhouette and isotropic lift.
    val startMotion = rememberGlassControlMotion(fraction(start), active == 0, active == 0, startActivation, enabled, GlassControlMotionSpec.NativeSlider)
    val endMotion = rememberGlassControlMotion(fraction(end), active == 1, active == 1, endActivation, enabled, GlassControlMotionSpec.NativeSlider)
    val restingWidth = GlassSliderThumbMetrics.width(range)
    val inset = with(density) { (restingWidth / 2).dp.toPx() }
    val baseline = initialValue?.takeIf { it.start.isFinite() && it.endInclusive.isFinite() }?.let {
        val first = if (range) it.start.coerceIn(valueRange.start, valueRange.endInclusive) else valueRange.start
        first..it.endInclusive.coerceIn(first, valueRange.endInclusive)
    }
    val ghost = baseline?.takeIf { abs(it.start - start) > ghostEpsilon || abs(it.endInclusive - end) > ghostEpsilon }

    fun snap(v: Float): Float {
        val f = fraction(v)
        return valueRange.start + (if (steps > 0) (f * (steps + 1)).roundToInt().toFloat() / (steps + 1) else f) * span
    }
    fun change(index: Int, v: Float): Boolean {
        val now = currentValue
        val next = snap(v)
        val changed = if (index == 0) next.coerceAtMost(now.endInclusive)..now.endInclusive
            else now.start..next.coerceAtLeast(if (range) now.start else valueRange.start)
        if (changed == now) return false
        update(changed)
        return true
    }
    Layout(
        modifier = modifier.fillMaxWidth().height(GlassSliderThumbMetrics.TOUCH_HEIGHT.dp).alpha(if (enabled) 1f else .38f)
            .glassGestureOwnership(enabled)
            .pointerInput(enabled, rtl, valueRange, steps, range) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    down.consume()
                    fun at(x: Float): Float {
                        val physical = ((x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        return valueRange.start + (if (rtl) 1f - physical else physical) * span
                    }
                    val downValue = at(down.position.x)
                    val now = currentValue
                    active = if (range && (
                        abs(downValue - now.start) < abs(downValue - now.endInclusive) ||
                            // Coincident thumbs have equal distance on either side.
                            // A rail tap must reopen the range toward that side.
                            (now.start == now.endInclusive && downValue < now.start)
                        )) 0 else 1
                    if (active == 0) startActivation++ else endActivation++
                    var lastTick = (fraction(if (active == 0) now.start else now.endInclusive) * (if (steps > 0) steps + 1 else 40)).roundToInt()
                    LiquidGlassHaptics.selectionClick(view)
                    // Track taps retain their absolute mapping; thumb drags start from the
                    // actual value, consume the measured slop once, then use full width.
                    val selectedValue = if (active == 0) now.start else now.endInclusive
                    val onThumb = abs(downValue - selectedValue) / span * size.width <= 24.dp.toPx()
                    val dragMapping = if (onThumb) GlassSliderDragMapping(
                        downX = down.position.x,
                        downValue = selectedValue,
                        controlWidth = size.width.toFloat(),
                        valueSpan = span,
                        slop = GlassSliderDragMapping.START_SLOP_DP.dp.toPx(),
                        rtl = rtl,
                    ) else null
                    change(active, if (onThumb) selectedValue else downValue)
                    var released = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                            // Compose represents platform cancellation as an
                            // already-consumed synthetic up. It must not commit.
                            if (pointer.isConsumed) break
                            if (!pointer.pressed) { released = true; break }
                            val target = dragMapping?.valueAt(pointer.position.x) ?: at(pointer.position.x)
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
            AppSliderTrack(
                startFraction = { if (range) startMotion.position.value else 0f },
                endFraction = { endMotion.position.value },
                tickCount = if (range || steps > 0) steps + 2 else 11,
                modifier = Modifier.fillMaxSize().layerBackdrop(backdrop),
                inset = (restingWidth / 2f).dp, range = range, activeColor = thumbColor, trackBrush = trackBrush,
                ghost = ghost?.let { fraction(it.start)..fraction(it.endInclusive) }, ghostTag = ghostTag,
            )
            for (index in (if (range) 0 else 1)..1) {
                val current = if (index == 0) start else end
                val accessibleRange = if (index == 0) valueRange.start..end else (if (range) start else valueRange.start)..valueRange.endInclusive
                val accessibleSteps = if (steps > 0) {
                    // TalkBack divides this local range, so retain the full-domain step size.
                    (((accessibleRange.endInclusive - accessibleRange.start) / span * (steps + 1)).roundToInt() - 1)
                        .coerceAtLeast(0)
                } else 0
                GlassSliderThumb(
                    trackBackdrop = backdrop,
                    pressedOverride = active == index,
                    range = range,
                    enabled = enabled,
                    motionOverride = if (index == 0) startMotion else endMotion,
                    modifier = Modifier.testTag(if (index == 0) "glass-slider-start" else "glass-slider-end")
                        .semantics {
                            if (!enabled) disabled()
                            progressBarRangeInfo = ProgressBarRangeInfo(current, accessibleRange, accessibleSteps)
                            setProgress { target ->
                                if (!enabled || !target.isFinite()) false
                                else change(index, target).also { changed -> if (changed) finish?.invoke() }
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
