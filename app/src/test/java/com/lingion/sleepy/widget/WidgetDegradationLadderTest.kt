package com.lingion.sleepy.widget

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 步骤 7 契约测试: 降级阶梯全尺寸同构 (设计 §4.4 / §11 步骤7)。
 *
 * 三档尺寸契约 (40×40 resize 最小地板 · 70×80 中档 · 600×600 大档):
 * - WeekGrid 位图: 几何纯函数, 40/80 高 → 色带档 (非空白), 600 → 常规文字档 (时间列可见);
 * - 四族 FIXED 窗口: 负 availH → forceFirst 保锚行不崩、无纯页脚;
 *   小正 availH → best-effort (保行优先于页脚); 大 availH → 全显无页脚。
 * JVM-safe: 只测纯函数; 位图绘制管线由编译 + 源码审查兜底 (同 WidgetVariantRenderTest 口径)。
 */
class WidgetDegradationLadderTest {

    private fun course(id: Long) = CourseEntity(
        id = id, groupId = "g$id", tableId = 1L, courseName = "课$id",
        day = 1, startNode = (((id - 1) % 10) * 2 + 1).toInt(), step = 1,
        startWeek = 1, endWeek = 20, color = "blue",
        ownTime = true, isIrregularTime = true,
        // 节点+时间按 id 错峰, 防 ConflictLayoutEngine 同节次并行为同一 lane 行
        startTime = "%02d:%02d".format((480 + ((id - 1) % 10) * 100) / 60, (480 + ((id - 1) % 10) * 100) % 60),
        endTime = "%02d:%02d".format((580 + ((id - 1) % 10) * 100) / 60, (580 + ((id - 1) % 10) * 100) % 60)
    )

    private fun day(dow: Int, n: Int) = DayData(
        LocalDate.now(), dow, (1..n).map { course((dow * 100 + it).toLong()) }, ""
    )

    private fun h(mm: String): Int =
        mm.substringBefore(':').toInt() * 60 + mm.substringAfter(':').toInt()

    // ---- WeekGrid 位图几何 (px 口径, 密度无关的档位断言) ----

    @Test
    fun `weekgrid room label stays inside reserve band on short cards`() {
        val (size, baseline) = WeekGridWidgetProvider.weekGridRoomLabelLayout(
            cardBottom = 30f,
            unifiedPad = 4f,
            roomReserveH = 2f,
            requestedSize = 5f
        )
        assertTrue("room label size must fit reserve band", size <= 2f / 1.1f)
        assertTrue("room baseline must remain below name area", baseline >= 30f - 4f - size)
    }

    @Test
    fun `weekgrid room label keeps requested size when reserve is sufficient`() {
        val (size, baseline) = WeekGridWidgetProvider.weekGridRoomLabelLayout(
            cardBottom = 100f,
            unifiedPad = 4f,
            roomReserveH = 12f,
            requestedSize = 6f
        )
        assertEquals(6f, size, 0f)
        assertEquals(100f - 4f - 6f * 0.3f, baseline, 0.001f)
    }

    @Test
    fun `weekgrid 40dp height falls to color band tier`() {
        for (density in floatArrayOf(2f, 3f)) {
            for (maxNode in listOf(8, 12)) {
                val (bodyH, slotH) = WeekGridWidgetProvider.weekGridBodyGeomPx(
                    (40 * density).toInt(), density, maxNode
                )
                assertTrue("bodyH 地板 20dp", bodyH >= (20 * density).toInt())
                assertTrue("slotH 地板 3dp", slotH >= 3f * density)
                assertTrue("40dp 高必色带 (d=$density n=$maxNode)", WeekGridWidgetProvider.weekGridColorBand(slotH, density))
            }
        }
    }

    @Test
    fun `weekgrid 80dp height still color band tier`() {
        for (density in floatArrayOf(2f, 3f)) {
            val (_, slotH) = WeekGridWidgetProvider.weekGridBodyGeomPx(
                (80 * density).toInt(), density, 12
            )
            assertTrue("70×80 中档仍色带", WeekGridWidgetProvider.weekGridColorBand(slotH, density))
        }
    }

    @Test
    fun `weekgrid 600dp height is normal tier with time labels`() {
        for (density in floatArrayOf(2f, 3f)) {
            val (_, slotH) = WeekGridWidgetProvider.weekGridBodyGeomPx(
                (600 * density).toInt(), density, 12
            )
            assertFalse("600dp 大档必常规", WeekGridWidgetProvider.weekGridColorBand(slotH, density))
            assertTrue("时间列阈值 slotH>18dp", slotH > 18f * density)
        }
    }

