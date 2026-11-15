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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.lingion.sleepy.ui.component.PillNavItemSpec
import com.lingion.sleepy.ui.theme.LocalDarkTheme
import java.io.File
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

/** Product GPU regression for the Dock's direct material; not an iOS appearance test. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class DockBackingRenderTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var contentView: View
    private lateinit var window: Window

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

    @Test
    fun heldLensIncludesChangesToItsActualBackingMaterial() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Enable with glassDockBackingCheck=true", arguments.getString("glassDockBackingCheck") == "true")
        val runId = (arguments.getString("glassDockBackingRunId") ?: System.currentTimeMillis().toString())
            .replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "dock-backing/$runId").apply { check(mkdirs() || isDirectory) }
        val backing = mutableStateOf(Color(0xFFB82020))
        val result = JSONObject().put("scope", "Production Dock PixelCopy; deterministic held state, not native motion or optical parity")
            .put("status", "running")
        val reportFile = File(directory, "result.json")
        reportFile.writeText(result.toString(2))
        try {
            compose.setContent {
                rememberWindow()
                val scene = rememberCanvasBackdrop { checkerboard() }
                val colors = darkColorScheme(
                    surface = Color(0xFF101010),
                    surfaceContainer = Color(0xFF292929),
                    surfaceContainerHighest = backing.value,
                    secondaryContainer = Color(0xFF404040),
                    onSecondaryContainer = Color.White,
                )
                MaterialTheme(colorScheme = colors) {
                    CompositionLocalProvider(LocalDarkTheme provides true,
                        LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L }) {
                        Box(Modifier.requiredSize(360.dp, 160.dp).background(Color(0xFF808080)).padding(20.dp)) {
                            LiquidGlassNavigationBar(
                                listOf(PillNavItemSpec(Icons.Filled.Home, "Home"),
                                    PillNavItemSpec(Icons.Filled.Search, "Explore"),
                                    PillNavItemSpec(Icons.Filled.Settings, "Settings")),
                                0, {}, Modifier.width(316.dp).testTag("dock"), scene,
                            )
                        }
                    }
                }
            }
            compose.waitForIdle()
            assertTrue("Real accelerated Window required", contentView.isHardwareAccelerated)
            compose.onNodeWithTag("dock").performTouchInput { down(Offset(width * .20f, height * .5f)) }
            compose.mainClock.advanceTimeBy(1600)
            compose.waitForIdle()
            val boundsBefore = lensBounds()
            val red = capture(directory, "red-held")
            compose.runOnIdle { backing.value = Color(0xFF20B820) }
            val green = capture(directory, "green-held")
            val boundsAfter = lensBounds()
            result.put("lens_bounds_before_px", boundsBefore.toJson()).put("lens_bounds_after_px", boundsAfter.toJson())
            assertEquals("Material change must not move the held lens", boundsBefore, boundsAfter)
            // The patch is inside the raised lens, away from both glyph rows
            // and its refracting edge. Only its direct backing material changed.
            val patch = Rect(
                boundsBefore.left + boundsBefore.width * .73f,
                boundsBefore.top + boundsBefore.height * .43f,
                boundsBefore.left + boundsBefore.width * .81f,
                boundsBefore.top + boundsBefore.height * .57f,
            )
            val before = patchMean(red, patch)
            val after = patchMean(green, patch)
            val redDrop = before[0] - after[0]
            val greenRise = after[1] - before[1]
            result.put("probe_px", patch.toJson()).put("before_rgb", JSONArray(before.toList()))
                .put("after_rgb", JSONArray(after.toList())).put("red_drop", redDrop).put("green_rise", greenRise)
            red.recycle()
            green.recycle()
            // A synthetic surfaceContainer copy ignores this live backing
            // change entirely. The threshold only distinguishes that omission;
            // it is not a fitted material-color or native-parity tolerance.
            val passed = redDrop > 20.0 && greenRise > 20.0
            result.put("status", if (passed) "passed" else "failed")
            reportFile.writeText(result.toString(2))
            assertTrue("Held lens skipped its real backing material: redDrop=$redDrop greenRise=$greenRise; artifacts=$directory", passed)
            compose.onNodeWithTag("dock").performTouchInput { cancel() }
        } catch (failure: Throwable) {
            result.put("status", "failed").put("exception", "${failure.javaClass.name}: ${failure.message}")
            reportFile.writeText(result.toString(2))
            throw failure
        }
    }

    private fun DrawScope.checkerboard() {
        val cell = 3.dp.toPx()
        var row = 0
        var y = 0f
        while (y < size.height) {
            var column = 0
            var x = 0f
            while (x < size.width) {
                drawRect(if ((row + column) % 2 == 0) Color(0xFF505050) else Color(0xFFB0B0B0),
                    Offset(x, y), Size(cell, cell))
                x += cell
                column++
            }
            y += cell
            row++
        }
    }

    private fun lensBounds(): Rect {
        val bounds = compose.onNodeWithTag("dock-lens").fetchSemanticsNode().config[DockLensBoundsInRootPx]
        val location = IntArray(2)
        compose.runOnUiThread { contentView.getLocationInWindow(location) }
        return bounds.translate(Offset(location[0].toFloat(), location[1].toFloat()))
    }

    private fun capture(directory: File, name: String): Bitmap {
        compose.waitForIdle()
        val committed = CountDownLatch(1)
        compose.runOnUiThread {
            contentView.rootView.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
            contentView.rootView.postInvalidateOnAnimation()
        }
        assertTrue("HWUI frame must commit", committed.await(5, TimeUnit.SECONDS))
        val bitmap = Bitmap.createBitmap(contentView.rootView.width, contentView.rootView.height, Bitmap.Config.ARGB_8888)
        val copied = CountDownLatch(1)
        var status = PixelCopy.ERROR_UNKNOWN
        PixelCopy.request(window, bitmap, { status = it; copied.countDown() }, Handler(Looper.getMainLooper()))
        assertTrue("PixelCopy must finish", copied.await(5, TimeUnit.SECONDS))
        assertEquals(PixelCopy.SUCCESS, status)
        File(directory, "$name-window.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        return bitmap
    }

    private fun patchMean(bitmap: Bitmap, rect: Rect): DoubleArray {
        val mean = DoubleArray(3)
        var count = 0
        for (y in rect.top.roundToInt() until rect.bottom.roundToInt()) {
            for (x in rect.left.roundToInt() until rect.right.roundToInt()) {
                val pixel = bitmap.getPixel(x, y)
                mean[0] += android.graphics.Color.red(pixel)
                mean[1] += android.graphics.Color.green(pixel)
                mean[2] += android.graphics.Color.blue(pixel)
                count++
            }
        }
        assertTrue("Probe must have real pixels", count > 0)
        return mean.map { it / count }.toDoubleArray()
    }

    private fun Rect.toJson() = JSONArray(listOf(left, top, right, bottom))
}
