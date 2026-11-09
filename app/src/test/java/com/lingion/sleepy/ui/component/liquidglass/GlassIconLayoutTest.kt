package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
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
class GlassIconLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun adjacentIconsKeepAVisualGutterInsideFullTouchTargets() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        Row {
                            repeat(2) { index ->
                                GlassIconButton({}, Modifier.testTag("target$index")) {
                                    Box(Modifier.fillMaxSize().testTag("pane$index"))
                                }
                            }
                        }
                    }
                }
            }
        }
        val first = compose.onNodeWithTag("pane0", true).fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("pane1", true).fetchSemanticsNode().boundsInRoot
        assertEquals("glass visual disc", 36f, first.width, .1f)
        compose.onNodeWithTag("target0").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        assertTrue("at least 12dp of visible space between adjacent discs", second.left - first.right >= 11.9f)
        compose.onNodeWithTag("target0").performTouchInput { down(center); moveBy(Offset(80f, 0f), 200) }
        compose.waitForIdle()
        val raised = compose.onNodeWithTag("pane0", true).fetchSemanticsNode().boundsInRoot
        assertTrue("pressed and dragged disc must leave its neighbor clear", raised.right < second.left)
        compose.onNodeWithTag("target0").performTouchInput { cancel() }
    }
}
