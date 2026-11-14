package com.lingion.sleepy.ui.component.liquidglass

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Geometry transfer only: not native appearance, RenderEffect/backdrop, or material-alpha QA. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class LiquidGlassExactContourRenderTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var contentView: View
    private lateinit var window: Window

    private data class Source(val operations: List<LiquidGlassSuperellipse.PathOp>, val path: Path,
        val exact: LiquidGlassExactContour?) {
        constructor(source: LiquidGlassContourOutline) : this(source.operations, source.path, source.exact)
    }

    private data class Fixture(val name: String, val width: Int, val height: Int, val density: Float,
        val sources: List<Source>, val extraPixels: Set<Pair<Int, Int>> = emptySet()) {
        val oracles = sources.map { LiquidGlassRawPathOracle(it.operations, density, it.path.getBounds().center) }
        val sizeDp = Size(width / density, height / density)
        val pathDp: Path = Path().apply {
            val native = if (sources.size == 1) sources[0].path else
                Path.combine(PathOperation.Union, sources[0].path, sources[1].path)
            addPath(native)
            transform(androidx.compose.ui.graphics.Matrix().apply { scale(1f / density, 1f / density) })
        }
        fun geometry(point: LiquidGlassRawPathOracle.V): LiquidGlassRawPathOracle.Geometry {
            val first = oracles[0].geometry(point)
            return if (oracles.size == 1) first else LiquidGlassRawPathOracle.union(first, oracles[1].geometry(point))
        }
    }

    @Test fun exactGpuGeometryMatchesIndependentRawCpuCubicsAndCoverage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Enable explicitly with glassExactCheck=true", arguments.getString("glassExactCheck") == "true")
        val forceLegacy = arguments.getString("glassExactForceLegacy") == "true"
        val runId = (arguments.getString("glassExactRunId") ?: System.currentTimeMillis().toString())
            .replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "glass-exact-geometry/$runId")
            .apply { check(mkdirs() || isDirectory) }
        val production = LiquidGlassShader.shaderSource(instrumentation.targetContext)
        val finalReturn = "return half4(min(glass, float3(coverage)), coverage);"
        check(production.indexOf(finalReturn) >= 0 && production.indexOf(finalReturn) == production.lastIndexOf(finalReturn))
        val diagnostic = "uniform float uExactDiagnosticPhase;\n" + production.replace(finalReturn, """
            float diagnosticInset;
            float2 diagnosticNormal;
            bool diagnosticFound = signedExactGeometry(point, false, diagnosticInset, diagnosticNormal);
            if (uExact2[1].x > 0.5) {
              float otherInset;
              float2 otherNormal;
              bool otherFound = signedExactGeometry(point, true, otherInset, otherNormal);
              diagnosticFound = diagnosticFound && otherFound;
              diagnosticInset = max(diagnosticInset, otherInset);
            }
            float diagnosticNorm2 = dot(normal, normal);
            bool diagnosticValid = exactFound && diagnosticFound && diagnosticInset > -1000000.0
                && diagnosticInset < 1000000.0 && inset >= 0.0 && inset < 1000000.0
                && diagnosticNorm2 > 0.99 && diagnosticNorm2 < 1.01;
            if (!diagnosticValid) return half4(1.0, 0.0, 1.0, 1.0);
            float diagnosticDepthChannel = uExactDiagnosticPhase > 0.5
                ? clamp(inset / 8.0, 0.0, 1.0)
                : clamp((diagnosticInset + 4.0) / 8.0, 0.0, 1.0);
            return half4(diagnosticDepthChannel, normal * 0.5 + 0.5, 1.0);
        """.trimIndent())
        val fixturePrefix = arguments.getString("glassExactFixturePrefix")
        val fixtures = fixtures().filter { fixturePrefix == null || it.name.startsWith(fixturePrefix) }
        check(fixtures.isNotEmpty()) { "No exact geometry fixture matches $fixturePrefix" }
        val selected = mutableStateOf(fixtures.first())
        val phase = mutableStateOf(0)
        val report = JSONObject().put("scope", "raw CPU cubic versus hardware AGSL geometry and independent CPU Path coverage; no native-appearance claim")
            .put("production_shader_sha256", sha(production))
            .put("diagnostic_shader_sha256", sha(diagnostic))
            .put("forced_legacy", forceLegacy)
            .put("fixture_prefix", fixturePrefix)
            .put("diagnostic", "magenta=invalid exact helper or exact-dispatch fallback; helper frame R=(exactHelperSignedDepth+4)/8; dispatch frame R=actualMainDispatchInset/8; both GB=actualMainDispatchNormal/2+0.5")
            .put("legacy_contract", "glassExactForceLegacy changes only instrumentation setup: old mode1/3 and original profile packing; exact uniforms remain solely for the separately labelled helper buffer; expectations and tolerances are unchanged")
            .put("tolerance", "8-bit PixelCopy: signed depth 0.05dp, normal component 0.025; exact joints allow either actual one-sided CPU tangent")
        val results = JSONArray()
        val failures = mutableListOf<String>()
        compose.setContent { Pane(selected.value, diagnostic, phase.value, forceLegacy) }
        try {
            fixtures.forEach { fixture ->
                compose.runOnIdle { selected.value = fixture; phase.value = 0 }
                val helperAndNormal = capture()
                compose.runOnIdle { phase.value = 1 }
                val dispatch = capture()
                compose.runOnIdle { phase.value = 2 }
                val mask = capture()
                try {
                    save(helperAndNormal, File(directory, "${fixture.name}-exact-helper-and-dispatch-normal.png"))
                    save(dispatch, File(directory, "${fixture.name}-actual-dispatch-inset.png"))
                    save(mask, File(directory, "${fixture.name}-cpu-path.png"))
                    assertEquals(fixture.width, helperAndNormal.width)
                    assertEquals(fixture.height, helperAndNormal.height)
                    assertEquals(fixture.width, dispatch.width)
                    assertEquals(fixture.height, dispatch.height)
                    val points = JSONArray()
                    samplePixels(fixture).forEach { (x, y) ->
                        val point = LiquidGlassRawPathOracle.V((x + .5) / fixture.density, (y + .5) / fixture.density)
                        val expected = fixture.geometry(point)
                        val pixel = helperAndNormal.getPixel(x, y)
                        val dispatchPixel = dispatch.getPixel(x, y)
                        val red = android.graphics.Color.red(pixel)
                        val green = android.graphics.Color.green(pixel)
                        val blue = android.graphics.Color.blue(pixel)
                        val dispatchRed = android.graphics.Color.red(dispatchPixel)
                        val invalid = (red > 248 && green < 8 && blue > 248) ||
                            (dispatchRed > 248 && android.graphics.Color.green(dispatchPixel) < 8 && android.graphics.Color.blue(dispatchPixel) > 248)
                        val expectedDepth = ((expected.signedInset + 4.0) / 8.0).coerceIn(0.0, 1.0)
                        val depthError = abs(red / 255.0 - expectedDepth) * 8.0
                        val expectedDispatchDepth = (max(expected.signedInset, 0.0) / 8.0).coerceIn(0.0, 1.0)
                        val dispatchDepthError = abs(dispatchRed / 255.0 - expectedDispatchDepth) * 8.0
                        val normalError = expected.normals.minOf { n ->
                            max(abs(green / 255.0 * 2.0 - 1.0 - n.x), abs(blue / 255.0 * 2.0 - 1.0 - n.y))
                        }
                        val cpuCoverage = android.graphics.Color.red(mask.getPixel(x, y))
                        val maskValid = when {
                            expected.signedInset * fixture.density > .8 -> cpuCoverage > 239
                            expected.signedInset * fixture.density < -.8 -> cpuCoverage < 16
                            else -> true // Antialias boundary pixels retain their measured coverage.
                        }
                        val valid = !invalid && depthError <= .05 && dispatchDepthError <= .05 && normalError <= .025 && maskValid
                        if (!valid) failures += "${fixture.name} ($x,$y): forcedLegacy=$forceLegacy invalid=$invalid helperDepth=$depthError actualDispatchDepth=$dispatchDepthError actualDispatchNormal=$normalError cpuMask=$cpuCoverage"
                        points.put(JSONObject().put("pixel", JSONArray(listOf(x, y)))
                            .put("expected_signed_inset_dp", expected.signedInset)
                            .put("expected_cpu_one_sided_normals", JSONArray(expected.normals.map { JSONArray(listOf(it.x, it.y)) }))
                            .put("helper_and_dispatch_normal_rgba", JSONArray(listOf(red, green, blue, android.graphics.Color.alpha(pixel))))
                            .put("actual_dispatch_inset_rgba", JSONArray(listOf(dispatchRed, android.graphics.Color.green(dispatchPixel),
                                android.graphics.Color.blue(dispatchPixel), android.graphics.Color.alpha(dispatchPixel))))
                            .put("expected_actual_dispatch_inset_dp", max(expected.signedInset, 0.0))
                            .put("cpu_path_coverage", cpuCoverage).put("exact_helper_signed_depth_error_dp", depthError)
                            .put("actual_dispatch_inset_error_dp", dispatchDepthError)
                            .put("actual_dispatch_normal_component_error", normalError).put("valid", valid))
                    }
                    results.put(JSONObject().put("name", fixture.name).put("density", fixture.density)
                        .put("forced_legacy", forceLegacy)
                        .put("actual_dispatch_mode", if (forceLegacy) (if (fixture.sources.size == 2) 3 else 1) else (if (fixture.sources.size == 2) 5 else 4))
                        .put("size_px", JSONArray(listOf(fixture.width, fixture.height)))
                        .put("raw_operations", JSONArray(fixture.sources.map { source -> JSONArray(source.operations.map { it.toString() }) }))
                        .put("raw_operations_sha256", JSONArray(fixture.sources.map { sha(it.operations.joinToString("\n")) }))
                        .put("exact_uniforms", JSONArray(fixture.sources.map { JSONArray(checkNotNull(it.exact?.pack(fixture.density)).toList()) }))
                        .put("samples", points))
                } finally { helperAndNormal.recycle(); dispatch.recycle(); mask.recycle() }
            }
        } finally {
            report.put("fixtures", results).put("failures", JSONArray(failures))
                .put("status", if (failures.isEmpty() && results.length() == fixtures.size) "passed" else "failed")
            File(directory, "result.json").writeText(report.toString(2))
        }
        assertTrue("Raw CPU/GPU contour mismatch: ${failures.take(20).joinToString()}; artifacts=$directory", failures.isEmpty())
    }

    @Composable private fun Pane(fixture: Fixture, source: String, phase: Int, forceLegacy: Boolean) {
        val view = LocalView.current
        SideEffect {
            contentView = view
            var context = view.context
            while (context !is Activity && context is ContextWrapper) context = context.baseContext
            window = (context as Activity).window
        }
        val white = remember { Bitmap.createBitmap(intArrayOf(android.graphics.Color.WHITE), 1, 1, Bitmap.Config.ARGB_8888) }
        DisposableEffect(white) { onDispose { white.recycle() } }
        val shader = remember(source, white) { RuntimeShader(source).apply {
            setInputShader("uTexture", BitmapShader(white, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
        } }
        val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
        // Physical-pixel dimensions keep the crop and oracle on exactly the same HWUI grid.
        val localDensity = androidx.compose.ui.platform.LocalDensity.current.density
        Box(Modifier.background(Color.Black).padding(8.dp)) {
            Canvas(Modifier.requiredSize((fixture.width / localDensity).dp, (fixture.height / localDensity).dp).testTag("exact-pane")) {
                val canvas = drawContext.canvas.nativeCanvas
                check(canvas.isHardwareAccelerated)
                if (phase == 2) {
                    paint.shader = null
                    paint.color = android.graphics.Color.WHITE
                    val path = if (fixture.sources.size == 1) fixture.sources[0].path else
                        Path.combine(PathOperation.Union, fixture.sources[0].path, fixture.sources[1].path)
                    canvas.drawPath(path.toAndroidPath(), paint)
                } else {
                    fun dp(path: Path) = Path().apply { addPath(path)
                        transform(androidx.compose.ui.graphics.Matrix().apply { scale(1f / fixture.density, 1f / fixture.density) }) }
                    val uniforms = LiquidGlassShader.build(size = fixture.sizeDp,
                        corners = LiquidGlassCornerRadii(0f, 0f, 0f, 0f), style = LiquidGlassStyle(Color.White),
                        density = fixture.density, atlasSizePx = size,
                        sampling = LiquidGlassShader.sampling(Offset.Zero, fixture.density, Rect(Offset.Zero, size)),
                        outline = fixture.pathDp,
                        dualContours = if (fixture.sources.size == 2) dp(fixture.sources[0].path) to dp(fixture.sources[1].path) else null,
                        exactContours = if (forceLegacy) null else fixture.sources.map { checkNotNull(it.exact) })
                    val expectedMode = if (forceLegacy) (if (fixture.sources.size == 2) 3f else 1f) else (if (fixture.sources.size == 2) 5f else 4f)
                    check(uniforms.getValue("uOutlined")[0] == expectedMode)
                    uniforms.forEach { (name, values) -> shader.setFloatUniform(name, values) }
                    if (forceLegacy) {
                        // Preserve real legacy profile packing/dispatch. These arrays feed only
                        // the separately labelled helper query added by this diagnostic return.
                        shader.setFloatUniform("uExact1", checkNotNull(fixture.sources[0].exact?.pack(fixture.density)))
                        shader.setFloatUniform("uExact2", fixture.sources.getOrNull(1)?.exact?.pack(fixture.density)
                            ?: FloatArray(LIQUID_GLASS_EXACT_FLOATS))
                    }
                    shader.setFloatUniform("uExactDiagnosticPhase", phase.toFloat())
                    paint.shader = shader
                    canvas.drawRect(0f, 0f, size.width, size.height, paint)
                }
            }
        }
    }

    private fun fixtures(): List<Fixture> {
        fun contour(rect: Rect, top: Float, bottom: Float = top) = Source(LiquidGlassContourOutline(LiquidGlassSuperellipse.buildPath(
            LiquidGlassSuperellipse.Rect2(rect.left, rect.top, rect.right, rect.bottom),
            LiquidGlassSuperellipse.Radii(LiquidGlassSuperellipse.Size2(top, top), LiquidGlassSuperellipse.Size2(top, top),
                LiquidGlassSuperellipse.Size2(bottom, bottom), LiquidGlassSuperellipse.Size2(bottom, bottom)))))
        fun single(name: String, w: Int, h: Int, radius: Float, density: Float = 1f, bottom: Float = radius) =
            Fixture(name, w, h, density, listOf(contour(Rect(0f, 0f, w.toFloat(), h.toFloat()), radius * density, bottom * density)))
        val anchor = contour(Rect(10.5f, 10.5f, 380.5f, 61.833332f), 8f)
        fun dual(name: String, menu: Rect, radius: Float) = Fixture(name, 391, 341, 1f, listOf(anchor, contour(menu, radius)))
        // Derived terminal geometry from the observed card/session and five row bounds;
        // retain the half-pixel menu origin and use the production motion calculation.
        val actualDensity = 2.975f
        val terminal = LanguageMenuMotion().apply { finish() }.geometry(
            Rect(70f, 536f, 1147f, 689f), Rect(236.5f, 536f, 980.5f, 1221f))
        val carrier = Rect(minOf(terminal.anchor.left, terminal.material.left), minOf(terminal.anchor.top, terminal.material.top),
            maxOf(terminal.anchor.right, terminal.material.right), maxOf(terminal.anchor.bottom, terminal.material.bottom))
        val localAnchor = terminal.anchor.translate(-carrier.topLeft)
        val localMenu = terminal.material.translate(-carrier.topLeft)
        val tinyAnchor = contour(localAnchor, minOf(8f * actualDensity, minOf(localAnchor.width, localAnchor.height) / 2f))
        val actualMenu = contour(localMenu, 26f * actualDensity)
        // Production foreground-free row ROI centers, translated into this carrier's pixel grid.
        val rowProbePixels = (0..4).map { row ->
            floor(807.7f - carrier.left + 53.549988f / 2f).toInt() to floor(628.5f + 125f * row - carrier.top).toInt()
        }.toSet()
        fun terminalFixture(name: String, sources: List<Source>) = Fixture(name,
            kotlin.math.ceil(carrier.width.toDouble()).toInt(), kotlin.math.ceil(carrier.height.toDouble()).toInt(), actualDensity, sources, rowProbePixels)
        fun dock(name: String, width: Int, height: Int, density: Float, direction: LayoutDirection) : Fixture {
            val shape = DockPlatterShape()
            val outline = shape.createOutline(Size(width.toFloat(), height.toFloat()), direction, Density(density)) as Outline.Generic
            val exact = checkNotNull(shape.exactContoursFor(outline)).single()
            // The independent CPU mask draws the original Kyant path, never a descriptor reconstruction.
            return Fixture(name, width, height, density, listOf(Source(exact.operations, outline.path, exact)))
        }
        return listOf(single("menu32-odd", 251, 147, 32f), single("menu32-even", 250, 146, 32f),
            single("alert34", 321, 173, 34f), single("sheet36-20", 343, 420, 36f, bottom = 20f),
            single("menu32-density275", 689, 403, 32f, 2.75f),
            Fixture("anchor8-wide", 391, 81, 1f, listOf(anchor)),
            dual("language-overlap-mid", Rect(70.25f, 43.5f, 320.25f, 273.5f), 70.5f),
            dual("language-open", Rect(70.25f, 80.5f, 320.25f, 310.5f), 26f),
            dual("language-closing", Rect(35.25f, 35.5f, 355.25f, 190.5f), 65f),
            terminalFixture("language-terminal-contained-density2975", listOf(tinyAnchor, actualMenu)),
            terminalFixture("language-terminal-tinyanchor-density2975", listOf(tinyAnchor)),
            terminalFixture("language-terminal-menu-density2975", listOf(actualMenu)),
            dock("dock-kyant-three", 274, 62, 1f, LayoutDirection.Ltr),
            dock("dock-kyant-three-density275", 754, 171, 2.75f, LayoutDirection.Ltr),
            dock("dock-kyant-four-density2975-rtl", 919, 184, 2.975f, LayoutDirection.Rtl))
    }

    private fun samplePixels(fixture: Fixture): Set<Pair<Int, Int>> {
        val points = linkedSetOf<Pair<Int, Int>>()
        fun add(point: LiquidGlassRawPathOracle.V) {
            val x = floor(point.x * fixture.density).toInt()
            val y = floor(point.y * fixture.density).toInt()
            if (x in 0 until fixture.width && y in 0 until fixture.height) points += x to y
        }
        fixture.oracles.forEach { oracle ->
            add(oracle.center)
            listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1).forEach { (x, y) ->
                add(oracle.center + LiquidGlassRawPathOracle.V(x / fixture.density.toDouble(), y / fixture.density.toDouble()))
            }
            oracle.segments.forEach { segment ->
                // Both sides of every joint, including the actual closing Line; no tangent averaging.
                listOf(0.0001, .02, .25, .5, .75, .98, .9999).forEach { t ->
                    val tangent = segment.tangent(t)
                    val length = tangent.length()
                    if (length > 1e-6) {
                        val normal = LiquidGlassRawPathOracle.V(tangent.y / length, -tangent.x / length)
                        listOf(-1.0, 0.0, .75, 3.0, 7.0).forEach { depth -> add(segment.at(t) - normal * depth) }
                    }
                }
            }
        }
        fixture.extraPixels.forEach { (x, y) -> if (x in 0 until fixture.width && y in 0 until fixture.height) points += x to y }
        return points
    }

    private fun capture(): Bitmap {
        compose.waitForIdle()
        val committed = CountDownLatch(1)
        compose.runOnUiThread {
            assertTrue(contentView.isHardwareAccelerated)
            contentView.rootView.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
            contentView.rootView.postInvalidateOnAnimation()
        }
        assertTrue("HWUI frame must commit", committed.await(5, TimeUnit.SECONDS))
        val root = contentView.rootView
        val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val copied = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        PixelCopy.request(window, full, { result = it; copied.countDown() }, Handler(Looper.getMainLooper()))
        assertTrue(copied.await(5, TimeUnit.SECONDS))
        assertEquals(PixelCopy.SUCCESS, result)
        val origin = IntArray(2)
        compose.runOnUiThread { contentView.getLocationInWindow(origin) }
        val bounds = compose.onNodeWithTag("exact-pane").fetchSemanticsNode().boundsInRoot
        assertEquals(bounds.left.roundToInt().toFloat(), bounds.left, .01f)
        assertEquals(bounds.top.roundToInt().toFloat(), bounds.top, .01f)
        return try { Bitmap.createBitmap(full, origin[0] + bounds.left.roundToInt(), origin[1] + bounds.top.roundToInt(),
            bounds.width.roundToInt(), bounds.height.roundToInt()) } finally { full.recycle() }
    }

    private fun save(bitmap: Bitmap, file: File) { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
    private fun sha(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
