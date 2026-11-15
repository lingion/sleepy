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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in hardware geometry diagnostic, not native appearance, alpha, or backdrop validation.
 * The complete production shader executes with only its final return replaced: green means
 * finite nonnegative inset and a finite unit normal; red means that contract was violated.
 * Density 1 and odd actual pixel sizes deliberately make the center exactly representable.
 * The dual control uses two identical real convex paths, whose union is the same CPU outline.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class LiquidGlassGenericGeometryRenderTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var contentView: View
    private lateinit var window: Window

    private data class Sample(
        val name: String,
        val widthPx: Int,
        val heightPx: Int,
        val radius: Float,
        val dual: Boolean = false,
    ) {
        var geometry: JSONObject? = null
    }

    @Test
    fun realGenericContoursHaveFiniteGeometryAtExactCenterAndAdjacentPixels() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Enable explicitly with glassGenericCheck=true",
            arguments.getString("glassGenericCheck") == "true")
        val runId = (arguments.getString("glassGenericRunId") ?: System.currentTimeMillis().toString())
            .replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null),
            "glass-generic-geometry/$runId").apply { check(mkdirs() || isDirectory) }
        val report = JSONObject().put("status", "running")
            .put("diagnostic", "complete production AGSL; final return encodes valid geometry green, invalid red")
            .put("scope", "hardware Canvas geometry only; not RenderEffect, native appearance, backdrop, or material alpha")
            .put("density_contract", "density=1; dp equals physical px to hit an exact odd-size pixel center")
        val reportFile = File(directory, "result.json")
        reportFile.writeText(report.toString(2))
        try {
            val source = LiquidGlassShader.shaderSource(instrumentation.targetContext)
            val finalReturn = "return half4(min(glass, float3(coverage)), coverage);"
            check(source.indexOf(finalReturn) >= 0 && source.indexOf(finalReturn) == source.lastIndexOf(finalReturn))
            // Comparisons reject both infinity and NaN; the bound is far beyond any fixture.
            // A unit-normal check also rejects a silently zeroed invalid vector.
            val diagnostic = source.replace(finalReturn, """
                float diagnosticNorm2 = dot(normal, normal);
                bool diagnosticValid = inset >= 0.0 && inset < 1000000.0
                    && normal.x >= -1.01 && normal.x <= 1.01
                    && normal.y >= -1.01 && normal.y <= 1.01
                    && diagnosticNorm2 > 0.99 && diagnosticNorm2 < 1.01;
                return diagnosticValid ? half4(0.0, 1.0, 0.0, 1.0) : half4(1.0, 0.0, 0.0, 1.0);
            """.trimIndent())
            report.put("production_shader_sha256", MessageDigest.getInstance("SHA-256")
                .digest(source.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) })
            val samples = listOf(
                Sample("menu24-single", 251, 147, 24f),
                Sample("alert34-single", 321, 173, 34f),
                Sample("menu24-dual-control", 251, 147, 24f, dual = true),
            )
            compose.setContent {
                RememberWindow()
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    Column(Modifier.background(Color.Black).padding(8.dp)) {
                        samples.forEach { sample ->
                            DiagnosticPane(sample, diagnostic)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
            compose.waitForIdle()
            assertTrue("The diagnostic requires a hardware-accelerated window", contentView.isHardwareAccelerated)
            val results = JSONArray()
            val failures = mutableListOf<String>()
            samples.forEach { sample ->
                val bitmap = capture(sample.name, directory)
                try {
                    File(directory, "${sample.name}.png").outputStream().use {
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                    assertEquals(sample.widthPx, bitmap.width)
                    assertEquals(sample.heightPx, bitmap.height)
                    assertTrue(bitmap.width % 2 == 1 && bitmap.height % 2 == 1)
                    val points = JSONArray()
                    listOf(0 to 0, -1 to 0, 1 to 0, 0 to -1, 0 to 1).forEach { (dx, dy) ->
                        val x = bitmap.width / 2 + dx
                        val y = bitmap.height / 2 + dy
                        val pixel = bitmap.getPixel(x, y)
                        val r = android.graphics.Color.red(pixel)
                        val g = android.graphics.Color.green(pixel)
                        val b = android.graphics.Color.blue(pixel)
                        val a = android.graphics.Color.alpha(pixel)
                        val valid = r < 16 && g > 239 && b < 16 && a == 255
                        points.put(JSONObject().put("x_px", x).put("y_px", y)
                            .put("local_pixel_center", JSONArray(listOf(x + .5, y + .5)))
                            .put("center_offset_px", JSONArray(listOf(dx, dy)))
                            .put("rgba", JSONArray(listOf(r, g, b, a))).put("valid", valid))
                        if (!valid) failures += "${sample.name} offset=($dx,$dy), rgba=($r,$g,$b,$a)"
                    }
                    results.put(JSONObject().put("name", sample.name)
                        .put("actual_geometry", compose.runOnIdle { checkNotNull(sample.geometry) })
                        .put("points", points))
                } finally {
                    bitmap.recycle()
                }
            }
            report.put("samples", results).put("failures", JSONArray(failures))
                .put("status", if (failures.isEmpty()) "passed" else "failed")
            reportFile.writeText(report.toString(2))
            assertTrue("Production Generic geometry was invalid: ${failures.joinToString()}; artifacts=$directory",
                failures.isEmpty())
        } catch (failure: Throwable) {
            report.put("status", "failed").put("exception", "${failure.javaClass.name}: ${failure.message}")
            reportFile.writeText(report.toString(2))
            throw failure
        }
    }

    @Composable
    private fun RememberWindow() {
        val view = LocalView.current
        SideEffect {
            contentView = view
            var context = view.context
            while (context !is Activity && context is ContextWrapper) context = context.baseContext
            window = (context as Activity).window
        }
    }

    @Composable
    private fun DiagnosticPane(sample: Sample, diagnostic: String) {
        val density = LocalDensity.current
        val shape = remember(sample.radius) { FlClashGlassShape(sample.radius) }
        val whitePixel = remember {
            Bitmap.createBitmap(intArrayOf(android.graphics.Color.WHITE), 1, 1, Bitmap.Config.ARGB_8888)
        }
        DisposableEffect(whitePixel) { onDispose { whitePixel.recycle() } }
        val shader = remember(diagnostic, whitePixel) {
            RuntimeShader(diagnostic).apply {
                setInputShader("uTexture", BitmapShader(whitePixel, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
            }
        }
        val paint = remember { Paint() }
        val style = remember(sample.radius) {
            if (sample.radius == 34f) LiquidGlassStyle.alert(lightColorScheme(), dark = false)
            else LiquidGlassStyle.menu(lightColorScheme(), dark = false)
        }
        Canvas(Modifier.requiredSize(sample.widthPx.dp, sample.heightPx.dp).testTag(sample.name)) {
            if (size.width <= 0f || size.height <= 0f) return@Canvas
            check(density.density == 1f)
            check(size.width == sample.widthPx.toFloat() && size.height == sample.heightPx.toFloat())
            val canvas = drawContext.canvas.nativeCanvas
            check(canvas.isHardwareAccelerated)
            val outline = shape.createOutline(size, LayoutDirection.Ltr, density)
            check(outline is Outline.Generic) { "Use the actual production Generic superellipse" }
            val uniforms = LiquidGlassShader.build(
                size = size,
                corners = LiquidGlassCornerRadii(sample.radius, sample.radius, sample.radius, sample.radius),
                style = style,
                density = 1f,
                atlasSizePx = size,
                sampling = LiquidGlassShader.sampling(Offset.Zero, 1f, Rect(Offset.Zero, size)),
                outline = outline.path,
                dualContours = if (sample.dual) outline.path to outline.path else null,
            )
            check(uniforms.getValue("uOutlined")[0] == if (sample.dual) 3f else 1f)
            sample.geometry = JSONObject().put("size_px", JSONArray(listOf(size.width, size.height)))
                .put("density", density.density).put("outline", "actual FlClashGlassShape / Outline.Generic")
                .put("uOutlined", uniforms.getValue("uOutlined")[0])
                .put("uFrame", JSONArray(uniforms.getValue("uFrame").toList()))
                .put("pixel_center_in_pane", JSONArray(listOf(size.width / 2f, size.height / 2f)))
            uniforms.forEach { (name, values) -> shader.setFloatUniform(name, values) }
            paint.shader = shader
            canvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
    }

    private fun capture(tag: String, directory: File): Bitmap {
        compose.waitForIdle()
        val committed = CountDownLatch(1)
        compose.runOnUiThread {
            contentView.rootView.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
            contentView.rootView.postInvalidateOnAnimation()
        }
        assertTrue("HWUI frame must commit", committed.await(5, TimeUnit.SECONDS))
        val root = contentView.rootView
        val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val copied = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        PixelCopy.request(window, full, { result = it; copied.countDown() }, Handler(Looper.getMainLooper()))
        assertTrue("GPU frame copy must finish", copied.await(5, TimeUnit.SECONDS))
        assertEquals(PixelCopy.SUCCESS, result)
        val origin = IntArray(2)
        compose.runOnUiThread { contentView.getLocationInWindow(origin) }
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        File(directory, "$tag-full-window.png").outputStream().use {
            check(full.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        File(directory, "$tag-crop.json").writeText(JSONObject()
            .put("root_size_px", JSONArray(listOf(root.width, root.height)))
            .put("view_window_origin_px", JSONArray(origin.toList()))
            .put("bounds_in_root_px", JSONArray(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)))
            .toString(2))
        assertEquals(bounds.left.roundToInt().toFloat(), bounds.left, .01f)
        assertEquals(bounds.top.roundToInt().toFloat(), bounds.top, .01f)
        return try {
            Bitmap.createBitmap(full, origin[0] + bounds.left.roundToInt(), origin[1] + bounds.top.roundToInt(),
                bounds.width.roundToInt(), bounds.height.roundToInt())
        } finally {
            full.recycle()
        }
    }
}
