package com.lingion.sleepy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Issue #23 / 用户 2026-09-06 想法: 课表外节次(第 0 节 / 第 N+1 节 / 第 -1 节 / 第 N+2 节) 与非标准时长。
 *
 * 节次语义:
 *   - 标准节次 1..N 不变, 沿用现有 timeJson 节点编号 (N = maxContiguousFromOne)
 *   - "前置"节次 < 1: 第一个新建 = 0, 后续 = (现有前置最小值) - 1
 *   - "后置"节次 > N: 第一个新建 = N+1, 后续 = (现有后置最大值) + 1
 *   - 删除某边缘节次上**全部**课程时, 该节点必须从 timeJson 中回收(用户明示:
 *     "课程表应恢复它正常之前的那一个节次, 而不是再留一个空的第 0 节在那边")
 */
class TimeTableUtilsEdgeNodeTest {

    private fun rows(json: String) = TimeTableUtils.parseTimeSlotRows(json)

    /** 默认 12 节 (1..12), 无边缘节点 */
    private val baseStandard = TimeTableUtils.DEFAULT_TIME_JSON

    // ===== insertEdgeNode(Before) =====

    @Test
    fun insertEdgeNode_before_first_call_inserts_node_zero() {
        val json = TimeTableUtils.insertEdgeNode(baseStandard, TimeTableUtils.EdgeClass.Before, "07:30", "08:00")
        val rs = rows(json)
        assertEquals("标准 1..12 + 前置 0 = 13 行", 13, rs.size)
        val zero = rs.firstOrNull { it.node == 0 }
        assertNotNull("节点 0 必须存在", zero)
        assertEquals("07:30", zero!!.start)
        assertEquals("08:00", zero.end)
    }

    @Test
    fun insertEdgeNode_before_when_zero_exists_inserts_minus_one() {
        // 用户先加了第 0 节, 又想加再早一节 → 第 -1 节
        val once = TimeTableUtils.insertEdgeNode(baseStandard, TimeTableUtils.EdgeClass.Before, "07:30", "08:00")
        val twice = TimeTableUtils.insertEdgeNode(once, TimeTableUtils.EdgeClass.Before, "07:00", "07:30")
        val rs = rows(twice)
        assertEquals("13 + 1 = 14 行", 14, rs.size)
        val minus1 = rs.firstOrNull { it.node == -1 }
        val zero = rs.firstOrNull { it.node == 0 }
        assertNotNull("节点 -1 必须存在", minus1)
        assertEquals("07:00", minus1!!.start)
        assertNotNull("节点 0 仍存在", zero)
    }

    @Test
    fun insertEdgeNode_before_keeps_standard_one_through_n_unchanged() {
        val json = TimeTableUtils.insertEdgeNode(baseStandard, TimeTableUtils.EdgeClass.Before, "07:30", "08:00")
        val rs = rows(json)
        for (n in 1..12) {
            val r = rs.firstOrNull { it.node == n }
            assertNotNull("标准节次 $n 必须保留", r)
        }
    }

    // ===== insertEdgeNode(After) =====

    @Test
    fun insertEdgeNode_after_first_call_inserts_node_max_plus_one() {
        // 标准 12 节, 第一个后置 = 13
        val json = TimeTableUtils.insertEdgeNode(baseStandard, TimeTableUtils.EdgeClass.After, "22:30", "23:15")
        val rs = rows(json)
        assertEquals("12 + 1 = 13 行", 13, rs.size)
        val n13 = rs.firstOrNull { it.node == 13 }
        assertNotNull("节点 13 必须存在", n13)
        assertEquals("22:30", n13!!.start)
        assertEquals("23:15", n13.end)
    }

    @Test
    fun insertEdgeNode_after_when_thirteen_exists_inserts_fourteen() {
        val once = TimeTableUtils.insertEdgeNode(baseStandard, TimeTableUtils.EdgeClass.After, "22:30", "23:15")
        val twice = TimeTableUtils.insertEdgeNode(once, TimeTableUtils.EdgeClass.After, "23:15", "24:00")
        val rs = rows(twice)
        assertEquals(14, rs.size)
        val n14 = rs.firstOrNull { it.node == 14 }
        assertNotNull(n14)
        assertEquals("23:15", n14!!.start)
    }

