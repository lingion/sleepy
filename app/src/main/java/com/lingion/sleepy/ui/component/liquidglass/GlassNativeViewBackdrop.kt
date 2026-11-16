package com.lingion.sleepy.ui.component.liquidglass

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.lang.ref.WeakReference
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Hardware native views remain in the Window's normal draw. Only its completed pixels enter
 * retained glass passes. Place this empty sibling at the native view's viewport, not around it.
 * Registration also precedes creation of [view], so the first frame cannot record its functor.
 */
@Composable
fun GlassNativeViewBackdrop(view: View?, modifier: Modifier = Modifier, depth: Float = 0f) {
    require(depth.isFinite()) { "Source depth must be finite" }
    val host = LocalGlassHost.current
    if (host == null) {
        Box(modifier)
        return
    }
    val scene = remember(host) { GlassNativeWindowScene(host) }
    val layer = rememberGraphicsLayer()
    val segment = remember(host, layer, depth) { GlassSourceSegment(depth, layer) }
    DisposableEffect(host, scene, segment) {
        host.nativeScenes.add(scene)
        host.sources.add(segment)
        host.changed()
        onDispose {
            host.sources.remove(segment)
            host.nativeScenes.remove(scene)
            scene.clear()
            host.changed()
        }
    }
    DisposableEffect(view, scene) {
        val capture = view?.let { NativeWindowCapture(it, scene) }
        capture?.start()
        onDispose {
            capture?.dispose()
            scene.clear()
        }
    }
    Box(modifier.onGloballyPositioned {
        segment.coordinates = it
        host.changed()
    }.drawWithContent {
        // Never call drawContent inside a recording containing the native sibling.
        recordGlassLayer(layer) { scene.drawViewport(this, segment.coordinates) }
        drawContent()
    })
}

internal data class GlassNativeWindowGeometry(
    val windowSize: IntSize,
    val viewport: IntRect,
    val viewOrigin: IntOffset,
    val viewSize: IntSize,
    val windowToken: IBinder,
)

internal data class GlassNativeWindowFrame(
    val image: ImageBitmap,
    val geometry: GlassNativeWindowGeometry,
)

internal class GlassNativeWindowScene(private val host: GlassHostState) {
    private var windowFrame by mutableStateOf<GlassNativeWindowFrame?>(null)
    private var viewportFrame by mutableStateOf<GlassNativeWindowFrame?>(null)

    fun clear() {
        windowFrame = null
        viewportFrame = null
        host.changed()
    }

    fun clearWindow() {
        windowFrame = null
        host.changed()
    }

    fun publishWindow(next: GlassNativeWindowFrame) {
        windowFrame = next
        host.changed()
    }

    fun publishViewport(next: GlassNativeWindowFrame) {
        viewportFrame = next
        host.changed()
        // Pre-draw runs after the frame's normal snapshot dispatch. Notify draw observers now,
        // so the source layer and its consumers can use these pixels in this same traversal.
        Snapshot.sendApplyNotifications()
    }

    fun drawWindow(scope: DrawScope, coordinates: LayoutCoordinates) {
        val current = windowFrame ?: return
        scope.inWindowCoordinates(coordinates) {
            drawImage(image = current.image, dstSize = current.geometry.windowSize)
        }
    }

    fun drawViewport(scope: DrawScope, coordinates: LayoutCoordinates?) {
        val current = viewportFrame ?: return
        val target = coordinates?.takeIf { it.isAttached } ?: return
        val viewport = current.geometry.viewport
        scope.inWindowCoordinates(target) {
            // This image was drawn from the native view alone, never from Window pixels that
            // may already contain a floating glass toolbar or other optical consumers.
            clipRect(viewport.left.toFloat(), viewport.top.toFloat(), viewport.right.toFloat(), viewport.bottom.toFloat()) {
                drawImage(
                    image = current.image,
                    dstOffset = current.geometry.viewOrigin,
                    dstSize = current.geometry.viewSize,
                )
            }
        }
    }
}

