package com.lingion.sleepy.console

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import com.lingion.sleepy.console.ui.ScreenCapture
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, shadows = [ScreenCaptureThreadingTest.FailingPixelCopy::class])
class ScreenCaptureThreadingTest {
    class ThreadCheckedActivity : Activity() {
        @Volatile var checkThread = false
        @Volatile var windowReadOffMain = false
        @Volatile var windowReads = 0

        override fun getWindow(): Window {
            if (checkThread) windowReads++
            if (checkThread && Looper.myLooper() != Looper.getMainLooper()) {
                windowReadOffMain = true
            }
            return super.getWindow()
        }
    }

    @Implements(PixelCopy::class)
    class FailingPixelCopy {
        companion object {
            @JvmStatic
            @Implementation
            fun request(
                source: Window,
                sourceRect: Rect?,
                destination: Bitmap,
                listener: PixelCopy.OnPixelCopyFinishedListener,
                listenerThread: Handler,
            ) {
                listenerThread.post { listener.onPixelCopyFinished(PixelCopy.ERROR_SOURCE_NO_DATA) }
            }
        }
    }

    @Test
    fun workerCaptureReadsWindowAndDrawsOnlyOnMainThread() {
        verifyCapture(320, 240, 320, 240)
    }

    @Test
    @Config(qualifiers = "w2000dp-h1000dp-land-mdpi")
    fun workerCaptureRetainsTheScreenshotSizeLimit() {
        verifyCapture(2000, 1000, 1568, 784)
    }

    @Test
    fun mainThreadCaptureReturnsWithoutWaitingOrReadingTheWindow() {
        val controller = Robolectric.buildActivity(ThreadCheckedActivity::class.java).setup()
        try {
            val activity = controller.get()
            activity.checkThread = true
            assertNull(ScreenCapture.capture(activity))
            assertEquals(0, activity.windowReads)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun workerCaptureReturnsNullForAnUnlaidOutWindow() {
        val controller = Robolectric.buildActivity(ThreadCheckedActivity::class.java).setup()
        try {
            val activity = controller.get()
            activity.window.decorView.layout(0, 0, 0, 0)
            activity.checkThread = true
            assertNull(captureOnWorker(activity))
            assertFalse(activity.windowReadOffMain)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun verifyCapture(width: Int, height: Int, expectedWidth: Int, expectedHeight: Int) {
        val controller = Robolectric.buildActivity(ThreadCheckedActivity::class.java).setup()
        try {
            val activity = controller.get()
            val drawThreads = mutableListOf<Looper?>()
            val content = object : View(activity) {
                override fun onDraw(canvas: Canvas) {
                    drawThreads.add(Looper.myLooper())
                    canvas.drawColor(Color.RED)
                }
            }
            activity.window.setBackgroundDrawable(ColorDrawable(Color.RED))
            activity.setContentView(content)
            activity.window.setLayout(width, height)
            shadowOf(Looper.getMainLooper()).idle()
            val decor = activity.window.decorView
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            decor.layout(0, 0, width, height)
            drawThreads.clear()
            activity.checkThread = true
            val bytes = captureOnWorker(activity)
            assertFalse("Window access must stay on the main thread", activity.windowReadOffMain)
            assertNotNull(bytes)
            val reportDirectory = File("build/reports/screen-capture").apply { mkdirs() }
            File(reportDirectory, "$width-$height.png").writeBytes(bytes!!)
            assertTrue("PixelCopy failure must fall back to drawing the view", drawThreads.isNotEmpty())
            assertTrue(drawThreads.all { it == Looper.getMainLooper() })
            val bitmap = BitmapFactory.decodeByteArray(bytes!!, 0, bytes.size)
            try {
                assertEquals(expectedWidth, bitmap.width)
                assertEquals(expectedHeight, bitmap.height)
                assertEquals(Color.RED, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
            } finally {
                bitmap.recycle()
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun captureOnWorker(activity: Activity): ByteArray? {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val capture = executor.submit<ByteArray?> { ScreenCapture.capture(activity) }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (!capture.isDone && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.yield()
            }
            return capture.get(1, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }
    }
}
