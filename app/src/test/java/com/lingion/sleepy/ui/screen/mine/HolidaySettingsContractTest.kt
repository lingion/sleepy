package com.lingion.sleepy.ui.screen.mine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * issue#44 调休映射设置页契约锁(源码扫描):
 * 设置页必须按课表读写映射、每个放假日卡 = 行 + 最右编辑按钮、
 * 右格用 Material3 ExposedDropdownMenuBox(原地长高 + 浮层)、候选三项固定顺序、
 * 默认空无预填、无表时禁写哨兵 ID、MainActivity 必须把当前课表 ID 传进来。
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
    fun dropdown_uses_material3_exposed_dropdown() {
        // 用户 2026-09-18 三次定稿: 用 Material3 ExposedDropdownMenuBox —
        // TextField 本体 = 右格(原地变高, 浮层, 第一行为空),
        // 候选 = 补班日全量→无→其他日期
        assertTrue(
            "right cell must use Material3 ExposedDropdownMenuBox",
            screen.contains("ExposedDropdownMenuBox")
        )
        assertTrue(
            "TextField anchor inside the cell",
            screen.contains("menuAnchor")
        )
    }

    @Test
    fun dropdown_menu_has_no_blank_placeholder_row() {
        // 用户原话 2026-09-18 五次定稿: 第 0 行 = 要展开的日期行(锚字段本身), 不在菜单里;
        // 菜单内不得再塞空白占位行 — 候选项直接从第 1 行开始
        assertFalse(
            "menu must NOT contain a blank placeholder item (Text(\"\"))",
            Regex("""Text\(""\)""").containsMatchIn(screen)
        )
    }

    @Test
    fun menu_container_follows_cell_color_and_corner() {
        // 用户原话: 展开整个菜单都是高亮色, 跟随母容器圆角矩形弧度
        assertTrue("menu container must use the cell color",
            screen.contains("containerColor = targetColor"))
        assertTrue("menu corner must match the cell shape",
            screen.contains("shape = SleepyTheme.shapes.medium"))
    }

    @Test
    fun dropdown_lists_all_workdays_then_none_then_other() {
        // 展开列表固定顺序: 官方补班日全量 → 无 → 其他日期; 不分组/不禁用
        val workdaysIdx = screen.indexOf("workdayDates.forEach")
        val noneIdx = screen.indexOf("noMappingLabel)")
        val otherIdx = screen.indexOf("pickOtherLabel)")
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
    fun no_in_place_inline_expansion_or_custom_popup() {
        // ExposedDropdownMenuBox 已经做"原地长高 + 浮层", 不要再叠 inline 撑开或自造 Popup
        assertFalse(
            "in-place AnimatedVisibility is banned (ExposedDropdownMenuBox already handles it)",
            screen.contains("AnimatedVisibility(")
        )
        assertFalse(
            "custom Popup layer is banned (ExposedDropdownMenuBox is the canonical control)",
            screen.contains("androidx.compose.ui.window.Popup(")
        )
    }

    @Test
    fun each_segment_row_has_edit_button() {
        // 用户 2026-09-18 四次定稿: 每个放假日行(每个段)最右加一个编辑按钮
        // (编辑段本身起止/名称); 自定义补班日去最下面单独添加按钮
        assertTrue(
            "HolidayTransferCard must expose an edit affordance on the segment row",
            screen.contains("HolidayTransferCard") &&
                screen.contains("onEditSegment")
        )
    }

    @Test
    fun add_button_at_bottom_for_custom_segments() {
        // 用户原话: 想要添加自定义补班日, 就去最下面那个按钮去添加
        assertTrue(
            "FilledTonalButton at the bottom of the page for adding custom segments",
            screen.contains("holiday_add_entry")
        )
    }

    @Test
    fun orphan_entries_rendered_with_hint_not_deleted() {
        // 孤儿映射 = sourceDate 不在放假日集合; 灰卡+提示+手动清除, 禁自动删
        assertTrue(screen.contains("HolidayOrphanCard"))
        assertTrue(screen.contains("holiday_transfer_orphan_title"))
    }

    @Test
    fun mutual_exclusion_enforced_in_prefs_not_ui() {
        assertTrue(prefs.contains("withTargetExclusivity"))
    }

    @Test
    fun table_switcher_bound_to_activeTableId_not_selectTable() {
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
