package com.lingion.sleepy.ui.component

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.ui.component.liquidglass.GlassBackground
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.component.liquidglass.GlassSlider
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
import java.io.Writer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Product controls only: committed API 36 HWUI output, without replacement drawing.
 * Opt in with instrumentation arguments sliderCapture=true and optional sliderRunId.
 * Outputs: target debug app external files / unified-slider-appearance / run ID.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 36)
class UnifiedSliderAppearanceCaptureTest {
    @get:Rule val compose = createComposeRule()

    private data class Scene(val style: UiStyle, val dark: Boolean, val changed: Boolean = false) {
        val styleName get() = if (style == UiStyle.Classic) "Material Design" else "Liquid Glass"
        val phase get() = if (changed) "changed-ghost" else "initial"
        val name get() = "${style.key}-${if (dark) "dark" else "light"}-$phase"
    }

    private lateinit var root: View
    private lateinit var contentView: View
    private lateinit var window: Window
    private var density = 1f
    private var palette = JSONObject()

    @Test fun captureBothStylesAndThemesWithBaselineGhosts() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Pass -e sliderCapture true to export the eight GPU reference screenshots",
            arguments.getString("sliderCapture").equals("true", ignoreCase = true))
        assumeTrue("This fixture records the API 36 GPU reference", Build.VERSION.SDK_INT == 36)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val runId = (arguments.getString("sliderRunId")
            ?: System.currentTimeMillis().toString()).replace(Regex("[^A-Za-z0-9_-]"), "_")
        val directory = File(context.getExternalFilesDir(null), "unified-slider-appearance/$runId")
        assertTrue("Create screenshot output directory", directory.isDirectory || directory.mkdirs())
        var scene by mutableStateOf(Scene(UiStyle.Classic, dark = false))
        compose.setContent { SliderFixture(scene) }
        compose.waitForIdle()
        assertTrue("GPU capture requires a hardware accelerated window", root.isHardwareAccelerated)

        val captures = JSONArray()
        val geometryFailures = JSONArray()
        val manifest = JSONObject()
            .put("schema", 2).put("run_id", runId).put("package", context.packageName)
            .put("sdk", Build.VERSION.SDK_INT).put("model", Build.MODEL)
            .put("build_fingerprint", Build.FINGERPRINT).put("density", density)
            .put("theme_preset", ThemePresets.KEY_DEFAULT)
            .put("renderer", "Product Compose controls; wait for HWUI frame commit, then PixelCopy window")
            .put("dimensions_source", "Unclipped semantics layout bounds, in dp and px; no inferred painted dimensions")
            .put("untagged_drawing", "Canvas-only track, dots or ghosts without product tags remain visible in PNG; they are not assigned invented bounds")
            .put("requested_thumb_dp", JSONObject()
                .put("shape", "horizontal oval")
                .put("single", JSONObject().put("width", 37).put("height", 24))
                .put("range", JSONObject().put("width", 27.75).put("height", 18))
                .put("range_scale", .75).put("aspect_ratio", 37.0 / 24.0))
            .put("baseline", JSONObject().put("single", .30).put("range_start", 3).put("range_end", 8).put("hue", 72))
            .put("changed", JSONObject().put("single", .70).put("range_start", 5).put("range_end", 10).put("hue", 220))
            .put("captures", captures).put("geometry_failures", geometryFailures)

        File(directory, "dimensions.csv").bufferedWriter().use { csv ->
            csv.appendLine("scene,style,theme,phase,control,tag,progress,left_dp,top_dp,width_dp,height_dp,left_px,top_px,width_px,height_px,png")
            for (style in listOf(UiStyle.Classic, UiStyle.LiquidGlass)) {
                for (dark in listOf(false, true)) {
                    // Keep the same control composition between initial and changed so
                    // the second capture exercises actual controlled-value updates.
                    for (changed in listOf(false, true)) {
                        val current = Scene(style, dark, changed)
                        compose.runOnIdle { scene = current }
                        compose.waitForIdle()
                        capture(current, directory, csv, captures)
                        // Preserve every screenshot even if one appearance assertion
                        // fails, so the exported evidence describes the actual output.
                        try {
                            assertProductGeometry(current)
                        } catch (failure: AssertionError) {
                            geometryFailures.put("${current.name}: ${failure.message}")
                        }
                        csv.flush()
                        File(directory, "dimensions.json").writeText(manifest.toString(2))
                    }
                }
            }
        }
        assertEquals("four style/theme scenes, each with baseline and changed values", 8, captures.length())
        assertEquals("Appearance mismatches are recorded alongside every capture: $geometryFailures", 0, geometryFailures.length())
    }

    @Composable private fun SliderFixture(scene: Scene) {
        val view = LocalView.current
        val currentDensity = LocalDensity.current.density
        SideEffect {
            contentView = view
            root = view.rootView
            density = currentDensity
            var context = view.context
            while (context !is Activity && context is ContextWrapper) context = context.baseContext
            window = (context as Activity).window
        }
        SleepyThemeProvider(darkTheme = scene.dark, themeKey = ThemePresets.KEY_DEFAULT) {
            CompositionLocalProvider(LocalUiStyle provides scene.style) {
                val colors = MaterialTheme.colorScheme
                SideEffect {
                    fun hex(value: Int) = "#${value.toUInt().toString(16).padStart(8, '0')}"
                    palette = JSONObject().put("primary", hex(colors.primary.toArgb()))
                        .put("surface", hex(colors.surface.toArgb()))
                        .put("background", hex(colors.background.toArgb()))
                }
                GlassHost(Modifier.fillMaxSize(), enabled = scene.style == UiStyle.LiquidGlass,
                    background = colors.background) {
                    GlassBackground(0f, Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(colors.background))
                    }
                    key(scene.style, scene.dark) {
                        Column(
                            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            Text("${scene.styleName} · ${if (scene.dark) "Dark" else "Light"}",
                                style = MaterialTheme.typography.titleLarge, color = colors.onBackground)
                            Text(if (scene.changed) "Changed values · baseline ghosts" else "Initial values · baseline aligned",
                                color = colors.onSurfaceVariant)
                            Column(Modifier.widthIn(max = 335.dp).fillMaxWidth()) {
                                Text("Single · ${if (scene.changed) "70" else "30"}%", color = colors.onSurface)
                                GlassSlider(
                                    value = if (scene.changed) .70f else .30f,
                                    onValueChange = {}, steps = 9, initialValue = .30f,
                                    ghostTag = "capture-single-ghost",
                                    modifier = Modifier.fillMaxWidth().testTag("capture-single"),
                                )
                            }
                            IntRangeSlider(
                                summary = if (scene.changed) "5–10" else "3–8",
                                minValue = 1, maxValue = 12,
                                startValue = if (scene.changed) 5 else 3,
                                endValue = if (scene.changed) 10 else 8,
                                onValueChange = { _, _ -> }, label = "Range",
                                initialStart = 3, initialEnd = 8,
                                modifier = Modifier.widthIn(max = 335.dp).fillMaxWidth().testTag("capture-range"),
                            )
                            Column(Modifier.widthIn(max = 335.dp).fillMaxWidth()) {
                                Text("Hue · ${if (scene.changed) "220" else "72"}°", color = colors.onSurface)
                                HueSlider(
                                    hue = if (scene.changed) 220f else 72f, onHueChange = {}, initialHue = 72f,
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("capture-hue"),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun assertProductGeometry(scene: Scene) {
        val expectedAspectRatio = 37f / 24f
        for ((control, thumbs, size) in listOf(
            Triple("single", listOf("glass-slider-end"), 37f to 24f),
            Triple("range", listOf("glass-slider-start", "glass-slider-end"), 27.75f to 18f),
            Triple("hue", listOf("glass-slider-end"), 37f to 24f),
        )) {
            val (expectedWidth, expectedHeight) = size
            val parent = hasAnyAncestor(hasTestTag("capture-$control"))
            for (tag in thumbs) {
                val nodes = compose.onAllNodes(hasTestTag(tag) and parent, useUnmergedTree = true)
                assertEquals("${scene.name}: $control must expose one $tag", 1, nodes.fetchSemanticsNodes().size)
                val bounds = nodes[0].getUnclippedBoundsInRoot()
                val width = (bounds.right - bounds.left).value
                val height = (bounds.bottom - bounds.top).value
                assertEquals("${scene.name}: $control $tag oval width", expectedWidth, width, .75f)
                assertEquals("${scene.name}: $control $tag oval height", expectedHeight, height, .75f)
                // Allow only the aspect change caused by rounding each dimension to pixels.
                val pixelRoundingDp = .5f / density
                val ratioTolerance = (expectedWidth + pixelRoundingDp) /
                    (expectedHeight - pixelRoundingDp) - expectedAspectRatio + .005f
                assertEquals("${scene.name}: $control $tag retains the shared oval aspect ratio",
                    expectedAspectRatio, width / height, ratioTolerance)
            }
            val track = compose.onAllNodes(hasTestTag("slider-track") and parent, useUnmergedTree = true)
            val ticks = compose.onAllNodes(hasTestTag("slider-ticks") and parent, useUnmergedTree = true)
            assertEquals("${scene.name}: $control must expose one real rail", 1, track.fetchSemanticsNodes().size)
            assertEquals("${scene.name}: $control must expose one real dots canvas", 1, ticks.fetchSemanticsNodes().size)
            val rail = track[0].getUnclippedBoundsInRoot()
            val dots = ticks[0].getUnclippedBoundsInRoot()
            assertEquals("${scene.name}: $control rail height", 8f, (rail.bottom - rail.top).value, .75f)
            assertEquals("${scene.name}: $control dots height", 2f, (dots.bottom - dots.top).value, .75f)
            val railCenter = (rail.top.value + rail.bottom.value) / 2f
            val dotsCenter = (dots.top.value + dots.bottom.value) / 2f
            assertEquals("${scene.name}: $control dots remain below the rail", 14f, dotsCenter - railCenter, .75f)
            val ghostTag = when (control) {
                "single" -> "capture-single-ghost"
                "range" -> "slider-ghost"
                else -> "hue-ghost"
            }
            val ghosts = compose.onAllNodes(hasTestTag(ghostTag) and parent, useUnmergedTree = true)
                .fetchSemanticsNodes()
            if (scene.changed) assertTrue("${scene.name}: $control must display its original baseline", ghosts.isNotEmpty())
            else assertTrue("${scene.name}: $control aligned baseline must have no ghost", ghosts.isEmpty())
        }
    }

    private fun capture(scene: Scene, directory: File, csv: Writer, captures: JSONArray) {
        val origin = IntArray(2)
        val committed = CountDownLatch(1)
        compose.runOnUiThread {
            contentView.getLocationInWindow(origin)
            root.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
            root.postInvalidateOnAnimation()
        }
        assertTrue("${scene.name}: HWUI frame did not commit", committed.await(5, TimeUnit.SECONDS))
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        try {
            val copied = CountDownLatch(1)
            var result = PixelCopy.ERROR_UNKNOWN
            PixelCopy.request(window, bitmap, { result = it; copied.countDown() }, Handler(Looper.getMainLooper()))
            assertTrue("${scene.name}: PixelCopy did not complete", copied.await(5, TimeUnit.SECONDS))
            assertEquals("${scene.name}: PixelCopy failed", PixelCopy.SUCCESS, result)
            val png = "${scene.name}.png"
            File(directory, png).outputStream().use {
                assertTrue("${scene.name}: encode screenshot", bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            val measurements = JSONArray()
            for (control in listOf("single", "range", "hue")) {
                val tag = "capture-$control"
                record(scene, control, tag, compose.onNodeWithTag(tag, true), png, measurements, csv)
                val descendants = compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
                descendants.fetchSemanticsNodes().forEachIndexed { index, node ->
                    val childTag = node.config.getOrNull(SemanticsProperties.TestTag)
                    val progress = node.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
                    if (childTag != null || progress != null) {
                        record(scene, control, childTag ?: "untagged-progress-$index",
                            descendants[index], png, measurements, csv)
                    }
                }
            }
            captures.put(JSONObject().put("scene", scene.name).put("style", scene.styleName)
                .put("theme", if (scene.dark) "dark" else "light").put("phase", scene.phase)
                .put("png", png).put("width_px", bitmap.width).put("height_px", bitmap.height)
                .put("content_origin_in_window_px", JSONArray(origin.toList()))
                .put("palette", JSONObject(palette.toString())).put("measurements", measurements))
        } finally {
            bitmap.recycle()
        }
    }

    private fun record(scene: Scene, control: String, tag: String, node: SemanticsNodeInteraction,
                       png: String, measurements: JSONArray, csv: Writer) {
        val bounds = node.getUnclippedBoundsInRoot()
        val values = listOf(bounds.left.value, bounds.top.value,
            (bounds.right - bounds.left).value, (bounds.bottom - bounds.top).value)
        val progress = node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.toString() ?: ""
        measurements.put(JSONObject().put("control", control).put("tag", tag).put("progress", progress)
            .put("bounds_dp", JSONArray(values)).put("bounds_px", JSONArray(values.map { it * density })))
        val cells = listOf(scene.name, scene.styleName, if (scene.dark) "dark" else "light", scene.phase,
            control, tag, progress) + values + values.map { it * density } + png
        csv.appendLine(cells.joinToString(",") { "\"${it.toString().replace("\"", "\"\"")}\"" })
    }
}
