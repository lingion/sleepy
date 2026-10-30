// Based on Kyant0/AndroidLiquidGlass catalog components, Apache-2.0.
// Modified for SleepDown-Schedule.
package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed

suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        // Observe one DOWN at Initial pass and keep that pointer id until it ends. The former
        // implementation awaited a second DOWN at Main pass; if a child consumed the first event,
        // that await leaked into the next gesture and made a quick tap after long-press disappear.
        val down = awaitFirstDown(false, PointerEventPass.Initial)
        onDragStart(down)
        onDrag(down, Offset.Zero)
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                onDragCancel()
                break
            }
            if (change.changedToUpIgnoreConsumed()) {
                onDragEnd(change)
                break
            }
            if (!change.pressed) {
                onDragCancel()
                break
            }
            val delta = change.position - change.previousPosition
            if (delta != Offset.Zero) onDrag(change, delta)
        }
    }
}
