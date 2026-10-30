package com.lingion.sleepy.ui.glass

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density

/** Pin a real Density inside a GraphicsLayer recording, rather than its owning draw scope. */
internal inline fun DrawScope.withRecordingDensity(
    recordingDensity: Density,
    block: DrawScope.() -> Unit
) {
    val previousDensity = drawContext.density
    drawContext.density = recordingDensity
    try {
        block()
    } finally {
        drawContext.density = previousDensity
    }
}
