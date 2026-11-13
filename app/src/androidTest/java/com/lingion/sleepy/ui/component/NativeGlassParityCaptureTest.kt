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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import com.lingion.sleepy.ui.component.liquidglass.GlassDropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberBottomSheetState
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.ui.component.liquidglass.GlassBackground
import com.lingion.sleepy.ui.component.liquidglass.DockLensBoundsInRootPx
import com.lingion.sleepy.ui.component.liquidglass.LocalDockInputTimeNanos
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.component.liquidglass.GlassButton
import com.lingion.sleepy.ui.component.liquidglass.GlassAlertDialog
import com.lingion.sleepy.ui.component.liquidglass.GlassDropdownMenu
import com.lingion.sleepy.ui.component.liquidglass.GlassIconButton
import com.lingion.sleepy.ui.component.liquidglass.GlassModalBottomSheet
import com.lingion.sleepy.ui.component.liquidglass.GlassSlider
import com.lingion.sleepy.ui.component.liquidglass.GlassSwitch
import com.lingion.sleepy.ui.component.liquidglass.GlassTopAppBar
import com.lingion.sleepy.ui.component.liquidglass.GlassTextButton
import com.lingion.sleepy.ui.component.liquidglass.LiquidGlassNavigationBar
import com.lingion.sleepy.ui.component.liquidglass.LocalGlassDepth
import com.lingion.sleepy.ui.component.liquidglass.LocalGlassHost
import com.lingion.sleepy.ui.theme.LocalDarkTheme
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.ui.theme.ThemePresets
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
    private var opticalFontScale = 1f
    private var opticalSystemFontScale = 1f
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

    private enum class OpticalSurface {
        home, share_sheet, edit_sheet, menu, alert;

        val heldTags get() = when (this) {
            home -> listOf("static-icon-share", "static-switch-lens", "segment-thumb", "glass-slider-end", "dock-lens")
            share_sheet, edit_sheet -> listOf("static-sheet-icon", "static-switch-lens", "segment-thumb", "glass-slider-end", "static-button")
            menu -> listOf("static-menu-delete", "static-menu-second", "static-menu-first")
            alert -> listOf("static-alert-confirm", "static-alert-cancel")
        }
        val presentation get() = this == menu || this == alert
        val triggerTag get() = "static-${name}-anchor"
        val carrierTag get() = "static-${name}-carrier"
    }
    private data class OpticalScene(val background: OpticalBackground, val dark: Boolean,
                                    val surface: OpticalSurface = OpticalSurface.home,
                                    val showControls: Boolean = true) {
        val name get() = "${background.name}-${if (dark) "dark" else "light"}-${surface.name}"
    }
    private data class OpticalContentDensityObservation(
        val scene: OpticalScene, val slot: String, val density: Float, val fontScale: Float,
        val view: View, val root: View, val uptimeMs: Long,
    ) {
        fun json() = JSONObject().put("scene", scene.name).put("surface", scene.surface.name)
            .put("slot", slot).put("density", density).put("font_scale", fontScale)
            .put("view_class", view.javaClass.name).put("view_identity", System.identityHashCode(view))
            .put("root_class", root.javaClass.name).put("root_identity", System.identityHashCode(root))
            .put("wall_uptime_ms", uptimeMs)
    }
    private var opticalActivity: OpticalCaptureWindow? = null
    private var opticalDialog: OpticalCaptureWindow? = null
    private val opticalContentDensityObservations = ConcurrentLinkedQueue<OpticalContentDensityObservation>()
    private val opticalPresentationEvents = ArrayList<String>()
    private val opticalPresentationTrace = ConcurrentLinkedQueue<JSONObject>()
    private var opticalPresentationTextColors: Map<String, Int> = emptyMap()

    /** Static optics are independent of the synthetic gesture/replay timelines below. */
    @Test fun captureStaticOpticalBackgroundMatrix() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Pass glassStaticCapture=true for the multi-background GPU matrix",
            arguments.getString("glassStaticCapture") == "true")
        fun selected(name: String, allowed: List<String>, defaults: List<String> = allowed): List<String> {
            val values = arguments.getString(name)?.split(',')?.map(String::trim)?.distinct() ?: defaults
            require(values.isNotEmpty() && values.all { it in allowed }) { "$name must select from $allowed" }
            return values
        }
        val backgrounds = selected("glassStaticBackgrounds", OpticalBackground.entries.map { it.name })
            .map(OpticalBackground::valueOf)
        val themes = selected("glassStaticThemes", listOf("light", "dark")).map { it == "dark" }
        val surfaces = selected("glassStaticSurfaces", OpticalSurface.entries.map { it.name },
            listOf("home", "share_sheet", "edit_sheet")).map(OpticalSurface::valueOf)
        assumeTrue("menu captures require actual attached-popup PixelCopy, available on API 34+",
            OpticalSurface.menu !in surfaces || Build.VERSION.SDK_INT >= 34)
        val states = selected("glassStaticStates", listOf("rest", "held"))
        val runId = (arguments.getString("glassStaticRunId") ?: System.currentTimeMillis().toString())
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "glass-optical-backgrounds/$runId")
        assertTrue("Create static optical output directory", directory.isDirectory || directory.mkdirs())
        val captures = JSONArray()
        val baselines = JSONArray()
        val presentationActions = JSONArray()
        val manifest = JSONObject().put("schema", 1).put("run_id", runId)
            .put("sdk", Build.VERSION.SDK_INT).put("model", Build.MODEL).put("fingerprint", Build.FINGERPRINT)
            .put("inputs", JSONArray(OpticalBackground.entries.map { it.metadata() }))
            .put("theme_preset", ThemePresets.KEY_DEFAULT)
            .put("matrix", "Selected fixed inputs × themes × carriers; optional menu/alert use actual public presentation controls, with rest and each named held control")
            .put("selected_backgrounds", JSONArray(backgrounds.map { it.name }))
            .put("selected_themes", JSONArray(themes.map { if (it) "dark" else "light" }))
            .put("selected_surfaces", JSONArray(surfaces.map { it.name })).put("selected_states", JSONArray(states))
            .put("fixture_scope", "Actual product GlassTopAppBar/buttons/Dock/common controls, GlassModalBottomSheet share/edit carriers, and opt-in GlassDropdownMenu/GlassAlertDialog; not a full screen data-flow test")
            .put("capture_sources", "PixelCopy of actual Activity/Dialog Window or API34+ attached Popup ViewRoot after HWUI commit, plus UiAutomation composited-display screenshot; both are saved separately")
            .put("content_density_policy", "Fixture-only fontScale1 is explicitly provided inside actual Popup content, each Alert slot and Sheet content; SideEffect reads are asserted against the captured root and exported per capture")
            .put("baseline_policy", "Plain background before any optical controls. Home window metrics use same-window baseline. Popup/Dialog surface metrics have no comparable surface baseline; display metrics compare the same full display background at measured screen coordinates, including any product scrim.")
            .put("acceptance", "No single background or scalar metric proves parity. Review every input, theme and carrier. Center/edge RGB, luminance, saturation and channel gradients are descriptive, not inferred opacity/dispersion or pass thresholds.")
            .put("clock", "Compose mainClock static snapshots; presentation taps wait for recomposition, advance one frame to start popup animation, then settle640ms. Held after640ms without movement; not natural-time motion evidence or a product-fix assertion.")
            .put("presentation_fixture", "Menu anchor [126.5,411.5,122.5,33.5]dp and public offset [-63.5,-146]dp align its top-left with native [63,299]pt on the larger Android display. Liquid Glass uses measured 42dp rows plus 10dp top/bottom insets; Material Design retains M3 dimensions. Content follows observed native above-anchor display order Delete/Second action/First action. Alert title/message/actions use the measured native layout. Actual root/screen rectangles are exported; backing controls and font rasterization differ from iOS.")
            .put("baselines", baselines).put("captures", captures).put("presentation_actions", presentationActions)
        val scene = mutableStateOf(OpticalScene(OpticalBackground.fine_grid, false, showControls = false))
        compose.setContent {
            val systemDensity = LocalDensity.current
            SideEffect { opticalSystemFontScale = systemDensity.fontScale }
            // The native optical reference uses its default content-size category.
            // Normalize only this measuring fixture, preserving the user's setting.
            CompositionLocalProvider(
                LocalDensity provides Density(systemDensity.density, fontScale = 1f),
                LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L },
            ) {
                OpticalFixture(scene.value)
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        try {
            for (dark in themes) for (background in backgrounds) {
                val plain = OpticalScene(background, dark, showControls = false)
                compose.runOnIdle { scene.value = plain }
                settleOptical()
                val activity = checkNotNull(opticalActivity)
                assertTrue("Static optics require hardware acceleration", activity.root.isHardwareAccelerated)
                val baselineWindow = activity.capture()
                val baselineName = "${background.name}-${if (dark) "dark" else "light"}-background"
                baselineWindow.saveOpticalPng(File(directory, "$baselineName-window.png"))
                val readyScreen = try {
                    awaitOpticalBackgroundScreen(activity, baselineWindow, density, directory, baselineName)
                } catch (failure: Throwable) {
                    baselineWindow.recycle()
                    throw failure
                }
                val baselineScreen = readyScreen.bitmap
                try {
                    baselineScreen.saveOpticalPng(File(directory, "$baselineName-screen.png"))
                    baselines.put(JSONObject().put("background", background.name).put("dark", dark)
                        .put("window_png", "$baselineName-window.png").put("screen_png", "$baselineName-screen.png")
                        .put("display_readiness", readyScreen.verification))
                    for (surface in surfaces) {
                        val current = OpticalScene(background, dark, surface)
                        compose.runOnIdle { opticalPresentationEvents.clear(); scene.value = current }
                        settleOptical()
                        if (surface.presentation) tapOptical(surface.triggerTag)
                        if ("rest" in states) captureOptical(current, "rest", directory, captures, baselineName, baselineWindow, baselineScreen)
                        for (tag in if ("held" in states) surface.heldTags else emptyList()) {
                            val node = compose.onNodeWithTag(tag, useUnmergedTree = true)
                            val press = if (tag == "dock-lens") {
                                node.fetchSemanticsNode().config[DockLensBoundsInRootPx].center - bounds(node).topLeft
                            } else bounds(node).let { Offset(it.width / 2, it.height / 2) }
                            node.performTouchInput { down(press) }
                            try {
                                settleOptical()
                                captureOptical(current, "held-$tag", directory, captures, baselineName, baselineWindow, baselineScreen)
                            } finally {
                                node.performTouchInput { cancel() }
                                settleOptical()
                            }
                        }
                        if (surface.presentation) verifyOpticalPresentationActions(current, presentationActions)
                        File(directory, "matrix.json").writeText(manifest.toString(2))
                    }
                } finally { baselineWindow.recycle(); baselineScreen.recycle() }
            }
            val statesPerScene = surfaces.sumOf { (if ("rest" in states) 1 else 0) +
                (if ("held" in states) it.heldTags.size else 0) }
            assertEquals("Every selected carrier/theme/background/state is captured",
                backgrounds.size * themes.size * statesPerScene, captures.length())
        } finally {
            compose.mainClock.autoAdvance = true
            File(directory, "matrix.json").writeText(manifest.put("density", density)
                .put("font_scale", opticalFontScale).put("system_font_scale", opticalSystemFontScale)
                .put("content_density_observations", JSONArray(opticalContentDensityObservations.map { it.json() }))
                .put("presentation_trace", JSONArray(opticalPresentationTrace.toList())).toString(2))
            writeOpticalMetricsCsv(File(directory, "optical-metrics.csv"), captures)
        }
    }

    private fun settleOptical() {
        compose.mainClock.advanceTimeBy(640)
        compose.waitForIdle()
    }

    private fun tapOptical(tag: String) {
        val rect = bounds(compose.onNodeWithTag(tag, useUnmergedTree = true))
        opticalPresentationTrace.add(JSONObject().put("event", "tap_requested").put("tag", tag)
            .put("compose_ms", compose.mainClock.currentTime).put("wall_uptime_ms", SystemClock.uptimeMillis())
            .put("node_root_rect_px", JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom))))
        compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput {
            down(center)
            advanceEventTime(70)
            up()
        }
        // A newly composed Popup owns a new animation. Let its composition and first
        // frame exist before advancing that animation's clock; Activity-only settling
        // can otherwise snapshot the Popup's initial alpha0/scale.8 frame.
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        settleOptical()
        compose.runOnIdle {
            opticalPresentationTrace.add(JSONObject().put("event", "tap_settled").put("tag", tag)
                .put("compose_ms", compose.mainClock.currentTime).put("wall_uptime_ms", SystemClock.uptimeMillis())
                .put("callbacks", JSONArray(opticalPresentationEvents)))
        }
    }

    /** Observe real test-delivered events without consuming or replacing the product handler. */
    private fun Modifier.observeOpticalPointer(tag: String): Modifier = pointerInput(tag) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                opticalPresentationTrace.add(JSONObject().put("event", "pointer_delivered").put("tag", tag)
                    .put("type", event.type.toString()).put("wall_uptime_ms", SystemClock.uptimeMillis())
                    .put("changes", JSONArray(event.changes.map { change ->
                        JSONObject().put("event_uptime_ms", change.uptimeMillis).put("pressed", change.pressed)
                            .put("previous_pressed", change.previousPressed).put("consumed_at_initial", change.isConsumed)
                            .put("local_px", JSONArray(listOf(change.position.x, change.position.y)))
                    })))
            }
        }
    }

    /** Exercise the same public open/action/dismiss path used for the captured carrier. */
    private fun verifyOpticalPresentationActions(scene: OpticalScene, records: JSONArray) {
        val actions = if (scene.surface == OpticalSurface.menu) listOf("first", "second", "delete")
            else listOf("confirm", "cancel")
        for ((index, action) in actions.withIndex()) {
            if (index > 0) tapOptical(scene.surface.triggerTag)
            assertEquals("${scene.name}: visible presentation before $action", 1,
                compose.onAllNodes(hasTestTag(scene.surface.carrierTag), useUnmergedTree = true).fetchSemanticsNodes().size)
            tapOptical("static-${scene.surface.name}-$action")
            assertEquals("${scene.name}: $action dismisses presentation", 0,
                compose.onAllNodes(hasTestTag(scene.surface.carrierTag), useUnmergedTree = true).fetchSemanticsNodes().size)
        }
        val observed = compose.runOnIdle { opticalPresentationEvents.toList() }
        assertEquals("${scene.name}: every public action emits one callback",
            actions.flatMap { listOf("opened", it) }, observed)
        records.put(JSONObject().put("scene", scene.name).put("events", JSONArray(observed))
            .put("verified", "public anchor DOWN/UP, visible carrier, public action DOWN/UP, carrier absent, one callback"))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun OpticalFixture(scene: OpticalScene) {
        val view = LocalView.current
        val currentDensity = LocalDensity.current
        SideEffect {
            opticalActivity = OpticalCaptureWindow.from(view)
            density = currentDensity.density
            opticalFontScale = currentDensity.fontScale
            if (!scene.showControls || scene.surface == OpticalSurface.home) opticalDialog = null
        }
        SleepyThemeProvider(darkTheme = scene.dark, themeKey = ThemePresets.KEY_DEFAULT) {
            CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                val colors = MaterialTheme.colorScheme
                GlassHost(Modifier.fillMaxSize(), background = colors.background) {
                    GlassBackground(0f, Modifier.fillMaxSize()) { OpticalCaptureBackground(scene.background) }
                    if (scene.showControls) key(scene.background, scene.dark, scene.surface) {
                        if (scene.surface == OpticalSurface.home) {
                            Box(Modifier.fillMaxSize()) {
                                GlassTopAppBar(title = { Text("Optical background matrix") }, windowInsets = WindowInsets(0),
                                    modifier = Modifier.align(Alignment.TopCenter).testTag("static-toolbar"),
                                    actions = {
                                        GlassIconButton({}, Modifier.testTag("static-icon-share")) { Icon(Icons.Filled.Share, "Share") }
                                        GlassIconButton({}, Modifier.testTag("static-icon-edit")) { Icon(Icons.Filled.Edit, "Edit") }
                                    })
                                Column(Modifier.align(Alignment.Center).widthIn(max = 335.dp).fillMaxWidth().padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)) { OpticalCommonControls() }
                                var selection by remember { mutableIntStateOf(0) }
                                val host = LocalGlassHost.current
                                LiquidGlassNavigationBar(
                                    listOf(PillNavItemSpec(Icons.Filled.Home, "Home"), PillNavItemSpec(Icons.Filled.Search, "Explore"),
                                        PillNavItemSpec(Icons.Filled.Settings, "Settings")), selection, { selection = it },
                                    Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp).width(316.dp).testTag("static-dock"),
                                    host?.backdropAt(LocalGlassDepth.current),
                                )
                            }
                        } else if (scene.surface.presentation) {
                            OpticalPresentation(scene, currentDensity)
                        } else {
                            val state = rememberBottomSheetState(initialValue = SheetValue.Expanded,
                                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
                            GlassModalBottomSheet(onDismissRequest = {}, sheetState = state,
                                modifier = Modifier.testTag("static-sheet-carrier")) {
                                OpticalMeasuredContent(scene, "sheet-content", currentDensity) {
                                    val dialogView = LocalView.current
                                    SideEffect { opticalDialog = OpticalCaptureWindow.from(dialogView) }
                                    Column(Modifier.fillMaxWidth().height(420.dp).padding(24.dp).testTag("static-sheet-content"),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically) {
                                            Text(if (scene.surface == OpticalSurface.share_sheet) "Share schedule" else "Adjust course",
                                                style = MaterialTheme.typography.titleLarge)
                                            GlassIconButton({}, Modifier.testTag("static-sheet-icon")) {
                                                Icon(if (scene.surface == OpticalSurface.share_sheet) Icons.Filled.Share else Icons.Filled.Edit, "Action")
                                            }
                                        }
                                        OpticalCommonControls()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable private fun OpticalPresentation(scene: OpticalScene, measuringDensity: Density) {
        val surface = scene.surface
        var visible by remember { mutableStateOf(false) }
        val colors = MaterialTheme.colorScheme
        SideEffect {
            opticalPresentationTextColors = mapOf(
                "static-menu-delete" to colors.error.toArgb(),
                "static-menu-second" to colors.onSurface.toArgb(), "static-menu-first" to colors.onSurface.toArgb(),
                "static-alert-confirm" to colors.primary.toArgb(), "static-alert-cancel" to colors.primary.toArgb())
        }
        fun dismiss(action: String) {
            opticalPresentationEvents.add(action)
            opticalPresentationTrace.add(JSONObject().put("event", "callback").put("action", action)
                .put("wall_uptime_ms", SystemClock.uptimeMillis()))
            visible = false
        }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.offset(x = 126.5.dp, y = 411.5.dp).width(122.5.dp)) {
                GlassButton({ opticalPresentationEvents.add("opened"); visible = true },
                    Modifier.fillMaxWidth().height(33.5.dp).testTag(surface.triggerTag)
                        .observeOpticalPointer(surface.triggerTag)) {
                    Text(if (surface == OpticalSurface.menu) "System menu" else "System alert")
                }
                if (surface == OpticalSurface.menu) {
                    GlassDropdownMenu(visible, { dismiss("dismissed") },
                        modifier = Modifier.width(250.dp).testTag(surface.carrierTag),
                        offset = DpOffset((-63.5).dp, (-146).dp)) {
                        OpticalMeasuredContent(scene, "menu-content", measuringDensity) {
                            val popupView = LocalView.current
                            SideEffect { opticalDialog = OpticalCaptureWindow.fromPopup(popupView) }
                            for ((action, label) in listOf("delete" to "Delete", "second" to "Second action", "first" to "First action")) {
                                GlassDropdownMenuItem(text = { Text(label,
                                    color = if (action == "delete") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
                                    onClick = { dismiss(action) }, modifier = Modifier.testTag("static-menu-$action")
                                        .observeOpticalPointer("static-menu-$action"))
                            }
                        }
                    }
                }
            }
            if (surface == OpticalSurface.alert && visible) {
                GlassAlertDialog({ dismiss("dismissed") },
                    modifier = Modifier.testTag(surface.carrierTag),
                    title = {
                        OpticalMeasuredContent(scene, "alert-title", measuringDensity) {
                            Text("Native Alert", Modifier.testTag("static-alert-title"))
                        }
                    },
                    text = {
                        OpticalMeasuredContent(scene, "alert-body", measuringDensity) {
                            Text("System-provided alert, controls, material, and transition.", Modifier.testTag("static-alert-body"))
                        }
                    },
                    confirmButton = {
                        OpticalMeasuredContent(scene, "alert-confirm", measuringDensity) {
                            val dialogView = LocalView.current
                            SideEffect { opticalDialog = OpticalCaptureWindow.from(dialogView) }
                            GlassTextButton({ dismiss("confirm") }, Modifier.testTag("static-alert-confirm")
                                .observeOpticalPointer("static-alert-confirm")) { Text("Confirm") }
                        }
                    },
                    dismissButton = {
                        OpticalMeasuredContent(scene, "alert-cancel", measuringDensity) {
                            GlassTextButton({ dismiss("cancel") }, Modifier.testTag("static-alert-cancel")
                                .observeOpticalPointer("static-alert-cancel")) { Text("Cancel") }
                        }
                    })
            }
        }
    }

    /** New Android windows provide density again; normalize inside their actual content. */
    @Composable private fun OpticalMeasuredContent(
        scene: OpticalScene, slot: String, measuringDensity: Density, content: @Composable () -> Unit,
    ) {
        // No layout node is added, preserving action-row parent data and product geometry.
        CompositionLocalProvider(LocalDensity provides measuringDensity) {
            val actualDensity = LocalDensity.current
            val actualView = LocalView.current
            SideEffect {
                opticalContentDensityObservations.add(OpticalContentDensityObservation(
                    scene, slot, actualDensity.density, actualDensity.fontScale,
                    actualView, actualView.rootView, SystemClock.uptimeMillis(),
                ))
            }
            content()
        }
    }

    private fun verifiedOpticalContentDensity(scene: OpticalScene, surface: OpticalCaptureWindow): JSONArray =
        compose.runOnIdle {
            val requiredSlots = when (scene.surface) {
                OpticalSurface.home -> emptyList()
                OpticalSurface.menu -> listOf("menu-content")
                OpticalSurface.alert -> listOf("alert-title", "alert-body", "alert-confirm", "alert-cancel")
                OpticalSurface.share_sheet, OpticalSurface.edit_sheet -> listOf("sheet-content")
            }
            val actual = opticalContentDensityObservations.filter { it.scene == scene }.associateBy { it.slot }
            JSONArray(requiredSlots.map { slot ->
                val observed = checkNotNull(actual[slot]) { "${scene.name}: missing actual $slot density observation" }
                assertTrue("${scene.name}: $slot belongs to captured surface", observed.root === surface.root)
                assertTrue("${scene.name}: $slot is attached", observed.view.isAttachedToWindow)
                assertEquals("${scene.name}: $slot actual font scale", 1f, observed.fontScale, 0f)
                assertEquals("${scene.name}: $slot actual density", density, observed.density, .0001f)
                observed.json()
            })
        }

    @Composable private fun OpticalCommonControls() {
        var checked by remember { mutableStateOf(true) }
        var selected by remember { mutableIntStateOf(0) }
        var value by remember { mutableFloatStateOf(.35f) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Include details", color = MaterialTheme.colorScheme.onSurface)
            GlassSwitch(checked, { checked = it }, Modifier.testTag("static-switch"),
                thumbContent = { Box(Modifier.fillMaxSize().testTag("static-switch-lens")) })
        }
        SegmentedSwitcher(listOf(0 to "First", 1 to "Second", 2 to "Third"), selected, { selected = it },
            Modifier.fillMaxWidth().testTag("static-segments"))
        GlassSlider(value, { value = it }, Modifier.fillMaxWidth().testTag("static-slider"))
        GlassButton({}, Modifier.fillMaxWidth().testTag("static-button")) { Text("Continue") }
    }

    /** Require material and text in the real GPU surface, rather than accepting semantics alone. */
    private fun verifyOpticalPresentationGpu(scene: OpticalScene, phase: String, bitmap: Bitmap,
                                             origin: IntArray): JSONArray {
        val observations = JSONArray()
        fun windowRect(tag: String) = bounds(compose.onNodeWithTag(tag, useUnmergedTree = true))
            .translate(Offset(origin[0].toFloat(), origin[1].toFloat()))
        fun inspect(tag: String, region: Rect, expectedText: Int? = null) {
            val left = floor(region.left).toInt(); val top = floor(region.top).toInt()
            val right = ceil(region.right).toInt(); val bottom = ceil(region.bottom).toInt()
            assertTrue("${scene.name}: $tag GPU ROI lies inside actual surface",
                left >= 0 && top >= 0 && right <= bitmap.width && bottom <= bitmap.height && right > left && bottom > top)
            var visible = 0; var textPixels = 0
            for (y in top until bottom) for (x in left until right) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = (pixel ushr 24) and 255
                if (alpha >= 32) visible++
                if (expectedText != null && alpha >= 192 && (0..2).all { channel ->
                    kotlin.math.abs(((pixel ushr (channel * 8)) and 255) -
                        ((expectedText ushr (channel * 8)) and 255)) <= 18
                }) textPixels++
            }
            val pixels = (right - left) * (bottom - top)
            val row = JSONObject().put("tag", tag).put("region_px", JSONArray(listOf(left, top, right, bottom)))
                .put("samples", pixels).put("alpha_ge32_pixels", visible)
                .put("expected_text_rgb", expectedText?.let { JSONArray(listOf((it ushr 16) and 255,
                    (it ushr 8) and 255, it and 255)) } ?: JSONObject.NULL)
                .put("expected_text_core_pixels", textPixels)
            observations.put(row)
            opticalPresentationTrace.add(JSONObject().put("event", "gpu_visibility").put("scene", scene.name)
                .put("phase", phase).put("observation", row))
            if (expectedText == null) assertTrue("${scene.name}: real GPU $tag material must cover its clear ROI",
                visible >= pixels * .95)
            else assertTrue("${scene.name}: real GPU $tag must contain visible label foreground", textPixels >= 4)
        }
        val carrier = windowRect(scene.surface.carrierTag)
        if (scene.surface == OpticalSurface.menu) {
            for (tag in scene.surface.heldTags) {
                val row = windowRect(tag)
                inspect("$tag-material", Rect(row.right - 48 * density, row.center.y - 8 * density,
                    row.right - 30 * density, row.center.y + 8 * density))
            }
        } else {
            inspect("static-alert-material", Rect(carrier.left + 16 * density, carrier.top + 40 * density,
                carrier.left + 34 * density, carrier.top + 56 * density))
        }
        if (phase == "rest") for (tag in scene.surface.heldTags) {
            val row = windowRect(tag)
            // Text core colors are resolved from this fixture's actual Material theme.
            inspect("$tag-foreground", Rect(row.left.coerceAtLeast(0f), row.top.coerceAtLeast(0f),
                row.right.coerceAtMost(bitmap.width.toFloat()), row.bottom.coerceAtMost(bitmap.height.toFloat())),
                checkNotNull(opticalPresentationTextColors[tag]))
        }
        return observations
    }

    private fun captureOptical(scene: OpticalScene, phase: String, directory: File, captures: JSONArray,
                               baselineName: String, baselineWindow: Bitmap, baselineScreen: Bitmap) {
        compose.waitForIdle()
        val surface = if (scene.surface == OpticalSurface.home) checkNotNull(opticalActivity) else checkNotNull(opticalDialog)
        val contentDensity = verifiedOpticalContentDensity(scene, surface)
        val origin = surface.origin()
        val screenOrigin = surface.screenOrigin()
        val windowBitmap = surface.capture()
        val stem = "${scene.name}-$phase"
        val readyScreen = try {
            if (scene.surface.presentation) awaitOpticalSurfaceScreen(surface, windowBitmap, density, directory, stem)
            else OpticalBackgroundScreen(opticalScreenCapture(), JSONObject().put("policy", "Legacy non-presentation capture; no per-surface display convergence checked"))
        } catch (failure: Throwable) {
            windowBitmap.saveOpticalPng(File(directory, "$stem-window.png"))
            windowBitmap.recycle()
            throw failure
        }
        val screenBitmap = readyScreen.bitmap
        try {
            windowBitmap.saveOpticalPng(File(directory, "$stem-window.png"))
            screenBitmap.saveOpticalPng(File(directory, "$stem-screen.png"))
            val gpuVisibility = if (scene.surface.presentation)
                verifyOpticalPresentationGpu(scene, phase, windowBitmap, origin) else JSONArray()
            val measurements = JSONArray()
            val tags = if (scene.surface.presentation) listOf(scene.surface.carrierTag) + scene.surface.heldTags +
                (if (scene.surface == OpticalSurface.alert) listOf("static-alert-title", "static-alert-body") else emptyList())
            else (if (scene.surface == OpticalSurface.home) listOf(
                "static-toolbar", "static-icon-share", "static-icon-edit", "static-dock", "dock-lens",
            ) else listOf("static-sheet-carrier", "static-sheet-content", "static-sheet-icon")) +
                listOf("static-switch", "static-switch-lens", "static-segments", "segment-thumb",
                    "static-slider", "glass-slider-end", "static-button")
            for (tag in tags) {
                val nodes = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true)
                assertEquals("${scene.name}: one $tag", 1, nodes.fetchSemanticsNodes().size)
                val node = nodes[0]
                val rect = if (tag == "dock-lens") node.fetchSemanticsNode().config[DockLensBoundsInRootPx] else bounds(node)
                val localWindowRect = rect.translate(Offset(origin[0].toFloat(), origin[1].toFloat()))
                val screenRect = rect.translate(Offset(screenOrigin[0].toFloat(), screenOrigin[1].toFloat()))
                measurements.put(JSONObject().put("tag", tag)
                    .put("geometry_source", if (tag == "dock-lens") "product painted capsule mapped to root" else "unclipped semantics layout bounds")
                    .put("window", opticalRegionMetrics(windowBitmap, if (scene.surface == OpticalSurface.home) baselineWindow else null, localWindowRect))
                    .put("composited_display", opticalRegionMetrics(screenBitmap, baselineScreen, screenRect)))
            }
            val anchorRect = if (scene.surface.presentation) {
                val activityOrigin = checkNotNull(opticalActivity).screenOrigin()
                bounds(compose.onNodeWithTag(scene.surface.triggerTag, useUnmergedTree = true))
                    .translate(Offset(activityOrigin[0].toFloat(), activityOrigin[1].toFloat()))
            } else null
            captures.put(JSONObject().put("scene", scene.name).put("background", scene.background.name)
                .put("theme", if (scene.dark) "dark" else "light").put("carrier", scene.surface.name).put("phase", phase)
                .put("window_source", when (scene.surface) {
                    OpticalSurface.home -> "activity"
                    OpticalSurface.menu -> "attached-popup-viewroot-api34"
                    else -> "dialog"
                })
                .put("window_png", "$stem-window.png").put("screen_png", "$stem-screen.png")
                .put("window_size_px", JSONArray(listOf(windowBitmap.width, windowBitmap.height)))
                .put("display_readiness", readyScreen.verification)
                .put("content_density_observations", contentDensity)
                .put("gpu_visibility", gpuVisibility)
                .put("presentation_anchor_screen_rect_px", anchorRect?.let {
                    JSONArray(listOf(it.left, it.top, it.right, it.bottom))
                } ?: JSONObject.NULL)
                .put("window_baseline_png", if (scene.surface == OpticalSurface.home) "$baselineName-window.png" else JSONObject.NULL)
                .put("display_baseline_png", "$baselineName-screen.png")
                .put("content_origin_in_window_px", JSONArray(origin.toList()))
                .put("content_origin_on_screen_px", JSONArray(screenOrigin.toList())).put("measurements", measurements))
        } finally { windowBitmap.recycle(); screenBitmap.recycle() }
    }

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
        compose.setContent {
            CompositionLocalProvider(LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L }) {
                ReferenceFixture(scene.value)
            }
        }
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
