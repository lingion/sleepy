package com.lingion.sleepy.ui.component.liquidglass

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Choreographer
import android.view.PixelCopy
import android.view.Window
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.kyant.backdrop.BackdropRenderOptions
import com.kyant.backdrop.asAndroidRuntimeShader
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Six static RenderEffect captures isolate language-menu geometry from material and source. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class LiquidGlassLanguageMaterialDiagnosticTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val variant = mutableStateOf(Variant.Legacy)
    private val errors = mutableListOf<String>()
    private val snapshots = mutableMapOf<Variant, JSONObject>()
    private val sourceDraws = mutableMapOf<Variant, Int>()
    private lateinit var window: Window
    private var bounds: Rect? = null

    private enum class Variant(val label: String) {
        Legacy("legacy3"), Exact("exact5"), NoShine("exact5-no-shine"),
        Swap("exact5-swap-complete-contours"), BigOnly("exact4-big-menu-only"),
        Diagnostic("exact5-mixed-depth-normal");
    }

    @Test fun captureTerminalLanguageMaterialWithSingleVariableControls() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Opt in with glassLanguageMaterialDiagnostic=true",
            args.getString("glassLanguageMaterialDiagnostic") == "true")
        val runId = (args.getString("glassLanguageMaterialDiagnosticRunId") ?: System.currentTimeMillis().toString())
            .replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "glass-language-material/$runId")
            .apply { check(mkdirs() || isDirectory) }
        val source = LiquidGlassShader.shaderSource(instrumentation.targetContext)
        val originalReturn = "return half4(min(glass, float3(coverage)), coverage);"
        check(source.split(originalReturn).size == 2)
        // Keep every material/sample/shine expression alive. Decode against Exact's RGB below.
        val mixed = source.replace(originalReturn, """
            if (!exactFound || !(inset >= 0.0 && inset < 1000000.0)) {
              return half4(1.0, 0.0, 1.0, 1.0);
            }
            float3 geometryCode = float3(clamp(inset / 128.0, 0.0, 1.0), normal * 0.5 + 0.5);
            return half4(0.5 * min(glass, float3(coverage)) + 0.5 * geometryCode, coverage);
        """.trimIndent())
        File(directory, "production-unmodified.agsl").writeText(source)
        File(directory, "production-mixed-diagnostic.agsl").writeText(mixed)
        val results = JSONArray()
        val report = JSONObject().put("run_id", runId).put("production_shader_sha256", sha(source))
            .put("mixed_shader_sha256", sha(mixed)).put("fixture_density", DENSITY)
            .put("carrier_size_px", JSONArray(listOf(WIDTH, HEIGHT)))
            .put("background_srgb_8bit", JSONArray(listOf(238, 238, 243)))
            .put("scope", "Six committed static output samples; actual drawPlainBackdrop + production liquidGlass prep + replaced RenderEffect snapshot, no performance or native-appearance acceptance claim")
            .put("uniform_contract", "Production preparation is retained. The identical complete uniform map is rebuilt, serialized, reapplied, then only the named control changes before immediate RenderEffect snapshot.")
            .put("draw_contract", "The composable reads each variant, key(variant) recreates its optical consumer, and the opaque source records with recordKey=variant. Snapshot metadata alone cannot establish a changed GPU output.")
            .put("limits", "Controlled opaque neutral source, no popup/window relocation or natural filterBand preprocessing; sampledBlurPx=0. A passing collection does not establish that actual glassControl popup uniforms/source layers are correct. Mixed invalid exact solves are magenta and must not be decoded as geometry.")
            .put("decode", "With opaque source and interior coverage=1: E=2*mixedRGB8/255-exactRGB8/255; insetDp=128*E.r (clamped encoding at128); normal=(2*E.g-1,2*E.b-1). Decode raw values before any visualization clamp.")
            .put("decode_quantization", "Assuming each RGB capture rounds by at most0.5/255: E error<=1.5/255, depth error<=192/255=0.753dp, normal-component error<=3/255=0.01177. Additional HWUI/color-conversion/half precision is not bounded by this arithmetic.")
            .put("cases", results)
        val crops = mutableMapOf<Variant, Bitmap>()
        Log.i(TAG, "BEGIN $runId")
        try {
            compose.setContent {
                CompositionLocalProvider(LocalDensity provides Density(DENSITY, .9f)) {
                    MaterialTheme { Surface(source, mixed) }
                }
            }
            for (current in Variant.entries) {
                instrumentation.runOnMainSync { variant.value = current }
                compose.waitForIdle()
                awaitCommittedFrames()
                val snapshot = checkNotNull(snapshots[current]) { "No RenderEffect snapshot for $current" }
                val crop = capture(directory, current)
                crops[current] = crop
                results.put(JSONObject().put("case", current.label).put("snapshot", snapshot)
                    .put("rows", rowMeans(crop)))
            }
            val exact = checkNotNull(crops[Variant.Exact])
            val diagnostic = checkNotNull(crops[Variant.Diagnostic])
            check(exact.width == diagnostic.width && exact.height == diagnostic.height)
            val decoded = Bitmap.createBitmap(exact.width, exact.height, Bitmap.Config.ARGB_8888)
            for (y in 0 until exact.height) for (x in 0 until exact.width) {
                val a = rgb(exact.getPixel(x, y)); val b = rgb(diagnostic.getPixel(x, y))
                val encoded = DoubleArray(3) { (2.0 * b[it] - a[it]) / 255.0 }
                decoded.setPixel(x, y, android.graphics.Color.rgb(
                    (encoded[0].coerceIn(0.0, 1.0) * 255).roundToInt(),
                    (encoded[1].coerceIn(0.0, 1.0) * 255).roundToInt(),
                    (encoded[2].coerceIn(0.0, 1.0) * 255).roundToInt()))
            }
            save(decoded, File(directory, "decoded-depth-normal-clamped-preview.png")); decoded.recycle()
            report.put("decoded_rows", decodedRows(exact, diagnostic))
            report.put("pixel_delta_from_exact_rows", JSONObject().apply {
                for (control in listOf(Variant.Legacy, Variant.NoShine, Variant.Swap, Variant.BigOnly)) {
                    put(control.label, rowDifferences(exact, checkNotNull(crops[control])))
                }
            })
            val validity = diagnosticValidity(exact, diagnostic)
            report.put("gpu_output_validity", validity)
            check(validity.getBoolean("passed")) {
                "Mixed output sentinel or decoded geometry validity failed; snapshots cannot prove an actual GPU mode change"
            }
        } catch (failure: Throwable) { errors += failure.stackTraceToString() }
        finally {
            crops.values.forEach { it.recycle() }
            try {
                val fd = instrumentation.uiAutomation.executeShellCommand(
                    "logcat -d -v threadtime --pid=${android.os.Process.myPid()} LiquidGlassEffect:W $TAG:I '*:S'")
                ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
                    File(directory, "runtime-logcat.txt").outputStream().use { input.copyTo(it) }
                }
                val log = File(directory, "runtime-logcat.txt").readText()
                val begin = log.lastIndexOf("BEGIN $runId")
                report.put("log_begin_retained", begin >= 0)
                if (begin >= 0) {
                    val fallbacks = log.substring(begin).lineSequence().count {
                        it.contains("LiquidGlassEffect") && it.contains("RuntimeShader fallback to blur+vibrancy") }
                    report.put("production_fallbacks_after_begin", fallbacks)
                    if (fallbacks > 0) errors += "$fallbacks production shader fallbacks"
                } else errors += "Unique BEGIN log marker was not retained; cannot verify production prep"
            } catch (failure: Throwable) { errors += failure.stackTraceToString() }
            report.put("errors", JSONArray(errors)).put("collection_complete", results.length() == Variant.entries.size)
                .put("status", if (errors.isEmpty()) "VALID_DIAGNOSTIC_COLLECTION" else "INVALID_DIAGNOSTIC_COLLECTION")
            File(directory, "result.json").writeText(report.toString(2))
            Log.i(TAG, "END $runId artifacts=${directory.absolutePath}")
        }
        // No visual tolerance is relaxed or inferred here: this is a causal diagnostic collection.
        assertTrue("Diagnostic collection failed; artifacts=$directory\n${errors.joinToString("\n")}", errors.isEmpty())
    }

    @Composable private fun Surface(source: String, mixed: String) {
        // A read only inside effects can rebuild a shader without redrawing a static consumer.
        val current = variant.value
        val view = LocalView.current
        val context = LocalContext.current
        SideEffect {
            var activity = view.context
            while (activity !is Activity && activity is ContextWrapper) activity = activity.baseContext
            window = (activity as Activity).window
        }
        val shape = remember { TerminalShape() }
        val backdrop = rememberLayerBackdrop()
        val style = LiquidGlassStyle.menu(MaterialTheme.colorScheme, false)
        val rim = LiquidGlassRim.menu(false)
        val options = remember(current) { BackdropRenderOptions(placementLayer = false, effectKey = { current }) }
        Box(Modifier.fillMaxSize().background(Color(0xFFEEEEF3))) {
            Canvas(Modifier.matchParentSize().layerBackdrop(backdrop, recordKey = { current })) {
                drawRect(Color(0xFFEEEEF3))
                sourceDraws[current] = (sourceDraws[current] ?: 0) + 1
            }
            key(current) {
                Box(Modifier.align(Alignment.Center).requiredSize((WIDTH / DENSITY).dp, (HEIGHT / DENSITY).dp)
                    .onGloballyPositioned { bounds = it.boundsInWindow() }
                    .drawPlainBackdrop(backdrop = backdrop, shape = { shape }, renderOptions = options,
                        effects = diagnosticEffects@{
                            if (!size.width.isFinite() || !size.height.isFinite() || size.width <= 0f || size.height <= 0f)
                                return@diagnosticEffects
                            try {
                                check(abs(density - DENSITY) < .00001f && abs(size.width - WIDTH) < .01f && abs(size.height - HEIGHT) < .01f)
                                fun dp(path: Path) = Path().apply { addPath(path)
                                    transform(androidx.compose.ui.graphics.Matrix().apply { scale(1f / density, 1f / density) }) }
                                val outline = dp(shape.outline.path)
                                val dual = dp(shape.sources[0].path) to dp(shape.sources[1].path)
                                liquidGlass(context, style, outline = outline, dualContours = dual, rim = rim)
                                val halfMinor = minOf(size.width, size.height) / (2f * density)
                                val uniforms = LiquidGlassShader.build(size = Size(size.width / density, size.height / density),
                                    corners = LiquidGlassCornerRadii(halfMinor, halfMinor, halfMinor, halfMinor), style = style,
                                    originPx = effectOffset, density = density, atlasSizePx = size,
                                    sampling = LiquidGlassShader.sampling(effectOffset, density, Rect(Offset.Zero, size)),
                                    outline = outline, dualContours = dual, rim = rim,
                                    exactContours = shape.sources.map { checkNotNull(it.exact) }).toMutableMap()
                                check(uniforms.getValue("uOutlined")[0] == 5f)
                                when (current) {
                                    Variant.Legacy -> uniforms["uOutlined"] = floatArrayOf(3f)
                                    Variant.NoShine -> uniforms["uShineLit"] = FloatArray(8)
                                    Variant.Swap -> {
                                        for ((a, b) in listOf("uExact1" to "uExact2", "uProfile" to "uProfile2")) {
                                            val old = uniforms.getValue(a); uniforms[a] = uniforms.getValue(b); uniforms[b] = old
                                        }
                                        val centers = uniforms.getValue("uProfileCenters")
                                        uniforms["uProfileCenters"] = floatArrayOf(centers[2], centers[3], centers[0], centers[1])
                                    }
                                    Variant.BigOnly -> {
                                        uniforms["uOutlined"] = floatArrayOf(4f)
                                        uniforms["uExact1"] = uniforms.getValue("uExact2")
                                        uniforms["uExact2"] = FloatArray(LIQUID_GLASS_EXACT_FLOATS)
                                        uniforms["uProfile"] = uniforms.getValue("uProfile2")
                                        uniforms["uProfile2"] = FloatArray(64)
                                        val centers = uniforms.getValue("uProfileCenters")
                                        uniforms["uProfileCenters"] = floatArrayOf(centers[2], centers[3], 0f, 0f)
                                    }
                                    else -> Unit
                                }
                                val shader = obtainRuntimeShader("LiquidGlassShader", if (current == Variant.Diagnostic) mixed else source)
                                uniforms.forEach { (name, values) -> shader.setFloatUniform(name, values) }
                                renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(
                                    shader.asAndroidRuntimeShader(), "uTexture").asComposeRenderEffect()
                                snapshots[current] = JSONObject().put("mode", uniforms.getValue("uOutlined")[0])
                                    .put("size_px", JSONArray(listOf(size.width, size.height))).put("density", density)
                                    .put("uniforms", JSONObject().apply { uniforms.forEach { (key, value) -> put(key, JSONArray(value.toList())) } })
                            } catch (failure: Throwable) { errors += failure.stackTraceToString() }
                        }))
            }
        }
    }

    private class TerminalShape : Shape, LiquidGlassExactContourShape {
        val sources = listOf(contour(Rect(352.875f, 122.9464111f, 391.125f, 161.1964111f), 19.125f),
            contour(Rect(0f, 0f, WIDTH, HEIGHT), 77.35f))
        val outline = Outline.Generic(Path.combine(PathOperation.Union, sources[0].path, sources[1].path))
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = outline
        override fun exactContoursFor(outline: Outline): List<LiquidGlassExactContour>? =
            if (this.outline === outline) sources.map { checkNotNull(it.exact) } else null
        private fun contour(rect: Rect, radius: Float) = LiquidGlassContourOutline(LiquidGlassSuperellipse.buildPath(
            LiquidGlassSuperellipse.Rect2(rect.left, rect.top, rect.right, rect.bottom), LiquidGlassSuperellipse.Radii.makeRadius(radius)))
    }

    private fun awaitCommittedFrames() {
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val choreographer = Choreographer.getInstance()
            choreographer.postFrameCallback { choreographer.postFrameCallback { done.countDown() } }
        }
        check(done.await(5, TimeUnit.SECONDS)) { "No two real display callbacks" }
    }

    private fun capture(directory: File, current: Variant): Bitmap {
        val full = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
        val done = CountDownLatch(1); var status = -1
        PixelCopy.request(window, full, { status = it; done.countDown() }, Handler(Looper.getMainLooper()))
        check(done.await(5, TimeUnit.SECONDS) && status == PixelCopy.SUCCESS) { "PixelCopy=$status" }
        save(full, File(directory, "${current.label}-window.png"))
        val rect = checkNotNull(bounds)
        check(abs(rect.width - WIDTH) < .01f && abs(rect.height - HEIGHT) < .01f)
        check((sourceDraws[current] ?: 0) > 0) { "No opaque source draw for ${current.label}" }
        val crop = Bitmap.createBitmap(full, floor(rect.left).toInt(), floor(rect.top).toInt(), WIDTH.toInt(), HEIGHT.toInt())
        save(crop, File(directory, "${current.label}-surface.png")); if (full !== crop) full.recycle()
        snapshots.getValue(current).put("window_bounds_px", JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom)))
            .put("source_draws_for_case", sourceDraws.getValue(current))
        return crop
    }

    private fun rowMeans(bitmap: Bitmap) = JSONArray((0..4).map { row ->
        val points = rowPixels(row); val sums = DoubleArray(3)
        points.forEach { (x, y) -> rgb(bitmap.getPixel(x, y)).forEachIndexed { channel, value -> sums[channel] += value } }
        JSONObject().put("row", row + 1).put("mean_rgb8", JSONArray(sums.map { it / points.size }))
    })

    private fun rowDifferences(a: Bitmap, b: Bitmap) = JSONArray((0..4).map { row ->
        val points = rowPixels(row); var absolute = 0.0; var maximum = 0
        points.forEach { (x, y) ->
            val aa = rgb(a.getPixel(x, y)); val bb = rgb(b.getPixel(x, y))
            for (channel in 0..2) { val delta = abs(aa[channel] - bb[channel]); absolute += delta; maximum = maxOf(maximum, delta) }
        }
        JSONObject().put("row", row + 1).put("mean_absolute_rgb8_delta", absolute / (points.size * 3))
            .put("maximum_rgb8_delta", maximum)
    })

    private fun decodedRows(exact: Bitmap, mixed: Bitmap) = JSONArray((0..4).map { row ->
        val samples = JSONArray()
        var invalidMagenta = 0
        rowPixels(row).forEach { (x, y) ->
            val a = rgb(exact.getPixel(x, y)); val b = rgb(mixed.getPixel(x, y))
            if (b[0] == 255 && b[1] == 0 && b[2] == 255) invalidMagenta++
            val e = DoubleArray(3) { (2.0 * b[it] - a[it]) / 255.0 }
            samples.put(JSONArray(listOf(x, y, 128.0 * e[0], 2.0 * e[1] - 1, 2.0 * e[2] - 1)))
        }
        JSONObject().put("row", row + 1).put("columns", "x,y,inset_dp,normal_x,normal_y")
            .put("invalid_magenta_pixels", invalidMagenta)
            .put("raw_unclamped_samples", samples)
    })

    private fun diagnosticValidity(exact: Bitmap, mixed: Bitmap): JSONObject {
        val rows = JSONArray()
        var passed = true
        for (row in 0..4) {
            val points = rowPixels(row)
            var changed = 0; var invalidMagenta = 0; var invalidNormal = 0; var invalidDepth = 0
            var minimumNormalLength = Double.POSITIVE_INFINITY; var maximumNormalLength = 0.0
            for ((x, y) in points) {
                val a = rgb(exact.getPixel(x, y)); val b = rgb(mixed.getPixel(x, y))
                if ((0..2).maxOf { abs(a[it] - b[it]) } >= 4) changed++
                if (b[0] == 255 && b[1] == 0 && b[2] == 255) invalidMagenta++
                val encoded = DoubleArray(3) { (2.0 * b[it] - a[it]) / 255.0 }
                val nx = 2.0 * encoded[1] - 1.0; val ny = 2.0 * encoded[2] - 1.0
                val normalLength = kotlin.math.sqrt(nx * nx + ny * ny)
                minimumNormalLength = minOf(minimumNormalLength, normalLength)
                maximumNormalLength = maxOf(maximumNormalLength, normalLength)
                if (!normalLength.isFinite() || abs(normalLength - 1.0) > .05) invalidNormal++
                val inset = 128.0 * encoded[0]
                if (!inset.isFinite() || inset < -.8 || inset > 128.8) invalidDepth++
            }
            // This is an instrumentation sentinel, not a material-appearance tolerance.
            // At these opaque interior ROIs the geometry channel must visibly replace half
            // the material, and the decoded normal remains unit length after RGB8 rounding.
            val rowPassed = changed >= points.size * .95 && invalidMagenta == 0 && invalidNormal == 0 && invalidDepth == 0
            passed = passed && rowPassed
            rows.put(JSONObject().put("row", row + 1).put("pixels", points.size)
                .put("pixels_changed_at_least_4_rgb8", changed).put("invalid_magenta_pixels", invalidMagenta)
                .put("invalid_unit_normal_pixels", invalidNormal).put("invalid_depth_pixels", invalidDepth)
                .put("minimum_normal_length", minimumNormalLength).put("maximum_normal_length", maximumNormalLength)
                .put("passed", rowPassed))
        }
        return JSONObject().put("passed", passed).put("minimum_changed_fraction_per_row", .95)
            .put("minimum_changed_channel_rgb8", 4).put("maximum_decoded_unit_normal_length_error", .05)
            .put("rows", rows)
    }

    private fun rowPixels(row: Int): List<Pair<Int, Int>> = buildList {
        // Original screen ROI [808,605+125r,861,652+125r), minus carrier origin(236.5,536).
        for (y in 69 + 125 * row until 116 + 125 * row) for (x in 572 until 625) add(x to y)
    }
    private fun rgb(pixel: Int) = intArrayOf(android.graphics.Color.red(pixel), android.graphics.Color.green(pixel), android.graphics.Color.blue(pixel))
    private fun save(bitmap: Bitmap, file: File) = file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    private fun sha(source: String) = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private companion object {
        const val TAG = "GlassLanguageMaterial"
        const val DENSITY = 2.975f
        const val WIDTH = 744f
        const val HEIGHT = 685f
    }
}
