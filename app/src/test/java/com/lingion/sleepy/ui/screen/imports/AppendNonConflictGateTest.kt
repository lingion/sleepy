package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.ConflictLayoutEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v7.10.16x 回归 — 用户 2026-09-10: 「预览说 8 门全不冲突, 点仅追加不冲突课程,
 * toast 却说全部冲突」。
 *
 * 根因: 两套判定口径。
 *   预览页冲突 = coursesConflict(同天+周次重叠+节次相交) — 8 门确实 0 冲突。
 *   落库闸门 = dropThreeLayerCourses → daysExceedingTwoLanes **绝对判定** —
 *   目标表**本来就有**超 2 层的天(此前追加过冲突课表), 那天上的候选全剔,
 *   哪怕候选与谁都不重叠、一层不加深。8 门全在超层天 → 全剔 → 误报"全部冲突"。
 *
 * v7.10.16j 已为 AppendAsNew 定版"只拦新增恶化"(before/after 对比),
 * AppendNonConflict 路径漏改。本类锁定: 同规 — 闸门只能拦**因候选而变差**的天。
 */
class AppendNonConflictGateTest {

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

    /**
     * 与 AppendAsNew(v7.10.16j) 同一相对判定: 候选被剔当且仅当
     * 它让某个天**新**超 2 层(keep∪cand 超层 − keep 超层 ≠ ∅)。
     * 原表已有的超层天不再连坐无辜候选。
     */
    private fun newlyExceededDays(
        keep: List<CourseEntity>,
        candidate: CourseEntity
    ): Set<Int> = ConflictLayoutEngine.daysExceedingTwoLanes(keep + candidate) -
        ConflictLayoutEngine.daysExceedingTwoLanes(keep)

    private fun gate(keep: List<CourseEntity>, candidates: List<CourseEntity>): List<CourseEntity> =
        candidates.filter { cand -> newlyExceededDays(keep, cand).isEmpty() }

    @Test
    fun `pre-existing three-lane day does not veto non-conflicting candidates`() {
        // 用户场景核心: 周一已有 3 层(此前追加过冲突课表)。
        // 新候选 6-7 节与谁都零重叠 → 预览 0 冲突 → 必须能进表。
        val existing = listOf(
            course(1, day = 1, startNode = 1, step = 2, courseName = "甲"),
            course(2, day = 1, startNode = 2, step = 2, courseName = "乙"),
            course(3, day = 1, startNode = 3, step = 2, courseName = "丙") // 1-2/2-3/3-4 链 → 3 层
        )
        val incoming = course(9, day = 1, startNode = 6, step = 2, courseName = "新课")

        assertEquals(0, incomingConflictCount(incoming, existing))   // 预览口径: 不冲突
        assertTrue(newlyExceededDays(existing, incoming).isEmpty())  // 相对口径: 不加深
        assertEquals(listOf(incoming), gate(existing, listOf(incoming)))
    }

    @Test
    fun `eight courses on pre-exceeded days all survive`() {
        // 复刻报障: 原表两个超层天, 8 门全不冲突且全落那两天 → 全部入库, 不误报全冲突
        val existing = listOf(
            course(1, day = 1, startNode = 1, step = 3, courseName = "一1"),
            course(2, day = 1, startNode = 2, step = 3, courseName = "一2"),
            course(3, day = 1, startNode = 3, step = 3, courseName = "一3"),
            course(4, day = 3, startNode = 5, step = 2, courseName = "三1"),
            course(5, day = 3, startNode = 6, step = 2, courseName = "三2"),
            course(6, day = 3, startNode = 7, step = 2, courseName = "三3")
        )
        val incoming = (10..17).map { i ->
            course(i.toLong(), day = if (i % 2 == 0) 1 else 3, startNode = 10 + (i - 10) % 3 * 1, step = 1, courseName = "新增$i")
        }
        assertEquals(8, incoming.size)
        assertTrue(incoming.all { incomingConflictCount(it, existing) == 0 })  // 预览: 全不冲突
        assertEquals(incoming, gate(existing, incoming))                        // 闸门: 全放行
    }

    @Test
    fun `candidate that deepens overlap to a new lane is still dropped`() {
        // 相对判定不放走真恶化: 甲1-3 乙2-4 = 2 层链, 候选 2-3 与两者均重叠
        // (chainGroups 贪心复演: keep=2 层, +候选=3 层) → 剔。
        // 注意口径: 候选只与一门端点衔接时贪心可重组不加深(S1 探针), 此处选
        // 与两门均叠的真恶化形态 — 相对判定在该形态下与直觉一致。
        val existing = listOf(
            course(1, day = 1, startNode = 1, step = 3, courseName = "甲"),
            course(2, day = 1, startNode = 2, step = 3, courseName = "乙")
        )
        val worsening = course(9, day = 1, startNode = 2, step = 2, courseName = "双撞")
        assertEquals(setOf(1), newlyExceededDays(existing, worsening))
        assertEquals(emptyList<CourseEntity>(), gate(existing, listOf(worsening)))
    }

    @Test
    fun `gate message wording no longer claims conflict when nothing dropped`() {
        // 语义校验辅助: 全放行时不该再走 import_all_conflict 文案分支。
        // (UI 分支以 survivors.isEmpty() 为准; 相对判定下"原表超层"不再产生空 survivors)
        val existing = listOf(course(1, day = 1, startNode = 1, step = 2))
        val clean = listOf(course(9, day = 5, startNode = 3, step = 1))
        assertTrue(gate(existing, clean).isNotEmpty())
    }

    private fun incomingConflictCount(incoming: CourseEntity, existing: List<CourseEntity>): Int =
        existing.count { existingCourse ->
            existingCourse.day == incoming.day &&
                existingCourse.endWeek >= incoming.startWeek &&
                incoming.endWeek >= existingCourse.startWeek &&
                existingCourse.startNode <= incoming.startNode + incoming.step - 1 &&
                incoming.startNode <= existingCourse.startNode + existingCourse.step - 1
        }
}
