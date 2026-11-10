package com.lingion.sleepy.widget

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 步骤 2 扩展契约测试: WeekList/WeekView 逐列 FIXED 窗口 (设计 §4.3) +
 * WeekGrid 位图地板删除/色带降级接线 (设计 §4.4)。
 *
 * 口径: WeekList availH=hDp−58−statusH 行19(16+3) 页脚19;
 *       WeekView availH=hDp−56−statusH 行=行数×lineH+3 页脚=lineH+3;
 *       两族 HEAD-only (整周列表无时间锚点, 无 ALL_DONE)。
 */
class FixedWindowWeekTest {

    private fun course(id: Long) = CourseEntity(
        id = id, groupId = "g$id", tableId = 1L, courseName = "课$id",
        day = 1, startNode = 1, step = 1,
        startWeek = 1, endWeek = 20, color = "blue",
        ownTime = true, isIrregularTime = true, startTime = "08:00", endTime = "09:40"
    )

    private fun day(dow: Int, n: Int) = DayData(
        LocalDate.now(), dow, (1..n).map { course((dow * 100 + it).toLong()) }, ""
    )

    // ---- WeekList (availH = hDp − 58 − statusH, 行 19, 页脚 19) ----

    @Test
    fun `weeklist column truncates with plus-N footer`() {
        // hDp=110 → availH=52: 2 行 38 装下, 3 行 57 超; 页脚 best-effort 预算 33 → 1 行+页脚
        val wins = WeekListWidgetReceiver.computeWeekListWindows(
            listOf(day(1, 5)), 110f, 0f
        )
        val w = wins.single()
        assertEquals(1, w.visible.size)
        assertTrue(w.footer)
        assertEquals(4, w.hiddenAheadCourses)
    }

    @Test
    fun `weeklist column without overflow has no footer`() {
        // availH=52, 2 行 38 全装下 → 无页脚
        val w = WeekListWidgetReceiver.computeWeekListWindows(
            listOf(day(1, 2)), 110f, 0f
        ).single()
        assertEquals(2, w.visible.size)
        assertFalse(w.footer)
        assertEquals(0, w.hiddenAheadCourses)
    }

    @Test
    fun `weeklist footer drops when budget leaves zero rows`() {
        // statusH=16 → availH=36: 1 行 19 装下 2 行超; 页脚预算 17 < 19 → 丢页脚保 1 行
        val w = WeekListWidgetReceiver.computeWeekListWindows(
            listOf(day(1, 5)), 110f, 16f
        ).single()
        assertEquals(1, w.visible.size)
        assertFalse("0 行回退: 不得纯页脚", w.footer)
        assertEquals(4, w.hiddenAheadCourses)
    }

    @Test
    fun `weeklist is head-only never all-done`() {
        // HEAD-only: 时间全在过去也不得 ALL_DONE (整周列表无「今日已结束」语义)
        val w = WeekListWidgetReceiver.computeWeekListWindows(
            listOf(day(1, 2)), 110f, 0f
        ).single()
        assertEquals(FixedWindowCore.Status.NONE, w.status)
        assertEquals(101L, w.visible.first().row.courses.first().id)
    }

    @Test
    fun `weeklist columns are independent`() {
        // 周一 5 节截断, 周二 1 节全显 — 逐列预算互不串扰
        val wins = WeekListWidgetReceiver.computeWeekListWindows(
            listOf(day(1, 5), day(2, 1)), 110f, 0f
        )
        assertEquals(2, wins.size)
        assertTrue(wins[0].footer)
        assertFalse(wins[1].footer)
        assertEquals(1, wins[1].visible.size)
    }

    // ---- WeekView (availH = hDp − 56 − statusH, 行 = 行数×lineH+3, 页脚 = lineH+3) ----

    private val lineH = 12f // 单行高 15, 双行高 27, 页脚 15

    private fun viewWindows(days: List<DayData>, hDp: Float, statusH: Float, lines: Int = 1) =
        WeekViewWidgetReceiver.computeWeekViewWindows(days, hDp, statusH, lineH) {
            lines * lineH + 3f
        }

    @Test
    fun `weekview column truncates with plus-N footer`() {
        // hDp=110 → availH=54: 3 行 45 装下 4 行 60 超; 页脚预算 39 → 2 行+页脚
        val w = viewWindows(listOf(day(1, 5)), 110f, 0f).single()
        assertEquals(2, w.visible.size)
        assertTrue(w.footer)
        assertEquals(3, w.hiddenAheadCourses)
    }

    @Test
    fun `weekview measures wrapped two-line titles in budget`() {
        // 双行标题 (27/行): availH=54 → 2 行 54 恰好装下; 页脚预算 39 → 退到 1 行+页脚
        val w = viewWindows(listOf(day(1, 5)), 110f, 0f, lines = 2).single()
        assertEquals(1, w.visible.size)
        assertTrue(w.footer)
        assertEquals(4, w.hiddenAheadCourses)
    }

    @Test
    fun `weekview fits branch has no footer`() {
        val w = viewWindows(listOf(day(1, 3)), 110f, 0f).single()
        assertEquals(3, w.visible.size)
        assertFalse(w.footer)
    }

}
