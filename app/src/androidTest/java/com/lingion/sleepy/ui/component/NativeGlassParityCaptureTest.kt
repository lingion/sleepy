package com.lingion.sleepy.ui.component

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import com.lingion.sleepy.ui.component.liquidglass.GlassMaterialScope
import com.lingion.sleepy.ui.component.liquidglass.LocalGlassMaterialBackdrop
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
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.IdentityHashMap
import kotlin.math.abs
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
    private data class OpticalScene(val background: OpticalBackground?, val dark: Boolean,
                                    val surface: OpticalSurface = OpticalSurface.home,
                                    val showControls: Boolean = true,
                                    val externalInputId: String? = null) {
        init { require((background != null) != (externalInputId != null)) }
        val backgroundId get() = externalInputId ?: checkNotNull(background).name
        val inputName get() = "$backgroundId-${if (dark) "dark" else "light"}"
        val name get() = "$inputName-${surface.name}"
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
    private var opticalAlertPane: GlassMaterialScope? = null
    private var opticalNativeCanvas = false
    private var opticalBackgroundCoordinates: LayoutCoordinates? = null
    private var opticalBackgroundHostCoordinates: LayoutCoordinates? = null
    private var opticalCurrentScene: OpticalScene? = null
    private data class OpticalAlertLayout(val pane: OpticalLayoutGeometry, val drawingSpace: OpticalLayoutGeometry,
                                         val paneOriginInDrawingSpace: Offset) {
        fun json() = JSONObject().put("pane", pane.json()).put("drawing_space_parent", drawingSpace.json())
            .put("pane_origin_in_drawing_space_px", JSONArray(listOf(paneOriginInDrawingSpace.x, paneOriginInDrawingSpace.y)))
            .put("drawing_space_source", "actual pane LayoutCoordinates.parentLayoutCoordinates, the product NativeGlassAlertDrawingSpace")
    }
    private data class OpticalNativeAlertAlignment(val placement: OpticalWindowPlacement, val report: JSONObject)
    private val opticalNativeAlertAlignments = IdentityHashMap<Window, OpticalNativeAlertAlignment>()
    private val opticalNativeAlertAlignmentReports = ArrayList<JSONObject>()
    private val opticalContentDensityObservations = ConcurrentLinkedQueue<OpticalContentDensityObservation>()
    private val opticalPresentationEvents = ArrayList<String>()
    private val opticalPresentationTrace = ConcurrentLinkedQueue<JSONObject>()
    private var opticalPresentationTextColors: Map<String, Int> = emptyMap()
    private data class StaticNativeInput(val bitmap: Bitmap, val metadata: JSONObject)
    private var opticalNativeInputs: Map<String, StaticNativeInput> = emptyMap()
    private var opticalSheetPane: GlassMaterialScope? = null
    private var opticalSheetContentHeightDp = 420f
    private var opticalStaticPlainAlertLifecycle = false
    private val opticalLifecycleContentObservations = ConcurrentLinkedQueue<JSONObject>()

    /** Static optics are independent of the synthetic gesture/replay timelines below. */
    @Test fun captureStaticOpticalBackgroundMatrix() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Pass glassStaticCapture=true for the multi-background GPU matrix",
            arguments.getString("glassStaticCapture") == "true")
        val lifecycleArgument = arguments.getString("glassStaticPlainAlertLifecycle") ?: "false"
        require(lifecycleArgument in listOf("true", "false")) { "glassStaticPlainAlertLifecycle must be true or false" }
        opticalStaticPlainAlertLifecycle = lifecycleArgument == "true"
        val nativeInputDirectory = arguments.getString("glassStaticNativeInputDirectory")
        val externalInputDirectory = arguments.getString("glassStaticExternalInputsDirectory")
        require(nativeInputDirectory == null || externalInputDirectory == null) {
            "Select either original native inputs or a preregistered external input set"
        }
        val externalManifest = externalInputDirectory?.let(::loadStaticExternalManifest)
        val externalRecords = externalManifest?.getJSONArray("inputs")?.let { inputs ->
            (0 until inputs.length()).map { inputs.getJSONObject(it) }
        }
        val inputDirectory = externalInputDirectory ?: nativeInputDirectory
        val sheetContentHeightArgument = arguments.getString("glassStaticSheetContentHeightDp")
        opticalSheetContentHeightDp = sheetContentHeightArgument?.toFloatOrNull() ?: run {
            require(sheetContentHeightArgument == null) { "glassStaticSheetContentHeightDp must be a finite number in48..600" }
            420f
        }
        require(opticalSheetContentHeightDp.isFinite() && opticalSheetContentHeightDp in 48f..600f) {
            "glassStaticSheetContentHeightDp must be a finite number in48..600"
        }
        val nativeCanvasArgument = arguments.getString("glassStaticNativeCanvas")
            ?: if (inputDirectory == null) "false" else "true"
        require(nativeCanvasArgument in listOf("true", "false")) { "glassStaticNativeCanvas must be true or false" }
        opticalNativeCanvas = nativeCanvasArgument == "true"
        require(inputDirectory == null || opticalNativeCanvas) {
            "External PNG inputs require the fixed375x667dp world; do not set glassStaticNativeCanvas=false"
        }
        fun selected(name: String, allowed: List<String>, defaults: List<String> = allowed): List<String> {
            val values = arguments.getString(name)?.split(',')?.map(String::trim)?.distinct() ?: defaults
            require(values.isNotEmpty() && values.all { it in allowed }) { "$name must select from $allowed" }
            return values
        }
        val backgrounds = selected("glassStaticBackgrounds",
            externalRecords?.map { it.getString("id") } ?: OpticalBackground.entries.map { it.name })
        val themes = selected("glassStaticThemes", listOf("light", "dark")).map { it == "dark" }
        val surfaces = selected("glassStaticSurfaces", OpticalSurface.entries.map { it.name },
            listOf("home", "share_sheet", "edit_sheet")).map(OpticalSurface::valueOf)
        assumeTrue("menu captures require actual attached-popup PixelCopy, available on API 34+",
            OpticalSurface.menu !in surfaces || Build.VERSION.SDK_INT >= 34)
        val states = selected("glassStaticStates", listOf("rest", "held"))
        require(!opticalStaticPlainAlertLifecycle ||
            (externalInputDirectory != null && opticalNativeCanvas &&
                surfaces == listOf(OpticalSurface.alert) && states == listOf("rest"))) {
            "glassStaticPlainAlertLifecycle=true requires external PNG inputs, fixed native canvas, glassStaticSurfaces=alert and glassStaticStates=rest"
        }
        val runId = (arguments.getString("glassStaticRunId") ?: System.currentTimeMillis().toString())
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "glass-optical-backgrounds/$runId")
        assertTrue("Create static optical output directory", directory.isDirectory || directory.mkdirs())
        val captures = JSONArray()
        val baselines = JSONArray()
        val presentationActions = JSONArray()
        val lifecycleCaptures = JSONArray()
        val lifecycleRequests = JSONArray()
        val manifest = JSONObject().put("schema", 1).put("run_id", runId).put("status", "running")
            .put("external_input_manifest", externalManifest ?: JSONObject.NULL)
            .put("evaluation_split", externalManifest?.getString("split") ?: "historical-calibration")
            .put("requested_sheet_content_height_dp", opticalSheetContentHeightDp)
            .put("sheet_content_height_explicit", sheetContentHeightArgument != null)
            .put("glass_static_native_canvas", opticalNativeCanvas)
            .put("background_canvas_policy", if (opticalNativeCanvas)
                "Opt-in glassStaticNativeCanvas=true: actual fixed 375x667dp source world at the existing GlassBackground top-left, clipped to that world with neutral #F0F0F5 outside; never stretched to the Activity. Geometry is observed with localToScreen."
                else "Default glassStaticNativeCanvas=false: preserve the original Activity-sized background reproduction; geometry is observed with localToScreen.")
            .put("native_canvas_limits", "Android font rasterization and menu/alert row shapes still differ from iOS. This is a static GPU fixture, not motion proof. Opt-in canvas captures do not establish an old-product GPU before image in that world; such comparisons need a separately captured old-product canvas or an explicitly labelled counterfactual proxy.")
            .put("sdk", Build.VERSION.SDK_INT).put("model", Build.MODEL).put("fingerprint", Build.FINGERPRINT)
            .put("inputs", JSONArray(externalRecords ?: OpticalBackground.entries.map { it.metadata() }))
            .put("theme_preset", ThemePresets.KEY_DEFAULT)
            .put("matrix", "Selected fixed inputs × themes × carriers; optional menu/alert use actual public presentation controls, with rest and each named held control")
            .put("selected_backgrounds", JSONArray(backgrounds))
            .put("selected_themes", JSONArray(themes.map { if (it) "dark" else "light" }))
            .put("selected_surfaces", JSONArray(surfaces.map { it.name })).put("selected_states", JSONArray(states))
            .put("fixture_scope", "Actual product GlassTopAppBar/buttons/Dock/common controls, GlassModalBottomSheet share/edit carriers, and opt-in GlassDropdownMenu/GlassAlertDialog; not a full screen data-flow test")
            .put("capture_sources", "PixelCopy of actual Activity/Dialog Window or API34+ attached Popup ViewRoot after HWUI commit, plus UiAutomation composited-display screenshot; both are saved separately")
            .put("content_density_policy", "Fixture-only fontScale1 is explicitly provided inside actual Popup content, each Alert slot and Sheet content; SideEffect reads are asserted against the captured root and exported per capture")
            .put("baseline_policy", "Plain background before any optical controls. Home window metrics use same-window baseline. Popup/Dialog surface metrics have no comparable surface baseline; display metrics compare the same full display background at measured screen coordinates, including any product scrim.")
            .put("acceptance", "No single background or scalar metric proves parity. Review every input, theme and carrier. Center/edge RGB, luminance, saturation and channel gradients are descriptive, not inferred opacity/dispersion or pass thresholds.")
            .put("clock", "Compose mainClock static snapshots; presentation taps wait for recomposition, advance one frame to start popup animation, then settle640ms. Held after640ms without movement; not natural-time motion evidence or a product-fix assertion.")
            .put("presentation_fixture", "Menu anchor [126.5,411.5,122.5,33.5]dp and public offset [-63.5,-146]dp align its top-left with native [63,299]pt on the larger Android display. Liquid Glass uses measured 42dp rows plus 10dp top/bottom insets; Material Design retains M3 dimensions. Content follows observed native above-anchor display order Delete/Second action/First action. Alert title/message/actions use the measured native layout. Actual root/screen rectangles are exported; backing controls and font rasterization differ from iOS.")
            .put("background_input_policy", when {
                externalManifest != null -> "Preregistered external holdout PNGs: validate the complete manifest and every input in both light/dark traits before composition, including each original PNG byte hash. Each complete750x1334 source maps once to the existing375x667dp world using FilterQuality.Low. These stimuli require independently captured new native screenshots, not historical native-image substitutions. No crop, alignment fit, product geometry change or appearance-parity claim."
                nativeInputDirectory != null -> "Opt-in native bare-background PNGs: all eight750x1334 inputs are validated and decoded before composition. Each complete image maps once to the existing375x667dp world using actual rounded Android layout pixels and FilterQuality.Low. The world stays at the real GlassBackground origin; the production Sheet is not moved or resized to fit it. No appearance-parity claim."
                else -> "Existing OpticalCaptureBackground remains selected; no external PNG input was supplied."
            })
            .put("baselines", baselines).put("captures", captures).put("presentation_actions", presentationActions)
        if (opticalStaticPlainAlertLifecycle) {
            manifest.put("plain_alert_lifecycle_request", JSONObject()
                .put("schema", 1)
                .put("glass_static_plain_alert_lifecycle", true)
                .put("instrumentation_arguments", JSONObject().apply {
                    for (argument in arguments.keySet().sorted()) put(argument, arguments.get(argument)?.toString() ?: JSONObject.NULL)
                })
                .put("external_inputs_directory", externalInputDirectory)
                .put("fixed_native_canvas", opticalNativeCanvas)
                .put("surfaces", JSONArray(surfaces.map { it.name })).put("states", JSONArray(states))
                .put("phases", JSONArray(listOf("clean-before", "rest", "rest-repeat", "dismiss-after")))
                .put("minimum_rest_complete_to_repeat_request_elapsed_ns", 250_000_000L)
                .put("repeat_interval_policy", "SystemClock.elapsedRealtimeNanos after first capture completion; real SystemClock.sleep, never Compose clock advancement")
                .put("trigger_policy", "Existing public trigger retains layout, semantics and pointer handler; fixture-only alpha0 prevents its drawing from contaminating the source")
                .put("action_policy", "Open once through public trigger, capture twice, cancel once through original public Cancel, switch to plain fixture, await unchanged display convergence"))
                .put("lifecycle_captures", lifecycleCaptures).put("lifecycle_requests", lifecycleRequests)
        }
        fun sceneFor(background: String, dark: Boolean, surface: OpticalSurface = OpticalSurface.home,
                     showControls: Boolean = true) = if (externalRecords == null)
            OpticalScene(OpticalBackground.valueOf(background), dark, surface, showControls)
        else OpticalScene(null, dark, surface, showControls, externalInputId = background)
        val scene = mutableStateOf(sceneFor(backgrounds.first(), themes.first(), showControls = false))
        File(directory, "matrix.json").writeText(manifest.toString(2))
        try {
            if (inputDirectory != null) {
                val validation = JSONObject().put("status", "validating")
                manifest.put("native_input_validation", validation)
                opticalNativeInputs = loadStaticNativeInputs(inputDirectory, validation, externalRecords)
                manifest.put("inputs", JSONArray(backgrounds.flatMap { background -> themes.map { dark ->
                    opticalNativeInputs.getValue(staticNativeInputName(background, dark)).metadata
                } }))
                File(directory, "matrix.json").writeText(manifest.toString(2))
            }
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
            for (dark in themes) for (background in backgrounds) {
                val plain = sceneFor(background, dark, showControls = false)
                val cleanStateRequest = if (opticalStaticPlainAlertLifecycle)
                    opticalLifecycleRequest(plain, "show-plain-before").also { lifecycleRequests.put(it) } else null
                val cleanLifecycle = if (opticalStaticPlainAlertLifecycle)
                    opticalLifecycleRecord(plain, "clean-before").also {
                        lifecycleCaptures.put(it)
                    } else null
                markOpticalLifecycle(cleanLifecycle, "plain_state_requested")
                compose.runOnIdle { scene.value = plain }
                settleOptical()
                markOpticalLifecycle(cleanLifecycle, "plain_state_settled")
                cleanStateRequest?.put("completed_at", opticalLifecycleInstant())
                val activity = checkNotNull(opticalActivity)
                assertTrue("Static optics require hardware acceleration", activity.root.isHardwareAccelerated)
                cleanLifecycle?.put("environment_before_capture", opticalLifecycleEnvironment(plain))
                markOpticalLifecycle(cleanLifecycle, "window_capture_requested")
                val baselineWindow = activity.capture()
                markOpticalLifecycle(cleanLifecycle, "window_capture_completed")
                val baselineName = "$background-${if (dark) "dark" else "light"}-background"
                baselineWindow.saveOpticalPng(File(directory, "$baselineName-window.png"))
                markOpticalLifecycle(cleanLifecycle, "screen_convergence_requested")
                val readyScreen = try {
                    awaitOpticalBackgroundScreen(activity, baselineWindow, density, directory, baselineName)
                } catch (failure: Throwable) {
                    baselineWindow.recycle()
                    cleanLifecycle?.put("status", "failed")?.put("exception", "${failure.javaClass.name}: ${failure.message}")
                    markOpticalLifecycle(cleanLifecycle, "capture_failed")
                    throw failure
                }
                markOpticalLifecycle(cleanLifecycle, "screen_capture_completed")
                val baselineScreen = readyScreen.bitmap
                try {
                    baselineScreen.saveOpticalPng(File(directory, "$baselineName-screen.png"))
                    val baselineRecord = JSONObject().put("background", background).put("dark", dark)
                        .put("window_png", "$baselineName-window.png").put("screen_png", "$baselineName-screen.png")
                        .put("background_canvas", opticalBackgroundWorld())
                        .put("window_geometry", activity.geometry())
                        .put("display_readiness", readyScreen.verification)
                    baselines.put(baselineRecord)
                    if (cleanLifecycle != null) {
                        val cleanCapture = JSONObject(baselineRecord.toString())
                            .put("scene", plain.name).put("phase", "clean-before")
                            .put("theme", if (dark) "dark" else "light").put("window_source", "activity")
                            .put("window_size_px", JSONArray(listOf(baselineWindow.width, baselineWindow.height)))
                            .put("screen_size_px", JSONArray(listOf(baselineScreen.width, baselineScreen.height)))
                            .put("content_origin_in_window_px", JSONArray(activity.origin().toList()))
                            .put("content_origin_on_screen_px", JSONArray(activity.screenOrigin().toList()))
                        cleanLifecycle.put("capture", cleanCapture)
                            .put("environment_after_capture", opticalLifecycleEnvironment(plain)).put("status", "captured")
                        markOpticalLifecycle(cleanLifecycle, "capture_completed")
                    }
                    for (surface in surfaces) {
                        val current = sceneFor(background, dark, surface)
                        val showTriggerRequest = if (opticalStaticPlainAlertLifecycle)
                            opticalLifecycleRequest(current, "show-alert-trigger").also { lifecycleRequests.put(it) } else null
                        compose.runOnIdle { opticalPresentationEvents.clear(); scene.value = current }
                        settleOptical()
                        showTriggerRequest?.put("completed_at", opticalLifecycleInstant())
                        if (opticalStaticPlainAlertLifecycle) {
                            val openRequest = opticalLifecycleRequest(current, "open", surface.triggerTag)
                            lifecycleRequests.put(openRequest)
                            tapOptical(surface.triggerTag)
                            openRequest.put("completed_at", opticalLifecycleInstant())
                            val rest = opticalLifecycleRecord(current, "rest").also { lifecycleCaptures.put(it) }
                            captureOptical(current, "rest", directory, captures, baselineName, baselineWindow, baselineScreen, rest)
                            val repeatNotBeforeNs = rest.getJSONObject("timing").getJSONObject("capture_completed")
                                .getLong("elapsed_realtime_ns") + 250_000_000L
                            val repeatWait = opticalLifecycleRequest(current, "wait-real-rest-repeat")
                                .put("not_before_elapsed_realtime_ns", repeatNotBeforeNs)
                            lifecycleRequests.put(repeatWait)
                            while (true) {
                                val remaining = repeatNotBeforeNs - SystemClock.elapsedRealtimeNanos()
                                if (remaining <= 0L) break
                                SystemClock.sleep((remaining + 999_999L) / 1_000_000L)
                            }
                            repeatWait.put("completed_at", opticalLifecycleInstant())
                            val repeat = opticalLifecycleRecord(current, "rest-repeat").also { lifecycleCaptures.put(it) }
                            val repeatRequestedNs = repeat.getJSONObject("timing").getJSONObject("capture_requested")
                                .getLong("elapsed_realtime_ns")
                            assertTrue("Real rest-repeat request is at least250ms after first capture completion", repeatRequestedNs >= repeatNotBeforeNs)
                            repeat.put("previous_capture_complete_to_request_elapsed_ns", repeatRequestedNs -
                                rest.getJSONObject("timing").getJSONObject("capture_completed").getLong("elapsed_realtime_ns"))
                            captureOptical(current, "rest-repeat", directory, captures, baselineName, baselineWindow, baselineScreen, repeat)
                            val cancelRequest = opticalLifecycleRequest(current, "cancel", "static-alert-cancel")
                            lifecycleRequests.put(cancelRequest)
                            verifyOpticalPresentationActions(current, presentationActions, cancelOnly = true)
                            cancelRequest.put("completed_at", opticalLifecycleInstant())
                            val dismiss = opticalLifecycleRecord(plain, "dismiss-after").also { lifecycleCaptures.put(it) }
                            val plainAfterRequest = opticalLifecycleRequest(plain, "show-plain-after-cancel").also { lifecycleRequests.put(it) }
                            markOpticalLifecycle(dismiss, "plain_state_requested")
                            compose.runOnIdle { scene.value = plain }
                            settleOptical()
                            markOpticalLifecycle(dismiss, "plain_state_settled")
                            plainAfterRequest.put("completed_at", opticalLifecycleInstant())
                            captureOpticalLifecyclePlain(plain, dismiss, directory)
                            File(directory, "matrix.json").writeText(manifest.toString(2))
                            continue
                        }
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
            val statesPerScene = if (opticalStaticPlainAlertLifecycle) 2 else surfaces.sumOf {
                (if ("rest" in states) 1 else 0) + (if ("held" in states) it.heldTags.size else 0) }
            assertEquals("Every selected carrier/theme/background/state is captured",
                backgrounds.size * themes.size * statesPerScene, captures.length())
            if (opticalStaticPlainAlertLifecycle) assertEquals("Every selected input/theme has four lifecycle captures",
                backgrounds.size * themes.size * 4, lifecycleCaptures.length())
            manifest.put("status", "passed")
        } catch (failure: Throwable) {
            manifest.put("status", "failed").put("exception", "${failure.javaClass.name}: ${failure.message}")
            throw failure
        } finally {
            compose.mainClock.autoAdvance = true
            File(directory, "matrix.json").writeText(manifest.put("density", density)
                .put("font_scale", opticalFontScale).put("system_font_scale", opticalSystemFontScale)
                .put("content_density_observations", JSONArray(opticalContentDensityObservations.map { it.json() }))
                .put("native_alert_window_alignments", JSONArray(opticalNativeAlertAlignmentReports))
                .put("presentation_trace", JSONArray(opticalPresentationTrace.toList())).toString(2))
            writeOpticalMetricsCsv(File(directory, "optical-metrics.csv"), captures)
        }
    }

    private fun settleOptical() {
        compose.mainClock.advanceTimeBy(640)
        compose.waitForIdle()
    }

    private fun opticalLifecycleInstant() = JSONObject()
        .put("elapsed_realtime_ns", SystemClock.elapsedRealtimeNanos())
        .put("wall_uptime_ms", SystemClock.uptimeMillis())
        .put("unix_epoch_ms", System.currentTimeMillis())
        .put("compose_ms", compose.mainClock.currentTime)

    private fun markOpticalLifecycle(record: JSONObject?, event: String) {
        record?.getJSONObject("timing")?.put(event, opticalLifecycleInstant())
    }

    private fun opticalLifecycleRequest(scene: OpticalScene, action: String, tag: String? = null) = JSONObject()
        .put("scene", scene.name).put("input_scene", scene.inputName).put("show_controls", scene.showControls)
        .put("action", action).put("tag", tag ?: JSONObject.NULL).put("requested_at", opticalLifecycleInstant())

    private fun opticalLifecycleRecord(scene: OpticalScene, phase: String) = JSONObject()
        .put("scene", "${scene.inputName}-alert").put("actual_fixture_scene", scene.name)
        .put("background", scene.backgroundId).put("theme", if (scene.dark) "dark" else "light")
        .put("phase", phase).put("status", "requested")
        .put("show_controls", scene.showControls).put("requested_presentation_visible", scene.showControls)
        .put("trigger_alpha", if (scene.showControls) 0.0 else JSONObject.NULL)
        .put("source_input", opticalNativeInputs.getValue(scene.inputName).metadata)
        .put("timing", JSONObject().put("capture_requested", opticalLifecycleInstant()))

    /** Raw platform configuration and actual composition observations, not inferred theme support. */
    private fun opticalLifecycleEnvironment(scene: OpticalScene): JSONObject = compose.runOnIdle {
        val sourceView = checkNotNull(opticalActivity).view
        var activityContext = sourceView.context
        while (activityContext !is Activity && activityContext is ContextWrapper) activityContext = activityContext.baseContext
        val activity = activityContext as Activity
        val configuration = activity.resources.configuration
        val displayMetrics = activity.resources.displayMetrics
        val observations = opticalLifecycleContentObservations.filter {
            it.getString("scene") == scene.name && it.getBoolean("show_controls") == scene.showControls
        }.associateBy { it.getString("slot") }
        JSONObject().put("observed_at", opticalLifecycleInstant())
            .put("activity_class", activity.javaClass.name).put("activity_identity", System.identityHashCode(activity))
            .put("activity_configuration", JSONObject()
                .put("ui_mode", configuration.uiMode)
                .put("ui_mode_night_mask", configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                .put("density_dpi", configuration.densityDpi).put("font_scale", configuration.fontScale)
                .put("locale_language_tags", configuration.locales.toLanguageTags())
                .put("display_metrics_density", displayMetrics.density).put("display_metrics_density_dpi", displayMetrics.densityDpi))
            .put("content_observations", JSONArray(observations.values.toList()))
            .put("observation_policy", "Activity resources are read at capture time; composition LocalDarkTheme/LocalUiStyle, density, resolved theme colors and actual view/root identity are observed independently inside each existing content scope")
    }

    @Composable private fun ObserveOpticalLifecycleContent(scene: OpticalScene, slot: String) {
        val actualDensity = LocalDensity.current
        val actualView = LocalView.current
        val actualDark = LocalDarkTheme.current
        val actualStyle = LocalUiStyle.current
        val colors = MaterialTheme.colorScheme
        SideEffect {
            opticalLifecycleContentObservations.add(JSONObject()
                .put("scene", scene.name).put("show_controls", scene.showControls).put("slot", slot)
                .put("observed_at", opticalLifecycleInstant()).put("local_dark_theme", actualDark)
                .put("local_ui_style", actualStyle.toString())
                .put("density", actualDensity.density).put("font_scale", actualDensity.fontScale)
                .put("resolved_primary_argb_int", colors.primary.toArgb())
                .put("resolved_surface_argb_int", colors.surface.toArgb())
                .put("resolved_background_argb_int", colors.background.toArgb())
                .put("view_class", actualView.javaClass.name).put("view_identity", System.identityHashCode(actualView))
                .put("root_class", actualView.rootView.javaClass.name).put("root_identity", System.identityHashCode(actualView.rootView))
                .put("view_attached_at_observation", actualView.isAttachedToWindow))
        }
    }

    private fun captureOpticalLifecyclePlain(scene: OpticalScene, record: JSONObject, directory: File) {
        check(opticalStaticPlainAlertLifecycle && !scene.showControls)
        compose.waitForIdle()
        val activity = checkNotNull(opticalActivity)
        val stem = "${scene.inputName}-alert-${record.getString("phase")}"
        record.put("environment_before_capture", opticalLifecycleEnvironment(scene))
        markOpticalLifecycle(record, "window_capture_requested")
        val windowBitmap = activity.capture()
        markOpticalLifecycle(record, "window_capture_completed")
        try {
            windowBitmap.saveOpticalPng(File(directory, "$stem-window.png"))
            markOpticalLifecycle(record, "screen_convergence_requested")
            val ready = awaitOpticalBackgroundScreen(activity, windowBitmap, density, directory, stem)
            markOpticalLifecycle(record, "screen_capture_completed")
            try {
                ready.bitmap.saveOpticalPng(File(directory, "$stem-screen.png"))
                record.put("capture", JSONObject().put("scene", scene.name).put("background", scene.backgroundId)
                    .put("theme", if (scene.dark) "dark" else "light").put("phase", record.getString("phase"))
                    .put("window_source", "activity").put("window_png", "$stem-window.png").put("screen_png", "$stem-screen.png")
                    .put("window_size_px", JSONArray(listOf(windowBitmap.width, windowBitmap.height)))
                    .put("screen_size_px", JSONArray(listOf(ready.bitmap.width, ready.bitmap.height)))
                    .put("window_geometry", activity.geometry()).put("background_canvas", opticalBackgroundWorld())
                    .put("content_origin_in_window_px", JSONArray(activity.origin().toList()))
                    .put("content_origin_on_screen_px", JSONArray(activity.screenOrigin().toList()))
                    .put("display_readiness", ready.verification))
                    .put("environment_after_capture", opticalLifecycleEnvironment(scene)).put("status", "captured")
                markOpticalLifecycle(record, "capture_completed")
            } finally { ready.bitmap.recycle() }
        } catch (failure: Throwable) {
            record.put("status", "failed").put("exception", "${failure.javaClass.name}: ${failure.message}")
            markOpticalLifecycle(record, "capture_failed")
            throw failure
        } finally { windowBitmap.recycle() }
    }

    private fun opticalBackgroundWorld(): JSONObject = compose.runOnIdle {
        val world = OpticalLayoutGeometry.read(checkNotNull(opticalBackgroundCoordinates), density)
        val host = OpticalLayoutGeometry.read(checkNotNull(opticalBackgroundHostCoordinates), density)
        if (opticalNativeCanvas) {
            assertEquals("Native canvas actual width", 375.0 * density, world.size.width.toDouble(), .5)
            assertEquals("Native canvas actual height", 667.0 * density, world.size.height.toDouble(), .5)
            assertTrue("Native canvas actual top-left is the real GlassBackground top-left",
                abs(world.screenOrigin.x - host.screenOrigin.x) <= .5f &&
                    abs(world.screenOrigin.y - host.screenOrigin.y) <= .5f)
        }
        world.json().put("native_canvas", opticalNativeCanvas)
            .put("requested_logical_size_dp", if (opticalNativeCanvas) JSONArray(listOf(375, 667)) else JSONObject.NULL)
            .put("glass_background_host", host.json())
            .put("outside_world", if (opticalNativeCanvas) "opaque neutral #F0F0F5, recorded in the real GlassBackground source" else JSONObject.NULL)
            .apply { opticalCurrentScene?.let { scene -> staticNativeInputMapping(scene, world)?.let {
                put("native_input_mapping", it)
            } } }
    }

    private fun staticNativeInputName(backgroundId: String, dark: Boolean) =
        "$backgroundId-${if (dark) "dark" else "light"}"

    private fun staticInputDirectory(directoryName: String): File {
        require(directoryName.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*"))) {
            "Input directory must be one directory name beneath target externalFilesDir"
        }
        val external = checkNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null))
            .canonicalFile
        val directory = File(external, directoryName).canonicalFile
        require(directory.parentFile == external && directory.isDirectory) {
            "Input directory must exist directly beneath target externalFilesDir: $directory"
        }
        return directory
    }

    /** Same schema1 preregistration protocol as the independent Dock fixture. */
    private fun loadStaticExternalManifest(directoryName: String): JSONObject {
        val directory = staticInputDirectory(directoryName)
        val file = File(directory, "manifest.json").canonicalFile
        require(file.parentFile == directory && file.isFile && file.length() <= 128 * 1024) {
            "External input manifest must be a local manifest.json of at most128KiB"
        }
        return JSONObject(file.readText()).also { manifest ->
            require(manifest.getInt("schema") == 1 && manifest.getString("split") in listOf("holdout", "calibration"))
            require(manifest.getString("set_id").matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]*")))
            val inputs = manifest.getJSONArray("inputs")
            require(inputs.length() in 1..32)
            val ids = mutableSetOf<String>()
            for (index in 0 until inputs.length()) {
                val record = inputs.getJSONObject(index)
                val id = record.getString("id")
                require(id.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]*")) && ids.add(id))
                require(record.getString("file") == "$id.png")
                require(record.getString("sha256").matches(Regex("[a-f0-9]{64}")))
            }
        }
    }

    /** Preflight every registered input in both themes before setContent; no draw I/O. */
    private fun loadStaticNativeInputs(directoryName: String, report: JSONObject,
                                       externalRecords: List<JSONObject>? = null): Map<String, StaticNativeInput> {
        val directory = staticInputDirectory(directoryName)
        val ids = externalRecords?.map { it.getString("id") } ?: OpticalBackground.entries.map { it.name }
        val expectedCount = ids.size * 2
        val records = JSONArray()
        report.put("directory", directory.path).put("expected_count", expectedCount).put("inputs", records)
            .put("decode_policy", "BitmapFactory inScaled=false, inSampleSize=1, ARGB_8888, preferred sRGB; Bitmap.DENSITY_NONE. SHA256 covers original PNG bytes. No pixel edits or pre-resampling.")
        val loaded = linkedMapOf<String, StaticNativeInput>()
        try {
            for (id in ids) for (dark in listOf(false, true)) {
                val name = staticNativeInputName(id, dark)
                val declared = externalRecords?.first { it.getString("id") == id }
                val file = File(directory, declared?.getString("file") ?: "$name.png").canonicalFile
                require(file.parentFile == directory && file.isFile) { "Missing native input $name.png" }
                val bytes = file.readBytes()
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it.toInt() and 255) }
                require(declared == null || hash == declared.getString("sha256")) {
                    "External input $id has changed after manifest registration"
                }
                val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
                require(bytes.size >= signature.size && signature.indices.all { bytes[it] == signature[it] }) {
                    "$name.png must contain PNG bytes"
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth == 750 && bounds.outHeight == 1334 && bounds.outMimeType == "image/png") {
                    "$name.png must be750x1334; got ${bounds.outWidth}x${bounds.outHeight} ${bounds.outMimeType}"
                }
                val options = BitmapFactory.Options().apply {
                    inScaled = false
                    inSampleSize = 1
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
                }
                val bitmap = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) {
                    "Cannot decode native input $name.png"
                }
                val metadata = JSONObject().put("scene", name).put("file", file.name)
                    .put("sha256", hash).put("external_input_record", declared ?: JSONObject.NULL)
                    .put("file_bytes", bytes.size).put("size_px", JSONArray(listOf(bitmap.width, bitmap.height)))
                    .put("native_pixels_per_point", 2).put("native_world_size_pt", JSONArray(listOf(375, 667)))
                    .put("encoded_color_space", bounds.outColorSpace?.name ?: JSONObject.NULL)
                    .put("decoded_color_space", bitmap.colorSpace?.name ?: JSONObject.NULL)
                    .put("decoded_config", bitmap.config?.name ?: JSONObject.NULL)
                loaded[name] = StaticNativeInput(bitmap, metadata)
                records.put(metadata)
                require(bitmap.width == 750 && bitmap.height == 1334 && bitmap.config == Bitmap.Config.ARGB_8888 &&
                    bitmap.colorSpace?.isSrgb == true) { "$name.png decoded with unexpected geometry/config/color space" }
                bitmap.density = Bitmap.DENSITY_NONE
                val row = IntArray(bitmap.width)
                var minimumAlpha = 255
                for (y in 0 until bitmap.height) {
                    bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
                    for (pixel in row) minimumAlpha = minOf(minimumAlpha, pixel ushr 24)
                }
                metadata.put("minimum_decoded_alpha", minimumAlpha).put("bitmap_density", bitmap.density)
                require(minimumAlpha == 255) { "$name.png must be an opaque bare background" }
            }
            require(loaded.size == expectedCount) { "All registered background/theme inputs are required" }
            report.put("status", "validated").put("validated_count", loaded.size)
            return loaded
        } catch (failure: Throwable) {
            report.put("status", "failed").put("exception", "${failure.javaClass.name}: ${failure.message}")
            loaded.values.forEach { it.bitmap.recycle() }
            throw failure
        }
    }

    @Composable private fun StaticNativeInputBackground(input: StaticNativeInput, modifier: Modifier) {
        val image = remember(input.bitmap) { input.bitmap.asImageBitmap() }
        Canvas(modifier) {
            drawImage(image = image, srcOffset = IntOffset.Zero, srcSize = IntSize(750, 1334),
                dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Low)
        }
    }

    private fun staticNativeInputMapping(scene: OpticalScene, source: OpticalLayoutGeometry): JSONObject? =
        opticalNativeInputs[scene.inputName]?.let { input ->
            JSONObject().put("input_scene", scene.inputName)
                .put("input_sha256", input.metadata.getString("sha256"))
                .put("source_rect_px", JSONArray(listOf(0, 0, 750, 1334)))
                .put("destination_rect_local_px", JSONArray(listOf(0, 0, source.size.width, source.size.height)))
                .put("requested_world_size_dp", JSONArray(listOf(375, 667)))
                .put("android_density_px_per_dp", source.density).put("native_density_px_per_pt", 2)
                .put("ideal_android_px_per_native_px", source.density / 2f)
                .put("actual_scale_xy", JSONArray(listOf(source.size.width / 750.0, source.size.height / 1334.0)))
                .put("destination_rounding_residual_px", JSONArray(listOf(
                    source.size.width - 375f * source.density, source.size.height - 667f * source.density)))
                .put("filter_quality", "Low").put("crop", false)
                .put("policy", "Complete source mapped once to the complete measured fixed-world layout; native2px/pt becomes actual Android density px/dp, with only existing independent layout rounding. No geometry fit or native similarity conclusion.")
        }

    /** Observe the actual production material coordinates after its sheet-placement offset. */
    private fun opticalSheetGeometryComparison(): JSONObject = compose.runOnIdle {
        val pane = OpticalLayoutGeometry.read(checkNotNull(checkNotNull(opticalSheetPane).coordinates), density)
        val world = OpticalLayoutGeometry.read(checkNotNull(opticalBackgroundCoordinates), density)
        val actual = Rect((pane.screenRect.left - world.screenOrigin.x) / density,
            (pane.screenRect.top - world.screenOrigin.y) / density,
            (pane.screenRect.right - world.screenOrigin.x) / density,
            (pane.screenRect.bottom - world.screenOrigin.y) / density)
        val native = Rect(8f, 317.7f, 367f, 659f)
        val worldWidth = world.size.width / density
        val worldHeight = world.size.height / density
        val overlapWidth = (minOf(actual.right, worldWidth) - maxOf(actual.left, 0f)).coerceAtLeast(0f)
        val overlapHeight = (minOf(actual.bottom, worldHeight) - maxOf(actual.top, 0f)).coerceAtLeast(0f)
        JSONObject().put("actual_material_layout", pane.json())
            .put("actual_pane_world_ltrb_dp", JSONArray(listOf(actual.left, actual.top, actual.right, actual.bottom)))
            .put("actual_pane_size_dp", JSONArray(listOf(actual.width, actual.height)))
            .put("native_ios26_5_medium_sheet_ax_world_ltrb_pt", JSONArray(listOf(native.left, native.top, native.right, native.bottom)))
            .put("native_sheet_ax_size_pt", JSONArray(listOf(native.width, native.height)))
            .put("android_minus_native_ltrb_logical", JSONArray(listOf(actual.left - native.left, actual.top - native.top,
                actual.right - native.right, actual.bottom - native.bottom)))
            .put("android_minus_native_size_logical", JSONArray(listOf(actual.width - native.width, actual.height - native.height)))
            .put("background_world_size_dp", JSONArray(listOf(worldWidth, worldHeight)))
            .put("pane_bounds_inside_background_world", actual.left >= 0 && actual.top >= 0 &&
                actual.right <= worldWidth && actual.bottom <= worldHeight)
            .put("pane_bounds_area_fraction_inside_background_world", overlapWidth * overlapHeight / (actual.width * actual.height))
            .put("outside_background_world", "Existing opaque neutral#F0F0F5; pane geometry and fixed source world are not fitted together")
            .put("geometry_source", "Production GlassMaterialScope attached LayoutCoordinates after real placement, mapped by localToScreen; a material layout rectangle, not an alpha mask or the pre-placement semantics carrier")
            .put("native_reference", "native-static-optics/rest/optics-fine_grid-light-presentations-sheet-rest; public AX[8,317.7,359,341.3]pt on375x667pt iOS26.5/23F77. Neutral visible raster contour separately measured[8,317.5,367,659]pt")
            .put("requested_sheet_content_height_dp", opticalSheetContentHeightDp)
            .put("limits", "Android production Expanded sheet with fixture${opticalSheetContentHeightDp}dp content and actual device Window differs from iOS system medium detent and native contents. Delta is descriptive, with Android dp versus iOS pt at declared world origins; no translation, resize, crop, contour equivalence or optical-parity inference.")
    }

    private fun opticalAlertLayout(): OpticalAlertLayout = compose.runOnIdle {
        val pane = checkNotNull(checkNotNull(opticalAlertPane).coordinates)
        check(pane.isAttached) { "Actual Alert material coordinates must be attached" }
        val drawingSpace = checkNotNull(pane.parentLayoutCoordinates) { "Actual Alert drawing-space parent is required" }
        OpticalAlertLayout(OpticalLayoutGeometry.read(pane, density),
            OpticalLayoutGeometry.read(drawingSpace, density), drawingSpace.localPositionOf(pane, Offset.Zero))
    }

    private fun assertNativeAlertWidth(scene: OpticalScene, layout: OpticalAlertLayout) {
        assertEquals("${scene.name}: actual native Alert layout width is 320dp", 320.0 * density,
            layout.pane.size.width.toDouble(), .5)
        assertEquals("${scene.name}: REST visible pane width is 320dp", 320.0 * density,
            layout.pane.screenRect.width.toDouble(), .5)
    }

    private fun assertAlertDrawingSpace(scene: OpticalScene, layout: OpticalAlertLayout) {
        val pane = layout.pane.size
        val outer = layout.drawingSpace.size
        val inset = layout.paneOriginInDrawingSpace
        assertTrue("${scene.name}: direct drawing-space parent includes real halo on both axes",
            outer.width > pane.width && outer.height > pane.height)
        assertTrue("${scene.name}: direct parent wraps pane and halo, rather than a full-window root",
            outer.width < pane.width * 1.1f && outer.height < pane.height * 1.1f)
        assertEquals("${scene.name}: pane centered within actual drawing-space width",
            (outer.width - pane.width) / 2.0, inset.x.toDouble(), .5)
        assertEquals("${scene.name}: pane centered within actual drawing-space height",
            (outer.height - pane.height) / 2.0, inset.y.toDouble(), .5)
        assertTrue("${scene.name}: REST pane is contained by actual drawing-space parent",
            inset.x >= 0f && inset.y >= 0f && inset.x + pane.width <= outer.width && inset.y + pane.height <= outer.height)
    }

    /** Resize the actual Dialog surface once at REST; never compensate its later press transform. */
    private fun alignOpticalNativeAlertOnce(scene: OpticalScene) {
        val surface = checkNotNull(opticalDialog)
        val dialog = checkNotNull(surface.window) { "Native Alert alignment requires its actual platform Window" }
        if (opticalNativeAlertAlignments.containsKey(dialog)) {
            assertOpticalNativeAlertPlacement(scene, surface)
            return
        }
        val before = opticalAlertLayout()
        assertNativeAlertWidth(scene, before)
        assertAlertDrawingSpace(scene, before)
        val background = compose.runOnIdle {
            OpticalLayoutGeometry.read(checkNotNull(opticalBackgroundCoordinates), density)
        }
        val target = background.screenOrigin + Offset(27.5f * density, 257.5f * density)
        val steps = JSONArray()
        val report = JSONObject().put("scene", scene.name).put("window_identity", System.identityHashCode(dialog))
            .put("target_pane_origin_in_world_dp", JSONArray(listOf(27.5, 257.5)))
            .put("target_pane_screen_origin_px", JSONArray(listOf(target.x, target.y)))
            .put("background_canvas", opticalBackgroundWorld()).put("before_layout", before.json())
            .put("before_window_geometry", surface.geometry()).put("requests", steps)
            .put("sizing_policy", "Public Window.setLayout to the actual DrawingSpace parent's measured WRAP_CONTENT pane-plus-halo size, then TOP|LEFT; at most three observed-delta public attributes relayouts")
            .put("held_policy", "Align this actual Window once after opening at REST; lock placement attributes thereafter so the product 1.0125 center scale remains visible")
        opticalNativeAlertAlignmentReports.add(report)

        fun committedLayout(): OpticalAlertLayout {
            compose.waitForIdle()
            surface.awaitHwuiCommit()
            return opticalAlertLayout()
        }
        val resizeRequest = JSONObject().put("operation", "setLayout-measured-wrap-content-and-top-left")
            .put("measured_drawing_space_size_px", JSONArray(listOf(before.drawingSpace.size.width, before.drawingSpace.size.height)))
            .put("requested_window_screen_origin_px", JSONObject.NULL)
            .put("request_origin_policy", "Initial gravity/inset origin is platform-defined and is observed after commit, never assumed zero")
        steps.put(resizeRequest)
        compose.runOnIdle {
            dialog.setLayout(before.drawingSpace.size.width, before.drawingSpace.size.height)
            val attributes = WindowManager.LayoutParams().apply {
                copyFrom(dialog.attributes)
                gravity = Gravity.TOP or Gravity.LEFT
                x = 0
                y = 0
                horizontalMargin = 0f
                verticalMargin = 0f
            }
            resizeRequest.put("requested_placement_attributes", OpticalWindowPlacement.read(attributes).json())
            dialog.attributes = attributes
        }
        var actual = committedLayout()
        resizeRequest.put("actual_layout_after_commit", actual.json()).put("actual_window_after_commit", surface.geometry())
        for (attempt in 1..3) {
            val delta = target - actual.pane.screenOrigin
            if (abs(delta.x) <= .5f && abs(delta.y) <= .5f) break
            val correction = Offset(delta.x.roundToInt().toFloat(), delta.y.roundToInt().toFloat())
            val previousWindow = surface.geometry()
            val previousOrigin = previousWindow.getJSONArray("window_screen_origin_px")
            val request = JSONObject().put("operation", "observed-delta-attributes-relayout").put("attempt", attempt)
                .put("observed_pane_screen_origin_px", JSONArray(listOf(actual.pane.screenOrigin.x, actual.pane.screenOrigin.y)))
                .put("observed_delta_to_target_px", JSONArray(listOf(delta.x, delta.y)))
                .put("integer_attributes_delta_px", JSONArray(listOf(correction.x, correction.y)))
                .put("requested_window_screen_origin_px", JSONArray(listOf(previousOrigin.getDouble(0) + correction.x,
                    previousOrigin.getDouble(1) + correction.y)))
                .put("request_origin_policy", "Prediction from previous observed window origin plus requested integer attributes delta; actual origin and insets are measured after commit")
                .put("window_before_request", previousWindow)
            steps.put(request)
            compose.runOnIdle {
                val attributes = WindowManager.LayoutParams().apply {
                    copyFrom(dialog.attributes)
                    x += correction.x.toInt()
                    y += correction.y.toInt()
                }
                check(attributes.x >= 0) { "Native fixture must not request a negative-x full-width dialog shift" }
                request.put("requested_placement_attributes", OpticalWindowPlacement.read(attributes).json())
                dialog.attributes = attributes
            }
            actual = committedLayout()
            request.put("actual_layout_after_commit", actual.json()).put("actual_window_after_commit", surface.geometry())
        }
        val residual = target - actual.pane.screenOrigin
        report.put("final_residual_px", JSONArray(listOf(residual.x, residual.y)))
            .put("final_layout", actual.json()).put("final_window_geometry", surface.geometry())
        assertNativeAlertWidth(scene, actual)
        assertAlertDrawingSpace(scene, actual)
        assertTrue("${scene.name}: actual native Alert top-left residual must be <=0.5 physical pixel: $residual",
            abs(residual.x) <= .5f && abs(residual.y) <= .5f)
        val placement = compose.runOnIdle { OpticalWindowPlacement.read(dialog.attributes) }
        report.put("locked_placement_attributes", placement.json()).put("verified", true)
        opticalNativeAlertAlignments[dialog] = OpticalNativeAlertAlignment(placement, report)
    }

    private fun assertOpticalNativeAlertPlacement(scene: OpticalScene, surface: OpticalCaptureWindow): JSONObject? {
        if (!opticalNativeCanvas || scene.surface != OpticalSurface.alert) return null
        val dialog = checkNotNull(surface.window)
        val aligned = checkNotNull(opticalNativeAlertAlignments[dialog]) { "${scene.name}: actual Alert Window must align once before capture" }
        val actual = compose.runOnIdle { OpticalWindowPlacement.read(dialog.attributes) }
        assertEquals("${scene.name}: Window placement remains locked after REST alignment, including held frames", aligned.placement, actual)
        return JSONObject().put("alignment", aligned.report).put("placement_unchanged", true)
            .put("actual_placement_attributes", actual.json())
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
        val openedScene = compose.runOnIdle { opticalCurrentScene }
        if (opticalNativeCanvas && openedScene != null && openedScene.surface == OpticalSurface.alert && tag == openedScene.surface.triggerTag) {
            alignOpticalNativeAlertOnce(openedScene)
        }
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
    private fun verifyOpticalPresentationActions(scene: OpticalScene, records: JSONArray, cancelOnly: Boolean = false) {
        check(!cancelOnly || opticalStaticPlainAlertLifecycle && scene.surface == OpticalSurface.alert)
        val actions = if (cancelOnly) listOf("cancel")
            else if (scene.surface == OpticalSurface.menu) listOf("first", "second", "delete")
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
            opticalCurrentScene = scene
            if (!scene.showControls || scene.surface == OpticalSurface.home) opticalDialog = null
        }
        SleepyThemeProvider(darkTheme = scene.dark, themeKey = ThemePresets.KEY_DEFAULT) {
            CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                val colors = MaterialTheme.colorScheme
                if (opticalStaticPlainAlertLifecycle) ObserveOpticalLifecycleContent(scene, "activity-content")
                GlassHost(Modifier.fillMaxSize(), background = colors.background) {
                    GlassBackground(0f, Modifier.fillMaxSize().onGloballyPositioned { opticalBackgroundHostCoordinates = it }) {
                        if (opticalNativeCanvas) {
                            Box(Modifier.fillMaxSize().background(Color(0xFFF0F0F5))) {
                                val sourceModifier = Modifier.align(Alignment.TopStart)
                                    .requiredSize(375.dp, 667.dp).clipToBounds()
                                    .onGloballyPositioned { opticalBackgroundCoordinates = it }
                                if (opticalNativeInputs.isEmpty()) OpticalCaptureBackground(checkNotNull(scene.background), sourceModifier)
                                else StaticNativeInputBackground(
                                    opticalNativeInputs.getValue(scene.inputName), sourceModifier)
                            }
                        } else {
                            OpticalCaptureBackground(checkNotNull(scene.background),
                                Modifier.onGloballyPositioned { opticalBackgroundCoordinates = it })
                        }
                    }
                    if (scene.showControls) key(scene.backgroundId, scene.dark, scene.surface) {
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
                                    Column(Modifier.fillMaxWidth().height(opticalSheetContentHeightDp.dp).padding(24.dp).testTag("static-sheet-content"),
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
                        .then(if (opticalStaticPlainAlertLifecycle) Modifier.alpha(0f) else Modifier)
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
                            val material = LocalGlassMaterialBackdrop.current as? GlassMaterialScope
                            SideEffect { opticalAlertPane = material }
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
            if (opticalStaticPlainAlertLifecycle) ObserveOpticalLifecycleContent(scene, slot)
            val actualDensity = LocalDensity.current
            val actualView = LocalView.current
            val actualMaterial = LocalGlassMaterialBackdrop.current as? GlassMaterialScope
            SideEffect {
                if (slot == "sheet-content") opticalSheetPane = actualMaterial
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
        fun windowRect(tag: String) = opticalBounds(scene, tag, compose.onNodeWithTag(tag, useUnmergedTree = true))
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
                               baselineName: String, baselineWindow: Bitmap, baselineScreen: Bitmap,
                               lifecycle: JSONObject? = null) {
        compose.waitForIdle()
        lifecycle?.put("environment_before_capture", opticalLifecycleEnvironment(scene))
        markOpticalLifecycle(lifecycle, "surface_capture_entered")
        val surface = if (scene.surface == OpticalSurface.home) checkNotNull(opticalActivity) else checkNotNull(opticalDialog)
        val nativeAlignment = assertOpticalNativeAlertPlacement(scene, surface)
        val contentDensity = verifiedOpticalContentDensity(scene, surface)
        val origin = surface.origin()
        val screenOrigin = surface.screenOrigin()
        markOpticalLifecycle(lifecycle, "window_capture_requested")
        val windowBitmap = surface.capture()
        markOpticalLifecycle(lifecycle, "window_capture_completed")
        val stem = "${scene.name}-$phase"
        markOpticalLifecycle(lifecycle, "screen_convergence_requested")
        val readyScreen = try {
            if (scene.surface.presentation) awaitOpticalSurfaceScreen(surface, windowBitmap, density, directory, stem)
            else OpticalBackgroundScreen(opticalScreenCapture(), JSONObject().put("policy", "Legacy non-presentation capture; no per-surface display convergence checked"))
        } catch (failure: Throwable) {
            windowBitmap.saveOpticalPng(File(directory, "$stem-window.png"))
            windowBitmap.recycle()
            lifecycle?.put("status", "failed")?.put("exception", "${failure.javaClass.name}: ${failure.message}")
            markOpticalLifecycle(lifecycle, "capture_failed")
            throw failure
        }
        markOpticalLifecycle(lifecycle, "screen_capture_completed")
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
                val rect = if (tag == "dock-lens") node.fetchSemanticsNode().config[DockLensBoundsInRootPx]
                    else opticalBounds(scene, tag, node)
                val localWindowRect = rect.translate(Offset(origin[0].toFloat(), origin[1].toFloat()))
                val screenRect = rect.translate(Offset(screenOrigin[0].toFloat(), screenOrigin[1].toFloat()))
                measurements.put(JSONObject().put("tag", tag)
                    .put("actual_root_rect_px", JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom)))
                    .put("actual_window_rect_px", JSONArray(listOf(localWindowRect.left, localWindowRect.top, localWindowRect.right, localWindowRect.bottom)))
                    .put("actual_screen_rect_px", JSONArray(listOf(screenRect.left, screenRect.top, screenRect.right, screenRect.bottom)))
                    .put("geometry_source", when {
                        tag == "dock-lens" -> "product painted capsule mapped to root"
                        scene.surface == OpticalSurface.alert -> "actual pane/slot layout coordinates; four transformed corners mapped to root, without clipping"
                        else -> "unclipped semantics layout bounds"
                    })
                    .put("window", opticalRegionMetrics(windowBitmap, if (scene.surface == OpticalSurface.home) baselineWindow else null, localWindowRect))
                    .put("composited_display", opticalRegionMetrics(screenBitmap, baselineScreen, screenRect)))
            }
            val anchorRect = if (scene.surface.presentation) {
                val activityOrigin = checkNotNull(opticalActivity).screenOrigin()
                bounds(compose.onNodeWithTag(scene.surface.triggerTag, useUnmergedTree = true))
                    .translate(Offset(activityOrigin[0].toFloat(), activityOrigin[1].toFloat()))
            } else null
            val captureRecord = JSONObject().put("scene", scene.name).put("background", scene.backgroundId)
                .put("theme", if (scene.dark) "dark" else "light").put("carrier", scene.surface.name).put("phase", phase)
                .put("window_source", when (scene.surface) {
                    OpticalSurface.home -> "activity"
                    OpticalSurface.menu -> "attached-popup-viewroot-api34"
                    else -> "dialog"
                })
                .put("window_png", "$stem-window.png").put("screen_png", "$stem-screen.png")
                .put("window_size_px", JSONArray(listOf(windowBitmap.width, windowBitmap.height)))
                .put("background_canvas", opticalBackgroundWorld())
                .put("window_geometry", surface.geometry())
                .put("actual_alert_layout", if (scene.surface == OpticalSurface.alert) opticalAlertLayout().json() else JSONObject.NULL)
                .put("sheet_geometry_comparison", if (opticalNativeInputs.isNotEmpty() &&
                    scene.surface in listOf(OpticalSurface.share_sheet, OpticalSurface.edit_sheet))
                    opticalSheetGeometryComparison() else JSONObject.NULL)
                .put("native_alert_alignment", nativeAlignment ?: JSONObject.NULL)
                .put("display_readiness", readyScreen.verification)
                .put("content_density_observations", contentDensity)
                .put("gpu_visibility", gpuVisibility)
                .put("presentation_anchor_screen_rect_px", anchorRect?.let {
                    JSONArray(listOf(it.left, it.top, it.right, it.bottom))
                } ?: JSONObject.NULL)
                .put("window_baseline_png", if (scene.surface == OpticalSurface.home) "$baselineName-window.png" else JSONObject.NULL)
                .put("display_baseline_png", "$baselineName-screen.png")
                .put("content_origin_in_window_px", JSONArray(origin.toList()))
                .put("content_origin_on_screen_px", JSONArray(screenOrigin.toList())).put("measurements", measurements)
            captures.put(captureRecord)
            assertOpticalNativeAlertPlacement(scene, surface)
            if (lifecycle != null) {
                captureRecord.put("screen_size_px", JSONArray(listOf(screenBitmap.width, screenBitmap.height)))
                lifecycle.put("capture", captureRecord)
                    .put("environment_after_capture", opticalLifecycleEnvironment(scene)).put("status", "captured")
                markOpticalLifecycle(lifecycle, "capture_completed")
            }
        } catch (failure: Throwable) {
            lifecycle?.put("status", "failed")?.put("exception", "${failure.javaClass.name}: ${failure.message}")
            markOpticalLifecycle(lifecycle, "capture_failed")
            throw failure
        } finally { windowBitmap.recycle(); screenBitmap.recycle() }
    }

    /** Layout size alone omits the Alert's outer graphicsLayer press transform. */
    private fun opticalBounds(scene: OpticalScene, tag: String, node: SemanticsNodeInteraction): Rect {
        if (scene.surface != OpticalSurface.alert) return bounds(node)
        val slotCoordinates = if (tag == scene.surface.carrierTag) null
            else node.fetchSemanticsNode().layoutInfo.coordinates
        return compose.runOnIdle {
            val coordinates = if (tag == scene.surface.carrierTag)
                checkNotNull(checkNotNull(opticalAlertPane).coordinates)
            else checkNotNull(slotCoordinates)
            check(coordinates.isAttached) { "${scene.name}: $tag coordinates must belong to the attached pane" }
            val w = coordinates.size.width.toFloat()
            val h = coordinates.size.height.toFloat()
            val corners = listOf(Offset.Zero, Offset(w, 0f), Offset(0f, h), Offset(w, h))
                .map(coordinates::localToRoot)
            Rect(corners.minOf { it.x }, corners.minOf { it.y }, corners.maxOf { it.x }, corners.maxOf { it.y })
        }
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
