package com.lingion.sleepy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 用户 2026-09-20 反馈：教务导入 → 选作息表 → 选日期 → 误报「第1节时间不能为空」
 * + 日期框标红。根因：v1.0.56 T6 作息表 Tab 选中既有表只记 ID 不水合 rows,
 * 但确认校验无条件打在手动 rows 上。本测试锁 effectiveRowsForConfirm 是
 * 确认流的真源 —— 校验/落库都从这里拿, 绑表 id>0 时手动 rows 不参与。
 */
class PeriodTableBindConfirmRowsTest {

    private fun feedbackScheduleJson(): String =
        TimeTableUtils.buildTimeJsonFromRows(
            listOf(
                1 to "08:10", 2 to "08:55", 3 to "09:40", 4 to "10:35",
                5 to "11:20", 6 to "14:50", 7 to "15:35", 8 to "16:20",
                9 to "17:05", 10 to "19:00", 11 to "19:45", 12 to "20:30",
                13 to "21:15"
            ).map { (n, s) ->
                val (sH, sM) = s.split(":").let { it[0].toInt() to it[1].toInt() }
                val eM = sM + 40
                val eH = sH + (if (eM >= 60) 1 else 0)
                val eMin = eM % 60
                TimeTableUtils.TimeSlotRow(n, s, String.format("%02d:%02d", eH, eMin))
            }
        )

    private val feedbackJson = feedbackScheduleJson()
    private val table42 = 42L to feedbackJson
    private val tables = listOf(table42)

    /** 教务协议没回节次时间 → 手动 rows 全空 → 绑了表 → 校验必须用表 timeJson */
    @Test
    fun bind_existing_table_hydrates_manual_blank_rows_with_table_times() {
        val manualBlank = (1..13).map { TimeTableUtils.TimeSlotRow(it, "", "") }
        val effective = TimeTableUtils.effectiveRowsForConfirm(manualBlank, bindId = 42L, tables = tables)
        assertEquals("绑表时 rows 必须来自该表, 与手动 rows 全空无关", 13, effective.size)
        assertEquals("第1节必须是表的时间 08:10, 不能再是空串(否则又误报)", "08:10", effective[0].start)
        assertEquals("08:50", effective[0].end)
        assertEquals("第13节时间必须到位", "21:15", effective[12].start)
        assertEquals("21:55", effective[12].end)
    }

    /** null (未绑) → 维持手动 rows 原值 (旧逻辑) */
    @Test
    fun bind_null_returns_manual_rows_unchanged() {
        val manual = listOf(TimeTableUtils.TimeSlotRow(1, "09:00", "09:45"))
        val effective = TimeTableUtils.effectiveRowsForConfirm(manual, bindId = null, tables = tables)
        assertSame("null = 走本表内置, manual rows 原样穿透", manual, effective)
    }

    /** -1 (本次导入自动建表) → 维持手动 rows (落库即建, 不引表) */
    @Test
    fun bind_minus_one_synthetic_uses_manual_rows() {
        val manual = listOf(TimeTableUtils.TimeSlotRow(1, "09:00", "09:45"))
        val effective = TimeTableUtils.effectiveRowsForConfirm(manual, bindId = -1L, tables = tables)
        assertSame("-1 = 自动建表, manual rows 落库即建", manual, effective)
    }

    /** id>0 但表列表里找不到 → 兜底用手动 rows, 不抛异常 */
    @Test
    fun bind_existing_table_not_in_list_falls_back_to_manual() {
        val manual = listOf(TimeTableUtils.TimeSlotRow(1, "09:00", "09:45"))
        val effective = TimeTableUtils.effectiveRowsForConfirm(manual, bindId = 999L, tables = tables)
        assertSame("找不到表 = 兜底 manual, 不抛", manual, effective)
    }
}