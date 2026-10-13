package com.lingion.sleepy.ui.screen.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * issue#44 调休映射取课契约锁(源码扫描): 取课链路(今日页)必须用
 * courseDayFor(反向查: 补班日=sourceDate 课, 放假日=无课),
 * 课表页 daySwap 建图保留 effectiveDayOfWeek(正向查)。
 */
class HolidayTransferScheduleContractTest {

    private fun src(rel: String): String = sequenceOf(
        System.getProperty("sleepy.test.root")?.let { java.io.File(it, "app/$rel") },
        java.io.File("app/$rel")
    ).filterNotNull().firstOrNull { it.isFile }?.readText() ?: error("Unable to load $rel")

    private val todayScreen = src("src/main/java/com/lingion/sleepy/ui/screen/today/TodayScreen.kt")
    private val scheduleVm = src("src/main/java/com/lingion/sleepy/ui/screen/schedule/ScheduleViewModel.kt")
    private val scheduleScreen = src("src/main/java/com/lingion/sleepy/ui/screen/schedule/ScheduleScreen.kt")

    @Test
    fun today_uses_courseDayFor() {
        // 取课走 state.courseDayFor(today) 反向查 — 锁定"不用 todayDayOfWeek"即可
        assertTrue(todayScreen.contains("courseDateFor"))
        assertTrue(!todayScreen.contains("DateUtils.todayDayOfWeek(today)"))
    }

    @Test
    fun schedule_vm_uses_courseDayForDate() {
        // 取课 resolver 必须走反向查 courseDayForDate(2026-10-11 报障修复)
        assertTrue(scheduleVm.contains("HolidayTransferOps.courseDayForDate"))
    }

    @Test
    fun schedule_vm_keeps_forward_lookup_for_daySwap() {
        // 课表页 daySwap 建图仍走正向查 effectiveDayOfWeek — 两个 resolver 并存勿混用
        assertTrue(scheduleVm.contains("HolidayTransferOps.effectiveDayOfWeek"))
    }

    @Test
    fun transfers_are_loaded_per_tableId() {
        // 单一真源: VM 装载/刷新时调 getHolidayTransfers; 设置页保存后调 VM.refreshTransfer
        assertTrue(scheduleVm.contains("getHolidayTransfers"))
        assertTrue(scheduleVm.contains("refreshTransfer"))
    }

    // ===== 行为锁(2026-10-11 报障): 周三(10-07)放假调休到周日(10-11) =====
    // ===== → 补班日周日取周三的课; 放假日周三无课               =====

    @Test
    fun makeup_sunday_takes_source_wednesday_courses() {
        val wednesday = LocalDate.of(2026, 10, 7)  // 放假日(周三)
        val sunday = LocalDate.of(2026, 10, 11)    // 补班日(周日)
        val transfers = listOf(
            com.lingion.sleepy.util.HolidayTransferEntry(wednesday, sunday, "seg")
        )
        // 补班日 → 上周三(3)的课
        assertEquals(3, com.lingion.sleepy.util.HolidayRangeOps.HolidayTransferOps.courseDayForDate(sunday, transfers))
        // 放假日 → 无课(null)
        assertEquals(null, com.lingion.sleepy.util.HolidayRangeOps.HolidayTransferOps.courseDayForDate(wednesday, transfers))
        // 未映射的下一个周日按自然星期
        assertEquals(7, com.lingion.sleepy.util.HolidayRangeOps.HolidayTransferOps.courseDayForDate(sunday.plusDays(7), transfers))
    }

    @Test
    fun schedule_screen_rewrites_render_day_from_mapping() {
        // 网格渲染层必须存在"调休改写 day"逻辑(渲染期替身, 不写库)
        assertTrue(scheduleScreen.contains("issue#44 调休改写"))
        assertTrue(scheduleScreen.contains("renderCourses"))
        assertTrue(scheduleScreen.contains("HolidayTransferOps.coursesForDate"))
    }
}
