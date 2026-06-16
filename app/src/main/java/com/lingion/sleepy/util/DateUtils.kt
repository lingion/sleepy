package com.lingion.sleepy.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * 日期/周次工具 — 完全不依赖 Android Context，单元测试方便。
 */
object DateUtils {

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val isoWeekFields = WeekFields.of(DayOfWeek.MONDAY, 1)

    /** 计算当前是学期第几周（1-based），若开学前返回负值 */
    fun currentWeek(startDate: String, today: LocalDate = LocalDate.now()): Int {
        return try {
            val start = LocalDate.parse(startDate, dateFormat)
            val days = ChronoUnit.DAYS.between(start, today)
            (days / 7).toInt() + 1
        } catch (e: Exception) {
            1
        }
    }

    /** 今天是星期几（1=周一, 7=周日） */
    fun todayDayOfWeek(today: LocalDate = LocalDate.now()): Int {
        // Java DayOfWeek.MONDAY = 1 ... SUNDAY = 7
        return today.dayOfWeek.value
    }

    /** 从周数和星期几得到具体日期 */
    fun dateOfWeek(startDate: String, week: Int, dayOfWeek: Int): LocalDate {
        val start = LocalDate.parse(startDate, dateFormat)
        return start.plusWeeks((week - 1).toLong()).plusDays((dayOfWeek - 1).toLong())
    }

    /** ISO 周编号 */
    fun isoWeekNumber(date: LocalDate): Int = date.get(isoWeekFields.weekOfWeekBasedYear())

    /** 短日期 (MM-dd) */
    fun shortDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("MM-dd"))

    /** 完整日期 (yyyy-MM-dd) */
    fun fullDate(date: LocalDate): String = date.format(dateFormat)

    /** 中文星期 */
    fun chineseDay(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        7 -> "周日"
        else -> ""
    }
}