package com.lingion.sleepy.ui.component

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
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
import com.lingion.sleepy.ui.component.liquidglass.LanguageMenuPresentationDiagnostics
import com.lingion.sleepy.ui.component.liquidglass.LanguageMenuVisualStateKey
import com.lingion.sleepy.ui.theme.LocalDarkTheme
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.abs
import kotlin.math.roundToInt

/** Product-menu behavior and actual GPU coverage with a real-time composition clock. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class LanguageMenuMotionCaptureTest {
    @get:Rule val compose = createComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val events = ConcurrentLinkedQueue<JSONObject>()
    private val selections = ConcurrentLinkedQueue<String>()
    private val dismissals = ConcurrentLinkedQueue<JSONObject>()
    private val rowSequence = AtomicInteger()
    private val languages = listOf(
        "zh-CN" to "简体中文", "zh-TW" to "繁體中文", "en" to "English",
        "ja" to "日本語", "es" to "Español",
    )
    private val neutral = Color(.94f, .94f, .96f)
    private var selected by mutableStateOf("zh-CN")
    private lateinit var activity: Activity
    private var realView: ComposeView? = null
    private var realRecomposer: Recomposer? = null
    private var density = 1f
    private var cardWidthDp: Float? = null
    private var cardTopDp: Float? = null
    private var activeDownTime = 0L
    private var activePointer = false
    private var lastPointer = Offset.Zero

    @Test fun selectionDismissalAndLongPressKeepTheOriginalPointerBehavior() {
        val tapHoldMs = 67L
        val longPressHoldMs = 450L
        val settleMs = 850L
        installRealtimeFixture()
        val priorObserver = LanguageMenuPresentationDiagnostics.observer
        LanguageMenuPresentationDiagnostics.observer = { sessionId, event, _, windowIdentity, windowClass ->
            add("presentation_event").put("session_id", sessionId).put("stage", event)
                .put("window_identity", windowIdentity ?: JSONObject.NULL)
                .put("window_class", windowClass ?: JSONObject.NULL)
        }
        try {
            waitFor("Language card laid out") { findTag("general_language_card")?.bounds?.width?.let { it > 0f } == true }
            open(tapHoldMs, settleMs)
            assertFirstOpenPresentationHandoff()
            tap(tagCenter("general_language#en"), tapHoldMs, "select-en")
            waitFor("English selection is accepted") { selected == "en" }
            waitFor("English popup exit completes") { !menuPresent() && dismissals.lastOrNull()?.optString("code") == "en" }
            SystemClock.sleep(settleMs)
            assertEquals("Initial selection callback", listOf("en"), selections.toList())
            assertSelectionPrecedesDismissal("en")

            open(tapHoldMs, settleMs)
            val callbacksBeforeOutside = selections.size
            val dismissalsBeforeOutside = dismissals.size
            tap(outsidePoint(), tapHoldMs, "outside-dismiss")
            waitFor("Outside click removes popup") { !menuPresent() && dismissals.size == dismissalsBeforeOutside + 1 }
            SystemClock.sleep(settleMs)
            assertEquals("Outside dismissal must not select", callbacksBeforeOutside, selections.size)
            assertEquals("Outside dismissal preserves English", "en", selected)
            assertTrue("Outside dismissal reports no selection", dismissals.last().isNull("code"))

            open(tapHoldMs, settleMs)
            val callbacksBeforeSame = selections.size
            val dismissalsBeforeSame = dismissals.size
            tap(tagCenter("general_language#en"), tapHoldMs, "select-same-en")
            waitFor("Same item removes popup") { !menuPresent() && dismissals.size == dismissalsBeforeSame + 1 }
            SystemClock.sleep(settleMs)
            assertEquals("Same item is accepted once", callbacksBeforeSame + 1, selections.size)
            assertEquals("Same item preserves English", "en", selected)
            assertSelectionPrecedesDismissal("en")

            val callbacksBeforeHold = selections.size
            val dismissalsBeforeHold = dismissals.size
            val heldAnchor = longPressOpen(longPressHoldMs, settleMs, "hold-anchor")
            inject(MotionEvent.ACTION_UP, heldAnchor, SystemClock.uptimeMillis(), "hold-anchor-up")
            SystemClock.sleep(settleMs)
            assertTrue("Stationary long press release keeps menu open", readOnMain { menuPresent() })
            assertEquals("Stationary hold does not select a row beneath the original point", callbacksBeforeHold, selections.size)
            assertEquals("Stationary hold does not dismiss", dismissalsBeforeHold, dismissals.size)
            tap(outsidePoint(), tapHoldMs, "hold-cleanup-outside")
            waitFor("Hold cleanup removes menu") { !menuPresent() && dismissals.size == dismissalsBeforeHold + 1 }
            SystemClock.sleep(settleMs)

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
            assertEquals("Long-press slide accepts exactly once", callbacksBeforeSlide + 1, selections.size)
            assertEquals("Long-press slide selects Japanese", "ja", selections.last())
            assertSelectionPrecedesDismissal("ja")

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
            assertEquals("Cancelled pointer must not select", callbacksBeforeCancel, selections.size)
            if (readOnMain { menuPresent() }) {
                tap(outsidePoint(), tapHoldMs, "cleanup-outside-dismiss")
                waitFor("Cleanup removes menu") { !menuPresent() }
            }
            SystemClock.sleep(settleMs)
        } finally {
            try {
                disposeFixture()
            } finally {
                LanguageMenuPresentationDiagnostics.observer = priorObserver
            }
        }
    }

    /** Every language row must retain real GPU material after the menu opens naturally. */
    @Test fun fiveLanguageRowsKeepGpuMaterialAfterNaturalOpen() {
        cardWidthDp = 362f
        cardTopDp = 180f
        var baseline: Bitmap? = null
        var expanded: Bitmap? = null
        try {
            installRealtimeFixture()
            waitFor("Language card laid out") { findTag("general_language_card")?.bounds?.width?.let { it > 0f } == true }
            SystemClock.sleep(400)
            val baselineImage = captureSettledGpu("closed-baseline")
            baseline = baselineImage
            open(67L, 850L)
            waitFor("All five rows finish natural expansion") {
                val state = findTag("general_language_menu")?.node?.config?.getOrNull(LanguageMenuVisualStateKey)
                state != null && state.alpha >= .999f && abs(state.scaleX - 1f) <= .001f &&
                    abs(state.scaleY - 1f) <= .001f && languages.all { (code, _) ->
                        findTag("general_language#$code")?.bounds?.height?.let { it >= 35f * density } == true
                    }
            }
            val rowBounds = readOnMain {
                languages.map { (code, _) -> code to requireNotNull(findTag("general_language#$code")).bounds }
            }
            val expandedImage = captureSettledGpu("expanded-five-rows")
            expanded = expandedImage
            assertEquals("GPU baseline width", baselineImage.width, expandedImage.width)
            assertEquals("GPU baseline height", baselineImage.height, expandedImage.height)
            val probes = rowBounds.map { (code, bounds) ->
                val roi = Rect(bounds.right - 48f * density, bounds.center.y - 8f * density,
                    bounds.right - 30f * density, bounds.center.y + 8f * density)
                gpuRowProbe(code, roi, baselineImage, expandedImage)
            }
            val firstRgb = probes.first().meanRgb
            assertTrue("GPU screenshot taken without an active pointer", !activePointer)
            probes.forEach { probe ->
                assertTrue("${probe.code}: glass must change actual row pixels from closed baseline; " +
                    "mean absolute delta=${probe.meanAbsDelta}", probe.meanAbsDelta >= 6.0)
                val distance = probe.meanRgb.indices.sumOf { abs(probe.meanRgb[it] - firstRgb[it]) } / 3.0
                assertTrue("${probe.code}: lower-row glass must cover the same neutral fixture as first row; " +
                    "RGB distance=$distance", distance <= 5.0)
            }
            assertEquals("Opening for pixel verification must not select a language", emptyList<String>(), selections.toList())
        } finally {
            disposeFixture()
            baseline?.recycle()
            expanded?.recycle()
        }
    }

    private data class CornerProbe(val name: String, val corner: Boolean, val delta: Double)
    private data class HeldRowProbes(val mode: String, val probes: List<CornerProbe>)

    /** A held row must leave all four outside corners untouched for both pointer origins. */
    @Test fun heldLanguageRowKeepsItsFourCornersOutsideTheHighlight() {
        cardWidthDp = 362f
        cardTopDp = 180f
        val results = mutableListOf<HeldRowProbes>()
        installRealtimeFixture()
        try {
            waitFor("Language card laid out") { findTag("general_language_card") != null }
            for (mode in listOf("direct-row", "anchor-drag")) {
                val anchor = if (mode == "anchor-drag") longPressOpen(450L, 850L, mode) else {
                    open(67L, 850L)
                    null
                }
                val before = captureSettledGpu("$mode-before", heldSnapshot = anchor != null)
                try {
                    val bounds = readOnMain { requireNotNull(findTag("general_language#en")).bounds }
                    if (anchor == null) {
                        inject(MotionEvent.ACTION_DOWN, bounds.center, SystemClock.uptimeMillis(), "$mode-down")
                    } else {
                        val beganAt = SystemClock.uptimeMillis()
                        repeat(12) { index ->
                            inject(MotionEvent.ACTION_MOVE, anchor + (bounds.center - anchor) * ((index + 1) / 12f),
                                beganAt + (index + 1) * 16L, "$mode-move")
                        }
                    }
                    SystemClock.sleep(700)
                    val held = captureSettledGpu("$mode-held", heldSnapshot = true)
                    try {
                        val probes = mutableListOf<CornerProbe>()
                        fun probe(name: String, x: Float, y: Float, corner: Boolean) {
                            val px = x.roundToInt(); val py = y.roundToInt()
                            var total = 0.0
                            for (dy in -1..1) for (dx in -1..1) {
                                val prior = before.getPixel(px + dx, py + dy)
                                val actual = held.getPixel(px + dx, py + dy)
                                for (shift in listOf(16, 8, 0))
                                    total += abs(((actual ushr shift) and 255) - ((prior ushr shift) and 255))
                            }
                            probes += CornerProbe(name, corner, total / 27.0)
                        }
                        val insetX = 4f * density
                        val insetY = 3f * density
                        probe("top-left", bounds.left + insetX, bounds.top + insetY, true)
                        probe("top-right", bounds.right - insetX, bounds.top + insetY, true)
                        probe("bottom-left", bounds.left + insetX, bounds.bottom - insetY, true)
                        probe("bottom-right", bounds.right - insetX, bounds.bottom - insetY, true)
                        probe("left-interior", bounds.left + insetX, bounds.center.y, false)
                        probe("right-interior", bounds.right - insetX, bounds.center.y, false)
                        results += HeldRowProbes(mode, probes)
                    } finally { held.recycle() }
                } finally {
                    before.recycle()
                    if (activePointer) inject(MotionEvent.ACTION_CANCEL, lastPointer, SystemClock.uptimeMillis(), "$mode-cancel")
                }
                SystemClock.sleep(250)
                assertEquals("Cancelled held row must not select", emptyList<String>(), selections.toList())
                assertTrue("Cancelled held row leaves menu available", readOnMain { menuPresent() })
                tap(outsidePoint(), 67L, "$mode-dismiss")
                waitFor("Held regression cleanup removes menu") { !menuPresent() }
            }
            results.forEach { result ->
                result.probes.forEach { probe ->
                    if (probe.corner) assertTrue("${result.mode}: ${probe.name} must remain outside highlight, delta=${probe.delta}", probe.delta <= 1.5)
                    else assertTrue("${result.mode}: ${probe.name} must contain a visible highlight, delta=${probe.delta}", probe.delta >= 5.0)
                }
            }
        } finally {
            disposeFixture()
        }
    }

    private fun disposeFixture() {
        if (activePointer) runCatching {
            inject(MotionEvent.ACTION_CANCEL, lastPointer, SystemClock.uptimeMillis(), "cleanup-cancel")
        }
        instrumentation.runOnMainSync {
            realView?.disposeComposition()
            realRecomposer?.cancel()
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
            val observer = FrameLayout(activity)
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

    private fun captureSettledGpu(name: String, heldSnapshot: Boolean = false): Bitmap {
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
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Composited display unavailable" }
        assertEquals("Pointer state stays unchanged throughout the static GPU snapshot", heldSnapshot, activePointer)
        return bitmap
    }

    private data class GpuRowProbe(val code: String, val meanRgb: DoubleArray, val meanAbsDelta: Double)

    private fun gpuRowProbe(code: String, roi: Rect, baseline: Bitmap, expanded: Bitmap): GpuRowProbe {
        val left = roi.left.roundToInt()
        val top = roi.top.roundToInt()
        val right = roi.right.roundToInt()
        val bottom = roi.bottom.roundToInt()
        require(left >= 0 && top >= 0 && right <= expanded.width && bottom <= expanded.height && right > left && bottom > top) {
            "$code: foreground-free row probe must remain inside the composited display: $roi"
        }
        val mean = DoubleArray(3)
        var delta = 0.0
        val count = (right - left) * (bottom - top)
        for (y in top until bottom) for (x in left until right) {
            val pixel = expanded.getPixel(x, y)
            val prior = baseline.getPixel(x, y)
            for (channel in 0..2) {
                val shift = 16 - channel * 8
                val value = (pixel ushr shift) and 255
                mean[channel] += value
                delta += abs(value - ((prior ushr shift) and 255))
            }
        }
        for (channel in 0..2) mean[channel] /= count
        return GpuRowProbe(code, mean, delta / (count * 3.0))
    }

    @Composable private fun Fixture() {
        val currentDensity = LocalDensity.current.density
        SideEffect { density = currentDensity }
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
                        // Keep the fixed product-menu fixture used by the behavior regression.
                        Box(Modifier.align(Alignment.TopCenter).offset(y = (cardTopDp ?: 146f).dp)
                            .width(requestedWidth.coerceAtMost(maxWidth))) {
                            GlassLanguageMenu("Language", selected, languages, onSelect = { code ->
                                add("selection_callback").put("code", code)
                                    .put("popup_present_at_callback", menuPresent())
                                selections.add(code)
                                selected = code
                            }, onDismissed = { code ->
                                dismissals.add(add("dismissal_callback").put("code", code ?: JSONObject.NULL)
                                    .put("popup_present_at_callback", menuPresent()))
                            })
                        }
                    }
                }
            }
        }
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

    private fun inject(action: Int, point: Offset, plannedUptimeMs: Long, label: String) {
        sleepUntil(plannedUptimeMs)
        val before = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) activeDownTime = before
        val event = MotionEvent.obtain(activeDownTime, before, action, point.x, point.y, 0)
            .apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        val accepted = try { instrumentation.uiAutomation.injectInputEvent(event, true) } finally { event.recycle() }
        activePointer = action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL
        lastPointer = point
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
    }

    /** Keep the same physical pointer down across the real product menu's opening. */
    private fun longPressOpen(holdMs: Long, settleMs: Long, label: String): Offset {
        val anchor = tagCenter("general_language_card")
        val beganAt = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, anchor, beganAt, "$label-down")
        sleepUntil(beganAt + holdMs)
        waitFor("Held press opens all five language rows") { findTag("general_language#es") != null }
        SystemClock.sleep(settleMs)
        return anchor
    }

    private fun assertSelectionPrecedesDismissal(code: String) {
        val accepted = events.last { it.optString("event") == "selection_callback" && it.optString("code") == code }
        val dismissed = dismissals.last()
        assertTrue("Selection callback still has popup content", accepted.getBoolean("popup_present_at_callback"))
        assertEquals("Dismissal reports accepted code", code, dismissed.getString("code"))
        assertTrue("Dismissal follows popup removal", !dismissed.getBoolean("popup_present_at_callback"))
        assertTrue("Selection callback precedes dismissal", accepted.getInt("sequence") < dismissed.getInt("sequence"))
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

    private fun add(event: String): JSONObject = JSONObject().put("event", event)
        .put("sequence", rowSequence.incrementAndGet()).also(events::add)

    private fun assertFirstOpenPresentationHandoff() {
        val rows = events.filter { it.optString("event") == "presentation_event" }
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
    }
}
