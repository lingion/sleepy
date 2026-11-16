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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in GPU geometry diagnostic, not a native iOS appearance or alpha comparison.
 * Only the production shader's final return is replaced: its actual edge branch
 * executes in HWUI and emits opaque grayscale, with four dp represented by 255.
 * Native Canvas Paint executes the geometry directly; this does not validate
 * RenderEffect scheduling, backdrop sampling, or production material alpha.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class LiquidGlassCornerRenderTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var contentView: View
    private lateinit var window: Window

    private data class Sample(val name: String, val widthDp: Float, val heightDp: Float) {
        var geometry: Geometry? = null
    }

    private data class Geometry(val sizePx: Size, val density: Float)

    @Test
    fun circularCpuCornersAndShaderEdgesAgreeAtEveryShoulder() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Enable explicitly with glassCornerCheck=true", arguments.getString("glassCornerCheck") == "true")
        val source = LiquidGlassShader.shaderSource(instrumentation.targetContext)
        val finalReturn = "return half4(min(glass, float3(coverage)), coverage);"
        check(source.indexOf(finalReturn) >= 0 && source.indexOf(finalReturn) == source.lastIndexOf(finalReturn)) {
            "The production shader must have exactly one known final return"
        }
        val diagnostic = source.replace(finalReturn,
            "return half4(float3(clamp(edge / 4.0, 0.0, 1.0)), 1.0);")
        val samples = listOf(Sample("toggle-rest", 37f, 24f),
            Sample("toggle-held", 58f, 115f / 3f), Sample("dock-held", 94f, 54f))
        compose.setContent {
            RememberWindow()
            Column(Modifier.background(Color.Black).padding(16.dp)) {
                samples.forEach { sample ->
                    DiagnosticPane(sample, diagnostic)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        compose.waitForIdle()
        assertTrue("The diagnostic requires a real hardware-accelerated window", contentView.isHardwareAccelerated)

        val failures = mutableListOf<String>()
        samples.forEach { sample ->
            val bitmap = capture(sample.name)
            try {
                val geometry = compose.runOnIdle { checkNotNull(sample.geometry) }
                val failure = compare(geometry, bitmap)
                if (failure.isNotEmpty()) failures += "${sample.name}: $failure"
            } finally {
                bitmap.recycle()
            }
        }
        assertTrue("CPU circular arc and production GPU edge diverged: ${failures.joinToString()}",
            failures.isEmpty())
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
        val shape = remember { RoundedCornerShape(50) }
        val whitePixel = remember { Bitmap.createBitmap(intArrayOf(android.graphics.Color.WHITE), 1, 1, Bitmap.Config.ARGB_8888) }
        DisposableEffect(whitePixel) { onDispose { whitePixel.recycle() } }
        val shader = remember(diagnostic, sample.name, whitePixel) {
            RuntimeShader(diagnostic).apply {
                // The final diagnostic return never samples this child. Bind a
                // real shader to retain the complete production shader contract.
                setInputShader("uTexture", BitmapShader(whitePixel, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
            }
        }
        val paint = remember { Paint() }
        val style = remember { LiquidGlassStyle.controlLens(lightColorScheme(), dark = false) }
        Canvas(Modifier.requiredSize(sample.widthDp.dp, sample.heightDp.dp).testTag(sample.name)) {
            if (size.width <= 0f || size.height <= 0f) return@Canvas
            val canvas = drawContext.canvas.nativeCanvas
            check(canvas.isHardwareAccelerated) { "A software Canvas cannot execute this diagnostic" }
            // Use the same real CPU outline classifier as production consumers.
            val outline = shape.createOutline(size, LayoutDirection.Ltr, density)
            check(outline is Outline.Rounded) { "The actual control outline must be circular Rounded" }
            val corners = checkNotNull(circularGlassCornerRadii(outline, density.density)) {
                "The production classifier must recognize the actual RoundedCornerShape(50)"
            }
            sample.geometry = Geometry(size, density.density)
            LiquidGlassShader.build(
                size = Size(size.width / density.density, size.height / density.density),
                corners = corners,
                style = style,
                density = density.density,
                atlasSizePx = size,
                sampling = LiquidGlassShader.sampling(Offset.Zero, density.density, Rect(Offset.Zero, size)),
                circularCorners = true,
            ).forEach { (name, values) -> shader.setFloatUniform(name, values) }
            // No CPU AA clip: every sampled pixel must retain its encoded value.
            // The expected curve below independently describes the actual CPU outline.
            // Assign only after uniforms are complete so Paint sees the current shader.
            paint.shader = shader
            canvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
    }

    private fun compare(geometry: Geometry, bitmap: Bitmap): String {
        assertEquals(geometry.sizePx.width.roundToInt(), bitmap.width)
        assertEquals(geometry.sizePx.height.roundToInt(), bitmap.height)
        val width = geometry.sizePx.width / geometry.density
        val height = geometry.sizePx.height / geometry.density
        val radius = minOf(width, height) / 2.0
        val capCenterFromMiddle = width / 2.0 - radius
        val counts = IntArray(4)
        val errors = DoubleArray(4)
        var opaque = true
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val localX = (x + .5) / geometry.density
            val localY = (y + .5) / geometry.density
            val centeredX = localX - width / 2.0
            val centeredY = localY - height / 2.0
            val capX = abs(centeredX) - capCenterFromMiddle
            if (capX <= 0.0) continue
            val angle = Math.toDegrees(atan2(abs(centeredY), capX))
            if (angle < 55.0 || angle > 85.0) continue
            // Independent analytic circle distance, not a copy of AGSL or its curve table.
            val expected = radius - hypot(capX, centeredY)
            if (expected < .30 || expected > 1.20) continue
            val pixel = bitmap.getPixel(x, y)
            val red = android.graphics.Color.red(pixel)
            val green = android.graphics.Color.green(pixel)
            val blue = android.graphics.Color.blue(pixel)
            val measured = (red + green + blue) / (3.0 * 255.0) * 4.0
            val error = abs(measured - expected)
            val corner = (if (centeredY >= 0.0) 2 else 0) + if (centeredX >= 0.0) 1 else 0
            counts[corner]++
            errors[corner] = maxOf(errors[corner], error)
            opaque = opaque && android.graphics.Color.alpha(pixel) == 255
        }
        val failure = buildList {
            if (!opaque) add("diagnostic output was not opaque")
            if (counts.any { it == 0 }) add("all four shoulders must contain interior sample pixels")
            if (errors.any { it > .05 }) add("per-corner maximum error exceeds .05 dp")
        }.joinToString("; ")
        return failure
    }

    private fun capture(tag: String): Bitmap {
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
        assertEquals("The diagnostic must start on an actual window pixel", bounds.left.roundToInt().toFloat(), bounds.left, .01f)
        assertEquals("The diagnostic must start on an actual window pixel", bounds.top.roundToInt().toFloat(), bounds.top, .01f)
        return try {
            Bitmap.createBitmap(full, origin[0] + bounds.left.roundToInt(), origin[1] + bounds.top.roundToInt(),
                bounds.width.roundToInt(), bounds.height.roundToInt())
        } finally {
            full.recycle()
        }
    }
}
