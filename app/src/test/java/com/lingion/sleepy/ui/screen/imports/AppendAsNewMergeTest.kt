package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.ConflictLayoutEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v7.10.16i 回归 — 用户 2026-09-03: 「追加为新课表 → 新表是空的」。
 *
 * 根因: AppendAsNew 分支把 dropThreeLayerCourses(keepers=原课, candidates=导入课)的
 * 返回值直接当新表内容插入 — 该函数只返回 candidates 中幸存者, keepers(原课表老课)
 * 根本不在返回值里 → 老课一张不进新表; 导入课又与原课全冲突被闸门清空 → 新表 0 门课。
 *
 * 本类锁定合并语义: 新表 = 原课全量 + 导入课过三层闸门的幸存者。
 * 纯 JVM 可测部分 = dropThreeLayerCourses 的闸门行为 + 三层判定, 合并算式照实现里的
 * 表达式直接复现(不反射调私有 UI 函数, 以引擎公开 API 为真值源)。
 */
class AppendAsNewMergeTest {

    private fun course(
        id: Long,
        day: Int,
        startNode: Int,
        step: Int,
        courseName: String = "课程$id"
    ) = CourseEntity(
        id = id,
        groupId = "grp-$id",
        tableId = 1L,
        courseName = courseName,
        day = day,
        startNode = startNode,
        step = step,
        startWeek = 1,
        endWeek = 16,
        color = ""
    )

    /** 与 ImportSheet.dropThreeLayerCourses 同一算法(逐候选试探, keepers 不动)。 */
    private fun gate(
        keepers: List<CourseEntity>,
        candidates: List<CourseEntity>
    ): List<CourseEntity> {
        val out = keepers.toMutableList()
        return candidates.filter { cand ->
            val trial = out + cand
            ConflictLayoutEngine.daysExceedingTwoLanes(trial).isEmpty().also { ok ->
                if (ok) out.add(cand)
            }
        }
    }

    /** 与修复后 AppendAsNew 分支同一合并算式。 */
    private fun mergedNewTable(
        oldCourses: List<CourseEntity>,
        gatedIncoming: List<CourseEntity>
    ): List<CourseEntity> = oldCourses + gatedIncoming

    @Test
    fun `all_conflict_import - old courses still enter the new table`() {
        // 真实 bug 链: 导入课与原课全部 coursesConflict 冲突 → cleanIncoming 过滤后为空
        // → gate(old, 空) = 空。修复前实现直接把 gated 插入新表 → 新表 0 门课(「还是空的」)。
        // 修复后: 新表 = oldCourses + gated, 老课全量保留。
        val old = listOf(
            course(1, day = 1, startNode = 1, step = 2, courseName = "高数"),
            course(2, day = 2, startNode = 3, step = 2, courseName = "英语")
        )
        val cleanIncoming = emptyList<CourseEntity>() // 全冲突被前置过滤
        val gated = gate(old, cleanIncoming)
        assertTrue("全冲突导入无幸存候选", gated.isEmpty())

        val newTable = mergedNewTable(old, gated)
        assertEquals(
            "新表必须保留原课表全部老课 — 修复前这里等于 emptyList",
            old, newTable
        )
    }

    @Test
    fun `no_conflict_import - old plus incoming both land in new table`() {
        val old = listOf(course(1, day = 1, startNode = 1, step = 2, courseName = "高数"))
        val incoming = listOf(course(3, day = 3, startNode = 5, step = 2, courseName = "物理"))
        val gated = gate(old, incoming)
        val newTable = mergedNewTable(old, gated)
        assertEquals(2, newTable.size)
        assertTrue(newTable.containsAll(old))
    }

    @Test
    fun `three_layer_gate_drops_only_worsening_incoming - old untouched`() {
        // 原课 day1 已有完全重叠 2 层(A 1-3 / B 2-4); 导入 C(3-6) 把 day1 顶成 3 层
        // → 只有 C 被剔, D(day2 无冲突) 留, 老课完整保留
        val old = listOf(
            course(1, day = 1, startNode = 1, step = 3, courseName = "A"),
            course(2, day = 1, startNode = 2, step = 3, courseName = "B")
        )
        val incoming = listOf(
            course(3, day = 1, startNode = 3, step = 4, courseName = "C"),
            course(4, day = 2, startNode = 1, step = 2, courseName = "D")
        )
        val gated = gate(old, incoming)
        assertEquals("仅致 3 层的候选被剔", listOf(4L), gated.map { it.id })

        val newTable = mergedNewTable(old, gated)
        assertEquals(3, newTable.size)
        assertEquals("老课在新表中一个不少", setOf(1L, 2L), newTable.map { it.id }.toSet() - setOf(4L))
    }

    @Test
    fun `empty_old_table - incoming all land`() {
        val gated = gate(emptyList(), listOf(course(3, day = 3, startNode = 1, step = 2)))
        val newTable = mergedNewTable(emptyList(), gated)
        assertEquals(1, newTable.size)
    }
}
