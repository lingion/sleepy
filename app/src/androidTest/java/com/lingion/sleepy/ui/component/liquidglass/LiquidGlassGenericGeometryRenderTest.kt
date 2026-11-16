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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
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
    )

    @Test
    fun realGenericContoursHaveFiniteGeometryAtExactCenterAndAdjacentPixels() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Enable explicitly with glassGenericCheck=true",
            arguments.getString("glassGenericCheck") == "true")
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
        val failures = mutableListOf<String>()
        samples.forEach { sample ->
            val bitmap = capture(sample.name)
            try {
                assertEquals(sample.widthPx, bitmap.width)
                assertEquals(sample.heightPx, bitmap.height)
                assertTrue(bitmap.width % 2 == 1 && bitmap.height % 2 == 1)
                listOf(0 to 0, -1 to 0, 1 to 0, 0 to -1, 0 to 1).forEach { (dx, dy) ->
                    val x = bitmap.width / 2 + dx
                    val y = bitmap.height / 2 + dy
                    val pixel = bitmap.getPixel(x, y)
                    val r = android.graphics.Color.red(pixel)
                    val g = android.graphics.Color.green(pixel)
                    val b = android.graphics.Color.blue(pixel)
                    val a = android.graphics.Color.alpha(pixel)
                    val valid = r < 16 && g > 239 && b < 16 && a == 255
                    if (!valid) failures += "${sample.name} offset=($dx,$dy), rgba=($r,$g,$b,$a)"
                }
            } finally {
                bitmap.recycle()
            }
        }
        assertTrue("Production Generic geometry was invalid: ${failures.joinToString()}",
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
            uniforms.forEach { (name, values) -> shader.setFloatUniform(name, values) }
            paint.shader = shader
            canvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
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
