package com.lingion.sleepy.ui.glass

import android.graphics.Matrix
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.abs

/** Project the shell and its contents together; reuse their layers instead of a second texture. */
internal fun Modifier.liquidMorphProjection(bounds: () -> Rect, taper: () -> Float): Modifier = drawWithCache {
    val rect = bounds()
    val amount = taper().coerceIn(-0.3f, 0.3f)
    val matrix = if (abs(amount) < 0.0001f || rect.width <= 0f || rect.height <= 0f) null else Matrix().apply {
        val top = amount.coerceAtLeast(0f) * rect.width
        val bottom = (-amount).coerceAtLeast(0f) * rect.width
        setPolyToPoly(
            floatArrayOf(rect.left, rect.top, rect.right, rect.top, rect.right, rect.bottom, rect.left, rect.bottom), 0,
            floatArrayOf(rect.left + top, rect.top, rect.right - top, rect.top,
                rect.right - bottom, rect.bottom, rect.left + bottom, rect.bottom), 0, 4
        )
    }
    onDrawWithContent {
        if (matrix == null) drawContent() else {
            val canvas = drawContext.canvas.nativeCanvas
            val checkpoint = canvas.save()
            try { canvas.concat(matrix); drawContent() }
            finally { canvas.restoreToCount(checkpoint) }
        }
    }
}
