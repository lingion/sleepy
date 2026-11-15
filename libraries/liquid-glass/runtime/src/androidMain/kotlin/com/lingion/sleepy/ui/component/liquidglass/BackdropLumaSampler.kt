package com.lingion.sleepy.ui.component.liquidglass

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.node.requireLayoutDirection
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * A completed five-pixel sample, composited over the caller's opaque fallback. [meanLuma] uses
 * the captured sRGB channel values directly with weights .2126/.7152/.0722; it is not linear-light
 * luminance or a native appearance classifier. No response threshold or hysteresis is supplied.
 *
 * [snapshotMainNanos] covers UI-thread recording and toImageBitmap, [readbackNanos] covers the
 * worker's copy/getPixels/mean calculation, and [endToEndNanos] also includes worker scheduling.
 * [sourceDirtyWhileBusy] counts observed source invalidation notifications, not every mutation.
 * Counters belong to this attached sampler, and are diagnostic rather than frame/timing promises.
 */
data class BackdropLumaSample(
    val meanLuma: Float,
    val sampledArgb: List<Int>,
    val requestSequence: Long,
    val generation: Long,
    val snapshotMainNanos: Long,
    val readbackNanos: Long,
    val endToEndNanos: Long = 0L,
    val sourceDirtyWhileBusy: Long,
    val acceptedCount: Long,
    val discardedCount: Long,
    val failedCount: Long = 0L,
    val snapshotCompletedAtNanos: Long = 0L,
    val sourceRevision: Int? = null,
    val startedAtNanos: Long = 0L,
    val completedAtNanos: Long = 0L,
)

/**
 * Samples only an already-recorded external scene; this modifier never calls drawContent to
 * capture the source. The normal content is painted once. Source producers must exclude this
 * consumer and its adaptive backing, and complete recording before this consumer is drawn.
 *
 * [sampleBoundsPx] receives the modifier's real size and returns a finite positive rectangle in
 * its local physical pixels. Five one-pixel areas at its center and (20%,30%), (80%,30%),
 * (20%,70%), (80%,70%) are copied into one 5x1 layer. No full-scene atlas or Window PixelCopy is
 * allocated. LayerBackdrop uses its completed-recording revision; other Backdrop implementations
 * must expose their content invalidation through snapshot reads in drawBackdrop. A source which
 * silently mutates a RenderNode without such reads cannot provide that version guarantee.
 * Retained child sources can publish completed source changes with [BackdropContentSignal]
 * and wrap the source using [withContentSignal]; this requires explicit caller integration.
 *
 * Recording and the API28+ toImageBitmap call stay on the UI thread. Only the returned standalone
 * bitmap is read on a worker. One request may be in flight. Content changes request the newest
 * sample after that readback; the already-frozen sample remains an ordered historical observation,
 * tagged with its snapshot time and the direct LayerBackdrop recording revision when available.
 * Source identity, geometry or configuration changes invalidate its result. Intermediate content
 * versions can be skipped. No sample is promised to describe the current frame, and repeated
 * unchanged draws do not poll. A retained consumer layer isolates observer invalidation from
 * sibling producers; otherwise redrawing a shared layer could record the source again and feed
 * its revision back into this sampler indefinitely. onSample runs on the UI thread.
 *
 * API<28 or invalid/unavailable geometry produces no sample. Existing output may be retained by
 * the caller while a fresh sample is pending. Relative scale/rotation of a LayerBackdrop needs
 * its existing withTransformCompensation adapter; changes which never invalidate this draw or
 * publish source reads cannot be detected. toImageBitmap can traverse retained RenderNodes and
 * rebuild an invalidated display list; a 5x1 target does not bound total GPU work or UI latency.
 */
fun Modifier.sampleBackdropLuma(
    backdrop: Backdrop,
    transparentBackground: Color,
    sampleBoundsPx: (Size) -> Rect,
    onSample: (BackdropLumaSample) -> Unit,
): Modifier {
    require(transparentBackground.alpha == 1f) { "The source fallback must be opaque" }
    return this.graphicsLayer() then BackdropLumaSamplerElement(backdrop, transparentBackground, sampleBoundsPx, onSample)
}

