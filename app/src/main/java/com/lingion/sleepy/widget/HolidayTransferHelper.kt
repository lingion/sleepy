package com.lingion.sleepy.widget

import android.content.Context
import com.lingion.sleepy.util.AppPrefs
import java.time.LocalDate

/** Shared per-table transfer lookup; content follows the source date, execution the target. */
object HolidayTransferHelper {
    /**
     * 某表某天实际上谁的课(取课语义, 2026-10-11 报障修复):
     * 补班日 → sourceDate 星期几; 放假日 → null(无课); 未映射=自然星期。
     * tableId 为 null(无表/兜底) → 自然星期。
     */
    fun courseDateForDate(context: Context, tableId: Long?, date: LocalDate): LocalDate? {
        if (tableId == null) return date
        return com.lingion.sleepy.util.HolidayRangeOps.HolidayTransferOps.courseDateForDate(
            date, AppPrefs.getHolidayTransfers(context, tableId)
        )
    }

    fun courseDayForDate(context: Context, tableId: Long?, date: LocalDate): Int? {
        if (tableId == null) return date.dayOfWeek.value
        val transfers = AppPrefs.getHolidayTransfers(context, tableId)
        return com.lingion.sleepy.util.HolidayRangeOps.HolidayTransferOps.courseDayForDate(date, transfers)
    }

    /**
     * 某表某天应"按星期几取课"(正向查遗留语义); 映射命中 → targetDate 的星期几, 未映射=自然星期。
     * tableId 为 null(无表/兜底) → 自然星期。
     *
     * 遗留列映射兼容方法；实际取课一律走 courseDateForDate，保留来源周次。
     */
    fun effectiveDayOfWeek(context: Context, tableId: Long?, date: LocalDate): Int {
        if (tableId == null) return date.dayOfWeek.value
        val transfers = AppPrefs.getHolidayTransfers(context, tableId)
        return com.lingion.sleepy.util.HolidayRangeOps.HolidayTransferOps.effectiveDayOfWeek(date, transfers)
    }
}
