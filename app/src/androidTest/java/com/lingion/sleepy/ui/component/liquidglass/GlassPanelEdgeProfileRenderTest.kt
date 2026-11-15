package com.lingion.sleepy.ui.component.liquidglass

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.kyant.backdrop.BackdropRenderOptions
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.isRuntimeShaderSupported
import com.lingion.sleepy.ui.component.OpticalBackground
import com.lingion.sleepy.ui.component.OpticalCaptureBackground
import com.lingion.sleepy.ui.component.OpticalCaptureWindow
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An opt-in, fixed-budget HWUI mechanism probe with explicit bevel20/bevel6 settings.
 * Bevel6 was rejected by same-world native comparison; it is not a product default.
 * Only bevel changes. These captures establish a local GPU response, not UIKit parity.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class GlassPanelEdgeProfileRenderTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var surface: OpticalCaptureWindow
    private lateinit var source: LayerBackdrop
    private var density = 0f
    private val effects = CopyOnWriteArrayList<EffectObservation>()
    private val sourceDraws = CopyOnWriteArrayList<SourceObservation>()

    private enum class Panel(val widthDp: Int, val heightDp: Int, val radiusDp: Float) {
        pill(333, 62, 31f), sheet(300, 240, 28f);

        fun style(dark: Boolean): LiquidGlassStyle = when (this) {
            pill -> LiquidGlassStyle.pill(if (dark) darkColorScheme() else lightColorScheme(), dark)
            sheet -> LiquidGlassStyle.sheet(dark)
        }
    }

    private data class Scene(val panel: Panel, val background: OpticalBackground, val dark: Boolean) {
        val id get() = "${panel.name}-${background.name}-${if (dark) "dark" else "light"}"
        val group get() = "${panel.name}-${if (dark) "dark" else "light"}"
    }

    private data class EffectObservation(
        val scene: String, val bevel: Double, val size: Size, val density: Float,
        val materialEffect: Boolean, val inputEffect: Boolean,
    )

    private data class SourceObservation(val background: OpticalBackground, val hardware: Boolean)

    private data class Capture(
        val bitmap: Bitmap, val sceneBounds: Rect, val panelBounds: Rect?,
        val cropInWindow: android.graphics.Rect, val density: Float, val sourceRevision: Int,
    ) {
        fun json() = JSONObject()
            .put("scene_bounds_in_root_px", sceneBounds.json())
            .put("panel_bounds_in_root_px", panelBounds?.json() ?: JSONObject.NULL)
            .put("crop_in_window_px", JSONArray(listOf(cropInWindow.left, cropInWindow.top, cropInWindow.right, cropInWindow.bottom)))
            .put("png_size_px", JSONArray(listOf(bitmap.width, bitmap.height)))
            .put("density", density).put("source_recording_revision", sourceRevision)

        fun panelInBitmap(): Rect = checkNotNull(panelBounds).translate(Offset(-sceneBounds.left, -sceneBounds.top))
    }

    private class Delta {
        var pixels = 0L
        var changed = 0L
        var maximum = 0
        var absoluteRgb = 0.0
        var minimumAlpha = 255

        fun add(a: Int, b: Int) {
            val dr = abs(android.graphics.Color.red(a) - android.graphics.Color.red(b))
            val dg = abs(android.graphics.Color.green(a) - android.graphics.Color.green(b))
            val db = abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))
            val da = abs(android.graphics.Color.alpha(a) - android.graphics.Color.alpha(b))
            val largest = maxOf(dr, dg, db, da)
            pixels++
            if (largest >= 2) changed++
            maximum = max(maximum, largest)
            absoluteRgb += (dr + dg + db) / 3.0
            minimumAlpha = minOf(minimumAlpha, android.graphics.Color.alpha(a), android.graphics.Color.alpha(b))
        }

        fun json() = JSONObject().put("pixels", pixels).put("changed_by_at_least_2_levels", changed)
            .put("max_channel_delta", maximum).put("mean_abs_rgb_delta", absoluteRgb / max(1L, pixels))
            .put("minimum_alpha", minimumAlpha)
    }

    private class InsetBand(val fromDp: Int) {
        private val delta = Delta()
        private val baselineRgb = DoubleArray(3)
        private val candidateRgb = DoubleArray(3)
        private val sourceRgb = DoubleArray(3)

        fun add(a: Int, b: Int, source: Int) {
            delta.add(a, b)
            accumulate(a, baselineRgb)
            accumulate(b, candidateRgb)
            accumulate(source, sourceRgb)
        }

        private fun accumulate(pixel: Int, sum: DoubleArray) {
            sum[0] += android.graphics.Color.red(pixel)
            sum[1] += android.graphics.Color.green(pixel)
            sum[2] += android.graphics.Color.blue(pixel)
        }

        fun json(): JSONObject {
            fun mean(sum: DoubleArray) = JSONArray(sum.map { it / max(1L, delta.pixels) })
            return JSONObject().put("inset_from_dp_inclusive", fromDp).put("inset_to_dp_exclusive", fromDp + 1)
                .put("baseline_candidate_delta", delta.json()).put("baseline_mean_rgb", mean(baselineRgb))
                .put("candidate_mean_rgb", mean(candidateRgb)).put("bare_source_mean_rgb", mean(sourceRgb))
        }
    }

    @Test
    fun explicitBevelProbeKeepsTheBodyAndChangesTheEdgeAcrossFourBackgrounds() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Enable explicitly with glassPanelEdgeProfileCheck=true",
            arguments.getString("glassPanelEdgeProfileCheck") == "true")
        val runId = (arguments.getString("glassPanelEdgeProfileRunId") ?: System.currentTimeMillis().toString())
            .replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(checkNotNull(context.getExternalFilesDir(null)), "panel-edge-profile/$runId")
            .apply { check(mkdirs() || isDirectory) }
        check(directory.listFiles()?.none { it.extension == "png" } != false) { "Use a fresh run id: $directory" }
        val scenes = Panel.entries.flatMap { panel ->
            listOf(false, true).flatMap { dark -> OpticalBackground.entries.map { Scene(panel, it, dark) } }
        }
        val selected = mutableStateOf(scenes.first())
        val bevel = mutableStateOf(20.0)
        val visible = mutableStateOf(false)
        val captures = JSONArray()
        val comparisons = JSONArray()
        val failures = mutableListOf<String>()
        val edgeAreaByGroup = mutableMapOf<String, Double>()
        val hashes = mutableListOf<String>()
        val report = JSONObject().put("status", "running").put("run_id", runId)
            .put("scope", "Opt-in mechanism probe: current liquidGlass HWUI/Window PixelCopy with explicit bevel20 then bevel6 on one retained consumer per pair. Bevel6 is a rejected experiment, not the production setting. No native optical or motion parity claim.")
            .put("scene_budget", 16).put("png_budget", 32)
            .put("logical_scene_max_dp", JSONArray(listOf(375, 360)))
            .put("geometry_scope", "Controlled circular capsule333x62dp/radius31dp and rounded rectangle300x240dp/radius28dp; production material factories only. The sheet fixture is not the actual App sheet contour or layout.")
            .put("source", "Existing OpticalCaptureBackground drawn and recorded in an external LayerBackdrop; source-only references stay in memory and are not PNG artifacts")
            .put("backgrounds", JSONArray(OpticalBackground.entries.map { it.metadata() }))
            .put("production_shader_sha256", sha(LiquidGlassShader.shaderSource(context).toByteArray()))
            .put("sampling_policy", "Analytic circular rounded-rectangle inset from measured bounds. Center is beyond max effective bevel + 8dp; edge is 2dp inside AA to 2dp before the old bevel boundary; outside probes are at least3dp outside. No cross-source comparison.")
            .put("thresholds", "Center/outside max channel delta<=1; opaque captured alpha>=254; each material phase mean center/source RGB delta>=0.5; each shape/theme has >=4dp^2 edge pixels changing by >=2 levels across four backgrounds.")
            .put("threshold_reason", "One 8-bit level permits quantization only. Edge area rejects isolated AA noise and fails an ignored bevel. Blurred fine grids can be flat, so edge sensitivity is required per shape/theme across all backgrounds, not falsely demanded of every uniform patch.")
            .put("limits", "Frame commits and PixelCopy read the Window surface. Runtime support, compiled production shader, effect attachment, source recording, unchanged center and a bevel-sensitive GPU edge guard fallback/transparent failures behaviorally; they are not exhaustive introspection of every GPU driver path.")
        val reportFile = File(directory, "result.json")
        reportFile.writeText(report.toString(2))
        try {
            assertTrue("API33+ RuntimeShader path is required", isRuntimeShaderSupported())
            // Fail explicitly if the production program cannot compile; do not accept its blur fallback.
            android.graphics.RuntimeShader(LiquidGlassShader.shaderSource(context))
            compose.setContent {
                val scene = selected.value
                val appContext = LocalContext.current
                val view = LocalView.current
                val physicalDensity = LocalDensity.current.density
                val backdrop = rememberLayerBackdrop()
                // Explicit experimental values keep this probe reproducible without
                // requiring a rejected bevel6 setting in the production factory.
                val production = scene.panel.style(scene.dark)
                val material = production.copy(bevel = bevel.value)
                val shape = remember(scene.panel) { AbsoluteRoundedCornerShape(scene.panel.radiusDp.dp) }
                val options = remember { BackdropRenderOptions(placementLayer = true) }
                SideEffect {
                    surface = OpticalCaptureWindow.from(view)
                    source = backdrop
                    density = physicalDensity
                }
                Box(Modifier.size(375.dp, 360.dp).background(Color.Magenta).testTag(SCENE_TAG)) {
                    OpticalCaptureBackground(scene.background,
                        Modifier.matchParentSize().layerBackdrop(backdrop, recordKey = { scene.background })
                            .drawWithContent {
                                drawContent()
                                sourceDraws.add(SourceObservation(scene.background, drawContext.canvas.nativeCanvas.isHardwareAccelerated))
                            })
                    if (visible.value) {
                        Box(Modifier.align(Alignment.Center)
                            .requiredSize(scene.panel.widthDp.dp, scene.panel.heightDp.dp)
                            .drawPlainBackdrop(backdrop = backdrop, shape = { shape }, renderOptions = options,
                                effects = {
                                    liquidGlass(appContext, material)
                                    if (size.width.isFinite() && size.height.isFinite() && size.width > 0f && size.height > 0f) {
                                        effects.add(EffectObservation(scene.id, material.bevel, size, density,
                                            renderEffect != null, inputRenderEffect != null))
                                    }
                                })
                            .testTag(PANEL_TAG))
                    }
                }
            }
            scenes.forEach { scene ->
                compose.runOnIdle { visible.value = false; selected.value = scene; bevel.value = 20.0 }
                val reference = capture(includePanel = false)
                var baseline: Capture? = null
                var candidate: Capture? = null
                try {
                    compose.runOnIdle { visible.value = true }
                    baseline = capture(includePanel = true)
                    save(directory, scene, 20.0, baseline, captures, hashes)
                    compose.runOnIdle { bevel.value = 6.0 }
                    candidate = capture(includePanel = true)
                    save(directory, scene, 6.0, candidate, captures, hashes)
                    compare(scene, reference, baseline, candidate, failures, edgeAreaByGroup, comparisons)
                } finally {
                    candidate?.bitmap?.recycle()
                    baseline?.bitmap?.recycle()
                    reference.bitmap.recycle()
                }
            }
            scenes.map { it.group }.distinct().forEach { group ->
                if ((edgeAreaByGroup[group] ?: 0.0) < 4.0) {
                    failures += "$group: bevel did not change at least4dp^2 of real edge pixels across its four backgrounds"
                }
            }
            if (captures.length() != 32) failures += "Expected exactly32 PNGs, got${captures.length()}"
        } catch (failure: Throwable) {
            failures += "${failure.javaClass.name}: ${failure.message}"
            throw failure
        } finally {
            report.put("captures", captures).put("comparisons", comparisons)
                .put("edge_changed_area_dp2_by_shape_theme", JSONObject(edgeAreaByGroup.mapValues { it.value }))
                .put("effect_observations", JSONArray(effects.map {
                    JSONObject().put("scene", it.scene).put("bevel_dp", it.bevel)
                        .put("size_px", JSONArray(listOf(it.size.width, it.size.height))).put("density", it.density)
                        .put("material_effect_attached", it.materialEffect).put("haze_input_effect_attached", it.inputEffect)
                }))
                .put("source_draw_observations", sourceDraws.size)
                .put("source_software_draws", sourceDraws.count { !it.hardware })
                .put("failures", JSONArray(failures))
                .put("status", if (failures.isEmpty() && captures.length() == 32) "passed" else "failed")
            reportFile.writeText(report.toString(2))
            File(directory, "sha256.txt").writeText(hashes.joinToString("\n", postfix = "\n"))
        }
        assertTrue("Panel edge profile regression: ${failures.joinToString()}; artifacts=$directory", failures.isEmpty())
    }

    private fun compare(
        scene: Scene, reference: Capture, baseline: Capture, candidate: Capture,
        failures: MutableList<String>, edgeArea: MutableMap<String, Double>, comparisons: JSONArray,
    ) {
        fun requireObserved(condition: Boolean, message: String) {
            if (!condition) failures += "${scene.id}: $message"
        }
        requireObserved(reference.sceneBounds == baseline.sceneBounds && baseline.sceneBounds == candidate.sceneBounds,
            "scene moved between source/baseline/candidate")
        requireObserved(baseline.panelBounds == candidate.panelBounds, "panel moved between bevels")
        requireObserved(reference.cropInWindow == baseline.cropInWindow && baseline.cropInWindow == candidate.cropInWindow,
            "PixelCopy source rectangle changed")
        requireObserved(reference.density == baseline.density && baseline.density == candidate.density, "density changed")
        requireObserved(baseline.bitmap.width == candidate.bitmap.width && baseline.bitmap.height == candidate.bitmap.height &&
            reference.bitmap.width == baseline.bitmap.width && reference.bitmap.height == baseline.bitmap.height, "bitmap sizes changed")
        requireObserved(baseline.sourceRevision == candidate.sourceRevision, "the retained external source was re-recorded between bevels")
        val panel = baseline.panelInBitmap()
        val radius = min(scene.panel.radiusDp.toDouble(), min(panel.width, panel.height) / (2.0 * density))
        val baselineBevel = min(20.0, min(panel.width, panel.height) / (4.0 * density))
        val centerInset = baselineBevel + 8.0
        val center = Delta()
        val edge = Delta()
        val outside = Delta()
        val baselineSourceCenter = Delta()
        val candidateSourceCenter = Delta()
        val baselineOutsideSource = Delta()
        val candidateOutsideSource = Delta()
        val insetProfile = (-4 until 24).map { InsetBand(it) }
        for (y in 0 until baseline.bitmap.height) for (x in 0 until baseline.bitmap.width) {
            // Bitmap crop is pixel aligned; retain the measured subpixel panel origin.
            val inset = circularInsetDp((x + .5 - panel.left) / density,
                (y + .5 - panel.top) / density, panel.width / density.toDouble(), panel.height / density.toDouble(), radius)
            val a = baseline.bitmap.getPixel(x, y)
            val b = candidate.bitmap.getPixel(x, y)
            val original = reference.bitmap.getPixel(x, y)
            if (inset >= -4.0 && inset < 24.0) insetProfile[floor(inset).toInt() + 4].add(a, b, original)
            when {
                inset >= centerInset -> {
                    center.add(a, b)
                    baselineSourceCenter.add(a, original)
                    candidateSourceCenter.add(b, original)
                }
                inset in 2.0..(baselineBevel - 2.0) -> edge.add(a, b)
                inset <= -3.0 -> {
                    outside.add(a, b)
                    baselineOutsideSource.add(a, original)
                    candidateOutsideSource.add(b, original)
                }
            }
        }
        requireObserved(center.pixels > 0 && edge.pixels > 0 && outside.pixels > 0, "probe masks must contain real pixels")
        requireObserved(center.maximum <= 1, "bevel changed the safe center: max=${center.maximum}")
        requireObserved(outside.maximum <= 1 && baselineOutsideSource.maximum <= 1 && candidateOutsideSource.maximum <= 1,
            "external background changed outside the panel")
        requireObserved(center.minimumAlpha >= 254, "opaque source/material produced a transparent center")
        requireObserved(baselineSourceCenter.absoluteRgb / max(1L, center.pixels) >= .5 &&
            candidateSourceCenter.absoluteRgb / max(1L, center.pixels) >= .5, "center equals the bare source: transparent/missing material frame")
        requireObserved(abs(panel.width / density - scene.panel.widthDp) < 1f / density &&
            abs(panel.height / density - scene.panel.heightDp) < 1f / density, "actual panel size differs from the requested fixed geometry")
        val records = sourceDraws.filter { it.background == scene.background }
        requireObserved(records.isNotEmpty() && records.none { !it.hardware }, "external source must be recorded on hardware")
        listOf(20.0, 6.0).forEach { bevel ->
            val measured = effects.filter { it.scene == scene.id && it.bevel == bevel }
            requireObserved(measured.isNotEmpty() && measured.all { it.materialEffect }, "bevel$bevel has no measured production RenderEffect")
            if (scene.panel == Panel.pill) requireObserved(measured.all { it.inputEffect }, "bevel$bevel lost the real haze-atlas input effect")
        }
        val changedArea = edge.changed / (density.toDouble() * density)
        edgeArea[scene.group] = (edgeArea[scene.group] ?: 0.0) + changedArea
        comparisons.put(JSONObject().put("scene", scene.id).put("shape", scene.panel.name).put("dark", scene.dark)
            .put("panel_in_scene_bitmap_px", panel.json()).put("radius_dp", radius)
            .put("effective_baseline_bevel_dp", baselineBevel).put("candidate_bevel_dp", 6.0)
            .put("center_minimum_inset_dp", centerInset).put("edge_inset_range_dp", JSONArray(listOf(2.0, baselineBevel - 2.0)))
            .put("center_baseline_candidate", center.json()).put("edge_baseline_candidate", edge.json())
            .put("outside_baseline_candidate", outside.json()).put("baseline_center_bare_source", baselineSourceCenter.json())
            .put("candidate_center_bare_source", candidateSourceCenter.json())
            .put("baseline_outside_bare_source", baselineOutsideSource.json()).put("candidate_outside_bare_source", candidateOutsideSource.json())
            .put("circular_signed_inset_profile_minus4_to24_dp", JSONArray(insetProfile.map { it.json() }))
            .put("changed_edge_area_dp2", changedArea))
    }

    private fun capture(includePanel: Boolean): Capture {
        compose.waitForIdle()
        assertTrue("A real accelerated Window is required", surface.view.isHardwareAccelerated)
        // Fence both the external recording and its consumer after attachment/placement.
        repeat(2) { surface.awaitHwuiCommit() }
        val sceneBounds = compose.onNodeWithTag(SCENE_TAG).fetchSemanticsNode().boundsInRoot
        val panelBounds = if (includePanel) compose.onNodeWithTag(PANEL_TAG).fetchSemanticsNode().boundsInRoot else null
        val origin = surface.origin()
        val crop = android.graphics.Rect(floor(origin[0] + sceneBounds.left).toInt(), floor(origin[1] + sceneBounds.top).toInt(),
            ceil(origin[0] + sceneBounds.right).toInt(), ceil(origin[1] + sceneBounds.bottom).toInt())
        assertTrue("The complete local scene must fit the Window, crop=$crop", crop.left >= 0 && crop.top >= 0 &&
            crop.right <= surface.root.width && crop.bottom <= surface.root.height)
        val bitmap = Bitmap.createBitmap(crop.width(), crop.height(), Bitmap.Config.ARGB_8888)
        val copied = CountDownLatch(1)
        var status = PixelCopy.ERROR_UNKNOWN
        PixelCopy.request(checkNotNull(surface.window), crop, bitmap,
            { status = it; copied.countDown() }, Handler(Looper.getMainLooper()))
        if (!copied.await(5, TimeUnit.SECONDS) || status != PixelCopy.SUCCESS) {
            bitmap.recycle()
            throw AssertionError("Local Window PixelCopy failed: $status")
        }
        // floor/ceil are exact for the integer scene origin produced by this fixture.
        assertEquals("Scene origin must be pixel aligned", (origin[0] + sceneBounds.left).toDouble(), crop.left.toDouble(), .01)
        assertEquals("Scene origin must be pixel aligned", (origin[1] + sceneBounds.top).toDouble(), crop.top.toDouble(), .01)
        return Capture(bitmap, sceneBounds, panelBounds, crop, density, compose.runOnIdle { source.recordingRevision })
    }

    private fun save(directory: File, scene: Scene, bevel: Double, capture: Capture, captures: JSONArray, hashes: MutableList<String>) {
        val file = File(directory, "${scene.id}-${if (bevel == 20.0) "baseline20" else "candidate6"}.png")
        file.outputStream().use { check(capture.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        val hash = sha(file.readBytes())
        hashes += "$hash  ${file.name}"
        captures.put(capture.json().put("scene", scene.id).put("background", scene.background.name).put("dark", scene.dark)
            .put("requested_panel_size_dp", JSONArray(listOf(scene.panel.widthDp, scene.panel.heightDp)))
            .put("png", file.name).put("png_sha256", hash).put("style", scene.panel.style(scene.dark).copy(bevel = bevel).json()))
    }

    private fun circularInsetDp(x: Double, y: Double, width: Double, height: Double, radius: Double): Double {
        val qx = abs(x - width / 2.0) - width / 2.0 + radius
        val qy = abs(y - height / 2.0) - height / 2.0 + radius
        return radius - hypot(max(qx, 0.0), max(qy, 0.0)) - min(max(qx, qy), 0.0)
    }

    private fun LiquidGlassStyle.json() = JSONObject().put("tint_argb", tint.toArgb()).put("tint_alpha", tint.alpha)
        .put("contrast", contrast).put("blur", blur).put("haze", haze).put("hazeBlur", hazeBlur)
        .put("refraction", refraction).put("bevel", bevel).put("saturation", saturation).put("brightness", brightness)
        .put("rim", rim).put("rimWidth", rimWidth).put("rimTail", rimTail).put("rimFocus", rimFocus).put("rimAdded", rimAdded)
        .put("endShade", endShade).put("dispersion", dispersion).put("rimBlur", rimBlur).put("hairline", hairline)
        .put("hairlineSide", hairlineSide ?: JSONObject.NULL).put("resolvedHairlineSide", hairlineSideOrHairline)

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        const val SCENE_TAG = "panel-edge-scene"
        const val PANEL_TAG = "panel-edge-consumer"
    }
}

private fun Rect.json() = JSONArray(listOf(left, top, right, bottom))
