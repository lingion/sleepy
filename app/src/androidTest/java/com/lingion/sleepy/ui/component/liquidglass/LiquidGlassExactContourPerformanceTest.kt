package com.lingion.sleepy.ui.component.liquidglass

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.kyant.backdrop.BackdropRenderOptions
import com.kyant.backdrop.asAndroidRuntimeShader
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.floor
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in same-APK GPU comparison, not a performance pass/fail threshold or native appearance test.
 * Both modes retain exact/path/profile CPU preparation. Only the final RenderEffect snapshot's
 * uOutlined changes (1/3 versus 4/5). The production shader, including its final return, is unmodified.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class LiquidGlassExactContourPerformanceTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val tick = mutableStateOf(Tick(-1, false, 0, 0L))
    private val scene = mutableStateOf(Fixture.Menu)
    private val evidence = Evidence()
    private lateinit var window: Window
    private lateinit var view: View

    private enum class Fixture(val widthDp: Float, val heightDp: Float, val dual: Boolean) {
        Menu(251f, 147f, false), Sheet(343f, 420f, false), Language(391f, 341f, true);
        fun mode(legacy: Boolean) = if (legacy) (if (dual) 3 else 1) else (if (dual) 5 else 4)
    }
    private data class Tick(val epoch: Int, val legacy: Boolean, val phase: Int, val vsyncNs: Long)
    private data class Epoch(val id: Int, val fixture: Fixture, val legacy: Boolean, val warmup: Boolean,
        val frames: Int, val vsyncs: MutableList<Long> = mutableListOf(), var endNs: Long = Long.MAX_VALUE)
    private data class Snapshot(val epoch: Int, val fixture: Fixture, val mode: Int, val vsyncNs: Long,
        val appliedNs: Long, val widthPx: Float, val heightPx: Float, val density: Float)
    private data class Metrics(val intended: Long, val vsync: Long, val gpu: Long, val total: Long,
        val deadline: Long, val firstDraw: Long, val drops: Int)
    private class Evidence {
        val frames = mutableListOf<Metrics>()
        val snapshots = mutableListOf<Snapshot>()
        val epochs = mutableListOf<Epoch>()
        val errors = mutableListOf<String>()
        val sourceDraws = mutableListOf<Tick>()
        val bounds = mutableMapOf<Fixture, Rect>()
        @Synchronized fun frame(value: Metrics) { frames += value }
        @Synchronized fun snapshot(value: Snapshot) { snapshots += value }
        @Synchronized fun error(value: Throwable) { errors += value.stackTraceToString() }
        @Synchronized fun sourceDraw(value: Tick) { sourceDraws += value }
        @Synchronized fun bound(fixture: Fixture, rect: Rect) { bounds[fixture] = rect }
    }

    @Test fun compareFullProductionRenderEffectWithRealWindowGpuFrames() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Opt in with glassExactPerf=true", args.getString("glassExactPerf") == "true")
        val runId = (args.getString("glassExactPerfRunId") ?: System.currentTimeMillis().toString())
            .replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "glass-exact-performance/$runId")
            .apply { check(mkdirs() || isDirectory) }
        val source = LiquidGlassShader.shaderSource(instrumentation.targetContext)
        File(directory, "production-unmodified.agsl").writeText(source)
        check(source.contains("return half4(min(glass, float3(coverage)), coverage);"))
        val measuredFrames = (args.getString("glassExactPerfFrames")?.toIntOrNull() ?: 180).coerceIn(160, 600)
        val rounds = (args.getString("glassExactPerfRounds")?.toIntOrNull() ?: 1).coerceIn(1, 2)
        val warmupFrames = 30
        val thread = HandlerThread("glass-exact-frame-metrics").apply { start() }
        val metricsHandler = Handler(thread.looper)
        val metadata = JSONObject().put("run_id", runId).put("shader_sha256", sha(source))
            .put("scope", "Same full production RenderEffect; mode-only GPU A/B, exact/profile CPU preparation retained")
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}").put("api", Build.VERSION.SDK_INT)
            .put("sequence", "ABBA".repeat(rounds)).put("warmup_frames_per_block", warmupFrames)
            .put("requested_measured_callbacks_per_block", measuredFrames)
            .put("minimum_attributable_frames_per_fixture_mode", 120)
            .put("boundary_guard_callbacks_each_end", 4)
            .put("attribution", "Both INTENDED_VSYNC_TIMESTAMP and VSYNC_TIMESTAMP must lie in one stable measured epoch; first-draw and boundary frames excluded. Epoch must have an actual matching mode RenderEffect snapshot before its stable interval.")
            .put("gpu_unavailable", "Negative GPU_DURATION is retained verbatim and excluded only from GPU distributions; never converted to zero")
            .put("limits", "Whole-window FrameMetrics, not isolated GPU instruction time; no threshold asserts one mode is fast enough. Same-shader comparison cannot measure shared new shader/uniform overhead versus the old asset or CPU increment.")
        val listener = Window.OnFrameMetricsAvailableListener { _, frame, dropped ->
            // Android reuses FrameMetrics. Read every needed long before returning from this callback.
            evidence.frame(Metrics(frame.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP),
                frame.getMetric(FrameMetrics.VSYNC_TIMESTAMP), frame.getMetric(FrameMetrics.GPU_DURATION),
                frame.getMetric(FrameMetrics.TOTAL_DURATION), frame.getMetric(FrameMetrics.DEADLINE),
                frame.getMetric(FrameMetrics.FIRST_DRAW_FRAME), dropped))
        }
        var attached = false
        var driver: Driver? = null
        Log.i("GlassExactPerf", "BEGIN $runId shader=${sha(source)}")
        try {
            compose.setContent { MaterialTheme { Surface(source) } }
            compose.waitForIdle()
            instrumentation.runOnMainSync {
                metadata.put("refresh_hz", view.display?.refreshRate ?: -1f)
                metadata.put("thermal_status_before", (view.context.getSystemService(PowerManager::class.java))?.currentThermalStatus ?: -1)
                window.addOnFrameMetricsAvailableListener(listener, metricsHandler)
                attached = true
            }
            val captures = JSONArray()
            metadata.put("captures", captures)
            for (fixture in Fixture.entries) {
                instrumentation.runOnMainSync { scene.value = fixture }
                // PixelCopy and disk encoding happen while the real-time driver is stopped.
                val staticPhase = ((measuredFrames - 1) % 96 + 17) % 96
                var legacyBefore: JSONObject? = null
                for (legacy in listOf(true, false)) {
                    instrumentation.runOnMainSync { tick.value = Tick(-1, legacy, staticPhase, 0L) }
                    compose.waitForIdle()
                    val capture = capture(directory, fixture, legacy)
                    captures.put(capture)
                    if (legacy) legacyBefore = capture
                }
                val epochs = mutableListOf<Epoch>()
                repeat(rounds) {
                    for (legacy in listOf(true, false, false, true)) {
                        epochs += Epoch(evidence.epochs.size + epochs.size, fixture, legacy, true, warmupFrames)
                        epochs += Epoch(evidence.epochs.size + epochs.size, fixture, legacy, false, measuredFrames)
                    }
                }
                evidence.epochs += epochs
                val complete = CountDownLatch(1)
                instrumentation.runOnMainSync {
                    driver = Driver(epochs, complete).also { it.start() }
                }
                check(complete.await(90, TimeUnit.SECONDS)) { "$fixture real-vsync driver timed out" }
                instrumentation.runOnMainSync { driver?.stop(); driver = null }
                // A post-run PixelCopy waits for HWUI/GPU commit outside all measured epochs.
                compose.waitForIdle()
                val after = capture(directory, fixture, true, "after-timing")
                captures.put(after)
                check(after.getString("surface_pixels_sha256") != legacyBefore?.getString("surface_pixels_sha256")) {
                    "$fixture same-mode surface pixels did not change between distinct visible input phases"
                }
                val drained = CountDownLatch(1)
                metricsHandler.post { drained.countDown() }
                check(drained.await(5, TimeUnit.SECONDS))
            }
            metadata.put("captures", captures)
        } catch (failure: Throwable) {
            evidence.error(failure)
        } finally {
            instrumentation.runOnMainSync {
                driver?.stop()
                if (attached) window.removeOnFrameMetricsAvailableListener(listener)
                if (::view.isInitialized) metadata.put("thermal_status_after",
                    view.context.getSystemService(PowerManager::class.java)?.currentThermalStatus ?: -1)
            }
            val drained = CountDownLatch(1)
            metricsHandler.post { drained.countDown() }
            drained.await(5, TimeUnit.SECONDS)
            thread.quitSafely()
            // Preserve source/report even if AGSL compilation, a surface, or metric collection fails.
            try {
                val fd = instrumentation.uiAutomation.executeShellCommand(
                    "logcat -d -v threadtime --pid=${android.os.Process.myPid()} LiquidGlassEffect:W GlassExactPerf:I '*:S'")
                ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
                    File(directory, "runtime-logcat.txt").outputStream().use { output -> input.copyTo(output) }
                }
                val log = File(directory, "runtime-logcat.txt").readText()
                val begin = log.lastIndexOf("BEGIN $runId ")
                metadata.put("runtime_log_scope", if (begin >= 0) "THIS_PID_AFTER_UNIQUE_BEGIN" else "BEGIN_NOT_RETAINED_NO_ZERO_FALLBACK_CLAIM")
                if (begin >= 0) {
                    val count = log.substring(begin).lineSequence().count { it.contains("LiquidGlassEffect") && it.contains("RuntimeShader fallback to blur+vibrancy") }
                    metadata.put("runtime_fallback_entries_after_begin", count)
                    if (count > 0) evidence.errors += "$count production LiquidGlassEffect fallbacks in this run's retained log"
                }
            } catch (failure: Throwable) { File(directory, "logcat-unavailable.txt").writeText(failure.stackTraceToString()) }
            writeEvidence(directory, metadata)
            Log.i("GlassExactPerf", "END $runId artifacts=${directory.absolutePath}")
        }
        assertTrue("Render/collection errors; retained artifacts=$directory\n${evidence.errors.joinToString("\n")}", evidence.errors.isEmpty())
    }

    private inner class Driver(private val epochs: List<Epoch>, private val complete: CountDownLatch) : Choreographer.FrameCallback {
        private var index = 0
        private var frame = 0
        private var stopped = false
        fun start() { Choreographer.getInstance().postFrameCallback(this) }
        fun stop() { stopped = true; Choreographer.getInstance().removeFrameCallback(this) }
        override fun doFrame(frameTimeNanos: Long) {
            if (stopped) return
            if (frame == epochs[index].frames) {
                epochs[index].endNs = frameTimeNanos
                index++
                frame = 0
                if (index == epochs.size) { stopped = true; complete.countDown(); return }
            }
            val epoch = epochs[index]
            epoch.vsyncs += frameTimeNanos
            // Identical per-block moving input sequence; a static retained RenderNode cannot win.
            tick.value = Tick(epoch.id, epoch.legacy, frame % 96, frameTimeNanos)
            frame++
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    @Composable private fun Surface(source: String) {
        val currentView = LocalView.current
        val context = LocalContext.current
        SideEffect {
            view = currentView
            var activity = currentView.context
            while (activity !is Activity && activity is ContextWrapper) activity = activity.baseContext
            window = (activity as Activity).window
        }
        val fixture = scene.value
        val backdrop = rememberLayerBackdrop()
        val style = if (fixture == Fixture.Sheet) LiquidGlassStyle.sheet(false)
            else LiquidGlassStyle.menu(MaterialTheme.colorScheme, false)
        val rim = if (fixture == Fixture.Sheet) LiquidGlassRim.sheet() else LiquidGlassRim.menu(false)
        val shape = remember(fixture) { ProbeShape(fixture) }
        val options = remember { BackdropRenderOptions(effectKey = { tick.value }) }
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFF4F4F7))) {
            val fit = minOf(1f, (maxWidth.value - 16f) / fixture.widthDp, (maxHeight.value - 16f) / fixture.heightDp)
            Canvas(Modifier.matchParentSize().layerBackdrop(backdrop, recordKey = { tick.value })) {
                val current = tick.value
                drawMovingPattern(current.phase)
                evidence.sourceDraw(current)
            }
            Box(Modifier.align(Alignment.Center).requiredSize((fixture.widthDp * fit).dp, (fixture.heightDp * fit).dp)
                .onGloballyPositioned { evidence.bound(fixture, it.boundsInWindow()) }
                .drawPlainBackdrop(backdrop = backdrop, shape = { shape }, renderOptions = options,
                    effects = probeEffects@{
                        if (!size.width.isFinite() || !size.height.isFinite() || size.width <= 0f || size.height <= 0f ||
                            !density.isFinite() || density <= 0f) return@probeEffects
                        val current = tick.value
                        try {
                            val resolved = shape.resolve(size, density)
                            fun dp(path: Path) = Path().apply { addPath(path)
                                transform(androidx.compose.ui.graphics.Matrix().apply { scale(1f / density, 1f / density) }) }
                            val outline = dp(resolved.outline.path)
                            val dual = if (fixture.dual) dp(resolved.sources[0].path) to dp(resolved.sources[1].path) else null
                            check(shape.exactContoursFor(resolved.outline)?.size == if (fixture.dual) 2 else 1)
                            // Actual production CPU prep, full shader, blur/haze/source atlas and padding.
                            liquidGlass(context = context, style = style, outline = outline, dualContours = dual, rim = rim)
                            val shader = obtainRuntimeShader("LiquidGlassShader", source)
                            val mode = fixture.mode(current.legacy)
                            shader.setFloatUniform("uOutlined", mode.toFloat())
                            // RenderEffect snapshots uniforms. Replace, never chain a second material pass.
                            renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(
                                shader.asAndroidRuntimeShader(), "uTexture").asComposeRenderEffect()
                            evidence.snapshot(Snapshot(current.epoch, fixture, mode, current.vsyncNs,
                                System.nanoTime(), size.width, size.height, density))
                        } catch (failure: Throwable) { evidence.error(failure) }
                    }))
        }
    }

    private data class Resolved(val size: Size, val density: Float, val sources: List<LiquidGlassContourOutline>,
        val outline: Outline.Generic)
    private class ProbeShape(private val fixture: Fixture) : Shape, LiquidGlassExactContourShape {
        private var cached: Resolved? = null
        fun resolve(size: Size, density: Float): Resolved {
            cached?.takeIf { it.size == size && it.density == density }?.let { return it }
            val sx = size.width / fixture.widthDp
            val sy = size.height / fixture.heightDp
            fun contour(rect: Rect, top: Float, bottom: Float = top): LiquidGlassContourOutline {
                val scale = minOf(sx, sy)
                val r = Rect(rect.left * sx, rect.top * sy, rect.right * sx, rect.bottom * sy)
                return LiquidGlassContourOutline(LiquidGlassSuperellipse.buildPath(
                    LiquidGlassSuperellipse.Rect2(r.left, r.top, r.right, r.bottom),
                    LiquidGlassSuperellipse.Radii(LiquidGlassSuperellipse.Size2(top * scale, top * scale),
                        LiquidGlassSuperellipse.Size2(top * scale, top * scale),
                        LiquidGlassSuperellipse.Size2(bottom * scale, bottom * scale),
                        LiquidGlassSuperellipse.Size2(bottom * scale, bottom * scale))))
            }
            val sources = when (fixture) {
                Fixture.Menu -> listOf(contour(Rect(0f, 0f, 251f, 147f), 32f))
                Fixture.Sheet -> listOf(contour(Rect(0f, 0f, 343f, 420f), 36f, 20f))
                Fixture.Language -> listOf(contour(Rect(10.5f, 10.5f, 380.5f, 61.833332f), 8f),
                    contour(Rect(70.25f, 80.5f, 320.25f, 310.5f), 26f))
            }
            check(sources.all { it.exact != null })
            val path = if (sources.size == 1) sources[0].path else Path.combine(PathOperation.Union, sources[0].path, sources[1].path)
            return Resolved(size, density, sources, Outline.Generic(path)).also { cached = it }
        }
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = resolve(size, density.density).outline
        override fun exactContoursFor(outline: Outline): List<LiquidGlassExactContour>? = cached
            ?.takeIf { it.outline === outline }?.sources?.map { checkNotNull(it.exact) }
    }

    private fun DrawScope.drawMovingPattern(phase: Int) {
        drawRect(Color(0xFFF0E4CE))
        val pitch = 16.dp.toPx()
        var x = -pitch * 2 + phase / 96f * pitch * 2
        while (x < size.width) {
            drawRect(Color(0xFF19385C), Offset(x, 0f), Size(pitch, size.height))
            x += pitch * 2
        }
        drawRect(Color(0xFFED6475), Offset(size.width * .55f, size.height * .15f), Size(size.width * .30f, size.height * .25f))
        drawRect(Color(0xFF69C5A6), Offset(size.width * .15f, size.height * .65f), Size(size.width * .45f, size.height * .2f))
    }

    private fun capture(directory: File, fixture: Fixture, legacy: Boolean, suffix: String = "before-timing"): JSONObject {
        val bitmap = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
        val done = CountDownLatch(1)
        var result = -1
        PixelCopy.request(window, bitmap, { result = it; done.countDown() }, Handler(Looper.getMainLooper()))
        check(done.await(5, TimeUnit.SECONDS) && result == PixelCopy.SUCCESS) { "Window PixelCopy failed: $result" }
        val name = "${fixture.name.lowercase()}-${if (legacy) "legacy" else "exact"}-$suffix"
        File(directory, "$name-window.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val bounds = checkNotNull(evidence.bounds[fixture])
        val left = floor(bounds.left).toInt().coerceIn(0, bitmap.width - 1)
        val top = floor(bounds.top).toInt().coerceIn(0, bitmap.height - 1)
        val right = ceil(bounds.right).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = ceil(bounds.bottom).toInt().coerceIn(top + 1, bitmap.height)
        val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        val pixels = ByteBuffer.allocate(crop.byteCount)
        crop.copyPixelsToBuffer(pixels)
        val pixelHash = MessageDigest.getInstance("SHA-256").digest(pixels.array())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        File(directory, "$name-surface.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (crop !== bitmap) crop.recycle()
        bitmap.recycle()
        val last = evidence.snapshots.lastOrNull { it.fixture == fixture }
        check(last?.mode == fixture.mode(legacy)) { "$fixture requested mode ${fixture.mode(legacy)} lacks a matching actual RenderEffect snapshot" }
        return JSONObject().put("name", name).put("window_bounds_px", JSONArray(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)))
            .put("surface_pixels_sha256", pixelHash).put("source_phase", tick.value.phase)
            .put("requested_mode", fixture.mode(legacy)).put("last_snapshot_mode", last?.mode ?: -1)
            .put("snapshot_size_px", JSONArray(listOf(last?.widthPx ?: -1f, last?.heightPx ?: -1f)))
    }

    private fun writeEvidence(directory: File, metadata: JSONObject) {
        val epochs = evidence.epochs.toList()
        val snapshots = evidence.snapshots.toList()
        val sourceDraws = evidence.sourceDraws.toList()
        fun stable(epoch: Epoch): Pair<Long, Long>? {
            if (epoch.warmup || epoch.vsyncs.size < 10) return null
            val first = snapshots.firstOrNull { it.epoch == epoch.id && it.mode == epoch.fixture.mode(epoch.legacy) } ?: return null
            val start = maxOf(epoch.vsyncs[4], first.vsyncNs)
            val end = minOf(epoch.vsyncs[epoch.vsyncs.size - 4], epoch.endNs)
            return if (start < end) start to end else null
        }
        val intervals = epochs.mapNotNull { e -> stable(e)?.let { Triple(e, it.first, it.second) } }
        val groups = mutableMapOf<Pair<Fixture, Boolean>, MutableList<Metrics>>()
        val csv = StringBuilder("intended_vsync_ns,vsync_ns,gpu_duration_ns,total_duration_ns,deadline_ns,first_draw,drop_count,epoch,fixture,mode,accepted,exclusion\n")
        for (frame in evidence.frames.toList()) {
            val interval = intervals.firstOrNull { (_, start, end) -> frame.intended >= start && frame.intended < end && frame.vsync >= start && frame.vsync < end }
            val exclusion = when {
                frame.intended < 0 || frame.vsync < 0 -> "timestamp_unavailable"
                frame.firstDraw != 0L -> "first_draw_or_unavailable"
                interval == null -> "warmup_boundary_or_outside_stable_epoch"
                frame.total < 0 -> "total_duration_unavailable"
                else -> ""
            }
            val epoch = interval?.first
            if (exclusion.isEmpty() && epoch != null) groups.getOrPut(epoch.fixture to epoch.legacy) { mutableListOf() }.add(frame)
            csv.append(listOf(frame.intended, frame.vsync, frame.gpu, frame.total, frame.deadline, frame.firstDraw, frame.drops,
                epoch?.id ?: -1, epoch?.fixture?.name ?: "", epoch?.let { it.fixture.mode(it.legacy) } ?: -1, exclusion.isEmpty(), exclusion).joinToString(",")).append('\n')
        }
        File(directory, "frames.csv").writeText(csv.toString())
        File(directory, "snapshots.csv").writeText("epoch,fixture,mode,source_vsync_ns,applied_system_ns,width_px,height_px,density\n" +
            snapshots.joinToString("\n") { "${it.epoch},${it.fixture},${it.mode},${it.vsyncNs},${it.appliedNs},${it.widthPx},${it.heightPx},${it.density}" } + "\n")
        File(directory, "source-draws.csv").writeText("epoch,legacy,phase,source_vsync_ns\n" +
            sourceDraws.joinToString("\n") { "${it.epoch},${it.legacy},${it.phase},${it.vsyncNs}" } + "\n")
        val summaries = JSONArray()
        for (fixture in Fixture.entries) for (legacy in listOf(true, false)) {
            val frames = groups[fixture to legacy].orEmpty()
            val gpu = frames.map { it.gpu }.filter { it >= 0 }
            val measuredIds = epochs.filter { it.fixture == fixture && it.legacy == legacy && !it.warmup }.map { it.id }.toSet()
            val inputPhases = sourceDraws.filter { it.epoch in measuredIds }.map { it.phase }.toSet().size
            if (frames.size < 120) evidence.errors += "$fixture mode=${fixture.mode(legacy)} has only ${frames.size} attributable frames; minimum 120"
            if (gpu.isNotEmpty() && gpu.size < 120) evidence.errors += "$fixture mode=${fixture.mode(legacy)} has only ${gpu.size} available GPU samples; minimum 120"
            if (inputPhases < 2) evidence.errors += "$fixture mode=${fixture.mode(legacy)} lacks changing source draw evidence"
            summaries.put(JSONObject().put("fixture", fixture.name).put("mode", fixture.mode(legacy)).put("attributable_frames", frames.size)
                .put("gpu_available_frames", gpu.size).put("gpu_unavailable_frames", frames.count { it.gpu < 0 })
                .put("distinct_source_phases_drawn_in_measured_epochs", inputPhases)
                .put("gpu_evidence_status", if (gpu.isEmpty()) "UNAVAILABLE" else if (gpu.size < 120) "INSUFFICIENT_GPU_SAMPLES" else "MEASURED_NO_PERFORMANCE_THRESHOLD")
                .put("gpu_ns", distribution(gpu)).put("total_ns", distribution(frames.map { it.total }))
                .put("deadline_available_frames", frames.count { it.deadline >= 0 })
                .put("total_exceeds_deadline", frames.count { it.deadline >= 0 && it.total > it.deadline })
                .put("metric_callbacks_dropped_before_reported_frames", frames.sumOf { it.drops }))
        }
        metadata.put("groups", summaries).put("raw_metric_rows", evidence.frames.size)
            .put("all_callback_drops", evidence.frames.sumOf { it.drops })
            .put("epochs", JSONArray(epochs.map { e -> JSONObject().put("id", e.id).put("fixture", e.fixture.name)
                .put("mode", e.fixture.mode(e.legacy)).put("warmup", e.warmup).put("callbacks", e.vsyncs.size)
                .put("start_vsync_ns", e.vsyncs.firstOrNull() ?: -1L).put("end_vsync_ns", e.endNs)
                .put("stable_start_ns", stable(e)?.first ?: -1L).put("stable_end_ns", stable(e)?.second ?: -1L) }))
            .put("errors", JSONArray(evidence.errors)).put("performance_pass_claim", false)
        File(directory, "result.json").writeText(metadata.toString(2))
        File(directory, "errors.txt").writeText(evidence.errors.joinToString("\n\n"))
    }

    private fun distribution(values: List<Long>): JSONObject {
        val sorted = values.sorted()
        fun percentile(fraction: Double): Any = if (sorted.isEmpty()) JSONObject.NULL
            else sorted[(ceil(sorted.size * fraction).toInt() - 1).coerceIn(0, sorted.lastIndex)]
        return JSONObject().put("count", sorted.size).put("median", percentile(.5)).put("p90", percentile(.9)).put("p95", percentile(.95))
    }
    private fun sha(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
