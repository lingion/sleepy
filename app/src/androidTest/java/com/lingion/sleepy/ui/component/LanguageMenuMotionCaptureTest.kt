package com.lingion.sleepy.ui.component

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.Choreographer
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inspector.WindowInspector
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.ui.component.liquidglass.GlassBackground
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.component.liquidglass.GlassLanguageMenu
import com.lingion.sleepy.ui.component.liquidglass.LanguageMenuHandoffStateKey
import com.lingion.sleepy.ui.component.liquidglass.LanguageMenuPresentationDiagnostics
import com.lingion.sleepy.ui.component.liquidglass.LanguageMenuVisualStateKey
import com.lingion.sleepy.ui.component.liquidglass.LiquidGlassShader
import com.lingion.sleepy.ui.theme.LocalDarkTheme
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import com.kyant.backdrop.BackdropDiagnostics
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Real-time product-menu fixtures. Start screenrecord externally for the motion method.
 * A lifecycle Choreographer composition owns animation time. The recorded sequence never captures
 * screenshots or advances a test clock. The separate opt-in GPU regression captures only settled,
 * pointer-free states, after HWUI commit, without changing the recorded motion path.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class LanguageMenuMotionCaptureTest {
    @get:Rule val compose = createComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val trace = ConcurrentLinkedQueue<JSONObject>()
    private val selections = ConcurrentLinkedQueue<String>()
    private val dismissals = ConcurrentLinkedQueue<JSONObject>()
    private val watched = IdentityHashMap<View, ViewTreeObserver.OnDrawListener>()
    private val touchObservers = IdentityHashMap<View, Unit>()
    private val rowSequence = AtomicInteger()
    private val languages = listOf(
        "zh-CN" to "简体中文", "zh-TW" to "繁體中文", "en" to "English",
        "ja" to "日本語", "es" to "Español",
    )
    private var neutral = Color(.94f, .94f, .96f)
    private var selected by mutableStateOf("zh-CN")
    private var syncMarker by mutableIntStateOf(0)
    private lateinit var activity: Activity
    private var realView: ComposeView? = null
    private var realRecomposer: Recomposer? = null
    private var density = 1f
    private var fontScale = 1f
    private var cardWidthDp: Float? = null
    private var cardTopDp: Float? = null
    @Volatile private var scenario = "setup"
    private var running = false
    private var frameNumber = 0
    private var drawNumber = 0
    private var lastFrameNs: Long? = null
    private var lastDeliveredUptimeMs: Long? = null
    private var lastDeliveredEventTimeMs: Long? = null
    private var lastDeliveredAction: Int? = null
    @Volatile private var lastInjectedUptimeMs: Long? = null
    private var startUptimeMs = 0L
    private var activeDownTime = 0L
    private var activePointer = false
    private var lastPointer = Offset.Zero

    @Test fun recordFiveLanguageMenuMotion() {
        val args = InstrumentationRegistry.getArguments()
        val recordOpticalDiagnostics = args.getString("languageOpticalDiagnostics") == "true"
        assumeTrue("Manual capture only: pass languageCapture=true", args.getString("languageCapture") == "true")
        fun positiveFloat(name: String): Float? = args.getString(name)?.toFloat()?.also {
            require(it.isFinite() && it > 0f) { "$name must be finite and positive" }
        }
        cardWidthDp = positiveFloat("languageCardWidthDp")
        cardTopDp = args.getString("languageCardTopDp")?.toFloat()?.also {
            require(it.isFinite() && it >= 0f) { "languageCardTopDp must be finite and nonnegative" }
        }
        val tapHoldMs = (args.getString("languageTapHoldMs")?.toLong() ?: 67L).also {
            require(it in 1..2_000) { "languageTapHoldMs must be 1..2000" }
        }
        val settleMs = (args.getString("languageSettleMs")?.toLong() ?: 850L).also {
            require(it in 300..5_000) { "languageSettleMs must be 300..5000" }
        }
        val longPressHoldMs = (args.getString("languageLongPressHoldMs")?.toLong() ?: 450L).also {
            require(it in 320..2_000) { "languageLongPressHoldMs must be 320..2000" }
        }
        val runId = (args.getString("languageRunId") ?: "language-${System.currentTimeMillis()}")
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "language-menu-motion/$runId")
        assertTrue("Create capture output", directory.isDirectory || directory.mkdirs())
        startUptimeMs = SystemClock.uptimeMillis()

        installRealtimeFixture()
        val priorPresentationObserver = LanguageMenuPresentationDiagnostics.observer
        LanguageMenuPresentationDiagnostics.observer = { sessionId, event, frameTimeNanos, windowIdentity, windowClass ->
            add("presentation_event").put("session_id", sessionId).put("stage", event)
                .put("frame_time_nanos", frameTimeNanos ?: JSONObject.NULL)
                .put("window_identity", windowIdentity ?: JSONObject.NULL)
                .put("window_class", windowClass ?: JSONObject.NULL)
        }
        val priorOpticalObserver = BackdropDiagnostics.observer
        if (recordOpticalDiagnostics) BackdropDiagnostics.observer = { name, value ->
            if (name.startsWith("Sample.") && (name.contains("Geometry") || name.contains("EffectRebuilt"))) {
                add("optical_diagnostic").put("name", name).put("value", value)
            }
        }
        try {
            waitFor("language card laid out") { findTag("general_language_card")?.bounds?.width?.let { it > 0f } == true }
            instrumentation.runOnMainSync {
                running = true
                Choreographer.getInstance().postFrameCallback(frameCallback)
            }
            writeSession(directory, tapHoldMs, longPressHoldMs, settleMs)
            SystemClock.sleep(500)
            mark("recording-sync")
            SystemClock.sleep(250)

            scenario = "open-select-english"
            mark(scenario)
            open(tapHoldMs, settleMs)
            assertFirstOpenPresentationHandoff()
            tap(tagCenter("general_language#en"), tapHoldMs, "select-en")
            waitFor("English selection is accepted") { selected == "en" }
            waitFor("English popup exit completes") { !menuPresent() && dismissals.lastOrNull()?.optString("code") == "en" }
            SystemClock.sleep(settleMs)
            outcome("selected English")
            assertEquals("Initial selection callback", listOf("en"), selections.toList())
            assertSelectionPrecedesDismissal("en")

            scenario = "open-outside-dismiss"
            mark(scenario)
            open(tapHoldMs, settleMs)
            val callbacksBeforeOutside = selections.size
            val dismissalsBeforeOutside = dismissals.size
            tap(outsidePoint(), tapHoldMs, "outside-dismiss")
            waitFor("Outside click removes popup") { !menuPresent() && dismissals.size == dismissalsBeforeOutside + 1 }
            SystemClock.sleep(settleMs)
            outcome("outside cancellation")
            assertEquals("Outside dismissal must not select", callbacksBeforeOutside, selections.size)
            assertEquals("Outside dismissal preserves English", "en", selected)
            assertTrue("Outside dismissal reports no selection", dismissals.last().isNull("code"))

            scenario = "open-select-same-item"
            mark(scenario)
            open(tapHoldMs, settleMs)
            val callbacksBeforeSame = selections.size
            val dismissalsBeforeSame = dismissals.size
            tap(tagCenter("general_language#en"), tapHoldMs, "select-same-en")
            waitFor("Same item removes popup") { !menuPresent() && dismissals.size == dismissalsBeforeSame + 1 }
            SystemClock.sleep(settleMs)
            outcome("same item accepted once before dismissal")
            assertEquals("Same item is accepted once", callbacksBeforeSame + 1, selections.size)
            assertEquals("Same item preserves English", "en", selected)
            assertSelectionPrecedesDismissal("en")

            scenario = "long-press-hold-anchor-release"
            mark(scenario)
            val callbacksBeforeHold = selections.size
            val dismissalsBeforeHold = dismissals.size
            val heldAnchor = longPressOpen(longPressHoldMs, settleMs, "hold-anchor")
            inject(MotionEvent.ACTION_UP, heldAnchor, SystemClock.uptimeMillis(), "hold-anchor-up")
            SystemClock.sleep(settleMs)
            outcome("holding original anchor then lifting keeps menu open")
            assertTrue("Stationary long press release keeps menu open", readOnMain { menuPresent() })
            assertEquals("Stationary hold does not select a row beneath the original point", callbacksBeforeHold, selections.size)
            assertEquals("Stationary hold does not dismiss", dismissalsBeforeHold, dismissals.size)
            tap(outsidePoint(), tapHoldMs, "hold-cleanup-outside")
            waitFor("Hold cleanup removes menu") { !menuPresent() && dismissals.size == dismissalsBeforeHold + 1 }
            SystemClock.sleep(settleMs)

            scenario = "long-press-slide-select-japanese"
            mark(scenario)
            val callbacksBeforeSlide = selections.size
            val dismissalsBeforeSlide = dismissals.size
            val slideAnchor = longPressOpen(longPressHoldMs, settleMs, "slide-ja")
            val japanese = tagCenter("general_language#ja")
            val slideStart = SystemClock.uptimeMillis()
            repeat(12) { index ->
                val fraction = (index + 1) / 12f
                inject(MotionEvent.ACTION_MOVE, slideAnchor + (japanese - slideAnchor) * fraction,
                    slideStart + (index + 1) * 16L, "slide-ja-move")
            }
            inject(MotionEvent.ACTION_UP, japanese, slideStart + 13 * 16L, "slide-ja-up")
            waitFor("Japanese slide selection is accepted") { selected == "ja" }
            waitFor("Japanese slide exit completes") { !menuPresent() && dismissals.size == dismissalsBeforeSlide + 1 }
            SystemClock.sleep(settleMs)
            outcome("original long-press stream slides to Japanese and commits on release")
            assertEquals("Long-press slide accepts exactly once", callbacksBeforeSlide + 1, selections.size)
            assertEquals("Long-press slide selects Japanese", "ja", selections.last())
            assertSelectionPrecedesDismissal("ja")

            scenario = "open-provisional-slide-cancel"
            mark(scenario)
            open(tapHoldMs, settleMs)
            val callbacksBeforeCancel = selections.size
            val inside = tagCenter("general_language#ja")
            val outside = outsidePoint()
            val beganAt = SystemClock.uptimeMillis()
            inject(MotionEvent.ACTION_DOWN, inside, beganAt, "slide-cancel-down")
            repeat(8) { index ->
                sleepUntil(beganAt + (index + 1) * 16L)
                val fraction = (index + 1) / 8f
                inject(MotionEvent.ACTION_MOVE, inside + (outside - inside) * fraction,
                    beganAt + (index + 1) * 16L, "slide-cancel-move")
            }
            inject(MotionEvent.ACTION_CANCEL, outside, SystemClock.uptimeMillis(), "slide-cancel-cancel")
            SystemClock.sleep(settleMs)
            outcome("provisional input cancellation; menu may remain open")
            assertEquals("Cancelled pointer must not select", callbacksBeforeCancel, selections.size)
            if (readOnMain { menuPresent() }) {
                tap(outsidePoint(), tapHoldMs, "cleanup-outside-dismiss")
                waitFor("Cleanup removes menu") { !menuPresent() }
            }
            SystemClock.sleep(settleMs)
            outcome("capture complete")
        } finally {
            if (recordOpticalDiagnostics) BackdropDiagnostics.observer = priorOpticalObserver
            if (activePointer) runCatching { inject(MotionEvent.ACTION_CANCEL, lastPointer,
                SystemClock.uptimeMillis(), "cleanup-cancel") }
            instrumentation.runOnMainSync {
                running = false
                Choreographer.getInstance().removeFrameCallback(frameCallback)
                watched.forEach { (view, listener) ->
                    if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(listener)
                }
                touchObservers.keys.forEach { it.setOnTouchListener(null) }
                touchObservers.clear()
                watched.clear()
                realView?.disposeComposition()
                realRecomposer?.cancel()
            }
            LanguageMenuPresentationDiagnostics.observer = priorPresentationObserver
            File(directory, "trace.jsonl").bufferedWriter().use { writer ->
                trace.forEach { writer.appendLine(it.toString()) }
            }
            File(directory, "result.json").writeText(JSONObject()
                .put("selected", selected).put("callbacks", JSONArray(selections.toList()))
                .put("dismissals", JSONArray(dismissals.toList()))
                .put("trace_rows", trace.size).toString(2))
        }
    }

    /**
     * Semantics alone still reported five rows when a backdrop placement layer clipped the real
     * material after the first row. This checks the composited GPU pixels of every row instead.
     * Run separately with languageGpuCheck=true; no capture is inserted into the motion method.
     */
    @Test fun fiveLanguageRowsKeepGpuMaterialAfterNaturalOpen() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Natural GPU fixture: pass languageGpuCheck=true", args.getString("languageGpuCheck") == "true")
        cardWidthDp = (args.getString("languageCardWidthDp")?.toFloat() ?: 362f).also {
            require(it.isFinite() && it > 0f) { "languageCardWidthDp must be finite and positive" }
        }
        cardTopDp = (args.getString("languageCardTopDp")?.toFloat() ?: 180f).also {
            require(it.isFinite() && it >= 0f) { "languageCardTopDp must be finite and nonnegative" }
        }
        val runId = (args.getString("languageRunId") ?: "language-gpu-${System.currentTimeMillis()}")
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "language-menu-motion/$runId")
        assertTrue("Create GPU fixture output", directory.isDirectory || directory.mkdirs())
        startUptimeMs = SystemClock.uptimeMillis()
        val report = JSONObject().put("schema", 1).put("fixture_only", true)
            .put("clock", "Lifecycle-aware real Choreographer recomposer; no Compose test-clock advances")
            .put("capture", "UiAutomation composited display; only after public pointer UP, natural settling and HWUI commit")
            .put("roi", "Each row's right interior: right-48dp..right-30dp, centerY±8dp; excludes labels, checkmark, divider and rounded rim")
            .put("threshold_scope", "Fixture coverage regression only; not native optical matching or inferred material alpha")
            .put("minimum_mean_abs_delta_from_closed_8bit", 6.0)
            .put("maximum_mean_rgb_distance_from_first_row_8bit", 5.0)
        var baseline: Bitmap? = null
        var expanded: Bitmap? = null
        val gpuBackground = args.getString("languageGpuBackground") ?: "neutral"
        when (gpuBackground) {
            "neutral" -> Unit
            // Explicit coverage fixture: retain existing RGB thresholds as the material evolves.
            "contrast" -> neutral = Color(.65f, .65f, .67f)
            else -> error("Unknown languageGpuBackground: $gpuBackground")
        }
        report.put("fixture_background", gpuBackground)
        val shaderDiagnostic = args.getString("languageShaderDiagnostic")
        val restoreShader = installNaturalShaderDiagnostic(shaderDiagnostic, directory)
        report.put("shader_diagnostic", shaderDiagnostic ?: "none")
        try {
            installRealtimeFixture()
            scenario = "natural-gpu-baseline"
            waitFor("Language card laid out") { findTag("general_language_card")?.bounds?.width?.let { it > 0f } == true }
            SystemClock.sleep(400)
            writeSession(directory, 67L, 450L, 850L)
            val baselineImage = captureSettledGpu(directory, "closed-baseline")
            baseline = baselineImage

            scenario = "natural-gpu-expanded"
            open(67L, 850L)
            waitFor("All five rows finish natural expansion") {
                val menu = findTag("general_language_menu")
                val state = menu?.node?.config?.getOrNull(LanguageMenuVisualStateKey)
                state != null && state.alpha >= .999f && abs(state.scaleX - 1f) <= .001f &&
                    abs(state.scaleY - 1f) <= .001f && languages.all { (code, _) ->
                        findTag("general_language#$code")?.bounds?.height?.let { it >= 35f * density } == true
                    }
            }
            val rowBounds = readOnMain {
                languages.map { (code, _) -> code to requireNotNull(findTag("general_language#$code")).bounds }
            }
            val expandedImage = captureSettledGpu(directory, "expanded-five-rows")
            expanded = expandedImage
            assertEquals("GPU baseline width", baselineImage.width, expandedImage.width)
            assertEquals("GPU baseline height", baselineImage.height, expandedImage.height)
            val probes = rowBounds.map { (code, bounds) ->
                val roi = Rect(bounds.right - 48f * density, bounds.center.y - 8f * density,
                    bounds.right - 30f * density, bounds.center.y + 8f * density)
                gpuRowProbe(code, bounds, roi, baselineImage, expandedImage)
            }
            val firstRgb = probes.first().meanRgb
            val rows = JSONArray()
            probes.forEach { probe ->
                val distanceFromFirst = probe.meanRgb.indices.sumOf { abs(probe.meanRgb[it] - firstRgb[it]) } / 3.0
                probe.record.put("mean_rgb_distance_from_first_row_8bit", distanceFromFirst)
                rows.put(probe.record)
            }
            report.put("rows", rows).put("density", density).put("font_scale", fontScale)
                .put("captured_without_active_pointer", !activePointer)
                .put("geometry_source", "Public transformed semantics are used only to locate foreground-free pixel probes; composited RGB is the coverage assertion")
            // Write all five actual measurements before any assertion, including the old failure.
            File(directory, "gpu-material-check.json").writeText(report.toString(2))
            assertTrue("GPU screenshot taken without an active pointer", !activePointer)
            probes.forEach { probe ->
                assertTrue("${probe.code}: glass must change actual row pixels from closed baseline; " +
                    "mean absolute delta=${probe.meanAbsDelta}", probe.meanAbsDelta >= 6.0)
                val firstDistance = probe.record.getDouble("mean_rgb_distance_from_first_row_8bit")
                assertTrue("${probe.code}: lower-row glass must cover the same neutral fixture as first row; " +
                    "RGB distance=$firstDistance", firstDistance <= 5.0)
            }
            assertEquals("Opening for pixel verification must not select a language", emptyList<String>(), selections.toList())
            report.put("passed", true)
        } finally {
            report.put("passed", report.optBoolean("passed", false))
            File(directory, "gpu-material-check.json").writeText(report.toString(2))
            if (activePointer) runCatching { inject(MotionEvent.ACTION_CANCEL, lastPointer,
                SystemClock.uptimeMillis(), "gpu-cleanup-cancel") }
            instrumentation.runOnMainSync {
                realView?.disposeComposition()
                realRecomposer?.cancel()
            }
            restoreShader()
            File(directory, "trace.jsonl").bufferedWriter().use { writer -> trace.forEach { writer.appendLine(it.toString()) } }
            baseline?.recycle()
            expanded?.recycle()
        }
    }

    /** Instrumentation-only source substitution; every production uniform and popup path stays live. */
    private fun installNaturalShaderDiagnostic(mode: String?, directory: File): () -> Unit {
        if (mode == null) return {}
        val original = LiquidGlassShader.shaderSource(instrumentation.targetContext)
        val finalReturn = "return half4(min(glass, float3(coverage)), coverage);"
        check(original.split(finalReturn).size == 2)
        val modified = when (mode) {
            "coordinates" -> original.replace(finalReturn, """
                if (uOutlined > 4.5) {
                    return half4(clamp(float3(point.x / 300.0, point.y / 300.0, uShape.y / 300.0), 0.0, 1.0), 1.0);
                }
                $finalReturn
            """.trimIndent())
            "contour-centers" -> original.replace(finalReturn, """
                if (uOutlined > 4.5) {
                    float bottom = uExact2[0].y;
                    for (int i = 3; i < 36; i += 2) bottom = max(bottom, uExact2[i].y);
                    return half4(clamp(float3(uExact1[0].w, uExact2[0].w, bottom) / 300.0, 0.0, 1.0), 1.0);
                }
                $finalReturn
            """.trimIndent())
            "mixed-geometry" -> original.replace(finalReturn, """
                if (uOutlined > 4.5) {
                    if (!exactFound) return half4(1.0, 0.0, 1.0, 1.0);
                    float3 code = float3(clamp(inset / 128.0, 0.0, 1.0), normal * 0.5 + 0.5);
                    return half4(0.5 * min(glass, float3(coverage)) + 0.5 * code, coverage);
                }
                $finalReturn
            """.trimIndent())
            "legacy-dispatch" -> original.replace("if (uOutlined > 3.5) {", "if (false) {")
            else -> error("Unknown languageShaderDiagnostic: $mode")
        }
        check(modified != original)
        File(directory, "instrumentation-source-$mode.agsl").writeText(modified)
        val field = LiquidGlassShader::class.java.getDeclaredField("cachedSource").apply { isAccessible = true }
        val previous = field.get(LiquidGlassShader)
        field.set(LiquidGlassShader, modified)
        return { field.set(LiquidGlassShader, previous) }
    }

    /**
     * Separate static held-state diagnostic: never inserted into the motion recording.
     * Native English-row held pixels show a capsule, including unchanged outside corners.
     * Exercise both a new pointer on an open row and the anchor's original drag stream.
     */
    @Test fun heldLanguageRowKeepsItsFourCornersOutsideTheHighlight() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Held GPU fixture: pass languageGpuCornerCheck=true",
            args.getString("languageGpuCornerCheck") == "true")
        cardWidthDp = 362f
        cardTopDp = 180f
        val runId = (args.getString("languageRunId") ?: "language-corners-${System.currentTimeMillis()}")
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "language-menu-motion/$runId")
        assertTrue("Create held GPU output", directory.isDirectory || directory.mkdirs())
        val results = JSONArray()
        val report = JSONObject().put("scope", "Actual settled GPU row highlight corners only; no natural motion or whole-menu/native material equivalence")
            .put("input", "Public direct row DOWN and original anchor long-press/drag; CANCEL must preserve selection")
            .put("rows", results)
        startUptimeMs = SystemClock.uptimeMillis()
        installRealtimeFixture()
        try {
            waitFor("Language card laid out") { findTag("general_language_card") != null }
            writeSession(directory, 67L, 450L, 850L)
            for (mode in listOf("direct-row", "anchor-drag")) {
                scenario = mode
                val anchor = if (mode == "anchor-drag") longPressOpen(450L, 850L, mode) else {
                    open(67L, 850L)
                    null
                }
                val before = captureSettledGpu(directory, "$mode-before", heldSnapshot = anchor != null)
                try {
                    val row = readOnMain { requireNotNull(findTag("general_language#en")).bounds }
                    if (anchor == null) {
                        inject(MotionEvent.ACTION_DOWN, row.center, SystemClock.uptimeMillis(), "$mode-down")
                    } else {
                        val beganAt = SystemClock.uptimeMillis()
                        repeat(12) { index ->
                            inject(MotionEvent.ACTION_MOVE, anchor + (row.center - anchor) * ((index + 1) / 12f),
                                beganAt + (index + 1) * 16L, "$mode-move")
                        }
                    }
                    SystemClock.sleep(700)
                    val held = captureSettledGpu(directory, "$mode-held", heldSnapshot = true)
                    try {
                        val probes = JSONArray()
                        fun probe(name: String, x: Float, y: Float, corner: Boolean) {
                            val px = x.roundToInt(); val py = y.roundToInt()
                            var total = 0.0
                            for (dy in -1..1) for (dx in -1..1) {
                                val prior = before.getPixel(px + dx, py + dy)
                                val actual = held.getPixel(px + dx, py + dy)
                                for (shift in listOf(16, 8, 0))
                                    total += abs(((actual ushr shift) and 255) - ((prior ushr shift) and 255))
                            }
                            probes.put(JSONObject().put("name", name).put("corner", corner)
                                .put("screen_center_px", JSONArray(listOf(px, py)))
                                .put("mean_abs_rgb_delta_8bit", total / 27.0))
                        }
                        val insetX = 4f * density
                        val insetY = 3f * density
                        probe("top-left", row.left + insetX, row.top + insetY, true)
                        probe("top-right", row.right - insetX, row.top + insetY, true)
                        probe("bottom-left", row.left + insetX, row.bottom - insetY, true)
                        probe("bottom-right", row.right - insetX, row.bottom - insetY, true)
                        probe("left-interior", row.left + insetX, row.center.y, false)
                        probe("right-interior", row.right - insetX, row.center.y, false)
                        results.put(JSONObject().put("mode", mode).put("row_screen_xywh_px", rect(row)).put("probes", probes))
                    } finally { held.recycle() }
                } finally {
                    before.recycle()
                    if (activePointer) inject(MotionEvent.ACTION_CANCEL, lastPointer, SystemClock.uptimeMillis(), "$mode-cancel")
                }
                SystemClock.sleep(250)
                assertEquals("Cancelled held row must not select", emptyList<String>(), selections.toList())
                assertTrue("Cancelled held row leaves menu available", readOnMain { menuPresent() })
                tap(outsidePoint(), 67L, "$mode-dismiss")
                waitFor("Held diagnostic cleanup removes menu") { !menuPresent() }
            }
            File(directory, "gpu-corner-check.json").writeText(report.put("density", density).toString(2))
            for (i in 0 until results.length()) {
                val result = results.getJSONObject(i)
                val probes = result.getJSONArray("probes")
                for (j in 0 until probes.length()) {
                    val probe = probes.getJSONObject(j)
                    val delta = probe.getDouble("mean_abs_rgb_delta_8bit")
                    if (probe.getBoolean("corner")) assertTrue("${result.getString("mode")}: ${probe.getString("name")} must remain outside highlight, delta=$delta", delta <= 1.5)
                    else assertTrue("${result.getString("mode")}: ${probe.getString("name")} must contain a visible highlight, delta=$delta", delta >= 5.0)
                }
            }
            report.put("passed", true)
        } finally {
            File(directory, "gpu-corner-check.json").writeText(report.put("passed", report.optBoolean("passed", false)).toString(2))
            if (activePointer) runCatching { inject(MotionEvent.ACTION_CANCEL, lastPointer, SystemClock.uptimeMillis(), "held-gpu-cleanup-cancel") }
            instrumentation.runOnMainSync { realView?.disposeComposition(); realRecomposer?.cancel() }
            File(directory, "trace.jsonl").bufferedWriter().use { writer -> trace.forEach { writer.appendLine(it.toString()) } }
        }
    }

    /** The rule only bootstraps an Activity; product animation owns a real-time recomposer. */
    private fun installRealtimeFixture() {
        compose.setContent {
            val view = LocalView.current
            SideEffect {
                var context = view.context
                while (context !is Activity && context is ContextWrapper) context = context.baseContext
                activity = context as Activity
            }
        }
        compose.waitForIdle()
        val ready = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val observer = object : FrameLayout(activity) {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    delivered(event, "activity_dispatch", this)
                    return super.dispatchTouchEvent(event)
                }
            }
            realView = ComposeView(activity).also { view ->
                val parent = view.createLifecycleAwareWindowRecomposer(lifecycle = (activity as LifecycleOwner).lifecycle)
                realRecomposer = parent
                view.setParentCompositionContext(parent)
                view.setContent { Fixture(); SideEffect { ready.countDown() } }
                observer.addView(view, FrameLayout.LayoutParams(-1, -1))
                activity.setContentView(observer)
            }
        }
        assertTrue("Real-time composition initializes", ready.await(5, TimeUnit.SECONDS))
    }

    private fun captureSettledGpu(directory: File, name: String, heldSnapshot: Boolean = false): Bitmap {
        assertTrue("Only the separate held GPU diagnostic may capture an active pointer", !activePointer || heldSnapshot)
        assertEquals("Held snapshot must have a real active pointer", heldSnapshot, activePointer)
        val roots = readOnMain {
            WindowInspector.getGlobalWindowViews().filter { it.isAttachedToWindow && it.isHardwareAccelerated && it.width > 0 && it.height > 0 }
        }
        assertTrue("Actual hardware-accelerated windows are present", roots.isNotEmpty())
        val committed = CountDownLatch(roots.size)
        instrumentation.runOnMainSync {
            roots.forEach { root ->
                root.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
                root.postInvalidateOnAnimation()
            }
        }
        assertTrue("HWUI commits all fixture windows before $name", committed.await(5, TimeUnit.SECONDS))
        // Submission precedes compositor presentation. This is a settled GPU diagnostic,
        // and its optional held snapshots are never used as motion-timing evidence.
        SystemClock.sleep(80)
        val begin = SystemClock.uptimeMillis()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Composited display unavailable" }
        add("gpu_screenshot").put("name", name).put("begin_uptime_ms", begin)
            .put("end_uptime_ms", SystemClock.uptimeMillis()).put("active_pointer", activePointer)
            .put("width_px", bitmap.width).put("height_px", bitmap.height)
        assertEquals("Pointer state stays unchanged throughout the static GPU snapshot", heldSnapshot, activePointer)
        File(directory, "$name.png").outputStream().use {
            assertTrue("Encode fixture GPU pixels: $name", bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        return bitmap
    }

    private data class GpuRowProbe(val code: String, val meanRgb: DoubleArray,
        val meanAbsDelta: Double, val record: JSONObject)

    private fun gpuRowProbe(code: String, rowBounds: Rect, roi: Rect, baseline: Bitmap, expanded: Bitmap): GpuRowProbe {
        val left = roi.left.roundToInt()
        val top = roi.top.roundToInt()
        val right = roi.right.roundToInt()
        val bottom = roi.bottom.roundToInt()
        require(left >= 0 && top >= 0 && right <= expanded.width && bottom <= expanded.height && right > left && bottom > top) {
            "$code: foreground-free row probe must remain inside the composited display: $roi"
        }
        val mean = DoubleArray(3)
        val priorMean = DoubleArray(3)
        var delta = 0.0
        val count = (right - left) * (bottom - top)
        for (y in top until bottom) for (x in left until right) {
            val pixel = expanded.getPixel(x, y)
            val prior = baseline.getPixel(x, y)
            for (channel in 0..2) {
                val shift = 16 - channel * 8
                val value = (pixel ushr shift) and 255
                val previous = (prior ushr shift) and 255
                mean[channel] += value
                priorMean[channel] += previous
                delta += abs(value - previous)
            }
        }
        for (channel in 0..2) { mean[channel] /= count; priorMean[channel] /= count }
        val meanDelta = delta / (count * 3.0)
        return GpuRowProbe(code, mean, meanDelta, JSONObject().put("code", code)
            .put("row_screen_bounds_px", rect(rowBounds)).put("roi_screen_bounds_px", rect(roi))
            .put("roi_pixel_edges", JSONArray(listOf(left, top, right, bottom))).put("pixel_count", count)
            .put("mean_rgb_8bit", JSONArray(mean.toList())).put("closed_mean_rgb_8bit", JSONArray(priorMean.toList()))
            .put("mean_abs_delta_from_closed_8bit", meanDelta))
    }

    @Composable private fun Fixture() {
        val view = LocalView.current
        val currentDensity = LocalDensity.current.density
        val currentFontScale = LocalDensity.current.fontScale
        SideEffect { density = currentDensity; fontScale = currentFontScale }
        val tint = Color(0f, .65f, .75f)
        MaterialTheme(colorScheme = lightColorScheme(primary = tint, background = neutral, surface = neutral,
            surfaceContainer = neutral, onSurface = Color(0xFF123239))) {
            CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass, LocalDarkTheme provides false) {
                GlassHost(Modifier.fillMaxSize(), background = neutral) {
                    GlassBackground(0f, Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(neutral).drawBehind {
                            val step = 20.dp.toPx()
                            for (x in 0..ceil(size.width / step).toInt()) drawLine(Color.Black.copy(alpha = .13f),
                                Offset(x * step, 0f), Offset(x * step, size.height), .5.dp.toPx())
                            for (y in 0..ceil(size.height / step).toInt()) drawLine(Color.Black.copy(alpha = .13f),
                                Offset(0f, y * step), Offset(size.width, y * step), .5.dp.toPx())
                        })
                    }
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val requestedWidth = cardWidthDp?.dp ?: minOf(420.dp, (maxWidth - 40.dp).coerceAtLeast(1.dp))
                        // Default follows NativeStage's safe-content top +146pt; actual screen origin is exported.
                        Box(Modifier.align(Alignment.TopCenter).offset(y = (cardTopDp ?: 146f).dp)
                            .width(requestedWidth.coerceAtMost(maxWidth))) {
                            GlassLanguageMenu("Language", selected, languages, onSelect = { code ->
                                add("selection_callback").put("code", code).put("previous", selected)
                                    .put("popup_present_at_callback", menuPresent())
                                selections.add(code)
                                selected = code
                            }, onDismissed = { code ->
                                dismissals.add(add("dismissal_callback").put("code", code ?: JSONObject.NULL)
                                    .put("selected", selected).put("popup_present_at_callback", menuPresent()))
                            })
                        }
                        // Visible fixture-only corner marker links video PTS to a recorded committed draw.
                        Box(Modifier.align(Alignment.TopStart).size(8.dp)
                            .background(if (syncMarker % 2 == 0) Color.Black else Color.White))
                    }
                }
            }
        }
        SideEffect { add("composition_observed").put("selected", selected).put("marker", syncMarker)
            .put("content_view_width_px", view.width).put("content_view_height_px", view.height) }
    }

    private data class TaggedNode(val tag: String, val bounds: Rect, val node: SemanticsNode)

    /** Read public test semantics directly, without Compose rule idling or clock advancement. */
    private fun nodes(): List<TaggedNode> {
        val found = ArrayList<TaggedNode>()
        fun visitNode(node: SemanticsNode, screenOffset: Offset) {
            val tag = node.config.getOrNull(SemanticsProperties.TestTag)
            if (tag?.startsWith("general_language") == true) {
                found += TaggedNode(tag, node.boundsInWindow.translate(screenOffset), node)
            }
            node.children.forEach { visitNode(it, screenOffset) }
        }
        fun visitView(view: View) {
            if (view is ViewRootForTest) {
                val screen = IntArray(2)
                val window = IntArray(2)
                view.getLocationOnScreen(screen)
                view.getLocationInWindow(window)
                visitNode(view.semanticsOwner.unmergedRootSemanticsNode,
                    Offset((screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat()))
            } else if (view is ViewGroup) repeat(view.childCount) { visitView(view.getChildAt(it)) }
        }
        WindowInspector.getGlobalWindowViews().filter { it.isAttachedToWindow }.forEach(::visitView)
        return found
    }

    private fun findTag(tag: String): TaggedNode? = nodes().firstOrNull { it.tag == tag }
    private fun menuPresent() = findTag("general_language_menu") != null
    private fun tagCenter(tag: String): Offset = readOnMain {
        requireNotNull(findTag(tag)) { "Missing $tag" }.bounds.center
    }

    private fun outsidePoint(): Offset = readOnMain {
        val menu = requireNotNull(findTag("general_language_menu")).bounds
        val root = WindowInspector.getGlobalWindowViews()
            .last { it.isAttachedToWindow && it.javaClass.simpleName == "PopupLayout" }
        val location = IntArray(2)
        root.getLocationOnScreen(location)
        val inset = 8f * density
        val middleY = menu.center.y.coerceIn(location[1] + inset, location[1] + root.height - inset)
        listOf(Offset(location[0] + inset, middleY),
            Offset(location[0] + root.width - inset, middleY),
            Offset(location[0] + root.width / 2f, location[1] + inset),
            Offset(location[0] + root.width / 2f, location[1] + root.height - inset))
            .first { !menu.contains(it) }
    }

    private fun snapshot(row: JSONObject) {
        row.put("selected", selected).put("marker", syncMarker)
            .put("last_inject_uptime_ms", lastInjectedUptimeMs ?: JSONObject.NULL)
            .put("last_delivery_uptime_ms", lastDeliveredUptimeMs ?: JSONObject.NULL)
            .put("last_delivered_event_time_ms", lastDeliveredEventTimeMs ?: JSONObject.NULL)
            .put("last_delivered_action", lastDeliveredAction ?: JSONObject.NULL)
        row.put("nodes", JSONArray(nodes().map { tagged ->
            JSONObject().put("tag", tagged.tag).put("screen_bounds_px", rect(tagged.bounds)).also { node ->
                tagged.node.config.getOrNull(LanguageMenuVisualStateKey)?.let { state ->
                    node.put("draw_state", JSONObject().put("scale_x", state.scaleX).put("scale_y", state.scaleY)
                        .put("alpha", state.alpha).put("content_alpha", state.contentAlpha)
                        .put("translation_x_dp", state.translationXDp).put("translation_y_dp", state.translationYDp))
                }
                tagged.node.config.getOrNull(LanguageMenuHandoffStateKey)?.let { state ->
                    node.put("handoff_state", JSONObject().put("session_id", state.sessionId)
                        .put("phase", state.phase).put("source_card_alpha", state.sourceCardAlpha))
                }
            }
        }))
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            frameNumber++
            val began = SystemClock.elapsedRealtimeNanos()
            val row = add("vsync_sample").put("frame", frameNumber).put("frame_time_nanos", frameTimeNanos)
                .put("refresh_interval_ms", lastFrameNs?.let { (frameTimeNanos - it) / 1_000_000.0 } ?: JSONObject.NULL)
            lastFrameNs = frameTimeNanos
            snapshot(row)
            WindowInspector.getGlobalWindowViews().filter { it.isAttachedToWindow }.forEach { watch(it) }
            row.put("observer_cost_ms", (SystemClock.elapsedRealtimeNanos() - began) / 1_000_000.0)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun watch(view: View) {
        if (watched.containsKey(view)) return
        val listener = ViewTreeObserver.OnDrawListener {
            if (running) {
                val draw = ++drawNumber
                val drawVsync = frameNumber
                val drawMarker = syncMarker
                val began = SystemClock.elapsedRealtimeNanos()
                val row = add("window_draw").put("draw_id", draw).put("vsync_frame", drawVsync)
                    .put("vsync_frame_time_nanos", lastFrameNs ?: JSONObject.NULL)
                    .put("root_class", view.javaClass.name).put("root_identity", System.identityHashCode(view))
                    .put("hardware_accelerated", view.isHardwareAccelerated).put("host_view_alpha", view.alpha)
                snapshot(row)
                row.put("observer_cost_ms", (SystemClock.elapsedRealtimeNanos() - began) / 1_000_000.0)
                if (view.isHardwareAccelerated) view.viewTreeObserver.registerFrameCommitCallback {
                    add("frame_commit").put("draw_id", draw).put("draw_vsync_frame", drawVsync)
                        .put("root_identity", System.identityHashCode(view)).put("draw_marker", drawMarker)
                }
            }
        }
        watched[view] = listener
        view.viewTreeObserver.addOnDrawListener(listener)
        // Main activity dispatch is observed by its fixture FrameLayout. Popup host dispatches
        // pointer events to its Compose child, so observe there without consuming or replacing handlers.
        fun attachTouchObserver(candidate: View) {
            if (candidate is ViewRootForTest) {
                touchObservers[candidate] = Unit
                candidate.setOnTouchListener { _, event ->
                    delivered(event, "compose_root_listener", candidate)
                    false
                }
            } else if (candidate is ViewGroup) repeat(candidate.childCount) { attachTouchObserver(candidate.getChildAt(it)) }
        }
        attachTouchObserver(view)
    }

    private fun delivered(event: MotionEvent, source: String, view: View) {
        lastDeliveredUptimeMs = SystemClock.uptimeMillis()
        lastDeliveredEventTimeMs = event.eventTime
        lastDeliveredAction = event.actionMasked
        add("pointer_delivered").put("source", source).put("root_identity", System.identityHashCode(view.rootView))
            .put("action", event.actionMasked).put("event_time_ms", event.eventTime).put("down_time_ms", event.downTime)
            .put("x_screen_px", event.rawX).put("y_screen_px", event.rawY).put("history_size", event.historySize)
    }

    private fun inject(action: Int, point: Offset, plannedUptimeMs: Long, label: String) {
        sleepUntil(plannedUptimeMs)
        val before = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) activeDownTime = before
        val event = MotionEvent.obtain(activeDownTime, before, action, point.x, point.y, 0)
            .apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        lastInjectedUptimeMs = before
        val accepted = try { instrumentation.uiAutomation.injectInputEvent(event, true) } finally { event.recycle() }
        val after = SystemClock.uptimeMillis()
        activePointer = action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL
        lastPointer = point
        add("pointer_injected").put("label", label).put("action", action).put("planned_uptime_ms", plannedUptimeMs)
            .put("event_time_ms", before).put("injection_return_uptime_ms", after)
            .put("lateness_ms", before - plannedUptimeMs).put("injection_block_ms", after - before)
            .put("x_screen_px", point.x).put("y_screen_px", point.y).put("accepted", accepted)
        assertTrue("Public input accepted: $label", accepted)
    }

    private fun tap(point: Offset, holdMs: Long, label: String) {
        val start = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, point, start, "$label-down")
        inject(MotionEvent.ACTION_UP, point, start + holdMs, "$label-up")
    }

    private fun open(holdMs: Long, settleMs: Long) {
        tap(tagCenter("general_language_card"), holdMs, "open")
        waitFor("Five-language menu appears") { findTag("general_language#es") != null }
        SystemClock.sleep(settleMs)
        readOnMain { snapshot(add("menu_settled")) }
    }

    /** Keep the same physical pointer down across the real product menu's opening. */
    private fun longPressOpen(holdMs: Long, settleMs: Long, label: String): Offset {
        val anchor = tagCenter("general_language_card")
        val beganAt = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, anchor, beganAt, "$label-down")
        sleepUntil(beganAt + holdMs)
        waitFor("Held press opens all five language rows") { findTag("general_language#es") != null }
        SystemClock.sleep(settleMs)
        readOnMain { snapshot(add("long_press_menu_settled").put("label", label)) }
        return anchor
    }

    private fun assertSelectionPrecedesDismissal(code: String) {
        val accepted = trace.last { it.optString("event") == "selection_callback" && it.optString("code") == code }
        val dismissed = dismissals.last()
        assertTrue("Selection callback still has popup content", accepted.getBoolean("popup_present_at_callback"))
        assertEquals("Dismissal reports accepted code", code, dismissed.getString("code"))
        assertTrue("Dismissal follows popup removal", !dismissed.getBoolean("popup_present_at_callback"))
        assertTrue("Selection callback precedes dismissal", accepted.getInt("sequence") < dismissed.getInt("sequence"))
    }

    private fun mark(label: String) = instrumentation.runOnMainSync {
        syncMarker++
        add("visual_sync_requested").put("label", label).put("marker", syncMarker)
    }

    private fun outcome(label: String) = readOnMain {
        add("scenario_result").put("label", label).put("selected", selected)
            .put("menu_present", menuPresent()).put("callbacks", JSONArray(selections.toList()))
            .put("dismissals", JSONArray(dismissals.toList()))
    }

    private fun waitFor(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (!readOnMain(condition)) {
            assertTrue("Timed out: $label", SystemClock.uptimeMillis() < deadline)
            SystemClock.sleep(10)
        }
    }

    private fun <T> readOnMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }

    private fun sleepUntil(deadline: Long) {
        while (true) {
            val remaining = deadline - SystemClock.uptimeMillis()
            if (remaining <= 0L) return
            SystemClock.sleep(remaining)
        }
    }

    private fun add(event: String): JSONObject = JSONObject().put("event", event).put("sequence", rowSequence.incrementAndGet())
        .put("scenario", scenario).put("uptime_ms", SystemClock.uptimeMillis())
        .put("elapsed_realtime_nanos", SystemClock.elapsedRealtimeNanos()).also(trace::add)

    private fun assertFirstOpenPresentationHandoff() {
        val rows = trace.filter { it.optString("event") == "presentation_event" && it.optString("scenario") == "open-select-english" }
            .sortedBy { it.getInt("sequence") }
        val prepared = rows.first { it.optString("stage") == "prepared" }
        val sessionId = prepared.getInt("session_id")
        val stages = rows.filter { it.getInt("session_id") == sessionId }
        val expected = listOf("prepared", "popup_positioned", "popup_first_content_draw", "popup_frame_committed",
            "handoff_waiting-source-hidden-frame", "source_hidden_first_content_draw", "source_hidden_frame_committed", "handoff_motion")
        val positions = expected.map { stage -> stages.indexOfFirst { it.optString("stage") == stage }.also {
            assertTrue("First natural open records $stage: ${stages.map { row -> row.optString("stage") }}", it >= 0)
        } }
        assertTrue("Popup submission precedes hiding the source and starting motion", positions.zipWithNext().all { (a, b) -> a < b })
        val popup = stages.first { it.optString("stage") == "popup_frame_committed" }
        val source = stages.first { it.optString("stage") == "source_hidden_frame_committed" }
        assertTrue("Popup and source fences belong to separate content windows", popup.getInt("window_identity") != source.getInt("window_identity"))
        assertTrue("Popup fence is registered by the Popup content window", popup.getString("window_class").endsWith("PopupLayout"))
        add("presentation_handoff_asserted").put("session_id", sessionId).put("ordered_stages", JSONArray(expected))
    }

    private fun rect(bounds: Rect) = JSONArray(listOf(bounds.left, bounds.top, bounds.width, bounds.height))

    private fun writeSession(directory: File, holdMs: Long, longPressHoldMs: Long, settleMs: Long) {
        val row = readOnMain {
            val view = requireNotNull(realView)
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            JSONObject().put("schema", 1).put("sdk", Build.VERSION.SDK_INT).put("model", Build.MODEL)
                .put("start_uptime_ms", startUptimeMs).put("density", density).put("font_scale", fontScale)
                .put("content_screen_origin_px", JSONArray(location.toList()))
                .put("content_width_px", view.width).put("content_height_px", view.height)
                .put("card_width_dp_requested", cardWidthDp ?: JSONObject.NULL)
                .put("card_top_dp_requested", cardTopDp ?: JSONObject.NULL)
                .put("card_default", "width=min(420dp, actual content width-40dp), centered; top=146dp from content top")
                .put("tap_hold_ms_requested", holdMs).put("settle_ms", settleMs)
                .put("long_press_hold_ms_requested", longPressHoldMs)
                .put("languages", JSONArray(languages.map { JSONArray(listOf(it.first, it.second)) }))
                .put("clock", "Lifecycle-aware AndroidUiDispatcher/Choreographer Recomposer; no Compose mainClock advances after bootstrap")
                .put("pixels", "Externally started adb screenrecord only; no screenshot or capture wait in motion path")
                .put("frame_observation", "vsync_sample is a Choreographer callback, not proof of a displayed buffer. window_draw snapshots semantics on OnDraw; frame_commit observes HWUI submission, not compositor present time. Video PTS plus visible corner marker is the independent display evidence.")
                .put("presentation_handoff", "presentation_event records per-session preparation, Popup positioning, replacement-card draw, the owning Popup window's HWUI submission, source-hidden draw/submission, and motion origin. The original card stays visible until the Popup submission. Optical sampling and motion wait for source-hidden submission. Window identities prove separate content windows. These submission fences do not prove compositor presentation; API 26–28/software fallback is explicitly named draw_posted_legacy_or_software.")
                .put("geometry", "screen_bounds_px is transformed public semantics geometry, not a segmented visible contour. draw_state is the product graphicsLayer input, not a material-alpha measurement. Host view alpha is separate. Extract visible menu/text edges from video for independent validation.")
                .put("input_observation", "Public UiAutomation MotionEvent injection records actual eventTime, blocking and lateness. Activity FrameLayout dispatch and nonconsuming Compose-root listeners record delivered uptime; PopupLayout may handle ACTION_OUTSIDE before its Compose child, so absence of a delivery row for an outside event is not fabricated as delivery.")
                .put("scope", "Real GlassLanguageMenu and all five labels; onSelect immediately updates local selected code, onDismissed records completion after popup removal. Preference persistence and Activity recreation are not emulated.")
                .put("long_press", "Public DOWN at original card center; wait for real menu while preserving pointer; stationary UP must leave it open. Separate same-stream 12x16ms path to Japanese row then UP must select once. Configured hold plus settling is a test trajectory, not an asserted native timing waveform.")
                .put("slide_cancel", "Provisional 8x16ms straight path from Japanese row to an outside point, followed by public ACTION_CANCEL. Not claimed to replay an observed native gesture; replace only after native waveform is available.")
                .put("limitations", "Observer traversal/JSON construction cost can perturb rendering; vsync rows export observer_cost_ms. No native geometry/alpha tolerance asserted without a matched five-language reference. First use and warmed reopen are separate scenarios.")
                .also { snapshot(it) }
        }
        File(directory, "session.json").writeText(row.toString(2))
    }
}
