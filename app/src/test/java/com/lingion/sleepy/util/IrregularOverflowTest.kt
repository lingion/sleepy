package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用户反馈 2026-09-09(需求真源): 非常规时间课跨午间空隙吸附 bug。
 *
 * 场景: 用户建两门非常规课 10:30~12:30 与 14:00~16:00。作息表上午最后一节 11:40
 * 结束(节 4), 下午 14:00 开始(节 5), 11:40~14:00 之间没有任何节次。
 * 结果: 12:30 这个结束时间跨过午间空隙"吸附"到下午 14:00 的节次里渲染,
 * 和 14:00 的课"硬生生冲突了"。
 *
 * 本文件 = 复现测试(先红后修)。normalizeNode 的跨空隙反算(timeToNode 把 end
 * 向上取到下一个存在节点的 end)是 issue#23 时代的设计取舍, 用户已明确否决。
 */
class IrregularOverflowTest {

    private val base = TimeTableUtils.DEFAULT_TIME_JSON

    /** 用户场景课 1: 10:30~12:30, 存储坐标 (1,1) — 真实位置由时间决定 */
    private fun morningOverflow(id: Long, day: Int = 1) = CourseEntity(
        id = id, groupId = "g$id", tableId = 1L, courseName = "上午溢出课",
        day = day, startNode = 1, step = 1,
        startWeek = 1, endWeek = 16, color = "",
        ownTime = true, startTime = "10:30", endTime = "12:30"
    )

    /** 用户场景课 2: 14:00~16:00 = 节 5-6 */
    private fun afternoon(id: Long, day: Int = 1) = CourseEntity(
        id = id, groupId = "g$id", tableId = 1L, courseName = "下午课",
        day = day, startNode = 5, step = 2,
        startWeek = 1, endWeek = 16, color = "",
        ownTime = true, startTime = "14:00", endTime = "16:00"
    )

    /**
     * 加载链复现: normalizeNode 用 timeToNode 反算, 12:30 跨过 11:40~14:00 空隙
     * 向上吸附到节 5(14:00~14:45) → 反算出 (3, 3), 节点范围 3..5 侵入下午。
     * 用户意图: 反算节点范围不得跨过空隙(end 节点必须 <= 4)。
     */
    @Test
    fun normalizeNode_gapOverflow_notAbsorbedIntoAfternoon() {
        val normalized = morningOverflow(1).normalizeNode(base)
        assertTrue(
            "反算节点范围不得跨过午间空隙(end 节点必须 <= 4), 实际=${normalized.startNode}..${normalized.startNode + normalized.step - 1}",
            normalized.startNode + normalized.step - 1 <= 4
        )
        assertTrue("step 不得为非正", normalized.step >= 1)
    }

    /** 常规 ownTime 课(不跨空隙)反算行为不变 — 回归锚。 */
    @Test
    fun normalizeNode_continuousOverlap_unchanged() {
        val c = CourseEntity(
            id = 1, groupId = "g1", tableId = 1L, courseName = "课",
            day = 1, startNode = 1, step = 1, startWeek = 1, endWeek = 16, color = "",
            ownTime = true, startTime = "10:00", endTime = "11:40"
        )
        val n = c.normalizeNode(base)
        assertEquals(3, n.startNode)
        assertEquals(2, n.step)
    }

    /** 端到端: 用户两门课经完整加载语义(反算→聚簇)绝不成簇 — bug 本体。 */
    @Test
    fun endToEnd_userScenario_noFalseConflict() {
        val loaded = listOf(morningOverflow(1), afternoon(2))
            .map { it.normalizeNode(base) }
        val clusters = ConflictLayoutEngine.findClusters(loaded)
        assertTrue(
            "用户场景: 10:30~12:30 与 14:00~16:00 真实时间不重叠, 绝不冲突",
            clusters.isEmpty()
        )
        assertFalse(loaded.isEmpty())
    }
}
