package com.lingion.sleepy

import androidx.compose.runtime.mutableStateListOf
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.screen.edit.MeetingBlockDraft
import com.lingion.sleepy.ui.screen.edit.findTargetBlockIndex
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * findTargetBlockIndex 纯函数 JVM 直测 — 锁匹配公式正确性, 重点覆盖歧义场景。
 */
class FindTargetBlockIndexTest {

    private fun block(id: Int, sourceIds: List<Long>, days: List<Int> = listOf(1), startNode: Int = 1, step: Int = 2) =
        MeetingBlockDraft(
            id = id,
            sourceIds = sourceIds,
            days = mutableStateListOf<Int>().apply { addAll(days) },
            startNode = startNode,
            step = step,
            startTime = "08:00",
            endTime = "09:40"
        )

    private fun course(id: Long, day: Int = 1, startNode: Int = 1, step: Int = 2) =
        CourseEntity(
            id = id,
            groupId = "g1", tableId = 1, courseName = "n", alias = "",
            teacher = "", room = "", note = "",
            day = day, startNode = startNode, step = step,
            startWeek = 1, endWeek = 16, type = 0,
            color = "", colorMode = com.lingion.sleepy.data.entity.CourseColorMode.GROUP,
            isIrregularNode = false, isIrregularTime = false, ownTime = false,
            startTime = "", endTime = ""
        )

    @Test
    fun `null editing course returns -1`() {
        val blocks = listOf(block(1, listOf(1L)))
        assertEquals(-1, findTargetBlockIndex(blocks, null))
    }

    @Test
    fun `editing course id matches sourceIds returns its index`() {
        val blocks = listOf(
            block(1, listOf(10L, 11L)),
            block(2, listOf(20L)),
            block(3, listOf(30L, 31L, 32L))
        )
        // 不同位置都能命中
        assertEquals(0, findTargetBlockIndex(blocks, course(id = 11L)))
        assertEquals(1, findTargetBlockIndex(blocks, course(id = 20L)))
        assertEquals(2, findTargetBlockIndex(blocks, course(id = 32L)))
    }

    @Test
    fun `editing course id not in any block returns -1`() {
        val blocks = listOf(block(1, listOf(10L)), block(2, listOf(20L)))
        assertEquals(-1, findTargetBlockIndex(blocks, course(id = 999L)))
    }

    /**
     * 用户报障 2026-10-08 — 同 (day, startNode) 跨组歧义:
     * - 课程 A: 周一 1-2节, step=2
     * - 课程 B: 周一 1节,   step=1
     * groupSlotsForEdit 按 (ownTime, startNode, step, ...) 切分, A 与 B 落入不同
     * 块, 但 (day=1, startNode=1) 共享。v1 用 (day+startNode) 匹配时 indexOfFirst
     * 命中靠前的块, 用户点 B 错跳 A。v2 用 CourseEntity.id 区分, 必命中 B。
     */
    @Test
    fun `ambiguous (day, startNode) across groups resolves by id`() {
        val courseAId = 100L  // 周一 1-2节, step=2
        val courseBId = 200L  // 周一 1节,   step=1
        val blocks = listOf(
            block(id = 1, sourceIds = listOf(courseAId), days = listOf(1), startNode = 1, step = 2),
            block(id = 2, sourceIds = listOf(courseBId), days = listOf(1), startNode = 1, step = 1)
        )
        // 用户点 A → 必返回 0
        assertEquals(0, findTargetBlockIndex(blocks, course(id = courseAId, day = 1, startNode = 1, step = 2)))
        // 用户点 B → 必返回 1 (v1 错返 0, 这条测试即用户报障的反例回归锁)
        assertEquals(1, findTargetBlockIndex(blocks, course(id = courseBId, day = 1, startNode = 1, step = 1)))
    }

    /**
     * 同 (day, startNode) 跨周次: 课程 A 第 1-8 周, 课程 B 第 9-16 周。
     * groupSlotsForEdit 按 startWeek/endWeek 切组, (day, startNode) 同样共享。
     */
    @Test
    fun `ambiguous (day, startNode) across week ranges resolves by id`() {
        val courseAId = 300L  // 第 1-8 周
        val courseBId = 400L  // 第 9-16 周
        val blocks = listOf(
            block(id = 1, sourceIds = listOf(courseAId)),
            block(id = 2, sourceIds = listOf(courseBId))
        )
        val editingA = course(id = courseAId).copy(startWeek = 1, endWeek = 8)
        val editingB = course(id = courseBId).copy(startWeek = 9, endWeek = 16)
        assertEquals(0, findTargetBlockIndex(blocks, editingA))
        assertEquals(1, findTargetBlockIndex(blocks, editingB))
    }
}
