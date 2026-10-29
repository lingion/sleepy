package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.util.TimeTableUtils.TimeSlotRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 时段编辑器重排缓动的公开 API 管线契约 (MD3E motion 全量补齐 Task 5 验收行:
 * "new pipeline test for reflow animation presence"):
 * reorderEnabled=false 时公开 TimeSlotEditor 直达 ManualTimeSlotEditor —
 * 增删行走真组合, 断言动画落定后的终态行节点数。
 *
 * 测试时钟隐式 waitForIdle: AnimatedVisibility enter 播完即稳态, animateContentSize
 * 收完即稳态 — 这里锁定"增行出现/删行收掉"的存在性终态, 不测过程帧
 * (过程帧归源码契约测试 + T1 sleepyExpandSpecs 家族自身)。
 * 存在性一律用 testTag 判定, 不用文案 — Robolectric 资源解析的 locale 桶
 * 与设备不同, 文案断言 (含 contentDescription) 在此 harness 不可靠。
 * 删除路径: canDelete = rows.size > 1, 单行无删除钮 — 断言钮消失即守卫。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rUS-w360dp-h640dp-port-xhdpi")
class TimeSlotEditorMotionPipelineTest {

    @get:Rule val compose = createComposeRule()

    private class Holder {
        var rows by mutableStateOf(listOf(TimeSlotRow(1, "08:00", "09:40")))
    }

    private fun page(holder: Holder) {
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .testTag("editor-host")
                ) {
                    TimeSlotEditor(
                        rows = holder.rows,
                        onRowsChange = { holder.rows = it },
                        reorderEnabled = false,
                    )
                }
            }
        }
    }

    private fun rowCount(): Int =
        compose.onAllNodesWithTag("timeslot-row").fetchSemanticsNodes().size

    private fun deleteCount(): Int =
        compose.onAllNodesWithTag("timeslot-delete", useUnmergedTree = true).fetchSemanticsNodes().size

    @Test fun adding_a_row_shows_the_entered_row_and_updates_count() {
        val holder = Holder()
        page(holder)
        compose.waitForIdle()
        assertEquals("初始 1 行", 1, rowCount())
        compose.onNodeWithTag("timeslot-add-row").performClick()
        compose.waitForIdle()
        assertEquals("新增行经 AnimatedVisibility 入场后必须稳态可见", 2, rowCount())
        assertEquals(2, holder.rows.size)
    }

    @Test fun deleting_a_row_collapses_it_away() {
        val holder = Holder()
        holder.rows = listOf(
            TimeSlotRow(1, "08:00", "09:40"),
            TimeSlotRow(2, "10:00", "11:40"),
        )
        page(holder)
        compose.waitForIdle()
        assertEquals(2, rowCount())
        assertEquals("两行时删除钮必须各就各位", 2, deleteCount())
        // 行根 semantics(mergeDescendants=true) 把删除钮并进行节点 — 未合并树才能单独命中钮
        compose.onAllNodesWithTag("timeslot-delete", useUnmergedTree = true)[0].performClick()
        compose.waitForIdle()
        assertEquals("删行收拢后行节点必须消失 (收拢由宿主 animateContentSize 播放)", 1, rowCount())
        assertEquals(1, holder.rows.size)
        assertEquals("剩单行后删除入口必须收起 (canDelete = size > 1)", 0, deleteCount())
    }

    @Test fun single_row_has_no_delete_affordance() {
        val holder = Holder()
        page(holder)
        compose.waitForIdle()
        // canDelete = displayRows.size > 1: 唯一行不得提供删除入口 (删空即坏数据)
        assertEquals("单行禁删守卫不许破", 0, deleteCount())
        assertEquals(1, rowCount())
    }
}
