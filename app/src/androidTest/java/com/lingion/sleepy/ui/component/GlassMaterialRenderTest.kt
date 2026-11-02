package com.lingion.sleepy.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.component.liquidglass.glassControl
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** GPU-only regression: optical consumers must not overwrite one another's shader uniforms. */
@RunWith(AndroidJUnit4::class)
class GlassMaterialRenderTest {
    @get:Rule val compose = createComposeRule()
    @Test fun selectedSegmentHasDistinctMaterialFromTrack() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        SegmentedSwitcher(
                            options = listOf(0 to "Left", 1 to "Right"), selected = 0,
                            onSelect = {}, modifier = Modifier.size(240.dp, 48.dp),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val left = image.getPixel(image.width / 4, image.height / 4)
        val right = image.getPixel(image.width * 3 / 4, image.height / 4)
        assertTrue("selected segment must retain a separate material: ${Integer.toHexString(left)} / ${Integer.toHexString(right)}", left != right)
    }

    @Test fun neighboringTintedPanesRetainIndependentColors() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        Row(Modifier.background(Color.White)) {
                            Box(Modifier.size(80.dp).glassControl(tint = Color.Red))
                            Box(Modifier.size(80.dp).glassControl(tint = Color.Blue))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val left = image.getPixel(image.width / 4, image.height / 2)
        val right = image.getPixel(image.width * 3 / 4, image.height / 2)
        assertTrue("left red pane retains red tint: ${Integer.toHexString(left)}", android.graphics.Color.red(left) > android.graphics.Color.blue(left) + 80)
        assertTrue("right blue pane retains blue tint: ${Integer.toHexString(right)}", android.graphics.Color.blue(right) > android.graphics.Color.red(right) + 80)
    }
}
