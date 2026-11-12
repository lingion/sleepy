package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.SliderState
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** Own touch input before Material's slop detector; retain its rendering and semantics.
 * SliderState performs discrete snapping; input uses the same inset as the shared circular thumb.
 */
@Composable
internal fun Modifier.materialSliderGestureOwnership(
    state: SliderState,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onFinished: (() -> Unit)?,
): Modifier {
    val update by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onFinished)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return glassGestureOwnership(enabled).pointerInput(state, enabled, rtl) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            down.consume()
            // Relative motion leaves the value unchanged for purely vertical detours.
            var pointerId = down.id
            var moved = false
            var movedHorizontally = false
            // Material's raw offset is initialized only by its internal onPress path.
            // Keep an unsnapped value here so small movements accumulate across steps.
            var dragValue = state.value
            val inset = (GlassSliderThumbMetrics.SINGLE_DIAMETER / 2f).dp.toPx()
            val trackWidth = (size.width - 2 * inset).coerceAtLeast(1f)
            val range = state.trackRange.endInclusive - state.trackRange.start
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pointer = event.changes.firstOrNull { it.id == pointerId } ?: break
                if (pointer.isConsumed) break
                if (!pointer.pressed) {
                    val next = event.changes.firstOrNull { it.pressed }
                    event.changes.forEach { it.consume() }
                    if (next == null) {
                        if (!moved && !movedHorizontally) {
                            val fraction = ((down.position.x - inset) / trackWidth).coerceIn(0f, 1f)
                            state.value = state.trackRange.start + (if (rtl) 1f - fraction else fraction) * range
                            update(state.value)
                        }
                        finish?.invoke()
                        break
                    }
                    pointerId = next.id
                    continue
                }
                if ((pointer.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                val dx = pointer.position.x - pointer.previousPosition.x
                if (dx != 0f) {
                    movedHorizontally = true
                    dragValue = (dragValue + (if (rtl) -dx else dx) / trackWidth * range)
                        .coerceIn(state.trackRange.start, state.trackRange.endInclusive)
                    state.value = dragValue
                    update(state.value)
                }
                event.changes.forEach { it.consume() }
            }
        }
    }
}

/** Consume after child controls, before ancestor scrollables, until every finger is up.
 * No shared lock survives cancellation, disabling, or removal from composition.
 */
internal fun Modifier.glassGestureOwnership(enabled: Boolean = true): Modifier =
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
            do {
                val event = currentEvent
                event.changes.forEach { it.consume() }
                if (event.changes.none { it.pressed }) break
                awaitPointerEvent(PointerEventPass.Main)
            } while (true)
        }
    }
