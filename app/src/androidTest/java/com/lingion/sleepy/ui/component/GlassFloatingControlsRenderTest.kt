package com.lingion.sleepy.ui.component

import android.graphics.Bitmap
import android.app.Activity
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.ui.component.liquidglass.*
import com.lingion.sleepy.ui.theme.LocalDarkTheme
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals

/** Held frames are exported from the real Android renderer for reference comparison. */
@RunWith(AndroidJUnit4::class)
class GlassFloatingControlsRenderTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var root: View
    private lateinit var contentView: View
    private lateinit var window: Window

    private fun labelInkWidth(bitmap: Bitmap): Int {
        val bounds = compose.onNodeWithText("For You").fetchSemanticsNode().boundsInRoot
        val origin = IntArray(2)
        compose.runOnUiThread { contentView.getLocationInWindow(origin) }
        var left = bitmap.width
        var right = -1
        for (y in (bounds.top.toInt() + origin[1] - 24).coerceAtLeast(0) until
            (bounds.bottom.toInt() + origin[1] + 24).coerceAtMost(bitmap.height)) {
            for (x in (bounds.left.toInt() + origin[0] - 24).coerceAtLeast(0) until
                (bounds.right.toInt() + origin[0] + 24).coerceAtMost(bitmap.width)) {
                val color = bitmap.getPixel(x, y)
                if (android.graphics.Color.red(color) < 100 && android.graphics.Color.green(color) < 100 &&
                    android.graphics.Color.blue(color) < 100) {
                    left = minOf(left, x)
                    right = maxOf(right, x)
                }
            }
        }
        assertTrue("fixture must contain visible label ink", right >= left)
        return right - left + 1
    }

    private fun waitForTabToSettle() {
        compose.waitUntil(timeoutMillis = 3_000) {
            val bounds = compose.onNodeWithTag("segment-thumb", true).getUnclippedBoundsInRoot()
            bounds.bottom - bounds.top <= 35.dp
        }
    }

    private fun save(name: String): Bitmap {
        compose.waitForIdle()
        // Compose's idle clock does not mean SurfaceFlinger has received the frame.
        // Wait for HWUI's actual commit, then copy that window buffer.
        val committed = CountDownLatch(1)
        compose.runOnUiThread {
            root.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
            root.postInvalidateOnAnimation()
        }
        assertTrue("$name: hardware frame was not committed", committed.await(5, TimeUnit.SECONDS))
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val copied = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        PixelCopy.request(window, bitmap, { result = it; copied.countDown() }, Handler(Looper.getMainLooper()))
        assertTrue("$name: window copy did not complete", copied.await(5, TimeUnit.SECONDS))
        assertEquals("$name: window copy failed", PixelCopy.SUCCESS, result)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "pr299-controls").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    @Test fun heldControlsCaptureTheirOrdinarySiblingsAndOverflowBothEdges() {
        val darkMode = mutableStateOf(false)
        compose.setContent {
            val view = LocalView.current
            SideEffect {
                contentView = view
                root = view.rootView
                var context = view.context
                while (context !is Activity && context is ContextWrapper) context = context.baseContext
                window = (context as Activity).window
            }
            var selected by remember { mutableIntStateOf(0) }
            var checked by remember { mutableStateOf(true) }
            var value by remember { mutableFloatStateOf(.55f) }
            val background = if (darkMode.value) Color(0xFF10131A) else Color(0xFFF3F7F8)
            MaterialTheme(colorScheme = if (darkMode.value) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass, LocalDarkTheme provides darkMode.value) {
                    GlassHost(modifier = Modifier.fillMaxSize(), background = background) {
                        Column(Modifier.fillMaxSize().background(background).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            Text("Toggle", color = MaterialTheme.colorScheme.onSurface)
                            GlassSwitch(checked, { checked = it }, Modifier.testTag("toggle"),
                                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF34C759),
                                    checkedThumbColor = Color.White, uncheckedThumbColor = Color.White))
                            Spacer(Modifier.height(8.dp))
                            Text("Tab", color = MaterialTheme.colorScheme.onSurface)
                            SegmentedSwitcher(listOf(0 to "For You", 1 to "Library"), selected, { selected = it }, Modifier.width(224.dp).testTag("tabs"))
                            Spacer(Modifier.height(8.dp))
                            Text("Slider", color = MaterialTheme.colorScheme.onSurface)
                            GlassSlider(value, { value = it }, Modifier.fillMaxWidth().testTag("slider"), initialValue = .55f, thumbColor = Color(0xFF59A9EB))
                        }
                    }
                }
            }
        }
        val restingInk = labelInkWidth(save("00-rest"))
        val tabs = compose.onNodeWithTag("tabs")
        tabs.performTouchInput { down(Offset(width / 4f, centerY)) }
        compose.waitForIdle()
        val track = tabs.getUnclippedBoundsInRoot()
        val held = compose.onNodeWithTag("segment-thumb", true).getUnclippedBoundsInRoot()
        assertTrue("glass must extend above track", held.top < track.top)
        assertTrue("glass must extend below track", held.bottom > track.bottom)
        val heldInk = labelInkWidth(save("01-tab-held"))
        assertTrue("content lens must visibly magnify glyphs: resting=$restingInk, held=$heldInk",
            heldInk >= restingInk * 1.1f)
        tabs.performTouchInput { moveTo(Offset(width / 2f, centerY), 200) }
        save("02-tab-between-labels")
        tabs.performTouchInput { moveTo(Offset(width * 3 / 4f, centerY), 200) }
        save("03-tab-right")
        tabs.performTouchInput { up() }
        waitForTabToSettle()
        save("04-tab-released")
        val toggle = compose.onNodeWithTag("toggle")
        toggle.performTouchInput { down(center) }
        save("05-toggle-held")
        toggle.performTouchInput { moveTo(Offset(0f, centerY), 200) }
        save("06-toggle-dragged")
        toggle.performTouchInput { up() }
        toggle.assertIsOff()
        val slider = compose.onNodeWithTag("slider")
        slider.performTouchInput { down(Offset(width * .55f, centerY)) }
        save("07-slider-held")
        slider.performTouchInput { moveTo(Offset(width * .72f, centerY), 200) }
        save("08-slider-dragged")
        slider.performTouchInput { up() }
        save("09-slider-released")
        compose.runOnIdle { darkMode.value = true }
        save("10-dark-rest")
        tabs.performTouchInput { down(Offset(width * 3 / 4f, centerY)) }
        save("11-dark-tab-held")
        tabs.performTouchInput { moveTo(Offset(width / 2f, centerY), 200) }
        save("12-dark-tab-between-labels")
        tabs.performTouchInput { up() }
        waitForTabToSettle()
        toggle.performTouchInput { click(center) }
        toggle.assertIsOn()
        toggle.performTouchInput { down(center) }
        save("13-dark-toggle-held")
        toggle.performTouchInput { up() }
        slider.performTouchInput { down(Offset(width * .72f, centerY)) }
        save("14-dark-slider-held")
        slider.performTouchInput { up() }
    }
}
