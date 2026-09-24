package com.lingion.sleepy.ui.screen.schedule

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2026-09-23 用户令: 首页表头只显示周次 — "表头就是表头, 只能显示第几周, 不应该显示其他东西"。
 *
 * 契约:
 *  A. ScheduleScreen 表头文案永远走周次渲染(schedule_current_week / schedule_week_prefix),
 *     不允许引用 schedule_nearest_busy_day / WeekDisplayStatus / statusForSelectedWeek —
 *     "最近有课日"功能可以影响自动选中的周, 但不能改写表头语义。
 *  B. 小组件(WidgetBitmapRenderers/TodayWidget)不受此约束 — 那边有日期语境, 契约另测。
 *
 * 仓库无 Robolectric — 与其他契约测试同风格, 源文件 token 扫描。
 */
class ScheduleHeaderWeekOnlyContractTest {

    private fun findUpward(rel: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, rel)
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("$rel not found")
    }

    private val scheduleScreen: String by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/schedule/ScheduleScreen.kt").readText()
    }

    @Test
    fun `header never renders nearest busy day text`() {
        assertTrue(
            "ScheduleScreen 禁止引用 schedule_nearest_busy_day — 表头只能显示周次",
            !scheduleScreen.contains("schedule_nearest_busy_day")
        )
    }

    @Test
    fun `header text does not branch on week display status`() {
        assertTrue(
            "ScheduleScreen 禁止引用 WeekDisplayStatus/statusForSelectedWeek — " +
                "最近有课日只能影响自动选周, 不能改写表头文案",
            !scheduleScreen.contains("WeekDisplayStatus") &&
                !scheduleScreen.contains("statusForSelectedWeek")
        )
    }

    @Test
    fun `header always renders week number via schedule_current_week`() {
        assertTrue(
            "表头 Text 必须无条件用 schedule_current_week 渲染周次",
            scheduleScreen.contains("stringResource(R.string.schedule_current_week, currentWeek)")
        )
    }
}