private fun DrawScope.inWindowCoordinates(coordinates: LayoutCoordinates, draw: DrawScope.() -> Unit) {
    if (!coordinates.isAttached) return
    val origin = coordinates.windowToLocal(Offset.Zero)
    val x = coordinates.windowToLocal(Offset(1f, 0f))
    val y = coordinates.windowToLocal(Offset(0f, 1f))
    if (listOf(origin.x, origin.y, x.x, x.y, y.x, y.y).any { !it.isFinite() }) return
    val matrix = Matrix().apply {
        this[0, 0] = x.x - origin.x; this[0, 1] = x.y - origin.y
        this[1, 0] = y.x - origin.x; this[1, 1] = y.y - origin.y
        this[3, 0] = origin.x; this[3, 1] = origin.y
    }
    withTransform({ transform(matrix) }, draw)
}

private class NativeWindowCapture(view: View, private val scene: GlassNativeWindowScene) {
    private val nativeView = WeakReference(view)
    private val handler = Handler(Looper.getMainLooper())
    private var observer: ViewTreeObserver? = null
    private var disposed = false
    private var scheduled = false
    private var inFlight = false
    private var viewportDirty = true
    private var windowDirty = true
    private var lastScroll: IntOffset? = null
    private var lastRequestAt = 0L
    private var noDataRetries = 0
    private var generation = 0
    private var geometry: GlassNativeWindowGeometry? = null

    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        val view = nativeView.get()
        val target = view?.let(::captureTarget)
        if (target?.geometry != geometry) {
            generation++
            geometry = target?.geometry
            scene.clear()
            viewportDirty = true
            windowDirty = true
            noDataRetries = 0
        }
        val scroll = view?.let { IntOffset(it.scrollX, it.scrollY) }
        if (view?.isDirty == true || scroll != lastScroll) {
            viewportDirty = true
            windowDirty = true
            noDataRetries = 0
        }
        lastScroll = scroll
        // Ordinary controls sample the native view before this traversal draws. They never
        // wait for an asynchronous Window readback, or go transparent while scrolling.
        if (viewportDirty && view != null && target != null) captureViewport(view, target.geometry)
        true
    }
    private val onDraw = ViewTreeObserver.OnDrawListener { if (windowDirty) schedule() }
    private val onScroll = ViewTreeObserver.OnScrollChangedListener {
        viewportDirty = true
        windowDirty = true
        noDataRetries = 0
    }
    private val attachment = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = attach(v)
        override fun onViewDetachedFromWindow(v: View) {
            detach()
            geometry = null
            scene.clear()
        }
    }
    private val copy = Runnable {
        scheduled = false
        copyWindow()
    }

    fun start() {
        val view = nativeView.get() ?: return
        view.addOnAttachStateChangeListener(attachment)
        if (view.isAttachedToWindow) attach(view)
    }

    fun dispose() {
        disposed = true
        detach()
        nativeView.get()?.removeOnAttachStateChangeListener(attachment)
        nativeView.clear()
    }

    private fun attach(view: View) {
        detach()
        observer = view.viewTreeObserver.also {
            it.addOnPreDrawListener(preDraw)
            it.addOnDrawListener(onDraw)
            it.addOnScrollChangedListener(onScroll)
        }
        viewportDirty = true
        windowDirty = true
        lastScroll = null
        noDataRetries = 0
        view.postInvalidateOnAnimation()
    }

    private fun detach() {
        generation++
        handler.removeCallbacks(copy)
        scheduled = false
        observer?.takeIf { it.isAlive }?.let {
            it.removeOnPreDrawListener(preDraw)
            it.removeOnDrawListener(onDraw)
            it.removeOnScrollChangedListener(onScroll)
        }
        observer = null
    }

    private fun schedule() {
        if (disposed || scheduled || inFlight || !windowDirty) return
        scheduled = true
        // A bitmap publication redraws Compose, but must not itself request another readback.
        val delay = max(0L, 16L - (SystemClock.uptimeMillis() - lastRequestAt))
        handler.postDelayed(copy, delay)
    }

    private fun captureViewport(view: View, geometry: GlassNativeWindowGeometry) {
        // A software snapshot does not change the live view's hardware layer/settings.
        val bitmap = try {
            backdropBitmap(view.width, view.height)
        } catch (_: OutOfMemoryError) {
            return
        }
        try {
            val scrollX = view.scrollX.toFloat()
            val scrollY = view.scrollY.toFloat()
            val canvas = Canvas(bitmap)
            canvas.scale(bitmap.width.toFloat() / view.width, bitmap.height.toFloat() / view.height)
            // View.draw expects the caller to apply its scroll, just like View's display list.
            canvas.translate(-scrollX, -scrollY)
            view.draw(canvas)
        } catch (_: RuntimeException) {
            bitmap.recycle()
            return
        } catch (_: OutOfMemoryError) {
            bitmap.recycle()
            return
        }
        viewportDirty = false
        scene.publishViewport(GlassNativeWindowFrame(bitmap.asImageBitmap(), geometry))
    }

    private fun copyWindow() {
        if (disposed || inFlight || !windowDirty) return
        val view = nativeView.get() ?: return
        val target = captureTarget(view) ?: run {
            scene.clear()
            return
        }
        val requestGeneration = generation
        val bitmap = try {
            backdropBitmap(target.geometry.windowSize.width, target.geometry.windowSize.height)
        } catch (_: OutOfMemoryError) {
            return
        }
        windowDirty = false
        inFlight = true
        lastRequestAt = SystemClock.uptimeMillis()
        try {
            // PixelCopy may offset this Rect for surface insets, so never reuse it.
            PixelCopy.request(target.window, Rect(0, 0, target.geometry.windowSize.width, target.geometry.windowSize.height), bitmap, { result ->
                inFlight = false
                val current = nativeView.get()?.let(::captureTarget)?.geometry
                if (disposed || generation != requestGeneration || current != target.geometry) {
                    bitmap.recycle()
                    if (!disposed) {
                        // An obsolete Window readback must not erase the newer viewport frame.
                        scene.clearWindow()
                        windowDirty = current != null
                        schedule()
                    }
                } else if (result == PixelCopy.SUCCESS) {
                    // Published bitmaps are never overwritten/recycled while retained layers use them.
                    scene.publishWindow(GlassNativeWindowFrame(bitmap.asImageBitmap(), target.geometry))
                    noDataRetries = 0
                    if (windowDirty) schedule()
                } else {
                    bitmap.recycle()
                    if (result == PixelCopy.ERROR_SOURCE_NO_DATA && noDataRetries++ < 2) windowDirty = true
                    if (windowDirty) schedule()
                }
            }, handler)
        } catch (_: IllegalArgumentException) {
            inFlight = false
            bitmap.recycle()
        }
    }
}

