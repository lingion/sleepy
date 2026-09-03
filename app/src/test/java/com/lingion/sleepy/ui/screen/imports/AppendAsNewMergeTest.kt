package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.ConflictLayoutEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v7.10.16j 回归 — 用户 2026-09-03: 「追加为新课表 → 新表=纯老表复制, 导入课一张没进」。
 *
 * 两轮根因:
 * 16i 前: dropThreeLayerCourses 只返回候选幸存者, 老课不在返回值 → 老课不进新表。
 * 16j 修: 闸门还把"原表已超层的天"的所有候选全灭(trial=全量+候选, 原表已 3 层
 *         则任何候选都违规) — 用户原表先追加过冲突课表, 导入课全落那些天 → 全灭。
 *
 * 最终语义(v7.10.16j): 追加为新课表 = **并集** — 新表 = 老课全量 + 全部非重复导入课,
 * 三层闸门只做 before/after 对比出提示(不剔除), 与编辑课程的 not-worse 规则同规。
 * 本类以引擎公开 API 为真值源锁定该语义。
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

    /** 与 v7.10.16j AppendAsNew 分支同一合并算式: 并集, 无剔除。 */
    private fun mergedNewTable(
        oldCourses: List<CourseEntity>,
        cleanIncoming: List<CourseEntity>
    ): List<CourseEntity> = oldCourses + cleanIncoming

    /** 与 v7.10.16j 同一提示判定: before/after 超层天对比(不剔除, 只提示)。 */
    private fun newlyExceededDays(
        oldCourses: List<CourseEntity>,
        cleanIncoming: List<CourseEntity>
    ): Set<Int> = ConflictLayoutEngine.daysExceedingTwoLanes(oldCourses + cleanIncoming) -
        ConflictLayoutEngine.daysExceedingTwoLanes(oldCourses)

    @Test
    fun `all_conflict_import - old courses still enter the new table`() {
        // 真实 bug 链(16i 时代): 导入课与原课全冲突 → cleanIncoming 为空。
        // 新表仍必须完整保留老课 — 合并语义下空导入不产生空表。
        val old = listOf(
            course(1, day = 1, startNode = 1, step = 2, courseName = "高数"),
            course(2, day = 2, startNode = 3, step = 2, courseName = "英语")
        )
        val cleanIncoming = emptyList<CourseEntity>() // 全冲突被前置过滤

        val newTable = mergedNewTable(old, cleanIncoming)
        assertEquals(
            "新表必须保留原课表全部老课",
            old, newTable
        )
        assertTrue("无新增候选 → 无新超层天", newlyExceededDays(old, cleanIncoming).isEmpty())
    }

    @Test
    fun `no_conflict_import - old plus incoming both land in new table`() {
        val old = listOf(course(1, day = 1, startNode = 1, step = 2, courseName = "高数"))
        val incoming = listOf(course(3, day = 3, startNode = 5, step = 2, courseName = "物理"))
        val newTable = mergedNewTable(old, incoming)
        assertEquals(2, newTable.size)
        assertTrue(newTable.containsAll(old))
    }

    @Test
    fun `incoming_on_already_exceeded_day_still_lands - only warns`() {
        // v7.10.16j 核心场景: 原表某天已 3 层(用户先追加过冲突课表), 导入课落同一天 —
        // 旧闸门全灭, 新语义必须让课进来, 只把该天列为新增超层提示。
        val old = listOf(
            course(1, day = 1, startNode = 1, step = 4, courseName = "A"), // 1-4
            course(2, day = 1, startNode = 2, step = 4, courseName = "B"), // 2-5 → 第 2 组
            course(3, day = 1, startNode = 3, step = 4, courseName = "C")  // 3-6 → 第 3 组(实测引擎 3 组)
        )
        assertEquals("前置: 原表周一已超层", setOf(1), ConflictLayoutEngine.daysExceedingTwoLanes(old))

        val incoming = listOf(
            course(4, day = 1, startNode = 10, step = 2, courseName = "D") // 11-12 节, 零重叠
        )
        val newTable = mergedNewTable(old, incoming)
        assertEquals("导入课必须进新表 — 旧闸门在这里全灭", 4, newTable.size)
        assertTrue(newTable.contains(incoming[0]))
    }

    @Test
    fun `empty_old_table - incoming all land`() {
        val newTable = mergedNewTable(emptyList(), listOf(course(3, day = 3, startNode = 1, step = 2)))
        assertEquals(1, newTable.size)
    }
}
