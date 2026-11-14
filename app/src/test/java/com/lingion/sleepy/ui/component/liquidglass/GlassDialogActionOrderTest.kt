package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.component.DialogActionButtons
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w600dp-h800dp-mdpi")
class GlassDialogActionOrderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun threeNativeActionsStackInDismissThirdConfirmOrderAndKeepTheirCallbacks() {
        val calls = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        Box(Modifier.width(480.dp)) {
                            DialogActionButtons(
                                confirmText = "Confirm", onConfirm = { calls += "confirm" },
                                dismissText = "Cancel", onDismiss = { calls += "cancel" },
                                thirdText = "Copy", onThird = { calls += "copy" },
                            )
                        }
                    }
                }
            }
        }
        val cancel = compose.onNodeWithText("Cancel").fetchSemanticsNode().boundsInRoot
        val copy = compose.onNodeWithText("Copy").fetchSemanticsNode().boundsInRoot
        val confirm = compose.onNodeWithText("Confirm").fetchSemanticsNode().boundsInRoot
        assertTrue("three native cells retain Cancel, Copy, Confirm order", cancel.bottom <= copy.top && copy.bottom <= confirm.top)
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Copy").performClick()
        compose.onNodeWithText("Confirm").performClick()
        assertEquals(listOf("cancel", "copy", "confirm"), calls)
    }

    @Test fun narrowLongLabelsStackInTheSameOrderAndDisabledConfirmStaysDisabled() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        Box(Modifier.width(180.dp)) {
                            DialogActionButtons(
                                confirmText = "确认保存这次修改", onConfirm = { error("disabled confirm") },
                                dismissText = "返回继续编辑", thirdText = "复制排查报告内容",
                                confirmEnabled = false,
                            )
                        }
                    }
                }
            }
        }
        val cancel = compose.onNodeWithText("返回继续编辑").fetchSemanticsNode().boundsInRoot
        val copy = compose.onNodeWithText("复制排查报告内容").fetchSemanticsNode().boundsInRoot
        val confirm = compose.onNodeWithText("确认保存这次修改").fetchSemanticsNode().boundsInRoot
        assertTrue("top-to-bottom order must stay Cancel, Copy, Confirm", cancel.center.y < copy.center.y && copy.center.y < confirm.center.y)
        compose.onNodeWithText("确认保存这次修改").assertIsNotEnabled()
    }

    @Test fun twoShortActionsReuseMeasuredNativeCellsAndTypography() {
        compose.setContent {
            Fixture {
                DialogActionButtons(
                    "Confirm", {}, dismissText = "Cancel", dbgTagPrefix = "actions",
                    modifier = Modifier.width(320.dp).testTag("group"),
                )
            }
        }
        val group = bounds("group")
        val cancel = bounds("actions_dismiss")
        val confirm = bounds("actions_confirm")
        close(16f, cancel.left - group.left)
        close(140f, cancel.width)
        close(140f, confirm.width)
        close(8f, confirm.left - cancel.right)
        close(48f, confirm.height)
        close(16f, group.bottom - confirm.bottom)
        assertEquals(17f, textLayout("Confirm").layoutInput.style.fontSize.value, 0f)
    }

    @Test fun destructiveConfirmAndThirdUseNativeRedTextInsteadOfMaterialBlockColors() {
        var expected = Color.Unspecified
        compose.setContent {
            Fixture {
                expected = MaterialTheme.colorScheme.error
                DialogActionButtons(
                    "Delete", {}, dismissText = "Cancel", thirdText = "Discard",
                    destructive = true, thirdDestructive = true, modifier = Modifier.width(320.dp),
                )
            }
        }
        assertEquals(expected, textLayout("Delete").layoutInput.style.color)
        assertEquals(expected, textLayout("Discard").layoutInput.style.color)
    }

    @Test fun inlineActionGroupIsLocalAndAllDisabledActionsRejectRealPresses() {
        val presses = NativeGlassAlertPressState()
        val enabled = mutableStateOf(false)
        var bodyWasAction = true
        var afterWasAction = true
        var calls = 0
        compose.setContent {
            Fixture {
                CompositionLocalProvider(LocalGlassAlertPressState provides presses) {
                    Column(Modifier.width(320.dp)) {
                        bodyWasAction = LocalGlassAlertAction.current
                        GlassTextButton({ calls++ }) { Text("Body control") }
                        DialogActionButtons(
                            "Confirm", { calls++ }, dismissText = "Cancel", onDismiss = { calls++ },
                            thirdText = "Third", onThird = { calls++ },
                            confirmEnabled = enabled.value, dismissEnabled = enabled.value, thirdEnabled = enabled.value,
                        )
                        afterWasAction = LocalGlassAlertAction.current
                        Text("Following body")
                    }
                }
            }
        }
        assertFalse(bodyWasAction)
        assertFalse(afterWasAction)
        compose.onNodeWithText("Body control").performTouchInput { down(center) }
        compose.runOnIdle { assertFalse(presses.isPressed) }
        compose.onNodeWithText("Body control").performTouchInput { cancel() }
        listOf("Cancel", "Third", "Confirm").forEach { label ->
            compose.onNodeWithText(label).assertIsNotEnabled().performTouchInput { click() }
        }
        compose.runOnIdle {
            assertEquals(0, calls)
            assertFalse(presses.isPressed)
        }
        compose.runOnIdle { enabled.value = true }
        compose.onNodeWithText("Confirm").performTouchInput { down(center) }
        compose.runOnIdle { assertTrue(presses.isPressed) }
        compose.onNodeWithText("Confirm").performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(1, calls)
            assertFalse(presses.isPressed)
        }
    }

    @Test fun twoLongActionsMeasureActualTextAndGrowAtDoubleFontScale() {
        compose.setContent {
            Fixture(fontScale = 2f) {
                DialogActionButtons(
                    "Save all changes to this timetable", {}, dismissText = "Return to editing",
                    dbgTagPrefix = "large", modifier = Modifier.width(280.dp),
                )
            }
        }
        val cancel = bounds("large_dismiss")
        val confirm = bounds("large_confirm")
        assertTrue(cancel.bottom <= confirm.top)
        assertTrue("native actions grow rather than clipping the current font", confirm.height > 48f)
        val text = textLayout("Save all changes to this timetable")
        assertTrue(text.lineCount > 1)
        assertFalse(text.hasVisualOverflow)
    }

    @Test fun materialAndGlassWithoutAHostKeepTheirExistingBlockButtonTypography() {
        var expected = Color.Unspecified
        compose.setContent {
            MaterialExpressiveTheme {
                expected = MaterialTheme.colorScheme.onPrimary
                Column {
                    CompositionLocalProvider(LocalUiStyle provides UiStyle.Classic) {
                        DialogActionButtons("Material", {}, modifier = Modifier.width(320.dp))
                    }
                    CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                        DialogActionButtons("No host", {}, modifier = Modifier.width(320.dp))
                    }
                }
            }
        }
        assertEquals(expected, textLayout("Material").layoutInput.style.color)
        assertEquals(expected, textLayout("No host").layoutInput.style.color)
        assertEquals(textLayout("Material").layoutInput.style, textLayout("No host").layoutInput.style)
    }

    @Composable
    private fun Fixture(fontScale: Float = 1f, content: @Composable () -> Unit) {
        MaterialExpressiveTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalUiStyle provides UiStyle.LiquidGlass,
                LocalDensity provides Density(density.density, fontScale),
            ) { GlassHost { content() } }
        }
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun textLayout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun close(expected: Float, actual: Float) = assertEquals(expected, actual, .05f)
}
