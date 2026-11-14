package com.lingion.sleepy.ui.component.liquidglass

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
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
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.BackdropDiagnostics
import com.kyant.backdrop.BackdropRenderOptions
import com.kyant.backdrop.drawPlainBackdrop
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** PixelCopy reads committed HWUI output: a software Canvas cannot prove RenderEffect behavior. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class LiquidGlassHazeRenderTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var contentView: View
    private lateinit var window: Window

    @After
    fun removeGeometryObserver() {
        BackdropDiagnostics.observer = null
    }

    @Composable
    private fun rememberWindow() {
        val view = LocalView.current
        SideEffect {
            contentView = view
            var context = view.context
            while (context !is Activity && context is ContextWrapper) context = context.baseContext
            window = (context as Activity).window
        }
    }

    private fun artifactDirectory(): File = File(
        InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
        "native-haze-reference",
    ).apply { mkdirs() }

    private fun capture(tag: String, name: String): Bitmap {
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
        return Bitmap.createBitmap(full, origin[0] + bounds.left.roundToInt(), origin[1] + bounds.top.roundToInt(),
            bounds.width.roundToInt(), bounds.height.roundToInt()).also { cropped ->
            File(artifactDirectory(), "$name.png").outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (cropped !== full) full.recycle()
        }
    }

    private fun DrawScope.checkerboard(cellPx: Float) {
        var row = 0
        var y = 0f
        while (y < size.height) {
            var column = 0
            var x = 0f
            while (x < size.width) {
                drawRect(if ((row + column) % 2 == 0) Color.Black else Color.White,
                    Offset(x, y), Size(cellPx, cellPx))
                x += cellPx
                column++
            }
            y += cellPx
            row++
        }
    }

    private fun luma(pixel: Int): Float =
        (android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel)) / 3f

    private fun mean(bitmap: Bitmap, x0: Int, y0: Int, width: Int, height: Int): Float {
        var sum = 0f
        for (y in y0 until y0 + height) for (x in x0 until x0 + width) sum += luma(bitmap.getPixel(x, y))
        return sum / (width * height)
    }

    private fun highFrequency(bitmap: Bitmap, x0: Int, y0: Int, width: Int, height: Int): Float {
        var sum = 0f
        for (y in y0 until y0 + height) for (x in x0 until x0 + width - 1) {
            sum += abs(luma(bitmap.getPixel(x + 1, y)) - luma(bitmap.getPixel(x, y)))
        }
        return sum / ((width - 1) * height)
    }

    @Test
    fun independentGaussianSlotReducesHighFrequencyAndLeavesClearPixelsUnchanged() {
        val enabled = mutableStateOf(false)
        val opticsEnabled = mutableStateOf(false)
        lateinit var atlas: LiquidGlassShader.HazeAtlas
        compose.setContent {
            rememberWindow()
            val context = LocalContext.current
            val density = LocalDensity.current
            val plan = remember(density) {
                with(density) {
                    LiquidGlassShader.hazeAtlas(Size(96.dp.toPx(), 64.dp.toPx()), Offset.Zero, 8.dp.toPx(), 0f)
                }
            }
            SideEffect { atlas = plan }
            val nativeAtlas = remember(plan) {
                createLiquidGlassHazeAtlasEffect(plan,
                    android.graphics.RuntimeShader(LiquidGlassShader.HAZE_ATLAS_SHADER),
                    android.graphics.RuntimeShader(LiquidGlassShader.HAZE_ATLAS_SHADER))
            }
            val effect = remember(nativeAtlas) { nativeAtlas.asComposeRenderEffect() }
            val identity = remember(plan) {
                val shader = android.graphics.RuntimeShader(LiquidGlassShader.HAZE_ATLAS_SHADER)
                shader.setFloatUniform("uSlot", 0f, 0f, plan.sizePx.width, plan.sizePx.height)
                android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "uTexture").asComposeRenderEffect()
            }
            val optical = remember(plan, context) {
                val shader = android.graphics.RuntimeShader(LiquidGlassShader.shaderSource(context))
                LiquidGlassShader.build(
                    size = Size(96f, 64f),
                    corners = LiquidGlassCornerRadii(0f, 0f, 0f, 0f),
                    style = LiquidGlassStyle(Color.Transparent, blur = 0.0, haze = .72, hazeBlur = 8.0,
                        refraction = 0.0, bevel = 1.0, saturation = 1.0, rim = 0.0),
                    originPx = plan.sourceOriginPx,
                    density = density.density,
                    atlasSizePx = plan.sizePx,
                    sampling = LiquidGlassShader.sampling(Offset.Zero, density.density, plan.clearBoundsPx),
                    hazeSampling = LiquidGlassShader.sampling(plan.hazeBoundsPx.topLeft, density.density, plan.hazeBoundsPx),
                ).forEach { (name, values) -> shader.setFloatUniform(name, values) }
                android.graphics.RenderEffect.createChainEffect(
                    android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "uTexture"), nativeAtlas,
                ).asComposeRenderEffect()
            }
            Canvas(Modifier.size(with(density) { plan.sizePx.width.toDp() }, with(density) { plan.sizePx.height.toDp() })
                .background(Color.White).graphicsLayer {
                    renderEffect = if (opticsEnabled.value) optical else if (enabled.value) effect else identity
                }.testTag("atlas")) {
                checkerboard(4.dp.toPx())
            }
        }
        val source = capture("atlas", "source")
        compose.runOnIdle { enabled.value = true }
        val packed = capture("atlas", "packed")
        compose.runOnIdle { opticsEnabled.value = true }
        val optical = capture("atlas", "atlas-material")
        val margin = 8
        val width = atlas.clearBoundsPx.width.toInt() - margin * 2
        val height = atlas.clearBoundsPx.height.toInt() - margin * 2
        val sourceX = atlas.sourceOriginPx.x.toInt() + margin
        val sourceY = atlas.sourceOriginPx.y.toInt() + margin
        val clearX = atlas.clearBoundsPx.left.toInt() + margin
        val clearY = atlas.clearBoundsPx.top.toInt() + margin
        val hazeX = atlas.hazeBoundsPx.left.toInt() + margin
        val hazeY = atlas.hazeBoundsPx.top.toInt() + margin
        var worstClearDifference = 0f
        var totalClearDifference = 0.0
        var changedClearPixels = 0
        var worstFlatDifference = 0f
        for (y in 0 until height) for (x in 0 until width) {
            val before = luma(source.getPixel(sourceX + x, sourceY + y))
            val after = luma(packed.getPixel(clearX + x, clearY + y))
            val difference = abs(before - after)
            worstClearDifference = maxOf(worstClearDifference, difference)
            totalClearDifference += difference
            if (difference > 0f) changedClearPixels++
            if (before == 0f || before == 255f) worstFlatDifference = maxOf(worstFlatDifference, difference)
        }
        val clearFrequency = highFrequency(packed, clearX, clearY, width, height)
        val hazeFrequency = highFrequency(packed, hazeX, hazeY, width, height)
        val clearMean = mean(packed, clearX, clearY, width, height)
        val hazeMean = mean(packed, hazeX, hazeY, width, height)
        val diagnostics = "atlas=$atlas clearWorst=$worstClearDifference clearMeanDifference=${totalClearDifference / (width * height)} " +
            "clearChangedPixels=$changedClearPixels/${width * height} clearFlatWorst=$worstFlatDifference " +
            "clearFrequency=$clearFrequency hazeFrequency=$hazeFrequency clearMean=$clearMean hazeMean=$hazeMean " +
            "fullOpticalMean=${mean(optical, sourceX, sourceY, width, height)} " +
            "fullOpticalFrequency=${highFrequency(optical, sourceX, sourceY, width, height)}"
        File(artifactDirectory(), "atlas-metrics.txt").writeText(diagnostics)
        assertTrue("clear branch changed: $diagnostics", worstClearDifference <= 1f)
        assertTrue("fixture must contain high-frequency black/white edges", clearFrequency > 10f)
        assertTrue("haze must be an actual Gaussian: clear=$clearFrequency haze=$hazeFrequency", hazeFrequency < clearFrequency * .2f)
        assertTrue("missing/transparent haze cannot pass as blur: $diagnostics", abs(hazeMean - clearMean) < 16f)
    }

    @Test
    fun productionOpticsReadTheBlurredSlotAndRestoreTheOriginalClearSource() {
        verifyProductionOptics(insetDp = 80, artifactSuffix = "")
    }

    @Test
    fun productionOpticsKeepBothAtlasSlotsAtTheWindowOrigin() {
        verifyProductionOptics(insetDp = 0, artifactSuffix = "-origin")
    }

    private fun verifyProductionOptics(insetDp: Int, artifactSuffix: String) {
        val haze = mutableStateOf(0.0)
        val placementLayer = mutableStateOf(true)
        val geometry = mutableListOf<String>()
        BackdropDiagnostics.observer = { name, _ -> if (name.contains("Geometry")) geometry.add(name) }
        compose.setContent {
            rememberWindow()
            val context = LocalContext.current
            val backdrop = rememberCanvasBackdrop {
                val matrix = android.graphics.Matrix()
                @Suppress("DEPRECATION")
                drawContext.canvas.nativeCanvas.getMatrix(matrix)
                geometry.add("SourceGeometry(size=$size,density=$density,matrix=$matrix)")
                checkerboard(4.dp.toPx())
            }
            Box(Modifier.padding(start = insetDp.dp, top = insetDp.dp)) {
            Box(Modifier.size(144.dp, 96.dp).background(Color.White).drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = {
                    liquidGlass(context, LiquidGlassStyle(
                        tint = Color.Transparent, blur = 0.0, haze = haze.value, hazeBlur = 8.0,
                        refraction = 0.0, bevel = 1.0, saturation = 1.0, brightness = 1.0,
                        rim = 0.0, endShade = 0.0, dispersion = 0.0, rimBlur = 0.0, hairline = 0.0,
                    ))
                },
                renderOptions = remember(placementLayer.value) { BackdropRenderOptions(placementLayer = placementLayer.value) },
            ).testTag("material"))
            }
        }
        val clear = capture("material", "clear$artifactSuffix")
        compose.runOnIdle { haze.value = .72 }
        val hazy = capture("material", "hazy$artifactSuffix")
        compose.runOnIdle { placementLayer.value = false }
        val unclipped = capture("material", "hazy-unclipped$artifactSuffix")
        compose.runOnIdle { placementLayer.value = true }
        compose.runOnIdle { haze.value = 0.0 }
        val restored = capture("material", "restored$artifactSuffix")
        val margin = clear.height / 4
        val width = clear.width - margin * 2
        val height = clear.height - margin * 2
        val clearFrequency = highFrequency(clear, margin, margin, width, height)
        val hazeFrequency = highFrequency(hazy, margin, margin, width, height)
        val clearMean = mean(clear, margin, margin, width, height)
        val hazeMean = mean(hazy, margin, margin, width, height)
        val diagnostics = "clearFrequency=$clearFrequency hazeFrequency=$hazeFrequency clearMean=$clearMean hazeMean=$hazeMean " +
            "dimensions=${clear.width}x${clear.height} region=$margin,$margin,$width,$height"
        File(artifactDirectory(), "material-metrics$artifactSuffix.txt").writeText(diagnostics +
            " unclippedMean=${mean(unclipped, margin, margin, width, height)} " +
            "unclippedFrequency=${highFrequency(unclipped, margin, margin, width, height)}")
        File(artifactDirectory(), "material-geometry$artifactSuffix.txt").writeText(geometry.joinToString("\n"))
        assertTrue("production haze must lower high-frequency detail: clear=$clearFrequency haze=$hazeFrequency",
            clearFrequency > 10f && hazeFrequency < clearFrequency * .45f)
        assertTrue("haze sampling must retain the backdrop's average luminance: $diagnostics", abs(hazeMean - clearMean) < 16f)
        var worstDifference = 0f
        for (y in margin until margin + height) for (x in margin until margin + width) {
            worstDifference = maxOf(worstDifference, abs(luma(clear.getPixel(x, y)) - luma(restored.getPixel(x, y))))
        }
        assertTrue("haze=0 must restore unchanged clear sampling, difference=$worstDifference", worstDifference <= 1f)
    }
}
