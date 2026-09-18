package com.lingion.sleepy.ui.screen.mine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * issue#44 调休映射设置页契约锁(源码扫描):
 * 设置页必须按课表读写映射、下拉候选 = 全量官方补班日(扁平不分组)+无+其他日期、
 * 默认灰格不预填猜测、无表时禁写哨兵 ID、MainActivity 必须把当前课表 ID 传进来。
 */
class HolidaySettingsContractTest {

    private fun src(rel: String): String = sequenceOf(
        java.io.File("app/$rel"),
        java.io.File("/tmp/sleepy-makeup-wt/app/$rel"),
        java.io.File("/Users/lingion_k/sleepy/app/$rel")
    ).firstOrNull { it.isFile }?.readText() ?: error("Unable to load $rel")

    private val screen = src("src/main/java/com/lingion/sleepy/ui/screen/mine/HolidaySettingsScreen.kt")
    private val main = src("src/main/java/com/lingion/sleepy/MainActivity.kt")
    private val prefs = src("src/main/java/com/lingion/sleepy/util/AppPrefs.kt")

    @Test
    fun screen_readsAndWrites_mappings_per_table() {
        assertTrue(screen.contains("getHolidayTransfers"))
        assertTrue(screen.contains("updateHolidayTransfer"))
        assertTrue(screen.contains("tableId"))
    }

    @Test
    fun dropdown_lists_all_workdays_then_none_then_other() {
        // 展开列表三项固定顺序: 官方补班日全量 → 无 → 其他日期; 不分组/不禁用
        val workdaysIdx = screen.indexOf("workdayDates.forEach")
        val noneIdx = screen.indexOf("ExpandOptionRow(text = noMappingLabel")
        val otherIdx = screen.indexOf("ExpandOptionRow(text = pickOtherLabel")
        assertTrue("must contain 补班日候选循环", workdaysIdx > 0)
        assertTrue("must contain 无 (noMappingLabel)", noneIdx > 0)
        assertTrue("must contain 其他日期 (pickOtherLabel)", otherIdx > 0)
        assertTrue(
            "official workdays must be listed above 无/其他日期",
            workdaysIdx < noneIdx && workdaysIdx < otherIdx
        )
    }

    @Test
    fun workday_candidates_never_filtered_or_disabled() {
        // 用户原话: 有多少补班日就列多少, 不猜不筛选不禁用
        assertFalse(screen.contains("enabled = false"))
    }

    @Test
    fun target_cell_opens_raised_popup_same_width() {
        // 用户 2026-09-18 二次定稿: 点右格 → 弹出"同一个矩形变高"的浮层
        // (宽度不变、左上角与格子重合、抬高一个图层 = Popup), 禁同图层 inline 撑开,
        // 禁 DropdownMenu 别处弹菜单
        assertTrue(
            "must open a raised-layer Popup (抬高一个图层)",
            screen.contains("Popup(")
        )
        assertTrue(
            "popup must anchor top-left to the cell (视觉=格子自己变高)",
            screen.contains("CellGrowPopupProvider")
        )
        assertFalse(
            "inline AnimatedVisibility expansion is banned",
            screen.contains("AnimatedVisibility(")
        )
        assertFalse(
            "DropdownMenu popup is banned",
            screen.contains("DropdownMenu")
        )
    }

    @Test
    fun mutual_exclusion_enforced_in_prefs_not_ui() {
        // 目标日互斥在落盘层(withTargetExclusivity)落实, UI 不禁用选项
        assertTrue(prefs.contains("withTargetExclusivity"))
    }

    @Test
    fun orphan_entries_rendered_with_hint_not_deleted() {
        // 孤儿映射 = sourceDate 不在放假日集合; 灰卡+提示+手动清除, 禁自动删
        assertTrue(screen.contains("HolidayOrphanCard"))
        assertTrue(screen.contains("holiday_transfer_orphan_title"))
    }

    @Test
    fun table_switcher_bound_to_activeTableId_not_selectTable() {
        // 卡内表切换只改"正在编辑哪张表"(activeTableId), 不动全局选中课表;
        // 唯一切换器的 onSelect 必须绑 activeTableId
        assertTrue(
            Regex("""SegmentedSwitcher\([\s\S]{0,300}?onSelect = \{ id -> activeTableId = id \}""").containsMatchIn(screen)
        )
        assertTrue(
            "table switcher must not drive viewModel.selectTable",
            !Regex("""SegmentedSwitcher\([\s\S]{0,300}?viewModel\.selectTable""").containsMatchIn(screen)
        )
    }

    @Test
    fun screen_blocks_mapping_without_a_table() {
        // 无表: 只读展示, 不写哨兵 tableId=0
        assertFalse(screen.contains("updateHolidayTransfer(context, 0"))
        assertTrue(screen.contains("holiday_makeup_no_table"))
    }

    @Test
    fun main_activity_passes_current_table_id() {
        assertTrue(
            Regex(
                """HolidaySettingsScreen\([\s\S]{0,160}?tableId\s*=\s*mainVm\.state\.value\.currentTable\?\.id"""
            ).containsMatchIn(main)
        )
    }
}
