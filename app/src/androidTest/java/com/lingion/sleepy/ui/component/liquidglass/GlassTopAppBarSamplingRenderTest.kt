package com.lingion.sleepy.ui.component.liquidglass

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual navigation/action lenses must consume the visible bar's material, not the page below it. */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class GlassTopAppBarSamplingRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun opaqueBarCoversStripedPageInBothSiblingButtonSourcesAndTracksColorChanges() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val color = mutableStateOf(Color.White)
        val alternateBackground = mutableStateOf(false)
        show {
            StripedPage(alternateBackground.value)
            Toolbar(color.value)
        }
        val white = pixels("opaque-white")
        white.forEach { pixel ->
            val channels = channels(pixel)
            assertTrue("white toolbar must hide colored stripes in both button sources: ${hex(pixel)}",
                channels.min() > 220 && channels.max() - channels.min() < 12)
        }
        compose.runOnIdle { alternateBackground.value = true }
        assertEquals("an opaque white toolbar must hide a second underlying pattern too",
            white, pixels("opaque-white-over-black-white"))
        compose.runOnIdle { color.value = Color.Blue }
        pixels("opaque-blue").forEach(::assertBlue)
    }

    @Test fun translucentBarIsCompositedExactlyOnceInTheButtonSources() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        show {
            Box(Modifier.size(280.dp, 120.dp).background(Color.Red))
            Toolbar(Color.Blue.copy(alpha = .5f))
        }
        pixels("half-blue-over-red").forEach { pixel ->
            assertTrue("half-blue over red must remain purple, without omitted or doubled backing: ${hex(pixel)}",
                abs(android.graphics.Color.red(pixel) - android.graphics.Color.blue(pixel)) < 25 &&
                    android.graphics.Color.blue(pixel) > android.graphics.Color.green(pixel) + 20)
        }
        val bar = compose.onNodeWithTag("sampling-toolbar").captureToImage().asAndroidBitmap()
        val plain = bar.getPixel(bar.width / 2, bar.height / 2)
        assertTrue("the ordinary bar fill itself must also composite once: ${hex(plain)}",
            abs(android.graphics.Color.red(plain) - android.graphics.Color.blue(plain)) <= 2 &&
                android.graphics.Color.green(plain) <= 1)
    }

    @Test fun scrollColorReachesBothSiblingButtonSources() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        lateinit var scrollState: TopAppBarState
        show {
            StripedPage()
            val state = rememberTopAppBarState()
            SideEffect { scrollState = state }
            Toolbar(Color.White, scrolled = Color.Blue, state = state)
        }
        pixels("before-scroll").forEach { pixel ->
            assertTrue("unscrolled toolbar source must be white: ${hex(pixel)}", channels(pixel).min() > 220)
        }
        compose.runOnIdle { scrollState.contentOffset = -10_000f }
        pixels("after-scroll").forEach(::assertBlue)
    }

    @Test fun titleForegroundIsExcludedFromBothButtonSources() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val foreground = mutableStateOf(Color.Green)
        show {
            StripedPage()
            Toolbar(Color.Blue, title = {
                // Deliberately draw behind both later sibling buttons. Exporting
                // the completed bar tree would include this foreground too.
                Box(Modifier.size(1.dp).drawBehind {
                    drawRect(foreground.value, Offset(-300.dp.toPx(), -100.dp.toPx()),
                        Size(900.dp.toPx(), 300.dp.toPx()))
                })
            })
        }
        val first = pixels("foreground-green")
        first.forEach(::assertBlue)
        compose.runOnIdle { foreground.value = Color.Yellow }
        val second = pixels("foreground-yellow")
        first.zip(second).forEach { (a, b) ->
            assertEquals("changing title foreground cannot alter a sibling button's optical input", a, b)
        }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost(modifier = Modifier.size(280.dp, 120.dp), background = Color.Red) { content() }
                }
            }
        }
    }

    @Composable
    private fun Toolbar(
        color: Color,
        scrolled: Color = color,
        state: TopAppBarState? = null,
        title: @Composable () -> Unit = {},
    ) {
        GlassTopAppBar(
            title = title,
            modifier = Modifier.testTag("sampling-toolbar"),
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = color, scrolledContainerColor = scrolled),
            scrollBehavior = state?.let { TopAppBarDefaults.pinnedScrollBehavior(it) },
            navigationIcon = { GlassIconButton({}, Modifier.testTag("sampling-navigation")) {} },
            actions = { GlassIconButton({}, Modifier.testTag("sampling-action")) {} },
        )
    }

    @Composable
    private fun StripedPage(alternate: Boolean = false) {
        GlassBackground(0f, Modifier.size(280.dp, 120.dp)) {
            Canvas(Modifier.size(280.dp, 120.dp)) {
                val colors = if (alternate) listOf(Color.White, Color.Black) else listOf(Color.Red, Color.Green, Color.Blue)
                val pitch = 18.dp.toPx()
                var index = 0
                while (index * pitch < size.width) {
                    drawRect(colors[index % colors.size], Offset(index * pitch, 0f), Size(pitch, size.height))
                    index++
                }
            }
        }
    }

    private fun pixels(name: String): List<Int> {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "glass-top-app-bar-sampling").apply { mkdirs() }
        val screen = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { screen.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return listOf("sampling-navigation", "sampling-action").map { tag ->
            val image = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
            image.getPixel(image.width / 2, image.height / 2)
        }
    }

    private fun assertBlue(pixel: Int) {
        assertTrue("button must see its blue toolbar backing: ${hex(pixel)}",
            android.graphics.Color.blue(pixel) > android.graphics.Color.red(pixel) + 25 &&
                android.graphics.Color.blue(pixel) > android.graphics.Color.green(pixel) + 25)
    }

    private fun channels(pixel: Int) = listOf(android.graphics.Color.red(pixel), android.graphics.Color.green(pixel), android.graphics.Color.blue(pixel))
    private fun hex(pixel: Int) = pixel.toUInt().toString(16)
}
