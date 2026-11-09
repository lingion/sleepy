package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class GlassSheetOutlineVisualTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightMenuRimFollowsTopCornerShapeInsteadOfDrawingAnOval() {
        val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost(Modifier.fillMaxSize(), background = Color(0xFFF0F0F2)) {
                        Box(
                            Modifier.offset(40.dp, 200.dp)
                                .size(320.dp, 300.dp)
                                .testTag("glass-sheet")
                                .glassControl(shape = shape, enabled = false, registerPane = false, role = GlassMaterialRole.Menu)
                        )
                    }
                }
            }
        }
        val bounds = compose.onNodeWithTag("glass-sheet").fetchSemanticsNode().boundsInRoot
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        fun pixel(x: Int, y: Int) = image.getPixel(bounds.left.toInt() + x, bounds.top.toInt() + y)
        val straightEdgeInterior = pixel(38, 50)
        val sameInterior = pixel(60, 50)
        val edgeDelta = listOf(16, 8, 0).maxOf { shift ->
            abs(((straightEdgeInterior shr shift) and 0xFF) - ((sameInterior shr shift) and 0xFF))
        }
        assertTrue(
            "menu rim must not form a large capsule/circle inside a sheet whose side is straight at y=50dp; " +
                "sampled pixels were ${Integer.toHexString(straightEdgeInterior)} vs ${Integer.toHexString(sameInterior)}",
            edgeDelta < 8,
        )
    }

    private fun android.graphics.Color.toArgbChannel(channel: Int): Int = when (channel) {
        0 -> (red() * 255).toInt()
        1 -> (green() * 255).toInt()
        else -> (blue() * 255).toInt()
    }
}
