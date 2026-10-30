package com.lingion.sleepy.console

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lingion.sleepy.console.ui.TagRegistry
import com.lingion.sleepy.console.ui.dbgTag
import com.lingion.sleepy.ui.component.SegmentedSwitcher
import com.lingion.sleepy.ui.theme.noRippleClickable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Task 3 Step 5（brief 原文）：Compose 规则下 dbgTag 节点 performClick 后
 * UI 状态变化可断言；无坐标参与。
 *
 * 三条语义链路各自钉死：
 *  1. tag 选择器命中 dbgTag 节点, performClick 真实翻转业务状态（testTag 面）；
 *  2. 同一 tag 的多实例用 text 二次筛可区分（find 语义前提, 生产同名标签
 *     settings_theme 就是主题段按钮组共用 tag 不同 text）；
 *  3. onPlaced 已把节点 bounds + clickable 上报 TagRegistry —— Gestures 的
 *     "语义优先命中注册 View" 通路没有这张表就退化成坐标兜底。
 *
 * 用最小宿主复现打标形态（SegmentedSwitcher/Switch/手势行 = 线上三种真实形态）,
 * 整屏装配链归 Task 8 设备验收清单。
 */
class UiOpsConsoleAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dbgTagNodeClickedByTagAlone_flipsState_andRegistryHoldsBounds() {
        var selected by mutableIntStateOf(0)
        var switched by mutableStateOf(false)
        composeRule.setContent {
            Column {
                SegmentedSwitcher(
                    options = listOf(0 to "system", 1 to "light", 2 to "dark"),
                    selected = selected,
                    onSelect = { selected = it },
                    dbgTagPrefix = "settings_theme"
                )
                Switch(
                    checked = switched,
                    onCheckedChange = { switched = it },
                    modifier = Modifier.dbgTag("settings_theme_switch", role = "switch")
                )
            }
        }

        // 1) 纯 tag 语义点击 — 全程无坐标
        composeRule.onNodeWithTag("settings_theme#1", useUnmergedTree = true).performClick()
        composeRule.runOnIdle { assertEquals(1, selected) }

        composeRule.onNodeWithTag("settings_theme_switch").performClick()
        composeRule.runOnIdle { assertTrue(switched) }

        // 2) 注册表侧: onPlaced 上报了该节点的 bounds 与身份
        val seg = TagRegistry.snapshot().firstOrNull { it.tag == "settings_theme#1" }
        assertNotNull("dbgTag 的 onPlaced 必须把节点写进 TagRegistry", seg)
        assertTrue("注册 bounds 必须非零面积", seg!!.bounds.right > seg.bounds.left && seg.bounds.bottom > seg.bounds.top)
        assertEquals("light", seg.text)
        assertTrue("按钮节点默认可点", seg.clickable)
    }

    @Test
    fun sameTagMultipleInstances_areDistinguishedByText() {
        var taps by mutableIntStateOf(0)
        var tappedName by mutableStateOf<String?>(null)
        composeRule.setContent {
            Column {
                // 生产形态复刻: AppearanceScreen 主题卡共用 tag=settings_theme, text 区分
                listOf("system", "light").forEach { name ->
                    Text(
                        text = name,
                        modifier = Modifier
                            .noRippleClickable { taps++; tappedName = name }
                            .dbgTag("settings_theme", text = name, role = "button")
                    )
                }
            }
        }
        val both = TagRegistry.snapshot().filter { it.tag == "settings_theme" }
        assertEquals("同 tag 多实例必须共存", 2, both.size)
        assertEquals(setOf("system", "light"), both.mapNotNull { it.text }.toSet())

        composeRule.onNodeWithText("light").performClick()
        composeRule.runOnIdle {
            assertEquals(1, taps)
            assertEquals("light", tappedName)
        }
    }

    /** 手势区（TimeSlotEditor 行拖拽）形态: 注册但不作语义 tap 目标。 */
    @Test
    fun gestureRegion_registeredButNotTapTarget() {
        composeRule.setContent {
            Text(
                "row",
                modifier = Modifier
                    .noRippleClickable { }
                    .dbgTag("slot_row#0", text = "第1节", clickable = false)
            )
        }
        val row = TagRegistry.snapshot().first { it.tag == "slot_row#0" }
        assertTrue("手势区 clickable=false → Gestures.tap 不选它做命中目标", !row.clickable)
        assertNotNull(row.text)
    }
}
