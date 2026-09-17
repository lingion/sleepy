package com.lingion.sleepy.ui.screen.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * issue#44 调休映射取课契约锁: 所有消费路径必须用 resolver,
 * 不能直接用自然 dayOfWeek。
 */
class HolidayMakeupScheduleContractTest {

    private fun src(rel: String): String = sequenceOf(
        java.io.File("app/$rel"),
        java.io.File("/tmp/sleepy-makeup-wt/app/$rel"),
        java.io.File("/Users/lingion_k/sleepy/app/$rel")
    ).firstOrNull { it.isFile }?.readText() ?: error("Unable to load $rel")

    private val todayScreen = src("src/main/java/com/lingion/sleepy/ui/screen/today/TodayScreen.kt")
    private val scheduleVm = src("src/main/java/com/lingion/sleepy/ui/screen/schedule/ScheduleViewModel.kt")

    @Test
    fun today_uses_courseDayFor() {
        // 间接走 state.courseDayFor(today) — 锁定"不用 todayDayOfWeek"即可
        assertTrue(todayScreen.contains("courseDayFor"))
        assertTrue(!todayScreen.contains("DateUtils.todayDayOfWeek(today)"))
    }

    @Test
    fun schedule_vm_uses_resolveCourseDay() {
        assertTrue(scheduleVm.contains("resolveCourseDay"))
    }

    @Test
    fun makeups_are_loaded_per_tableId() {
        // 单一真源: VM 装载/刷新时调, 设置页保存后也调 VM.refreshMakeup
        assertTrue(scheduleVm.contains("getHolidayMakeupDays"))
    }
}
