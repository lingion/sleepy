package com.lingion.sleepy

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 提醒设置页示例的课表本地化契约。
 *
 * 示例是设置页的说明文案，不应改变通知调度；这里只锁定它与真实课表
 * 使用同一套当前课表、学期、周次和日期筛选口径，并保留无课表时的旧示例。
 */
class ReminderPreviewContractTest {

    private val source: String by lazy {
        sequenceOf(
            File("app/src/main/java/com/lingion/sleepy/ui/screen/mine/ReminderScreen.kt"),
            File("src/main/java/com/lingion/sleepy/ui/screen/mine/ReminderScreen.kt")
        ).first { it.isFile }.readText()
    }

    @Test
    fun `preview loads current schedule and keeps generic fallback`() {
        assertTrue(source.contains("WidgetTableResolver.resolveCurrentTable()"))
        assertTrue(source.contains("SleepyApp.get().repository.getCourses(table.id)"))
        assertTrue(source.contains("schedulePreview = loadReminderSchedulePreview()"))
        assertTrue(source.contains("?: stringResource(R.string.reminder_daily_preview)"))
        assertTrue(source.contains("?: stringResource(R.string.reminder_tomorrow_preview)"))
        assertTrue(source.contains("?: stringResource(R.string.reminder_before_class_preview)"))
    }

    @Test
    fun `preview filters courses using the same date and week semantics as notifications`() {
        assertTrue(source.contains("DateUtils.semesterStatus(table.startDate, table.maxWeek, date)"))
        assertTrue(source.contains("DateUtils.currentWeek(table.startDate, date)"))
        assertTrue(source.contains("it.day == DateUtils.todayDayOfWeek(date) && it.inWeek(week)"))
        assertTrue(source.contains("today.plusDays(1)"))
        assertTrue(source.contains("LocalTime.now()"))
    }

    @Test
    fun `preview localizes actual dates and course details`() {
        listOf(
            "reminder_preview_today_date",
            "reminder_preview_tomorrow_date",
            "reminder_preview_date",
            "reminder_daily_preview_dynamic",
            "reminder_before_class_preview_dynamic"
        ).forEach { key ->
            assertTrue("ReminderScreen must use $key", source.contains("R.string.$key"))
        }
        assertTrue(source.contains("first.course.courseName"))
        assertTrue(source.contains("first.course.room"))
        assertTrue(source.contains("next.course.teacher"))
    }
}
