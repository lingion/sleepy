package com.lingion.sleepy.ui.screen.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * issue#44 调休映射取课契约锁(源码扫描): 所有消费路径必须用
 * HolidayTransferOps.effectiveDayOfWeek(渲染期替身, 不写库),
 * 不能直接用自然 dayOfWeek。
 */
class HolidayTransferScheduleContractTest {

    private fun src(rel: String): String = sequenceOf(
        java.io.File("app/$rel"),
        java.io.File("/tmp/sleepy-makeup-wt/app/$rel"),
        java.io.File("/Users/lingion_k/sleepy/app/$rel")
    ).firstOrNull { it.isFile }?.readText() ?: error("Unable to load $rel")

    private val todayScreen = src("src/main/java/com/lingion/sleepy/ui/screen/today/TodayScreen.kt")
    private val scheduleVm = src("src/main/java/com/lingion/sleepy/ui/screen/schedule/ScheduleViewModel.kt")
    private val scheduleScreen = src("src/main/java/com/lingion/sleepy/ui/screen/schedule/ScheduleScreen.kt")

    @Test
    fun today_uses_transferDayFor() {
        // 间接走 state.transferDayFor(today) — 锁定"不用 todayDayOfWeek"即可
        assertTrue(todayScreen.contains("transferDayFor"))
        assertTrue(!todayScreen.contains("DateUtils.todayDayOfWeek(today)"))
    }

    @Test
    fun schedule_vm_uses_effectiveDayOfWeek() {
        assertTrue(scheduleVm.contains("HolidayTransferOps.effectiveDayOfWeek"))
    }

    @Test
    fun transfers_are_loaded_per_tableId() {
        // 单一真源: VM 装载/刷新时调 getHolidayTransfers; 设置页保存后调 VM.refreshTransfer
        assertTrue(scheduleVm.contains("getHolidayTransfers"))
        assertTrue(scheduleVm.contains("refreshTransfer"))
    }

    // ===== 行为锁: 周日(2026-10-11)映射到周四(2026-10-08) → 周日取周四的课 =====

    @Test
    fun sunday_mapped_to_thursday_takes_thursday_courses() {
        val sunday = LocalDate.of(2026, 10, 11)   // 2026-10-11 是周日
        val thursday = LocalDate.of(2026, 10, 8)  // 周四
        val transfers = listOf(
            com.lingion.sleepy.util.HolidayTransferEntry(sunday, thursday, "seg")
        )
        assertEquals(4, com.lingion.sleepy.util.HolidayRangeOps.HolidayTransferOps.effectiveDayOfWeek(sunday, transfers))
        // 未映射的下一个周日按自然星期
        assertEquals(7, com.lingion.sleepy.util.HolidayRangeOps.HolidayTransferOps.effectiveDayOfWeek(sunday.plusDays(7), transfers))
    }

    @Test
    fun schedule_screen_rewrites_render_day_from_mapping() {
        // 网格渲染层必须存在"调休改写 day"逻辑(渲染期替身, 不写库)
        assertTrue(scheduleScreen.contains("issue#44 调休改写"))
        assertTrue(scheduleScreen.contains("renderCourses"))
        assertTrue(scheduleScreen.contains("daySwap"))
    }
}
