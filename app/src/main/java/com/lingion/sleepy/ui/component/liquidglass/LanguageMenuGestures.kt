package com.lingion.sleepy.ui.component.liquidglass

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput

// Approximate down-to-open interval observed in the native reference capture.
private const val LanguageMenuLongPressMillis = 300L

/**
 * Opens on tap-up or a held press, retaining the original pointer for slide selection.
 * Positions are local to the anchor; callback Booleans mean opened by long press.
 * A tap only calls [onOpen], so lifting the opening tap cannot select a menu row.
 * The owner supplies click semantics separately.
 */
@Composable
internal fun Modifier.languageMenuAnchorGesture(
    enabled: Boolean,
    onOpen: () -> Unit,
    onPosition: (Offset, Boolean) -> Unit,
    onRelease: (Offset, Boolean) -> Unit,
    onCancel: () -> Unit,
): Modifier {
    val currentEnabled by rememberUpdatedState(enabled)
    val open by rememberUpdatedState(onOpen)
    val position by rememberUpdatedState(onPosition)
    val release by rememberUpdatedState(onRelease)
    val cancel by rememberUpdatedState(onCancel)
    // Opening usually disables the anchor. Keep its existing pointer stream alive.
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)
            if (!currentEnabled) return@awaitEachGesture
            var completed = false
            try {
                val remaining = (down.uptimeMillis + LanguageMenuLongPressMillis - SystemClock.uptimeMillis())
                    .coerceAtLeast(0L)
                val outcome = withTimeoutOrNull(remaining) { awaitLanguageMenuAnchorUp(down) }
                if (!currentEnabled || outcome == LanguageMenuAnchorOutcome.Cancelled) return@awaitEachGesture
                if (outcome == LanguageMenuAnchorOutcome.Tap) {
                    completed = true
                    open()
                    return@awaitEachGesture
                }

                val held = currentEvent.changes.firstOrNull { it.id == down.id }
                if (held == null || !held.pressed || currentEvent.changes.any { it.isConsumed } ||
                    currentEvent.changes.any { it.id != down.id && it.pressed }
                ) return@awaitEachGesture
                currentEvent.changes.forEach { it.consume() }
                open()
                position(held.position, true)

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val pointer = event.changes.firstOrNull { it.id == down.id }
                    // Compose dispatches ACTION_CANCEL as consumed synthetic up changes.
                    // Never turn a window handoff or another recognizer's claim into selection.
                    if (pointer == null || event.changes.any { it.isConsumed } ||
                        event.changes.any { it.id != down.id && it.pressed }
                    ) {
                        event.changes.forEach { it.consume() }
                        break
                    }
                    event.changes.forEach { it.consume() }
                    if (!pointer.pressed) {
                        completed = true
                        release(pointer.position, true)
                        break
                    }
                    position(pointer.position, true)
                }
            } finally {
                if (!completed) cancel()
            }
        }
    }
}

private enum class LanguageMenuAnchorOutcome { Tap, Cancelled }

/** Leave the press unconsumed until opening, allowing an ancestor to start scrolling. */
private suspend fun AwaitPointerEventScope.awaitLanguageMenuAnchorUp(
    down: PointerInputChange,
): LanguageMenuAnchorOutcome {
    if (awaitPointerEvent(PointerEventPass.Final).changes.any { it.isConsumed }) {
        return LanguageMenuAnchorOutcome.Cancelled
    }
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        val pointer = event.changes.firstOrNull { it.id == down.id }
            ?: return LanguageMenuAnchorOutcome.Cancelled
        if (event.changes.any { it.isConsumed } || event.changes.any { it.id != down.id && it.pressed } ||
            pointer.position.x < 0f || pointer.position.x > size.width ||
            pointer.position.y < 0f || pointer.position.y > size.height ||
            (pointer.position - down.position).getDistance() > viewConfiguration.touchSlop
        ) return LanguageMenuAnchorOutcome.Cancelled
        if (!pointer.pressed) {
            pointer.consume()
            return LanguageMenuAnchorOutcome.Tap
        }
        if (awaitPointerEvent(PointerEventPass.Final).changes.any { it.isConsumed }) {
            return LanguageMenuAnchorOutcome.Cancelled
        }
    }
}
