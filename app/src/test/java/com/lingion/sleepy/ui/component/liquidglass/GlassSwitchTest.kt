package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GlassSwitchTest {
    @get:Rule val compose = createComposeRule()
    private val checked = mutableStateOf(false)
    private val enabled = mutableStateOf(true)
    private val changes = mutableListOf<Boolean>()

    private fun show(
        rtl: Boolean = false,
        accept: Boolean = true,
        parentOwnsClick: Boolean = false,
        style: UiStyle = UiStyle.LiquidGlass,
        host: Boolean = true,
    ) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalUiStyle provides style,
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    GlassHost(enabled = host) {
                        val update: (Boolean) -> Unit = { changes += it; if (accept) checked.value = it }
                        Row(if (parentOwnsClick) Modifier.testTag("row").toggleable(
                            value = checked.value, role = Role.Switch, onValueChange = update,
                        ) else Modifier) {
                            GlassSwitch(
                                checked = checked.value,
                                onCheckedChange = if (parentOwnsClick) null else update,
                                enabled = enabled.value,
                                modifier = Modifier.testTag("switch"),
                                thumbContent = {
                                    Box(Modifier.testTag("thumb").fillMaxSize()) { Text("thumb") }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    @Test fun pressStretchesThumbIntoCapsuleWiderThanTall() {
        // iOS 26 定标(用户 2026-11-04: 按住变宽 + 圆形泡成胶囊): 按住(或拖动)时 thumb 宽显著
        // 大于高, 松手(tapLift 200ms + lift 弹簧落定)后回弹近方。thumb 载体 = thumbContent 根
        // Box(fillMaxSize 贴合滑块 requiredSize 溢出尺寸); 尺寸取 SemanticsNode.boundsInRoot(px)。
        show()
        val thumb = compose.onNodeWithTag("thumb", useUnmergedTree = true)
        fun thumbBox() = thumb.fetchSemanticsNode().boundsInRoot
        val rest = thumbBox()
        assertTrue("rest thumb must be near-square (circle): $rest",
            kotlin.math.abs(rest.width - rest.height) < rest.width * 0.35f)
        val switch = compose.onNodeWithTag("switch")
        switch.performTouchInput { down(Offset(centerX, centerY)) }
        compose.waitForIdle()
        val pressed = thumbBox()
        assertTrue("pressed thumb must grow wider than tall (capsule): $pressed",
            pressed.width > pressed.height * 1.25f)
        switch.performTouchInput { up() }
        compose.waitForIdle()
        val settled = thumbBox()
        assertTrue("released thumb settles back near its rest size: $settled",
            kotlin.math.abs(settled.width - rest.width) < 3f &&
                kotlin.math.abs(settled.height - rest.height) < 3f)
    }

    @Test fun tapCommitsOnceAndExposesSwitchStateAndMinimumTarget() {
        show()
        compose.onNodeWithTag("switch")
            .assertIsOff()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            .performTouchInput { click() }
        compose.onNodeWithTag("switch").assertIsOn()
        compose.onNodeWithText("thumb", useUnmergedTree = true).assertExists()
        compose.runOnIdle { assertEquals(listOf(true), changes) }
    }

    @Test fun dragCrossingThresholdCommitsOnlyOnRelease() {
        show()
        val node = compose.onNodeWithTag("switch")
        node.performTouchInput {
            down(Offset(width / 6f, centerY))
            moveTo(Offset(width * 5f / 6f, centerY), 250)
        }
        compose.runOnIdle { assertEquals(emptyList<Boolean>(), changes) }
        node.performTouchInput { up() }
        node.assertIsOn()
        compose.runOnIdle { assertEquals(listOf(true), changes) }
    }

    @Test fun dragReturningBelowThresholdDoesNotToggle() {
        show()
        compose.onNodeWithTag("switch").performTouchInput {
            down(Offset(width / 6f, centerY))
            moveTo(Offset(width * 5f / 6f, centerY), 200)
            moveTo(Offset(width / 6f, centerY), 200)
            up()
        }.assertIsOff()
        compose.runOnIdle { assertEquals(emptyList<Boolean>(), changes) }
    }

    @Test fun cancelDoesNotCommitAndNextTapWorks() {
        show()
        compose.onNodeWithTag("switch").performTouchInput {
            down(Offset(width / 6f, centerY))
            moveTo(Offset(width * 5f / 6f, centerY), 250)
            cancel()
        }.assertIsOff()
        compose.runOnIdle { assertEquals(emptyList<Boolean>(), changes) }
        compose.onNodeWithTag("switch").performTouchInput { click() }.assertIsOn()
        compose.runOnIdle { assertEquals(listOf(true), changes) }
    }

    @Test fun disabledSwitchIgnoresTapAndDrag() {
        enabled.value = false
        show()
        compose.onNodeWithTag("switch").assertIsNotEnabled().performTouchInput {
            click()
            swipe(Offset(width / 6f, centerY), Offset(width * 5f / 6f, centerY), 300)
        }.assertIsOff()
        compose.runOnIdle { assertEquals(emptyList<Boolean>(), changes) }
    }

    @Test fun rtlDragMovesFromRightOffToLeftOn() {
        show(rtl = true)
        compose.onNodeWithTag("switch").performTouchInput {
            swipe(Offset(width * 5f / 6f, centerY), Offset(width / 6f, centerY), 300)
        }.assertIsOn()
        compose.runOnIdle { assertEquals(listOf(true), changes) }
    }

    @Test fun rejectedDragSettlesBackBeforeNextGesture() {
        show(accept = false)
        repeat(2) {
            compose.onNodeWithTag("switch").performTouchInput {
                swipe(Offset(width / 6f, centerY), Offset(width * 5f / 6f, centerY), 300)
            }.assertIsOff()
            compose.waitForIdle()
        }
        compose.runOnIdle { assertEquals(listOf(true, true), changes) }
    }

    @Test fun externalUpdateDuringDragCancelsStaleCommit() {
        show()
        val node = compose.onNodeWithTag("switch")
        node.performTouchInput {
            down(Offset(width / 6f, centerY))
            moveTo(Offset(width * 5f / 6f, centerY), 200)
        }
        compose.runOnIdle { checked.value = true }
        node.performTouchInput {
            moveTo(Offset(width / 6f, centerY), 200)
            up()
        }.assertIsOn()
        compose.runOnIdle { assertEquals(emptyList<Boolean>(), changes) }
    }

    @Test fun disablingDuringDragCancelsPendingCommit() {
        show()
        val node = compose.onNodeWithTag("switch")
        node.performTouchInput {
            down(Offset(width / 6f, centerY))
            moveTo(Offset(width * 5f / 6f, centerY), 200)
        }
        compose.runOnIdle { enabled.value = false }
        node.performTouchInput { up() }.assertIsOff().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(emptyList<Boolean>(), changes) }
    }

    @Test fun nullCallbackLeavesTouchAndSemanticActionToParent() {
        show(parentOwnsClick = true)
        compose.onNodeWithTag("switch", useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
            .performTouchInput { click() }
        compose.onNodeWithTag("row").assertIsOn()
        compose.runOnIdle { assertEquals(listOf(true), changes) }
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @Test fun keyboardActivationCommitsOnce() {
        show()
        val node = compose.onNodeWithTag("switch")
        node.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        node.performKeyInput { pressKey(Key.Spacebar) }.assertIsOn()
        compose.runOnIdle { assertEquals(listOf(true), changes) }
    }

    @Test fun classicUsesMaterialInteraction() {
        show(style = UiStyle.Classic)
        compose.onNodeWithTag("switch").performClick().assertIsOn()
        compose.runOnIdle { assertEquals(listOf(true), changes) }
    }

    @Test fun missingHostUsesMaterialInteraction() {
        show(host = false)
        compose.onNodeWithTag("switch").performClick().assertIsOn()
        compose.runOnIdle { assertEquals(listOf(true), changes) }
    }
}
