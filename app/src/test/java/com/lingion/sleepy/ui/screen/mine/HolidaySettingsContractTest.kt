package com.lingion.sleepy.ui.screen.mine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * issue#44 调休映射设置页契约锁(源码扫描):
 * 设置页必须按课表读写映射、选择器必须空默认(禁猜测预填)、
 * 未建表时禁写哨兵 ID、MainActivity 必须把当前课表 ID 传进来。
 */
class HolidaySettingsContractTest {

    private fun src(rel: String): String = sequenceOf(
        java.io.File("app/$rel"),
        java.io.File("/tmp/sleepy-makeup-wt/app/$rel"),
        java.io.File("/Users/lingion_k/sleepy/app/$rel")
    ).firstOrNull { it.isFile }?.readText() ?: error("Unable to load $rel")

    private val screen = src("src/main/java/com/lingion/sleepy/ui/screen/mine/HolidaySettingsScreen.kt")
    private val main = src("src/main/java/com/lingion/sleepy/MainActivity.kt")

    @Test
    fun screen_readsAndWrites_mappings_per_table() {
        assertTrue(screen.contains("getHolidayMakeupDays"))
        assertTrue(screen.contains("updateHolidayMakeupDay"))
        assertTrue(screen.contains("tableId"))
    }

    @Test
    fun screen_selector_defaults_to_unset_not_a_guess() {
        // 选择器必须带"未设置"选项; 禁预填任何猜测星期
        assertTrue(screen.contains("holiday_makeup_unset"))
        assertFalse(screen.contains("sourceDayOfWeek = 1)"))
    }

    @Test
    fun screen_blocks_mapping_without_a_table() {
        // 无表: 只读展示, 不写哨兵 tableId=0
        assertFalse(screen.contains("updateHolidayMakeupDay(context, 0"))
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
