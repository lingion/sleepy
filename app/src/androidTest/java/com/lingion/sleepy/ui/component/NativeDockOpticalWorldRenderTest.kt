package com.lingion.sleepy.ui.component

import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
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

    private data class Scene(val background: OpticalBackground, val dark: Boolean, val showDock: Boolean) {
        val name get() = "${background.name}-${if (dark) "dark" else "light"}"
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
                GlassHost(Modifier.fillMaxSize(), background = MaterialTheme.colorScheme.background) {
                    GlassBackground(0f, Modifier.fillMaxSize().onGloballyPositioned { hostCoordinates = it }) {
                        Box(Modifier.fillMaxSize().background(Color(0xFFF0F0F5))) {
                            OpticalCaptureBackground(scene.background, Modifier.align(Alignment.TopStart)
                                .requiredSize(375.dp, 667.dp).clipToBounds()
                                .onGloballyPositioned { sourceCoordinates = it })
                        }
                    }
                    if (scene.showDock) key(scene.background, scene.dark) {
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
