package com.lingion.sleepy.ui.component

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.os.SystemClock
import android.view.Choreographer
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.ui.component.liquidglass.DockLensBoundsInRootPx
import com.lingion.sleepy.ui.component.liquidglass.GlassBackground
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.component.liquidglass.GlassRangeSlider
import com.lingion.sleepy.ui.component.liquidglass.GlassSlider
import com.lingion.sleepy.ui.component.liquidglass.GlassSwitch
import com.lingion.sleepy.ui.component.liquidglass.LiquidGlassNavigationBar
import com.lingion.sleepy.ui.theme.LocalDarkTheme
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Opt-in real-time observation of product controls. Start screenrecord separately.
 * The fixture never advances the test clock, substitutes motion, or reads a screenshot
 * during injection. Geometry is public semantics read in bracketed observation intervals;
 * HWUI commit is not claimed to be the compositor's presentation timestamp.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class NativeControlMotionCaptureTest {
    @get:Rule val compose = createComposeRule()

    private enum class Control { dock3, dock4, toggle, segmented, slider, range }
    private data class ObservedNode(val node: SemanticsNode, val screenOffset: Offset, val rootOrigin: Offset) {
        val tag get() = node.config.getOrNull(SemanticsProperties.TestTag)
        val bounds get() = node.boundsInWindow.translate(screenOffset)
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val trace = ConcurrentLinkedQueue<JSONObject>()
    private val callbacks = ConcurrentLinkedQueue<JSONObject>()
    private val sequence = AtomicInteger()
    private lateinit var activity: Activity
    private lateinit var dispatchObserver: FrameLayout
    private var realView: ComposeView? = null
    private var recomposer: Recomposer? = null
    private var control by mutableStateOf(Control.dock3)
    private var selected by mutableIntStateOf(0)
    private var checked by mutableStateOf(false)
    private var value by mutableFloatStateOf(.25f)
    private var rangeStart by mutableFloatStateOf(.25f)
    private var rangeEnd by mutableFloatStateOf(.75f)
    private var marker by mutableIntStateOf(0)
    private var density = 1f
    private var fontScale = 1f
    private var running = false
    private var vsyncCount = 0
    private var drawCount = 0
    private var lastVsyncNs: Long? = null
    private var drawListener: ViewTreeObserver.OnDrawListener? = null
    private var lastDeliveredAtNs: Long? = null
    private var lastDeliveredMonotonicNs: Long? = null
    private var lastDeliveredUptimeMs: Long? = null
    private var lastDeliveredEventMs: Long? = null
    private var lastDeliveredAction: Int? = null
    @Volatile private var scenario = "setup"
    @Volatile private var activePointer = false
    @Volatile private var lastPointer = Offset.Zero
    private var downTime = 0L
    private var requestedHoldMs = 67L
    private var settleMs = 1_100L
    private var rapidGapMs = 40L
    private var groups = setOf("direct", "hold", "drag", "cancel", "rapid")

    @Test fun recordControlsNaturalMotion() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Pass controlMotionCapture=true for real-time control capture",
            args.getString("controlMotionCapture") == "true")
        val controls = (args.getString("controlMotionControls") ?: "dock3,dock4")
            .split(',').map { Control.valueOf(it.trim()) }.distinct()
        require(controls.isNotEmpty())
        requestedHoldMs = argumentLong("controlMotionTapHoldMs", 67, 1L..1_000L)
        settleMs = argumentLong("controlMotionSettleMs", 1_100, 300L..5_000L)
        rapidGapMs = argumentLong("controlMotionRapidGapMs", 40, 0L..1_000L)
        groups = (args.getString("controlMotionScenarios") ?: "direct,hold,drag,cancel,rapid")
            .split(',').map(String::trim).toSet()
        require(groups.all { it in setOf("direct", "hold", "drag", "cancel", "rapid", "interrupt") })
        val runId = (args.getString("controlMotionRunId") ?: "controls-${System.currentTimeMillis()}")
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "native-control-motion/$runId")
        assertTrue("Create capture output", directory.isDirectory || directory.mkdirs())

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
            dispatchObserver = object : FrameLayout(activity) {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    delivery(event, "dispatch_before")
                    return super.dispatchTouchEvent(event).also { handled ->
                        delivery(event, "dispatch_after").put("handled", handled)
                    }
                }
            }
            val view = ComposeView(activity)
            val parent = view.createLifecycleAwareWindowRecomposer(lifecycle = (activity as LifecycleOwner).lifecycle)
            recomposer = parent
            realView = view
            view.setParentCompositionContext(parent)
            view.setContent { Fixture(); SideEffect { ready.countDown() } }
            dispatchObserver.addView(view, FrameLayout.LayoutParams(-1, -1))
            activity.setContentView(dispatchObserver)
        }
        assertTrue("Natural-time composition initialized", ready.await(5, TimeUnit.SECONDS))
        try {
            waitFor("product control layout") { tagged("motion-control")?.bounds?.width?.let { it > 0 } == true }
            readOnMain {
                assertTrue("GPU capture requires hardware acceleration", dispatchObserver.isHardwareAccelerated)
                running = true
                watchDraws()
                Choreographer.getInstance().postFrameCallback(frameObserver)
            }
            writeSession(directory, controls)
            for (next in controls) {
                readOnMain {
                    control = next
                    selected = 0
                    checked = false
                    value = .25f
                    rangeStart = .25f
                    rangeEnd = .75f
                    marker++
                }
                scenario = "${next.name}:setup"
                SystemClock.sleep(settleMs)
                mark("control-ready")
                when (next) {
                    Control.dock3, Control.dock4, Control.segmented -> selectionScript()
                    Control.toggle -> toggleScript()
                    Control.slider, Control.range -> sliderScript()
                }
                SystemClock.sleep(settleMs)
                readOnMain { snapshot(add("control_complete")) }
            }
        } finally {
            if (activePointer) runCatching {
                inject(MotionEvent.ACTION_CANCEL, lastPointer, SystemClock.uptimeMillis(), "cleanup-cancel")
            }
            readOnMain {
                add("teardown_begin").also(::snapshot)
                // Make the lifecycle boundary explicit in the video and trace;
                // post-disposal pixels are not part of control acceptance.
                marker++
            }
            SystemClock.sleep(150)
            readOnMain {
                add("composition_dispose_begin")
                running = false
                Choreographer.getInstance().removeFrameCallback(frameObserver)
                drawListener?.let {
                    if (dispatchObserver.viewTreeObserver.isAlive) dispatchObserver.viewTreeObserver.removeOnDrawListener(it)
                }
                realView?.disposeComposition()
                recomposer?.cancel()
                add("composition_dispose_end")
            }
            File(directory, "trace.jsonl").bufferedWriter().use { writer ->
                trace.sortedBy { it.getInt("sequence") }.forEach { writer.appendLine(it.toString()) }
            }
            File(directory, "callbacks.jsonl").bufferedWriter().use { writer ->
                callbacks.forEach { writer.appendLine(it.toString()) }
            }
            File(directory, "result.json").writeText(JSONObject().put("trace_rows", trace.size)
                .put("callback_rows", callbacks.size).put("controls", JSONArray(controls.map { it.name }))
                .put("scenarios", JSONArray(groups.toList())).toString(2))
        }
    }

    @Composable private fun Fixture() {
        val currentDensity = LocalDensity.current
        SideEffect { density = currentDensity.density; fontScale = currentDensity.fontScale }
        val tint = Color(0f, .65f, .75f)
        val neutral = Color(.94f, .94f, .96f)
        MaterialTheme(colorScheme = lightColorScheme(primary = tint, secondary = tint,
            secondaryContainer = tint, onSecondaryContainer = Color(0xFF123239),
            surface = neutral, surfaceContainer = neutral, background = neutral)) {
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
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        key(control) {
                            val modifier = Modifier.testTag("motion-control")
                            when (control) {
                                Control.dock3, Control.dock4 -> LiquidGlassNavigationBar(
                                    listOf(PillNavItemSpec(Icons.Filled.Home, "Home"),
                                        PillNavItemSpec(Icons.Filled.Search, "Explore")) +
                                        (if (control == Control.dock4) listOf(PillNavItemSpec(Icons.Filled.Star, "Favorites")) else emptyList()) +
                                        PillNavItemSpec(Icons.Filled.Settings, "Settings"),
                                    selected, { recordCallback(it.toString()); selected = it }, modifier.width(351.dp),
                                )
                                Control.segmented -> SegmentedSwitcher(
                                    listOf(0 to "First", 1 to "Second", 2 to "Third"), selected,
                                    { recordCallback(it.toString()); selected = it }, modifier.width(335.dp),
                                )
                                Control.toggle -> GlassSwitch(checked, { recordCallback(it.toString()); checked = it },
                                    modifier, thumbContent = { Box(Modifier.fillMaxSize().testTag("motion-switch-lens")) },
                                    colors = SwitchDefaults.colors(checkedTrackColor = tint,
                                        checkedThumbColor = Color.White, uncheckedThumbColor = Color.White))
                                Control.slider -> GlassSlider(value, { recordCallback(it.toString()); value = it },
                                    modifier.width(335.dp), onValueChangeFinished = { add("value_change_finished") })
                                Control.range -> GlassRangeSlider(rangeStart..rangeEnd, {
                                    recordCallback("${it.start},${it.endInclusive}")
                                    rangeStart = it.start; rangeEnd = it.endInclusive
                                }, modifier.width(335.dp), valueRange = 0f..1f, steps = 0)
                            }
                        }
                        Box(Modifier.align(Alignment.TopStart).size(8.dp)
                            .background(if (marker % 2 == 0) Color.Black else Color.White))
                    }
                }
            }
        }
        SideEffect { add("composition_observed").put("selected", selected).put("checked", checked)
            .put("value", value).put("range_start", rangeStart).put("range_end", rangeEnd).put("marker", marker) }
    }

    private fun selectionScript() {
        val seats = readOnMain {
            if (control == Control.segmented) {
                val b = requireNotNull(tagged("motion-control")).bounds
                (0..2).map { Offset(b.left + b.width * (it + .5f) / 3f, b.center.y) }
            } else observedNodes().filter { it.node.config.getOrNull(SemanticsProperties.Role) == Role.Tab }
                .sortedBy { it.bounds.center.x }.map { it.bounds.center }
        }
        assertEquals("Fixture exposes every destination", if (control == Control.dock4) 4 else 3, seats.size)
        val a = seats.first(); val b = seats.last()
        if ("direct" in groups) {
            if (control == Control.dock3) {
                case("adjacent-first-to-second-tap") { tap(seats[1], "tap-adjacent") }
                assertSelected(1)
                case("adjacent-second-to-first-tap") { tap(a, "tap-adjacent-return") }
                assertSelected(0)
            }
            case("first-to-last-tap") { tap(b, "tap-last") }
            assertSelected(seats.lastIndex)
            case("last-to-first-tap") { tap(a, "tap-first") }
            assertSelected(0)
        }
        if ("hold" in groups) {
            selectFirst(a)
            case("hold-unselected-last") { tap(b, "hold-last", 700) }
            assertSelected(seats.lastIndex)
            case("hold-unselected-first") { tap(a, "hold-first", 700) }
            assertSelected(0)
            val before = callbacks.size
            case("hold-selected-first") { tap(a, "hold-selected", 700) }
            assertEquals("Holding selected destination does not navigate", before, callbacks.size)
        }
        if ("drag" in groups) {
            selectFirst(a)
            case("slow-forward-drag") { drag(a, listOf(b), 1_000, "slow-forward") }
            assertSelected(seats.lastIndex)
            case("slow-reverse-drag") { drag(b, listOf(a), 1_000, "slow-reverse") }
            assertSelected(0)
            case("fast-forward-drag") { drag(a, listOf(b), 180, "fast-forward") }
            assertSelected(seats.lastIndex)
            case("fast-reverse-drag") { drag(b, listOf(a), 180, "fast-reverse") }
            assertSelected(0)
            val before = callbacks.size
            case("held-drag-reverses-before-up") { drag(a, listOf(b, a), 300, "reverse-before-up") }
            assertSelected(0)
            assertEquals("Returning to source before UP does not navigate", before, callbacks.size)
        }
        if ("cancel" in groups) {
            selectFirst(a)
            val before = callbacks.size
            case("direct-preview-cancel") { tap(b, "preview-cancel", 350, cancel = true) }
            assertSelected(0)
            assertEquals("Cancelled preview does not navigate", before, callbacks.size)
            case("drag-cancel") { drag(a, listOf(b), 300, "drag-cancel", cancel = true) }
            assertSelected(0)
            assertEquals("Cancelled drag does not navigate", before, callbacks.size)
        }
        if ("rapid" in groups) {
            selectFirst(a)
            val before = callbacks.size
            case("six-unsettled-first-last-taps") {
                val began = SystemClock.uptimeMillis()
                repeat(6) { index ->
                    val point = if (index % 2 == 0) b else a
                    val deadline = began + index * (requestedHoldMs + rapidGapMs)
                    inject(MotionEvent.ACTION_DOWN, point, deadline, "rapid-$index-down")
                    inject(MotionEvent.ACTION_UP, point, deadline + requestedHoldMs, "rapid-$index-up")
                }
            }
            assertSelected(0)
            assertEquals("Every alternating direct tap commits once", 6, callbacks.size - before)
        }
        if ("interrupt" in groups && control != Control.segmented) {
            selectFirst(a)
            val before = callbacks.size
            case("moving-direct-preview-becomes-drag") {
                drag(b, listOf(a), 180, "preview-to-drag", holdMs = 150, endHoldMs = 0)
            }
            assertSelected(0)
            assertEquals("Returning from an interrupted preview does not navigate", before, callbacks.size)
            case("released-drag-interrupted-by-direct-return") {
                drag(a, listOf(b), 180, "drag-to-direct", holdMs = 0, endHoldMs = 0)
                SystemClock.sleep(40)
                tap(a, "direct-interrupt")
            }
            assertSelected(0)
            assertEquals("Both completed interruption gestures commit once", before + 2, callbacks.size)
        }
    }

    private fun toggleScript() {
        fun center() = readOnMain { requireNotNull(tagged("motion-control")).bounds.center }
        fun lens() = readOnMain { requireNotNull(tagged("motion-switch-lens")).bounds.center }
        fun assertChecked(expected: Boolean) = assertEquals("Switch value after $scenario", expected, readOnMain { checked })
        if ("direct" in groups) {
            case("tap-on") { tap(center(), "tap-on") }
            assertTrue("Tap enables switch", readOnMain { checked })
            case("tap-off") { tap(center(), "tap-off") }
            assertTrue("Second tap disables switch", !readOnMain { checked })
        }
        if ("hold" in groups) {
            if (readOnMain { checked }) case("setup-off") { tap(center(), "setup-off") }
            case("held-toggle-on") { tap(center(), "hold-on", 700) }
            assertChecked(true)
            case("held-toggle-off") { tap(center(), "hold-off", 700) }
            assertChecked(false)
        }
        if ("drag" in groups) {
            if (readOnMain { checked }) case("setup-off") { tap(center(), "setup-off") }
            val a = lens(); val b = a + Offset(22f * density, 0f)
            case("slow-forward-drag") { drag(a, listOf(b), 600, "toggle-forward") }
            assertChecked(true)
            case("slow-reverse-drag") { drag(lens(), listOf(a), 600, "toggle-reverse") }
            assertChecked(false)
            case("fast-forward-drag") { drag(lens(), listOf(b), 120, "toggle-fast-forward") }
            assertChecked(true)
            case("fast-reverse-drag") { drag(lens(), listOf(a), 120, "toggle-fast-reverse") }
            assertChecked(false)
            case("held-drag-reverses-before-up") { drag(lens(), listOf(b, a), 200, "toggle-reverse-before-up") }
            assertChecked(false)
        }
        if ("cancel" in groups) {
            val before = callbacks.size
            val oldValue = readOnMain { checked }
            case("press-cancel") { tap(center(), "toggle-cancel", 350, cancel = true) }
            assertEquals("Cancelled switch press does not commit", before, callbacks.size)
            assertChecked(oldValue)
            for (initialValue in listOf(false, true)) {
                if (readOnMain { checked } != initialValue) {
                    case("setup-cancel-$initialValue") { tap(center(), "setup-cancel-$initialValue") }
                    assertChecked(initialValue)
                }
                val beforeDrag = callbacks.size
                val start = lens()
                val destination = start + Offset((if (initialValue) -22f else 22f) * density, 0f)
                case("drag-cancel-$initialValue") {
                    drag(start, listOf(destination), 300, "toggle-drag-cancel-$initialValue", cancel = true)
                }
                assertEquals("Cancelled moving switch does not commit", beforeDrag, callbacks.size)
                assertChecked(initialValue)
                case("tap-after-drag-cancel-$initialValue") {
                    tap(center(), "toggle-after-cancel-$initialValue")
                }
                assertChecked(!initialValue)
                assertEquals("A new tap after cancellation commits exactly once", beforeDrag + 1, callbacks.size)
            }
        }
        if ("rapid" in groups) {
            val before = callbacks.size
            case("six-unsettled-toggle-taps") {
                val point = center(); val began = SystemClock.uptimeMillis()
                repeat(6) { index ->
                    val deadline = began + index * (requestedHoldMs + rapidGapMs)
                    inject(MotionEvent.ACTION_DOWN, point, deadline, "rapid-$index-down")
                    inject(MotionEvent.ACTION_UP, point, deadline + requestedHoldMs, "rapid-$index-up")
                }
            }
            assertEquals("Every rapid switch tap commits once", 6, callbacks.size - before)
        }
    }

    private fun sliderScript() {
        fun thumb(tag: String) = readOnMain { requireNotNull(tagged(tag)).bounds.center }
        val rail = readOnMain { requireNotNull(tagged("motion-control")).bounds }
        fun railAt(fraction: Float) = Offset(rail.left + rail.width * fraction, rail.center.y)
        fun endValue() = readOnMain { if (control == Control.range) rangeEnd else value }
        fun assertBounds() = readOnMain {
            assertTrue("Single value stays finite and in domain", value.isFinite() && value in 0f..1f)
            assertTrue("Range values stay finite, ordered and in domain",
                rangeStart.isFinite() && rangeEnd.isFinite() && rangeStart in 0f..rangeEnd && rangeEnd <= 1f)
        }
        if ("direct" in groups) {
            case("tap-rail-high") { tap(railAt(.85f), "rail-high") }
            assertTrue("High rail tap moves the end toward the high side", endValue() > .8f)
            case("tap-rail-low") { tap(railAt(.15f), "rail-low") }
            if (control == Control.range) readOnMain {
                assertTrue("Low rail tap moves the start only", rangeStart < .2f && rangeEnd > .8f)
            } else assertTrue("Low rail tap moves the value toward the low side", endValue() < .2f)
            assertBounds()
        }
        if ("hold" in groups) {
            val before = readOnMain { Triple(value, rangeStart, rangeEnd) }
            val beforeCallbacks = callbacks.size
            case("hold-end-thumb") { tap(thumb("glass-slider-end"), "hold-end", 700) }
            if (control == Control.range) case("hold-start-thumb") { tap(thumb("glass-slider-start"), "hold-start", 700) }
            assertEquals("Stationary thumb holds do not change a value", before,
                readOnMain { Triple(value, rangeStart, rangeEnd) })
            assertEquals("Stationary thumb holds emit no value change", beforeCallbacks, callbacks.size)
        }
        if ("drag" in groups) {
            var previousEnd = endValue()
            case("slow-forward-end") { drag(thumb("glass-slider-end"), listOf(railAt(.9f)), 700, "end-forward") }
            assertTrue("Slow forward drag increases the end", endValue() > previousEnd)
            previousEnd = endValue()
            case("slow-reverse-end") { drag(thumb("glass-slider-end"), listOf(railAt(.35f)), 700, "end-reverse") }
            assertTrue("Slow reverse drag decreases the end", endValue() < previousEnd)
            previousEnd = endValue()
            case("fast-forward-end") { drag(thumb("glass-slider-end"), listOf(railAt(.95f)), 150, "end-fast") }
            assertTrue("Fast forward drag increases the end", endValue() > previousEnd)
            case("held-end-reverses-before-up") { drag(thumb("glass-slider-end"), listOf(railAt(.4f), railAt(.85f)), 200, "end-reverses") }
            if (control == Control.range) {
                case("slow-forward-start") { drag(thumb("glass-slider-start"), listOf(railAt(.65f)), 700, "start-forward") }
                case("fast-reverse-start") { drag(thumb("glass-slider-start"), listOf(railAt(.1f)), 150, "start-reverse") }
                case("start-cannot-cross-end") { drag(thumb("glass-slider-start"), listOf(railAt(.98f)), 300, "start-crossing") }
                assertTrue("Range remains ordered", readOnMain { rangeStart <= rangeEnd })
            }
            assertBounds()
        }
        if ("cancel" in groups) {
            val finishedBefore = trace.count { it.getString("event") == "value_change_finished" }
            case("end-drag-cancel") { drag(thumb("glass-slider-end"), listOf(railAt(.5f)), 300, "end-cancel", cancel = true) }
            assertEquals("Cancel does not report a finished slider gesture", finishedBefore,
                trace.count { it.getString("event") == "value_change_finished" })
            assertBounds()
            // Sliders emit value updates while moving. Cancel is observed without assuming rollback.
        }
        if ("rapid" in groups && control == Control.range) {
            readOnMain { rangeStart = .5f; rangeEnd = .5f; marker++ }
            SystemClock.sleep(settleMs)
            case("coincident-range-opens-low-by-tap") { tap(railAt(.15f), "coincident-low") }
            readOnMain {
                assertTrue("A lower rail tap reopens coincident thumbs", rangeStart < .2f)
                assertEquals(.5f, rangeEnd, .0001f)
                rangeStart = .5f; rangeEnd = .5f; marker++
            }
            SystemClock.sleep(settleMs)
            case("coincident-range-opens-high-by-tap") { tap(railAt(.85f), "coincident-high") }
            readOnMain {
                assertEquals(.5f, rangeStart, .0001f)
                assertTrue("A higher rail tap reopens coincident thumbs", rangeEnd > .8f)
                rangeStart = .25f; rangeEnd = .75f; marker++
            }
            SystemClock.sleep(settleMs)
        }
        if ("rapid" in groups) {
            val before = callbacks.size
            case("six-unsettled-rail-taps") {
                val began = SystemClock.uptimeMillis()
                repeat(6) { index ->
                    // Exercise the same range endpoint, rather than repeatedly
                    // tapping each endpoint's already unchanged destination.
                    val fraction = if (control == Control.range) {
                        if (index % 2 == 0) .9f else .6f
                    } else if (index % 2 == 0) .8f else .2f
                    val point = railAt(fraction)
                    val deadline = began + index * (requestedHoldMs + rapidGapMs)
                    inject(MotionEvent.ACTION_DOWN, point, deadline, "rapid-$index-down")
                    inject(MotionEvent.ACTION_UP, point, deadline + requestedHoldMs, "rapid-$index-up")
                }
            }
            assertEquals("All six alternating rail taps change a value", 6, callbacks.size - before)
            assertBounds()
        }
    }

    private fun case(label: String, gesture: () -> Unit) {
        scenario = "${readOnMain { control.name }}:$label"
        mark(label)
        gesture()
        SystemClock.sleep(settleMs)
        readOnMain { snapshot(add("scenario_complete")) }
    }

    private fun selectFirst(first: Offset) {
        if (readOnMain { selected } != 0) case("setup-first") { tap(first, "setup-first") }
    }

    private fun assertSelected(expected: Int) = assertEquals("Selected destination after $scenario", expected, readOnMain { selected })

    private fun tap(point: Offset, label: String, holdMs: Long = requestedHoldMs, cancel: Boolean = false) {
        val began = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, point, began, "$label-down")
        // The first asynchronous injection may arrive late. Anchor the hold to
        // actual app delivery so a requested 67ms tap cannot shrink to 28ms.
        waitFor("$label down delivery") {
            lastDeliveredAction == MotionEvent.ACTION_DOWN && lastDeliveredEventMs == downTime
        }
        val delivered = readOnMain { checkNotNull(lastDeliveredUptimeMs) }
        inject(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, point, delivered + holdMs, "$label-end")
    }

    private fun drag(from: Offset, destinations: List<Offset>, durationPerLegMs: Long, label: String,
        cancel: Boolean = false, holdMs: Long = 450L, endHoldMs: Long = 250L) {
        val began = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, from, began, "$label-down")
        var previous = from
        destinations.forEachIndexed { leg, destination ->
            val steps = ceil(durationPerLegMs / 16.0).toInt()
            repeat(steps) { index ->
                val fraction = (index + 1f) / steps
                val deadline = began + holdMs + leg * durationPerLegMs + (durationPerLegMs * fraction).roundToLong()
                inject(MotionEvent.ACTION_MOVE, previous + (destination - previous) * fraction, deadline, "$label-leg-$leg-$index")
            }
            previous = destination
        }
        inject(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, previous,
            began + holdMs + destinations.size * durationPerLegMs + endHoldMs, "$label-end")
    }

    private fun inject(action: Int, point: Offset, deadline: Long, label: String) {
        sleepUntil(deadline)
        val beforeNs = SystemClock.elapsedRealtimeNanos()
        val beforeMs = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) downTime = beforeMs
        val event = MotionEvent.obtain(downTime, beforeMs, action, point.x, point.y, 0)
            .apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        // Async public input injection preserves short deadlines. Actual app dispatch is observed below.
        val accepted = try { instrumentation.uiAutomation.injectInputEvent(event, false) } finally { event.recycle() }
        activePointer = action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL
        lastPointer = point
        add("pointer_injected").put("label", label).put("action", action).put("down_time_ms", downTime)
            .put("planned_uptime_ms", deadline).put("event_time_ms", beforeMs)
            .put("inject_start_elapsed_ns", beforeNs).put("inject_end_elapsed_ns", SystemClock.elapsedRealtimeNanos())
            .put("lateness_ms", beforeMs - deadline).put("x_screen_px", point.x).put("y_screen_px", point.y)
            .put("sync", false).put("accepted", accepted)
        assertTrue("Public input accepted: $label", accepted)
    }

    private fun delivery(event: MotionEvent, phase: String): JSONObject {
        val now = SystemClock.elapsedRealtimeNanos()
        val monotonic = System.nanoTime()
        if (phase == "dispatch_before") {
            lastDeliveredAtNs = now
            lastDeliveredMonotonicNs = monotonic
            lastDeliveredUptimeMs = SystemClock.uptimeMillis()
            lastDeliveredEventMs = event.eventTime
            lastDeliveredAction = event.actionMasked
        }
        return add("pointer_delivered").put("phase", phase).put("action", event.actionMasked)
            .put("event_time_ms", event.eventTime).put("down_time_ms", event.downTime)
            .put("delivery_elapsed_ns", now).put("delivery_monotonic_ns", monotonic)
            .put("x_screen_px", event.rawX).put("y_screen_px", event.rawY)
            .put("pressure", event.pressure).put("tool_type", event.getToolType(event.actionIndex))
            .put("history_size", event.historySize).put("selected_at_dispatch", selected).put("checked_at_dispatch", checked)
            .put("history_clock", "historical_event_time_ms is MotionEvent uptime, matching event_time_ms; coordinates are actual delivered event samples")
            .put("history", JSONArray((0 until event.historySize).map { history ->
                JSONObject().put("historical_event_time_ms", event.getHistoricalEventTime(history))
                    .put("pointers", JSONArray((0 until event.pointerCount).map { pointer ->
                        JSONObject().put("id", event.getPointerId(pointer)).put("tool_type", event.getToolType(pointer))
                            .put("x_local_px", event.getHistoricalX(pointer, history))
                            .put("y_local_px", event.getHistoricalY(pointer, history))
                            .put("pressure", event.getHistoricalPressure(pointer, history))
                            .put("x_screen_px", event.getHistoricalX(pointer, history) + event.getRawX(pointer) - event.getX(pointer))
                            .put("y_screen_px", event.getHistoricalY(pointer, history) + event.getRawY(pointer) - event.getY(pointer))
                    }))
            }))
    }

    private fun recordCallback(newValue: String) {
        callbacks.add(add("value_callback").put("value", newValue).put("selected_before", selected)
            .put("checked_before", checked).put("last_delivery_action", lastDeliveredAction ?: JSONObject.NULL)
            .put("last_delivery_event_ms", lastDeliveredEventMs ?: JSONObject.NULL))
    }

    private fun observedNodes(): List<ObservedNode> {
        val found = ArrayList<ObservedNode>()
        fun visitNode(node: SemanticsNode, offset: Offset, rootOrigin: Offset) {
            val tag = node.config.getOrNull(SemanticsProperties.TestTag)
            if (tag != null || node.config.getOrNull(SemanticsProperties.Role) == Role.Tab) found += ObservedNode(node, offset, rootOrigin)
            node.children.forEach { visitNode(it, offset, rootOrigin) }
        }
        fun visitView(view: View) {
            if (view is ViewRootForTest) {
                val screen = IntArray(2); val window = IntArray(2)
                view.getLocationOnScreen(screen); view.getLocationInWindow(window)
                visitNode(view.semanticsOwner.unmergedRootSemanticsNode,
                    Offset((screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat()),
                    Offset(screen[0].toFloat(), screen[1].toFloat()))
            } else if (view is ViewGroup) repeat(view.childCount) { visitView(view.getChildAt(it)) }
        }
        realView?.let(::visitView)
        return found
    }

    private fun tagged(tag: String) = observedNodes().firstOrNull { it.tag == tag }

    private fun snapshot(row: JSONObject) {
        val began = SystemClock.elapsedRealtimeNanos()
        row.put("read_start_elapsed_ns", began).put("read_start_monotonic_ns", System.nanoTime())
            .put("selected", selected).put("checked", checked)
            .put("value", value).put("range_start", rangeStart).put("range_end", rangeEnd).put("marker", marker)
            .put("last_delivery_elapsed_ns", lastDeliveredAtNs ?: JSONObject.NULL)
            .put("last_delivery_monotonic_ns", lastDeliveredMonotonicNs ?: JSONObject.NULL)
            .put("last_delivery_event_ms", lastDeliveredEventMs ?: JSONObject.NULL)
            .put("last_delivery_action", lastDeliveredAction ?: JSONObject.NULL)
        row.put("nodes", JSONArray(observedNodes().map { observed ->
            val node = observed.node
            JSONObject().put("tag", observed.tag ?: JSONObject.NULL).put("screen_bounds_px", rect(observed.bounds))
                .put("bounds_in_root_px", rect(node.boundsInRoot)).put("root_screen_origin_px", point(observed.rootOrigin))
                .put("role", node.config.getOrNull(SemanticsProperties.Role)?.toString() ?: JSONObject.NULL)
                .put("selected", node.config.getOrNull(SemanticsProperties.Selected) ?: JSONObject.NULL)
                .put("text", node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: JSONObject.NULL)
                .also { item ->
                    node.config.getOrNull(DockLensBoundsInRootPx)?.let { painted ->
                        item.put("painted_dock_lens_bounds_root_px", rect(painted))
                            .put("painted_dock_lens_bounds_screen_px", rect(painted.translate(observed.rootOrigin)))
                    }
                }
        }))
        val ended = SystemClock.elapsedRealtimeNanos()
        row.put("read_end_elapsed_ns", ended).put("read_end_monotonic_ns", System.nanoTime())
            .put("observer_cost_ms", (ended - began) / 1_000_000.0)
    }

    private val frameObserver = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val row = add("vsync_sample").put("frame", ++vsyncCount).put("frame_time_nanos", frameTimeNanos)
                .put("refresh_interval_ms", lastVsyncNs?.let { (frameTimeNanos - it) / 1_000_000.0 } ?: JSONObject.NULL)
            lastVsyncNs = frameTimeNanos
            snapshot(row)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun watchDraws() {
        val listener = ViewTreeObserver.OnDrawListener {
            if (running) {
                val drawId = ++drawCount
                val frame = vsyncCount
                val drawMarker = marker
                snapshot(add("window_draw").put("draw_id", drawId).put("vsync_frame", frame)
                    .put("vsync_frame_time_nanos", lastVsyncNs ?: JSONObject.NULL)
                    .put("hardware_accelerated", dispatchObserver.isHardwareAccelerated))
                dispatchObserver.viewTreeObserver.registerFrameCommitCallback {
                    add("frame_commit").put("draw_id", drawId).put("draw_vsync_frame", frame).put("draw_marker", drawMarker)
                }
            }
        }
        drawListener = listener
        dispatchObserver.viewTreeObserver.addOnDrawListener(listener)
    }

    private fun mark(label: String) = readOnMain {
        marker++
        add("visual_sync_requested").put("label", label).put("marker", marker)
    }

    private fun add(event: String) = JSONObject().put("event", event).put("sequence", sequence.incrementAndGet())
        .put("control", control.name).put("scenario", scenario).put("uptime_ms", SystemClock.uptimeMillis())
        .put("elapsed_realtime_nanos", SystemClock.elapsedRealtimeNanos()).put("monotonic_nanos", System.nanoTime()).also(trace::add)

    private fun writeSession(directory: File, controls: List<Control>) {
        val row = readOnMain {
            JSONObject().put("schema", 1).put("sdk", Build.VERSION.SDK_INT).put("model", Build.MODEL)
                .put("fingerprint", Build.FINGERPRINT).put("density", density).put("font_scale", fontScale)
                .put("controls", JSONArray(controls.map { it.name })).put("scenarios", JSONArray(groups.toList()))
                .put("tap_hold_ms_requested", requestedHoldMs).put("settle_ms_requested", settleMs)
                .put("rapid_gap_ms_requested", rapidGapMs).put("composition_clock", "Lifecycle AndroidUiDispatcher/Choreographer; no test clock after bootstrap")
                .put("input", "Public UiAutomation injectInputEvent(sync=false), actual eventTime and injection start/end exported; real FrameLayout dispatch_before/after records delivered time and callback order. Actual deadlines may be late and are never fabricated.")
                .put("clock_domains", "frame_time_nanos is Choreographer's monotonic clock; each row and read interval also exports System.nanoTime for that domain. elapsed_realtime_nanos includes device sleep, and MotionEvent eventTime/uptime_ms are millisecond uptime; their offsets are observed, never assumed interchangeable.")
                .put("sampling", "vsync_sample and window_draw bracket public semantics reads. Observer callback order is not product withFrame callback order; this fixture does not expose or infer the latter. frame_commit is HWUI submission, not display presentation.")
                .put("geometry", "screen_bounds_px is transformed public semantics geometry, not segmented video pixels. Dock's custom painted rectangle includes its parent transform; carrier bounds cover whole Dock. Both root/screen origins and tab bounds are exported to remove parent motion. Other lens nodes expose layout geometry.")
                .put("display_evidence", "Record the hardware display externally with adb screenrecord; the 8dp corner marker links requested scene transitions to committed draws and video frames. Capture alone does not prove native parity.")
                .put("gestures", "Synthetic public trajectories: direct first/last taps, selected/unselected 700ms holds, slow/fast drags both ways, same-stream reverse, pointer cancel, and six direct toggles with short gaps. These are Android acceptance inputs, not asserted native waveforms.")
                .put("scope", "Actual product controls/handlers/shaders. Fixture callback immediately updates controlled state; no app navigation/persistence. Sliders emit while moving and cancellation does not imply rollback.")
                .also(::snapshot)
        }
        File(directory, "session.json").writeText(row.toString(2))
    }

    private fun rect(rect: Rect) = JSONArray(listOf(rect.left, rect.top, rect.width, rect.height))
    private fun point(point: Offset) = JSONArray(listOf(point.x, point.y))

    private fun argumentLong(name: String, fallback: Long, range: LongRange): Long =
        (InstrumentationRegistry.getArguments().getString(name)?.toLong() ?: fallback).also { require(it in range) { "$name outside $range" } }

    private fun <T> readOnMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }

    private fun waitFor(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (!readOnMain(condition)) {
            assertTrue("Timed out: $label", SystemClock.uptimeMillis() < deadline)
            SystemClock.sleep(10)
        }
    }

    private fun sleepUntil(deadline: Long) {
        while (true) {
            val remaining = deadline - SystemClock.uptimeMillis()
            if (remaining <= 0) return
            SystemClock.sleep(remaining)
        }
    }
}
