package com.lingion.sleepy.widget.notification

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 上课自动勿扰边界计算纯函数测试 — 不依赖 Android 运行时。
 * 不变量: 区间 [start, end) 半开; 只排未来边界; 破损时间跳过; end<=start 跳过。
 */
class ClassDndSchedulerTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 7)

    private fun interval(startMin: String, endMin: String, date: LocalDate = today) =
        LocalDateTime.parse("${date}T$startMin") to LocalDateTime.parse("${date}T$endMin")

    private fun nodesOf(vararg spec: Pair<Int, Pair<String, String>>): List<TimeTableUtils.NodeTime> =
        spec.map { (node, se) ->
            TimeTableUtils.NodeTime(node, LocalTime.parse(se.first), LocalTime.parse(se.second))
        }

    private fun ownCourse(start: String, end: String) = CourseEntity(
        id = 1, groupId = "g", tableId = 1, courseName = "c",
        day = 3, startNode = 1, step = 1, startWeek = 1, endWeek = 20,
        color = "#FF6750A4", ownTime = true, startTime = start, endTime = end,
    )

    // ==================== isCurrentlyInClass ====================

    @Test
    fun `区间起点含 终点不含`() {
        val iv = listOf(interval("08:00", "09:40"))
        assertTrue(ClassDndScheduler.isCurrentlyInClass(iv, LocalDateTime.parse("${today}T08:00")))
        assertTrue(ClassDndScheduler.isCurrentlyInClass(iv, LocalDateTime.parse("${today}T09:39")))
        assertFalse(ClassDndScheduler.isCurrentlyInClass(iv, LocalDateTime.parse("${today}T09:40")))
        assertFalse(ClassDndScheduler.isCurrentlyInClass(iv, LocalDateTime.parse("${today}T07:59")))
    }

    @Test
    fun `空区间永远 false`() {
        assertFalse(ClassDndScheduler.isCurrentlyInClass(emptyList(), LocalDateTime.now()))
    }

    // ==================== nextBoundaries ====================

    @Test
    fun `只取未来边界`() {
        val iv = listOf(
            interval("08:00", "09:40"), // now=12:00 → 全过期
            interval("14:00", "15:40", today.plusDays(1)),
        )
        val now = LocalDateTime.parse("${today}T12:00")
        val b = ClassDndScheduler.nextBoundaries(iv, now)
        assertEquals(LocalDateTime.parse("${today.plusDays(1)}T14:00"), b.nextStart)
        assertEquals(LocalDateTime.parse("${today.plusDays(1)}T15:40"), b.nextEnd)
    }

    @Test
    fun `无未来边界 双双 null`() {
        val b = ClassDndScheduler.nextBoundaries(listOf(interval("08:00", "09:40")), LocalDateTime.parse("${today}T12:00"))
        assertNull(b.nextStart)
        assertNull(b.nextEnd)
    }

    @Test
    fun `多门课 各自取最早未来`() {
        val iv = listOf(
            interval("10:00", "11:40"),
            interval("09:00", "09:40"),
            interval("13:00", "14:40"),
        )
        val b = ClassDndScheduler.nextBoundaries(iv, LocalDateTime.parse("${today}T08:00"))
        assertEquals(LocalDateTime.parse("${today}T09:00"), b.nextStart)
        assertEquals(LocalDateTime.parse("${today}T09:40"), b.nextEnd)
    }

    // ==================== courseInterval ====================

    @Test
    fun `ownTime 课 直接用自定义时间`() {
        val iv = ClassDndScheduler.courseInterval(today, ownCourse("09:30", "11:00"), nodesOf(1 to ("08:00" to "08:45")))
        assertEquals(LocalDateTime.parse("${today}T09:30"), iv?.first)
        assertEquals(LocalDateTime.parse("${today}T11:00"), iv?.second)
    }

    @Test
    fun `节次课 按节点表换算 step 跨多节`() {
        val nodes = nodesOf(1 to ("08:00" to "08:45"), 2 to ("08:55" to "09:40"))
        val c = CourseEntity(
            id = 1, groupId = "g", tableId = 1, courseName = "c",
            day = 3, startNode = 1, step = 2, startWeek = 1, endWeek = 20,
            color = "#FF6750A4",
        )
        val iv = ClassDndScheduler.courseInterval(today, c, nodes)
        assertEquals(LocalDateTime.parse("${today}T08:00"), iv?.first)
        assertEquals(LocalDateTime.parse("${today}T09:40"), iv?.second)
    }

    @Test
    fun `破损 ownTime 跳过`() {
        assertNull(ClassDndScheduler.courseInterval(today, ownCourse("25:00", "11:00"), emptyList()))
        assertNull(ClassDndScheduler.courseInterval(today, ownCourse("09:00", "99:99"), emptyList()))
    }

    @Test
    fun `找不到节点表 跳过`() {
        val c = CourseEntity(
            id = 1, groupId = "g", tableId = 1, courseName = "c",
            day = 3, startNode = 9, step = 1, startWeek = 1, endWeek = 20,
            color = "#FF6750A4",
        )
        assertNull(ClassDndScheduler.courseInterval(today, c, nodesOf(1 to ("08:00" to "08:45"))))
    }

    @Test
    fun `结束不晚于开始 跳过`() {
        assertNull(ClassDndScheduler.courseInterval(today, ownCourse("10:00", "10:00"), emptyList()))
        assertNull(ClassDndScheduler.courseInterval(today, ownCourse("10:00", "09:00"), emptyList()))
    }
}
