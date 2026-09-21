package com.lingion.sleepy.widget

import android.content.Context
import com.lingion.sleepy.util.AppPrefs
import java.time.LocalDate

/**
 * issue#44 调休映射在 widget / 通知链路里的共用 helper(第二轮: 放假日→补班日)。
 * 关键: 共享同一份映射读取 + effectiveDayOfWeek 逻辑, 避免各 widget 各写各的判断。
 */
object HolidayTransferHelper {
    /**
     * 某表某天应"按星期几取课"; 映射命中 → targetDate 的星期几, 未映射=自然星期。
     * tableId 为 null(无表/兜底) → 自然星期。
     */
    fun effectiveDayOfWeek(context: Context, tableId: Long?, date: LocalDate): Int {
        if (tableId == null) return date.dayOfWeek.value
        val transfers = AppPrefs.getHolidayTransfers(context, tableId)
        return com.lingion.sleepy.util.HolidayRangeOps.HolidayTransferOps.effectiveDayOfWeek(date, transfers)
    }
}
