package com.lingion.sleepy.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-mdpi")
class ColorPickerLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hueSliderReservesHeightForTheRaisedFortyDpLens() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        ColorPickerDialog("#3399DD", onConfirm = {}, onDismiss = {})
                    }
                }
            }
        }
        val slider = compose.onNodeWithTag("color-hue-slider", true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("48dp slider host must contain the raised 40dp lens: $slider", slider.height >= 47.9f)
    }
}
