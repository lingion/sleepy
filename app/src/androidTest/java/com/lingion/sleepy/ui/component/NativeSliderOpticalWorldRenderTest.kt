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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
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
import com.lingion.sleepy.ui.component.liquidglass.GlassSlider
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

/**
 * Opt-in REST/settled-HELD optical evidence at the current native SwiftUI Slider anchor.
 * A single bounded fixture translation aligns background sampling, not product geometry.
 * Production rendering and pointer handling remain unchanged; this is not natural motion.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class NativeSliderOpticalWorldRenderTest {
    @get:Rule val compose = createComposeRule()

    private data class Scene(val background: OpticalBackground, val dark: Boolean, val showSlider: Boolean) {
        val name get() = "${background.name}-${if (dark) "dark" else "light"}"
    }

    private data class World(val source: OpticalLayoutGeometry, val host: OpticalLayoutGeometry) {
        fun json() = JSONObject().put("source", source.json()).put("glass_background_host", host.json())
            .put("requested_size_dp", JSONArray(listOf(375, 667)))
            .put("outside_world", "opaque neutral #F0F0F5 recorded in the same GlassBackground")
    }

    private data class SliderGeometry(
        val world: World,
        val holder: OpticalLayoutGeometry,
        val owner: OpticalLayoutGeometry,
        val carrier: OpticalLayoutGeometry,
        val holderSemanticRoot: Rect,
        val carrierSemanticRoot: Rect,
        val windowOrigin: Offset,
        val screenOrigin: Offset,
    ) {
        val holderRoot get() = holder.screenRect.translate(-screenOrigin)
        val ownerRoot get() = owner.screenRect.translate(-screenOrigin)
        val carrierRoot get() = carrier.screenRect.translate(-screenOrigin)
        val carrierWorld get() = carrier.screenRect.inWorld(world.source)
        val evidenceRoot get() = Rect(
            minOf(holderRoot.left, carrierRoot.left - 6f * world.source.density),
            minOf(holderRoot.top, carrierRoot.top - 6f * world.source.density),
            maxOf(holderRoot.right, carrierRoot.right + 6f * world.source.density),
            maxOf(holderRoot.bottom, carrierRoot.bottom + 6f * world.source.density),
        )

        fun json() = JSONObject().put("background_world", world.json())
            .put("outer_holder", holder.json()).put("isotropic_thumb_owner", owner.json())
            .put("affine_visual_carrier", carrier.json())
            .put("holder_actual_root_rect_px", holderRoot.json())
            .put("holder_semantic_root_rect_px", holderSemanticRoot.json())
            .put("owner_actual_root_rect_px", ownerRoot.json())
            .put("visual_carrier_actual_root_rect_px", carrierRoot.json())
            .put("visual_carrier_semantic_root_rect_px", carrierSemanticRoot.json())
            .put("visual_carrier_world_rect_dp", carrierWorld.json())
            .put("visual_carrier_world_center_dp", carrierWorld.center.json())
            .put("optical_evidence_root_rect_px", evidenceRoot.json())
            .put("geometry_source", "Actual attached LayoutCoordinates map all four corners and unit basis through the production transform; semantic boundsInRoot are recorded separately. Visual carrier bounds exclude shadow and are not an alpha-mask or a claim of native geometry parity. Six dp around the carrier, union the whole touch holder, is retained as capture/readiness room.")
            .put("content_origin_in_window_px", windowOrigin.json())
            .put("content_origin_on_screen_px", screenOrigin.json())
    }

    private var surface: OpticalCaptureWindow? = null
    private var sourceCoordinates: LayoutCoordinates? = null
    private var hostCoordinates: LayoutCoordinates? = null
    private var density = 1f
    private var fontScale = 1f
    private var systemFontScale = 1f
    private var currentValue = .25f
    private var pointerDown = false
    private var heldDownComposeMs: Long? = null
    private var heldDownUptimeMs: Long? = null
    private val fixedTranslationPx = mutableStateOf(Offset.Zero)
    private val valueEvents = ConcurrentLinkedQueue<JSONObject>()
    private val finishEvents = ConcurrentLinkedQueue<JSONObject>()
    private val inputEvents = JSONArray()

    @Test fun captureNativeSliderOpticalWorld() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Enable with glassSliderOpticalWorld=true", arguments.getString("glassSliderOpticalWorld") == "true")
        fun selected(argument: String, allowed: List<String>): List<String> {
            val result = arguments.getString(argument)?.split(',')?.map(String::trim)?.distinct() ?: allowed
            require(result.isNotEmpty() && result.all { it in allowed }) { "$argument must select from $allowed" }
            return result
        }
        val backgrounds = selected("glassSliderOpticalBackgrounds", OpticalBackground.entries.map { it.name })
            .map(OpticalBackground::valueOf)
        val themes = selected("glassSliderOpticalThemes", listOf("light", "dark")).map { it == "dark" }
        val runId = (arguments.getString("glassSliderOpticalRunId") ?: System.currentTimeMillis().toString())
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "slider-optical-world/$runId").apply { check(isDirectory || mkdirs()) }
        val captures = JSONArray()
        val baselines = JSONArray()
        val observations = JSONArray()
        val calibration = JSONObject().put("status", "pending").put("maximum_correction_per_axis_dp", 1)
            .put("purpose", "Optical material sampling alignment only; excludes native/product geometry acceptance.")
            .put("policy", "Derive one fixture-only translation from the first raw REST visual center; each axis must be within1dp or abort. Reuse it unchanged across backgrounds, themes, HELD and post-CANCEL REST. Never retune by theme, value or state; never move or stretch the backing world.")
        val result = JSONObject().put("schema", 1).put("status", "running").put("run_id", runId)
            .put("sdk", Build.VERSION.SDK_INT).put("model", Build.MODEL).put("fingerprint", Build.FINGERPRINT)
            .put("scope", "Production GlassSlider + GlassHost/GlassBackground on ordinary backing, value.25, width335dp; fixed375x667dp source world. REST and settled center-HELD before cleanup CANCEL. Optical comparison only; capture/coordinate validity is not material, geometry or natural-motion parity.")
            .put("native_reference", "Measured source72 iOS26.5 SwiftUI Slider held signals across all8 verified optical captures:375x667pt source world; actual targetFramePt(x20,y414,w335,h31), actualDown(113.5,430)pt, selected value25%. Observer screenshots start at least0.65s after actualDown. Older AX-normalized holds are a distinct geometry and must not be relabeled.")
            .put("native_nominal_control_world_xywh_pt", JSONArray(listOf(20, 414, 335, 31)))
            .put("native_quarter_thumb_center_world_pt", NATIVE_CENTER.json())
            .put("android_uncalibrated_touch_holder_world_xywh_dp", JSONArray(listOf(20, 406, 335, 48)))
            .put("placement_policy", "Unmodified production335dp width and48dp touch height; nominal origin(20,406)dp. Production37dp REST thumb yields ideal local quarter center93dp rather than native93.5pt. Export the first actual residual before one bounded translation; preserve production size, rail, ticks, shape and all subsequent state changes.")
            .put("source_world_policy", "Required375x667dp at the observed GlassBackground top-left, fully visible with unit local-to-screen axes. Fixture-only fontScale1 and LTR. Ordinary backing only; no glass parent panel.")
            .put("theme_preset", ThemePresets.KEY_DEFAULT).put("inputs", JSONArray(backgrounds.map { it.metadata() }))
            .put("selected_themes", JSONArray(themes.map { if (it) "dark" else "light" }))
            .put("clock_policy", "Compose clock supplied to production Slider input through LocalDockInputTimeNanos;1600ms settle before REST/HELD. Real uptime exported independently. This synthetic clock demonstrates stable optical states and is not native real-time dynamics.")
            .put("capture_policy", "Actual Activity PixelCopy after HWUI commit plus composited display; existing>=99.5% RGB3 readiness threshold unchanged. Exclude only actual systemBars/displayCutout for control-screen readiness and require the entire holder, carrier and6dp carrier shadow room inside the compared region. Save full Window/display PNGs independently.")
            .put("input_policy", "DOWN at the actual mapped REST visual-carrier center, no MOVE or UP, capture while held; cleanup CANCEL afterwards. Quarter value and empty callbacks required throughout. No release dynamics or UP commit evidence.")
            .put("calibration", calibration).put("baselines", baselines).put("captures", captures)
            .put("geometry_observations", observations).put("input_events", inputEvents)
        val report = File(directory, "result.json")
        fun save() {
            result.put("density", density).put("font_scale", fontScale).put("system_font_scale", systemFontScale)
                .put("fixed_translation_px", fixedTranslationPx.value.json())
                .put("value_events", JSONArray(valueEvents.toList())).put("finish_events", JSONArray(finishEvents.toList()))
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
                compose.runOnIdle { scene.value = current; valueEvents.clear(); finishEvents.clear() }
                settle()
                val activity = checkNotNull(surface)
                assertTrue("Hardware accelerated capture required", activity.root.isHardwareAccelerated)
                val world = observeWorld()
                observations.put(JSONObject().put("scene", current.name).put("phase", "background").put("world", world.json()))
                validateWorld(world)
                save()
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
                    compose.runOnIdle { scene.value = current.copy(showSlider = true) }
                    settle()
                    if (calibration.getString("status") == "pending") {
                        val raw = observeSlider(activity)
                        val residual = NATIVE_CENTER - raw.carrierWorld.center
                        calibration.put("source_scene", current.name).put("raw_geometry", raw.json())
                            .put("raw_center_world_dp", raw.carrierWorld.center.json())
                            .put("target_minus_raw_center_dp", residual.json())
                        save()
                        assertTrue("Optical alignment must not normalize a product layout regression: $residual",
                            abs(residual.x) <= 1f && abs(residual.y) <= 1f)
                        compose.runOnIdle { fixedTranslationPx.value = residual * density }
                        calibration.put("status", "fixed").put("translation_px", fixedTranslationPx.value.json())
                            .put("translation_dp", residual.json())
                        settle()
                    }
                    val rest = observeSlider(activity)
                    observations.put(observation(current, "rest", rest))
                    validateSlider(rest, held = false)
                    assertEquals("Optical sample REST center x after one fixed translation", 113.5f, rest.carrierWorld.center.x, .05f)
                    assertEquals("Optical sample REST center y after one fixed translation", 430f, rest.carrierWorld.center.y, .05f)
                    capture(current, "rest", rest, directory, captures, baselineWindow, ready.bitmap)
                    save()
                    val node = compose.onNodeWithTag(HOLDER_TAG, useUnmergedTree = true)
                    // Injection is relative to the semantics touch bounds; actual mapped geometry
                    // supplies the target, and the two coordinate paths are exported separately.
                    val press = rest.carrierRoot.center - rest.holderSemanticRoot.topLeft
                    assertTrue("Measured REST thumb center lies inside the actual touch holder",
                        press.x in 0f..rest.holderSemanticRoot.width && press.y in 0f..rest.holderSemanticRoot.height)
                    heldDownComposeMs = compose.mainClock.currentTime
                    heldDownUptimeMs = SystemClock.uptimeMillis()
                    result.put("active_stage", "held-down")
                    val downEvent = event(current, "DOWN").put("status", "requested")
                        .put("holder_semantic_local_px", press.json())
                        .put("actual_visual_center_root_px", rest.carrierRoot.center.json())
                        .put("actual_visual_center_screen_px", rest.carrier.screenRect.center.json())
                        .put("fixed_translation_px", fixedTranslationPx.value.json())
                    inputEvents.put(downEvent)
                    save()
                    node.performTouchInput { down(press) }
                    pointerDown = true
                    downEvent.put("status", "injected").put("after_injection_uptime_ms", SystemClock.uptimeMillis())
                    try {
                        settle()
                        val held = observeSlider(activity)
                        observations.put(observation(current, "held", held))
                        validateSlider(held, held = true)
                        assertTrue("HELD reaches the real production visual-carrier expansion",
                            held.carrierRoot.width > rest.carrierRoot.width + 1f &&
                                held.carrierRoot.height > rest.carrierRoot.height + 1f)
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
                    val cleanup = observeSlider(activity)
                    observations.put(observation(current, "post-cancel-rest", cleanup))
                    validateSlider(cleanup, held = false)
                    assertEquals("Fixed optical alignment persists after CANCEL x", rest.carrierWorld.center.x,
                        cleanup.carrierWorld.center.x, .05f)
                    assertEquals("Fixed optical alignment persists after CANCEL y", rest.carrierWorld.center.y,
                        cleanup.carrierWorld.center.y, .05f)
                } finally {
                    baselineWindow.recycle()
                    baselineScreen?.recycle()
                }
            }
            assertEquals("Every selected scene has REST and HELD", backgrounds.size * themes.size * 2, captures.length())
            assertEquals("Every selected scene has a plain background baseline", backgrounds.size * themes.size, baselines.length())
            result.put("status", "passed").put("active_stage", "complete")
                .put("acceptance", "Requested optical evidence passed fixed-world, capture-readiness, one bounded fixed sampling translation, quarter-value and no-callback checks. Native material, shape, geometry and natural-motion parity remain unverified by this fixture.")
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
                    if (scene.showSlider) key(scene.background, scene.dark) {
                        var value by remember { mutableFloatStateOf(.25f) }
                        SideEffect { currentValue = value }
                        Box(Modifier.offset(20.dp, 406.dp).width(335.dp).graphicsLayer {
                            translationX = fixedTranslationPx.value.x
                            translationY = fixedTranslationPx.value.y
                        }.testTag(HOLDER_TAG)) {
                            GlassSlider(value, { next ->
                                valueEvents.add(event(scene, "onValueChange").put("value", next))
                                value = next
                            }, Modifier.fillMaxWidth(), onValueChangeFinished = {
                                finishEvents.add(event(scene, "onValueChangeFinished"))
                            })
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

    private fun observation(scene: Scene, phase: String, geometry: SliderGeometry) =
        JSONObject().put("scene", scene.name).put("phase", phase).put("geometry", geometry.json())
            .put("fixed_translation_px", fixedTranslationPx.value.json())
            .put("center_minus_native_anchor_dp", (geometry.carrierWorld.center - NATIVE_CENTER).json())

    private fun observeWorld(): World = compose.runOnIdle {
        World(OpticalLayoutGeometry.read(checkNotNull(sourceCoordinates), density),
            OpticalLayoutGeometry.read(checkNotNull(hostCoordinates), density))
    }

    private fun observeSlider(activity: OpticalCaptureWindow): SliderGeometry {
        val holder = compose.onNodeWithTag(HOLDER_TAG, useUnmergedTree = true).fetchSemanticsNode()
        val owner = compose.onNodeWithTag("glass-slider-end", useUnmergedTree = true).fetchSemanticsNode()
        val carrier = compose.onNodeWithTag("glass-slider-thumb-carrier", useUnmergedTree = true).fetchSemanticsNode()
        val windowOrigin = activity.origin().let { Offset(it[0].toFloat(), it[1].toFloat()) }
        val screenOrigin = activity.screenOrigin().let { Offset(it[0].toFloat(), it[1].toFloat()) }
        return compose.runOnIdle {
            SliderGeometry(
                World(OpticalLayoutGeometry.read(checkNotNull(sourceCoordinates), density),
                    OpticalLayoutGeometry.read(checkNotNull(hostCoordinates), density)),
                OpticalLayoutGeometry.read(holder.layoutInfo.coordinates, density),
                OpticalLayoutGeometry.read(owner.layoutInfo.coordinates, density),
                OpticalLayoutGeometry.read(carrier.layoutInfo.coordinates, density),
                holder.boundsInRoot, carrier.boundsInRoot, windowOrigin, screenOrigin,
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

    private fun validateSlider(geometry: SliderGeometry, held: Boolean) {
        validateWorld(geometry.world)
        assertEquals("Unmodified production slider width", 335.0 * density, geometry.holder.size.width.toDouble(), .5)
        assertEquals("Unmodified production touch height", 48.0 * density, geometry.holder.size.height.toDouble(), .5)
        assertUnitAxes("Translated slider holder", geometry.holder)
        assertTrue("Actual visual carrier fits inside the fixed world",
            geometry.carrierWorld.left >= 0f && geometry.carrierWorld.top >= 0f &&
                geometry.carrierWorld.right <= 375f && geometry.carrierWorld.bottom <= 667f)
        assertRect("Mapped carrier vs semantic bounds (independent coordinate paths)",
            geometry.carrierRoot, geometry.carrierSemanticRoot, 1f)
        assertRect("Mapped holder vs semantic bounds (independent coordinate paths)",
            geometry.holderRoot, geometry.holderSemanticRoot, 1f)
        assertEquals("Center hold preserves quarter value", .25f, currentValue, 0f)
        assertTrue("Center DOWN/CANCEL must not publish a value", valueEvents.isEmpty())
        assertTrue("Center DOWN/CANCEL must not finish an interaction", finishEvents.isEmpty())
        if (held) {
            assertTrue("HELD capture occurs during active DOWN before cleanup CANCEL", pointerDown)
            assertTrue("HELD completes1600ms of the declared synthetic clock",
                compose.mainClock.currentTime - checkNotNull(heldDownComposeMs) >= 1600)
        }
    }

    private fun capture(scene: Scene, phase: String, geometry: SliderGeometry, directory: File, captures: JSONArray,
                        baselineWindow: Bitmap, baselineScreen: Bitmap) {
        val activity = checkNotNull(surface)
        val stem = "${scene.name}-$phase"
        val window = activity.capture()
        window.saveOpticalPng(File(directory, "$stem-window.png"))
        try {
            val ready = awaitOpticalSurfaceScreen(activity, window, density, directory, stem, excludeSystemBars = true)
            try {
                val compared = ready.verification.getJSONArray("compared_screen_rect_px")
                val evidence = geometry.evidenceRoot.translate(geometry.screenOrigin)
                assertTrue("System-owned insets must not exclude holder, carrier or6dp shadow room",
                    evidence.left >= compared.getDouble(0) && evidence.top >= compared.getDouble(1) &&
                        evidence.right <= compared.getDouble(2) && evidence.bottom <= compared.getDouble(3))
                ready.bitmap.saveOpticalPng(File(directory, "$stem-screen.png"))
                assertBitmapContains("Fixed world in actual Window", window,
                    geometry.world.source.screenRect.translate(geometry.windowOrigin - geometry.screenOrigin))
                assertBitmapContains("Fixed world on actual display", ready.bitmap, geometry.world.source.screenRect)
                assertBitmapContains("Entire optical region in actual Window", window, geometry.evidenceRoot.translate(geometry.windowOrigin))
                assertBitmapContains("Entire optical region on actual display", ready.bitmap, evidence)
                fun metrics(tag: String, root: Rect) = JSONObject().put("tag", tag)
                    .put("actual_root_rect_px", root.json())
                    .put("window", opticalRegionMetrics(window, baselineWindow, root.translate(geometry.windowOrigin)))
                    .put("composited_display", opticalRegionMetrics(ready.bitmap, baselineScreen, root.translate(geometry.screenOrigin)))
                captures.put(JSONObject().put("scene", scene.name).put("background", scene.background.name)
                    .put("theme", if (scene.dark) "dark" else "light").put("phase", phase)
                    .put("window_png", "$stem-window.png").put("screen_png", "$stem-screen.png")
                    .put("baseline_window_png", "${scene.name}-background-window.png")
                    .put("baseline_screen_png", "${scene.name}-background-screen.png")
                    .put("geometry", geometry.json()).put("window_geometry", activity.geometry())
                    .put("display_readiness", ready.verification).put("fixed_translation_px", fixedTranslationPx.value.json())
                    .put("value", currentValue).put("pointer_down", pointerDown)
                    .put("compose_ms", compose.mainClock.currentTime).put("wall_uptime_ms", SystemClock.uptimeMillis())
                    .put("down_compose_ms", if (phase == "held") heldDownComposeMs else JSONObject.NULL)
                    .put("down_wall_uptime_ms", if (phase == "held") heldDownUptimeMs else JSONObject.NULL)
                    .put("value_events", JSONArray(valueEvents.toList())).put("finish_events", JSONArray(finishEvents.toList()))
                    .put("measurements", JSONArray(listOf(metrics("slider-touch-holder", geometry.holderRoot),
                        metrics("slider-actual-affine-carrier", geometry.carrierRoot),
                        metrics("slider-with-shadow-room", geometry.evidenceRoot)))))
            } finally { ready.bitmap.recycle() }
        } finally { window.recycle() }
    }

    private fun assertBitmapContains(label: String, bitmap: Bitmap, rect: Rect) {
        assertTrue("$label must fit entirely without clamped or skipped coordinates: $rect in ${bitmap.width}x${bitmap.height}",
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
        const val HOLDER_TAG = "native-slider-optical-holder"
        val NATIVE_CENTER = Offset(113.5f, 430f)
        fun Offset.json() = JSONArray(listOf(x, y))
        fun Rect.json() = JSONArray(listOf(left, top, right, bottom))
        fun Rect.inWorld(world: OpticalLayoutGeometry) = Rect(
            (left - world.screenOrigin.x) / world.density, (top - world.screenOrigin.y) / world.density,
            (right - world.screenOrigin.x) / world.density, (bottom - world.screenOrigin.y) / world.density)
    }
}
