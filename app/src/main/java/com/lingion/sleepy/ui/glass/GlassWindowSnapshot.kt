package com.lingion.sleepy.ui.glass

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.Window
import androidx.compose.ui.geometry.Rect
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.suspendCancellableCoroutine

/** One bounded copy of actual composited View pixels, including native material RenderEffects. */
internal suspend fun captureGlassWindowRegion(window: Window, bounds: Rect): Bitmap? {
    val decor = window.decorView
    if (decor.width <= 0 || decor.height <= 0 || bounds.width <= 0f || bounds.height <= 0f) return null
    // Never stretch the visible part of a partially offscreen card to its full source size.
    if (bounds.left < 0f || bounds.top < 0f || bounds.right > decor.width || bounds.bottom > decor.height) return null
    val left = bounds.left.roundToInt().coerceIn(0, decor.width - 1)
    val top = bounds.top.roundToInt().coerceIn(0, decor.height - 1)
    val right = bounds.right.roundToInt().coerceIn(left + 1, decor.width)
    val bottom = bounds.bottom.roundToInt().coerceIn(top + 1, decor.height)
    val bitmap = Bitmap.createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888)
    return suspendCancellableCoroutine { continuation ->
        try {
            PixelCopy.request(window, android.graphics.Rect(left, top, right, bottom), bitmap, { result ->
                if (!continuation.isActive) bitmap.recycle()
                else if (result == PixelCopy.SUCCESS) continuation.resume(bitmap)
                else {
                    bitmap.recycle()
                    Log.w("GlassWindowSnapshot", "PixelCopy failed: $result")
                    continuation.resume(null)
                }
            }, Handler(Looper.getMainLooper()))
        } catch (error: IllegalArgumentException) {
            bitmap.recycle()
            Log.w("GlassWindowSnapshot", "Window region unavailable", error)
            if (continuation.isActive) continuation.resume(null)
        }
    }
}
