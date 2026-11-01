package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SettingsCard 折叠内容显隐管线契约 (MD3E motion 全量补齐 Task 7a):
 * 折叠内容经 AnimatedVisibility(sleepyExpandSpecs 家族) 显隐 — 断言动画
 * 落定后的存在性终态: expanded=true 内容稳态在树里、切 false 退场播完后
 * 节点真的移除、再切 true 重新入场 (与 T6 徽标管线测试同构)。
 *
 * 载体: 公开 SettingsCard(title, expanded, onToggle, content), 折叠开关
 * 由外部 holder 状态驱动 (与生产中 remember(expanded) 翻转同形态); 内容
 * 槽放带 testTag 的 Box, 存在性一律 testTag 判定不用文案 (locale 桶 trap,
 * T5 定型)。chevron 旋转与展开动画同拍, 但旋转是无语义装饰, 终态断言
 * 不触它 — 接线正确性由 ExpandChevronMotionContractTest 源码闸锁。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rUS-w360dp-h640dp-port-xhdpi")
class SettingsCardExpandPipelineTest {

    @get:Rule val compose = createComposeRule()

    private class Holder {
        var expanded by mutableStateOf(false)
    }

    private fun page(holder: Holder) {
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                SettingsCard(
                    title = "折叠卡",
                    expanded = holder.expanded,
                    onToggle = { holder.expanded = !holder.expanded },
                ) {
                    Box(modifier = Modifier.testTag("expand-content"))
                }
            }
        }
    }

    private fun contentCount(): Int =
        compose.onAllNodesWithTag("expand-content", useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun collapse_content_appears_and_disappears_with_expand_family() {
        val holder = Holder()
        page(holder)
        compose.waitForIdle()
        assertEquals("初始折叠态不得有内容节点", 0, contentCount())
        holder.expanded = true
        compose.waitForIdle()
        assertEquals("展开入场播完后内容必须稳态可见", 1, contentCount())
        holder.expanded = false
        compose.waitForIdle()
        assertEquals("收起退场播完后内容节点必须从树里移除", 0, contentCount())
    }

    @Test
    fun tap_header_toggle_drives_same_cycle() {
        // 行为面: onToggle 接线即点标题行翻折叠 — 走真实点击而非直接改 holder
        val holder = Holder()
        page(holder)
        compose.waitForIdle()
        assertEquals(0, contentCount())
        compose.onNodeWithText("折叠卡").performClick()
        compose.waitForIdle()
        assertEquals("点标题行应展开 (onToggle 接线)", 1, contentCount())
        compose.onNodeWithText("折叠卡").performClick()
        compose.waitForIdle()
        assertEquals("再点标题行应收起", 0, contentCount())
    }
}
