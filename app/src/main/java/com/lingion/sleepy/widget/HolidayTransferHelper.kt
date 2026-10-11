package com.lingion.sleepy.widget

import android.content.Context
import com.lingion.sleepy.util.AppPrefs
import java.time.LocalDate

/**
 * issue#44 调休映射在 widget / 通知链路里的共用 helper(第二轮: 放假日→补班日)。
 * 关键: 共享同一份映射读取 + 解析逻辑, 避免各 widget 各写各的判断。
 *
 * 两个 resolver 并存, 方向不同勿混用:
 * - courseDayForDate(反向查): 取课链路用 — 补班日=上 sourceDate 的课, 放假日=无课。
 * - effectiveDayOfWeek(正向查): 课表页 daySwap 专用 — 回答"这天的课排进哪列"。
 */
object HolidayTransferHelper {
    /**
     * 某表某天实际上谁的课(取课语义, 2026-10-11 报障修复):
     * 补班日 → sourceDate 星期几; 放假日 → null(无课); 未映射=自然星期。
     * tableId 为 null(无表/兜底) → 自然星期。
     */
    fun courseDayForDate(context: Context, tableId: Long?, date: LocalDate): Int? {
        if (tableId == null) return date.dayOfWeek.value
        val transfers = AppPrefs.getHolidayTransfers(context, tableId)
        return com.lingion.sleepy.util.HolidayRangeOps.HolidayTransferOps.courseDayForDate(date, transfers)
    }

    /**
     * 某表某天应"按星期几取课"(正向查遗留语义); 映射命中 → targetDate 的星期几, 未映射=自然星期。
     * tableId 为 null(无表/兜底) → 自然星期。
     *
     * 仅课表页 daySwap 建图用; 取课(widget/通知/勿扰/今日页)一律走 courseDayForDate。
     */
    fun effectiveDayOfWeek(context: Context, tableId: Long?, date: LocalDate): Int {
        if (tableId == null) return date.dayOfWeek.value
        val transfers = AppPrefs.getHolidayTransfers(context, tableId)
        return com.lingion.sleepy.util.HolidayRangeOps.HolidayTransferOps.effectiveDayOfWeek(date, transfers)
    }
}