private data class BackdropLumaSamplerElement(
    val backdrop: Backdrop,
    val transparentBackground: Color,
    val sampleBoundsPx: (Size) -> Rect,
    val onSample: (BackdropLumaSample) -> Unit,
) : ModifierNodeElement<BackdropLumaSamplerNode>() {
    override fun create() = BackdropLumaSamplerNode(backdrop, transparentBackground, sampleBoundsPx, onSample)

    override fun update(node: BackdropLumaSamplerNode) {
        node.update(backdrop, transparentBackground, sampleBoundsPx, onSample)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "sampleBackdropLuma"
        properties["backdrop"] = backdrop
        properties["transparentBackground"] = transparentBackground
    }
}

private data class LumaGeometry(
    val size: Size,
    val bounds: Rect,
    val screenOrigin: Offset,
    val screenX: Offset,
    val screenY: Offset,
    val density: Float,
    val fontScale: Float,
    val layoutDirection: LayoutDirection,
)

private data class LumaReadback(val pixels: IntArray, val mean: Float, val nanos: Long)

private class BackdropLumaSamplerNode(
    private var backdrop: Backdrop,
    private var transparentBackground: Color,
    private var sampleBoundsPx: (Size) -> Rect,
    private var onSample: (BackdropLumaSample) -> Unit,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode, ObserverModifierNode {
    private var coordinates: LayoutCoordinates? = null
    private var sampleLayer: GraphicsLayer? = null
    private var geometry: LumaGeometry? = null
    private var generation = 0L
    private var dirty = true
    private var inFlight = false
    private var requestJob: Job? = null
    private var requestSequence = 0L
    private var sourceDirtyWhileBusy = 0L
    private var acceptedCount = 0L
    private var discardedCount = 0L
    private var failedCount = 0L

    fun update(
        backdrop: Backdrop,
        transparentBackground: Color,
        sampleBoundsPx: (Size) -> Rect,
        onSample: (BackdropLumaSample) -> Unit,
    ) {
        if (this.backdrop !== backdrop || this.transparentBackground != transparentBackground) {
            markDirty()
        }
        this.backdrop = backdrop
        this.transparentBackground = transparentBackground
        this.sampleBoundsPx = sampleBoundsPx
        this.onSample = onSample
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val target = coordinates?.takeIf { it.isAttached } ?: run {
            discardGeometry()
            return
        }
        val recordingDensity = Density(density, fontScale)
        val currentGeometry = geometryOf(target, size, recordingDensity, layoutDirection) ?: run {
            discardGeometry()
            return
        }
        if (geometry != currentGeometry) {
            geometry = currentGeometry
            markDirty()
        }
        if (!dirty || inFlight) return

        dirty = false
        inFlight = true
        val request = ++requestSequence
        val requestedGeneration = generation
        val source = backdrop
        val fallback = transparentBackground
        val started = System.nanoTime()
        val layer = sampleLayer ?: requireGraphicsContext().createGraphicsLayer().apply {
            compositingStrategy = CompositingStrategy.Offscreen
        }.also { sampleLayer = it }
        var recordedRevision: Int? = null
        try {
            observeReads {
                recordedRevision = (source as? LayerBackdrop)?.recordingRevision
                layer.record(recordingDensity, layoutDirection, IntSize(5, 1)) {
                    drawRect(fallback)
                    val bounds = currentGeometry.bounds
                    for (index in 0 until 5) {
                        val fraction = when (index) {
                            0 -> Offset(.5f, .5f)
                            1 -> Offset(.2f, .3f)
                            2 -> Offset(.8f, .3f)
                            3 -> Offset(.2f, .7f)
                            else -> Offset(.8f, .7f)
                        }
                        val point = Offset(bounds.left + bounds.width * fraction.x,
                            bounds.top + bounds.height * fraction.y)
                        clipRect(index.toFloat(), 0f, index + 1f, 1f) {
                            translate(index + .5f - point.x, .5f - point.y) {
                                val recordedSize = drawContext.size
                                try {
                                    drawContext.size = currentGeometry.size
                                    with(source) { drawBackdrop(recordingDensity, target, null) }
                                } finally {
                                    drawContext.size = recordedSize
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            failedCount++
            inFlight = false
            return
        }

        // UNDISPATCHED is intentional: API28+ snapshots before another UI task can mutate
        // the source graph. No DrawScope or LayoutCoordinates is passed to the worker.
        val launched = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var frozen: Bitmap? = null
            try {
                val image = layer.toImageBitmap().asAndroidBitmap()
                frozen = image
                val snapshotCompleted = System.nanoTime()
                val snapshotNanos = snapshotCompleted - started
                val readback = withContext(Dispatchers.Default) {
                    coroutineContext.ensureActive()
                    val readStarted = System.nanoTime()
                    var software: Bitmap? = null
                    try {
                        val readable = if (image.config == Bitmap.Config.HARDWARE) {
                            image.copy(Bitmap.Config.ARGB_8888, false).also { software = it }
                                ?: error("Unable to copy the tiny hardware sample")
                        } else image
                        val pixels = IntArray(5)
                        readable.getPixels(pixels, 0, 5, 0, 0, 5, 1)
                        var total = 0f
                        for (pixel in pixels) {
                            total += (.2126f * ((pixel ushr 16) and 255) +
                                .7152f * ((pixel ushr 8) and 255) + .0722f * (pixel and 255)) / 255f
                        }
                        coroutineContext.ensureActive()
                        LumaReadback(pixels, total / 5f, System.nanoTime() - readStarted)
                    } finally {
                        software?.recycle()
                    }
                }
                val currentTarget = coordinates?.takeIf { it.isAttached }
                val latestGeometry = currentTarget?.let {
                    geometryOf(it, it.size.toSize(), requireDensity(), requireLayoutDirection())
                }
                if (isAttached && generation == requestedGeneration && backdrop === source &&
                    latestGeometry == currentGeometry) {
                    acceptedCount++
                    val completed = System.nanoTime()
                    onSample(BackdropLumaSample(
                        meanLuma = readback.mean,
                        sampledArgb = readback.pixels.toList(),
                        requestSequence = request,
                        generation = requestedGeneration,
                        snapshotMainNanos = snapshotNanos,
                        readbackNanos = readback.nanos,
                        endToEndNanos = completed - started,
                        sourceDirtyWhileBusy = sourceDirtyWhileBusy,
                        acceptedCount = acceptedCount,
                        discardedCount = discardedCount,
                        failedCount = failedCount,
                        snapshotCompletedAtNanos = snapshotCompleted,
                        sourceRevision = recordedRevision,
                        startedAtNanos = started,
                        completedAtNanos = completed,
                    ))
                } else {
                    discardedCount++
                    dirty = true
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failedCount++
            } finally {
                frozen?.recycle()
                inFlight = false
                requestJob = null
                if (isAttached && dirty) invalidateDraw()
            }
        }
        // An exception before the first suspension may already have completed the job.
        requestJob = launched.takeUnless { it.isCompleted }
    }

    override fun onObservedReadsChanged() {
        if (inFlight) sourceDirtyWhileBusy++
        // Content may advance while a frozen sample is read. Deliver that ordered historical
        // sample, then capture the latest source; discarding every old revision would starve
        // appearance updates during continuous scrolling.
        dirty = true
        invalidateDraw()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        invalidateDraw()
    }

    override fun onDetach() {
        markDirty()
        coordinates = null
        geometry = null
        requestJob?.cancel()
        requestJob = null
        sampleLayer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        sampleLayer = null
    }

    private fun markDirty() {
        generation++
        dirty = true
    }

    private fun discardGeometry() {
        if (geometry != null) {
            geometry = null
            markDirty()
        }
    }

    private fun geometryOf(
        target: LayoutCoordinates,
        size: Size,
        density: Density,
        direction: LayoutDirection,
    ): LumaGeometry? = try {
        val bounds = sampleBoundsPx(size)
        val origin = target.localToScreen(Offset.Zero)
        val x = target.localToScreen(Offset(1f, 0f))
        val y = target.localToScreen(Offset(0f, 1f))
        if (size.width.isFinite() && size.height.isFinite() && size.width > 0f && size.height > 0f &&
            bounds.left.isFinite() && bounds.top.isFinite() && bounds.right.isFinite() && bounds.bottom.isFinite() &&
            bounds.width > 0f && bounds.height > 0f && bounds.left >= 0f && bounds.top >= 0f &&
            bounds.right <= size.width && bounds.bottom <= size.height &&
            origin.x.isFinite() && origin.y.isFinite() && x.x.isFinite() && x.y.isFinite() &&
            y.x.isFinite() && y.y.isFinite()) {
            LumaGeometry(size, bounds, origin, x, y, density.density, density.fontScale, direction)
        } else null
    } catch (_: Exception) {
        null
    }
}
