package com.lingion.sleepy.widget

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import com.lingion.sleepy.util.AppClock

/**
 * 步骤 2 契约测试: FIXED 窗口 push 层接线 (设计 §4.1/§4.2)。
 *
 * 锁死两件事:
 * 1. computeTodayWindow / computeTwoDayWindows 的容量口径与渲染逐字节同源
 *    (Today availH=hDp−70 行36距7; TwoDay availH=hDp−44 行36距6 堆叠3);
 * 2. push 源码接线不漂移 (forceScroll 参数 / 静态分支放行窗口 / 页脚布局+PI)。
 */
class FixedWindowWiringTest {

    private fun course(
        id: Long,
        day: Int = 1,
        startNode: Int = 1,
        step: Int = 1,
        start: String,
        end: String
    ) = CourseEntity(
        id = id, groupId = "g$id", tableId = 1L, courseName = "课$id",
        day = day, startNode = startNode, step = step,
        startWeek = 1, endWeek = 20, color = "blue",
        ownTime = true, isIrregularTime = true, startTime = start, endTime = end
    )

    private fun h(m: String): Int {
        val p = m.split(":")
        return p[0].toInt() * 60 + p[1].toInt()
    }

    private fun dayCourses(n: Int, firstStartMin: Int = 8 * 60) = (1..n).map { i ->
        val s = firstStartMin + (i - 1) * 100
        val e = s + 100
        course(
            id = i.toLong(), startNode = (i - 1) * 2 + 1,
            start = "%02d:%02d".format(s / 60, s % 60),
            end = "%02d:%02d".format(e / 60, e % 60)
        )
    }

    private fun dataOf(courses: List<CourseEntity>) = WidgetData(
        date = LocalDate.now(AppClock), courses = courses, timeJson = "", hasTable = true
    )

    // ---- Today 系容量口径 (availH = hDp − 30 − 底部条28 = hDp − 58, 行 30 距 4) ----

    @Test
    fun `today S tier shows one row plus footer when overflowing`() {
        // hDp=110 → availH=52: 一行 30 装得下, 两行 64 装不下 → 1 行 + 胶囊 (填满档 footer=true)
        val w = TodayWidgetReceiver.computeTodayWindow(
            dataOf(dayCourses(5)), 110f, h("07:00")
        )
        assertEquals(1, w.visible.size)
        assertTrue(w.footer)
        assertEquals(4, w.hiddenAheadCourses)
        assertEquals(FixedWindowCore.Status.NONE, w.status)
    }

    @Test
    fun `today M tier fits three rows plus footer`() {
        // hDp=160 → availH=102: 三行 98 装得下, 四行 132 装不下 → 3 行 + 胶囊
        val w = TodayWidgetReceiver.computeTodayWindow(
            dataOf(dayCourses(5)), 160f, h("07:00")
        )
        assertEquals(3, w.visible.size)
        assertTrue(w.footer)
        assertEquals(2, w.hiddenAheadCourses)
    }

    @Test
    fun `today no footer when everything fits`() {
        val w = TodayWidgetReceiver.computeTodayWindow(
            dataOf(dayCourses(2)), 160f, h("07:00")
        )
        assertEquals(2, w.visible.size)
        assertFalse(w.footer)
        assertEquals(0, w.hiddenAheadCourses)
    }

    @Test
    fun `today all-done gate fires via compute even when fits`() {
        // 评审 #4: fits 少课也要 ALL_DONE — 公共闸与溢出无关
        val w = TodayWidgetReceiver.computeTodayWindow(
            dataOf(dayCourses(2)), 160f, h("23:00")
        )
        assertEquals(FixedWindowCore.Status.ALL_DONE, w.status)
        assertTrue(w.visible.isEmpty())
    }

    @Test
    fun `today past-day uses head mode without now`() {
        // 非今天 (导航翻页/预览) → HEAD: 从第一节排, 不锚当前时刻
        val w = TodayWidgetReceiver.computeTodayWindow(
            dataOf(dayCourses(5)), 110f, null
        )
        assertEquals(1, w.visible.size)
        assertEquals(1L, w.visible.first().row.courses.first().id)
        assertTrue(w.footer)
    }

    @Test
    fun `today anchors on in-progress class`() {
        // 5 节, 第 3 节进行中 → 窗口从第 3 节起
        val w = TodayWidgetReceiver.computeTodayWindow(
            dataOf(dayCourses(5)), 160f, h("11:50")
        )
        assertEquals(3L, w.visible.first().row.courses.first().id)
    }

    // ---- TwoDay 系容量口径 (availH = hDp − 44, 行 36 距 6) ----

    private fun twoDayData(todayN: Int, tomorrowN: Int) = TwoDayData(
        days = listOf(
            DayData(LocalDate.now(AppClock), 1, dayCourses(todayN), ""),
            DayData(LocalDate.now(AppClock).plusDays(1), 2, dayCourses(tomorrowN), "")
        ),
        hasTable = true
    )

    @Test
    fun `twoday per-column windows with merged footer budget`() {
        // hDp=160 → availH=116: 两行 78 装得下, 三行 120 装不下 → 每列 2 行 + 页脚
        // (2026-09-14c 密度上调: 删标签行 + 行 44→36 距 8→6 → 同屏多装一行)
        val wins = TwoDayWidgetReceiver.computeTwoDayWindows(
            twoDayData(4, 3), 160f, h("07:00")
        )
        assertEquals(2, wins.size)
        assertEquals(2, wins[0].visible.size)
        assertTrue(wins[0].footer)
        assertEquals(2, wins[0].hiddenAheadCourses)
        assertEquals(1, wins[1].hiddenAheadCourses)
    }

    @Test
    fun `twoday tomorrow column stays HEAD when today is all done`() {
        // 今天全下课 → 今天列 ALL_DONE; 明天列 HEAD 从第一节起 (不得被 ALL_DONE 传染)
        val wins = TwoDayWidgetReceiver.computeTwoDayWindows(
            twoDayData(2, 5), 160f, h("23:00")
        )
        assertEquals(FixedWindowCore.Status.ALL_DONE, wins[0].status)
        assertEquals(FixedWindowCore.Status.NONE, wins[1].status)
        assertEquals(1L, wins[1].visible.first().row.courses.first().id)
    }

    @Test
    fun `twoday empty column yields empty window not all-done`() {
        // 明天无课 → 空窗口 (渲染走「无课程」既有分支), 不得谎报已结束
        val wins = TwoDayWidgetReceiver.computeTwoDayWindows(
            twoDayData(2, 0), 160f, h("23:00")
        )
        assertTrue(wins[1].visible.isEmpty())
        assertEquals(FixedWindowCore.Status.NONE, wins[1].status)
        assertFalse(wins[1].footer)
    }

}