    @Test
    fun insertEdgeNode_after_when_both_standard_and_edge_present_uses_edge_max_plus_one() {
        // 用户先扩到 14 (1..14 都是标准), 再加后置 → 15
        // 这里手动改 baseStandard 没法, 走 buildTimeJsonFromRows 模拟 13 节标准
        val fourteenStandard = TimeTableUtils.buildTimeJsonFromRows(
            (1..14).map { TimeTableUtils.TimeSlotRow(it, "08:00", "08:45") }
        )
        val once = TimeTableUtils.insertEdgeNode(fourteenStandard, TimeTableUtils.EdgeClass.After, "23:00", "23:45")
        val rs = rows(once)
        // 1..14 是标准(连续), 15 是后置
        assertEquals(15, rs.size)
        assertNotNull(rs.firstOrNull { it.node == 15 })
    }

    // ===== removeEdgeNodeIfUnused =====

    @Test
    fun removeEdgeNodeIfUnused_returns_unchanged_when_node_still_referenced() {
        // 节次 0 上还有课程引用 → 不能删
        val json = TimeTableUtils.insertEdgeNode(baseStandard, TimeTableUtils.EdgeClass.Before, "07:30", "08:00")
        val usedNodes = setOf(0)   // 还有课程 startNode == 0
        val result = TimeTableUtils.removeEdgeNodeIfUnused(json, 0, usedNodes)
        assertEquals(json, result)
    }

    @Test
    fun removeEdgeNodeIfUnused_removes_when_no_course_references() {
        // 节次 0 上已无课程 → 回收
        val json = TimeTableUtils.insertEdgeNode(baseStandard, TimeTableUtils.EdgeClass.Before, "07:30", "08:00")
        val usedNodes = emptySet<Int>()
        val result = TimeTableUtils.removeEdgeNodeIfUnused(json, 0, usedNodes)
        val rs = rows(result)
        assertEquals("回到 12 行", 12, rs.size)
        assertNull("节点 0 必须被移除", rs.firstOrNull { it.node == 0 })
    }

    @Test
    fun removeEdgeNodeIfUnused_does_not_remove_standard_node() {
        // 标准节次 (1..12) 严禁被回收 — 即便无人引用, 也不在 edge 范围
        val result = TimeTableUtils.removeEdgeNodeIfUnused(baseStandard, 5, emptySet())
        assertEquals(baseStandard, result)
    }

    // ===== edgeNodesOf =====

    @Test
    fun edgeNodesOf_Before_returns_descending_list() {
        // 用户连加三次前置: 第 0 节, 第 -1 节, 第 -2 节
        var json = baseStandard
        json = TimeTableUtils.insertEdgeNode(json, TimeTableUtils.EdgeClass.Before, "07:30", "08:00")
        json = TimeTableUtils.insertEdgeNode(json, TimeTableUtils.EdgeClass.Before, "07:00", "07:30")
        json = TimeTableUtils.insertEdgeNode(json, TimeTableUtils.EdgeClass.Before, "06:30", "07:00")
        // 前置列表 = [0, -1, -2] (按节点号降序, 与插入顺序一致)
        val edges = TimeTableUtils.edgeNodesOf(json, TimeTableUtils.EdgeClass.Before)
        assertEquals(listOf(0, -1, -2), edges)
    }

    @Test
    fun edgeNodesOf_After_returns_ascending_list() {
        var json = baseStandard
        json = TimeTableUtils.insertEdgeNode(json, TimeTableUtils.EdgeClass.After, "22:30", "23:15")
        json = TimeTableUtils.insertEdgeNode(json, TimeTableUtils.EdgeClass.After, "23:15", "24:00")
        val edges = TimeTableUtils.edgeNodesOf(json, TimeTableUtils.EdgeClass.After)
        assertEquals(listOf(13, 14), edges)
    }