// Sampling is already filtered by the glass material. Half-size readbacks bound per-frame
// allocation while the live native view stays full resolution and hardware accelerated.
private fun backdropBitmap(width: Int, height: Int): Bitmap = Bitmap.createBitmap(
    max(1, ceil(width * .5).toInt()), max(1, ceil(height * .5).toInt()), Bitmap.Config.ARGB_8888,
)

private data class NativeCaptureTarget(val window: Window, val geometry: GlassNativeWindowGeometry)

private fun captureTarget(view: View): NativeCaptureTarget? {
    if (!view.isAttachedToWindow || !view.isShown || view.width <= 0 || view.height <= 0) return null
    val window = view.context.activityWindow() ?: return null
    val decor = window.peekDecorView() ?: return null
    val token = view.windowToken ?: return null
    if (!decor.isAttachedToWindow || decor.windowToken != token || decor.width <= 0 || decor.height <= 0) return null
    val location = IntArray(2)
    view.getLocationInWindow(location)
    val viewport = IntRect(
        max(0, location[0]), max(0, location[1]),
        min(decor.width, location[0] + view.width), min(decor.height, location[1] + view.height),
    )
    if (viewport.width <= 0 || viewport.height <= 0) return null
    return NativeCaptureTarget(window, GlassNativeWindowGeometry(
        IntSize(decor.width, decor.height), viewport, IntOffset(location[0], location[1]),
        IntSize(view.width, view.height), token,
    ))
}

private fun Context.activityWindow(): Window? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current.window
        val next = current.baseContext
        if (next === current) return null
        current = next
    }
    return (current as? Activity)?.window
}
