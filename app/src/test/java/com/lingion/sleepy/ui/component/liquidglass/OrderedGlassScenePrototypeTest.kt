package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Separate, explicit prototype; does not weaken GlassHostCaptureTest's failing regression. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w240dp-h240dp-mdpi")
class OrderedGlassScenePrototypeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun explicitPrefixNeverIncludesALaterTranslucentSegment() {
        lateinit var prefix: GraphicsLayer
        compose.setContent {
            prefix = rememberGraphicsLayer()
            OrderedGlassScene(Modifier.size(120.dp)) {
                OrderedGlassBackground(depth = 0f, modifier = Modifier.size(120.dp)) {
                    Box(Modifier.size(120.dp).background(Color.Green))
                }
                OrderedGlassPrefixProbe(depth = 1f, destination = prefix, background = Color.Black,
                    modifier = Modifier.size(120.dp)) {
                    Box(Modifier.size(80.dp).background(Color.Blue))
                }
                OrderedGlassBackground(depth = 2f, modifier = Modifier.size(80.dp)) {
                    Box(Modifier.size(80.dp).background(Color.Red.copy(alpha = 0.5f)))
                }
            }
        }
        compose.waitForIdle()
        val display = compose.onRoot().captureToImage().asAndroidBitmap()
        val source = runBlocking { prefix.toImageBitmap() }.asAndroidBitmap()
        assertEquals("only the earlier green source is sampled", android.graphics.Color.GREEN, source.getPixel(30, 30))
        assertEquals("later transparent red still paints above blue", 0xFF80007F.toInt(), display.getPixel(30, 30))
    }
}
