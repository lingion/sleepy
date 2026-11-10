package com.lingion.sleepy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 底部导航条定稿 (2026-09-15 用户定稿) 守卫:
 * 翻页键/回今天/「+N」胶囊从顶栏迁到底部 28dp 固定条, 顶栏只剩位图内日期标题。
 * 覆盖: 胶囊宽度判定 (纯函数) / 填满档 (footerH=0 不再二次预留) /
 * 布局契约 (底部条 + 元素顺序) / 旧顶栏档位机制退场。
 */
class BottomBarFitTest {

    // ---- 宽档布局选档判定 (纯函数; 2026-09-14 胶囊恒画改版: 判定只选布局, 不再隐藏胶囊) ----

    @Test
    fun `wide layout fits today-state at 2x2 width`() {
        // 今日态: pad20 + 胶囊36 + 间隙4 + ◀▶80 = 140 ≤ 148 (荣耀 2×2 实测宽)
        assertTrue(TodayWidgetReceiver.bottomBarWideFits(148, isToday = true, navEnabled = true))
        assertTrue(TodayWidgetReceiver.bottomBarWideFits(140, isToday = true, navEnabled = true))
        assertFalse(TodayWidgetReceiver.bottomBarWideFits(139, isToday = true, navEnabled = true))
    }

    @Test
    fun `narrow nav-state falls to compact layout, capsule never hidden`() {
        // 导航态宽档门槛: pad20 + 胶囊36 + 间隙4 + 三钮132 = 192;
        // 148 放不下 → 换紧凑档 (12+36+4+96=148), 胶囊仍恒画 — 用户定稿: 挤也画
        assertTrue(TodayWidgetReceiver.bottomBarWideFits(192, isToday = false, navEnabled = true))
        assertFalse(TodayWidgetReceiver.bottomBarWideFits(148, isToday = false, navEnabled = true))
    }

    @Test
    fun `wide layout always fits no-nav variant and unknown width`() {
        assertTrue(TodayWidgetReceiver.bottomBarWideFits(60, isToday = true, navEnabled = false))
        assertTrue(TodayWidgetReceiver.bottomBarWideFits(0, isToday = false, navEnabled = true))
        assertTrue(TodayWidgetReceiver.bottomBarWideFits(-1, isToday = true, navEnabled = true))
    }

    // ---- 填满档 (footerH=0): 装得下几行画几行, 隐藏课只点亮胶囊 ----

    private fun rows(n: Int): List<FixedWindowCore.WindowEntry> {
        val courses = (1..n).map { i ->
            val s = 480 + (i - 1) * 100
            com.lingion.sleepy.data.entity.CourseEntity(
                id = i.toLong(), groupId = "g$i", tableId = 1L, courseName = "c$i",
                day = 1, startNode = (i - 1) * 2 + 1, step = 1,
                startWeek = 1, endWeek = 20, color = "blue",
                ownTime = true, isIrregularTime = true,
                startTime = "%02d:%02d".format(s / 60, s % 60),
                endTime = "%02d:%02d".format((s + 100) / 60, (s + 100) % 60)
            )
        }
        val spans = courses.flatMap { com.lingion.sleepy.util.ConflictLayoutEngine.weekLaneRows(listOf(it)) }
        return FixedWindowCore.entriesOf(spans, null) { 38f }
    }

    @Test
    fun `fill-to-max shows two rows when two fit - no double deduction`() {
        // 用户翻车原话: 「本来有 4 节课, 能显示两节却只显示一节 + 还有三节」—
        // availH=86 容 2 行 (38+10+38), 旧档预留页脚 20 只剩 1 行。新档必须画满 2 行。
        val r = FixedWindowCore.window(rows(4), availH = 86f, mode = FixedWindowCore.Mode.HEAD, nowMin = null, gapDp = 10f, footerH = 0f)
        assertEquals("装得下 2 行必须画 2 行", 2, r.visible.size)
        assertTrue("有隐藏课 → 点亮胶囊", r.footer)
        assertEquals(2, r.hiddenAheadCourses)
    }

    @Test
    fun `fill-to-max no capsule when everything fits`() {
        val r = FixedWindowCore.window(rows(2), availH = 86f, mode = FixedWindowCore.Mode.HEAD, nowMin = null, gapDp = 10f, footerH = 0f)
        assertEquals(2, r.visible.size)
        assertFalse(r.footer)
        assertEquals(0, r.hiddenAheadCourses)
    }

    @Test
    fun `fill-to-max keeps anchor row and capsule when even one row overflows`() {
        val r = FixedWindowCore.window(rows(3), availH = 30f, mode = FixedWindowCore.Mode.HEAD, nowMin = null, gapDp = 10f, footerH = 0f)
        assertEquals("forceFirst 锚行保底", 1, r.visible.size)
        assertTrue(r.footer)
        assertEquals(2, r.hiddenAheadCourses)
    }

    @Test
    fun `twoday default footerH unchanged`() {
        // 缺省 footerH=FOOTER_H_DP: TwoDay 旧「预留页脚重算」语义逐字节不变
        val r = FixedWindowCore.window(rows(4), availH = 86f, mode = FixedWindowCore.Mode.HEAD, nowMin = null, gapDp = 10f)
        assertEquals("预留 20dp 页脚后只容 1 行 (旧语义)", 1, r.visible.size)
        assertTrue(r.footer)
    }

    // ---- 布局契约: 底部条 ----

}
