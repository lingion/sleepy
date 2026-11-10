package com.lingion.sleepy.ui.screen.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用户反馈 2026-09-09: 加课表单里「非常规节次」「非常规时间」两个开关平铺,
 * 用户误以为表单不能添加正常课程。契约:
 *  - 两个开关收进同一个折叠栏「非常规选项」(irregular_options_section), 默认折叠;
 *  - 编辑已启用任一非常规项的课程时折叠栏自动展开 (否则看不到已启用项);
 *  - 标准节次字段 (双头滑杆 IntRangeSlider) 留在折叠栏外 — 普通课程主路径不受折叠影响;
 *  - 展开交互沿用仓库既有模式 (ExpandMore chevron 旋转 + AnimatedVisibility, 参考
 *    EditTableScreen 节次时间表 / SettingsCards.SettingsCard);
 *  - 六 locale 文案齐全 (irregular_options_section / _sub)。
 *
 * 仓库无 Robolectric, 折叠默认值与摘要文案走 JVM 纯逻辑直测 (MeetingBlockDraft
 * 构造先例: BuildCourseEntityAliasTest); UI 结构走源码级守卫 (先例: TodayDateNavWiringTest)。
 */
class IrregularOptionsCollapseTest {

    // ---- A. 折叠栏默认展开状态: 由构造参数派生 ----

    @Test
    fun new_normal_block_collapsed_by_default() {
        val b = draft(isIrregularNode = false, isIrregularTime = false)
        assertFalse("新建普通课程卡: 折叠栏必须默认收起", b.irregularOptionsExpanded)
    }

    @Test
    fun editing_irregular_node_block_expands_by_default() {
        val b = draft(isIrregularNode = true, isIrregularTime = false)
        assertTrue("编辑已启用非常规节次的卡: 折叠栏必须自动展开", b.irregularOptionsExpanded)
    }

    @Test
    fun editing_irregular_time_block_expands_by_default() {
        val b = draft(isIrregularNode = false, isIrregularTime = true)
        assertTrue("编辑已启用非常规时间的卡: 折叠栏必须自动展开", b.irregularOptionsExpanded)
    }

    @Test
    fun editing_both_irregular_block_expands_by_default() {
        val b = draft(isIrregularNode = true, isIrregularTime = true)
        assertTrue("两项都启用时折叠栏必须自动展开", b.irregularOptionsExpanded)
    }

    private fun draft(isIrregularNode: Boolean, isIrregularTime: Boolean) = MeetingBlockDraft(
        id = 1,
        days = androidx.compose.runtime.mutableStateListOf(1),
        startNode = 1,
        step = 2,
        startTime = "08:00",
        endTime = "09:40",
        isIrregularNode = isIrregularNode,
        isIrregularTime = isIrregularTime
    )

    // ---- B. 折叠态摘要: 收起但有启用项时, 栏头露出启用了什么 ----

    @Test
    fun summary_blank_when_no_option_active() {
        assertEquals(
            "",
            irregularOptionsSummary(
                isIrregularNode = false, isIrregularTime = false,
                startTime = "08:00", endTime = "09:40",
                nodeSwitchLabel = "非常规节次", edgeNodeLabel = "第 0 节",
                timeSwitchLabel = "非常规时间"
            )
        )
    }

    @Test
    fun summary_lists_node_when_only_node_active() {
        assertEquals(
            "非常规节次 · 第 0 节",
            irregularOptionsSummary(
                isIrregularNode = true, isIrregularTime = false,
                startTime = "", endTime = "",
                nodeSwitchLabel = "非常规节次", edgeNodeLabel = "第 0 节",
                timeSwitchLabel = "非常规时间"
            )
        )
    }

    @Test
    fun summary_lists_time_when_only_time_active() {
        assertEquals(
            "非常规时间 · 19:00–20:40",
            irregularOptionsSummary(
                isIrregularNode = false, isIrregularTime = true,
                startTime = "19:00", endTime = "20:40",
                nodeSwitchLabel = "非常规节次", edgeNodeLabel = "第 0 节",
                timeSwitchLabel = "非常规时间"
            )
        )
    }

    @Test
    fun summary_lists_both_joined_with_slash() {
        assertEquals(
            "非常规节次 · 第 -1 节 / 非常规时间 · 19:00–20:40",
            irregularOptionsSummary(
                isIrregularNode = true, isIrregularTime = true,
                startTime = "19:00", endTime = "20:40",
                nodeSwitchLabel = "非常规节次", edgeNodeLabel = "第 -1 节",
                timeSwitchLabel = "非常规时间"
            )
        )
    }

    @Test
    fun summary_time_without_valid_range_shows_label_only() {
        // 跨午夜守卫会清空 endTime — 摘要降级为只显示开关名, 不拼半截区间
        assertEquals(
            "非常规时间",
            irregularOptionsSummary(
                isIrregularNode = false, isIrregularTime = true,
                startTime = "23:00", endTime = "",
                nodeSwitchLabel = "非常规节次", edgeNodeLabel = "第 0 节",
                timeSwitchLabel = "非常规时间"
            )
        )
    }

    // ---- C. 源码级守卫: AddCourseScreen 表单结构 ----

}
