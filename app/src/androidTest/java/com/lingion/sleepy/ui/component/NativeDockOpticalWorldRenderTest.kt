package com.lingion.sleepy.ui.component

import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.ui.component.liquidglass.DockLensBoundsInRootPx
import com.lingion.sleepy.ui.component.liquidglass.GlassBackground
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.component.liquidglass.LiquidGlassNavigationBar
import com.lingion.sleepy.ui.component.liquidglass.LocalDockInputTimeNanos
import com.lingion.sleepy.ui.component.liquidglass.LocalGlassDepth
import com.lingion.sleepy.ui.component.liquidglass.LocalGlassHost
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.LocalDarkTheme
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.ui.theme.ThemePresets
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
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Opt-in hardware optical evidence in the native reference's fixed source world.
 * Production Dock geometry, material, animation, and pointer handling are reused.
 * A successful run validates capture coordinates/readiness, not native appearance.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class NativeDockOpticalWorldRenderTest {
    @get:Rule val compose = createComposeRule()

    /** Keep the older independent UITabBar evidence distinct from the app's SwiftUI parent. */
    private enum class NativeParent(
        val argument: String,
        val callerOrigin: Offset,
        val callerWidth: Float,
        val nativeHolder: List<Int>,
        val base: Rect,
        val lens: Rect,
    ) {
        UIKit("uikit", Offset(30f, 560f), 316f, listOf(12, 572, 351, 83),
            Rect(51f, 572f, 325f, 634f), Rect(55f, 576f, 149f, 630f)),
        SwiftUI("swiftui", Offset(0f, 572f), 375f, listOf(0, 584, 375, 83),
            Rect(21f, 584f, 354f, 646f), Rect(25f, 588f, 113.25f, 642f)),
    }

    private lateinit var nativeParent: NativeParent

    private enum class SpatialInput(val gray: Int, val bandTop: Int? = null, val bandBottom: Int? = null) {
        A(26), B(26, 550, 667), C(26, 200, 317), D(230),
    }

    private data class Scene(val background: OpticalBackground, val dark: Boolean, val showDock: Boolean,
                             val spatialInput: SpatialInput? = null, val spatialStage: Int = -1) {
        val name get() = "${spatialInput?.name ?: background.name}-${if (dark) "dark" else "light"}"
    }

    private data class World(val source: OpticalLayoutGeometry, val host: OpticalLayoutGeometry) {
        fun json() = JSONObject().put("source", source.json()).put("glass_background_host", host.json())
            .put("requested_size_dp", JSONArray(listOf(375, 667)))
            .put("outside_world", "opaque neutral #F0F0F5 recorded in the same GlassBackground")
    }

    private data class DockGeometry(
        val world: World,
        val holder: OpticalLayoutGeometry,
        val carrier: OpticalLayoutGeometry,
        val baseLocal: Rect,
        val baseRootCorners: List<Offset>,
        val baseScreenCorners: List<Offset>,
        val lensRoot: Rect,
        val windowOrigin: Offset,
        val screenOrigin: Offset,
    ) {
        val baseScreen get() = bounds(baseScreenCorners)
        val lensScreen get() = lensRoot.translate(screenOrigin)
        val baseWorld get() = baseScreen.inWorld(world.source)
        val lensWorld get() = lensScreen.inWorld(world.source)

        fun json() = JSONObject().put("background_world", world.json())
            .put("outer_holder", holder.json()).put("shader_carrier", carrier.json())
            .put("base_source_local_rect_px", baseLocal.json())
            .put("base_actual_root_corners_px", points(baseRootCorners))
            .put("base_actual_screen_corners_px", points(baseScreenCorners))
            .put("base_world_rect_dp", baseWorld.json())
            .put("base_geometry_source", "Source-declared production base rect [0,12dp,actual carrier width,74dp], all four corners mapped through the real attached dock-lens carrier LayoutCoordinates. Base is a sibling with the same transformed parent. Excludes shadow; this is not a separately tagged base node or an alpha segmentation.")
            .put("painted_lens_root_rect_px", lensRoot.json())
            .put("painted_lens_window_rect_px", lensRoot.translate(windowOrigin).json())
            .put("painted_lens_screen_rect_px", lensScreen.json())
            .put("painted_lens_world_rect_dp", lensWorld.json())
            .put("lens_geometry_source", "Production DockLensBoundsInRootPx semantics: actual painted capsule including parent swell/pull, excluding shadow. Carrier node bounds are recorded separately.")
            .put("content_origin_in_window_px", windowOrigin.json())
            .put("content_origin_on_screen_px", screenOrigin.json())
    }

    private var surface: OpticalCaptureWindow? = null
    private var sourceCoordinates: LayoutCoordinates? = null
    private var hostCoordinates: LayoutCoordinates? = null
    private var density = 1f
    private var fontScale = 1f
    private var systemFontScale = 1f
    private var selectedIndex = 0
    private var pointerDown = false
    private val selectionEvents = ConcurrentLinkedQueue<JSONObject>()
    private val inputEvents = JSONArray()
    private var heldDownComposeMs: Long? = null
    private var heldDownUptimeMs: Long? = null
    private var spatialBitmaps: Map<SpatialInput, Bitmap> = emptyMap()
    private var localAppearanceInstance: Any? = null
    private var localAppearanceCreates = 0
    private var localAppearanceDisposals = 0
    private var localAppearanceTheme = JSONObject()
    private var spatialDrawnInput: SpatialInput? = null
    private var spatialDrawnStage = -1
    private var spatialDrawnBitmap: Bitmap? = null
    private var spatialDrawSerial = 0L
    private var spatialDrawUptime = 0L
    private val spatialFirstDraws = ConcurrentHashMap<Int, JSONObject>()

    /** Retained REST sequence; deliberately separate from the legacy synthetic REST/HELD test. */
    @Test fun captureRetainedDockLocalAppearance() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Enable with glassDockLocalAppearance=true", arguments.getString("glassDockLocalAppearance") == "true")
        nativeParent = NativeParent.SwiftUI
        val runId = (arguments.getString("glassDockOpticalRunId") ?: System.currentTimeMillis().toString())
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "dock-local-appearance/$runId").apply { check(isDirectory || mkdirs()) }
        val captures = JSONArray()
        val stages = JSONArray()
        val result = JSONObject().put("schema", 1).put("status", "running").put("run_id", runId)
            .put("sdk", Build.VERSION.SDK_INT).put("model", Build.MODEL).put("fingerprint", Build.FINGERPRINT)
            .put("session_origin_uptime_ms", SystemClock.uptimeMillis()).put("session_origin_unix_ms", System.currentTimeMillis())
            .put("scope", "Unchanged production LiquidGlassNavigationBar, GlassHost and GlassBackground; one retained four-item SwiftUI-placement Dock, selected index0; REST only. Capture validity and descriptive body transfer, not native material or motion parity.")
            .put("native_reference", "dock-local-appearance-79;375x667pt at2px/pt; natural bar[0,584,375,83], visible platter[21,584,333,62], first lens[25,588,88.25,54]")
            .put("source_policy", "Direct opaque sRGB ARGB_8888 bitmaps at the actual rounded375x667dp source layout size. Each declared world boundary is independently rounded once using actual density. ImageBitmap drawImage has equal integer source/destination pixel sizes,FilterQuality.None and Bitmap.DENSITY_NONE; no scaling, registration or fitted axes. B/C rounded counts and equality are reported, never forced to match. Outside the fixed world remains the existing opaque#F0F0F5 GlassBackground.")
            .put("clock_policy", "Default production input clock and default Compose test autoAdvance; no LocalDockInputTimeNanos override and no1600ms synthetic settle. Each fixture stageID latches its first Compose draw invocation once, including repeated same-color stages. After confirming that draw, checkpoint budgets start conservatively at the subsequent HWUI-commit return and request >=1000ms and >=3000ms of actual uptime. Mutation, draw invocation and commit clocks are distinct; Compose draw invocation is not a compositor timestamp. Budgets do not establish settling time or adaptation latency.")
            .put("capture_policy", "One committed full Window PixelCopy and one immediate full composited display per checkpoint; no readiness retry or state preference. The existing RGB3/99.5% sampled opaque-pixel criterion and observed systemBars/displayCutout exclusion are evaluated after saving both originals. Mismatches and deadline overshoot remain evidence; readiness failures are asserted only after all14 observations.")
            .put("input_policy", "A→B→C→D→C→B→A; alpha255 throughout; A gray26; B gray230 at y550..667dp on gray26; C gray230 at y200..317dp on gray26; D gray230. No Dock touch, scroll, hold/release or renderer override; showDock true throughout.")
            .put("foreground_limits", "Android Material icons/labels differ from native SF Symbols, synthetic scroll text and fixed top overlay. Native parent hierarchy and unknown material sampling support are not reproduced or inferred. Body probes exclude selected lens, glyphs, labels and rims; no whole-image RGB or classifier claim.")
            .put("stages", stages).put("captures", captures)
        val report = File(directory, "result.json")
        fun save() { report.writeText(result.toString(2)) }
        save()
        val scene = mutableStateOf(Scene(OpticalBackground.fine_grid, dark = true, showDock = true,
            spatialInput = SpatialInput.A))
        try {
            compose.setContent {
                val actualDensity = LocalDensity.current
                SideEffect { systemFontScale = actualDensity.fontScale }
                CompositionLocalProvider(LocalDensity provides Density(actualDensity.density, fontScale = 1f),
                    LocalLayoutDirection provides LayoutDirection.Ltr) { Fixture(scene.value) }
            }
            compose.waitForIdle()
            val activity = checkNotNull(surface)
            assertTrue("Hardware accelerated capture required", activity.root.isHardwareAccelerated)
            val initialGeometry = observeDock(activity)
            validateDock(initialGeometry, rest = true)
            val inputs = exportSpatialInputs(directory, initialGeometry.world.source)
            result.put("density", density).put("font_scale", fontScale).put("system_font_scale", systemFontScale)
                .put("initial_geometry", initialGeometry.json()).put("raw_inputs", inputs)
                .put("raw_B_C_histograms_equal", inputs.getJSONObject(1).getJSONArray("rgba_histogram").toString() ==
                    inputs.getJSONObject(2).getJSONArray("rgba_histogram").toString())
            val identity = localAppearanceState(activity).getJSONObject("identities")
            result.put("retained_identities", identity)
            save()
            val sequence = listOf(SpatialInput.A, SpatialInput.B, SpatialInput.C, SpatialInput.D,
                SpatialInput.C, SpatialInput.B, SpatialInput.A)
            for ((ordinal, input) in sequence.withIndex()) {
                val stage = JSONObject().put("stage", ordinal).put("input", input.name)
                    .put("apply_requested_uptime_ms", SystemClock.uptimeMillis())
                stages.put(stage)
                var appliedUptime = 0L
                compose.runOnIdle {
                    scene.value = scene.value.copy(spatialInput = input, spatialStage = ordinal)
                    appliedUptime = SystemClock.uptimeMillis()
                }
                stage.put("input_mutated_uptime_ms", appliedUptime)
                compose.waitForIdle()
                stage.put("source_commit_requested_uptime_ms", SystemClock.uptimeMillis())
                activity.awaitHwuiCommit()
                val budgetAnchor = SystemClock.uptimeMillis()
                val firstDraw = checkNotNull(spatialFirstDraws[ordinal]) { "Stage$ordinal must draw its raw source before its HWUI commit returns" }
                assertEquals("First source invocation stage input", input.name, firstDraw.getString("input"))
                assertTrue("Confirmed Compose source invocation precedes committed-stage budget anchor",
                    firstDraw.getLong("invocation_returned_uptime_ms") <= budgetAnchor)
                stage.put("source_commit_returned_uptime_ms", budgetAnchor).put("first_source_draw_invocation", firstDraw)
                    .put("budget_anchor_uptime_ms", budgetAnchor)
                    .put("budget_anchor_policy", "HWUI commit return after confirming this stageID's first Compose source draw invocation; no ongoing redraw resets this anchor")
                save()
                for (budgetMs in listOf(1000L, 3000L)) {
                    val remaining = budgetAnchor + budgetMs - SystemClock.uptimeMillis()
                    if (remaining > 0) SystemClock.sleep(remaining)
                    val stem = "%02d-%s-%dms-rest".format(ordinal, input.name, budgetMs)
                    val capture = JSONObject().put("stage", ordinal).put("input", input.name).put("phase", "rest")
                        .put("budget_ms", budgetMs).put("input_mutated_uptime_ms", appliedUptime)
                        .put("budget_anchor_uptime_ms", budgetAnchor).put("deadline_uptime_ms", budgetAnchor + budgetMs)
                    captures.put(capture)
                    capture.put("state_before", localAppearanceState(activity))
                    val requested = SystemClock.uptimeMillis()
                    capture.put("window_capture_requested_uptime_ms", requested)
                        .put("actual_request_elapsed_ms", requested - budgetAnchor)
                        .put("request_elapsed_since_mutation_ms", requested - appliedUptime)
                        .put("request_deadline_overshoot_ms", requested - budgetAnchor - budgetMs)
                    val window = activity.capture()
                    try {
                        capture.put("window_capture_returned_uptime_ms", SystemClock.uptimeMillis())
                            .put("screen_capture_requested_uptime_ms", SystemClock.uptimeMillis())
                        val screen = opticalScreenCapture()
                        try {
                            capture.put("screen_capture_returned_uptime_ms", SystemClock.uptimeMillis())
                            window.saveOpticalPng(File(directory, "$stem-window.png"))
                            screen.saveOpticalPng(File(directory, "$stem-screen.png"))
                            capture.put("window_png", "$stem-window.png").put("screen_png", "$stem-screen.png")
                                .put("state_after", localAppearanceState(activity))
                            val geometry = observeDock(activity)
                            capture.put("geometry", geometry.json()).put("window_geometry", activity.geometry())
                                .put("display_readiness", localAppearanceReadiness(activity, window, screen))
                                .put("body_probes", localAppearanceBodyProbes(window, screen, geometry))
                            save()
                            validateDock(geometry, rest = true)
                            assertBitmapContains("Fixed source in Window", window,
                                geometry.world.source.screenRect.translate(geometry.windowOrigin - geometry.screenOrigin))
                            assertBitmapContains("Fixed source on display", screen, geometry.world.source.screenRect)
                            val compared = capture.getJSONObject("display_readiness").getJSONArray("compared_screen_rect_px")
                            val holder = geometry.holder.screenRect
                            assertTrue("Observed system-bar exclusion keeps the complete Dock holder/shadow room",
                                holder.left >= compared.getDouble(0) && holder.top >= compared.getDouble(1) &&
                                    holder.right <= compared.getDouble(2) && holder.bottom <= compared.getDouble(3))
                            for (field in listOf("state_before", "state_after")) {
                                val state = capture.getJSONObject(field)
                                val observed = state.getJSONObject("identities")
                                for (name in identity.keys()) assertEquals("Retained $name", identity.get(name), observed.get(name))
                                assertEquals("Single retained Dock composition", 1, state.getInt("composition_creates"))
                                assertEquals("Dock has not been disposed", 0, state.getInt("composition_disposals"))
                                assertEquals("Schedule remains selected", 0, state.getInt("selected_index"))
                                assertEquals("Actual drawn source matches this stage", input.name, state.getString("drawn_input"))
                                assertEquals("Actual drawn stageID matches this checkpoint", ordinal, state.getInt("drawn_stage"))
                                assertEquals("Actual drawn bitmap is the exported raw source",
                                    System.identityHashCode(spatialBitmaps.getValue(input)), state.getInt("drawn_bitmap_identity"))
                            }
                        } finally { screen.recycle() }
                    } finally {
                        if (!File(directory, "$stem-window.png").isFile) window.saveOpticalPng(File(directory, "$stem-window.png"))
                        window.recycle()
                    }
                }
                stage.put("leaving_uptime_ms", SystemClock.uptimeMillis())
                save()
            }
            assertEquals("Seven stages with two REST checkpoints", 14, captures.length())
            assertTrue("No input or selection callbacks", !pointerDown && selectionEvents.isEmpty() && inputEvents.length() == 0)
            assertTrue("All retained full Window/display pairs satisfy the unchanged readiness criterion",
                (0 until captures.length()).all { captures.getJSONObject(it).getJSONObject("display_readiness").getBoolean("passed") })
            result.put("status", "passed").put("acceptance", "14 REST observations retain one selected-first Dock and pass actual geometry and Window/display readiness; no appearance-parity conclusion.")
        } catch (failure: Throwable) {
            result.put("status", "failed").put("exception", "${failure.javaClass.name}: ${failure.message}")
            throw failure
        } finally { save() }
    }

    @Test fun captureNativeDockOpticalWorld() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Enable with glassDockOpticalWorld=true", arguments.getString("glassDockOpticalWorld") == "true")
        fun selected(argument: String, allowed: List<String>): List<String> {
            val result = arguments.getString(argument)?.split(',')?.map(String::trim)?.distinct() ?: allowed
            require(result.isNotEmpty() && result.all { it in allowed }) { "$argument must select from $allowed" }
            return result
        }
        val backgrounds = selected("glassDockOpticalBackgrounds", OpticalBackground.entries.map { it.name })
            .map(OpticalBackground::valueOf)
        val themes = selected("glassDockOpticalThemes", listOf("light", "dark")).map { it == "dark" }
        val parentArgument = arguments.getString("glassDockOpticalParent") ?: "uikit"
        nativeParent = NativeParent.entries.firstOrNull { it.argument == parentArgument }
            ?: error("glassDockOpticalParent must be uikit or swiftui")
        val runId = (arguments.getString("glassDockOpticalRunId") ?: System.currentTimeMillis().toString())
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "dock-optical-world/$runId").apply { check(isDirectory || mkdirs()) }
        val captures = JSONArray()
        val baselines = JSONArray()
        val observations = JSONArray()
        val result = JSONObject().put("schema", 1).put("status", "running").put("run_id", runId)
            .put("sdk", Build.VERSION.SDK_INT).put("model", Build.MODEL).put("fingerprint", Build.FINGERPRINT)
            .put("scope", "Production LiquidGlassNavigationBar + GlassHost/GlassBackground; fixed 375x667dp source world; initially selected first destination; REST and settled HELD before END. Capture/coordinate validity only, not an appearance or motion parity verdict.")
            .put("native_parent", nativeParent.argument)
            .put("native_reference", if (nativeParent == NativeParent.SwiftUI)
                "swiftui-dock-parent-61; four-item SwiftUI TabView in the production-like navigation parent;375x667pt at2px/pt"
                else "dock-optics-natural-20261113; independent three-item UITabBar;375x667pt at2px/pt")
            .put("native_holder_world_xywh_pt", JSONArray(nativeParent.nativeHolder))
            .put("native_rest_base_world_xywh_pt", JSONArray(listOf(nativeParent.base.left, nativeParent.base.top,
                nativeParent.base.width, nativeParent.base.height)))
            .put("native_rest_lens_world_xywh_pt", JSONArray(listOf(nativeParent.lens.left, nativeParent.lens.top,
                nativeParent.lens.width, nativeParent.lens.height)))
            .put("native_rest_base_world_rect_pt", nativeParent.base.json())
            .put("native_rest_lens_world_rect_pt", nativeParent.lens.json())
            .put("native_target_limits", if (nativeParent == NativeParent.SwiftUI)
                "REST visible base body and first-lens AX bounds from the native four-tab parent. No alpha-mask claim. Native synthetic scroll text and SF Symbols differ; optical comparisons must exclude their influence. Native local material style adapts to background independently of the dark window; this fixture still uses the requested app theme."
                else "Painted base body bright rim and selected lens gray fill from original fine_grid-light REST pixels; excludes outer shadow and does not claim complete alpha support. Holder remains distinct. Base local maximum-jump checks agree29/32; lens32/32.")
            .put("placement_policy", "Translate the declared caller only; retain product21dp side margins,62dp base height and12dp shadow room. Native holder and Android caller are distinct. Runtime carrier width must equal round(caller*density)-2*round(21*density); native-world edges independently retain the existing one-physical-pixel tolerance. Never stretch the source or product to fit screenshots.")
            .put("source_world_policy", "Required375x667dp at the observed GlassBackground top-left; unit local-to-screen axes, no stretch, and fully visible in the real host are required. Font scale1 and LTR are fixture-only native defaults.")
            .put("theme_preset", ThemePresets.KEY_DEFAULT).put("inputs", JSONArray(backgrounds.map { it.metadata() }))
            .put("selected_themes", JSONArray(themes.map { if (it) "dark" else "light" }))
            .put("clock_policy", "Compose clock supplied to production Dock input;1600ms settle before REST/HELD. Clock time and real uptime exported separately. No synthetic clock is claimed to be native real-time motion.")
            .put("capture_policy", "Actual Activity PixelCopy after HWUI commit plus composited display; >=99.5% RGB3 thresholds unchanged. This full-Activity fixture explicitly excludes only actual visible systemBars/displayCutout insets from display readiness and asserts that the complete Dock holder remains inside the compared region. Full Window and display PNGs are saved independently.")
            .put("input_policy", "DOWN at measured REST painted Home lens center, no move, held capture before cleanup CANCEL. No UP is injected and selected-first wrapper state does not prove UP commit.")
            .put("baselines", baselines).put("captures", captures).put("geometry_observations", observations)
            .put("input_events", inputEvents)
        val report = File(directory, "result.json")
        fun save() {
            result.put("density", density).put("font_scale", fontScale).put("system_font_scale", systemFontScale)
                .put("selection_events", JSONArray(selectionEvents.toList()))
            report.writeText(result.toString(2))
        }
        save()
        val scene = mutableStateOf(Scene(backgrounds.first(), themes.first(), false))
        try {
            compose.setContent {
                val actualDensity = LocalDensity.current
                SideEffect { systemFontScale = actualDensity.fontScale }
                CompositionLocalProvider(
                    LocalDensity provides Density(actualDensity.density, fontScale = 1f),
                    LocalLayoutDirection provides LayoutDirection.Ltr,
                    LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L },
                ) { Fixture(scene.value) }
            }
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            for (dark in themes) for (background in backgrounds) {
                val current = Scene(background, dark, false)
                result.put("active_scene", current.name).put("active_stage", "plain-background")
                compose.runOnIdle { scene.value = current; selectionEvents.clear() }
                settle()
                val activity = checkNotNull(surface)
                assertTrue("Hardware accelerated capture required", activity.root.isHardwareAccelerated)
                val world = observeWorld()
                observations.put(JSONObject().put("scene", current.name).put("phase", "background").put("world", world.json()))
                save()
                validateWorld(world)
                val stem = "${current.name}-background"
                val baselineWindow = activity.capture()
                baselineWindow.saveOpticalPng(File(directory, "$stem-window.png"))
                var baselineScreen: Bitmap? = null
                try {
                    val ready = awaitOpticalBackgroundScreen(activity, baselineWindow, density, directory, stem)
                    baselineScreen = ready.bitmap
                    ready.bitmap.saveOpticalPng(File(directory, "$stem-screen.png"))
                    val contentWindow = activity.origin().let { Offset(it[0].toFloat(), it[1].toFloat()) }
                    val contentScreen = activity.screenOrigin().let { Offset(it[0].toFloat(), it[1].toFloat()) }
                    assertBitmapContains("Baseline fixed world in actual Window", baselineWindow,
                        world.source.screenRect.translate(contentWindow - contentScreen))
                    assertBitmapContains("Baseline fixed world on actual display", ready.bitmap, world.source.screenRect)
                    baselines.put(JSONObject().put("scene", current.name).put("window_png", "$stem-window.png")
                        .put("screen_png", "$stem-screen.png").put("background_world", world.json())
                        .put("window_geometry", activity.geometry()).put("display_readiness", ready.verification))
                    result.put("active_stage", "rest")
                    compose.runOnIdle { scene.value = current.copy(showDock = true) }
                    settle()
                    val rest = observeDock(activity)
                    observations.put(JSONObject().put("scene", current.name).put("phase", "rest").put("geometry", rest.json()))
                    save()
                    validateDock(rest, rest = true)
                    capture(current, "rest", rest, directory, captures, baselineWindow, ready.bitmap)
                    save()
                    val node = compose.onNodeWithTag("dock-lens", useUnmergedTree = true)
                    val carrierBounds = node.getUnclippedBoundsInRoot().let {
                        Rect(it.left.value * density, it.top.value * density,
                            it.right.value * density, it.bottom.value * density)
                    }
                    val press = rest.lensRoot.center - carrierBounds.topLeft
                    assertTrue("Measured REST Home lens center must be inside the actual touch carrier",
                        press.x in 0f..carrierBounds.width && press.y in 0f..carrierBounds.height)
                    result.put("active_stage", "held-down")
                    heldDownComposeMs = compose.mainClock.currentTime
                    heldDownUptimeMs = SystemClock.uptimeMillis()
                    val downEvent = event(current, "DOWN").put("status", "requested")
                        .put("carrier_local_px", press.json()).put("painted_lens_root_center_px", rest.lensRoot.center.json())
                    inputEvents.put(downEvent)
                    save()
                    node.performTouchInput { down(press) }
                    pointerDown = true
                    downEvent.put("status", "injected").put("after_injection_uptime_ms", SystemClock.uptimeMillis())
                    try {
                        settle()
                        val held = observeDock(activity)
                        observations.put(JSONObject().put("scene", current.name).put("phase", "held").put("geometry", held.json()))
                        save()
                        validateDock(held, rest = false)
                        assertTrue("HELD reaches the real product's painted-lens expansion",
                            held.lensRoot.width > rest.lensRoot.width + 1f &&
                                held.lensRoot.height > rest.lensRoot.height + 1f)
                        capture(current, "held", held, directory, captures, baselineWindow, ready.bitmap)
                        save()
                    } finally {
                        if (pointerDown) {
                            node.performTouchInput { cancel() }
                            pointerDown = false
                            inputEvents.put(event(current, "CANCEL").put("purpose", "post-capture cleanup; no UP commit evidence"))
                            settle()
                        }
                        save()
                    }
                    assertEquals("Home remains selected after held cleanup", 0, selectedIndex)
                    assertTrue("Selected-first DOWN/CANCEL must not activate any destination", selectionEvents.isEmpty())
                } finally {
                    baselineWindow.recycle()
                    baselineScreen?.recycle()
                }
            }
            assertEquals("Every selected scene has REST and HELD", backgrounds.size * themes.size * 2, captures.length())
            assertEquals("Every selected scene has a plain background baseline", backgrounds.size * themes.size, baselines.length())
            result.put("status", "passed").put("active_stage", "complete")
                .put("acceptance", "All requested captures passed fixed-world, production-coordinate, Home selection, and Window/display readiness checks. Native material/appearance similarity remains an external review.")
        } catch (failure: Throwable) {
            result.put("status", "failed").put("exception", "${failure.javaClass.name}: ${failure.message}")
            throw failure
        } finally {
            compose.mainClock.autoAdvance = true
            save()
        }
    }

    @Composable private fun Fixture(scene: Scene) {
        val view = LocalView.current
        val actualDensity = LocalDensity.current
        SideEffect {
            surface = OpticalCaptureWindow.from(view)
            density = actualDensity.density
            fontScale = actualDensity.fontScale
        }
        SleepyThemeProvider(darkTheme = scene.dark, themeKey = ThemePresets.KEY_DEFAULT) {
            CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                if (scene.spatialInput != null) {
                    val isAppDark = LocalDarkTheme.current
                    val scheme = MaterialTheme.colorScheme
                    SideEffect {
                        localAppearanceTheme = JSONObject().put("requested_dark", scene.dark)
                            .put("local_dark_theme", isAppDark).put("surface_luminance", scheme.surface.luminance())
                            .put("production_dark_policy_result", isAppDark || scheme.surface.luminance() < .5f)
                    }
                }
                GlassHost(Modifier.fillMaxSize(), background = MaterialTheme.colorScheme.background) {
                    GlassBackground(0f, Modifier.fillMaxSize().onGloballyPositioned { hostCoordinates = it }) {
                        Box(Modifier.fillMaxSize().background(Color(0xFFF0F0F5))) {
                            val sourceModifier = Modifier.align(Alignment.TopStart)
                                .requiredSize(375.dp, 667.dp).clipToBounds()
                                .onGloballyPositioned { sourceCoordinates = it }
                            if (scene.spatialInput != null) SpatialBackground(scene.spatialInput, scene.spatialStage, sourceModifier)
                            else OpticalCaptureBackground(scene.background, sourceModifier)
                        }
                    }
                    if (scene.showDock) key(scene.background, scene.dark) {
                        if (scene.spatialInput != null) {
                            val retainedInstance = remember { Any() }
                            DisposableEffect(retainedInstance) {
                                localAppearanceInstance = retainedInstance
                                localAppearanceCreates++
                                onDispose { localAppearanceDisposals++ }
                            }
                        }
                        var selection by remember { mutableIntStateOf(0) }
                        SideEffect { selectedIndex = selection }
                        val host = LocalGlassHost.current
                        Box(Modifier.offset(nativeParent.callerOrigin.x.dp, nativeParent.callerOrigin.y.dp)
                            .width(nativeParent.callerWidth.dp)
                            .testTag("native-dock-optical-holder")) {
                            LiquidGlassNavigationBar(
                                if (nativeParent == NativeParent.SwiftUI) listOf(
                                    PillNavItemSpec(Icons.Filled.CalendarToday, "Schedule"),
                                    PillNavItemSpec(Icons.Filled.WbSunny, "Today"),
                                    PillNavItemSpec(Icons.Filled.GridView, "Manage"),
                                    PillNavItemSpec(Icons.Filled.Person, "Mine"),
                                ) else listOf(PillNavItemSpec(Icons.Filled.Home, "Home"),
                                    PillNavItemSpec(Icons.Filled.Search, "Explore"),
                                    PillNavItemSpec(Icons.Filled.Settings, "Settings")),
                                selection,
                                { index ->
                                    selectionEvents.add(event(scene, "onSelect").put("index", index))
                                    selection = index
                                },
                                Modifier.fillMaxWidth(),
                                checkNotNull(host).backdropAt(LocalGlassDepth.current),
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable private fun SpatialBackground(input: SpatialInput, stage: Int, modifier: Modifier) {
        val actualDensity = LocalDensity.current.density
        val size = IntSize((375f * actualDensity).roundToInt(), (667f * actualDensity).roundToInt())
        val inputs = remember(size, actualDensity) {
            SpatialInput.entries.associateWith { stage ->
                val pixels = IntArray(size.width * size.height) { android.graphics.Color.rgb(stage.gray, stage.gray, stage.gray) }
                if (stage.bandTop != null && stage.bandBottom != null) {
                    val top = (stage.bandTop * actualDensity).roundToInt()
                    val bottom = (stage.bandBottom * actualDensity).roundToInt()
                    for (y in top until bottom) pixels.fill(android.graphics.Color.rgb(230, 230, 230),
                        y * size.width, (y + 1) * size.width)
                }
                Bitmap.createBitmap(pixels, size.width, size.height, Bitmap.Config.ARGB_8888)
                    .apply { density = Bitmap.DENSITY_NONE }
            }
        }
        SideEffect { spatialBitmaps = inputs }
        val bitmap = inputs.getValue(input)
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        Canvas(modifier) {
            val invoked = SystemClock.uptimeMillis()
            check(this.size.width == bitmap.width.toFloat() && this.size.height == bitmap.height.toFloat()) {
                "Direct input pixels must equal the actual source layout; no scaling is allowed"
            }
            drawImage(image, srcSize = IntSize(bitmap.width, bitmap.height),
                dstSize = IntSize(bitmap.width, bitmap.height), filterQuality = FilterQuality.None)
            val returned = SystemClock.uptimeMillis()
            spatialDrawnInput = input
            spatialDrawnStage = stage
            spatialDrawnBitmap = bitmap
            spatialDrawSerial++
            spatialDrawUptime = returned
            spatialFirstDraws.putIfAbsent(stage, JSONObject().put("stage", stage).put("input", input.name)
                .put("bitmap_identity", System.identityHashCode(bitmap))
                .put("size_px", JSONArray(listOf(bitmap.width, bitmap.height)))
                .put("invocation_requested_uptime_ms", invoked).put("invocation_returned_uptime_ms", returned)
                .put("clock_scope", "Compose Canvas drawImage invocation only; not GPU completion, actual presentation time or compositor evidence"))
        }
    }

    private fun exportSpatialInputs(directory: File, source: OpticalLayoutGeometry): JSONArray {
        val reports = JSONArray()
        for (input in SpatialInput.entries) {
            val bitmap = spatialBitmaps.getValue(input)
            assertEquals("Raw source width", source.size.width, bitmap.width)
            assertEquals("Raw source height", source.size.height, bitmap.height)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val histogram = mutableMapOf<Int, Int>()
            for (pixel in pixels) histogram[pixel] = (histogram[pixel] ?: 0) + 1
            assertTrue("Raw input is exactly opaque gray26/230", histogram.keys.all {
                it == android.graphics.Color.rgb(26, 26, 26) || it == android.graphics.Color.rgb(230, 230, 230)
            })
            val brightPixels = if (input.bandTop != null && input.bandBottom != null)
                bitmap.width * ((input.bandBottom * source.density).roundToInt() - (input.bandTop * source.density).roundToInt())
                else if (input == SpatialInput.D) pixels.size else 0
            assertEquals("Exact independently rounded bright-band pixel count", brightPixels,
                histogram[android.graphics.Color.rgb(230, 230, 230)] ?: 0)
            val file = File(directory, "input-${input.name}.png")
            bitmap.saveOpticalPng(file)
            val report = JSONObject().put("input", input.name).put("png", file.name).put("sha256", sha256(file))
                .put("size_px", JSONArray(listOf(bitmap.width, bitmap.height))).put("density", source.density)
                .put("logical_size_dp", JSONArray(listOf(bitmap.width / source.density, bitmap.height / source.density)))
                .put("size_residual_dp", JSONArray(listOf(bitmap.width / source.density - 375f, bitmap.height / source.density - 667f)))
                .put("color_space", bitmap.colorSpace?.name).put("is_srgb", bitmap.colorSpace?.isSrgb)
                .put("rgba_histogram", JSONArray(histogram.entries.sortedBy { it.key }.map {
                    JSONObject().put("rgba", JSONArray(listOf(android.graphics.Color.red(it.key),
                        android.graphics.Color.green(it.key), android.graphics.Color.blue(it.key), android.graphics.Color.alpha(it.key))))
                        .put("pixels", it.value)
                }))
            if (input.bandTop != null && input.bandBottom != null) {
                val top = (input.bandTop * source.density).roundToInt()
                val bottom = (input.bandBottom * source.density).roundToInt()
                report.put("requested_bright_band_world_ltrb_dp", JSONArray(listOf(0, input.bandTop, 375, input.bandBottom)))
                    .put("bright_band_ltrb_px", JSONArray(listOf(0, top, bitmap.width, bottom)))
                    .put("actual_bright_band_world_ltrb_dp", JSONArray(listOf(0f, top / source.density,
                        bitmap.width / source.density, bottom / source.density)))
                    .put("bright_band_edge_residual_dp", JSONArray(listOf(0f, top / source.density - input.bandTop,
                        bitmap.width / source.density - 375f, bottom / source.density - input.bandBottom)))
            }
            reports.put(report)
        }
        return reports
    }

    private fun localAppearanceState(activity: OpticalCaptureWindow): JSONObject {
        val holder = compose.onNodeWithTag("native-dock-optical-holder", useUnmergedTree = true).fetchSemanticsNode()
        val carrier = compose.onNodeWithTag("dock-lens", useUnmergedTree = true).fetchSemanticsNode()
        return compose.runOnIdle {
            JSONObject().put("uptime_ms", SystemClock.uptimeMillis()).put("compose_ms", compose.mainClock.currentTime)
                .put("selected_index", selectedIndex).put("pointer_down", pointerDown)
                .put("selection_event_count", selectionEvents.size).put("composition_creates", localAppearanceCreates)
                .put("composition_disposals", localAppearanceDisposals).put("theme", localAppearanceTheme)
                .put("drawn_input", spatialDrawnInput?.name).put("drawn_stage", spatialDrawnStage)
                .put("drawn_bitmap_identity", System.identityHashCode(spatialDrawnBitmap))
                .put("source_draw_serial", spatialDrawSerial).put("source_last_draw_uptime_ms", spatialDrawUptime)
                .put("source_geometry", OpticalLayoutGeometry.read(checkNotNull(sourceCoordinates), density).json())
                .put("holder_geometry", OpticalLayoutGeometry.read(holder.layoutInfo.coordinates, density).json())
                .put("carrier_geometry", OpticalLayoutGeometry.read(carrier.layoutInfo.coordinates, density).json())
                .put("painted_lens_root_rect_px", carrier.config[DockLensBoundsInRootPx].json())
                .put("identities", JSONObject().put("retained_composition", System.identityHashCode(checkNotNull(localAppearanceInstance)))
                    .put("view", System.identityHashCode(activity.view)).put("root", System.identityHashCode(activity.root))
                    .put("window", System.identityHashCode(checkNotNull(activity.window)))
                    .put("source_coordinates", System.identityHashCode(checkNotNull(sourceCoordinates)))
                    .put("host_coordinates", System.identityHashCode(checkNotNull(hostCoordinates)))
                    .put("holder_semantics", holder.id).put("holder_coordinates", System.identityHashCode(holder.layoutInfo.coordinates))
                    .put("carrier_semantics", carrier.id).put("carrier_coordinates", System.identityHashCode(carrier.layoutInfo.coordinates)))
        }
    }

    /** The old helper retries for frame readiness; these timed observations retain their first display frame. */
    private fun localAppearanceReadiness(activity: OpticalCaptureWindow, window: Bitmap, screen: Bitmap): JSONObject {
        val geometry = activity.geometry()
        val insets = geometry.getJSONObject("root_window_insets_px").getJSONArray("system_bars_and_cutout")
        val rootWindow = geometry.getJSONArray("root_window_origin_px")
        val rootScreen = geometry.getJSONArray("root_screen_origin_px")
        assertEquals("Full Activity Window origin x", 0, rootWindow.getInt(0))
        assertEquals("Full Activity Window origin y", 0, rootWindow.getInt(1))
        val left = insets.getInt(0)
        val top = insets.getInt(1)
        val right = window.width - insets.getInt(2)
        val bottom = window.height - insets.getInt(3)
        val dx = rootScreen.getInt(0) - rootWindow.getInt(0)
        val dy = rootScreen.getInt(1) - rootWindow.getInt(1)
        assertBitmapContains("Entire readiness domain on display", screen,
            Rect((left + dx).toFloat(), (top + dy).toFloat(), (right + dx).toFloat(), (bottom + dy).toFloat()))
        val step = (2 * density).toInt().coerceAtLeast(1)
        var samples = 0
        var matched = 0
        var maxError = 0
        for (y in top until bottom step step) for (x in left until right step step) {
            val a = window.getPixel(x, y)
            if ((a ushr 24) < 254) continue
            val b = screen.getPixel(x + dx, y + dy)
            val error = (0..2).maxOf { abs(((a ushr (it * 8)) and 255) - ((b ushr (it * 8)) and 255)) }
            samples++
            if (error <= 3) matched++
            maxError = maxOf(maxError, error)
        }
        val fraction = if (samples == 0) 0.0 else matched.toDouble() / samples
        return JSONObject().put("passed", samples > 0 && fraction >= .995).put("samples", samples)
            .put("matched_fraction_rgb_tolerance3", fraction).put("max_channel_error", maxError)
            .put("sample_step_px", step).put("exclude_system_bars", true)
            .put("observed_excluded_system_insets_px", insets)
            .put("compared_window_rect_px", JSONArray(listOf(left, top, right, bottom)))
            .put("compared_screen_rect_px", JSONArray(listOf(left + dx, top + dy, right + dx, bottom + dy)))
            .put("policy", "Same opaque alpha>=254,RGB3,>=99.5% criterion and sampled grid as awaitOpticalSurfaceScreen; first full display observation retained without retries. Only observed systemBars/displayCutout excluded.")
    }

    private fun localAppearanceBodyProbes(window: Bitmap, screen: Bitmap, geometry: DockGeometry): JSONArray {
        val probes = JSONArray()
        for (world in listOf(Rect(196f, 592f, 208f, 596f), Rect(244f, 592f, 256f, 596f))) {
            val source = geometry.world.source
            val screenRect = Rect(source.screenOrigin.x + world.left * source.density,
                source.screenOrigin.y + world.top * source.density, source.screenOrigin.x + world.right * source.density,
                source.screenOrigin.y + world.bottom * source.density)
            val probe = JSONObject().put("world_ltrb_dp", world.json())
            for ((name, bitmap, rect) in listOf(Triple("window", window,
                screenRect.translate(geometry.windowOrigin - geometry.screenOrigin)), Triple("composited_display", screen, screenRect))) {
                val box = Rect(rect.left.roundToInt().toFloat(), rect.top.roundToInt().toFloat(),
                    rect.right.roundToInt().toFloat(), rect.bottom.roundToInt().toFloat())
                assertBitmapContains("Raw glyph-free body probe", bitmap, box)
                val colors = mutableMapOf<Int, Int>()
                for (y in box.top.toInt() until box.bottom.toInt()) for (x in box.left.toInt() until box.right.toInt()) {
                    val rgb = bitmap.getPixel(x, y) and 0xFFFFFF
                    colors[rgb] = (colors[rgb] ?: 0) + 1
                }
                probe.put(name, JSONObject().put("box_ltrb_px", box.json()).put("sample_count", colors.values.sum())
                    .put("rgb_histogram", JSONArray(colors.entries.sortedBy { it.key }.map {
                        JSONObject().put("rgb", JSONArray(listOf((it.key ushr 16) and 255, (it.key ushr 8) and 255, it.key and 255)))
                            .put("pixels", it.value)
                    })))
            }
            probes.put(probe)
        }
        return probes
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1600)
        compose.waitForIdle()
    }

    private fun event(scene: Scene, action: String) = JSONObject().put("scene", scene.name).put("action", action)
        .put("compose_ms", compose.mainClock.currentTime).put("wall_uptime_ms", SystemClock.uptimeMillis())

    private fun observeWorld(): World = compose.runOnIdle {
        World(OpticalLayoutGeometry.read(checkNotNull(sourceCoordinates), density),
            OpticalLayoutGeometry.read(checkNotNull(hostCoordinates), density))
    }

    private fun observeDock(activity: OpticalCaptureWindow): DockGeometry {
        val holder = compose.onNodeWithTag("native-dock-optical-holder", useUnmergedTree = true).fetchSemanticsNode()
        val carrier = compose.onNodeWithTag("dock-lens", useUnmergedTree = true).fetchSemanticsNode()
        val lensRoot = carrier.config[DockLensBoundsInRootPx]
        val windowOrigin = activity.origin().let { Offset(it[0].toFloat(), it[1].toFloat()) }
        val screenOrigin = activity.screenOrigin().let { Offset(it[0].toFloat(), it[1].toFloat()) }
        return compose.runOnIdle {
            val coordinates = carrier.layoutInfo.coordinates
            val baseLocal = Rect(0f, 12f * density, coordinates.size.width.toFloat(), 74f * density)
            val corners = listOf(baseLocal.topLeft, baseLocal.topRight, baseLocal.bottomLeft, baseLocal.bottomRight)
            DockGeometry(
                World(OpticalLayoutGeometry.read(checkNotNull(sourceCoordinates), density),
                    OpticalLayoutGeometry.read(checkNotNull(hostCoordinates), density)),
                OpticalLayoutGeometry.read(holder.layoutInfo.coordinates, density),
                OpticalLayoutGeometry.read(coordinates, density), baseLocal,
                corners.map(coordinates::localToRoot), corners.map(coordinates::localToScreen),
                lensRoot, windowOrigin, screenOrigin,
            )
        }
    }

    private fun validateWorld(world: World) {
        assertEquals("Fixed source world width", 375.0 * density, world.source.size.width.toDouble(), .5)
        assertEquals("Fixed source world height", 667.0 * density, world.source.size.height.toDouble(), .5)
        assertTrue("A sufficiently wide/tall actual source host is required",
            world.host.size.width >= world.source.size.width && world.host.size.height >= world.source.size.height)
        assertTrue("Source world starts at the actual GlassBackground top-left",
            (world.source.screenOrigin - world.host.screenOrigin).getDistance() <= .5f)
        assertUnitAxes("source world", world.source)
        assertUnitAxes("GlassBackground host", world.host)
        assertEquals("Fixture default native font scale", 1f, fontScale, 0f)
    }

    private fun validateDock(geometry: DockGeometry, rest: Boolean) {
        validateWorld(geometry.world)
        assertEquals("Caller includes the unchanged product21dp side margins", nativeParent.callerWidth.toDouble() * density,
            geometry.holder.size.width.toDouble(), .5)
        // Compose rounds the caller and each padding edge separately. At density2.975,
        // round(375*d)-2*round(21*d)=992px, while 333*d=990.675px.
        // Assert that exact product layout contract; keep native-world edge checks below.
        val callerWidthPx = (nativeParent.callerWidth * density).roundToInt()
        val sideMarginPx = (21f * density).roundToInt()
        assertEquals("Production carrier equals the rounded caller minus both rounded21dp margins",
            callerWidthPx - 2 * sideMarginPx, geometry.carrier.size.width)
        assertEquals("Production carrier retains62dp base plus12dp top/bottom room", 86.0 * density,
            geometry.carrier.size.height.toDouble(), .5)
        val lens = geometry.lensWorld
        assertTrue("Actual painted lens is finite and remains inside the fixed world",
            listOf(lens.left, lens.top, lens.right, lens.bottom).all { it.isFinite() } &&
                lens.width > 0f && lens.height > 0f && lens.left >= 0f && lens.top >= 0f &&
                lens.right <= 375f && lens.bottom <= 667f)
        assertTrue("Actual base remains inside the fixed world",
            geometry.baseWorld.left >= 0f && geometry.baseWorld.top >= 0f &&
                geometry.baseWorld.right <= 375f && geometry.baseWorld.bottom <= 667f)
        assertEquals("Home remains initially selected", 0, selectedIndex)
        assertTrue("No destination activation before capture", selectionEvents.isEmpty())
        if (rest) {
            assertUnitAxes("REST Dock carrier", geometry.carrier)
            // At most one physical pixel for accumulated dp-to-pixel placement rounding.
            val tolerance = 1f / density + .01f
            assertRect("REST production base in native world", nativeParent.base, geometry.baseWorld, tolerance)
            assertRect("REST painted first lens in native world", nativeParent.lens, geometry.lensWorld, tolerance)
        } else {
            assertTrue("HELD capture occurs during active DOWN before cleanup END", pointerDown)
            assertTrue("HELD has completed1600ms of the declared synthetic clock",
                compose.mainClock.currentTime - checkNotNull(heldDownComposeMs) >= 1600)
        }
    }

    private fun capture(scene: Scene, phase: String, geometry: DockGeometry, directory: File, captures: JSONArray,
                        baselineWindow: Bitmap, baselineScreen: Bitmap) {
        val activity = checkNotNull(surface)
        val stem = "${scene.name}-$phase"
        val window = activity.capture()
        window.saveOpticalPng(File(directory, "$stem-window.png"))
        try {
            val ready = awaitOpticalSurfaceScreen(activity, window, density, directory, stem, excludeSystemBars = true)
            try {
                val compared = ready.verification.getJSONArray("compared_screen_rect_px")
                val holder = geometry.holder.screenRect
                assertTrue("System-owned insets must not exclude any Dock holder or its shadow room",
                    holder.left >= compared.getDouble(0) && holder.top >= compared.getDouble(1) &&
                        holder.right <= compared.getDouble(2) && holder.bottom <= compared.getDouble(3))
                ready.bitmap.saveOpticalPng(File(directory, "$stem-screen.png"))
                assertBitmapContains("Fixed world in actual Window", window,
                    geometry.world.source.screenRect.translate(geometry.windowOrigin - geometry.screenOrigin))
                assertBitmapContains("Fixed world on actual display", ready.bitmap, geometry.world.source.screenRect)
                for ((name, root) in listOf("base" to bounds(geometry.baseRootCorners), "lens" to geometry.lensRoot)) {
                    assertBitmapContains("Painted $name in actual Window", window, root.translate(geometry.windowOrigin))
                    assertBitmapContains("Painted $name on actual display", ready.bitmap, root.translate(geometry.screenOrigin))
                }
                fun metrics(tag: String, root: Rect) = JSONObject().put("tag", tag)
                    .put("actual_root_rect_px", root.json())
                    .put("window", opticalRegionMetrics(window, baselineWindow, root.translate(geometry.windowOrigin)))
                    .put("composited_display", opticalRegionMetrics(ready.bitmap, baselineScreen, root.translate(geometry.screenOrigin)))
                val baseRoot = bounds(geometry.baseRootCorners)
                captures.put(JSONObject().put("scene", scene.name).put("background", scene.background.name)
                    .put("theme", if (scene.dark) "dark" else "light").put("phase", phase)
                    .put("window_png", "$stem-window.png").put("screen_png", "$stem-screen.png")
                    .put("baseline_window_png", "${scene.name}-background-window.png")
                    .put("baseline_screen_png", "${scene.name}-background-screen.png")
                    .put("geometry", geometry.json()).put("window_geometry", activity.geometry())
                    .put("display_readiness", ready.verification)
                    .put("selected_index", selectedIndex).put("pointer_down", pointerDown)
                    .put("compose_ms", compose.mainClock.currentTime).put("wall_uptime_ms", SystemClock.uptimeMillis())
                    .put("down_compose_ms", if (phase == "held") heldDownComposeMs else JSONObject.NULL)
                    .put("down_wall_uptime_ms", if (phase == "held") heldDownUptimeMs else JSONObject.NULL)
                    .put("selection_events", JSONArray(selectionEvents.toList()))
                    .put("measurements", JSONArray(listOf(metrics("dock-base-source-mapped", baseRoot),
                        metrics("dock-painted-lens", geometry.lensRoot)))))
            } finally { ready.bitmap.recycle() }
        } finally { window.recycle() }
    }

    private fun assertBitmapContains(label: String, bitmap: Bitmap, rect: Rect) {
        assertTrue("$label must fit entirely without clamped or skipped image coordinates: $rect in ${bitmap.width}x${bitmap.height}",
            rect.left >= -.5f && rect.top >= -.5f && rect.right <= bitmap.width + .5f &&
                rect.bottom <= bitmap.height + .5f && rect.width > 0f && rect.height > 0f)
    }

    private fun assertUnitAxes(label: String, geometry: OpticalLayoutGeometry) {
        val origin = geometry.screenBasis[0]
        val x = geometry.screenBasis[1] - origin
        val y = geometry.screenBasis[2] - origin
        assertTrue("$label has unit unrotated local-to-screen axes; no stretch",
            abs(x.x - 1f) <= .001f && abs(x.y) <= .001f && abs(y.x) <= .001f && abs(y.y - 1f) <= .001f)
    }

    private fun assertRect(label: String, expected: Rect, actual: Rect, tolerance: Float) {
        assertEquals("$label left", expected.left, actual.left, tolerance)
        assertEquals("$label top", expected.top, actual.top, tolerance)
        assertEquals("$label right", expected.right, actual.right, tolerance)
        assertEquals("$label bottom", expected.bottom, actual.bottom, tolerance)
    }

    private companion object {
        fun Offset.json() = JSONArray(listOf(x, y))
        fun Rect.json() = JSONArray(listOf(left, top, right, bottom))
        fun points(values: List<Offset>) = JSONArray(values.map { it.json() })
        fun bounds(values: List<Offset>) = Rect(values.minOf { it.x }, values.minOf { it.y },
            values.maxOf { it.x }, values.maxOf { it.y })
        fun Rect.inWorld(world: OpticalLayoutGeometry) = Rect(
            (left - world.screenOrigin.x) / world.density, (top - world.screenOrigin.y) / world.density,
            (right - world.screenOrigin.x) / world.density, (bottom - world.screenOrigin.y) / world.density)
    }
}
