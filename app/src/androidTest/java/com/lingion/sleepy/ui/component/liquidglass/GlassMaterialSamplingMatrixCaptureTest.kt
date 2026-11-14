package com.lingion.sleepy.ui.component.liquidglass

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/** Retained GPU PNGs and measurements across patterned inputs and different backings. */
@RunWith(AndroidJUnit4::class)
class GlassMaterialSamplingMatrixCaptureTest {
    @get:Rule val compose = createComposeRule()
    private data class Scene(val pattern: Int, val material: Int)

    @Test fun recordPatternAndMaterialMatrixWithRaisedNativeSliderLens() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val active = mutableStateOf(Scene(0, 0))
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost(modifier = Modifier.size(240.dp, 200.dp).testTag("sampling-matrix"), background = Color.White) {
                        GlassBackground(0f, Modifier.size(240.dp, 200.dp)) {
                            Canvas(Modifier.size(240.dp, 200.dp)) {
                                when (active.value.pattern) {
                                    0 -> {
                                        drawRect(Color.White)
                                        val stripe = 12.dp.toPx()
                                        var x = 0f
                                        while (x < size.width) {
                                            drawRect(Color.Black, Offset(x, 0f), Size(stripe, size.height))
                                            x += stripe * 2f
                                        }
                                    }
                                    1 -> {
                                        drawRect(Color(0xFFF0F0F0))
                                        val pitch = 6.dp.toPx()
                                        var p = 0f
                                        while (p < size.width.coerceAtLeast(size.height)) {
                                            drawLine(Color(0xFF202020), Offset(p, 0f), Offset(p, size.height), 1.dp.toPx())
                                            drawLine(Color(0xFF202020), Offset(0f, p), Offset(size.width, p), 1.dp.toPx())
                                            p += pitch
                                        }
                                    }
                                    else -> {
                                        drawRect(Color(0xFF101820))
                                        drawRect(Color(0xFFF8E8C0), Offset(size.width / 2f, 0f), Size(size.width / 2f, size.height / 2f))
                                        drawRect(Color(0xFFE8F2FF), Offset(0f, size.height / 2f), Size(size.width / 2f, size.height / 2f))
                                        drawRect(Color(0xFF202B60), Offset(size.width / 2f, size.height / 2f), Size(size.width / 2f, size.height / 2f))
                                    }
                                }
                            }
                        }
                        key(active.value.material) {
                            val shape = RoundedCornerShape(28.dp)
                            val scope = rememberGlassMaterialScope(shape)
                            val base = Modifier.offset(20.dp, 20.dp).size(200.dp, 144.dp)
                            val backing = if (active.value.material == 0) {
                                base.glassSurfaceBackground(Color.White.copy(alpha = .25f), shape, materialScope = scope)
                            } else {
                                base.trackGlassMaterial(scope).glassControl(shape, enabled = false,
                                    role = if (active.value.material == 1) GlassMaterialRole.Regular else GlassMaterialRole.Menu,
                                    exportedBackdrop = scope?.exported)
                            }
                            Box(backing) {
                                GlassMaterialContent(scope) {
                                    val motion = remember {
                                        // Hold an existing product control at its measured
                                        // fully raised state; its renderer is unchanged.
                                        GlassControlMotion(0f, GlassControlMotionSpec.NativeSlider).apply { elevation.jumpTo(1f) }
                                    }
                                    GlassSliderThumb(trackBackdrop = null, range = true, motionOverride = motion,
                                        modifier = Modifier.offset(70.dp, 52.dp).testTag("matrix-lens"))
                                }
                            }
                        }
                    }
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "glass-material-sampling").apply { mkdirs() }
        val csv = StringBuilder("pattern,material,widthPx,heightPx,meanR,meanG,meanB,lumaStdDev,edgeMaxChroma,deltaFromStripes\n")
        val firstByMaterial = mutableMapOf<Int, Bitmap>()
        val patternNames = listOf("stripes", "fine-grid", "light-dark-blocks")
        val materialNames = listOf("translucent-card", "regular-glass", "frosted-glass")
        for (material in 0..2) {
            for (pattern in 0..2) {
                compose.runOnIdle { active.value = Scene(pattern, material) }
                compose.waitForIdle()
                val name = "${patternNames[pattern]}-${materialNames[material]}"
                val parent = compose.onNodeWithTag("sampling-matrix").captureToImage().asAndroidBitmap()
                val child = compose.onNodeWithTag("matrix-lens").captureToImage().asAndroidBitmap()
                File(directory, "$name.png").outputStream().use { parent.compress(Bitmap.CompressFormat.PNG, 100, it) }
                File(directory, "$name-lens.png").outputStream().use { child.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val first = firstByMaterial.getOrPut(material) { child }
                val delta = difference(first, child)
                val metrics = metrics(child)
                csv.append("${patternNames[pattern]},${materialNames[material]},${child.width},${child.height},${metrics.joinToString(",")},$delta\n")
                File(directory, "measurements.csv").writeText(csv.toString())
                // These backgrounds have deliberately different tone/structure.
                // A constant passthrough or missing source cannot satisfy all nine.
                if (pattern != 0) assertTrue("$name must retain a distinct optical input; mean RGB delta=$delta", delta > 2.0)
            }
        }
        android.util.Log.i("GlassMaterialSampling", "GPU PNGs and measurements: ${directory.absolutePath}")
    }

    private fun metrics(bitmap: Bitmap): List<Double> {
        var r = 0.0; var g = 0.0; var b = 0.0; var sum = 0.0; var squares = 0.0
        var edgeChroma = 0
        val count = bitmap.width * bitmap.height
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            val red = android.graphics.Color.red(pixel); val green = android.graphics.Color.green(pixel); val blue = android.graphics.Color.blue(pixel)
            r += red; g += green; b += blue
            val luma = .2126 * red + .7152 * green + .0722 * blue
            sum += luma; squares += luma * luma
            if (x < bitmap.width / 5 || x >= bitmap.width * 4 / 5 || y < bitmap.height / 5 || y >= bitmap.height * 4 / 5) {
                edgeChroma = maxOf(edgeChroma, maxOf(red, green, blue) - minOf(red, green, blue))
            }
        }
        return listOf(r / count, g / count, b / count, sqrt((squares / count - (sum / count) * (sum / count)).coerceAtLeast(0.0)), edgeChroma.toDouble())
    }

    private fun difference(first: Bitmap, second: Bitmap): Double {
        check(first.width == second.width && first.height == second.height)
        var total = 0.0
        for (y in 0 until first.height) for (x in 0 until first.width) {
            val a = first.getPixel(x, y); val b = second.getPixel(x, y)
            total += abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) +
                abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) +
                abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))
        }
        return total / (first.width * first.height * 3)
    }
}
