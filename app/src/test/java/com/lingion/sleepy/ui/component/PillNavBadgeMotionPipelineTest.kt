package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
 * 底栏徽标圆点显隐管线契约 (MD3E motion 全量补齐 Task 6):
 * 徽标经 AnimatedVisibility(popup 家族) 显隐 — 断言动画落定后的存在性终态:
 * badge=true 出现、切 false 消失、再切 true 重新出现 (退场节点不得赖在树里)。
 *
 * 载体: 公开 PillNavigationBar + PillNavItemSpec(badge) 数据驱动, 用可变
 * items 状态重放徽标开关 — 与 AppRoot 生产中 badge 随状态变化的形态一致。
 * dock=false(贴底) 与 dock=true(Dock) 两形态各测一遍, 两处接线都必须活。
 *
 * 存在性一律 testTag("nav-badge") 判定不用文案 (locale 桶 trap, T5 定型);
 * 徽标 Box 无自身语义, 用 unmerged 树命中。测试时钟隐式 waitForIdle:
 * popup enter/exit 播完即稳态。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rUS-w360dp-h640dp-port-xhdpi")
class PillNavBadgeMotionPipelineTest {

    @get:Rule val compose = createComposeRule()

    private class Holder {
        var badge by mutableStateOf(false)
    }

    private fun page(holder: Holder, dock: Boolean) {
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                PillNavigationBar(
                    items = listOf(
                        PillNavItemSpec(Icons.Filled.Home, "课表", badge = holder.badge),
                        PillNavItemSpec(Icons.Filled.Check, "我的"),
                    ),
                    selectedIndex = 0,
                    onSelect = {},
                    dock = dock,
                    modifier = Modifier.fillMaxSize().testTag("nav-host"),
                )
            }
        }
    }

    private fun badgeCount(): Int =
        compose.onAllNodesWithTag("nav-badge", useUnmergedTree = true).fetchSemanticsNodes().size

    private fun runBadgeCycle(dock: Boolean) {
        val holder = Holder()
        page(holder, dock)
        compose.waitForIdle()
        assertEquals("初始 badge=false 不得有徽标节点", 0, badgeCount())
        holder.badge = true
        compose.waitForIdle()
        assertEquals("badge=true 入场播完后徽标必须稳态可见", 1, badgeCount())
        holder.badge = false
        compose.waitForIdle()
        assertEquals("badge=false 退场播完后徽标节点必须从树里移除", 0, badgeCount())
    }

    @Test fun badge_dot_appears_and_disappears_in_flat_bar() = runBadgeCycle(dock = false)

    @Test fun badge_dot_appears_and_disappears_in_dock_bar() = runBadgeCycle(dock = true)
}