    @Test
    fun edgeNodesOf_empty_when_no_edge() {
        assertEquals(emptyList<Int>(), TimeTableUtils.edgeNodesOf(baseStandard, TimeTableUtils.EdgeClass.Before))
        assertEquals(emptyList<Int>(), TimeTableUtils.edgeNodesOf(baseStandard, TimeTableUtils.EdgeClass.After))
    }

    // ===== reclaimUnusedEdgeNodes (issue#23 fix: ScheduleRepository 删课后接线) =====

    @Test
    fun reclaimUnusedEdgeNodes_removes_all_unused_before_edges() {
        // 第 0 节 + 第 -1 节 都无人引用 → 全部回收
        var json = baseStandard
        json = TimeTableUtils.insertEdgeNode(json, TimeTableUtils.EdgeClass.Before, "07:30", "08:00")
        json = TimeTableUtils.insertEdgeNode(json, TimeTableUtils.EdgeClass.Before, "07:00", "07:30")
        val reclaimed = TimeTableUtils.reclaimUnusedEdgeNodes(json, emptySet())
        assertEquals(12, rows(reclaimed).size)
        assertNull(rows(reclaimed).firstOrNull { it.node == 0 })
        assertNull(rows(reclaimed).firstOrNull { it.node == -1 })
    }

    @Test
    fun reclaimUnusedEdgeNodes_removes_all_unused_after_edges() {
        var json = baseStandard
        json = TimeTableUtils.insertEdgeNode(json, TimeTableUtils.EdgeClass.After, "22:30", "23:15")
        json = TimeTableUtils.insertEdgeNode(json, TimeTableUtils.EdgeClass.After, "23:15", "24:00")
        val reclaimed = TimeTableUtils.reclaimUnusedEdgeNodes(json, emptySet())
        assertEquals(12, rows(reclaimed).size)
        assertNull(rows(reclaimed).firstOrNull { it.node == 13 })
        assertNull(rows(reclaimed).firstOrNull { it.node == 14 })
    }

    @Test
    fun reclaimUnusedEdgeNodes_keeps_referenced_edges() {
        // 第 0 节还有课引用, 第 -1 节无人引用 → 只回收 -1
        var json = baseStandard
        json = TimeTableUtils.insertEdgeNode(json, TimeTableUtils.EdgeClass.Before, "07:30", "08:00")
        json = TimeTableUtils.insertEdgeNode(json, TimeTableUtils.EdgeClass.Before, "07:00", "07:30")
        val reclaimed = TimeTableUtils.reclaimUnusedEdgeNodes(json, setOf(0))
        val rs = rows(reclaimed)
        assertEquals(13, rs.size)
        assertNotNull(rs.firstOrNull { it.node == 0 })  // 保留
        assertNull(rs.firstOrNull { it.node == -1 })    // 回收
    }

    @Test
    fun reclaimUnusedEdgeNodes_handles_mixed_before_and_after() {
        // 第 0 节无引用 + 第 13 节有引用 → 仅回收 0, 保留 13
        var json = baseStandard
        json = TimeTableUtils.insertEdgeNode(json, TimeTableUtils.EdgeClass.Before, "07:30", "08:00")
        json = TimeTableUtils.insertEdgeNode(json, TimeTableUtils.EdgeClass.After, "22:30", "23:15")
        val reclaimed = TimeTableUtils.reclaimUnusedEdgeNodes(json, setOf(13))
        val rs = rows(reclaimed)
        assertEquals(13, rs.size)
        assertNull(rs.firstOrNull { it.node == 0 })
        assertNotNull(rs.firstOrNull { it.node == 13 })
    }

    @Test
    fun reclaimUnusedEdgeNodes_noop_when_no_edges_present() {
        assertEquals(baseStandard, TimeTableUtils.reclaimUnusedEdgeNodes(baseStandard, emptySet()))
    }

    @Test
    fun reclaimUnusedEdgeNodes_does_not_remove_standard_nodes() {
        // 即便 usedNodes 为空, 标准 1..12 也绝不回收
        assertEquals(baseStandard, TimeTableUtils.reclaimUnusedEdgeNodes(baseStandard, emptySet()))
    }
}