package com.lingion.sleepy.ui.component.liquidglass

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.nativeCanvas
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
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.BackdropRenderOptions
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Same-node provider replacement, observed through real effects and production GPU output. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class LiquidGlassDynamicShapeRenderTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var contentView: View
    private lateinit var window: Window

    private data class EffectObservation(val phase: Int, val expected: Shape, val actual: Shape,
        val receiver: BackdropEffectScope, val size: Size, val density: Float)
    private data class SourceObservation(val phase: Int, val size: Size, val hardware: Boolean)

    @Test fun retainedConsumerReplacesShapeProviderAtFixedSizeForEffectsAndGpuCorners() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Enable explicitly with glassDynamicShapeCheck=true", arguments.getString("glassDynamicShapeCheck") == "true")
        val phase = mutableStateOf(0)
        val narrow: Shape = AbsoluteRoundedCornerShape(4.dp)
        val rounded: Shape = AbsoluteRoundedCornerShape(40.dp)
        val observations = CopyOnWriteArrayList<EffectObservation>()
        val sourceDraws = CopyOnWriteArrayList<SourceObservation>()
        val failures = mutableListOf<String>()
        var physicalDensity = 0f

        compose.setContent {
            val view = LocalView.current
            val appContext = LocalContext.current
            val localDensity = LocalDensity.current.density
            val currentPhase = phase.value
            val expectedShape = if (currentPhase == 1) rounded else narrow
            SideEffect {
                contentView = view
                var activityContext = view.context
                while (activityContext !is Activity && activityContext is ContextWrapper) activityContext = activityContext.baseContext
                window = (activityContext as Activity).window
                physicalDensity = localDensity
            }
            // One retained source. Changing its color forces a visibly different input record;
            // recording the phase proves that the real backdrop draw ran for each capture.
            val backdrop = rememberCanvasBackdrop {
                val sourcePhase = phase.value
                sourceDraws += SourceObservation(sourcePhase, size, drawContext.canvas.nativeCanvas.isHardwareAccelerated)
                drawRect(when (sourcePhase) { 0 -> Color.Red; 1 -> Color.Green; else -> Color.Blue })
            }
            val options = remember { BackdropRenderOptions(placementLayer = false) }
            Box(Modifier.background(Color.Black).padding(12.dp)) {
                // No key, conditional consumer, external clip, or replacement graphics layer.
                // Capture expectedShape in this composition's lambda: reading phase directly
                // from an old shape lambda would conceal the stale-provider regression.
                Box(Modifier.size(128.dp, 96.dp).drawBehind { phase.value }
                    .drawPlainBackdrop(backdrop = backdrop, shape = { expectedShape }, effects = {
                        if (size.width.isFinite() && size.height.isFinite() && size.width > 0f && size.height > 0f) {
                            observations += EffectObservation(currentPhase, expectedShape, shape, this, size, density)
                        }
                        // An opaque neutral result distinguishes the full production effect
                        // from its red/green/blue input and from the blur/vibrancy fallback.
                        liquidGlass(appContext, LiquidGlassStyle(tint = Color.White,
                            contrast = 0.0, blur = 0.0, haze = 0.0, refraction = 0.0, bevel = 1.0,
                            saturation = 1.0, brightness = 1.0, rim = 0.0, endShade = 0.0,
                            dispersion = 0.0, rimBlur = 0.0, hairline = 0.0))
                    }, renderOptions = options).testTag("dynamic-shape-consumer"))
            }
        }

        for (index in 0..2) {
            compose.runOnIdle { phase.value = index }
            val bitmap = capture()
            try {
                val observed = observations.filter { it.phase == index }
                if (observed.isEmpty()) failures += "phase$index: no measured effects observation"
                if (observed.any { it.actual !== it.expected }) failures += "phase$index: effects.scope.shape is not the current Shape instance"
                val draws = sourceDraws.filter { it.phase == index }
                if (draws.isEmpty()) failures += "phase$index: source was not recorded"
                if (draws.any { !it.hardware }) failures += "phase$index: source was not hardware accelerated"
                inspectPixels(bitmap, physicalDensity, index, failures)
            } finally { bitmap.recycle() }
        }
        val receiver = observations.firstOrNull()?.receiver
        if (receiver == null || observations.any { it.receiver !== receiver }) failures += "consumer's effects receiver changed; same-node lifetime not established"
        val measuredSize = observations.firstOrNull()?.size
        if (observations.any { it.size != measuredSize }) failures += "effect layout dimensions changed between shapes"
        assertTrue("Retained backdrop shape mismatch: ${failures.joinToString()}", failures.isEmpty())
    }

    private fun inspectPixels(bitmap: Bitmap, density: Float, phase: Int, failures: MutableList<String>) {
        val near = floor(7f * density).toInt()
        val right = bitmap.width - 1 - near
        val bottom = bitmap.height - 1 - near
        val points = listOf("top-left" to (near to near), "top-right" to (right to near),
            "bottom-right" to (right to bottom), "bottom-left" to (near to bottom),
            "center" to (bitmap.width / 2 to bitmap.height / 2),
            "top-middle" to (bitmap.width / 2 to near), "right-middle" to (right to bitmap.height / 2),
            "bottom-middle" to (bitmap.width / 2 to bottom), "left-middle" to (near to bitmap.height / 2))
        points.forEachIndexed { index, (name, point) ->
            val (x, y) = point
            val expectedWhite = phase != 1 || index >= 4
            val radius = if (phase == 1) 40.0 else 4.0
            val halfWidth = bitmap.width / density.toDouble() / 2.0
            val halfHeight = bitmap.height / density.toDouble() / 2.0
            val qx = abs((x + .5) / density - halfWidth) - halfWidth + radius
            val qy = abs((y + .5) / density - halfHeight) - halfHeight + radius
            val signedInside = radius - hypot(max(qx, 0.0), max(qy, 0.0)) - min(max(qx, qy), 0.0)
            val marginValid = if (expectedWhite) signedInside >= 3.0 else signedInside <= -3.0
            val pixel = bitmap.getPixel(x, y)
            val rgba = listOf(android.graphics.Color.red(pixel), android.graphics.Color.green(pixel),
                android.graphics.Color.blue(pixel), android.graphics.Color.alpha(pixel))
            val rgbValid = rgba.take(3).all { if (expectedWhite) it >= 240 else it <= 16 }
            if (!marginValid || !rgbValid) failures += "phase$phase $name: expectedWhite=$expectedWhite rgba=$rgba analyticInsetDp=$signedInside"
        }
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
        assertTrue("PixelCopy must finish", copied.await(5, TimeUnit.SECONDS))
        assertEquals(PixelCopy.SUCCESS, result)
        val origin = IntArray(2)
        compose.runOnUiThread { contentView.getLocationInWindow(origin) }
        val bounds = compose.onNodeWithTag("dynamic-shape-consumer").fetchSemanticsNode().boundsInRoot
        assertEquals(bounds.left.roundToInt().toFloat(), bounds.left, .01f)
        assertEquals(bounds.top.roundToInt().toFloat(), bounds.top, .01f)
        return try { Bitmap.createBitmap(full, origin[0] + bounds.left.roundToInt(), origin[1] + bounds.top.roundToInt(),
            bounds.width.roundToInt(), bounds.height.roundToInt()) } finally { full.recycle() }
    }


}
