// SleepDown extension to the Apache-2.0 Kyant API.
package com.kyant.backdrop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow

/** A drawing-only sampling boundary. Unknown bounds keep first-frame sampling enabled. */
class BackdropViewport {
    private var bounds: Rect? by mutableStateOf(null)

    fun update(windowBounds: Rect) {
        bounds = windowBounds
    }

    internal fun intersects(coordinates: LayoutCoordinates): Boolean =
        bounds?.let { viewport ->
            val card = coordinates.boundsInWindow(clipBounds = false)
            card.right >= viewport.left && card.left <= viewport.right &&
                card.bottom >= viewport.top && card.top <= viewport.bottom
        } ?: true
}
