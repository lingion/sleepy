package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.FilledTonalButton as MaterialTonalButton
import androidx.compose.material3.IconButton as MaterialIconButton
import androidx.compose.material3.OutlinedButton as MaterialOutlinedButton
import androidx.compose.material3.TextButton as MaterialTextButton

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31, 35])
class GlassButtonsTest {
    @get:Rule val compose = createComposeRule()
    private val wrappedVisible = mutableStateOf(true)

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

    @Test
    @Config(sdk = [35], qualifiers = "w600dp-h800dp-mdpi")
    fun materialDefaultsMatchOfficialExpressiveButtonsAtRestAndWhilePressed() {
        assertOfficialParity(defaultPairs(), requirePressChange = true)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w600dp-h800dp-mdpi")
    fun materialExplicitShapeColorsBorderPaddingAndElevationMatchOfficialButtons() {
        assertOfficialParity(overriddenPairs())
    }

    @Test
    @Config(sdk = [35], qualifiers = "w600dp-h800dp-mdpi")
    fun materialOutlinedPreservesExplicitNullBorderAndElevation() {
        assertOfficialParity(listOf(ButtonPair(
            name = "outlined-null-overrides",
            wrapped = { modifier, source ->
                GlassOutlinedButton(
                    {}, modifier, border = null, elevation = null, interactionSource = source,
                ) { Marker() }
            },
            official = { modifier, source ->
                MaterialOutlinedButton(
                    {}, shapes = ButtonDefaults.shapes(), modifier = modifier,
                    border = null, elevation = null, interactionSource = source,
                ) { Marker() }
            },
        )))
    }

    @Test
    @Config(sdk = [35], qualifiers = "w600dp-h800dp-mdpi")
    fun materialNeverInvokesGlassControlEvenWhenItWouldReturnAnUnchangedModifier() {
        val withoutHost = mutableStateOf(false)
        val calls = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                GlassHost(enabled = !withoutHost.value) {
                    CompositionLocalProvider(
                        LocalUiStyle provides if (withoutHost.value) UiStyle.LiquidGlass else UiStyle.Classic,
                        // glassControl checks this before its Material/no-host early return.
                        LocalGlassSourceContent provides true,
                    ) {
                        Column {
                            GlassButton({ calls += "filled" }, Modifier.testTag("filled")) { Text("Save") }
                            GlassTonalButton({ calls += "tonal" }, Modifier.testTag("tonal")) { Text("Open") }
                            GlassTextButton({ calls += "text" }, Modifier.testTag("text"), enabled = false) { Text("Delete") }
                            GlassOutlinedButton({ calls += "outlined" }, Modifier.testTag("outlined")) { Text("Edit") }
                            GlassIconButton({ calls += "icon" }, Modifier.testTag("icon")) { Text("Back") }
                        }
                    }
                }
            }
        }
        repeat(2) { mode ->
            compose.runOnIdle { withoutHost.value = mode == 1 }
            compose.onNodeWithTag("text").assertIsNotEnabled()
            listOf("filled", "tonal", "outlined", "icon").forEach { tag ->
                compose.onNodeWithTag(tag).performClick()
            }
        }
        assertEquals(List(2) { listOf("filled", "tonal", "outlined", "icon") }.flatten(), calls)
    }

    private data class ButtonPair(
        val name: String,
        val wrapped: @Composable (Modifier, MutableInteractionSource) -> Unit,
        val official: @Composable (Modifier, MutableInteractionSource) -> Unit,
    )

    private fun defaultPairs() = listOf(
        ButtonPair("filled", { modifier, source ->
            GlassButton({}, modifier, interactionSource = source) { Marker() }
        }, { modifier, source ->
            MaterialButton({}, shapes = ButtonDefaults.shapes(), modifier = modifier, interactionSource = source) { Marker() }
        }),
        ButtonPair("tonal", { modifier, source ->
            GlassTonalButton({}, modifier, interactionSource = source) { Marker() }
        }, { modifier, source ->
            MaterialTonalButton({}, shapes = ButtonDefaults.shapes(), modifier = modifier, interactionSource = source) { Marker() }
        }),
        ButtonPair("text", { modifier, source ->
            GlassTextButton({}, modifier, interactionSource = source) { Marker() }
        }, { modifier, source ->
            MaterialTextButton({}, shapes = ButtonDefaults.shapes(), modifier = modifier, interactionSource = source) { Marker() }
        }),
        ButtonPair("outlined", { modifier, source ->
            GlassOutlinedButton({}, modifier, interactionSource = source) { Marker() }
        }, { modifier, source ->
            MaterialOutlinedButton({}, shapes = ButtonDefaults.shapes(), modifier = modifier, interactionSource = source) { Marker() }
        }),
        ButtonPair("icon", { modifier, source ->
            GlassIconButton({}, modifier, interactionSource = source) { IconMarker() }
        }, { modifier, source ->
            MaterialIconButton({}, shapes = IconButtonDefaults.shapes(), modifier = modifier, interactionSource = source) { IconMarker() }
        }),
    )

    private fun overriddenPairs(): List<ButtonPair> {
        val shape = RoundedCornerShape(5.dp)
        val border = BorderStroke(2.dp, Color.Green)
        val padding = PaddingValues(horizontal = 31.dp, vertical = 19.dp)
        return listOf(
            ButtonPair("filled-overrides", { modifier, source ->
                GlassButton(
                    {}, modifier, shape = shape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Magenta, contentColor = Color.Blue),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 7.dp, pressedElevation = 11.dp),
                    border = border, contentPadding = padding, interactionSource = source,
                ) { Marker() }
            }, { modifier, source ->
                MaterialButton(
                    {}, shapes = ButtonDefaults.shapes(shape, shape), modifier = modifier,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Magenta, contentColor = Color.Blue),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 7.dp, pressedElevation = 11.dp),
                    border = border, contentPadding = padding, interactionSource = source,
                ) { Marker() }
            }),
            ButtonPair("tonal-overrides", { modifier, source ->
                GlassTonalButton(
                    {}, modifier, shape = shape,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.Magenta, contentColor = Color.Blue),
                    elevation = ButtonDefaults.filledTonalButtonElevation(defaultElevation = 7.dp, pressedElevation = 11.dp),
                    border = border, contentPadding = padding, interactionSource = source,
                ) { Marker() }
            }, { modifier, source ->
                MaterialTonalButton(
                    {}, shapes = ButtonDefaults.shapes(shape, shape), modifier = modifier,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.Magenta, contentColor = Color.Blue),
                    elevation = ButtonDefaults.filledTonalButtonElevation(defaultElevation = 7.dp, pressedElevation = 11.dp),
                    border = border, contentPadding = padding, interactionSource = source,
                ) { Marker() }
            }),
            ButtonPair("text-overrides", { modifier, source ->
                GlassTextButton(
                    {}, modifier, shape = shape,
                    colors = ButtonDefaults.textButtonColors(containerColor = Color.Magenta, contentColor = Color.Blue),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 7.dp, pressedElevation = 11.dp),
                    border = border, contentPadding = padding, interactionSource = source,
                ) { Marker() }
            }, { modifier, source ->
                MaterialTextButton(
                    {}, shapes = ButtonDefaults.shapes(shape, shape), modifier = modifier,
                    colors = ButtonDefaults.textButtonColors(containerColor = Color.Magenta, contentColor = Color.Blue),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 7.dp, pressedElevation = 11.dp),
                    border = border, contentPadding = padding, interactionSource = source,
                ) { Marker() }
            }),
            ButtonPair("outlined-overrides", { modifier, source ->
                GlassOutlinedButton(
                    {}, modifier, shape = shape,
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Magenta, contentColor = Color.Blue),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 7.dp, pressedElevation = 11.dp),
                    border = border, contentPadding = padding, interactionSource = source,
                ) { Marker() }
            }, { modifier, source ->
                MaterialOutlinedButton(
                    {}, shapes = ButtonDefaults.shapes(shape, shape), modifier = modifier,
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Magenta, contentColor = Color.Blue),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 7.dp, pressedElevation = 11.dp),
                    border = border, contentPadding = padding, interactionSource = source,
                ) { Marker() }
            }),
            ButtonPair("icon-overrides", { modifier, source ->
                GlassIconButton(
                    {}, modifier, colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Magenta, contentColor = Color.Blue),
                    interactionSource = source,
                ) { IconMarker() }
            }, { modifier, source ->
                MaterialIconButton(
                    {}, shapes = IconButtonDefaults.shapes(), modifier = modifier,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Magenta, contentColor = Color.Blue),
                    interactionSource = source,
                ) { IconMarker() }
            }),
        )
    }

    private fun assertOfficialParity(pairs: List<ButtonPair>, requirePressChange: Boolean = false) {
        compose.mainClock.autoAdvance = false
        val selected = mutableStateOf(pairs.first())
        val source = MutableInteractionSource()
        compose.setContent {
            MaterialExpressiveTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.Classic) {
                    // Keep both shadows at the same window coordinates and the same animation time.
                    Box(Modifier.background(Color.White)) {
                        key(selected.value.name) {
                            selected.value.wrapped(Modifier.testTag("app").graphicsLayer {
                                alpha = if (wrappedVisible.value) 1f else 0f
                            }, source)
                            selected.value.official(Modifier.testTag("official").graphicsLayer {
                                alpha = if (wrappedVisible.value) 0f else 1f
                            }, source)
                        }
                    }
                }
            }
        }
        pairs.forEach { pair ->
            compose.runOnIdle { selected.value = pair }
            settle()
            val resting = assertSamePixels("${pair.name} resting")
            val press = PressInteraction.Press(Offset(20f, 20f))
            compose.runOnIdle { assertTrue(source.tryEmit(press)) }
            settle()
            val held = assertSamePixels("${pair.name} pressed")
            if (requirePressChange && pair.name in setOf("filled", "tonal")) {
                assertFalse("${pair.name} must expose the official press response", resting.sameAs(held))
            }
            compose.runOnIdle { assertTrue(source.tryEmit(PressInteraction.Release(press))) }
            settle()
        }
    }

    private fun settle() {
        compose.waitForIdle()
        // The official default shape morph updates its outline during host layout/draw.
        // Drive both controls through those frames, rather than only advancing Compose time.
        repeat(100) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private fun assertSamePixels(label: String): android.graphics.Bitmap {
        compose.runOnIdle { wrappedVisible.value = true }
        compose.waitForIdle()
        val actual = compose.onNodeWithTag("app").captureToImage().asAndroidBitmap()
        compose.runOnIdle { wrappedVisible.value = false }
        compose.waitForIdle()
        val expected = compose.onNodeWithTag("official").captureToImage().asAndroidBitmap()
        assertTrue("$label must match the official Expressive component's pixels and dimensions", actual.sameAs(expected))
        return expected
    }

    @Composable
    private fun Marker() {
        Box(Modifier.size(20.dp).background(LocalContentColor.current))
    }

    @Composable
    private fun IconMarker() {
        Box(Modifier.fillMaxSize().background(LocalContentColor.current))
    }
}
