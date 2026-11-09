package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31, 35])
class GlassButtonsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun enabledCallbacksAndDisabledSemanticsSurviveGlassMaterial() {
        var calls = 0
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        Column {
                            GlassButton(onClick = { calls++ }) { Text("Save") }
                            GlassTextButton(onClick = { calls += 10 }, enabled = false) { Text("Delete") }
                            GlassIconButton(onClick = { calls += 100 }) { Text("Back") }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Delete").assertIsNotEnabled()
        compose.onNodeWithText("Back").performClick()
        assertEquals(101, calls)
    }
}
