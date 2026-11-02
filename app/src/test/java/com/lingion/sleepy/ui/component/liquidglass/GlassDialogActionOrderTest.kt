package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.component.DialogActionButtons
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
@Config(sdk = [35], qualifiers = "w600dp-h800dp-mdpi")
class GlassDialogActionOrderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun horizontalActionsKeepDismissThirdConfirmOrderAndTheirCallbacks() {
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
        assertTrue("left-to-right order must be Cancel, Copy, Confirm", cancel.center.x < copy.center.x && copy.center.x < confirm.center.x)
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
}
