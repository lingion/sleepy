package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.component.liquidglass.GlassAlertDialog
import com.lingion.sleepy.ui.component.liquidglass.GlassDropdownMenu
import com.lingion.sleepy.ui.component.liquidglass.GlassDropdownMenuItem
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.component.liquidglass.GlassTextButton
import com.lingion.sleepy.ui.component.liquidglass.LocalGlassAlertAction
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.LocalDarkTheme
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
import kotlinx.coroutines.flow.collect
import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.material3.DropdownMenu as MaterialDropdownMenu
import androidx.compose.material3.DropdownMenuItem as MaterialDropdownMenuItem
import androidx.compose.material3.TextButton as MaterialTextButton

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w600dp-h800dp-mdpi")
class GlassOverlayContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nativeAlertUsesMeasuredHeaderAndEqualActionCellsWithoutChangingCallbacks() {
        var confirmations = 0
        compose.setContent {
            Theme(UiStyle.LiquidGlass) {
                GlassAlertDialog(
                    modifier = Modifier.testTag("alert"), onDismissRequest = {},
                    title = { Text("Title", Modifier.fillMaxWidth().testTag("title")) },
                    text = { Text("Message", Modifier.fillMaxWidth().testTag("message")) },
                    dismissButton = { GlassTextButton({}, Modifier.testTag("cancel"), enabled = false) { Text("Cancel") } },
                    confirmButton = { GlassTextButton({ confirmations++ }, Modifier.testTag("confirm")) { Text("Confirm") } },
                )
            }
        }
        val alert = bounds("alert")
        val title = bounds("title")
        val message = bounds("message")
        val cancel = bounds("cancel")
        val confirm = bounds("confirm")
        close(320f, alert.width)
        close(30f, title.left - alert.left)
        close(260f, title.width)
        close(22f, title.top - alert.top)
        close(7f, message.top - title.bottom)
        close(140f, cancel.width)
        close(140f, confirm.width)
        close(48f, confirm.height)
        close(16f, cancel.left - alert.left)
        close(8f, confirm.left - cancel.right)
        close(16f, alert.right - confirm.right)
        close(16f, alert.bottom - confirm.bottom)
        compose.onNodeWithTag("cancel").assertIsNotEnabled()
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(1, confirmations)
    }

    @Test fun emptyActionSlotDoesNotLeaveAnInvisibleActionRow() {
        compose.setContent {
            Theme(UiStyle.LiquidGlass) {
                GlassAlertDialog(
                    modifier = Modifier.testTag("alert"), onDismissRequest = {}, confirmButton = {},
                    title = { Text("Title") },
                    text = { Text("Message", Modifier.fillMaxWidth().testTag("message")) },
                )
            }
        }
        close(4f, bounds("alert").bottom - bounds("message").bottom)
    }

    @Test fun nativeAlertDefaultsToLeadingTextWithoutExpandingCallerSlots() {
        var titleAlign = TextAlign.Unspecified
        var messageAlign = TextAlign.Unspecified
        compose.setContent {
            Theme(UiStyle.LiquidGlass) {
                GlassAlertDialog(
                    modifier = Modifier.testTag("alert"), onDismissRequest = {}, confirmButton = {},
                    title = {
                        titleAlign = LocalTextStyle.current.textAlign
                        Text("Short title", Modifier.testTag("title"))
                    },
                    text = {
                        messageAlign = LocalTextStyle.current.textAlign
                        Text("Short message", Modifier.testTag("message"))
                    },
                )
            }
        }
        close(30f, bounds("title").left - bounds("alert").left)
        close(30f, bounds("message").left - bounds("alert").left)
        assertTrue(bounds("title").width < 260f)
        assertTrue(bounds("message").width < 260f)
        assertEquals(TextAlign.Start, titleAlign)
        assertEquals(TextAlign.Start, messageAlign)
    }

    @Test fun existingScrollableBodyKeepsAFiniteViewportAndItsOwnActions() {
        var bodyWasAction = true
        var insideClicks = 0
        compose.setContent {
            Theme(UiStyle.LiquidGlass) {
                GlassAlertDialog(
                    onDismissRequest = {}, confirmButton = {}, title = { Text("Long form") },
                    text = {
                        bodyWasAction = LocalGlassAlertAction.current
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                            repeat(60) { Text("Form field $it") }
                            GlassTextButton({ insideClicks++ }) { Text("Save inside form") }
                        }
                    },
                )
            }
        }
        compose.onNodeWithText("Save inside form").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, insideClicks)
        assertFalse("body controls must not inherit the native alert action adaptation", bodyWasAction)
    }

    @Test fun explicitActionSizeAndColorsSurviveNativeDefaultCells() {
        var contentColor = Color.Unspecified
        compose.setContent {
            Theme(UiStyle.LiquidGlass) {
                GlassAlertDialog(
                    onDismissRequest = {}, title = { Text("Overrides") },
                    confirmButton = {
                        GlassTextButton(
                            {}, Modifier.width(96.dp).height(56.dp).testTag("custom"),
                            colors = ButtonDefaults.textButtonColors(containerColor = Color.Magenta, contentColor = Color.Green),
                        ) {
                            contentColor = LocalContentColor.current
                            Text("Custom")
                        }
                    },
                )
            }
        }
        close(96f, bounds("custom").width)
        close(56f, bounds("custom").height)
        assertEquals(Color.Green, contentColor)
    }

    @Test fun multipleNodesInACustomActionSlotRemainReachableInOrder() {
        val calls = mutableListOf<String>()
        compose.setContent {
            Theme(UiStyle.LiquidGlass) {
                GlassAlertDialog(
                    onDismissRequest = {}, title = { Text("Custom actions") },
                    confirmButton = {
                        GlassTextButton({ calls += "first" }, Modifier.testTag("first")) { Text("First") }
                        GlassTextButton({ calls += "second" }, Modifier.testTag("second")) { Text("Second") }
                    },
                )
            }
        }
        assertTrue(bounds("first").bottom <= bounds("second").top)
        compose.onNodeWithTag("first").performClick()
        compose.onNodeWithTag("second").performClick()
        assertEquals(listOf("first", "second"), calls)
    }

    @Test fun classicAlertStillMatchesDirectMaterialLayout() {
        val wrapped = mutableStateOf(false)
        compose.setContent {
            Theme(UiStyle.Classic) {
                if (wrapped.value) {
                    GlassAlertDialog(
                        onDismissRequest = {}, modifier = Modifier.testTag("alert"),
                        title = { Text("Title", Modifier.testTag("title")) },
                        text = { Text("Message", Modifier.testTag("message")) },
                        dismissButton = { GlassTextButton({}, Modifier.testTag("cancel")) { Text("Cancel") } },
                        confirmButton = { GlassTextButton({}, Modifier.testTag("confirm")) { Text("Confirm") } },
                    )
                } else {
                    MaterialAlertDialog(
                        onDismissRequest = {}, modifier = Modifier.testTag("alert"),
                        title = { Text("Title", Modifier.testTag("title")) },
                        text = { Text("Message", Modifier.testTag("message")) },
                        dismissButton = { MaterialTextButton({}, Modifier.testTag("cancel")) { Text("Cancel") } },
                        confirmButton = { MaterialTextButton({}, Modifier.testTag("confirm")) { Text("Confirm") } },
                    )
                }
            }
        }
        val tags = listOf("alert", "title", "message", "cancel", "confirm")
        val direct = tags.map(::bounds)
        compose.runOnIdle { wrapped.value = true }
        assertEquals(direct, tags.map(::bounds))
    }

    @Test fun glassMenuUsesNative42PointRowsAnd10PointShellEdges() {
        var clicks = 0
        compose.setContent {
            Theme(UiStyle.LiquidGlass) {
                GlassDropdownMenu(true, {}, Modifier.width(250.dp).testTag("menu")) {
                    repeat(3) { index ->
                        GlassDropdownMenuItem(
                            text = { Text("Action $index") }, onClick = { clicks++ },
                            modifier = Modifier.testTag("row$index"), enabled = index != 1,
                        )
                    }
                }
            }
        }
        val menu = bounds("menu")
        val rows = (0..2).map { bounds("row$it") }
        close(250f, menu.width)
        close(146f, menu.height)
        rows.forEach { close(42f, it.height) }
        close(10f, rows.first().top - menu.top)
        close(10f, menu.bottom - rows.last().bottom)
        compose.onNodeWithTag("row1").assertIsNotEnabled()
        compose.onNodeWithTag("row0").performClick()
        assertEquals(1, clicks)
    }

    @Test fun classicMenuStillMatchesDirectMaterialLayout() {
        val wrapped = mutableStateOf(false)
        compose.setContent {
            Theme(UiStyle.Classic) {
                if (wrapped.value) {
                    GlassDropdownMenu(true, {}, Modifier.width(250.dp).testTag("menu")) {
                        GlassDropdownMenuItem({ Text("Action") }, {}, Modifier.testTag("row"))
                    }
                } else {
                    MaterialDropdownMenu(true, {}, Modifier.width(250.dp).testTag("menu")) {
                        MaterialDropdownMenuItem({ Text("Action") }, {}, Modifier.testTag("row"))
                    }
                }
            }
        }
        val direct = listOf(bounds("menu"), bounds("row"))
        compose.runOnIdle { wrapped.value = true }
        assertEquals(direct, listOf(bounds("menu"), bounds("row")))
        assertTrue(bounds("row").height >= 48f)
    }

    /** Plain row raster regression; actual Popup GPU material is tested separately. */
    @Test fun menuPressCancelAndReleaseUseTheCallerInteractionSourceAndAnInsetFill() {
        val source = MutableInteractionSource()
        val delivered = mutableListOf<Interaction>()
        val dark = mutableStateOf(false)
        var clicks = 0
        compose.setContent {
            Theme(UiStyle.LiquidGlass) {
                val backing = if (dark.value) Color.Black else Color.White
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme(onSurface = Color.White) else lightColorScheme(onSurface = Color.Black)) {
                    CompositionLocalProvider(LocalDarkTheme provides dark.value) {
                        LaunchedEffect(source) { source.interactions.collect { delivered += it } }
                        GlassDropdownMenuItem(
                            { Text("Action") }, { clicks++ }, Modifier.width(250.dp).background(backing).testTag("row"),
                            interactionSource = source,
                        )
                    }
                }
            }
        }
        val row = compose.onNodeWithTag("row")
        for (isDark in listOf(false, true)) {
            compose.runOnIdle { dark.value = isDark; delivered.clear() }
            val rest = row.captureToImage().toPixelMap()
            row.performTouchInput { down(center) }
            compose.waitForIdle()
            assertTrue(delivered.last() is PressInteraction.Press)
            val held = row.captureToImage().toPixelMap()
            val x = held.width - 40
            val y = held.height / 2
            if (isDark) assertTrue(held[x, y].red > rest[x, y].red + .1f)
            else assertTrue(held[x, y].red < rest[x, y].red - .02f)
            assertEquals("horizontal inset must stay untouched", rest[5, y], held[5, y])
            assertEquals("capsule corner must stay untouched", rest[11, 1], held[11, 1])
            row.performTouchInput { cancel() }
            compose.waitForIdle()
            assertTrue(delivered.last() is PressInteraction.Cancel)
            assertEquals(rest[x, y], row.captureToImage().toPixelMap()[x, y])
            assertEquals(0, clicks)
        }
        row.performTouchInput { down(center); up() }
        compose.waitForIdle()
        assertTrue(delivered.last() is PressInteraction.Release)
        assertEquals(1, clicks)
    }

    @Test fun disabledMenuRowEmitsNoPressFeedbackOrAction() {
        val source = MutableInteractionSource()
        val delivered = mutableListOf<Interaction>()
        var clicks = 0
        compose.setContent {
            Theme(UiStyle.LiquidGlass) {
                LaunchedEffect(source) { source.interactions.collect { delivered += it } }
                GlassDropdownMenuItem(
                    { Text("Disabled") }, { clicks++ }, Modifier.width(250.dp).background(Color.White).testTag("row"),
                    enabled = false,
                    interactionSource = source,
                )
            }
        }
        val row = compose.onNodeWithTag("row").assertIsNotEnabled()
        val rest = row.captureToImage().toPixelMap()
        row.performTouchInput { down(center) }
        compose.waitForIdle()
        val held = row.captureToImage().toPixelMap()
        assertEquals(rest[rest.width - 40, rest.height / 2], held[held.width - 40, held.height / 2])
        row.performTouchInput { up() }
        compose.runOnIdle {
            assertTrue(delivered.isEmpty())
            assertEquals(0, clicks)
        }
    }

    @Composable private fun Theme(style: UiStyle, content: @Composable () -> Unit) {
        MaterialTheme {
            CompositionLocalProvider(LocalUiStyle provides style) { GlassHost { content() } }
        }
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun close(expected: Float, actual: Float) = assertEquals(expected, actual, 1f)
}
