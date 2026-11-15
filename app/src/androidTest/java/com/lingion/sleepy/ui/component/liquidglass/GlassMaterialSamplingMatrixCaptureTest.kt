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
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Different backings must retain changes to their actual GPU sampling input. */
@RunWith(AndroidJUnit4::class)
class GlassMaterialSamplingMatrixCaptureTest {
    @get:Rule val compose = createComposeRule()
    private data class Scene(val pattern: Int, val material: Int)

    @Test fun raisedSliderLensRetainsDifferentInputsAcrossTheMaterialMatrix() {
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
        val firstByMaterial = mutableMapOf<Int, Bitmap>()
        val patternNames = listOf("stripes", "fine-grid", "light-dark-blocks")
        val materialNames = listOf("translucent-card", "regular-glass", "frosted-glass")
        try {
            for (material in 0..2) {
                for (pattern in 0..2) {
                    compose.runOnIdle { active.value = Scene(pattern, material) }
                    compose.waitForIdle()
                    val name = "${patternNames[pattern]}-${materialNames[material]}"
                    val child = compose.onNodeWithTag("matrix-lens").captureToImage().asAndroidBitmap()
                    val first = firstByMaterial.getOrPut(material) { child }
                    try {
                        val delta = difference(first, child)
                        // A constant passthrough or missing source cannot satisfy all nine.
                        if (pattern != 0) assertTrue("$name must retain a distinct optical input; mean RGB delta=$delta", delta > 2.0)
                    } finally {
                        if (child !== first) child.recycle()
                    }
                }
            }
        } finally {
            firstByMaterial.values.forEach(Bitmap::recycle)
        }
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
