package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.component.CourseDrawItem
import com.lingion.sleepy.ui.component.layoutFor
import com.lingion.sleepy.ui.component.markHitArea
import com.lingion.sleepy.ui.component.overlayMarkOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ConflictLayoutEngine 纯 JVM 单测（Task 1：聚簇 + 主课判定；Task 2：零露出 + 标记归属 + 变体分配；
 * Task 4：layoutFor 引擎封装——UI 渲染层的唯一入口）。
 *
 * Task 1 覆盖五块:
 *   1. 聚簇传递闭包 — 同天共享节次即归一簇,链式相邻亦传播
 *   2. 不相交不聚簇 — 同天不重叠 / 跨天皆不成簇
 *   3. 单课不成簇 — size<2 的簇不返回
 *   4. 输出顺序 — 簇间 day 升序
 *   5. 主课判定 — step 降 > startNode 升 > id 升 三分量 tie-break
 *
 * Task 2 覆盖七场景（layoutCluster）:
 *   1. 完全重叠两课 — hidden=true,variant 按 style 出 STACK/FOLD/RAIL
 *   2. 同起不同止 — 短课区间必含于长课区间 → 零露出 hidden=true(语义推导见测试注释)
 *   3. 完全包含 — 1-5 内嵌 2-3,内嵌课 hidden=true
 *   4. 梯形 1-3/2-4/3-5 — 全部有独占节次,hidden=false,variant=NONE
 *   5. topOverrideId — 翻转 z 序并重算 hidden;未命中回落主课判定序
 *   6. N≥3 stack→FOLD 合流
 *   7. RAIL 变体值与簇大小无关 — N=2 单轨 / N≥3 分段是 UI 职责
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

    // ============================ layoutCluster (Task 2) ============================
    //
    // 露出定义: 课 X 的节点区间减去所有 z 序高于 X 的课覆盖区间并集后的剩余节点集。
    // hidden = 露出集为空;顶层课(zRank 0)永不为 hidden。
    // zRank: 0=顶层=主课判定序第一位,除非 topOverrideId 命中某课 id。
    // 变体: 隐藏课统一按 style 分配(stack+2课=STACK,stack+N≥3=FOLD,fold/rail 直配);
    //       非隐藏课(含顶层)一律 NONE。

    private fun layoutById(
        courses: List<CourseEntity>,
        style: String,
        topOverrideId: Long? = null,
        maxNode: Int? = null
    ): Map<Long, LaidOutCourse> =
        ConflictLayoutEngine.layoutCluster(
            ConflictCluster(day = courses.first().day, courses = courses),
            style,
            topOverrideId,
            maxNode
        ).associateBy { it.course.id }

    @Test
    fun layout_fully_overlapping_pair_hidden_true_variants_per_style() {
        // 完全重叠两课: 1-3 与 1-3(step 降 > id 升 → id=1 顶层)
        // 短判长: 次课区间 ⊆ 顶层覆盖 → 零露出 hidden=true
        val top = course(id = 1, day = 1, startNode = 1, step = 3)
        val under = course(id = 2, day = 1, startNode = 1, step = 3)
        val cluster = ConflictCluster(1, listOf(top, under))

        assertEquals(ConflictVariant.STACK, layoutById(listOf(top, under), "stack").getValue(2L).variant)
        assertEquals(ConflictVariant.FOLD, layoutById(listOf(top, under), "fold").getValue(2L).variant)
        assertEquals(ConflictVariant.RAIL, layoutById(listOf(top, under), "rail").getValue(2L).variant)

        // 三种 style 下,顶层的 zRank/hidden/variant 一致;次课 zRank=1 且 hidden
        for (style in listOf("stack", "fold", "rail")) {
            val byId = layoutById(listOf(top, under), style)
            assertEquals(LaidOutCourse(top, 0, false, ConflictVariant.NONE), byId.getValue(1L))
            assertEquals(LaidOutCourse(under, 1, true, variantFor(style, n = 2)), byId.getValue(2L))
        }
    }

    @Test
    fun layout_same_start_different_end_short_course_zero_exposure_hidden() {
        // 同起不同止: 1-4 与 1-3。同起必包含(1-3 ⊆ 1-4),主课判定序 step 降 → 1-4 顶层,
        // 短课露出集 = {1,2,3} − {1,2,3,4} = ∅ → hidden=true(非 brief 注释里的 false;
        // 以语义精确定义为准,短课拿变体标记才能保持可见可达,符合设计文档 §2.3/§9.1)
        val long1to4 = course(id = 1, day = 1, startNode = 1, step = 4)
        val short1to3 = course(id = 2, day = 1, startNode = 1, step = 3)
        val byId = layoutById(listOf(long1to4, short1to3), "rail")
        assertEquals(0, byId.getValue(1L).zRank)
        assertEquals(false, byId.getValue(1L).hidden)
        assertEquals(1, byId.getValue(2L).zRank)
        assertEquals(true, byId.getValue(2L).hidden)
        assertEquals(ConflictVariant.RAIL, byId.getValue(2L).variant)
        assertEquals(ConflictVariant.NONE, byId.getValue(1L).variant)
    }

    @Test
    fun layout_fully_contained_inner_course_hidden() {
        // 完全包含: 1-5(step5) 内嵌 2-3(step2)。主课判定序 step 降 → 1-5 顶层,
        // 2-3 露出集 = {2,3} − {1..5} = ∅ → hidden=true
        val outer = course(id = 1, day = 2, startNode = 1, step = 5)
        val inner = course(id = 2, day = 2, startNode = 2, step = 2)
        val byId = layoutById(listOf(outer, inner), "fold")
        assertEquals(LaidOutCourse(outer, 0, false, ConflictVariant.NONE), byId.getValue(1L))
        assertEquals(LaidOutCourse(inner, 1, true, ConflictVariant.FOLD), byId.getValue(2L))
    }

    @Test
    fun layout_trapezoid_all_courses_have_exposure() {
        // 梯形 1-3 / 2-4 / 3-5(step 全 2,startNode 1/2/3):
        // 主课判定序 = startNode 升 → 1-3 顶层,2-4 次层,3-5 底层。
        //   2-4 露出集 = {2,3,4} − {1,2,3} = {4} 非空
        //   3-5 露出集 = {3,4,5} − ({1,2,3} ∪ {2,3,4}) = {5} 非空
        // → 三课全部 hidden=false,variant=NONE
        val a13 = course(id = 1, day = 3, startNode = 1, step = 2)
        val b24 = course(id = 2, day = 3, startNode = 2, step = 2)
        val c35 = course(id = 3, day = 3, startNode = 3, step = 2)
        val laid = ConflictLayoutEngine.layoutCluster(ConflictCluster(3, listOf(a13, b24, c35)), "stack")
        assertEquals(
            listOf(
                LaidOutCourse(a13, 0, false, ConflictVariant.NONE),
                LaidOutCourse(b24, 1, false, ConflictVariant.NONE),
                LaidOutCourse(c35, 2, false, ConflictVariant.NONE)
            ),
            laid
        )
    }

    @Test
    fun layout_topOverrideId_flips_z_order_and_recomputes_hidden() {
        // topOverrideId 翻转: 1-5(step5,默认顶层)与 2-6(step5,startNode 大,id 大)。
        // override=2 → id=2 升顶层,id=1 降为底层;
        //   id=1 露出集 = {1..5} − {2..6} = {1} 仍非空(部分重叠互不包含)
        // 同段完全重叠翻转: 1-3 与 1-3,override=id2 → id1 变 hidden=true,variant 出 STACK
        val a = course(id = 1, day = 1, startNode = 1, step = 5)
        val b = course(id = 2, day = 1, startNode = 2, step = 5)
        val flipped = layoutById(listOf(a, b), "stack", topOverrideId = 2L)
        assertEquals(0, flipped.getValue(2L).zRank)
        assertEquals(false, flipped.getValue(2L).hidden)
        assertEquals(1, flipped.getValue(1L).zRank)
        assertEquals(false, flipped.getValue(1L).hidden) // 独占节 1 仍露出

        val t = course(id = 1, day = 2, startNode = 1, step = 3)
        val u = course(id = 2, day = 2, startNode = 1, step = 3)
        val sameRangeFlipped = layoutById(listOf(t, u), "stack", topOverrideId = 2L)
        assertEquals(0, sameRangeFlipped.getValue(2L).zRank)
        assertEquals(1, sameRangeFlipped.getValue(1L).zRank)
        assertEquals(true, sameRangeFlipped.getValue(1L).hidden) // 被压下后零露出
        assertEquals(ConflictVariant.STACK, sameRangeFlipped.getValue(1L).variant)
        assertEquals(ConflictVariant.NONE, sameRangeFlipped.getValue(2L).variant)
    }

    @Test
    fun layout_topOverrideId_miss_falls_back_to_primary_order() {
        // override id 不在簇内 → 回落主课判定序,行为与不传相同
        val top = course(id = 1, day = 1, startNode = 1, step = 3)
        val under = course(id = 2, day = 1, startNode = 1, step = 3)
        val byId = layoutById(listOf(top, under), "rail", topOverrideId = 999L)
        assertEquals(0, byId.getValue(1L).zRank)
        assertEquals(true, byId.getValue(2L).hidden)
        assertEquals(ConflictVariant.RAIL, byId.getValue(2L).variant)
    }

    @Test
    fun layout_three_courses_stack_converges_to_fold() {
        // N≥3 stack → FOLD 合流(A 合流): 1-3 与 1-3 与 1-3(step 降 > id 升)
        //   id=2 露出集 = {1,2,3} − {1,2,3}(id=1 覆盖) = ∅ → hidden
        //   id=3 露出集 = {1,2,3} − ({1,2,3} ∪ {1,2,3}) = ∅ → hidden
        val a = course(id = 1, day = 1, startNode = 1, step = 3)
        val b = course(id = 2, day = 1, startNode = 1, step = 3)
        val c = course(id = 3, day = 1, startNode = 1, step = 3)
        val byId = layoutById(listOf(a, b, c), "stack")
        assertEquals(LaidOutCourse(a, 0, false, ConflictVariant.NONE), byId.getValue(1L))
        assertEquals(LaidOutCourse(b, 1, true, ConflictVariant.FOLD), byId.getValue(2L))
        assertEquals(LaidOutCourse(c, 2, true, ConflictVariant.FOLD), byId.getValue(3L))
    }

    @Test
    fun layout_rail_variant_independent_of_cluster_size() {
        // N=2 RAIL 单轨 / N≥3 RAIL 分段 —— 分段数由 UI 读簇大小,引擎层 variant 值恒为 RAIL
        val two = listOf(
            course(id = 1, day = 1, startNode = 1, step = 3),
            course(id = 2, day = 1, startNode = 1, step = 3)
        )
        assertEquals(ConflictVariant.RAIL, layoutById(two, "rail").getValue(2L).variant)

        val three = two + course(id = 3, day = 1, startNode = 1, step = 3)
        assertEquals(ConflictVariant.RAIL, layoutById(three, "rail").getValue(2L).variant)
        assertEquals(ConflictVariant.RAIL, layoutById(three, "rail").getValue(3L).variant)
    }

    @Test
    fun layout_output_preserves_primary_order_with_override_last() {
        // 输出顺序: 簇内主课判定序;topOverrideId 命中时该课提到 zRank 0,其余保持相对顺序
        val a = course(id = 1, day = 1, startNode = 1, step = 3)
        val b = course(id = 2, day = 1, startNode = 1, step = 3)
        val c = course(id = 3, day = 1, startNode = 1, step = 3)
        // 默认: a(顶层) > b > c
        assertEquals(listOf(a, b, c), ConflictLayoutEngine.layoutCluster(ConflictCluster(1, listOf(c, a, b)), "rail").map { it.course })
        // override=c: c 提顶,a/b 保相对顺序
        assertEquals(
            listOf(c, a, b),
            ConflictLayoutEngine.layoutCluster(ConflictCluster(1, listOf(c, a, b)), "rail", topOverrideId = 3L).map { it.course }
        )
    }

    /** stack 变体在 N=2/N≥3 下的期望值(测试内共享的小映射,避免魔数散落)。 */
    private fun variantFor(style: String, n: Int): ConflictVariant = when {
        style == "fold" -> ConflictVariant.FOLD
        style == "rail" -> ConflictVariant.RAIL
        n >= 3 -> ConflictVariant.FOLD
        else -> ConflictVariant.STACK
    }

    // ============================ layoutCluster maxNode 裁剪 (final fix wave) ============================
    //
    // Important 修复: hidden 必须与 UI 的 maxNode 裁剪空间一致。UI 渲染前把每课区间
    // clamp 进 [1, maxNode](startNode ∈ 1..maxNode,step 截到不越界),引擎若在未裁剪
    // 节点空间算露出,会出现「引擎判非 hidden(界外节 13 独占)但 UI 裁剪后零视觉零
    // tap 无标记」的不可达课(复现 issue#10 形态)。maxNode=null 时行为与不裁剪完全一致。

    @Test
    fun layoutCluster_maxNode_clamps_exposure_space_out_of_grid_tail_hidden() {
        // 复现场景: maxNode=12 的表,课 Y=10-12(step3)、课 X=11-13(step3)。
        // 主课判定序 step 降 → startNode 升 → Y 顶层(zRank0)。
        // 未裁剪空间: X 露出集 = {11,12,13} − {10,11,12} = {13} 非空 → 旧逻辑 hidden=false(缺标记)。
        // 裁剪空间 [1,12]: X 裁为 11-12,X 露出集 = {11,12} − {10,11,12} = ∅ → hidden=true,拿标记。
        val y = course(id = 1, day = 1, startNode = 10, step = 3)
        val x = course(id = 2, day = 1, startNode = 11, step = 3)
        val byId = layoutById(listOf(y, x), "rail", maxNode = 12)
        assertEquals(0, byId.getValue(1L).zRank)
        assertEquals(false, byId.getValue(1L).hidden)
        assertEquals(true, byId.getValue(2L).hidden)
        assertEquals(ConflictVariant.RAIL, byId.getValue(2L).variant)
    }

    @Test
    fun layoutCluster_maxNode_mixed_groups_in_one_cluster() {
        // spec §7「同簇多组混合」: 同天一簇内两个完全同段组——1-2 与 1-2 与 3-4 与 3-4。
        // 主课判定序: step 同 → startNode 升 → id 升 → 顶层=id1(1-2),z 序 1,2,3,4。
        //   id=2 露出集 = {1,2} − {1,2} = ∅ → hidden
        //   id=3(3-4) 露出集 = {3,4} − {1,2} = {3,4} 非空 → 非 hidden(第二组顶层)
        //   id=4 露出集 = {3,4} − ({1,2} ∪ {3,4}) = ∅ → hidden
        // variant: stack 样式但簇 N=4 ≥3 → 按既有合流规则 hidden 课一律 FOLD(spec §3)
        val a1 = course(id = 1, day = 1, startNode = 1, step = 2)
        val a2 = course(id = 2, day = 1, startNode = 1, step = 2)
        val b1 = course(id = 3, day = 1, startNode = 3, step = 2)
        val b2 = course(id = 4, day = 1, startNode = 3, step = 2)
        val byId = layoutById(listOf(a1, a2, b1, b2), "stack")
        assertEquals(LaidOutCourse(a1, 0, false, ConflictVariant.NONE), byId.getValue(1L))
        assertEquals(LaidOutCourse(a2, 1, true, ConflictVariant.FOLD), byId.getValue(2L))
        assertEquals(LaidOutCourse(b1, 2, false, ConflictVariant.NONE), byId.getValue(3L))
        assertEquals(LaidOutCourse(b2, 3, true, ConflictVariant.FOLD), byId.getValue(4L))
    }

    // ============================ layoutFor (Task 4) ============================
    //
    // layoutFor = UI 层唯一入口: findClusters(仅 size≥2)后逐簇 layoutCluster 展开,
    // 返回展平的 List<LaidOutCourse>(只含簇内课,不含单课/无冲突课)。

    @Test
    fun layoutFor_multi_cluster_input_flattens_all_cluster_courses() {
        // 两天各一簇(2 课 + 3 课) → 展平输出 5 门课,单日无关课不出现
        val d1a = course(id = 1, day = 1, startNode = 1, step = 2)
        val d1b = course(id = 2, day = 1, startNode = 2, step = 2)
        val d3a = course(id = 3, day = 3, startNode = 1, step = 3)
        val d3b = course(id = 4, day = 3, startNode = 1, step = 3)
        val d3c = course(id = 5, day = 3, startNode = 1, step = 3)
        val solo = course(id = 9, day = 2, startNode = 1, step = 2) // 无冲突,不参与
        val laid = layoutFor(listOf(d1a, d1b, d3a, d3b, d3c, solo), "stack", null)

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), laid.map { it.course.id })
        // 簇间 day 升序: day1 簇在前,day3 簇在后;簇内主课判定序
        assertEquals(listOf(d1a, d1b, d3a, d3b, d3c), laid.map { it.course })
    }

    @Test
    fun layoutFor_zRank_restarts_from_zero_per_cluster() {
        // zRank 按簇独立: day1 簇 0..1,day3 簇重新从 0 起(展平后不连续,但簇内连续)
        val d1a = course(id = 1, day = 1, startNode = 1, step = 2)
        val d1b = course(id = 2, day = 1, startNode = 2, step = 2)
        val d3a = course(id = 3, day = 3, startNode = 1, step = 3)
        val d3b = course(id = 4, day = 3, startNode = 1, step = 3)
        val laid = layoutFor(listOf(d1a, d1b, d3a, d3b), "stack", null)
        assertEquals(listOf(0, 1, 0, 1), laid.map { it.zRank })
    }

    @Test
    fun layoutFor_topOverrideId_applies_to_target_cluster_only() {
        // override=id4 → 仅 day3 簇翻转(id4 升顶层),day1 簇不受影响
        val d1a = course(id = 1, day = 1, startNode = 1, step = 2)
        val d1b = course(id = 2, day = 1, startNode = 2, step = 2)
        val d3a = course(id = 3, day = 3, startNode = 1, step = 3)
        val d3b = course(id = 4, day = 3, startNode = 1, step = 3)
        val laid = layoutFor(listOf(d1a, d1b, d3a, d3b), "stack", topOverrideId = 4L)
        // day1 簇保持主课判定序
        assertEquals(listOf(d1a, d1b), laid.take(2).map { it.course })
        assertEquals(listOf(0, 1), laid.take(2).map { it.zRank })
        // day3 簇翻转: id4 顶层,id3 降底层且被完全覆盖 → hidden
        assertEquals(listOf(d3b, d3a), laid.drop(2).map { it.course })
        assertEquals(listOf(0, 1), laid.drop(2).map { it.zRank })
        assertEquals(false, laid[2].hidden)
        assertEquals(true, laid[3].hidden)
        assertEquals(ConflictVariant.STACK, laid[3].variant)
    }

    @Test
    fun layoutFor_no_conflicts_returns_empty() {
        // 无冲突输入(单课/不相交/空表) → 空输出
        val a = course(id = 1, day = 1, startNode = 1, step = 2)
        val b = course(id = 2, day = 1, startNode = 3, step = 2)
        assertEquals(emptyList<LaidOutCourse>(), layoutFor(listOf(a, b), "rail", null))
        assertEquals(emptyList<LaidOutCourse>(), layoutFor(emptyList(), "rail", null))
    }

    @Test
    fun layoutFor_style_propagates_to_hidden_variants() {
        // style 直通 layoutCluster: rail 下 hidden 课拿 RAIL,fold 下拿 FOLD
        val a = course(id = 1, day = 1, startNode = 1, step = 3)
        val b = course(id = 2, day = 1, startNode = 1, step = 3)
        val byStyle: (String) -> ConflictVariant = { style ->
            layoutFor(listOf(a, b), style, null).first { it.course.id == 2L }.variant
        }
        assertEquals(ConflictVariant.RAIL, byStyle("rail"))
        assertEquals(ConflictVariant.FOLD, byStyle("fold"))
    }

    // ============================ overlayMarkOrder (Task 4 fix round 1) ============================
    //
    // 变体标记必须在顶层卡之后绘制(overlay 层)——hidden 课被顶层完全覆盖,
    // 标记画在它自己层会被顶层卡背景盖住,永不可见(评审 Critical)。
    // 断言用「类型@课id」字符串序列,与 variant 具体值解耦(变体值已有 Task 2 测试覆盖)。

    /** CourseDrawItem 序列 → 可读断言形式: "Card:2" / "Mark:3" */
    private fun drawOrderIds(items: List<CourseDrawItem>): List<String> = items.map {
        when (it) {
            is CourseDrawItem.Card -> "Card:${it.laid.course.id}"
            is CourseDrawItem.Mark -> "Mark:${it.hiddenCourseId}"
        }
    }

    @Test
    fun overlayMarkOrder_marks_come_after_top_card_in_draw_order() {
        // 完全重叠两课: 绘制序 = 非顶层课卡(id=2) → 顶层课卡(id=1) → overlay 标记(hiddenId=2)
        val a = course(id = 1, day = 1, startNode = 1, step = 3)
        val b = course(id = 2, day = 1, startNode = 1, step = 3)
        val order = overlayMarkOrder(layoutFor(listOf(a, b), "stack", null))
        assertEquals(listOf("Card:2", "Card:1", "Mark:2"), drawOrderIds(order))
    }

    @Test
    fun overlayMarkOrder_all_hidden_marks_appended_and_pickable() {
        // N=3 全重叠: 两个 hidden 课(2,3)各出一个 overlay 标记(zRank 升序),均可点
        val a = course(id = 1, day = 1, startNode = 1, step = 3)
        val b = course(id = 2, day = 1, startNode = 1, step = 3)
        val c = course(id = 3, day = 1, startNode = 1, step = 3)
        val order = overlayMarkOrder(layoutFor(listOf(a, b, c), "rail", null))
        assertEquals(listOf("Card:3", "Card:2", "Card:1", "Mark:2", "Mark:3"), drawOrderIds(order))
    }

    @Test
    fun overlayMarkOrder_no_hidden_only_cards_and_top_last() {
        // 梯形 1-3/2-4/3-5: 全部有露出,无 hidden → 无 overlay 标记;
        // 绘制序 = zRank 降序(3-5 → 2-4 → 1-3 顶层最后)
        val a13 = course(id = 1, day = 3, startNode = 1, step = 2)
        val b24 = course(id = 2, day = 3, startNode = 2, step = 2)
        val c35 = course(id = 3, day = 3, startNode = 3, step = 2)
        val order = overlayMarkOrder(layoutFor(listOf(a13, b24, c35), "stack", null))
        assertEquals(listOf("Card:3", "Card:2", "Card:1"), drawOrderIds(order))
    }

    @Test
    fun overlayMarkOrder_override_moves_hidden_mark_with_course() {
        // override 翻转后 hidden 归属重算: 完全重叠两课 override=id2 → id1 变 hidden,
        // 标记跟课走(Mark:1),id2 升顶层
        val a = course(id = 1, day = 1, startNode = 1, step = 3)
        val b = course(id = 2, day = 1, startNode = 1, step = 3)
        val order = overlayMarkOrder(layoutFor(listOf(a, b), "fold", topOverrideId = 2L))
        assertEquals(listOf("Card:1", "Card:2", "Mark:1"), drawOrderIds(order))
    }

    @Test
    fun overlayMarkOrder_out_of_grid_top_falls_back_to_first_visible() {
        // Important-2 回归: 簇内 zRank 0 课是出界课(startNode > maxNode)被 UI 过滤后,
        // 输入列表无 zRank 0 → 不再 return emptyList,兜底取列表首位(first)保渲染保点击。
        // 模拟过滤: 手工构造无 zRank 0 的 laid 列表(id=9 出界主课不在其中)
        val visible = listOf(
            LaidOutCourse(course(id = 2, day = 1, startNode = 1, step = 2), 1, false, ConflictVariant.NONE),
            LaidOutCourse(course(id = 3, day = 1, startNode = 2, step = 2), 2, false, ConflictVariant.NONE)
        )
        val order = overlayMarkOrder(visible)
        // 兜底: 首位(id=2)当顶层卡渲染,zRank 降序不变
        assertEquals(listOf("Card:3", "Card:2"), drawOrderIds(order))
    }

    @Test
    fun overlayMarkOrder_empty_input_returns_empty() {
        // 空输入(全出界被过滤到零) → 空输出,调用方据此整簇跳过
        assertEquals(emptyList<CourseDrawItem>(), overlayMarkOrder(emptyList()))
    }

    // ============================ markHitArea (Task 4 fix round 2) ============================
    //
    // Important-1: 标记命中区必须收缩到「标记视觉区 + 12dp 内延」,不能铺满整张顶层卡,
    // 否则 hidden 存在时顶层卡 onCourseClick 全域不可达(点主体=编辑最上层,设计 §4)。

    /** Dp 二维断言辅助 */
    private fun assertRect(
        actual: Pair<Float, Float>,
        w: Float,
        h: Float,
        message: String
    ) {
        assertEquals(message, w, actual.first, 0.001f)
        assertEquals(message, h, actual.second, 0.001f)
    }

    @Test
    fun markHitArea_stack_is_bottom_edge_strip_not_full_card() {
        // STACK: 视觉区=右下 14dp 方块 + 12dp 内延 → 右下 26dp 见方,绝不等于整卡
        val (w, h) = markHitArea(ConflictVariant.STACK, cardWidth = 60f, cardHeight = 120f)
        assertRect(w to h, 26f, 26f, "STACK hit area")
    }

    @Test
    fun markHitArea_fold_is_top_corner_triangle_zone_not_full_card() {
        // FOLD: 右上 14dp 三角 + 12dp 内延 → 右上 26dp 见方
        val (w, h) = markHitArea(ConflictVariant.FOLD, cardWidth = 60f, cardHeight = 120f)
        assertRect(w to h, 26f, 26f, "FOLD hit area")
    }

    @Test
    fun markHitArea_rail_is_right_stripe_not_full_card() {
        // RAIL: 右侧 6dp 竖条 + 12dp 内延 → 宽 18dp,高=整卡高(竖条纵贯)
        val (w, h) = markHitArea(ConflictVariant.RAIL, cardWidth = 60f, cardHeight = 120f)
        assertRect(w to h, 18f, 120f, "RAIL hit area")
    }

    @Test
    fun markHitArea_never_exceeds_card_bounds_and_never_fills_card() {
        // 命中区任何变体都 < 整卡面积(小卡片时内延会被裁剪,但恒 ≤ 卡宽/卡高)
        for (variant in listOf(ConflictVariant.STACK, ConflictVariant.FOLD)) {
            val (w, h) = markHitArea(variant, cardWidth = 20f, cardHeight = 30f)
            assertTrue("w<=cardW", w <= 20f)
            assertTrue("h<=cardH", h <= 30f)
            assertTrue("not full card", w * h < 20f * 30f)
        }
    }
}
