package com.lingion.sleepy.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DisplayModeOption 选中徽标显隐管线契约 (MD3E motion 全量补齐 Task 7b):
 * Check 徽标经 AnimatedVisibility(sleepyPopupEnter/Exit popup mini 家族) 显隐
 * — 断言动画落定后的存在性终态: selected=true 徽标稳态在树里、切 false 退场
 * 播完后节点真的移除、再切 true 重新入场 (与 T6/T7a 管线测试同构)。
 *
 * 色彩缓动 (labelColor) 无语义终态可断 (AFASE 中间态即正确), 接线正确性由
 * StateSwapMotionContractTest 源码闸锁; 存在性一律 testTag 判定不用文案
 * (locale 桶 trap, T5 定型)。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rUS-w360dp-h640dp-port-xhdpi")
class DisplayModeOptionCheckPipelineTest {

    @get:Rule val compose = createComposeRule()

    private class Holder {
        var selected by mutableStateOf(false)
    }

    private fun page(holder: Holder) {
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                DisplayModeOption(
                    label = "跟随系统",
                    subtitle = "按系统深色设置",
                    selected = holder.selected,
                    onClick = { holder.selected = !holder.selected },
                )
            }
        }
    }

    private fun checkCount(): Int =
        compose.onAllNodesWithTag("display-mode-check", useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun check_badge_appears_and_disappears_with_popup_family() {
        val holder = Holder()
        page(holder)
        compose.waitForIdle()
        assertEquals("初始未选态不得有徽标节点", 0, checkCount())
        holder.selected = true
        compose.waitForIdle()
        assertEquals("选中弹入播完后徽标必须稳态可见", 1, checkCount())
        holder.selected = false
        compose.waitForIdle()
        assertEquals("取消选中退场播完后徽标必须从树里移除", 0, checkCount())
    }

    @Test
    fun reselect_mounts_badge_again() {
        val holder = Holder()
        page(holder)
        compose.waitForIdle()
        holder.selected = true
        compose.waitForIdle()
        assertEquals(1, checkCount())
        holder.selected = false
        compose.waitForIdle()
        assertEquals(0, checkCount())
        holder.selected = true
        compose.waitForIdle()
        assertEquals("再次选中应重新入场", 1, checkCount())
    }
}
