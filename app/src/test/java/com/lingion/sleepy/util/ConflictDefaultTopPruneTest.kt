package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v7.10.16p — 删课后的置顶偏好与冲突簇重算(用户 2026-09-03 报障):
 *   a) 删掉冲突课之一后, 网格冲突组要立即按剩余课重算(Room Flow 已保证 courses
 *      重放; 真正的残留是【置顶偏好】指向已删课)。
 *   b) 已删课的 repId 残留在 defaultTopMap → 簇里已无该 id, 消费方拿它当
 *      topOverrideId 查不到图层, 回落系统默认 — 表面"不生效";
 *      更糟: 详情弹窗把已删课当图层画出幽灵选项。
 *
 * 规则:
 *   1. 指向【已不存在课程 id】的键 → 删
 *   2. 指向【当前不再成簇的课程】的键 → 删(删课可能让簇解体/重排)
 *   3. 键本身派生自锚课三元组; 锚课被删 → 键在剩余课里算不出来 → 删
 *   4. 仍指向现存簇现存课的有效键 → 保留
 */
class ConflictDefaultTopPruneTest {

    private fun course(
        id: Long,
        day: Int,
        startNode: Int,
        step: Int,
        courseName: String = "课程"
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

    // ---- 规则 1+4: repId 已删的键删, 有效键保留 ----

    @Test
    fun prune_removes_entries_pointing_to_deleted_courses() {
        val a = course(1, 1, 1, 3)
        val b = course(2, 1, 1, 3) // 与 a 完全重叠成簇
        val stored = mapOf("1:1:3" to 2L) // 用户置顶了课2

        // 删了课2 → 键指向不存在的课 → 必须清
        val pruned = ConflictLayoutEngine.pruneConflictDefaultTop(stored, listOf(a))
        assertFalse("entry pointing to deleted course 2 must be pruned", pruned.containsKey("1:1:3"))

        // 没删 → 原样保留
        val intact = ConflictLayoutEngine.pruneConflictDefaultTop(stored, listOf(a, b))
        assertEquals(2L, intact["1:1:3"])
    }

    // ---- 规则 2: 删课后簇解体, 存的键也要跟着消失 ----

    @Test
    fun prune_removes_entries_when_cluster_dissolves() {
        val a = course(1, 1, 1, 3)
        val b = course(2, 1, 2, 4)
        val c = course(3, 1, 5, 2) // 与 a/b 无交集, 不入簇
        // 簇首 = primaryComparator 首位(step 降) = b(step4) → 键 "1:2:4"
        val stored = mapOf("1:2:4" to 1L, "1:5:2" to 3L)

        // a/b 仍相交成簇(键 1:2:4 有效); c 单课不成簇 → 它的键必被清
        val kept = ConflictLayoutEngine.pruneConflictDefaultTop(stored, listOf(a, b, c))
        assertEquals(1L, kept["1:2:4"])
        assertFalse("single course has no cluster — entry must go", kept.containsKey("1:5:2"))
    }

    // ---- 规则 3: 锚课被删, 键漂移 — 旧键换算不出 → 清 ----

    @Test
    fun prune_drops_stale_keys_whose_anchor_no_longer_exists() {
        val anchor = course(1, 2, 3, 2) // 锚课(主序首位, 生成键 2:3:2)
        val other = course(2, 2, 3, 3)  // 与锚重叠
        val stored = mapOf("2:3:2" to 2L)

        // 删锚课(1) → 剩余课重算出的簇键不再是 2:3:2(anchor 变成别的课/或键全变)
        // 无论哪种, 旧键在现存键集合里查无 → 清
        val pruned = ConflictLayoutEngine.pruneConflictDefaultTop(stored, listOf(other))
        assertFalse("stale key must be pruned when anchor deleted", pruned.containsKey("2:3:2"))
    }

    // ---- 空输入防御 ----

    @Test
    fun prune_empty_inputs_are_safe() {
        val some = mapOf("1:1:3" to 1L)
        assertTrue(ConflictLayoutEngine.pruneConflictDefaultTop(some, emptyList()).isEmpty())
        assertTrue(ConflictLayoutEngine.pruneConflictDefaultTop(emptyMap(), listOf(course(1, 1, 1, 2))).isEmpty())
    }

    // ---- 簇键公式真值: 与 UI 两处手写公式一致(防漂移) ----

    @Test
    fun cluster_key_formula_matches_ui_string_template() {
        val anchor = course(7, 3, 5, 4)
        assertEquals("3:5:4", ConflictLayoutEngine.conflictClusterKey(anchor))
        // keyOf 派生自 findClusters 输出的簇首课 — 锚定序重排后公式仍自洽
        val a = course(1, 1, 1, 3)
        val b = course(2, 1, 1, 3)
        val clusters = ConflictLayoutEngine.findClusters(listOf(a, b))
        assertEquals(
            ConflictLayoutEngine.conflictClusterKey(clusters.first().courses.first()),
            ConflictLayoutEngine.conflictClusterKey(clusters.first())
        )
    }
}
