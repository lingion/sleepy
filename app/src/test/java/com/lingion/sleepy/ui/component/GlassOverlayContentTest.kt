package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.component.liquidglass.GlassAlertDialog
import com.lingion.sleepy.ui.component.liquidglass.GlassDatePickerDialog
import com.lingion.sleepy.ui.component.liquidglass.GlassDropdownMenu
import com.lingion.sleepy.ui.component.liquidglass.GlassDropdownMenuItem
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.component.liquidglass.GlassTextButton
import com.lingion.sleepy.ui.component.liquidglass.LocalGlassAlertAction
import com.lingion.sleepy.ui.component.liquidglass.NativeGlassAlertActionGroup
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.LocalDarkTheme
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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

    @After fun restoreFontScale() { RuntimeEnvironment.setFontScale(1f) }

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

    @Test fun longRawActionSlotsStackWithEqualWidthAndHeight() = checkLongRawActions(fontScale = 1f)

    @Test fun longRawActionSlotsUseRealDialogFontScaleAndKeepEqualCells() = checkLongRawActions(fontScale = 2f)

    private fun checkLongRawActions(fontScale: Float) {
        val label = "Save every change to this timetable before closing"
        var confirmations = 0
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            Theme(UiStyle.LiquidGlass) {
                GlassAlertDialog(
                    {}, modifier = Modifier.testTag("alert"),
                    dismissButton = {
                        GlassTextButton({}, Modifier.testTag("cancel"), enabled = false) { Text("Cancel") }
                    },
                    confirmButton = {
                        GlassTextButton({ confirmations++ }, Modifier.testTag("confirm")) { Text(label) }
                    },
                )
            }
        }
        val pane = bounds("alert")
        val cancel = bounds("cancel")
        val confirm = bounds("confirm")
        close(320f, pane.width)
        close(288f, cancel.width)
        close(cancel.width, confirm.width)
        close(cancel.height, confirm.height)
        assertTrue("the long label must grow both cells beyond the native minimum", confirm.height > 48f)
        close(16f, cancel.left - pane.left)
        close(cancel.left, confirm.left)
        close(16f, cancel.top - pane.top)
        close(8f, confirm.top - cancel.bottom)
        close(16f, pane.bottom - confirm.bottom)
        var layout: TextLayoutResult? = null
        compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            val results = mutableListOf<TextLayoutResult>()
            action(results)
            layout = results.single()
        }
        val text = requireNotNull(layout)
        assertEquals(fontScale, text.layoutInput.density.fontScale, 0f)
        assertTrue("the full-width action must really contain wrapped text", text.lineCount > 1)
        assertEquals(label.length, text.getLineEnd(text.lineCount - 1, visibleEnd = true))
        assertFalse(text.isLineEllipsized(text.lineCount - 1))
        assertTrue(text.size.height <= confirm.height)
        compose.onNodeWithTag("cancel").assertIsNotEnabled()
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(1, confirmations)
    }

    @Test fun subcomposedRawActionContentKeepsItsSingleCompositionAndExistingLayout() {
        var cancelCompositions = 0
        var confirmCompositions = 0
        val calls = mutableListOf<String>()
        compose.setContent {
            Theme(UiStyle.LiquidGlass) {
                GlassAlertDialog(
                    {}, modifier = Modifier.testTag("alert"),
                    dismissButton = {
                        DisposableEffect(Unit) {
                            cancelCompositions++
                            onDispose { }
                        }
                        GlassTextButton({ calls += "cancel" }, Modifier.testTag("cancel")) {
                            BoxWithConstraints { Text("Cancel") }
                        }
                    },
                    confirmButton = {
                        DisposableEffect(Unit) {
                            confirmCompositions++
                            onDispose { }
                        }
                        GlassTextButton({ calls += "confirm" }, Modifier.testTag("confirm")) { Text("Confirm") }
                    },
                )
            }
        }
        val pane = bounds("alert")
        val cancel = bounds("cancel")
        val confirm = bounds("confirm")
        close(140f, cancel.width)
        close(140f, confirm.width)
        close(48f, cancel.height)
        close(48f, confirm.height)
        close(16f, cancel.left - pane.left)
        close(8f, confirm.left - cancel.right)
        compose.onNodeWithTag("cancel").performClick()
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(listOf("cancel", "confirm"), calls)
        assertEquals(1, cancelCompositions)
        assertEquals(1, confirmCompositions)
    }

    @Test fun glassDatePickerUsesTheSameFullWidthNativeActionGroupWithoutAdaptingItsBody() {
        var bodyWasAction = true
        var cancellations = 0
        var confirmations = 0
        compose.setContent {
            Theme(UiStyle.LiquidGlass) {
                GlassDatePickerDialog(
                    onDismissRequest = {}, modifier = Modifier.testTag("date-alert"),
                    dismissButton = {
                        GlassTextButton({ cancellations++ }, Modifier.testTag("cancel")) { Text("Cancel") }
                    },
                    confirmButton = {
                        GlassTextButton({ confirmations++ }, Modifier.testTag("confirm")) { Text("Confirm") }
                    },
                ) {
                    bodyWasAction = LocalGlassAlertAction.current
                    Box(Modifier.fillMaxWidth().height(100.dp).testTag("calendar"))
                }
            }
        }
        val pane = bounds("date-alert")
        val cancel = bounds("cancel")
        val confirm = bounds("confirm")
        close(360f, pane.width)
        close(160f, cancel.width)
        close(160f, confirm.width)
        close(16f, cancel.left - pane.left)
        close(8f, confirm.left - cancel.right)
        close(16f, pane.bottom - confirm.bottom)
        close(48f, confirm.height)
        assertFalse(bodyWasAction)
        compose.onNodeWithTag("cancel").performClick()
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(1, cancellations)
        assertEquals(1, confirmations)
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Test fun realDatePickerKeepsMultipleBodyNodesVerticalAndACompleteConfirmGroupOwnsOneInset() {
        val calls = mutableListOf<String>()
        var bodyWasAction = true
        compose.setContent {
            Theme(UiStyle.LiquidGlass) {
                GlassDatePickerDialog(
                    {}, modifier = Modifier.testTag("date-alert"),
                    confirmButton = {
                        DialogActionButtons(
                            "Confirm", { calls += "confirm" }, dismissText = "Cancel",
                            onDismiss = { calls += "cancel" }, dbgTagPrefix = "date-actions",
                        )
                    },
                ) {
                    bodyWasAction = LocalGlassAlertAction.current
                    DatePicker(
                        state = rememberDatePickerState(initialDisplayedMonthMillis = 1767225600000L),
                        modifier = Modifier.fillMaxWidth().testTag("calendar"), showModeToggle = false,
                    )
                    Box(Modifier.fillMaxWidth().height(24.dp).testTag("calendar-footer"))
                }
            }
        }
        val pane = bounds("date-alert")
        val calendar = bounds("calendar")
        val footer = bounds("calendar-footer")
        val cancel = bounds("date-actions_dismiss")
        val confirm = bounds("date-actions_confirm")
        close(360f, pane.width)
        close(calendar.bottom, footer.top)
        close(24f, footer.height)
        close(16f, cancel.top - footer.bottom)
        close(16f, cancel.left - pane.left)
        close(160f, cancel.width)
        close(160f, confirm.width)
        close(8f, confirm.left - cancel.right)
        close(16f, pane.bottom - confirm.bottom)
        assertFalse(bodyWasAction)
        compose.onNodeWithTag("date-actions_dismiss").performClick()
        compose.onNodeWithTag("date-actions_confirm").performClick()
        assertEquals(listOf("cancel", "confirm"), calls)
    }

    @Test fun completeStackedGroupInAlertConfirmSlotOwnsItsOnlyInsets() {
        compose.setContent {
            Theme(UiStyle.LiquidGlass) {
                GlassAlertDialog(
                    {}, modifier = Modifier.testTag("alert"),
                    confirmButton = {
                        NativeGlassAlertActionGroup(
                            stacked = true,
                            dismissButton = { GlassTextButton({}, Modifier.testTag("cancel")) { Text("Cancel") } },
                            confirmButton = { GlassTextButton({}, Modifier.testTag("confirm")) { Text("Confirm") } },
                        )
                    },
                )
            }
        }
        val pane = bounds("alert")
        val cancel = bounds("cancel")
        val confirm = bounds("confirm")
        close(16f, cancel.left - pane.left)
        close(16f, cancel.top - pane.top)
        close(288f, cancel.width)
        close(288f, confirm.width)
        close(8f, confirm.top - cancel.bottom)
        close(16f, pane.bottom - confirm.bottom)
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Test fun materialDatePickerStillMatchesDirectOfficialLayout() {
        val wrapped = mutableStateOf(false)
        compose.setContent {
            Theme(UiStyle.Classic) {
                if (wrapped.value) {
                    GlassDatePickerDialog(
                        {}, modifier = Modifier.testTag("date-alert"),
                        dismissButton = { GlassTextButton({}, Modifier.testTag("cancel")) { Text("Cancel") } },
                        confirmButton = { GlassTextButton({}, Modifier.testTag("confirm")) { Text("Confirm") } },
                    ) { Box(Modifier.fillMaxWidth().height(100.dp).testTag("calendar")) }
                } else {
                    androidx.compose.material3.DatePickerDialog(
                        {}, modifier = Modifier.testTag("date-alert"),
                        dismissButton = {
                            MaterialTextButton({}, shapes = ButtonDefaults.shapes(), modifier = Modifier.testTag("cancel")) { Text("Cancel") }
                        },
                        confirmButton = {
                            MaterialTextButton({}, shapes = ButtonDefaults.shapes(), modifier = Modifier.testTag("confirm")) { Text("Confirm") }
                        },
                    ) { Box(Modifier.fillMaxWidth().height(100.dp).testTag("calendar")) }
                }
            }
        }
        val tags = listOf("date-alert", "calendar", "cancel", "confirm")
        val direct = tags.map(::bounds)
        compose.runOnIdle { wrapped.value = true }
        assertEquals(direct, tags.map(::bounds))
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
                        dismissButton = { MaterialTextButton({}, shapes = ButtonDefaults.shapes(), modifier = Modifier.testTag("cancel")) { Text("Cancel") } },
                        confirmButton = { MaterialTextButton({}, shapes = ButtonDefaults.shapes(), modifier = Modifier.testTag("confirm")) { Text("Confirm") } },
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
