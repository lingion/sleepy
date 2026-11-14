package com.lingion.sleepy.ui.component

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.data.undo.UndoManager
import com.lingion.sleepy.ui.component.liquidglass.GlassBackground
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.screen.schedule.TopBar
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import java.io.File
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the actual schedule toolbar and its custom start/center/end subcompositions. */
@RunWith(AndroidJUnit4::class)
class ScheduleToolbarSamplingRenderTest {
    @get:Rule val compose = createComposeRule()
    @After fun clearHistory() { UndoManager.clear() }

    @Test fun actualFullToolbarSamplesItsDirectBackingAboveTwoPageBackgrounds() = verifyOpaque(false)
    @Test fun actualBusyToolbarSamplesItsDirectBackingAboveTwoPageBackgrounds() = verifyOpaque(true)

    @Test fun actualToolbarCompositesItsTranslucentBackingExactlyOnce() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val backing = mutableStateOf(Color.Blue.copy(alpha = .5f))
        val pattern = mutableStateOf(2)
        show(backing, pattern)
        val image = capture("translucent-blue-over-red")
        val paddingPixel = image.getPixel(image.width / 2, 1)
        assertTrue("the visible toolbar fill composites once: ${hex(paddingPixel)}",
            abs(android.graphics.Color.red(paddingPixel) - android.graphics.Color.blue(paddingPixel)) <= 2 &&
                android.graphics.Color.green(paddingPixel) <= 1)
        actionPixels().forEach { pixel ->
            assertTrue("the lens samples half-blue over red once rather than the page alone or twice: ${hex(pixel)}",
                abs(android.graphics.Color.red(pixel) - android.graphics.Color.blue(pixel)) < 25 &&
                    android.graphics.Color.blue(pixel) > android.graphics.Color.green(pixel) + 20)
        }
    }

    @Test fun materialDesignKeepsTheOriginalSolidActionFill() {
        val backing = mutableStateOf(Color.White)
        val pattern = mutableStateOf(0)
        show(backing, pattern, style = UiStyle.Classic)
        val first = actionImages()
        capture("material-white-rgb-stripes")
        compose.runOnIdle { pattern.value = 1 }
        val second = actionImages()
        first.zip(second).forEach { (a, b) -> assertSamePixels(a, b) }
        capture("material-white-black-white")
    }

    @Test fun actualToolbarDoesNotExportItsIconsIntoTheSiblingMaterials() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val backing = mutableStateOf(Color.Blue)
        val pattern = mutableStateOf(0)
        val foreground = mutableStateOf(Color.Green)
        show(backing, pattern, foreground = foreground)
        val first = actionImages()
        val firstMaterial = actionPixels()
        capture("foreground-green")
        compose.runOnIdle { foreground.value = Color.Yellow }
        val second = actionImages()
        assertTrue("the actual icon foreground must visibly change during this test",
            first.zip(second).any { (a, b) -> maximumDifference(a, b) > 20 })
        assertEquals("changing the real icon foreground cannot change sibling optical input",
            firstMaterial, actionPixels())
        capture("foreground-yellow")
    }

    private fun verifyOpaque(busy: Boolean) {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val backing = mutableStateOf(Color.White)
        val pattern = mutableStateOf(0)
        show(backing, pattern, busy = busy)
        val first = actionImages()
        capture("${if (busy) "busy" else "full"}-white-rgb-stripes")
        compose.runOnIdle { pattern.value = 1 }
        val second = actionImages()
        first.zip(second).forEach { (a, b) -> assertSamePixels(a, b) }
        capture("${if (busy) "busy" else "full"}-white-black-white")
        compose.runOnIdle { backing.value = Color.Blue }
        actionPixels().forEach { pixel ->
            assertTrue("a retained real action must see the new blue toolbar backing: ${hex(pixel)}",
                android.graphics.Color.blue(pixel) > android.graphics.Color.red(pixel) + 25 &&
                    android.graphics.Color.blue(pixel) > android.graphics.Color.green(pixel) + 25)
        }
        capture("${if (busy) "busy" else "full"}-blue-black-white")
    }

    private fun show(
        backing: MutableState<Color>,
        pattern: MutableState<Int>,
        busy: Boolean = false,
        style: UiStyle = UiStyle.LiquidGlass,
        foreground: MutableState<Color>? = null,
    ) {
        UndoManager.clear()
        if (busy) UndoManager.capture(emptyList(), emptyList(), null)
        compose.setContent {
            val colors = lightColorScheme(surface = backing.value).let {
                if (foreground == null) it else it.copy(onSurfaceVariant = foreground.value)
            }
            MaterialTheme(colorScheme = colors) {
                CompositionLocalProvider(LocalUiStyle provides style) {
                    GlassHost(Modifier.width(320.dp).height(120.dp).testTag("real-toolbar-host"), background = Color.Red) {
                        // It is painted below the actual toolbar. Its depth=0
                        // must not reorder it over that toolbar's direct fill.
                        GlassBackground(0f, Modifier.width(320.dp).height(120.dp)) {
                            Canvas(Modifier.width(320.dp).height(120.dp)) {
                                val colors = when (pattern.value) {
                                    0 -> listOf(Color.Red, Color.Green, Color.Blue)
                                    1 -> listOf(Color.Black, Color.White)
                                    else -> listOf(Color.Red)
                                }
                                val pitch = 18.dp.toPx()
                                var stripe = 0
                                while (stripe * pitch < size.width) {
                                    drawRect(colors[stripe % colors.size], Offset(stripe * pitch, 0f), Size(pitch, size.height))
                                    stripe++
                                }
                            }
                        }
                        Box(Modifier.width(320.dp)) {
                            TopBar(2, 20, "2099-09-01", null, {}, {}, {}, busy, {}, {}, {}, {}, {}, {}, {})
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun actionImages(): List<Bitmap> {
        compose.waitForIdle()
        return listOf("schedule_switch_table", "schedule_prev_week", "schedule_next_week", "schedule_add_course", "schedule_share").map {
            compose.onNodeWithTag(it).captureToImage().asAndroidBitmap()
        }
    }

    private fun actionPixels() = actionImages().map { image ->
        // The glyph is 20dp inside a 36dp glass disc. This point is below
        // its glyph and inside the lens, rather than in the edge antialiasing.
        image.getPixel(image.width / 2, image.height * 4 / 5)
    }

    private fun assertSamePixels(first: Bitmap, second: Bitmap) {
        val maximum = maximumDifference(first, second)
        assertTrue("an opaque real toolbar must hide both page backgrounds in its complete action crops; max channel delta=$maximum", maximum <= 1)
    }

    private fun maximumDifference(first: Bitmap, second: Bitmap): Int {
        assertEquals(first.width, second.width)
        assertEquals(first.height, second.height)
        var maximum = 0
        for (y in 0 until first.height) for (x in 0 until first.width) {
            val a = first.getPixel(x, y)
            val b = second.getPixel(x, y)
            maximum = maxOf(maximum, maxOf(abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)),
                abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)),
                abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))))
        }
        return maximum
    }

    private fun capture(name: String): Bitmap {
        compose.waitForIdle()
        val image = compose.onNodeWithTag("real-toolbar-host").captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "schedule-toolbar-sampling").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return image
    }

    private fun hex(pixel: Int) = pixel.toUInt().toString(16)
}
