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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.ui.component.liquidglass.GlassBackground
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.component.liquidglass.GlassRangeSlider
import com.lingion.sleepy.ui.component.liquidglass.LocalDockInputTimeNanos
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

/** Actual production Range and pointer path; synthetic settled optics, not natural motion. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class NativeRangeOpticalWorldRenderTest {
    @get:Rule val compose = createComposeRule()

    private data class Scene(val background: OpticalBackground, val dark: Boolean, val showRange: Boolean) {
        val name get() = "${background.name}-${if (dark) "dark" else "light"}"
    }
    private data class World(val source: OpticalLayoutGeometry, val host: OpticalLayoutGeometry) {
        fun json() = JSONObject().put("source", source.json()).put("glass_background_host", host.json())
    }
    private data class Thumb(val owner: OpticalLayoutGeometry, val carrier: OpticalLayoutGeometry,
                             val semanticRoot: Rect)
    private data class Geometry(val world: World, val holder: OpticalLayoutGeometry,
                                val holderSemanticRoot: Rect, val track: OpticalLayoutGeometry,
                                val thumbs: Map<String, Thumb>, val windowOrigin: Offset, val screenOrigin: Offset) {
        fun root(layout: OpticalLayoutGeometry) = layout.screenRect.translate(-screenOrigin)
        fun worldRect(layout: OpticalLayoutGeometry) = layout.screenRect.inWorld(world.source)
        val evidenceRoot get(): Rect {
            val room = 6f * world.source.density
            val all = listOf(root(holder)) + thumbs.values.map {
                val r = root(it.carrier)
                Rect(r.left - room, r.top - room, r.right + room, r.bottom + room)
            }
            return Rect(all.minOf { it.left }, all.minOf { it.top }, all.maxOf { it.right }, all.maxOf { it.bottom })
        }
        fun json(): JSONObject {
            val endpoints = JSONObject()
            thumbs.forEach { (endpoint, thumb) ->
                val rect = worldRect(thumb.carrier)
                endpoints.put(endpoint, JSONObject().put("owner", thumb.owner.json()).put("carrier", thumb.carrier.json())
                    .put("carrier_actual_root_rect_px", root(thumb.carrier).json())
                    .put("carrier_semantic_root_rect_px", thumb.semanticRoot.json())
                    .put("carrier_world_rect_dp", rect.json()).put("carrier_world_center_dp", rect.center.json())
                    .put("center_minus_nominal_fractional_rail_center_dp", (rect.center - nominalCenter(endpoint)).json()))
            }
            val rail = worldRect(track)
            return JSONObject().put("background_world", world.json()).put("outer_holder", holder.json())
                .put("holder_actual_root_rect_px", root(holder).json())
                .put("holder_semantic_root_rect_px", holderSemanticRoot.json())
                .put("holder_world_rect_dp", worldRect(holder).json())
                .put("track", track.json()).put("track_world_rect_dp", rail.json())
                .put("track_center_minus_nominal_dp", (rail.center - Offset(187.5f, 396.36f)).json())
                .put("track_width_minus_native_host_rail_dp", rail.width - 294.75f)
                .put("track_height_minus_native_host_rail_dp", rail.height - 6f)
                .put("thumbs", endpoints).put("optical_evidence_root_rect_px", evidenceRoot.json())
                .put("content_origin_in_window_px", windowOrigin.json()).put("content_origin_on_screen_px", screenOrigin.json())
                .put("geometry_source", "Actual attached LayoutCoordinates, all four corners and unit basis; semantic bounds recorded separately. Carrier rectangles exclude shadow and are not alpha contours. Nominal residuals are measured, never corrected.")
        }
    }

    private var surface: OpticalCaptureWindow? = null
    private var sourceCoordinates: LayoutCoordinates? = null
    private var hostCoordinates: LayoutCoordinates? = null
    private var density = 1f
    private var fontScale = 1f
    private var systemFontScale = 1f
    private var requestedPalette = "product"
    private var actualPrimaryRGBA = emptyList<Float>()
    private var currentValue: ClosedFloatingPointRange<Float> = .25f.. .75f
    private var pointerDown = false
    private var downComposeMs: Long? = null
    private var downUptimeMs: Long? = null
    private val valueEvents = ConcurrentLinkedQueue<JSONObject>()
    private val inputEvents = JSONArray()

    @Test fun captureNativeRangeOpticalWorld() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Enable with glassRangeOpticalWorld=true", args.getString("glassRangeOpticalWorld") == "true")
        requestedPalette = args.getString("glassRangeOpticalPalette") ?: "product"
        require(requestedPalette in listOf("native", "product")) { "glassRangeOpticalPalette must be native or product" }
        fun selected(argument: String, allowed: List<String>): List<String> {
            val result = args.getString(argument)?.split(',')?.map(String::trim)?.distinct() ?: allowed
            require(result.isNotEmpty() && result.all { it in allowed }) { "$argument must select from $allowed" }
            return result
        }
        val backgrounds = selected("glassRangeOpticalBackgrounds", OpticalBackground.entries.map { it.name }).map(OpticalBackground::valueOf)
        val themes = selected("glassRangeOpticalThemes", listOf("light", "dark")).map { it == "dark" }
        val runId = (args.getString("glassRangeOpticalRunId") ?: System.currentTimeMillis().toString()).replace(Regex("[^A-Za-z0-9_-]"), "_")
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "range-optical-world/$runId").apply { check(isDirectory || mkdirs()) }
        val captures = JSONArray()
        val baselines = JSONArray()
        val observations = JSONArray()
        val result = JSONObject().put("schema", 1).put("status", "running").put("run_id", runId)
            .put("sdk", Build.VERSION.SDK_INT).put("model", Build.MODEL).put("fingerprint", Build.FINGERPRINT)
            .put("scope", "Production GlassRangeSlider .25.. .75 on ordinary GlassHost/GlassBackground; fixed375x667dp world. REST and separate lower/upper settled center-HELD optics only; no material, contour, natural-motion or release-law acceptance.")
            .put("placement_policy", "One fixed holder origin(26.25,372.36)dp, width322.5dp, production48dp touch height; no corrective translation, scaling, per-endpoint registration, motion override or material override. Export actual rounded endpoint/track geometry in every state.")
            .put("nominal_holder_world_xywh_dp", JSONArray(listOf(26.25, 372.36, 322.5, 48)))
            .put("native_nominal_control_world_xywh_pt", JSONArray(listOf(26.25, 383.61, 322.5, 25.5)))
            .put("native_nominal_host_rail_world_xywh_pt", JSONArray(listOf(40.125, 393.36, 294.75, 6)))
            .put("native_center_policy", "Native actual REST/HELD centers come from the new raw reference audit. Nominal fractional rail centers113.8125/261.1875,396.36 are metadata only; do not replace observed cached-model or presentation centers.")
            .put("rail_policy", "Preserve real Android8dp track versus native host6pt rail; report actual size/center residuals, no rail normalization.")
            .put("source_world_policy", "Required375x667dp at actual GlassBackground top-left; neutral#F0F0F5 outside world, fixture fontScale1 and LTR.")
            .put("clock_policy", "LocalDockInputTimeNanos uses Compose mainClock;1600ms synthetic settling. Real uptime exported separately. No equality with native real-time chronology or natural velocity is claimed.")
            .put("input_policy", "Actual mapped REST endpoint center supplies Compose DOWN, no MOVE/UP, capture while held, cleanup CANCEL. Pair and value callbacks unchanged. GlassRangeSlider exposes no finish callback; this fixture cannot observe one.")
            .put("capture_policy", "Shared75 PixelCopy/display helpers; original>=99.5% RGB3 readiness unchanged. Whole world and holder+both carriers+6dp room must fit actual captures and comparison region.")
            .put("metric_policy", "Shared rectangular color-difference summaries are descriptive, include rail/background, and are not alpha, opacity, refraction or dispersion fits.")
            .put("palette_policy", "product retains the existing SleepyTheme scheme; native is a fixture-only diagnostic changing only primary to sRGB(0,.65,.75,1), matching ReferencePalette.uiTint. Inactive, secondary and other roles may still differ.")
            .put("inputs", JSONArray(backgrounds.map { it.metadata() })).put("selected_themes", JSONArray(themes.map { if (it) "dark" else "light" }))
            .put("baselines", baselines).put("captures", captures).put("geometry_observations", observations).put("input_events", inputEvents)
        val report = File(directory, "result.json")
        fun save() {
            result.put("density", density).put("font_scale", fontScale).put("system_font_scale", systemFontScale)
                .put("requested_palette", requestedPalette).put("actual_primary_float_rgba", JSONArray(actualPrimaryRGBA))
                .put("value_events", JSONArray(valueEvents.toList()))
            report.writeText(result.toString(2))
        }
        val scene = mutableStateOf(Scene(backgrounds.first(), themes.first(), false))
        save()
        try {
            compose.setContent {
                val actual = LocalDensity.current
                SideEffect { systemFontScale = actual.fontScale }
                CompositionLocalProvider(LocalDensity provides Density(actual.density, fontScale = 1f),
                    LocalLayoutDirection provides LayoutDirection.Ltr,
                    LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L }) { Fixture(scene.value) }
            }
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            for (dark in themes) for (background in backgrounds) {
                val current = Scene(background, dark, false)
                result.put("active_scene", current.name).put("active_stage", "background")
                compose.runOnIdle { scene.value = current; valueEvents.clear() }
                settle()
                val activity = checkNotNull(surface)
                assertTrue("Hardware accelerated capture required", activity.root.isHardwareAccelerated)
                val world = observeWorld()
                validateWorld(world)
                val stem = "${current.name}-background"
                val baselineWindow = activity.capture()
                var baselineScreen: Bitmap? = null
                try {
                    baselineWindow.saveOpticalPng(File(directory, "$stem-window.png"))
                    val ready = awaitOpticalBackgroundScreen(activity, baselineWindow, density, directory, stem)
                    baselineScreen = ready.bitmap
                    ready.bitmap.saveOpticalPng(File(directory, "$stem-screen.png"))
                    val windowOrigin = activity.origin().let { Offset(it[0].toFloat(), it[1].toFloat()) }
                    val screenOrigin = activity.screenOrigin().let { Offset(it[0].toFloat(), it[1].toFloat()) }
                    contains("Baseline world in Window", baselineWindow, world.source.screenRect.translate(windowOrigin - screenOrigin))
                    contains("Baseline world on display", ready.bitmap, world.source.screenRect)
                    baselines.put(JSONObject().put("scene", current.name).put("window_png", "$stem-window.png")
                        .put("requested_palette", requestedPalette).put("actual_primary_float_rgba", JSONArray(actualPrimaryRGBA))
                        .put("screen_png", "$stem-screen.png").put("background_world", world.json())
                        .put("window_geometry", activity.geometry()).put("display_readiness", ready.verification))
                    compose.runOnIdle { scene.value = current.copy(showRange = true) }
                    settle()
                    val rest = observe(activity)
                    validate(rest)
                    observations.put(observation(current, "rest", rest))
                    capture(current, "rest", rest, directory, captures, baselineWindow, ready.bitmap)
                    save()
                    for (endpoint in ENDPOINTS) {
                        val before = observe(activity)
                        validate(before)
                        val node = compose.onNodeWithTag(HOLDER_TAG, useUnmergedTree = true)
                        val carrier = before.thumbs.getValue(endpoint).carrier
                        val press = before.root(carrier).center - before.holderSemanticRoot.topLeft
                        assertTrue("Actual endpoint center lies inside touch holder", press.x in 0f..before.holderSemanticRoot.width && press.y in 0f..before.holderSemanticRoot.height)
                        downComposeMs = compose.mainClock.currentTime
                        downUptimeMs = SystemClock.uptimeMillis()
                        val downEvent = event(current, "DOWN").put("endpoint", endpoint).put("status", "requested")
                            .put("holder_semantic_local_px", press.json()).put("actual_center_root_px", before.root(carrier).center.json())
                            .put("actual_center_screen_px", carrier.screenRect.center.json()).put("actual_center_world_dp", before.worldRect(carrier).center.json())
                        inputEvents.put(downEvent)
                        result.put("active_stage", "$endpoint-held")
                        save()
                        node.performTouchInput { down(press) }
                        pointerDown = true
                        downEvent.put("status", "injected").put("after_injection_uptime_ms", SystemClock.uptimeMillis())
                        try {
                            settle()
                            val held = observe(activity)
                            validate(held)
                            assertTrue("HELD remains inside active DOWN", pointerDown)
                            assertTrue("Declared synthetic settle completed", compose.mainClock.currentTime - checkNotNull(downComposeMs) >= 1600)
                            val target = held.root(held.thumbs.getValue(endpoint).carrier)
                            val original = before.root(carrier)
                            assertTrue("Real target carrier expands in both axes", target.width > original.width + 1f && target.height > original.height + 1f)
                            val peer = ENDPOINTS.single { it != endpoint }
                            assertRect("Peer remains at REST", before.root(before.thumbs.getValue(peer).carrier), held.root(held.thumbs.getValue(peer).carrier), 1f)
                            observations.put(observation(current, "$endpoint-held", held))
                            capture(current, "$endpoint-held", held, directory, captures, baselineWindow, ready.bitmap)
                            save()
                        } finally {
                            if (pointerDown) {
                                node.performTouchInput { cancel() }
                                pointerDown = false
                                inputEvents.put(event(current, "CANCEL").put("endpoint", endpoint).put("purpose", "post-capture cleanup; no UP/release evidence"))
                                settle()
                            }
                            save()
                        }
                        val cleanup = observe(activity)
                        validate(cleanup)
                        ENDPOINTS.forEach { assertRect("Both carriers return to REST after CANCEL", rest.root(rest.thumbs.getValue(it).carrier), cleanup.root(cleanup.thumbs.getValue(it).carrier), 1f) }
                        observations.put(observation(current, "$endpoint-post-cancel-rest", cleanup))
                    }
                } finally { baselineWindow.recycle(); baselineScreen?.recycle() }
            }
            assertEquals("REST plus two fresh endpoint holds per scene", backgrounds.size * themes.size * 3, captures.length())
            assertEquals("Plain baseline per scene", backgrounds.size * themes.size, baselines.length())
            result.put("status", "passed").put("active_stage", "complete")
                .put("acceptance", "Fixed-world/capture-readiness, measured geometry, real Range target expansion/peer REST, unchanged pair and no value callback checks passed. Native material, contour and natural dynamics remain separate evaluations.")
        } catch (failure: Throwable) {
            result.put("status", "failed").put("exception", "${failure.javaClass.name}: ${failure.message}")
            throw failure
        } finally { compose.mainClock.autoAdvance = true; save() }
    }

    @Composable private fun Fixture(scene: Scene) {
        val view = LocalView.current
        val actual = LocalDensity.current
        SideEffect { surface = OpticalCaptureWindow.from(view); density = actual.density; fontScale = actual.fontScale }
        SleepyThemeProvider(darkTheme = scene.dark, themeKey = ThemePresets.KEY_DEFAULT) {
            Palette {
                val primary = MaterialTheme.colorScheme.primary
                SideEffect { actualPrimaryRGBA = listOf(primary.red, primary.green, primary.blue, primary.alpha) }
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost(Modifier.fillMaxSize(), background = MaterialTheme.colorScheme.background) {
                        GlassBackground(0f, Modifier.fillMaxSize().onGloballyPositioned { hostCoordinates = it }) {
                            Box(Modifier.fillMaxSize().background(Color(0xFFF0F0F5))) {
                                OpticalCaptureBackground(scene.background, Modifier.align(Alignment.TopStart).requiredSize(375.dp, 667.dp)
                                    .clipToBounds().onGloballyPositioned { sourceCoordinates = it })
                            }
                        }
                        if (scene.showRange) key(scene.background, scene.dark) {
                            var value by remember { mutableStateOf<ClosedFloatingPointRange<Float>>(.25f.. .75f) }
                            SideEffect { currentValue = value }
                            Box(Modifier.offset(26.25.dp, 372.36.dp).width(322.5.dp).testTag(HOLDER_TAG)) {
                                GlassRangeSlider(value, { next ->
                                    valueEvents.add(event(scene, "onValueChange").put("lower", next.start).put("upper", next.endInclusive))
                                    value = next
                                }, Modifier.fillMaxWidth(), valueRange = 0f..1f, steps = 0)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable private fun Palette(content: @Composable () -> Unit) {
        val original = MaterialTheme.colorScheme
        val primary = if (requestedPalette == "native") Color(red = 0f, green = .65f, blue = .75f) else original.primary
        if (requestedPalette == "native") MaterialTheme(colorScheme = original.copy(primary = primary), content = content)
        else content()
    }

    private fun settle() { compose.mainClock.advanceTimeBy(1600); compose.waitForIdle() }
    private fun event(scene: Scene, action: String) = JSONObject().put("scene", scene.name).put("action", action)
        .put("requested_palette", requestedPalette).put("actual_primary_float_rgba", JSONArray(actualPrimaryRGBA))
        .put("compose_ms", compose.mainClock.currentTime).put("wall_uptime_ms", SystemClock.uptimeMillis())
    private fun observation(scene: Scene, phase: String, geometry: Geometry) = JSONObject().put("scene", scene.name)
        .put("requested_palette", requestedPalette).put("actual_primary_float_rgba", JSONArray(actualPrimaryRGBA))
        .put("phase", phase).put("geometry", geometry.json())
    private fun observeWorld(): World = compose.runOnIdle { World(OpticalLayoutGeometry.read(checkNotNull(sourceCoordinates), density), OpticalLayoutGeometry.read(checkNotNull(hostCoordinates), density)) }
    private fun observe(activity: OpticalCaptureWindow): Geometry {
        val holder = compose.onNodeWithTag(HOLDER_TAG, useUnmergedTree = true).fetchSemanticsNode()
        val track = compose.onNodeWithTag("slider-track", useUnmergedTree = true).fetchSemanticsNode()
        val nodes = ENDPOINTS.associateWith { endpoint ->
            val ownerTag = if (endpoint == "lower") "glass-slider-start" else "glass-slider-end"
            compose.onNodeWithTag(ownerTag, useUnmergedTree = true).fetchSemanticsNode() to
                compose.onAllNodes(hasTestTag("glass-slider-thumb-carrier") and hasAnyAncestor(hasTestTag(ownerTag)), useUnmergedTree = true).fetchSemanticsNodes().single()
        }
        val windowOrigin = activity.origin().let { Offset(it[0].toFloat(), it[1].toFloat()) }
        val screenOrigin = activity.screenOrigin().let { Offset(it[0].toFloat(), it[1].toFloat()) }
        return compose.runOnIdle {
            fun read(coordinates: LayoutCoordinates) = OpticalLayoutGeometry.read(coordinates, density)
            Geometry(World(read(checkNotNull(sourceCoordinates)), read(checkNotNull(hostCoordinates))),
                read(holder.layoutInfo.coordinates), holder.boundsInRoot, read(track.layoutInfo.coordinates),
                nodes.mapValues { (_, pair) -> Thumb(read(pair.first.layoutInfo.coordinates), read(pair.second.layoutInfo.coordinates), pair.second.boundsInRoot) }, windowOrigin, screenOrigin)
        }
    }
    private fun validateWorld(world: World) {
        assertEquals("World width", 375.0 * density, world.source.size.width.toDouble(), .5)
        assertEquals("World height", 667.0 * density, world.source.size.height.toDouble(), .5)
        assertTrue("Whole world fits host", world.host.size.width >= world.source.size.width && world.host.size.height >= world.source.size.height)
        assertTrue("World at actual background origin", (world.source.screenOrigin - world.host.screenOrigin).getDistance() <= .5f)
        unitAxes("world", world.source); unitAxes("host", world.host)
        assertEquals("Fixture font scale", 1f, fontScale, 0f)
    }
    private fun validate(g: Geometry) {
        validateWorld(g.world)
        assertEquals("Fixed holder width", 322.5 * density, g.holder.size.width.toDouble(), .5)
        assertEquals("Production touch height", 48.0 * density, g.holder.size.height.toDouble(), .5)
        val holder = g.worldRect(g.holder)
        assertEquals("Fixed holder x with pixel rounding", 26.25f, holder.left, .51f / density)
        assertEquals("Fixed holder y with pixel rounding", 372.36f, holder.top, .51f / density)
        unitAxes("holder", g.holder)
        assertRect("Holder mapping vs semantics", g.root(g.holder), g.holderSemanticRoot, 1f)
        g.thumbs.forEach { (_, thumb) ->
            val rect = g.worldRect(thumb.carrier)
            assertTrue("Whole carrier fits fixed world", rect.left >= 0 && rect.top >= 0 && rect.right <= 375 && rect.bottom <= 667)
            assertRect("Carrier mapping vs semantics", g.root(thumb.carrier), thumb.semanticRoot, 1f)
        }
        assertEquals("Lower pair unchanged", .25f, currentValue.start, 0f)
        assertEquals("Upper pair unchanged", .75f, currentValue.endInclusive, 0f)
        assertTrue("Stationary center DOWN/CANCEL publishes no value", valueEvents.isEmpty())
    }
    private fun capture(scene: Scene, phase: String, g: Geometry, directory: File, captures: JSONArray,
                        baselineWindow: Bitmap, baselineScreen: Bitmap) {
        val activity = checkNotNull(surface)
        val stem = "${scene.name}-$phase"
        val window = activity.capture()
        try {
            window.saveOpticalPng(File(directory, "$stem-window.png"))
            val ready = awaitOpticalSurfaceScreen(activity, window, density, directory, stem, excludeSystemBars = true)
            try {
                val compared = ready.verification.getJSONArray("compared_screen_rect_px")
                val evidence = g.evidenceRoot.translate(g.screenOrigin)
                assertTrue("Comparison keeps full holder, carriers and shadow room", evidence.left >= compared.getDouble(0) && evidence.top >= compared.getDouble(1) && evidence.right <= compared.getDouble(2) && evidence.bottom <= compared.getDouble(3))
                contains("World in Window", window, g.world.source.screenRect.translate(g.windowOrigin - g.screenOrigin))
                contains("World on display", ready.bitmap, g.world.source.screenRect)
                contains("Evidence in Window", window, g.evidenceRoot.translate(g.windowOrigin))
                contains("Evidence on display", ready.bitmap, evidence)
                ready.bitmap.saveOpticalPng(File(directory, "$stem-screen.png"))
                fun metric(tag: String, rect: Rect) = JSONObject().put("tag", tag).put("actual_root_rect_px", rect.json())
                    .put("window", opticalRegionMetrics(window, baselineWindow, rect.translate(g.windowOrigin)))
                    .put("composited_display", opticalRegionMetrics(ready.bitmap, baselineScreen, rect.translate(g.screenOrigin)))
                val metrics = listOf(metric("range-holder", g.root(g.holder)), metric("range-with-shadow-room", g.evidenceRoot)) +
                    g.thumbs.map { (endpoint, thumb) -> metric("range-$endpoint-carrier", g.root(thumb.carrier)) }
                captures.put(JSONObject().put("scene", scene.name).put("background", scene.background.name).put("theme", if (scene.dark) "dark" else "light")
                    .put("requested_palette", requestedPalette).put("actual_primary_float_rgba", JSONArray(actualPrimaryRGBA))
                    .put("phase", phase).put("window_png", "$stem-window.png").put("screen_png", "$stem-screen.png")
                    .put("baseline_window_png", "${scene.name}-background-window.png").put("baseline_screen_png", "${scene.name}-background-screen.png")
                    .put("geometry", g.json()).put("window_geometry", activity.geometry()).put("display_readiness", ready.verification)
                    .put("pair", JSONArray(listOf(currentValue.start, currentValue.endInclusive))).put("pointer_down", pointerDown)
                    .put("compose_ms", compose.mainClock.currentTime).put("wall_uptime_ms", SystemClock.uptimeMillis())
                    .put("down_compose_ms", if (pointerDown) downComposeMs else JSONObject.NULL).put("down_wall_uptime_ms", if (pointerDown) downUptimeMs else JSONObject.NULL)
                    .put("value_events", JSONArray(valueEvents.toList())).put("measurements", JSONArray(metrics)))
            } finally { ready.bitmap.recycle() }
        } finally { window.recycle() }
    }
    private fun contains(label: String, bitmap: Bitmap, rect: Rect) = assertTrue("$label fully fits without clamping: $rect in ${bitmap.width}x${bitmap.height}",
        rect.left >= -.5f && rect.top >= -.5f && rect.right <= bitmap.width + .5f && rect.bottom <= bitmap.height + .5f && rect.width > 0 && rect.height > 0)
    private fun unitAxes(label: String, g: OpticalLayoutGeometry) {
        val x = g.screenBasis[1] - g.screenBasis[0]; val y = g.screenBasis[2] - g.screenBasis[0]
        assertTrue("$label keeps unit axes", abs(x.x - 1) <= .001f && abs(x.y) <= .001f && abs(y.x) <= .001f && abs(y.y - 1) <= .001f)
    }
    private fun assertRect(label: String, a: Rect, b: Rect, tolerance: Float) {
        assertEquals("$label left", a.left, b.left, tolerance); assertEquals("$label top", a.top, b.top, tolerance)
        assertEquals("$label right", a.right, b.right, tolerance); assertEquals("$label bottom", a.bottom, b.bottom, tolerance)
    }
    private companion object {
        const val HOLDER_TAG = "native-range-optical-holder"
        val ENDPOINTS = listOf("lower", "upper")
        fun nominalCenter(endpoint: String) = Offset(if (endpoint == "lower") 113.8125f else 261.1875f, 396.36f)
        fun Offset.json() = JSONArray(listOf(x, y))
        fun Rect.json() = JSONArray(listOf(left, top, right, bottom))
        fun Rect.inWorld(world: OpticalLayoutGeometry) = Rect((left - world.screenOrigin.x) / world.density,
            (top - world.screenOrigin.y) / world.density, (right - world.screenOrigin.x) / world.density, (bottom - world.screenOrigin.y) / world.density)
    }
}
