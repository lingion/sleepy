package com.lingion.sleepy.widget

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 契约测试: [WidgetBoundaryScheduler] — 课程边界闹钟调度 (设计文稿 §5)。
 *
 * 锁死: 边界纯函数 (+1 分钟偏移、严格未来、无未来→null) / 行 end 分钟口径
 * (与 FIXED 锚点同源: 冲突行取 max、不可解析剔除) / 三处接线存在性
 * (notifyDataChanged 链内重排 · BootReceiver 无条件重排 · manifest 注册)。
 */
class WidgetBoundarySchedulerTest {

    // ---- nextBoundaryMin ----

    @Test
    fun `no boundaries yields null`() {
        assertNull(WidgetBoundaryScheduler.nextBoundaryMin(emptyList(), 540))
    }

    @Test
    fun `picks earliest future boundary with one minute offset`() {
        // 行 end 600/700, now=550 → 下一边界 601 (end+1, 让「已结束」判定生效)
        assertEquals(601, WidgetBoundaryScheduler.nextBoundaryMin(listOf(700, 600), 550))
    }

    @Test
    fun `boundary at now is already stale - strictly future only`() {
        // end=599 → 边界 600; now=600 → 600 不 > 600, 跳过取下一个
        assertEquals(701, WidgetBoundaryScheduler.nextBoundaryMin(listOf(599, 700), 600))
    }

    @Test
    fun `all boundaries past yields null`() {
        assertNull(WidgetBoundaryScheduler.nextBoundaryMin(listOf(480, 600), 1300))
    }

    @Test fun `wide grid schedules every individual period end without legacy offset`() {
        val periods = (1..12).map { WidePeriod(it, 480 + (it - 1) * 60, 525 + (it - 1) * 60) }
        val date = java.time.LocalDate.of(2026, 11, 2)
        assertEquals(date.atTime(8, 45), WidgetBoundaryScheduler.nextWidePeriodBoundary(periods, date.atTime(8, 0), 4))
        assertEquals(date.atTime(9, 45), WidgetBoundaryScheduler.nextWidePeriodBoundary(periods, date.atTime(8, 45), 4))
        assertEquals(date.atTime(10, 45), WidgetBoundaryScheduler.nextWidePeriodBoundary(periods, date.atTime(9, 45), 4))
        assertNull(WidgetBoundaryScheduler.nextWidePeriodBoundary(periods, date.atTime(23, 0), 4))
    }

    @Test fun `wide grid skips alarms after final full window and unresolved timings`() {
        val date = java.time.LocalDate.of(2026, 11, 2)
        assertNull(WidgetBoundaryScheduler.nextWidePeriodBoundary((1..4).map { WidePeriod(it, 0, 525) }, date.atTime(8, 0), 4))
        assertNull(WidgetBoundaryScheduler.nextWidePeriodBoundary(List(12) { WidePeriod(it + 1, null, null) }, date.atTime(8, 0), 4))
    }

    @Test fun `wide grid epoch conversion follows business zone not device zone`() {
        val time = java.time.LocalDate.of(2026, 11, 2).atTime(8, 45)
        val expected = time.atZone(com.lingion.sleepy.util.AppClock.zone).toInstant().toEpochMilli()
        val old = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"))
            assertEquals(expected, WidgetBoundaryScheduler.wideBoundaryEpoch(time))
        } finally { java.util.TimeZone.setDefault(old) }
    }

    @Test fun `complete alarm recomputation holds the scheduler monitor`() {
        val method = WidgetBoundaryScheduler::class.java.getDeclaredMethod("armNext", android.content.Context::class.java)
        assertTrue("read, compute, cancel and set must share the same monitor", java.lang.reflect.Modifier.isSynchronized(method.modifiers))
    }

    // ---- rowEndMins ----

    private fun course(
        id: Long,
        startNode: Int,
        step: Int,
        start: String,
        end: String
    ) = CourseEntity(
        id = id, groupId = "g$id", tableId = 1L, courseName = "课$id",
        day = 1, startNode = startNode, step = step,
        startWeek = 1, endWeek = 20, color = "blue",
        ownTime = true, isIrregularTime = true, startTime = start, endTime = end
    )

    @Test
    fun `sequential classes yield each row end minute`() {
        val mins = WidgetBoundaryScheduler.rowEndMins(
            listOf(
                course(1, startNode = 1, step = 2, start = "08:00", end = "09:40"),
                course(2, startNode = 3, step = 2, start = "10:00", end = "11:40")
            ),
            null
        )
        assertEquals(listOf(580, 700), mins.sorted())
    }

    @Test
    fun `conflict row takes max end of merged rows`() {
        // node1 step2 + node2 step2 重叠 → 同一冲突行, end 取两课 max
        val mins = WidgetBoundaryScheduler.rowEndMins(
            listOf(
                course(1, startNode = 1, step = 2, start = "08:00", end = "09:40"),
                course(2, startNode = 2, step = 2, start = "08:45", end = "10:25")
            ),
            null
        )
        assertEquals(listOf(625), mins)
    }

    @Test
    fun `unparseable course excluded from boundaries`() {
        val mins = WidgetBoundaryScheduler.rowEndMins(
            listOf(
                course(1, startNode = 1, step = 1, start = "", end = ""),
                course(2, startNode = 3, step = 1, start = "10:00", end = "10:50")
            ),
            null
        )
        assertEquals(listOf(650), mins)
    }

    // ---- 接线存在性 (源码级, 沿用 WeekViewScrollableWiringTest 风格) ----

    private fun source(rel: String): String {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, rel)
            if (f.isFile) return f.readText()
            dir = dir.parentFile
        }
        error("source not found: $rel")
    }

}
