package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 节次行拖拽排序契约测试。
 * 锁的是顺序本身（CLAUDE.md 四铁律③），而非存在性。
 */
class TimeSlotReorderContractTest {

    private fun row(node: Int, start: String = "", end: String = "") =
        TimeTableUtils.TimeSlotRow(node = node, start = start, end = end)

    private fun course(startNode: Int, step: Int, ownTime: Boolean = false) =
        CourseEntity(
            groupId = "g", tableId = 1L, courseName = "课", day = 1,
            startNode = startNode, step = step,
            startWeek = 1, endWeek = 16, type = 0,
            color = "#FF6750A4", ownTime = ownTime,
            startTime = "", endTime = ""
        )

    @Test
    fun `reorderTimeSlotRows maintains node equals index plus one`() {
        val rows = listOf(row(1, "08:00", "08:45"), row(2, "08:55", "09:40"), row(3, "10:00", "10:45"))
        val reordered = TimeTableUtils.reorderTimeSlotRows(rows, 0, 2)
        assertEquals(1, reordered[0].node)
        assertEquals(2, reordered[1].node)
        assertEquals(3, reordered[2].node)
    }

    @Test
    fun `reorderTimeSlotRows persists through parse and build`() {
        val original = listOf(
            row(1, "08:00", "08:45"),
            row(2, "08:55", "09:40"),
            row(3, "10:00", "10:45"),
            row(4, "10:55", "11:40")
        )
        // 把第 2 行 (08:55) 移到 index=3 → 新位序:
        //   [1号(08:00), 3号(10:00), 4号(10:55), 2号(08:55)]
        val reordered = TimeTableUtils.reorderTimeSlotRows(original, 1, 3)
        val json = TimeTableUtils.buildTimeJsonFromRows(reordered)
        val reparsed = TimeTableUtils.parseTimeSlotRows(json)
        // node 重编号为 1..4
        assertEquals(1, reparsed[0].node)
        assertEquals(2, reparsed[1].node)
        assertEquals(3, reparsed[2].node)
        assertEquals(4, reparsed[3].node)
        // start 保持原行语义, 按新位序排列
        assertEquals("08:00", reparsed[0].start)
        assertEquals("10:00", reparsed[1].start)
        assertEquals("10:55", reparsed[2].start)
        assertEquals("08:55", reparsed[3].start)
    }

    @Test
    fun `isTimeSlotOrderValid skips blank rows`() {
        val withBlank = listOf(row(1, "08:00", "08:45"), row(2, "", ""), row(3, "09:00", "09:45"))
        assertTrue(TimeTableUtils.isTimeSlotOrderValid(withBlank))
    }

    @Test
    fun `isTimeSlotOrderValid detects invalid order despite blank`() {
        val withBlank = listOf(row(1, "10:00", "10:45"), row(2, "", ""), row(3, "08:00", "08:45"))
        assertFalse(TimeTableUtils.isTimeSlotOrderValid(withBlank))
    }

    @Test
    fun `canReorderTimeSlot rejects breaking multi-step course`() {
        val rows = listOf(row(1, "08:00", "08:45"), row(2, "08:55", "09:40"), row(3, "10:00", "10:45"))
        val courses = listOf(course(startNode = 2, step = 2))
        assertFalse(TimeTableUtils.canReorderTimeSlot(rows, 0, 2, courses))
    }

    @Test
    fun `canReorderTimeSlot rejects when reorder breaks time order`() {
        val rows = listOf(row(1, "08:00", "08:45"), row(2, "08:55", "09:40"), row(3, "10:00", "10:45"))
        val courses = emptyList<CourseEntity>()
        // 把 08:00 移到 index=2 → 顺序变为 [08:55, 10:00, 08:00], 时间序破坏
        assertFalse(TimeTableUtils.canReorderTimeSlot(rows, 0, 2, courses))
    }

    @Test
    fun `canReorderTimeSlot allows blank row to go anywhere`() {
        val rows = listOf(row(1, "08:00", "08:45"), row(2, "", ""), row(3, "10:00", "10:45"))
        val courses = listOf(course(startNode = 1, step = 1))
        assertTrue(TimeTableUtils.canReorderTimeSlot(rows, 1, 0, courses))
        assertTrue(TimeTableUtils.canReorderTimeSlot(rows, 1, 2, courses))
    }

    @Test
    fun `canReorderTimeSlot rejects single step course when it breaks time order`() {
        val rows = listOf(row(1, "08:00", "08:45"), row(2, "08:55", "09:40"), row(3, "10:00", "10:45"))
        val courses = listOf(course(startNode = 2, step = 1))
        // 把 08:00 挪到 index=1 → 顺序 [08:55, 08:00, 10:00] 时间序破坏
        assertFalse(TimeTableUtils.canReorderTimeSlot(rows, 0, 1, courses))
    }

    @Test
    fun `canReorderTimeSlot allows ownTime course to swap without breaking time order`() {
        val rows = listOf(row(1, "08:00", "08:45"), row(2, "08:55", "09:40"), row(3, "10:00", "10:45"))
        val courses = listOf(course(startNode = 1, step = 2, ownTime = true))
        // ownTime 课不参与连续性, 但时间序依然要守
        // 把 08:00 挪到 index=1 → 时间序破坏 → reject
        assertFalse(TimeTableUtils.canReorderTimeSlot(rows, 0, 1, courses))
    }
}
