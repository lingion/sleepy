package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.RangeSliderState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.component.liquidglass.GlassRangeSlider
import com.lingion.sleepy.ui.component.liquidglass.GlassSlider
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A live glass host must not change Material controls' default pixels or dimensions. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MaterialSliderAppearanceIsolationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun singleMatchesDefaultMaterialWithGlassHostPresent() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.Classic) {
                    GlassHost {
                        Column {
                            GlassSlider(.4f, {}, Modifier.width(280.dp).testTag("app"), steps = 9)
                            val state = remember { SliderState(.4f, steps = 9) }
                            Slider(state, {}, Modifier.width(280.dp).testTag("native"))
                        }
                    }
                }
            }
        }
        assertSamePixels()
    }

    @Test fun rangeMatchesDefaultMaterialWithGlassHostPresent() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.Classic) {
                    GlassHost {
                        Column(Modifier.width(280.dp)) {
                            GlassRangeSlider(.2f.. .8f, {}, Modifier.testTag("app"), 0f..1f, 9)
                            val state = remember { RangeSliderState(.2f, .8f, steps = 9) }
                            RangeSlider(state, {}, Modifier.testTag("native"))
                        }
                    }
                }
            }
        }
        assertSamePixels()
    }

    private fun assertSamePixels() {
        compose.waitForIdle()
        val actual = compose.onNodeWithTag("app").captureToImage().asAndroidBitmap()
        val expected = compose.onNodeWithTag("native").captureToImage().asAndroidBitmap()
        assertTrue("Material appearance must equal the native control, including size, track, and handles",
            actual.sameAs(expected))
    }
}
