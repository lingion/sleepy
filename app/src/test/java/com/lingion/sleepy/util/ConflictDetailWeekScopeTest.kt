package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * v7.10.16q — 详情弹窗"幽灵图层"回归(用户 2026-09-03 21:xx 报障):
 * ICS 往返后周四 8-10 节的电路与电子II存在两行(周1-4 / 周6-13, 间隔周5),
 * 加上 6-9(周16) 实验室行 — 三行节次两两重叠但**周次永不相交**。
 * 网格按周过滤后本周只有一行(无冲突);详情弹窗此前拿全周课程 → 3 图层 →
 * 错误地弹"选择默认置顶课程"。
 *
 * 定案: 详情弹窗的簇判定必须与网格同周域 — 课程集先按选中周过滤再聚簇。
 * 本测试用真实 ICS 往返形态, 纯 JVM 复现(week 2 视角):
 *   week-filtered → 周四只剩 8-10(a) → findClusters 无该课的簇 → 不弹置顶
 *   unfiltered    → 三行同簇 → 3 图层 → 复现报障(改前行为)
 */
class ConflictDetailWeekScopeTest {

    private fun c(
        id: Long, day: Int, startNode: Int, endNode: Int,
        startWeek: Int, endWeek: Int, type: Int = 0,
        name: String = "电路与电子II"
    ) = CourseEntity(
        id = id, groupId = "g$id", tableId = 1, courseName = name,
        day = day, startNode = startNode, step = endNode - startNode + 1,
        startWeek = startWeek, endWeek = endWeek, type = type, color = ""
    )

    /** ICS 往返后的周四全量行(真实形态) */
    private val thursdayRows = listOf(
        c(1, 4, 1, 2, 2, 4, 0, "工程设计与计算"),
        c(2, 4, 1, 4, 16, 16, 0, "电路与电子II"),      // 周16 实验室
        c(3, 4, 3, 4, 1, 4, 0, "大学物理C（二）"),
        c(3+9, 4, 3, 4, 6, 13, 0, "大学物理C（二）"),   // 换教师拆行(周6-13 王雷)
        c(4, 4, 6, 9, 16, 16, 0, "电路与电子II"),      // 周16 实验室
        c(5, 4, 8, 10, 1, 4, 0, "电路与电子II"),       // 周1-4
        c(6, 4, 8, 10, 6, 13, 0, "电路与电子II"),      // 周6-13 ← 用户点的(周6 视角)
        c(7, 4, 11, 13, 6, 6, 0, "迈克尔逊"),
    )

    @Test
    fun week_filtered_thursday_has_no_phantom_conflict_at_nodes_8_10() {
        // 周2 视角(网格所见): 只有周1-4 的行存在
        val week2 = thursdayRows.filter { it.inWeek(2) }
        val nodes89 = week2.filter { it.startNode <= 8 && 8 <= it.startNode + it.step - 1 }
        assertEquals("week 2 has exactly one 8-10 row", 1, nodes89.count { it.startNode == 8 })

        val clusters = ConflictLayoutEngine.findClusters(week2)
        val tappedCluster = clusters.firstOrNull { cl -> cl.courses.any { it.id == 5L } }
        assertNull("week 2: tapped 8-10 has NO conflict cluster (weeks 1-4 rows only overlap 工设1-2/物理3-4 — not 8-10)", tappedCluster)
    }

    @Test
    fun week_filtered_week6_keeps_real_layering_for_tapped_course() {
        // 周6 视角: 8-10(b) 与 迈克尔逊11-13 不重叠节次; 8-10 只此一行 → 仍无置顶可选
        val week6 = thursdayRows.filter { it.inWeek(6) }
        val clusters = ConflictLayoutEngine.findClusters(week6)
        val tappedCluster = clusters.firstOrNull { cl -> cl.courses.any { it.id == 6L } }
        // 周6 的 8-10(b) 邻居: 11-13 迈克尔逊(不相交), 6-9实验室不在周6 → 单课不成簇
        assertNull("week 6: tapped 8-10(b) has no overlapping neighbor", tappedCluster)
    }

    @Test
    fun unfiltered_courses_reproduce_reported_three_layers() {
        // 改前行为存档: 全周课程聚簇 → 被点课所在簇 = {6-9, 8-10(a), 8-10(b)} → 3 图层
        val clusters = ConflictLayoutEngine.findClusters(thursdayRows)
        val tappedCluster = clusters.first { cl -> cl.courses.any { it.id == 6L } }
        val layers = ConflictLayoutEngine.chainGroups(tappedCluster.courses)
        assertEquals(3, layers.size)
    }

    @Test
    fun week16_lab_rows_form_no_phantom_cluster() {
        // 周16 视角: 在场行 = 1-4(电路实验室) + 6-9(电路实验室)。
        // 节次 1-4 与 6-9 不相交 → 无任何簇(不弹置顶)。
        val week16 = thursdayRows.filter { it.inWeek(16) }
        assertEquals(2, week16.size)
        val clusters = ConflictLayoutEngine.findClusters(week16)
        assertNull(
            "week16: lab rows 1-4 / 6-9 don't overlap nodes — no cluster",
            clusters.firstOrNull { cl -> cl.courses.any { it.id == 4L } }
        )
    }
}
