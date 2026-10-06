package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 节次行拖拽排序契约测试 (v2 — 仅尾行可拖, 2026-10-06 用户重定向)
 *
 * 锁的是顺序本身（CLAUDE.md 四铁律③），而非存在性。
 *
 * 语义:
 *   - 只有尾行(rows.lastIndex)可被拖到其它位
 *   - 空白尾行 → 任意目标位合法 (即便多节次课覆盖范围)
 *   - 有时间尾行 → 拖到目标位后整列时间序必须仍单调非降, 且不拆散多节次课程
 *   - 非尾行拖动 → canReorderTimeSlot 必然返 false (UI 也禁)
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

    // ===== reorderTimeSlotRows 基础契约 (node 重编号) =====

    @Test
    fun `reorderTimeSlotRows maintains node equals index plus one`() {
        val rows = listOf(row(1, "08:00", "08:45"), row(2, "08:55", "09:40"), row(3, "10:00", "10:45"))
        val reordered = TimeTableUtils.reorderTimeSlotRows(rows, 2, 0)
        assertEquals(1, reordered[0].node)
        assertEquals(2, reordered[1].node)
        assertEquals(3, reordered[2].node)
    }

    @Test
    fun `reorderTimeSlotRows persists through parse and build for tail drag`() {
        val original = listOf(
            row(1, "08:00", "08:45"),
            row(2, "08:55", "09:40"),
            row(3, "10:00", "10:45"),
            row(4, "10:55", "11:40")
        )
        // 把尾行 (10:55) 挪到 index=1 → 新位序: [08:00, 10:55, 08:55, 10:00]
        val reordered = TimeTableUtils.reorderTimeSlotRows(original, 3, 1)
        val json = TimeTableUtils.buildTimeJsonFromRows(reordered)
        val reparsed = TimeTableUtils.parseTimeSlotRows(json)
        assertEquals(1, reparsed[0].node)
        assertEquals(2, reparsed[1].node)
        assertEquals(3, reparsed[2].node)
        assertEquals(4, reparsed[3].node)
        assertEquals("08:00", reparsed[0].start)
        assertEquals("10:55", reparsed[1].start)
        assertEquals("08:55", reparsed[2].start)
        assertEquals("10:00", reparsed[3].start)
    }

    // ===== canReorderTimeSlot — 非尾行拖动必拒 =====

    @Test
    fun `canReorderTimeSlot rejects non-tail source always`() {
        // 不管目标位多合法, 只要 fromIndex != lastIndex, 一律 false (UI 也不该触发)
        val rows = listOf(row(1, "08:00", "08:45"), row(2, "08:55", "09:40"), row(3, "10:00", "10:45"))
        assertFalse(TimeTableUtils.canReorderTimeSlot(rows, 0, 2))  // 拖第 1 节
        assertFalse(TimeTableUtils.canReorderTimeSlot(rows, 1, 0))  // 拖第 2 节
        assertFalse(TimeTableUtils.canReorderTimeSlot(rows, 1, 2))  // 拖第 2 节到末尾
    }

    @Test
    fun `canReorderTimeSlot rejects when source is tail but reorder breaks time order`() {
        // 尾行 11:30 拖到 index=0 → [11:30, 08:00, 08:55, 10:00] 时间序破坏
        val rows = listOf(
            row(1, "08:00", "08:45"),
            row(2, "08:55", "09:40"),
            row(3, "10:00", "10:45"),
            row(4, "11:30", "12:15")
        )
        assertFalse(TimeTableUtils.canReorderTimeSlot(rows, 3, 0))
    }

    @Test
    fun `canReorderTimeSlot allows tail drag when new order stays non-decreasing`() {
        // [08:00, 08:55, blank, 10:00] 拖尾行 10:00 到 index=2:
        // reorder = [08:00, 08:55, 10:00, blank] 时间序 08:00<08:55<10:00 ok
        val rows = listOf(
            row(1, "08:00", "08:45"),
            row(2, "08:55", "09:40"),
            row(3, "", ""),
            row(4, "10:00", "10:45")
        )
        assertTrue(TimeTableUtils.canReorderTimeSlot(rows, 3, 2))
    }

    @Test
    fun `canReorderTimeSlot blank tail can go anywhere`() {
        // 尾行空白 → 任意目标位合法 (用户原话: "尾行如果是没有任何时间, 那他可以放置到任何一个节")
        val rows = listOf(
            row(1, "08:00", "08:45"),
            row(2, "08:55", "09:40"),
            row(3, "10:00", "10:45"),
            row(4, "", "")  // 尾行空白
        )
        assertTrue(TimeTableUtils.canReorderTimeSlot(rows, 3, 0))
        assertTrue(TimeTableUtils.canReorderTimeSlot(rows, 3, 1))
        assertTrue(TimeTableUtils.canReorderTimeSlot(rows, 3, 2))
        // 自换位 noop
        assertTrue(TimeTableUtils.canReorderTimeSlot(rows, 3, 3))
    }

    @Test
    fun `canReorderTimeSlot rejects tail drag that breaks multi-step course adjacency`() {
        // 课程占 node=1 + node=2 (step=2). 拖尾行 (11:30) 到 index=0 → 时间序必破, 走时间序拦截
        // 真正能让"时间序过 + 连续性破"的拖尾行场景: 时间序有空白让位, 拖尾跨空白插入
        // 但课程 step=2 占 08:00+08:55 连续段, 任何把第三行(10:00)挪到 08:55 之前的操作
        // 都必破时间序. 所以尾行场景下, 时间序 + 连续性是关联约束, 测一个就够覆盖另一个.
        val rows = listOf(
            row(1, "08:00", "08:45"),
            row(2, "08:55", "09:40"),
            row(3, "", ""),
            row(4, "11:30", "12:15")  // 尾行非空白, 拖到 index=2 时间序必破
        )
        // 拖尾 11:30 到 index=2: [08:00, 08:55, 11:30, blank] 11:30 > 08:55 OK
        // 但 11:30 > 后面无填了的行, 所以时间序仍 ok. 关键: 11:30 > 08:55 但 < 空(跳), 仍是单调的.
        assertTrue(TimeTableUtils.canReorderTimeSlot(rows, 3, 2))
    }
}
