package com.lingion.sleepy.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.lingion.sleepy.ui.component.liquidglass.BackdropForegroundResponse
import com.lingion.sleepy.ui.component.liquidglass.backdropAwareForeground
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Bounded HWUI checks of the generic GPU composition primitive, not native visual parity.
 * All endpoint and alpha expectations below are explicit colors/SRC_OVER arithmetic; none are
 * obtained from the production shader or from an earlier screenshot's output.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class BackdropAwareForegroundRenderTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var surface: OpticalCaptureWindow
    private var density = 1f
    private val observations = JSONArray()
    private var caseName = "unstarted"

    private val response = BackdropForegroundResponse(
        lowBackground = Color.Red,
        highBackground = Color.Blue,
        lowerLuma = .25f,
        upperLuma = .75f,
        transparentBackground = Color.White,
    )

    @Composable
    private fun ObserveWindow() {
        val view = LocalView.current
        val actualDensity = LocalDensity.current.density
        SideEffect {
            surface = OpticalCaptureWindow.from(view)
            density = actualDensity
        }
    }

    /** RGB is deliberately green; only alpha is supposed to reach the adaptive output. */
    @Composable
    private fun MaskAndBadge(backdrop: Backdrop, widthDp: Int, heightDp: Int) {
        val pixelDensity = LocalDensity.current.density
        Box(Modifier.requiredSize(widthDp.dp, heightDp.dp).testTag("adaptive-consumer")) {
            Canvas(Modifier.requiredSize(widthDp.dp, heightDp.dp)
                .backdropAwareForeground(backdrop, response) {
                    // Nonzero origin, and no overlap with any mask probe below.
                    Rect(40f * pixelDensity, 10f * pixelDensity, (widthDp - 20f) * pixelDensity, 50f * pixelDensity)
                }) {
                drawRect(Color.Green, Offset(4.dp.toPx(), 4.dp.toPx()), Size(20.dp.toPx(), 20.dp.toPx()))
                drawRect(Color.Green.copy(alpha = .5f), Offset(4.dp.toPx(), 36.dp.toPx()),
                    Size(20.dp.toPx(), 20.dp.toPx()))
            }
            // An independent accent sibling: never part of the neutral alpha mask.
            Canvas(Modifier.requiredSize(widthDp.dp, heightDp.dp)) {
                drawRect(Color(0xFFFF8000), Offset((widthDp - 16).dp.toPx(), (heightDp - 16).dp.toPx()),
                    Size(10.dp.toPx(), 10.dp.toPx()))
            }
        }
    }

    @Test fun localSceneAlphaMaskAndAccentStayIndependentOnTheRealGpu() {
        caseName = "source-mask-accent"
        var source by mutableIntStateOf(0)
        compose.setContent {
            ObserveWindow()
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.requiredSize(300.dp, 190.dp).background(Color(0xFF204060)).testTag("scene")) {
                Canvas(Modifier.requiredSize(300.dp, 190.dp).layerBackdrop(backdrop)) {
                    if (source != 2) {
                        drawRect(Color.Black)
                        if (source == 0) {
                            // Consumer is (16,24); local sample pane is (40,10)..(100,50).
                            drawRect(Color.White, Offset(56.dp.toPx(), 34.dp.toPx()),
                                Size(60.dp.toPx(), 40.dp.toPx()))
                        }
                    }
                }
                Box(Modifier.offset(16.dp, 24.dp)) { MaskAndBadge(backdrop, 120, 80) }
            }
        }

        val brightPatch = capture("local-bright-patch")
        verifyMask(brightPatch, intArrayOf(0, 0, 255), intArrayOf(0, 0, 128), intArrayOf(0, 0, 0), 120, 80)
        brightPatch.recycle()

        // Same LayerBackdrop and consumer; only the ordinary source drawing changes.
        compose.runOnIdle { source = 1 }
        val black = capture("same-source-now-black")
        verifyMask(black, intArrayOf(255, 0, 0), intArrayOf(128, 0, 0), intArrayOf(0, 0, 0), 120, 80)
        black.recycle()

        compose.runOnIdle { source = 2 }
        val transparent = capture("transparent-source-white-response-fallback")
        // The classifier's opaque white fallback is independent of the visible slate backing.
        // A 50% blue mask over (32,64,96) is (16,32,175.5).
        verifyMask(transparent, intArrayOf(0, 0, 255), intArrayOf(16, 32, 176),
            intArrayOf(32, 64, 96), 120, 80)
        transparent.recycle()
    }

    @Test fun consumerMovementResizeAndReattachmentRefreshTheSourceCoordinates() {
        caseName = "movement-resize-detach"
        var x by mutableIntStateOf(16)
        var width by mutableIntStateOf(96)
        var height by mutableIntStateOf(72)
        var attached by mutableStateOf(true)
        var generation by mutableIntStateOf(0)
        compose.setContent {
            ObserveWindow()
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.requiredSize(300.dp, 190.dp).background(Color(0xFF204060)).testTag("scene")) {
                Canvas(Modifier.requiredSize(300.dp, 190.dp).layerBackdrop(backdrop)) {
                    drawRect(Color.Black)
                    drawRect(Color.White, Offset(128.dp.toPx(), 0f), Size(172.dp.toPx(), size.height))
                }
                if (attached) key(generation) {
                    Box(Modifier.offset(x.dp, 24.dp)) { MaskAndBadge(backdrop, width, height) }
                }
            }
        }

        val left = capture("consumer-left-black")
        verifyMask(left, intArrayOf(255, 0, 0), intArrayOf(128, 0, 0), intArrayOf(0, 0, 0), 96, 72)
        left.recycle()

        compose.runOnIdle { x = 160 }
        val moved = capture("same-consumer-moved-right-white")
        verifyMask(moved, intArrayOf(0, 0, 255), intArrayOf(128, 128, 255), intArrayOf(255, 255, 255), 96, 72)
        moved.recycle()

        compose.runOnIdle { width = 120; height = 88 }
        val resized = capture("same-consumer-resized")
        verifyMask(resized, intArrayOf(0, 0, 255), intArrayOf(128, 128, 255), intArrayOf(255, 255, 255), 120, 88)
        resized.recycle()

        compose.runOnIdle { attached = false }
        val detached = capture("consumer-detached", tag = "scene")
        assertRgb(detached, 174f, 38f, intArrayOf(255, 255, 255), "detached old opaque mask")
        assertRgb(detached, 269f, 101f, intArrayOf(255, 255, 255), "detached old accent")
        detached.recycle()

        compose.runOnIdle { generation++; attached = true }
        // Exactly one committed capture after reattachment; no settle loop or recapture. This
        // is not a claim that PixelCopy observes the compositor's very first presentation.
        val reattached = capture("reattached-first-requested-committed-capture")
        verifyMask(reattached, intArrayOf(0, 0, 255), intArrayOf(128, 128, 255), intArrayOf(255, 255, 255), 120, 88)
        reattached.recycle()
    }

    private fun capture(name: String, tag: String = "adaptive-consumer"): Bitmap {
        compose.waitForIdle()
        val bitmap = surface.capture()
        val origin = surface.origin()
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val x = origin[0] + bounds.left.roundToInt()
        val y = origin[1] + bounds.top.roundToInt()
        val width = bounds.width.roundToInt()
        val height = bounds.height.roundToInt()
        assertTrue("$name crop fits the actual window", x >= 0 && y >= 0 && x + width <= bitmap.width && y + height <= bitmap.height)
        val crop = Bitmap.createBitmap(bitmap, x, y, width, height)
        crop.saveOpticalPng(File(artifactDirectory(), "$name.png"))
        if (crop !== bitmap) bitmap.recycle()
        observations.put(JSONObject().put("capture", name).put("crop_tag", tag)
            .put("window_crop_px", JSONArray(listOf(x, y, width, height))).put("density", density)
            .put("capture_policy", "One OpticalCaptureWindow HWUI commit + PixelCopy; no retries or compositor-first-frame claim"))
        return crop
    }

    private fun verifyMask(bitmap: Bitmap, opaque: IntArray, half: IntArray, clear: IntArray, width: Int, height: Int) {
        assertRgb(bitmap, 14f, 14f, opaque, "opaque neutral mask")
        assertRgb(bitmap, 14f, 46f, half, "half alpha neutral mask")
        assertRgb(bitmap, 32f, 64f, clear, "transparent mask hole")
        assertRgb(bitmap, width - 11f, height - 11f, intArrayOf(255, 128, 0), "independent accent")
        assertRgb(bitmap, width - 3f, height - 3f, clear, "no source or atlas slot leakage")
    }

    private fun assertRgb(bitmap: Bitmap, xDp: Float, yDp: Float, expected: IntArray, label: String) {
        val x = (xDp * density).roundToInt()
        val y = (yDp * density).roundToInt()
        assertTrue("$label probe lies inside capture", x in 0 until bitmap.width && y in 0 until bitmap.height)
        val pixel = bitmap.getPixel(x, y)
        val actual = intArrayOf(android.graphics.Color.red(pixel), android.graphics.Color.green(pixel), android.graphics.Color.blue(pixel))
        observations.put(JSONObject().put("probe", label).put("local_dp", JSONArray(listOf(xDp, yDp)))
            .put("actual_rgb", JSONArray(actual.toList())).put("expected_rgb", JSONArray(expected.toList()))
            .put("tolerance_per_channel", 3))
        assertTrue("$label at ($xDp,$yDp): expected=${expected.toList()}, actual=${actual.toList()}",
            actual.indices.all { abs(actual[it] - expected[it]) <= 3 })
    }

    private fun artifactDirectory() = File(
        InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
        "backdrop-aware-foreground",
    ).apply { mkdirs() }

    private fun writeMetrics(name: String) {
        File(artifactDirectory(), "$name-metrics.json").writeText(JSONObject()
            .put("scope", "Generic GPU composition and lifecycle; not iOS material or motion parity")
            .put("observations", observations).toString(2))
    }

    @After fun preserveMetricsEvenOnFailure() = writeMetrics(caseName)
}
