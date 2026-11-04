package com.lingion.sleepy.ui.screen.mine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * issue#44 调休映射设置页契约锁(源码扫描):
 * 设置页必须按课表读写映射、日期编辑位于映射之后的日期管理区、
 * 右格用 Material3 ExposedDropdownMenuBox(原地长高 + 浮层)、候选三项固定顺序、
 * 默认空无预填、无表时禁写哨兵 ID、MainActivity 必须把当前课表 ID 传进来。
 */
class HolidaySettingsContractTest {

    private fun src(rel: String): String = sequenceOf(
        System.getProperty("sleepy.test.root")?.let { java.io.File(it, "app/$rel") },
        java.io.File("app/$rel")
    ).filterNotNull().firstOrNull { it.isFile }?.readText() ?: error("Unable to load $rel")

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
    fun segment_editing_follows_mapping_in_date_management() {
        val mapping = screen.indexOf("HolidayTransferCard(")
        val management = screen.indexOf("settings_holiday_dates)")
        val editRows = screen.indexOf("HolidayDateManagementRow(")
        val add = screen.indexOf("holiday_add_entry")
        assertTrue("date editing belongs after mapping and before adding dates",
            mapping >= 0 && management > mapping && editRows > management && add > editRows)
        val transferCard = screen.substringAfter("private fun HolidayTransferCard(")
            .substringBefore("private fun HolidayOrphanCard(")
        assertFalse("mapping card must not edit shared dates", transferCard.contains("onEditSegment"))
        assertTrue("all active segments remain editable", screen.contains("merged.active.forEach"))
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
        val switcher = screen.substringAfter("if (activeTableId != null && vmState.tables.size > 1)")
            .substringBefore("// Loaded holiday data")
        assertTrue(
            switcher.contains("ExposedDropdownMenuBox(") &&
                switcher.contains("DropdownMenuItem(") &&
                switcher.contains("activeTableId = table.id")
        )
        assertFalse(switcher.contains("SegmentedSwitcher("))
        assertTrue(
            "table switcher must not drive viewModel.selectTable",
            !switcher.contains("viewModel.selectTable")
        )
    }

    @Test
    fun screen_blocks_mapping_without_a_table() {
        assertFalse(screen.contains("updateHolidayTransfer(context, 0"))
        assertTrue(screen.contains("holiday_makeup_no_table"))
    }

    @Test
    fun main_activity_passes_current_table_id() {
        // Navigation 3 迁移后: MainActivity 把当前课表 ID 作为 SleepyNavHost 参数下发,
        // NavHost 里 HolidaySettingsScreen(tableId = currentTableId) — 语义等价。
        assertTrue(
            Regex(
                """currentTableId\s*=\s*currentTableId"""
            ).containsMatchIn(main)
        )
        assertTrue(
            Regex(
                """mainVm\.state\.map\s*\{\s*it\.currentTable\?\.id\s*\}\.distinctUntilChanged\(\)"""
            ).containsMatchIn(main)
        )
        assertTrue(main.contains("collectAsState(initial = null)"))
        assertTrue(
            Regex(
                """HolidaySettingsScreen\([\s\S]{0,160}?tableId\s*=\s*currentTableId"""
            ).containsMatchIn(
                java.io.File(
                    System.getProperty("sleepy.test.root"), "app/src/main/java/com/lingion/sleepy/ui/nav/SleepyNavHost.kt"
                ).readText()
            )
        )
    }
}
