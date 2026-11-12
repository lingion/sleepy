package com.lingion.sleepy.ui.component

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.ui.component.liquidglass.GlassBackground
import com.lingion.sleepy.ui.component.liquidglass.DockLensBoundsInRootPx
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.component.liquidglass.GlassSlider
import com.lingion.sleepy.ui.component.liquidglass.GlassSwitch
import com.lingion.sleepy.ui.component.liquidglass.LiquidGlassNavigationBar
import com.lingion.sleepy.ui.component.liquidglass.LocalGlassDepth
import com.lingion.sleepy.ui.component.liquidglass.LocalGlassHost
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.roundToInt

/**
 * API 33+ hardware captures of product controls, using a controlled Compose clock.
 * The declared injection timeline is synthetic; wall capture time is recorded separately.
 * No control geometry, animation, shader, or pointer handler is replaced by this fixture.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class NativeGlassParityCaptureTest {
    @get:Rule val compose = createComposeRule()

    private enum class Background { neutral, grid, checker, stripes }
    private enum class Control(val lensTag: String?) {
        toggle("parity-toggle-lens"), segmented("segment-thumb"),
        dock("dock-lens"), slider("glass-slider-end"),
    }
    private data class Scene(val background: Background, val control: Control,
                             val scenario: String = "hold-drag")
    private data class SelectionObservation(val scene: Scene, val event: String, val value: String,
                                           val composeMs: Long, val injectedMs: Long, val wallMs: Double)

    private lateinit var root: View
    private lateinit var contentView: View
    private lateinit var window: Window
    private lateinit var fixtureActivity: Activity
    private var density = 1f
    private var valueDescription = ""
    private var pointer = Offset.Zero
    private var pointerDown = false
    private var injectedMs = 0L
    private var frame = 0
    private var gestureStartMs = 0L
    private var wallStartNs = 0L
    private var initialValueOverride: Float? = null
    private var activeCaptureScene: Scene? = null
    private var inputEvent = "setup"
    private val selectionObservations = ConcurrentLinkedQueue<SelectionObservation>()
    private data class ReplayCallback(val value: String, val uptimeMs: Long)
    private val replayCallbacks = ConcurrentLinkedQueue<ReplayCallback>()
    private var realTimeReplay = false

    private val tint = Color(0f, .65f, .75f, 1f)
    private val neutral = Color(.94f, .94f, .96f, 1f)
    private val ink = Color(0xFF123239)
    // main.swift uses dynamic UIColor system colors. These explicit light sRGB
    // samples are declared in metadata; they are not claimed to be a resolved iOS trace.
    private var stripePaletteSource = "explicit classic light sRGB fallback; not resolved from this iOS run"
    private var stripeColors = listOf(
        Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFCC00), Color(0xFF34C759),
        Color(0xFF30B0C7), Color(0xFF007AFF), Color(0xFFAF52DE),
    )

    @Test fun captureReferenceMotion() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Reference capture is manual; pass glassCapture=true", arguments.getString("glassCapture") == "true")
        val backgrounds = (arguments.getString("glassBackgrounds") ?: "neutral,grid,checker,stripes")
            .split(',').map { Background.valueOf(it.trim()) }
        val controls = (arguments.getString("glassControls") ?: "toggle,segmented,dock,slider")
            .split(',').map { Control.valueOf(it.trim()) }
        configureStripeColors(arguments.getString("glassStripeRGBA"))
        assertTrue("At least one background and control are required", backgrounds.isNotEmpty() && controls.isNotEmpty())
        val scene = mutableStateOf(Scene(backgrounds.first(), controls.first()))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runID = (arguments.getString("glassRunId") ?: System.currentTimeMillis().toString())
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
        val directory = File(context.getExternalFilesDir(null), "native-glass-parity/$runID")
        assertTrue("Create the capture directory", directory.isDirectory || directory.mkdirs())
        val frames = File(directory, "frames.csv").bufferedWriter()
        val inputs = File(directory, "inputs.csv").bufferedWriter()
        frames.appendLine("background,control,scenario,phase,frame,compose_ms,gesture_compose_ms,declared_injected_ms,wall_elapsed_ms,pointer_down,input_x_dp,input_y_dp,control_x_dp,control_y_dp,control_w_dp,control_h_dp,lens_x_dp,lens_y_dp,lens_w_dp,lens_h_dp,lens_geometry_source,value,png,crop_png")
        inputs.appendLine("background,control,scenario,event,declared_injected_ms,compose_ms,x_dp,y_dp,requested_delta_ms,value,observation,wall_elapsed_ms")
        wallStartNs = SystemClock.elapsedRealtimeNanos()
        compose.setContent { ReferenceFixture(scene.value) }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        writeMetadata(directory, backgrounds, controls)
        try {
            for (background in backgrounds) for (control in controls) {
                val current = Scene(background, control)
                compose.runOnIdle { scene.value = current }
                advance(64)
                val node = compose.onNodeWithTag("parity-control")
                node.fetchSemanticsNode() // Fail before creating a partial gesture if layout is missing.
                val output = File(directory, "${background.name}-${control.name}").apply { mkdirs() }
                frame = 0
                injectedMs = 0L
                pointerDown = false
                activeCaptureScene = current
                gestureStartMs = compose.mainClock.currentTime
                pointer = startPoint(control, node)
                capture(current, "rest", output, frames)
                prepareInput(current, "down", 0)
                node.performTouchInput { down(pointer) }
                pointerDown = true
                input(current, "down", 0, inputs)
                for ((delta, phase) in listOf(32 to "rise-32", 64 to "rise-96", 80 to "rise-176", 464 to "held-640")) {
                    hold(node, delta)
                    capture(current, phase, output, frames)
                }

                val paths = pathPoints(control, node)
                move(current, node, paths[0], 16, "slow-drag", output, frames, inputs, captureEvery = 4)
                move(current, node, paths[1], 4, "fast-drag", output, frames, inputs)
                for ((delta, phase) in listOf(16 to "stop-16", 48 to "stop-64", 96 to "stop-160")) {
                    hold(node, delta)
                    capture(current, phase, output, frames)
                }
                move(current, node, paths[2], 4, "reverse", output, frames, inputs)
                prepareInput(current, "up", 16)
                node.performTouchInput { advanceEventTime(16); up() }
                pointerDown = false
                input(current, "up", 16, inputs)
                for ((delta, phase) in listOf(16 to "release-16", 48 to "release-64", 64 to "release-128", 128 to "release-256", 256 to "release-512", 512 to "release-1024")) {
                    advance(delta)
                    capture(current, phase, output, frames)
                }

                // Recreate only this fixture's state, so A is selected before
                // touching unselected B. Do not emulate a product callback.
                val direct = current.copy(scenario = "direct-b-hold")
                compose.runOnIdle { scene.value = direct }
                advance(64)
                activeCaptureScene = direct
                injectedMs = 0L
                gestureStartMs = compose.mainClock.currentTime
                val directNode = compose.onNodeWithTag("parity-control")
                pointer = directBPoint(control, directNode)
                capture(direct, "direct-b-rest", output, frames)
                prepareInput(direct, "down", 0)
                directNode.performTouchInput { down(pointer) }
                pointerDown = true
                input(direct, "down", 0, inputs)
                capture(direct, "direct-b-down", output, frames)
                for ((delta, phase) in listOf(32 to "rise-32", 64 to "rise-96", 80 to "rise-176", 464 to "held-640")) {
                    hold(directNode, delta)
                    capture(direct, "direct-b-$phase", output, frames)
                }
                prepareInput(direct, "up", 16)
                directNode.performTouchInput { advanceEventTime(16); up() }
                pointerDown = false
                input(direct, "up", 16, inputs)
                capture(direct, "direct-b-up", output, frames)
                for ((delta, phase) in listOf(16 to "release-16", 112 to "release-128", 384 to "release-512")) {
                    advance(delta)
                    capture(direct, "direct-b-$phase", output, frames)
                }
                drainSelectionObservations(inputs)
                frames.flush()
                inputs.flush()
            }
        } finally {
            if (pointerDown) runCatching { compose.onNodeWithTag("parity-control").performTouchInput { cancel() } }
            drainSelectionObservations(inputs)
            activeCaptureScene = null
            compose.mainClock.autoAdvance = true
            frames.close()
            inputs.close()
        }
    }

    /** Replays observed UIKit point deltas and timing through Android's public input API. */
    @Test fun replayNativeGesture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val replayPath = arguments.getString("glassReplay")
        assumeTrue("replayNativeGesture requires glassReplay; captureReferenceMotion is independent", replayPath != null)
        val replayFile = File(requireNotNull(replayPath))
        require(replayFile.isAbsolute && replayFile.isFile) { "glassReplay must name an existing absolute JSON file" }
        val replay = JSONObject(replayFile.readText())
        require(replay.getInt("schema") == 1) { "Unsupported export-touches schema" }
        val index = arguments.getString("glassGestureIndex")?.toInt() ?: 0
        val gesture = replay.getJSONArray("gestures").getJSONObject(index)
        require(gesture.getBoolean("complete")) { "Replay requires one complete single-finger gesture" }
        val samples = gesture.getJSONArray("samples")
        require(samples.length() >= 2 && samples.getJSONObject(0).getInt("phase") == 0 &&
            samples.getJSONObject(samples.length() - 1).getInt("phase") in 3..4) { "Gesture must begin down and end up/cancel" }
        val control = Control.valueOf(requireNotNull(arguments.getString("glassControl")) { "glassControl must explicitly name the target control" })
        initialValueOverride = arguments.getString("glassInitialValue")?.toFloat()?.also {
            require(it.isFinite()) { "glassInitialValue must be finite" }
        }
        val background = Background.valueOf(arguments.getString("glassBackground") ?: "grid")
        fun startFraction(name: String): Float? = arguments.getString(name)?.toFloat()?.also {
            require(it.isFinite() && it in 0f..1f) { "$name must be finite and between 0 and 1" }
        }
        val startXFraction = startFraction("glassStartXFraction")
        val startYFraction = startFraction("glassStartYFraction")
        val scene = Scene(background, control)
        realTimeReplay = true
        configureStripeColors(arguments.getString("glassStripeRGBA"))
        val context = instrumentation.targetContext
        val runID = (arguments.getString("glassRunId") ?: "replay-${System.currentTimeMillis()}")
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
        val directory = File(context.getExternalFilesDir(null), "native-glass-parity/$runID")
        assertTrue("Create replay output directory", directory.isDirectory || directory.mkdirs())
        compose.mainClock.autoAdvance = true
        compose.setContent { ReferenceFixture(scene) }
        compose.waitForIdle()

        // A test rule's autoAdvance clock pumps time during synchronization; it
        // is not a wall clock. Give this composition a lifecycle-aware Android
        // Choreographer parent so sleep/inject never freezes or fast-forwards it.
        var realView: ComposeView? = null
        var realRecomposer: Recomposer? = null
        val ready = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val activity = fixtureActivity
            realView = ComposeView(activity).also { view ->
                val parent = view.createLifecycleAwareWindowRecomposer(lifecycle =
                    (activity as androidx.lifecycle.LifecycleOwner).lifecycle)
                realRecomposer = parent
                view.setParentCompositionContext(parent)
                view.setContent {
                    ReferenceFixture(scene)
                    SideEffect { ready.countDown() }
                }
                activity.setContentView(view)
            }
        }
        assertTrue("Real-time composition must initialize", ready.await(5, TimeUnit.SECONDS))
        compose.waitUntil(5_000) { realView?.width?.let { it > 0 } == true }
        val node = compose.onNodeWithTag("parity-control")
        val controlBounds = bounds(node)
        val standardStart = startPoint(control, node)
        val start = Offset(startXFraction?.times(controlBounds.width) ?: standardStart.x,
            startYFraction?.times(controlBounds.height) ?: standardStart.y) + controlBounds.topLeft
        val origin = IntArray(2)
        instrumentation.runOnMainSync { contentView.getLocationOnScreen(origin) }
        writeMetadata(directory, listOf(background), listOf(control), realTime = true)
        val first = samples.getJSONObject(0)
        val firstTime = first.getDouble("dtMs")
        val replayMetadata = JSONObject()
            .put("source", replayFile.absolutePath).put("gesture_index", index)
            .put("touch_id", gesture.get("touchID")).put("sample_count", samples.length())
            .put("initial_value_override", initialValueOverride ?: JSONObject.NULL)
            .put("mapping", "First observed UIKit point anchors the current control standard start, with each provided glassStartXFraction/glassStartYFraction replacing that local coordinate by fraction*parity-control bounds size. Missing axes keep their standard start. Each subsequent UIKit point delta uses 1pt=1Android dp*density. No full-window normalization, interpolation, clamping, or smoothing.")
            .put("start_fraction_override", JSONObject().put("x", startXFraction ?: JSONObject.NULL)
                .put("y", startYFraction ?: JSONObject.NULL))
            .put("android_control_bounds_root_px", JSONArray(listOf(controlBounds.left,
                controlBounds.top, controlBounds.width, controlBounds.height)))
            .put("first_native_point_pt", JSONArray(listOf(first.getDouble("x"), first.getDouble("y"))))
            .put("android_anchor_screen_px", JSONArray(listOf(start.x + origin[0], start.y + origin[1])))
            .put("timing", "Each dtMs deadline is scheduled against uptimeMillis. MotionEvent eventTime is actual injection-call uptime, not the requested deadline. injectInputEvent(sync=true) blocking and lateness are exported; application pointer-delivery time is not observed.")
            .put("animation_clock", "Lifecycle-aware AndroidUiDispatcher/Choreographer Recomposer, separate from Compose test mainClock. No test synchronization or screenshots during injection.")
        File(directory, "replay.json").writeText(replayMetadata.toString(2))
        File(directory, "source-replay-touches.json").writeText(replay.toString(2))
        var downTime = 0L
        var active = false
        var lastX = start.x + origin[0]
        var lastY = start.y + origin[1]
        val injected = ArrayList<String>(samples.length())
        val startUptime = SystemClock.uptimeMillis() + 250L
        var previousTime = firstTime
        try {
            for (sampleIndex in 0 until samples.length()) {
                val sample = samples.getJSONObject(sampleIndex)
                val time = sample.getDouble("dtMs")
                require(time.isFinite() && time >= previousTime) { "Recorded samples must have finite nondecreasing dtMs" }
                previousTime = time
                val deadline = startUptime + (time - firstTime).roundToLong()
                val action = when (sample.getInt("phase")) {
                    0 -> MotionEvent.ACTION_DOWN
                    1, 2 -> MotionEvent.ACTION_MOVE
                    3 -> MotionEvent.ACTION_UP
                    4 -> MotionEvent.ACTION_CANCEL
                    else -> error("Unsupported UIKit touch phase")
                }
                require(sampleIndex == 0 || action != MotionEvent.ACTION_DOWN) { "Multiple began phases are not a single gesture" }
                val x = start.x + origin[0] + (sample.getDouble("x") - first.getDouble("x")).toFloat() * density
                val y = start.y + origin[1] + (sample.getDouble("y") - first.getDouble("y")).toFloat() * density
                require(x.isFinite() && y.isFinite()) { "Recorded positions must be finite" }
                while (true) {
                    val remaining = deadline - SystemClock.uptimeMillis()
                    if (remaining <= 0L) break
                    SystemClock.sleep(remaining)
                }
                val before = SystemClock.uptimeMillis()
                if (sampleIndex == 0) downTime = before
                val event = MotionEvent.obtain(downTime, before, action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
                val accepted = try { instrumentation.uiAutomation.injectInputEvent(event, true) } finally { event.recycle() }
                val after = SystemClock.uptimeMillis()
                active = action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL
                lastX = x
                lastY = y
                injected += "$sampleIndex,${sample.getInt("phase")},$time,$deadline,$before,$after,${before - deadline},${after - before},$x,$y,$accepted"
                assertTrue("Input injection must be accepted for sample $sampleIndex", accepted)
            }
            // Leave a real settling interval for the externally started screenrecord.
            SystemClock.sleep(1_500)
            var finalValue = ""
            instrumentation.runOnMainSync { finalValue = valueDescription }
            val callbacks = replayCallbacks.toList()
            File(directory, "callbacks-uptime.csv").writeText(
                "value,callback_uptime_ms\n" + callbacks.joinToString("\n", postfix = "\n") { "${it.value},${it.uptimeMs}" })
            replayMetadata.put("final_value", finalValue)
                .put("callbacks", JSONArray(callbacks.map { JSONObject().put("value", it.value).put("uptime_ms", it.uptimeMs) }))
            File(directory, "replay.json").writeText(replayMetadata.toString(2))
            arguments.getString("glassExpectedValue")?.let { expected ->
                assertEquals("Replay final value", expected, finalValue)
            }
            arguments.getString("glassExpectedCallbacks")?.let { expected ->
                val values = if (expected == "none") emptyList() else expected.split(',')
                assertEquals("Replay callback order", values, callbacks.map { it.value })
            }
        } finally {
            if (active) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, lastX, lastY, 0)
                    .apply { source = InputDevice.SOURCE_TOUCHSCREEN }
                try { instrumentation.uiAutomation.injectInputEvent(event, true) } finally { event.recycle() }
            }
            File(directory, "injected-uptime.csv").writeText(
                "sample,ios_phase,source_dt_ms,planned_uptime_ms,actual_inject_uptime_ms,inject_return_uptime_ms,lateness_ms,inject_block_ms,x_screen_px,y_screen_px,accepted\n" +
                    injected.joinToString("\n", postfix = "\n"))
            instrumentation.runOnMainSync { realView?.disposeComposition(); realRecomposer?.cancel() }
        }
    }

    private fun configureStripeColors(encoded: String?) {
        if (encoded == null) return
        val colors = JSONArray(encoded)
        require(colors.length() == 7) { "glassStripeRGBA requires seven resolved sRGB RGBA arrays" }
        stripeColors = (0 until colors.length()).map { index ->
            val rgba = colors.getJSONArray(index)
            Color(rgba.getDouble(0).toFloat(), rgba.getDouble(1).toFloat(), rgba.getDouble(2).toFloat(), 1f)
        }
        stripePaletteSource = "glassStripeRGBA argument: caller-provided resolved iOS sRGB"
    }

    @Composable private fun ReferenceFixture(scene: Scene) {
        val view = LocalView.current
        val currentDensity = LocalDensity.current.density
        SideEffect {
            contentView = view
            root = view.rootView
            density = currentDensity
            var activityContext = view.context
            while (activityContext !is Activity && activityContext is ContextWrapper) activityContext = activityContext.baseContext
            fixtureActivity = activityContext as Activity
            window = fixtureActivity.window
        }
        MaterialTheme(colorScheme = lightColorScheme(
            primary = tint, onPrimary = Color.White, secondary = tint, onSecondary = ink,
            secondaryContainer = tint, onSecondaryContainer = ink,
            onSurfaceVariant = Color(0xFF456269), surface = neutral, surfaceContainer = neutral,
            background = neutral, onSurface = ink, onBackground = ink,
        )) {
            CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass, LocalDarkTheme provides false) {
                GlassHost(Modifier.fillMaxSize(), background = neutral) {
                    GlassBackground(0f, Modifier.fillMaxSize()) { ReferenceBackground(scene.background) }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        key(scene) { ReferenceControl(scene.control) }
                    }
                }
            }
        }
    }

    @Composable private fun ReferenceControl(control: Control) {
        var checked by remember { mutableStateOf((initialValueOverride ?: 0f) >= .5f) }
        var selected by remember { mutableIntStateOf(initialValueOverride?.roundToInt()?.coerceIn(0, 2) ?: 0) }
        var value by remember { mutableFloatStateOf(initialValueOverride?.coerceIn(0f, 1f) ?: .25f) }
        val scheme = MaterialTheme.colorScheme
        val host = LocalGlassHost.current
        val backdrop = host?.backdropAt(LocalGlassDepth.current)
        val modifier = Modifier.testTag("parity-control")
        when (control) {
            Control.toggle -> GlassSwitch(
                checked, { observeSelection(control, it.toString()); checked = it }, modifier,
                thumbContent = { Box(Modifier.fillMaxSize().testTag("parity-toggle-lens")) },
                colors = SwitchDefaults.colors(checkedTrackColor = scheme.primary,
                    checkedThumbColor = Color.White, uncheckedThumbColor = Color.White),
            )
            Control.segmented -> SegmentedSwitcher(
                listOf(0 to "First", 1 to "Second", 2 to "Third"), selected,
                { observeSelection(control, it.toString()); selected = it }, modifier.width(335.dp),
            )
            Control.dock -> LiquidGlassNavigationBar(
                listOf(PillNavItemSpec(Icons.Filled.Home, "Home"),
                    PillNavItemSpec(Icons.Filled.Search, "Explore"),
                    PillNavItemSpec(Icons.Filled.Settings, "Settings")),
                selected, { observeSelection(control, it.toString()); selected = it }, modifier.width(316.dp), backdrop,
            )
            Control.slider -> GlassSlider(value, { observeSelection(control, it.toString()); value = it },
                modifier.width(335.dp), thumbColor = scheme.primary)
        }
        SideEffect { valueDescription = when (control) {
            Control.toggle -> checked.toString()
            Control.slider -> value.toString()
            else -> selected.toString()
        } }
    }

    @Composable private fun ReferenceBackground(background: Background) {
        Box(Modifier.fillMaxSize().background(neutral).drawBehind {
            when (background) {
                Background.neutral -> Unit
                Background.grid -> {
                    val step = 20.dp.toPx()
                    for (x in 0..ceil(size.width / step).toInt()) drawLine(Color.Black.copy(alpha = .13f),
                        Offset(x * step, 0f), Offset(x * step, size.height), .5.dp.toPx())
                    for (y in 0..ceil(size.height / step).toInt()) drawLine(Color.Black.copy(alpha = .13f),
                        Offset(0f, y * step), Offset(size.width, y * step), .5.dp.toPx())
                }
                Background.checker -> {
                    val step = 20.dp.toPx()
                    for (y in 0..ceil(size.height / step).toInt()) for (x in 0..ceil(size.width / step).toInt()) {
                        drawRect(if ((x + y) % 2 == 0) Color.Black else Color.White,
                            Offset(x * step, y * step), Size(step, step))
                    }
                }
                Background.stripes -> {
                    val step = 28.dp.toPx()
                    for (x in 0..ceil(size.width / step).toInt()) drawRect(
                        stripeColors[x % stripeColors.size].copy(alpha = .52f),
                        Offset(x * step, 0f), Size(step, size.height))
                }
            }
        })
    }

    private fun advance(milliseconds: Int) {
        repeat(milliseconds / 16) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
    }

    private fun hold(node: SemanticsNodeInteraction, milliseconds: Int) {
        inputEvent = "hold"
        injectedMs += milliseconds
        node.performTouchInput { advanceEventTime(milliseconds.toLong()) }
        advance(milliseconds)
    }

    private fun startPoint(control: Control, node: SemanticsNodeInteraction): Offset {
        val controlBounds = bounds(node)
        val lens = lensBounds(control)
        return if (lens != null) lens.center - controlBounds.topLeft else {
            val selected = initialValueOverride?.roundToInt()?.coerceIn(0, 2) ?: 0
            Offset(controlBounds.width * (.23f + .27f * selected), controlBounds.height / 2f)
        }
    }

    private fun pathPoints(control: Control, node: SemanticsNodeInteraction): List<Offset> {
        val width = bounds(node).width
        val start = pointer
        return if (control == Control.toggle) listOf(11f, 22f, 5.5f).map { Offset(start.x + it * density, start.y) }
            else listOf(.5f, .8f, .35f).map { Offset(width * it, start.y) }
    }

    private fun directBPoint(control: Control, node: SemanticsNodeInteraction): Offset {
        val controlBounds = bounds(node)
        val initial = startPoint(control, node)
        return when (control) {
            Control.toggle -> Offset(controlBounds.width - initial.x, initial.y)
            Control.slider -> Offset(controlBounds.width * .75f, initial.y)
            Control.segmented, Control.dock -> Offset(controlBounds.width * .5f, initial.y)
        }
    }

    private fun move(scene: Scene, node: SemanticsNodeInteraction, target: Offset, steps: Int,
                     phase: String, output: File, frames: java.io.Writer, inputs: java.io.Writer,
                     captureEvery: Int = 1) {
        val from = pointer
        for (step in 1..steps) {
            pointer = from + (target - from) * (step.toFloat() / steps)
            prepareInput(scene, "move-$phase", 16)
            node.performTouchInput { moveTo(pointer, 16) }
            input(scene, "move-$phase", 16, inputs)
            advance(16)
            if (step % captureEvery == 0) capture(scene, "$phase-$step", output, frames)
        }
    }

    private fun input(scene: Scene, event: String, delta: Int, writer: java.io.Writer) {
        drainSelectionObservations(writer)
        val wallMs = (SystemClock.elapsedRealtimeNanos() - wallStartNs) / 1_000_000.0
        writer.appendLine("${scene.background},${scene.control},${scene.scenario},$event,$injectedMs,${compose.mainClock.currentTime},${pointer.x / density},${pointer.y / density},$delta,$valueDescription,injection_return_snapshot,$wallMs")
    }

    private fun prepareInput(scene: Scene, event: String, delta: Int) {
        activeCaptureScene = scene
        inputEvent = event
        injectedMs += delta
    }

    private fun observeSelection(control: Control, value: String) {
        if (realTimeReplay) replayCallbacks.add(ReplayCallback(value, SystemClock.uptimeMillis()))
        val scene = activeCaptureScene ?: return
        if (scene.control != control) return
        selectionObservations.add(SelectionObservation(scene, inputEvent, value,
            compose.mainClock.currentTime, injectedMs,
            (SystemClock.elapsedRealtimeNanos() - wallStartNs) / 1_000_000.0))
    }

    private fun drainSelectionObservations(writer: java.io.Writer) {
        while (true) {
            val observed = selectionObservations.poll() ?: break
            writer.appendLine("${observed.scene.background},${observed.scene.control},${observed.scene.scenario},callback-${observed.event},${observed.injectedMs},${observed.composeMs},,,,${observed.value},product_onValueChange,${observed.wallMs}")
        }
    }

    private fun capture(scene: Scene, phase: String, output: File, writer: java.io.Writer) {
        compose.waitForIdle()
        val control = bounds(compose.onNodeWithTag("parity-control"))
        val lens = lensBounds(scene.control)
        val committed = CountDownLatch(1)
        val origin = IntArray(2)
        compose.runOnUiThread {
            contentView.getLocationInWindow(origin)
            root.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
            root.postInvalidateOnAnimation()
        }
        assertTrue("$phase: HWUI did not commit", committed.await(5, TimeUnit.SECONDS))
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val copied = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        PixelCopy.request(window, bitmap, { result = it; copied.countDown() }, Handler(Looper.getMainLooper()))
        assertTrue("$phase: PixelCopy did not complete", copied.await(5, TimeUnit.SECONDS))
        assertEquals("$phase: PixelCopy failed", PixelCopy.SUCCESS, result)
        val filename = "${(frame++).toString().padStart(3, '0')}-$phase.png"
        File(output, filename).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val margin = 40f * density
        val left = floor(control.left + origin[0] - margin).toInt().coerceIn(0, bitmap.width - 1)
        val top = floor(control.top + origin[1] - margin).toInt().coerceIn(0, bitmap.height - 1)
        val right = ceil(control.right + origin[0] + margin).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = ceil(control.bottom + origin[1] + margin).toInt().coerceIn(top + 1, bitmap.height)
        val cropName = filename.removeSuffix(".png") + "-crop.png"
        Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top).let { crop ->
            File(output, cropName).outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (crop !== bitmap) crop.recycle()
        }
        bitmap.recycle()
        val lensValues = lens?.let { geometry(it) } ?: ",,,"
        val wallMs = (SystemClock.elapsedRealtimeNanos() - wallStartNs) / 1_000_000.0
        val relative = "${scene.background}-${scene.control}"
        val geometrySource = when {
            lens == null -> "not_exposed"
            scene.control == Control.dock -> "painted_rect_mapped_to_root_including_parent_transform"
            else -> "unclipped_semantics_bounds"
        }
        writer.appendLine("${scene.background},${scene.control},${scene.scenario},$phase,${frame - 1},${compose.mainClock.currentTime},${compose.mainClock.currentTime - gestureStartMs},$injectedMs,$wallMs,$pointerDown,${pointer.x / density},${pointer.y / density},${geometry(control)},$lensValues,$geometrySource,$valueDescription,$relative/$filename,$relative/$cropName")
    }

    private fun lensBounds(control: Control): Rect? {
        val tag = control.lensTag ?: return null
        val node = compose.onNodeWithTag(tag, true)
        // The Dock's shader carrier covers the whole bar. Its custom property
        // exports the actual drawn capsule, already mapped to root pixels.
        return if (control == Control.dock) node.fetchSemanticsNode().config[DockLensBoundsInRootPx]
            else bounds(node)
    }

    private fun geometry(bounds: Rect): String =
        "${bounds.left / density},${bounds.top / density},${bounds.width / density},${bounds.height / density}"

    private fun bounds(node: SemanticsNodeInteraction): Rect = node.getUnclippedBoundsInRoot().let {
        Rect(it.left.value * density, it.top.value * density, it.right.value * density, it.bottom.value * density)
    }

    private fun writeMetadata(directory: File, backgrounds: List<Background>, controls: List<Control>, realTime: Boolean = false) {
        val origin = IntArray(2)
        compose.runOnUiThread { contentView.getLocationInWindow(origin) }
        val metadata = JSONObject()
            .put("schema", 1).put("sdk", Build.VERSION.SDK_INT).put("model", Build.MODEL)
            .put("density", density).put("window_width_px", root.width).put("window_height_px", root.height)
            .put("content_origin_in_window_px", JSONArray(origin.toList()))
            .put("logical_unit", "Android dp corresponds to iOS reference pt; PNG remains device pixels")
            .put("clock", if (realTime) "Android Choreographer lifecycle Recomposer; uptimeMillis real-time public MotionEvent replay; no mainClock advances during injection"
                else "Compose mainClock controlled in 16ms steps; declared_injected_ms is requested synthetic event time, not measured native touch delivery")
            .put("wall_clock", "elapsedRealtimeNanos includes PixelCopy waits; it is not the animation timeline")
            .put("tint_srgb", JSONArray(listOf(0.0, .65, .75, 1.0)))
            .put("neutral_srgb", JSONArray(listOf(.94, .94, .96, 1.0)))
            .put("selection_tokens", "Material primary and secondaryContainer use reference tint; onSecondaryContainer=#123239; product selectionColors unchanged")
            .put("ios_color_note", "UIKit UISwitch explicitly sets onTintColor; SwiftUI sets .tint at NativeStage root. Rendered iOS output must confirm effective colors. Pass iOS session backgrounds.stripeResolvedRGBA as glassStripeRGBA for exact resolved stripe inputs.")
            .put("stripe_palette_source", stripePaletteSource)
            .put("stripe_srgb", JSONArray(stripeColors.map { JSONArray(listOf(it.red, it.green, it.blue, 1f)) }))
            .put("stripe_srgb_hex", JSONArray(listOf("FF3B30", "FF9500", "FFCC00", "34C759", "30B0C7", "007AFF", "AF52DE")))
            .put("backgrounds", JSONArray(backgrounds.map { it.name })).put("controls", JSONArray(controls.map { it.name }))
            .put("grid", "20dp pitch, black alpha .13, .5dp stroke; origin is Compose content origin")
            .put("stripes", "28dp width, alpha .52 over neutral; origin is Compose content origin")
            .put("checker", "20dp black/white cells; diagnostic addition, absent from iOS main.swift")
            .put("layout", "One product control centered per case. Segment/slider requested width335dp; Dock outer width316dp. Actual bounds exported in CSV; no absolute height assertion.")
            .put("capture", "Wait HWUI registerFrameCommitCallback, then PixelCopy window; crop adds40dp. Dock lens geometry uses DockLensBoundsInRootPx custom semantics from its real shader carrier, with the actual painted capsule mapped through parent swell/pull to root pixels; it excludes shadow. The carrier node bounds are never used as the Dock lens bounds.")
            .put("capture_scenarios", "hold-drag uses the initial selected lens. direct-b-hold recreates fixture state so A is selected, then holds the opposite Switch endpoint, middle Segment/Dock item, or Slider .75 track point without move. down/up frames advance no animation time; rise/hold/release frames use declared Compose-clock steps.")
            .put("selection_observations", "inputs.csv product_onValueChange rows observe the real product callback invocation before the fixture assigns state, with callback argument plus current Compose clock and elapsed wall time. declared_injected_ms is the current requested event timeline, not measured pointer delivery. injection_return_snapshot value can precede recomposition; frames.csv value is read after idle. Callback rows have no inferred pointer location or requested delta.")
        File(directory, "session.json").writeText(metadata.toString(2))
    }
}
