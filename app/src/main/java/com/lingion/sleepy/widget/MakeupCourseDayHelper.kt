package com.lingion.sleepy.widget

import android.content.Context
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.HolidayRangeOps
import java.time.LocalDate

/**
 * issue#44 调休映射在 widget / 通知链路里的共用 helper。
 * 关键: 共享同一份映射读取 + resolveCourseDay 逻辑, 避免各 widget 各写各的判断。
 */
object MakeupCourseDayHelper {
    /**
     * 某表某天应"按星期几取课"; 未映射=自然星期。
     * tableId 为 null(无表/兜底) → 自然星期。
     */
    fun effectiveDayOfWeek(context: Context, tableId: Long?, date: LocalDate): Int {
        if (tableId == null) return date.dayOfWeek.value
        val mappings = AppPrefs.getHolidayMakeupDays(context, tableId)
        return HolidayRangeOps.resolveCourseDay(date, mappings)
    }
}
