package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ConflictLayoutEngine 纯 JVM 单测（Task 1：聚簇 + 主课判定）。
 *
 * 覆盖五块:
 *   1. 聚簇传递闭包 — 同天共享节次即归一簇,链式相邻亦传播
 *   2. 不相交不聚簇 — 同天不重叠 / 跨天皆不成簇
 *   3. 单课不成簇 — size<2 的簇不返回
 *   4. 输出顺序 — 簇间 day 升序
 *   5. 主课判定 — step 降 > startNode 升 > id 升 三分量 tie-break
 *
 * fixture 与 CourseColorUtilTest.course(...) 同款:纯 JVM,无 Robolectric。
 * 判定仅消费 day / startNode / step / id,其余字段填默认值。
 */
class ConflictLayoutEngineTest {

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

    // ============================ findClusters ============================

    @Test
    fun clusters_share_node_merge_into_one_cluster() {
        // 同一天 1-2 与 2-3 共享节 2 → 归一簇;簇内按主课判定序(step 同 → startNode 升)
        val a = course(id = 1, day = 1, startNode = 1, step = 2)
        val b = course(id = 2, day = 1, startNode = 2, step = 2)
        val clusters = ConflictLayoutEngine.findClusters(listOf(a, b))
        assertEquals(1, clusters.size)
        assertEquals(1, clusters[0].day)
        assertEquals(listOf(a, b), clusters[0].courses)
    }

    @Test
    fun clusters_transitive_closure_through_chain() {
        // 传递闭包: 1-2 / 2-3 / 3-4 链式相邻共享节 → 三课同簇(尽管 1-2 与 3-4 不直接相交)
        val a = course(id = 1, day = 2, startNode = 1, step = 2)
        val b = course(id = 2, day = 2, startNode = 2, step = 2)
        val c = course(id = 3, day = 2, startNode = 3, step = 2)
        val clusters = ConflictLayoutEngine.findClusters(listOf(a, b, c))
        assertEquals(1, clusters.size)
        assertEquals(2, clusters[0].day)
        assertEquals(listOf(a, b, c), clusters[0].courses)
    }

    @Test
    fun clusters_disjoint_same_day_not_merged() {
        // 同一天 1-2 与 3-4 不相交 → 不聚簇(各自单课不成簇,返回空)
        val a = course(id = 1, day = 1, startNode = 1, step = 2)
        val b = course(id = 2, day = 1, startNode = 3, step = 2)
        assertEquals(emptyList<ConflictCluster>(), ConflictLayoutEngine.findClusters(listOf(a, b)))
    }

    @Test
    fun clusters_cross_day_never_merged() {
        // 跨天不聚簇: 节次完全相同但在不同天
        val a = course(id = 1, day = 1, startNode = 1, step = 2)
        val b = course(id = 2, day = 2, startNode = 1, step = 2)
        assertEquals(emptyList<ConflictCluster>(), ConflictLayoutEngine.findClusters(listOf(a, b)))
    }

    @Test
    fun clusters_single_course_and_empty_input_not_returned() {
        // 单课不成簇不返回; 空列表输入返回空
        assertEquals(
            emptyList<ConflictCluster>(),
            ConflictLayoutEngine.findClusters(listOf(course(1, 1, 1, 2)))
        )
        assertEquals(emptyList<ConflictCluster>(), ConflictLayoutEngine.findClusters(emptyList()))
    }

    @Test
    fun clusters_output_days_ascending() {
        // 簇间 day 升序输出,与输入顺序无关
        val d3 = course(id = 1, day = 3, startNode = 1, step = 2)
        val d3b = course(id = 2, day = 3, startNode = 2, step = 2)
        val d1 = course(id = 3, day = 1, startNode = 1, step = 2)
        val d1b = course(id = 4, day = 1, startNode = 2, step = 2)
        val clusters = ConflictLayoutEngine.findClusters(listOf(d3, d3b, d1, d1b))
        assertEquals(listOf(1, 3), clusters.map { it.day })
        assertEquals(listOf(d1, d1b), clusters[0].courses)
        assertEquals(listOf(d3, d3b), clusters[1].courses)
    }

    // ============================ primaryOrder ============================

    @Test
    fun primaryOrder_step_descending_is_first_component() {
        // 第一分量 step 降: 3 节 > 2 节 > 1 节
        val one = course(id = 1, day = 1, startNode = 1, step = 1)
        val two = course(id = 2, day = 1, startNode = 2, step = 2)
        val three = course(id = 3, day = 1, startNode = 3, step = 3)
        assertEquals(
            listOf(three, two, one),
            ConflictLayoutEngine.primaryOrder(listOf(one, three, two))
        )
    }

    @Test
    fun primaryOrder_same_step_startNode_ascending() {
        // step 相同 → startNode 升
        val late = course(id = 1, day = 1, startNode = 3, step = 2)
        val early = course(id = 2, day = 1, startNode = 1, step = 2)
        assertEquals(listOf(early, late), ConflictLayoutEngine.primaryOrder(listOf(late, early)))
    }

    @Test
    fun primaryOrder_same_step_same_startNode_id_ascending() {
        // step、startNode 皆同 → id 升
        val bigId = course(id = 9, day = 1, startNode = 1, step = 2)
        val smallId = course(id = 4, day = 1, startNode = 1, step = 2)
        assertEquals(listOf(smallId, bigId), ConflictLayoutEngine.primaryOrder(listOf(bigId, smallId)))
    }

    @Test
    fun primaryOrder_full_tie_break_chain() {
        // 三分量混合: step 降优先;同 step 内 startNode 升;startNode 亦同 → id 升
        val startLate = course(id = 5, day = 1, startNode = 4, step = 2) // step 同, startNode 大
        val idSmall = course(id = 2, day = 1, startNode = 1, step = 2)
        val idBig = course(id = 7, day = 1, startNode = 1, step = 2)
        // 期望: idSmall(id=2) < idBig(id=7) < startLate(startNode=4)
        assertEquals(
            listOf(idSmall, idBig, startLate),
            ConflictLayoutEngine.primaryOrder(listOf(startLate, idBig, idSmall))
        )
    }
}