    @Test
    fun `weekgrid meal break adds visible spacing without changing body bounds`() {
        val (normalBody, normalSlot) = WeekGridWidgetProvider.weekGridBodyGeomPx(1200, 2f, 12)
        val (breakBody, breakSlot) = WeekGridWidgetProvider.weekGridBodyGeomPx(1200, 2f, 12, mealBreakCount = 1)
        assertEquals(normalBody, breakBody)
        assertTrue(breakSlot < normalSlot)
    }

    // ---- 四族 FIXED 窗口: 三档尺寸全尺寸同构 (无崩溃 + 锚行保底 + 页脚 best-effort) ----

    private fun todayData(n: Int) = WidgetData(
        date = LocalDate.now(),
        courses = (1..n).map { course(it.toLong()) },
        timeJson = "", hasTable = true
    )

    @Test
    fun `today window at 40 80 600 dp never crashes and keeps anchor row`() {
        for (hDp in floatArrayOf(40f, 80f)) {
            val w = TodayWidgetReceiver.computeTodayWindow(todayData(5), hDp, h("07:00"))
            assertEquals("极小高度 forceFirst 保 1 锚行", 1, w.visible.size)
            // 底部条定稿: 填满档 (footerH=0) 截断即点亮「+N」胶囊 → footer=true
            assertTrue("截断须点亮胶囊", w.footer)
            assertEquals(4, w.hiddenAheadCourses)
        }
        val big = TodayWidgetReceiver.computeTodayWindow(todayData(5), 600f, h("07:00"))
        assertEquals(5, big.visible.size)
        assertFalse(big.footer)
        assertEquals(0, big.hiddenAheadCourses)
    }

    @Test
    fun `twoday window at 40 80 600 dp never crashes and keeps anchor row`() {
        val data = TwoDayData(
            days = listOf(
                DayData(LocalDate.now(), 1, (1..4).map { course(it.toLong()) }, ""),
                DayData(LocalDate.now().plusDays(1), 2, (101..103).map { course(it.toLong()) }, "")
            ),
            hasTable = true
        )
        for (hDp in floatArrayOf(40f, 80f)) {
            val wins = TwoDayWidgetReceiver.computeTwoDayWindows(data, hDp, h("07:00"))
            assertEquals(2, wins.size)
            wins.forEach { w ->
                assertEquals("每列 forceFirst 保 1 锚行", 1, w.visible.size)
                assertFalse(w.footer)
            }
        }
        val big = TwoDayWidgetReceiver.computeTwoDayWindows(data, 600f, h("07:00"))
        assertEquals(4, big[0].visible.size)
        assertEquals(3, big[1].visible.size)
        big.forEach { assertFalse(it.footer); assertEquals(0, it.hiddenAheadCourses) }
    }

    @Test
    fun `weeklist window at 40 80 600 dp never crashes and keeps anchor row`() {
        for (hDp in floatArrayOf(40f, 80f)) {
            val w = WeekListWidgetReceiver.computeWeekListWindows(listOf(day(1, 5)), hDp, 0f).single()
            assertEquals("forceFirst 保 1 锚行", 1, w.visible.size)
            assertFalse("预算不足时保行弃页脚", w.footer)
            assertEquals(4, w.hiddenAheadCourses)
        }
        val big = WeekListWidgetReceiver.computeWeekListWindows(listOf(day(1, 5)), 600f, 0f).single()
        assertEquals(5, big.visible.size)
        assertFalse(big.footer)
    }

    @Test
    fun `weekview window at 40 80 600 dp never crashes and keeps anchor row`() {
        val lineH = 16f
        val heightOf: (CourseEntity) -> Float = { lineH }
        for (hDp in floatArrayOf(40f, 80f)) {
            val w = WeekViewWidgetReceiver.computeWeekViewWindows(
                listOf(day(1, 5)), hDp, 0f, lineH, heightOf
            ).single()
            assertEquals("forceFirst 保 1 锚行", 1, w.visible.size)
            assertFalse("预算不足时保行弃页脚", w.footer)
            assertEquals(4, w.hiddenAheadCourses)
        }
        val big = WeekViewWidgetReceiver.computeWeekViewWindows(
            listOf(day(1, 5)), 600f, 0f, lineH, heightOf
        ).single()
        assertEquals(5, big.visible.size)
        assertFalse(big.footer)
    }
}
