package com.lingion.sleepy.ui.component

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.compose.ui.window.DialogWindowProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal data class OpticalCaptureWindow(val view: View, val root: View, val window: Window?) {
    companion object {
        fun from(view: View): OpticalCaptureWindow {
            var parent = view.parent
            while (parent != null) {
                if (parent is DialogWindowProvider) return OpticalCaptureWindow(view, view.rootView, parent.window)
                parent = parent.parent
            }
            var context = view.context
            while (context !is Activity && context is ContextWrapper) context = context.baseContext
            return OpticalCaptureWindow(view, view.rootView, (context as Activity).window)
        }

    }

    fun origin(): IntArray = IntArray(2).also { result ->
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.getLocationInWindow(result) }
    }

    fun awaitHwuiCommit() {
        val committed = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            root.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
            root.postInvalidateOnAnimation()
        }
        check(committed.await(5, TimeUnit.SECONDS)) { "HWUI did not commit the optical capture" }
    }

    fun capture(): Bitmap {
        awaitHwuiCommit()
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val copied = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        if (window != null) {
            PixelCopy.request(window, bitmap, { result = it; copied.countDown() }, Handler(Looper.getMainLooper()))
        } else {
            check(Build.VERSION.SDK_INT >= 34) { "Actual Popup PixelCopy requires API 34" }
            // Public API uses this attached View's own ViewRoot surface, never its Activity surface.
            // ofWindow(View) otherwise copies surfaceInsets too, squeezing its
            // shadow-bearing surface into the root-sized destination. Supply
            // window coordinates; PixelCopy itself applies the surface insets.
            val rootLocation = IntArray(2)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { root.getLocationInWindow(rootLocation) }
            val sourceRect = android.graphics.Rect(rootLocation[0], rootLocation[1],
                rootLocation[0] + root.width, rootLocation[1] + root.height)
            val request = PixelCopy.Request.Builder.ofWindow(root).setSourceRect(sourceRect)
                .setDestinationBitmap(bitmap).build()
            PixelCopy.request(request, { command -> Handler(Looper.getMainLooper()).post(command) }) {
                result = it.status
                copied.countDown()
            }
        }
        check(copied.await(5, TimeUnit.SECONDS) && result == PixelCopy.SUCCESS) { "Optical PixelCopy failed: $result" }
        return bitmap
    }
}
