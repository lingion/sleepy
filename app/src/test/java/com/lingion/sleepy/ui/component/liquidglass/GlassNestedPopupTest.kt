package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GlassNestedPopupTest {
    @get:Rule val compose = createComposeRule()

    @Test fun aMenuInsideAnAlertSamplesItsImmediateDialogNotThePage() {
        var dialogScene: GlassHostState? = null
        var menuSource: GlassHostState? = null
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        GlassAlertDialog(
                            onDismissRequest = {}, confirmButton = { Text("Confirm") },
                            text = {
                                dialogScene = LocalGlassPopupScene.current
                                Box(Modifier.size(120.dp)) {
                                    GlassDropdownMenu(expanded = true, onDismissRequest = {}) {
                                        menuSource = LocalGlassOverlaySamplingHost.current
                                        Text("Nested menu")
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Nested menu").assertIsDisplayed()
        compose.runOnIdle {
            assertNotNull(dialogScene)
            assertSame("the submenu must use the alert scene rather than the underlying route scene", dialogScene, menuSource)
        }
    }
}
