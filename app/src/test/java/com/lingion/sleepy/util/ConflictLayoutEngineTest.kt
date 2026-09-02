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
        // 视觉修订 v3: FOLD 同起点闸门 — 内嵌课起点(2)≠上层起点(1)→ 回落 STACK
        val outer = course(id = 1, day = 2, startNode = 1, step = 5)
        val inner = course(id = 2, day = 2, startNode = 2, step = 2)
        val byId = layoutById(listOf(outer, inner), "fold")
        assertEquals(LaidOutCourse(outer, 0, false, ConflictVariant.NONE), byId.getValue(1L))
        assertEquals(LaidOutCourse(inner, 1, true, ConflictVariant.STACK), byId.getValue(2L))
    }

    @Test
    fun layout_trapezoid_all_courses_have_exposure() {
        // 梯形 1-3 / 2-4 / 3-5(step 全 2,startNode 1/2/3) — v7.8 分层:
        // Layer0={1-3,3-5}(零重叠最大集), Layer1={2-4}(单独层)。
        // 主序最高课=1-3(step2,start1,id1);Layer0 top=3-5;Layer1 top=2-4。
        // sortedBy LayerSortKey: Layer1(2-4) -2,2,2 < Layer0(3-5) -2,3,3 → Layer1 先 → z0=2-4。
        // zOrder: 2-4(z0,顶层), 1-3(z1), 3-5(z2)。
        // 链组态(hasChainLayer=true)→ 所有 hidden=false;顶层链组=false(顶层是单课层)。
        val a13 = course(id = 1, day = 3, startNode = 1, step = 2)
        val b24 = course(id = 2, day = 3, startNode = 2, step = 2)
        val c35 = course(id = 3, day = 3, startNode = 3, step = 2)
        val laid = ConflictLayoutEngine.layoutCluster(ConflictCluster(3, listOf(a13, b24, c35)), "stack")
        assertEquals(
            listOf(
                LaidOutCourse(b24, 0, false, ConflictVariant.NONE, chainFront = false),
                LaidOutCourse(a13, 1, false, ConflictVariant.NONE, chainFront = false),
                LaidOutCourse(c35, 2, false, ConflictVariant.NONE, chainFront = false)
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

    // ============================ v7.9 默认置顶偏好集成 ============================
    //
    // CourseTableView 把 defaultTopMap[clusterKey] 作 topOverrideId fallback ——
    // 本节断言:把 default-top 当作 topOverrideId 喂入引擎后,引擎确实把目标图层整组提到顶层
    // (保持图层原子性,而不是只提 representative 单课)。

    @Test
    fun v79_defaultTop_lifts_chain_layer_atomically() {
        // chainGroups 划分: A=1-3, B=3-6, C=1-2 → Layer0 = {C, B}, Layer1 = {A}。
        // 注:这是按"区间图最大独立集(贪心右端点)"分的层,与"链式衔接=同层"不是一回事。
        val a = course(id = 1, day = 1, startNode = 1, step = 3, courseName = "A")
        val b = course(id = 2, day = 1, startNode = 3, step = 4, courseName = "B")
        val c = course(id = 3, day = 1, startNode = 1, step = 2, courseName = "C")

        val cluster = ConflictCluster(1, listOf(a, b, c))
        val groups = ConflictLayoutEngine.chainGroups(cluster.courses)
        assertEquals(2, groups.size)
        val cbLayer = groups.first { it.any { x -> x.id == 3L } && it.any { x -> x.id == 2L } }
        val aLayer = groups.first { it.size == 1 && it.first().id == 1L }

        // 默认序: 每图层 top = g.maxWith(primaryComparator); primaryComparator 是
        //   compareByDescending { step }.thenBy { startNode }.thenBy { id }
        // → "maxWith" = 主序最小者 = step 最小者。
        //   cbLayer 选 C(step=2), aLayer 选 A(step=3)。
        //   LayerSortKey = (-step, startNode, id): cbLayer (-2, 1, 3), aLayer (-3, 1, 1)。
        //   升序: (-3) < (-2) → aLayer 先 → zOrdered = [A] ++ [C, B by startNode] = A, C, B。
        val defaultLaid = layoutById(cluster.courses, "rail")
        assertEquals(0, defaultLaid.getValue(1L).zRank) // A 默认顶层
        assertEquals(1, defaultLaid.getValue(3L).zRank) // C 次
        assertEquals(2, defaultLaid.getValue(2L).zRank) // B 末

        // 用户保存默认置顶 = C。layerOfId[C] = cbLayer = {C, B}; 整组置顶。
        // orderedLayers = [{C, B}, {A}]
        // zOrdered = [C, B] sortedBy startNode = [C(1), B(3)] ++ [A] = C, B, A
        val pickedLaid = layoutById(cluster.courses, "rail", topOverrideId = 3L)
        assertEquals(0, pickedLaid.getValue(3L).zRank)
        assertEquals(1, pickedLaid.getValue(2L).zRank)
        assertEquals(2, pickedLaid.getValue(1L).zRank)
    }

    @Test
    fun v79_defaultTop_lifts_singleton_layer() {
        // 1-2 与 1-3 完全包含 → 两层各一课(经典叠放)。
        val outer = course(id = 10, day = 1, startNode = 1, step = 3)
        val inner = course(id = 20, day = 1, startNode = 1, step = 2)
        // 默认: maxWith 取 step 最小者 = inner; 但 LayerSortKey 是 (-step, ...);
        //   outer 顶层 = (-3, 1, 10), inner 顶层 = (-2, 1, 20)
        //   升序: outer (-3) < inner (-2) → outer 顶层 zRank=0
        val defaultLaid = layoutById(listOf(outer, inner), "rail")
        assertEquals(0, defaultLaid.getValue(10L).zRank)
        assertEquals(1, defaultLaid.getValue(20L).zRank)

        // 用户保存默认置顶 = inner
        val pickedLaid = layoutById(listOf(outer, inner), "rail", topOverrideId = 20L)
        assertEquals(0, pickedLaid.getValue(20L).zRank)
        assertEquals(1, pickedLaid.getValue(10L).zRank)
    }

    @Test
    fun v79_defaultTop_chain_rep_same_as_member_lifts_whole_layer() {
        // 用户保存的代表 id 落在图层 {C, B} 内的 B,引擎按 layerOfId 找整组提到前。
        // 实际分组 Layer0 = {C, B}, Layer1 = {A}。
        val a = course(id = 1, day = 1, startNode = 1, step = 3)
        val b = course(id = 2, day = 1, startNode = 3, step = 4) // Layer0
        val c = course(id = 3, day = 1, startNode = 1, step = 2) // Layer0
        // 用户选 B(id=2)作为代表 → Layer0 {C, B} 整组置顶。
        // zOrdered = Layer0.sortedBy startNode = [C(1), B(3)] ++ [A] = C, B, A
        val pickedLaid = layoutById(listOf(a, b, c), "rail", topOverrideId = 2L)
        assertEquals(0, pickedLaid.getValue(3L).zRank) // C 顶层(startNode 小)
        assertEquals(1, pickedLaid.getValue(2L).zRank) // B 次
        assertEquals(2, pickedLaid.getValue(1L).zRank) // A 沉底
    }

    // ============================ v7.6 图层语义 ============================

    @Test
    fun chainGroups_layer_count_group_counts_as_single_layer() {
        // v7.8 分层器: 1-3/4-6 零重叠 → Layer0={1-3,4-6};1-6(1..6) 重叠两者 → Layer1={1-6}。
        // 两图层(layerSize=[2,1])— 1-3+4-6 算一层, 1-6 单课算一层。
        // 用户 2026-09-02: 「分组之后这两节就绑定在一个图层了」— v7.6 N≥3 判定按图层数。
        val courses = listOf(
            course(id = 1, day = 1, startNode = 1, step = 3),
            course(id = 2, day = 1, startNode = 4, step = 3),
            course(id = 3, day = 1, startNode = 1, step = 6)
        )
        val groups = ConflictLayoutEngine.chainGroups(courses)
        assertEquals(listOf(2, 1), groups.map { it.size })
    }

    @Test
    fun layout_grouped_overlapper_hidden_variant_not_driven_by_raw_course_count() {
        // v7.8 链组态语义: 1-3/4-6/1-6 → Layer0={1-3,4-6}, Layer1={1-6}。
        // 链组态(hasChainLayer=true)→ 所有课 hidden=false, 沉底方渲染真卡。
        // N≥3 合流闸门不再以裸课数 = 3 触发(链组态隔离), 全员 NONE。
        val courses = listOf(
            course(id = 1, day = 1, startNode = 1, step = 3),
            course(id = 2, day = 1, startNode = 4, step = 3),
            course(id = 3, day = 1, startNode = 1, step = 6)
        )
        val byId = layoutById(courses, "stack")
        assertEquals(false, byId.getValue(3L).hidden)
        assertEquals(ConflictVariant.NONE, byId.getValue(3L).variant)
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
        // v7.8 分层: 1-2/1-2/3-4/3-4 → Layer0={id1,id3}(零重叠最大集), Layer1={id2,id4}(剩余零重叠)。
        // 链组态(hasChainLayer=true)→ 全员 hidden=false;N≥3 合流不再以裸课数触发。
        // zOrder: Layer0 top key(-2,3,3) < Layer1 top key(-2,3,4) → Layer0 置顶 → id1(z0),id3(z1),id2(z2),id4(z3)。
        // chainFront: 顶层多课层 = Layer0 = {id1,id3} → chainFront=true;沉底 Layer1 → chainFront=false。
        val a1 = course(id = 1, day = 1, startNode = 1, step = 2)
        val a2 = course(id = 2, day = 1, startNode = 1, step = 2)
        val b1 = course(id = 3, day = 1, startNode = 3, step = 2)
        val b2 = course(id = 4, day = 1, startNode = 3, step = 2)
        val byId = layoutById(listOf(a1, a2, b1, b2), "stack")
        assertEquals(LaidOutCourse(a1, 0, false, ConflictVariant.NONE, chainFront = true), byId.getValue(1L))
        assertEquals(LaidOutCourse(b1, 1, false, ConflictVariant.NONE, chainFront = true), byId.getValue(3L))
        assertEquals(LaidOutCourse(a2, 2, false, ConflictVariant.NONE, chainFront = false), byId.getValue(2L))
        assertEquals(LaidOutCourse(b2, 3, false, ConflictVariant.NONE, chainFront = false), byId.getValue(4L))
    }

    @Test
    fun layoutCluster_maxNode_tail_out_of_grid_course_produces_no_phantom_coverage() {
        // fix wave 1b: 尾向整课出界(startNode>maxNode)的课必须 clamp 为空区间——
        // coerceIn(1,maxNode) 会把它钳成 [maxNode,maxNode] 幻影区间,给界内课制造伪覆盖:
        // maxNode=12, 簇 {Z=13-15(step3), C=12-13(step2)}。主课判定序 step 降 → Z 顶层。
        // 幻影 bug 下: Z 钳成 [12,12] → C 露出集={12}−{12}=∅ → C 伪 hidden=true(UI 渲染 C
        // 却多一枚伪标记,onPickTop 后标记凭空消失)。交集语义下: Z=EMPTY 不产生覆盖,
        // Z 自身区间空 → Z hidden=true(出界课不渲染,标记派生自 drawList 无锚定风险);
        // C 露出集={12} 非空 → hidden=false。
        val z = course(id = 1, day = 1, startNode = 13, step = 3)
        val c = course(id = 2, day = 1, startNode = 12, step = 2)
        val byId = layoutById(listOf(z, c), "rail", maxNode = 12)
        assertEquals(true, byId.getValue(1L).hidden)
        assertEquals(false, byId.getValue(2L).hidden)
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
        // v7.8 梯形 1-3/2-4/3-5: Layer0={1-3,3-5} 零重叠 → 同层;Layer1={2-4} 单独层。
        // 链组态下所有课 hidden=false, 绘制序 = 全部 Card, 无 Mark。
        // zOrder: 2-4(z0,顶层)=>1-3(z1)=>3-5(z2)。overlayMarkOrder = 非顶卡按 zRank desc + 顶卡最后:
        //   [Card:3(z2), Card:1(z1), Card:2(z0)]
        val a13 = course(id = 1, day = 3, startNode = 1, step = 2)
        val b24 = course(id = 2, day = 3, startNode = 2, step = 2)
        val c35 = course(id = 3, day = 3, startNode = 3, step = 2)
        val order = overlayMarkOrder(layoutFor(listOf(a13, b24, c35), "stack", null))
        assertEquals(listOf("Card:3", "Card:1", "Card:2"), drawOrderIds(order))
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

    // ============================ markHitArea (Task 4 fix round 2; 视觉修订 v2 放大) ============================
    //
    // Important-1: 标记命中区必须收缩到「标记视觉区 + 内延」,不能铺满整张顶层卡,
    // 否则 hidden 存在时顶层卡 onCourseClick 全域不可达(点主体=编辑最上层,设计 §4)。
    // 视觉修订 v2(用户实测: 三种面积都过小): 视觉基准 14→16dp,内延 12→20dp。

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
        // STACK: 视觉区=右下 16dp 方块 + 20dp 内延 → 右下 36dp 见方,绝不等于整卡
        val (w, h) = markHitArea(ConflictVariant.STACK, cardWidth = 60f, cardHeight = 120f)
        assertRect(w to h, 36f, 36f, "STACK hit area")
    }

    @Test
    fun markHitArea_fold_is_top_corner_triangle_zone_not_full_card() {
        // FOLD: 右上 16dp 角 + 20dp 内延 → 右上 36dp 见方
        val (w, h) = markHitArea(ConflictVariant.FOLD, cardWidth = 60f, cardHeight = 120f)
        assertRect(w to h, 36f, 36f, "FOLD hit area")
    }

    @Test
    fun markHitArea_rail_is_same_as_stack_not_right_stripe() {
        // v7.8.4 修订: RAIL 不再有侧边竖轨结构, 命中区复用 STACK 风格 —— 36dp 见方。
        val (w, h) = markHitArea(ConflictVariant.RAIL, cardWidth = 60f, cardHeight = 120f)
        assertRect(w to h, 36f, 36f, "RAIL hit area (v7.8.4 = STACK style)")
    }

    @Test
    fun markHitArea_never_exceeds_card_bounds_and_never_fills_card() {
        // 命中区任何变体都 < 整卡面积(小卡片时内延会被裁剪,但恒 ≤ 卡宽/卡高)
        // v7.8.4: RAIL 与 STACK/FOLD 走同一命中区计算 —— 36dp 见方。
        for (variant in listOf(ConflictVariant.STACK, ConflictVariant.FOLD, ConflictVariant.RAIL)) {
            val (w, h) = markHitArea(variant, cardWidth = 20f, cardHeight = 30f)
            assertTrue("w<=cardW", w <= 20f)
            assertTrue("h<=cardH", h <= 30f)
            assertTrue("not full card", w * h < 20f * 30f)
        }
    }

    // ============================ FOLD 同起点闸门(视觉修订 v3) ============================

    @Test
    fun foldGate_same_start_keeps_fold() {
        // 同起点(1-3 与 1-3)→ FOLD 保留
        val a = course(id = 1, day = 1, startNode = 1, step = 3)
        val b = course(id = 2, day = 1, startNode = 1, step = 3)
        assertEquals(ConflictVariant.FOLD, layoutById(listOf(a, b), "fold").getValue(2L).variant)
    }

    @Test
    fun foldGate_different_start_falls_back_to_stack() {
        // 起点错位 + hidden: 顶层 2-4(step3),底层 2-4(step3, id 更大)@maxNode=3
        // → 底层界内 2..3 全被 2..3 覆盖 → hidden;若起点同(2=2)则 FOLD。
        // 再叠加 maxNode=2 裁剪: 界内两课均 2..2,同起点仍 FOLD。
        // 真正的错位构造: 顶层 2-4 与底层 3-4(id 更大),maxNode=3:
        //   primaryOrder: step 同 3 → startNode 升 → 顶层=2-4(id1),底层=3-4(id2)
        //   底层 clamp 后 3..3 ⊆ 顶层 2..3 → hidden,起点 3≠2 → STACK
        val top = course(id = 1, day = 1, startNode = 2, step = 3)
        val under = course(id = 2, day = 1, startNode = 3, step = 2)
        val byId = layoutById(listOf(top, under), "fold", maxNode = 3)
        assertEquals(0, byId.getValue(1L).zRank)
        assertEquals(true, byId.getValue(2L).hidden)
        assertEquals(ConflictVariant.STACK, byId.getValue(2L).variant)
        // 对照组: 同段完全重叠(1-3 与 1-3)同起点 → FOLD 不回落
        val sameA = course(id = 3, day = 2, startNode = 1, step = 3)
        val sameB = course(id = 4, day = 2, startNode = 1, step = 3)
        val byIdSame = layoutById(listOf(sameA, sameB), "fold")
        assertEquals(ConflictVariant.FOLD, byIdSame.getValue(4L).variant)
    }

    @Test
    fun foldGate_chain_middle_course_compares_to_immediate_above() {
        // 链式三课 1-4/1-3/1-2: id2(1-3) 同起点 → FOLD;
        // id3(1-2) 与紧邻上层 id2(1-3) 同起点 1 → 也 FOLD(紧邻比较,非与顶层)
        val a = course(id = 1, day = 1, startNode = 1, step = 4)
        val b = course(id = 2, day = 1, startNode = 1, step = 3)
        val c = course(id = 3, day = 1, startNode = 1, step = 2)
        val byId = layoutById(listOf(a, b, c), "fold")
        assertEquals(ConflictVariant.FOLD, byId.getValue(2L).variant)
        assertEquals(ConflictVariant.FOLD, byId.getValue(3L).variant)
    }

    @Test
    fun foldGate_trapezoid_partial_overlap_not_hidden_so_no_variant() {
        // 梯形 1-3/2-4: 底层有露出 → 非 hidden → 无变体(闸门只作用于 hidden 课)
        val a = course(id = 1, day = 1, startNode = 1, step = 3)
        val b = course(id = 2, day = 1, startNode = 2, step = 3)
        val byId = layoutById(listOf(a, b), "fold")
        assertEquals(false, byId.getValue(2L).hidden)
        assertEquals(ConflictVariant.NONE, byId.getValue(2L).variant)
    }

    // ==================== 链式冲突分层(v7): 端点衔接课拼成一条,包夹课单独一条 ====================

    @Test
    fun chainGroups_end_to_end_courses_share_group() {
        // 用户定版分组判据(v7.1): 同组 = 两课**零重叠**(有洞也行)。
        // A=1-2 / B=2-3 / C=3-4: A 与 C 零重叠 → 同组;B 与两者重叠 → 独立。
        val a = course(id = 1, day = 1, startNode = 1, step = 2)
        val b = course(id = 2, day = 1, startNode = 2, step = 2)
        val c = course(id = 3, day = 1, startNode = 3, step = 2)
        val groups = ConflictLayoutEngine.chainGroups(listOf(a, b, c))
        assertEquals(2, groups.size)
        val ids = groups.map { g -> g.map { it.id }.toSet() }
        assertTrue(ids.contains(setOf(1L, 3L))) // A+C 零重叠拼接
        assertTrue(ids.contains(setOf(2L)))     // B 独立
    }

    @Test
    fun chainGroups_gap_between_courses_still_one_group() {
        // 用户 v7.1 原话场景: 1-9 / 1-3 / 5-9 —— 1-3 与 5-9 零重叠且中间有洞(第4节空)→ 同组
        // (共同重叠者 1-9 在场;纯 1-3/5-9 无重叠者根本不聚簇,不会进本函数)
        val big = course(id = 3, day = 1, startNode = 1, step = 9)
        val a = course(id = 1, day = 1, startNode = 1, step = 3)
        val b = course(id = 2, day = 1, startNode = 5, step = 5)
        val groups = ConflictLayoutEngine.chainGroups(listOf(big, a, b))
        assertEquals(2, groups.size)
        val ids = groups.map { g -> g.map { it.id }.toSet() }
        assertTrue(ids.contains(setOf(1L, 2L))) // 1-3 + 5-9 隔洞同组
        assertTrue(ids.contains(setOf(3L)))     // 1-9 独立
    }

    @Test
    fun chainGroups_cross_region_courses_never_share_group() {
        // v7.8 分层器: 反复贪心最大独立集。场景: 上半 1-3/4-6/1-4 + 下半 7-9/10-12/7-12。
        // 整段按 startNode asc: 1-3(1),1-4(1),4-6(4),7-9(7),7-12(7),10-12(10)
        //   第一轮: 1-3(start=1,end=3) → 4-6(4,6) → 7-9(7,9) → 10-12(10,12) 四个零重叠
        //   第二轮: 1-4,7-12(同 start=1/7,零重叠)
        // 验证: 跨区不会再被收一组(分层自然保证), 上半组与下半组各自形成独立层
        val h1 = course(id = 1, day = 1, startNode = 1, step = 3)
        val t1 = course(id = 2, day = 1, startNode = 4, step = 3)
        val o1 = course(id = 3, day = 1, startNode = 1, step = 4)
        val h2 = course(id = 4, day = 1, startNode = 7, step = 3)
        val t2 = course(id = 5, day = 1, startNode = 10, step = 3)
        val o2 = course(id = 6, day = 1, startNode = 7, step = 6)
        val groups = ConflictLayoutEngine.chainGroups(listOf(h1, t1, o1, h2, t2, o2))
        val ids = groups.map { g -> g.map { it.id }.toSet() }
        // 跨区互不串扰通过 findClusters(同天不重叠→不聚簇)把守, chainGroups 只看区间图
        assertEquals(2, groups.size)
        // Layer0: 1-3,4-6,7-9,10-12 (四个零重叠的最大集)
        assertTrue(ids.contains(setOf(1L, 2L, 4L, 5L)))
        // Layer1: 1-4,7-12 (剩余零重叠)
        assertTrue(ids.contains(setOf(3L, 6L)))
    }

    @Test
    fun chainGroups_unrelated_zero_overlap_pair_not_grouped() {
        // v7.8 分层 = 反复贪心最大独立集: 1-3 (1..3) 与 7-9 (7..9) 零重叠 → 同层(layer0)。
        // 注意 chainGroups 现在是分层器, 不再按"共同重叠者"判组;
        // 真正的跨区串扰防御在 findClusters(同天不聚簇): 此处输入不在同一聚簇, 调用方过滤。
        val a = course(id = 1, day = 1, startNode = 1, step = 3)
        val b = course(id = 2, day = 1, startNode = 7, step = 3)
        val groups = ConflictLayoutEngine.chainGroups(listOf(a, b))
        assertEquals(1, groups.size)
        assertEquals(listOf(1L, 2L), groups[0].map { it.id })
    }

    @Test
    fun chainGroups_one_overlapper_two_disjoint_form_group() {
        // 用户案例: 1-9 / 1-3 / 5-9 → 1-3 与 5-9 零重叠同组,1-9 独立一组
        val big = course(id = 1, day = 1, startNode = 1, step = 9)
        val head = course(id = 2, day = 1, startNode = 1, step = 3)
        val tail = course(id = 3, day = 1, startNode = 5, step = 5)
        val groups = ConflictLayoutEngine.chainGroups(listOf(big, head, tail))
        assertEquals(2, groups.size)
        val ids = groups.map { g -> g.map { it.id }.toSet() }
        assertTrue(ids.contains(setOf(2L, 3L))) // head+tail 一组
        assertTrue(ids.contains(setOf(1L)))     // big 独立
    }

    @Test
    fun chainGroups_overlap_never_same_group() {
        // 1-3 与 3-5: 共享第3节 = 有重叠 → 用户明确: 拼不到一起,各自一组
        val a = course(id = 1, day = 1, startNode = 1, step = 3)
        val b = course(id = 2, day = 1, startNode = 3, step = 3)
        val groups = ConflictLayoutEngine.chainGroups(listOf(a, b))
        assertEquals(2, groups.size)
    }

    @Test
    fun chainGroups_full_overlap_separate_groups() {
        // 1-3 与 1-3 完全重叠 → 各占一组
        val a = course(id = 1, day = 1, startNode = 1, step = 3)
        val b = course(id = 2, day = 1, startNode = 1, step = 3)
        val groups = ConflictLayoutEngine.chainGroups(listOf(a, b))
        assertEquals(2, groups.size)
    }

    @Test
    fun chainGroups_hard_case_123_23_24() {
        // 硬案例 1-3 / 2-3 / 2-4: 两两直接重叠 → 每课一组(N≥3 讨论分支)
        val a = course(id = 1, day = 1, startNode = 1, step = 3)
        val b = course(id = 2, day = 1, startNode = 2, step = 2)
        val c = course(id = 3, day = 1, startNode = 2, step = 3)
        val groups = ConflictLayoutEngine.chainGroups(listOf(a, b, c))
        assertEquals(3, groups.size)
    }

    @Test
    fun chainLayering_b_group_top_shows_ac_chained() {
        // 分层验证: A=1-2 / B=2-3 / C=3-4, B 置顶 → 分组链 [A,C] 在后、[B] 在前;
        // 分组层: B 层 z=0, AC 层 z=1;层内 A、C 都可见(各自占位,衔接成一条)
        val a = course(id = 1, day = 1, startNode = 1, step = 2)
        val b = course(id = 2, day = 1, startNode = 2, step = 2)
        val c = course(id = 3, day = 1, startNode = 3, step = 2)
        val byId = layoutById(listOf(a, b, c), "stack", topOverrideId = 2L)
        assertEquals(0, byId.getValue(2L).zRank)
        assertEquals(1, byId.getValue(1L).zRank)
        assertEquals(2, byId.getValue(3L).zRank)
        // 分组语义下 AC 都不该判 hidden(链式层里它们各自露出自己区间)
        assertEquals(false, byId.getValue(1L).hidden)
        assertEquals(false, byId.getValue(3L).hidden)
    }

    // ==================== v7.2 用户实测缺陷: 默认 z 序必须走分组 ====================
    // 场景 1-3 / 4-6 / 1-4: 分组=[[1-3,4-6],[1-4]],但默认 z 序仍按 step 降把 1-4 排最前,
    // 视觉上 1-4 压顶 + 4-6 露出贴其下,1-3 全遮不可见 → 用户看到"1-4 和 4-6 拼一起"。
    // 修复: 有链组(多课组)时整组置前为默认态。

    @Test
    fun chainDefaultZ_multi_course_group_front_by_default() {
        // v7.8 默认态: 主序最高课=1-4(step4)所在图层置顶 → 1-4 z0, {1-3,4-6} 层垫后(z1/z2)。
        // 链组态(hasChainLayer=true)→ 所有课 hidden=false, 沉底方渲染真卡。
        // chainFront 仅顶层多课层成员标 true; 顶层 = {1-4} 单课 → 全员 chainFront=false。
        val head = course(id = 1, day = 1, startNode = 1, step = 3)
        val tail = course(id = 2, day = 1, startNode = 4, step = 3)
        val big = course(id = 3, day = 1, startNode = 1, step = 4)
        val byId = layoutById(listOf(head, tail, big), "stack")
        assertEquals(0, byId.getValue(3L).zRank)          // 1-4 顶层
        assertEquals(1, byId.getValue(1L).zRank)          // 1-3 沉底层
        assertEquals(2, byId.getValue(2L).zRank)          // 4-6 沉底层
        assertEquals(false, byId.getValue(1L).hidden)
        assertEquals(false, byId.getValue(2L).hidden)
        assertEquals(false, byId.getValue(3L).hidden)
        assertEquals(false, byId.getValue(1L).chainFront)
        assertEquals(false, byId.getValue(2L).chainFront)
        assertEquals(false, byId.getValue(3L).chainFront)
    }

    @Test
    fun chainFoldStyle_overlapper_variant_fold_when_chained() {
        // v7.8 链组态: 1-4 顶层(主序最高),{1-3,4-6} 沉底层;全员 hidden=false,variant=NONE。
        val head = course(id = 1, day = 1, startNode = 1, step = 3)
        val tail = course(id = 2, day = 1, startNode = 4, step = 3)
        val big = course(id = 3, day = 1, startNode = 1, step = 4)
        val byId = layoutById(listOf(head, tail, big), "fold")
        assertEquals(ConflictVariant.NONE, byId.getValue(3L).variant)
    }

    @Test
    fun chainStackStyle_overlapper_stays_stack_when_chained() {
        // v7.8 链组态: 全部 NONE, STACK 条带语义不再用于沉底卡(沉底是真卡)。
        val head = course(id = 1, day = 1, startNode = 1, step = 3)
        val tail = course(id = 2, day = 1, startNode = 4, step = 3)
        val big = course(id = 3, day = 1, startNode = 1, step = 4)
        val byId = layoutById(listOf(head, tail, big), "stack")
        assertEquals(ConflictVariant.NONE, byId.getValue(3L).variant)
    }

    @Test
    fun chainOverride_singleton_group_flips_front() {
        // v7.8 默认 1-4 已在顶(主序最高),点击 1-4 维持置顶。
        // {1-3,4-6} 链组沉底层, 全员 hidden=false。
        val head = course(id = 1, day = 1, startNode = 1, step = 3)
        val tail = course(id = 2, day = 1, startNode = 4, step = 3)
        val big = course(id = 3, day = 1, startNode = 1, step = 4)
        val byId = layoutById(listOf(head, tail, big), "fold", topOverrideId = 3L)
        assertEquals(0, byId.getValue(3L).zRank)
        assertEquals(false, byId.getValue(1L).hidden)
        assertEquals(false, byId.getValue(2L).hidden)
        assertEquals(false, byId.getValue(3L).hidden)
        assertEquals(false, byId.getValue(3L).chainFront)             // 单课组不算链前置
    }

    @Test
    fun chainGroups_user_case_1346_14() {
        // 用户实测场景分组本身: [1-3, 4-6] 一组, [1-4] 一组
        val head = course(id = 1, day = 1, startNode = 1, step = 3)
        val tail = course(id = 2, day = 1, startNode = 4, step = 3)
        val big = course(id = 3, day = 1, startNode = 1, step = 4)
        val groups = ConflictLayoutEngine.chainGroups(listOf(head, tail, big))
        assertEquals(2, groups.size)
        val ids = groups.map { g -> g.map { it.id }.toSet() }
        assertTrue(ids.contains(setOf(1L, 2L)))
        assertTrue(ids.contains(setOf(3L)))
    }

    // ==================== v7.3 用户实测缺陷: 点击链组成员必须整组前置 ====================

    @Test
    fun chainOverride_member_toggles_whole_group_front() {
        // v7.8: 点击 4-6(组合层第二成员)→ 整层 {1-3,4-6} 置顶 (z0/z1), 1-4 层沉底 z2。
        // 链组态(hasChainLayer=true)→ 全员 hidden=false;chainFront 在顶层多课层上全员为 true。
        val head = course(id = 1, day = 1, startNode = 1, step = 3)
        val tail = course(id = 2, day = 1, startNode = 4, step = 3)
        val big = course(id = 3, day = 1, startNode = 1, step = 4)
        val byId = layoutById(listOf(head, tail, big), "stack", topOverrideId = 2L)
        assertEquals(0, byId.getValue(1L).zRank)      // 1-3 层内拼接序保持
        assertEquals(1, byId.getValue(2L).zRank)      // 4-6 跟组一起前置
        assertEquals(2, byId.getValue(3L).zRank)      // 1-4 垫后
        assertEquals(false, byId.getValue(3L).hidden)
        assertEquals(false, byId.getValue(2L).hidden)
        assertEquals(true, byId.getValue(1L).chainFront)
        assertEquals(true, byId.getValue(2L).chainFront)
        assertEquals(false, byId.getValue(3L).chainFront)
    }

    @Test
    fun chainOverride_flips_between_group_and_singleton_repeatedly() {
        // v7.8 多次往返: 点 1-4(单课层)→ 1-4 仍置顶;再点 4-6(组合层)→ 组合层整体置顶。
        // 链组态 → 全员 hidden=false。
        val head = course(id = 1, day = 1, startNode = 1, step = 3)
        val tail = course(id = 2, day = 1, startNode = 4, step = 3)
        val big = course(id = 3, day = 1, startNode = 1, step = 4)

        // 第一次: 点单课层 1-4 — 1-4 已是默认顶层, 无变化
        val flip1 = layoutById(listOf(head, tail, big), "stack", topOverrideId = 3L)
        assertEquals(0, flip1.getValue(3L).zRank)
        assertEquals(false, flip1.getValue(1L).hidden)
        assertEquals(false, flip1.getValue(2L).hidden)

        // 第二次: 点 4-6 → 组合层 {1-3,4-6} 整体置顶, 1-4 层沉底
        val flip2 = layoutById(listOf(head, tail, big), "stack", topOverrideId = 2L)
        assertEquals(0, flip2.getValue(1L).zRank)
        assertEquals(1, flip2.getValue(2L).zRank)
        assertEquals(2, flip2.getValue(3L).zRank)
        assertEquals(false, flip2.getValue(3L).hidden)
        assertEquals(true, flip2.getValue(2L).chainFront)
    }

    // ==================== v7.8 图层语义(用户 2026-09-02 权威版) ====================
    // 一、分层 = 贪心最大独立集(能并排的尽量多并一层;剩下的零重叠也继续并)
    // 二、默认 z 序 = 全局主序最高课所在层置顶
    // 三、点击 = 被点课所在层整体置顶
    // 四、链组态所有课可见(hidden=false),沉底方渲染真卡,不再藏成 Mark

    @Test
    fun layering_five_courses_two_chain_layers() {
        // 用户钦定案例: 1-3/4-6/7-9/2-4/5-8 →
        //   层1 = {1-3,4-6,7-9}(最大并排集,3门), 层2 = {2-4,5-8}(剩余零重叠继续并)
        val a = course(id = 1, day = 1, startNode = 1, step = 3)
        val b = course(id = 2, day = 1, startNode = 4, step = 3)
        val c = course(id = 3, day = 1, startNode = 7, step = 3)
        val d = course(id = 4, day = 1, startNode = 2, step = 3)
        val e = course(id = 5, day = 1, startNode = 5, step = 4)
        val layers = ConflictLayoutEngine.chainGroups(listOf(a, b, c, d, e))
        assertEquals(2, layers.size)
        assertEquals(listOf(1L, 2L, 3L), layers[0].map { it.id }) // 层内 startNode 升序
        assertEquals(listOf(4L, 5L), layers[1].map { it.id })
    }

    @Test
    fun layering_1346_regresses_same_partition() {
        // 1-3/1-4/4-6 → 层1={1-3,4-6}(2门并排), 层2={1-4} — 与 v7.5 划分一致
        val head = course(id = 1, day = 1, startNode = 1, step = 3)
        val tail = course(id = 2, day = 1, startNode = 4, step = 3)
        val big = course(id = 3, day = 1, startNode = 1, step = 4)
        val layers = ConflictLayoutEngine.chainGroups(listOf(head, tail, big))
        assertEquals(2, layers.size)
        assertEquals(listOf(1L, 2L), layers[0].map { it.id })
        assertEquals(listOf(3L), layers[1].map { it.id })
    }

    @Test
    fun layering_full_overlap_degenerates_to_singletons() {
        // 三课完全重叠: 每层一门(经典叠放退化)
        val a = course(id = 1, day = 1, startNode = 1, step = 3)
        val b = course(id = 2, day = 1, startNode = 1, step = 3)
        val c = course(id = 3, day = 1, startNode = 1, step = 3)
        val layers = ConflictLayoutEngine.chainGroups(listOf(a, b, c))
        assertEquals(3, layers.size)
        assertTrue(layers.all { it.size == 1 })
    }

    @Test
    fun v78_defaultZ_top_layer_is_primary_highest_course_layer() {
        // 默认态: 全局主序最高课(1-4, 4节)所在层置顶 → 1-4 z0,
        // 组合层 {1-3,4-6} 垫后(层内 startNode 升序: 1-3 z1, 4-6 z2)
        val head = course(id = 1, day = 1, startNode = 1, step = 3)
        val tail = course(id = 2, day = 1, startNode = 4, step = 3)
        val big = course(id = 3, day = 1, startNode = 1, step = 4)
        val byId = layoutById(listOf(head, tail, big), "stack")
        assertEquals(0, byId.getValue(3L).zRank)          // 1-4 顶层
        assertEquals(1, byId.getValue(1L).zRank)          // 组合层垫后
        assertEquals(2, byId.getValue(2L).zRank)
        // v7.8 核心: 所有课可见(沉底方渲染真卡, 不藏 Mark)
        assertEquals(false, byId.getValue(1L).hidden)
        assertEquals(false, byId.getValue(2L).hidden)
        assertEquals(false, byId.getValue(3L).hidden)
    }

    @Test
    fun v78_click_member_lifts_whole_layer() {
        // 点击组合层成员 1-3 → 组合层整体置顶(1-3 z0, 4-6 z1), 1-4 层沉底 z2, 全员可见
        val head = course(id = 1, day = 1, startNode = 1, step = 3)
        val tail = course(id = 2, day = 1, startNode = 4, step = 3)
        val big = course(id = 3, day = 1, startNode = 1, step = 4)
        val byId = layoutById(listOf(head, tail, big), "stack", topOverrideId = 1L)
        assertEquals(0, byId.getValue(1L).zRank)
        assertEquals(1, byId.getValue(2L).zRank)
        assertEquals(2, byId.getValue(3L).zRank)
        assertEquals(false, byId.getValue(1L).hidden)
        assertEquals(false, byId.getValue(2L).hidden)
        assertEquals(false, byId.getValue(3L).hidden)
    }

    @Test
    fun v78_click_other_member_lifts_whole_layer() {
        // 点击组合层成员 4-6(非层首) → 同样整层置顶, 层内拼接序保持
        val head = course(id = 1, day = 1, startNode = 1, step = 3)
        val tail = course(id = 2, day = 1, startNode = 4, step = 3)
        val big = course(id = 3, day = 1, startNode = 1, step = 4)
        val byId = layoutById(listOf(head, tail, big), "stack", topOverrideId = 2L)
        assertEquals(0, byId.getValue(1L).zRank)
        assertEquals(1, byId.getValue(2L).zRank)
        assertEquals(2, byId.getValue(3L).zRank)
    }

    @Test
    fun v78_toggle_back_to_overlapper_layer() {
        // 组合层置顶后再点 1-4 → 1-4 层回顶, 组合层沉底(往返稳定)
        val head = course(id = 1, day = 1, startNode = 1, step = 3)
        val tail = course(id = 2, day = 1, startNode = 4, step = 3)
        val big = course(id = 3, day = 1, startNode = 1, step = 4)
        val byId = layoutById(listOf(head, tail, big), "stack", topOverrideId = 3L)
        assertEquals(0, byId.getValue(3L).zRank)
        assertEquals(1, byId.getValue(1L).zRank)
        assertEquals(2, byId.getValue(2L).zRank)
    }

    @Test
    fun v78_five_courses_default_and_click() {
        // 五课案例默认: 主序最高 = 5-8(step4) → 层2={2-4,5-8} 置顶(z0/z1),
        // 层1={1-3,4-6,7-9} 垫后(z2/z3/z4); 点 1-3 → 层1 置顶
        // v7.8: 层内按 startNode 升序拼接(用户权威 2026-09-02)。
        val a = course(id = 1, day = 1, startNode = 1, step = 3)
        val b = course(id = 2, day = 1, startNode = 4, step = 3)
        val c = course(id = 3, day = 1, startNode = 7, step = 3)
        val d = course(id = 4, day = 1, startNode = 2, step = 3)
        val e = course(id = 5, day = 1, startNode = 5, step = 4)
        val byId = layoutById(listOf(a, b, c, d, e), "stack")
        // 默认: 层2 置顶(层内 startNode 升序: 2-4 先, 5-8 后); 层1 垫后(1-3,4-6,7-9)
        assertEquals(listOf(4L, 5L, 1L, 2L, 3L), byId.values.sortedBy { it.zRank }.map { it.course.id })
        assertTrue(byId.values.none { it.hidden })

        val clicked = layoutById(listOf(a, b, c, d, e), "stack", topOverrideId = 1L)
        // 点 1-3 → 层1 置顶(层内 startNode 升序: 1-3,4-6,7-9); 层2 沉底(2-4,5-8)
        assertEquals(
            listOf(1L, 2L, 3L, 4L, 5L),
            clicked.values.sortedBy { it.zRank }.map { it.course.id }
        )
    }

    @Test
    fun v78_classic_full_overlap_keeps_hidden_semantics() {
        // 经典无链组(三课全叠): 现行为原样 — hidden 判定/variant 不动
        val a = course(id = 1, day = 1, startNode = 1, step = 3)
        val b = course(id = 2, day = 1, startNode = 1, step = 3)
        val c = course(id = 3, day = 1, startNode = 1, step = 3)
        val byId = layoutById(listOf(a, b, c), "stack")
        assertEquals(false, byId.getValue(1L).hidden)
        assertEquals(true, byId.getValue(2L).hidden)
        assertEquals(true, byId.getValue(3L).hidden)
        assertEquals(ConflictVariant.FOLD, byId.getValue(2L).variant) // N≥3 合流保留
    }

    @Test
    fun v78_partial_overlap_chain_all_visible() {
        // 组合层+单课层两两局部重叠(1-3/2-4 邻接挤压形态): 1-3 与 2-4 重叠(节2,3),
        // 各自成层 → 全员可见(沉底方真卡), 不再 hidden
        val a = course(id = 1, day = 1, startNode = 1, step = 2)
        val b = course(id = 2, day = 1, startNode = 2, step = 2)
        val byId = layoutById(listOf(a, b), "stack")
        assertEquals(false, byId.getValue(1L).hidden)
        assertEquals(false, byId.getValue(2L).hidden)
    }

    // =====================================================================================
    // v7.10 Feature 2 — 周视图局部栏位分割(weekLaneSegments)
    // 用户 2026-09-02 权威语义:「分栏只适用于周视图 跟网格视图无关」。
    // 1-2 / 2-3 / 3-4 三课: 2-3 与两头都冲突 → 连通冲突区域 = 节1..4;
    // 1-2 与 3-4 零重叠同栏, 2-3 独占另一栏(用户手绘: AC 一列、B 一列)。
    // =====================================================================================

    @Test
    fun v710_lane_two_courses_same_slot_split_into_two_lanes() {
        // 两门课都是 1-2 节 → 局部分栏, 各占一栏
        val a = course(id = 1, day = 1, startNode = 1, step = 2)
        val b = course(id = 2, day = 1, startNode = 1, step = 2)
        val lanes = ConflictLayoutEngine.weekLaneSegments(listOf(a, b))
        assertEquals(2, lanes.size)
        assertEquals(setOf(0, 1), lanes.map { it.lane }.toSet())
        assertEquals(2, lanes[0].laneCount)
        assertEquals(2, lanes[1].laneCount)
    }

    @Test
    fun v710_lane_chain_123_23_34_ac_share_lane_b_own_lane() {
        // 用户手绘场景: A=1-2 / B=2-3 / C=3-4。连通冲突区域 = 节1..4。
        // A 与 C 零重叠 → 同栏; B 与两者重叠 → 独占另一栏。
        val a = course(id = 1, day = 1, startNode = 1, step = 2)
        val b = course(id = 2, day = 1, startNode = 2, step = 2)
        val c = course(id = 3, day = 1, startNode = 3, step = 2)
        val lanes = ConflictLayoutEngine.weekLaneSegments(listOf(a, b, c))
        assertEquals(3, lanes.size)
        val byId = lanes.associateBy { it.course.id }
        // A、C 同栏(B 挪走后复用), B 另一栏
        assertEquals(byId.getValue(1L).lane, byId.getValue(3L).lane)
        assertTrue(byId.getValue(1L).lane != byId.getValue(2L).lane)
        // 全区域都是两栏
        assertEquals(2, byId.getValue(1L).laneCount)
        assertEquals(2, byId.getValue(2L).laneCount)
        assertEquals(2, byId.getValue(3L).laneCount)
    }

    @Test
    fun v710_lane_continuity_course_keeps_same_lane_whole_span() {
        // 连续占两节空间的课(2-3 夹在 1-2 与 3-4 之间)必须整段同栏, 不许中间换栏
        val a = course(id = 1, day = 1, startNode = 1, step = 2)
        val b = course(id = 2, day = 1, startNode = 2, step = 2)
        val c = course(id = 3, day = 1, startNode = 3, step = 2)
        val lanes = ConflictLayoutEngine.weekLaneSegments(listOf(a, b, c))
        // 每课只有一条 segment(整课一个 lane), 不拆节
        assertEquals(listOf(1L, 2L, 3L), lanes.map { it.course.id }.sorted())
        assertEquals(3, lanes.map { it.course.id }.toSet().size)
    }

    @Test
    fun v710_lane_connected_region_is_transitive_span() {
        // 1-2 / 2-3 / 3-4: 虽然节1只有 A、节4只有 C, 但传递连通 → 全区域 1..4 都按两栏渲染
        // (laneCount 覆盖整段, A 与 C 的空档不回退全宽)
        val a = course(id = 1, day = 1, startNode = 1, step = 2)
        val b = course(id = 2, day = 1, startNode = 2, step = 2)
        val c = course(id = 3, day = 1, startNode = 3, step = 2)
        val lanes = ConflictLayoutEngine.weekLaneSegments(listOf(a, b, c))
        val byId = lanes.associateBy { it.course.id }
        assertEquals(2, byId.getValue(1L).laneCount) // A 在节1也要半宽(区域连通)
        assertEquals(2, byId.getValue(3L).laneCount)
    }

    @Test
    fun v710_lane_non_conflict_course_full_width_lane_count_1() {
        // 无冲突课(与任何课不重叠)→ laneCount=1 = 全宽, lane=0
        val a = course(id = 1, day = 1, startNode = 1, step = 2)
        val solo = course(id = 9, day = 1, startNode = 5, step = 2)
        val lanes = ConflictLayoutEngine.weekLaneSegments(listOf(a, solo))
        val byId = lanes.associateBy { it.course.id }
        assertEquals(1, byId.getValue(9L).laneCount)
        assertEquals(0, byId.getValue(9L).lane)
    }

    @Test
    fun v710_lane_zero_conflict_returns_empty_for_cluster() {
        // 单课无冲突: 不产生任何分栏 segment
        val solo = course(id = 1, day = 1, startNode = 1, step = 2)
        val lanes = ConflictLayoutEngine.weekLaneSegments(listOf(solo))
        assertEquals(1, lanes.size)
        assertEquals(1, lanes[0].laneCount)
    }

    @Test
    fun v710_lane_day_scope_isolation() {
        // 跨天永不串扰: day1 的冲突不影响 day2 的课
        val a1 = course(id = 1, day = 1, startNode = 1, step = 2)
        val b1 = course(id = 2, day = 1, startNode = 1, step = 2)
        val solo2 = course(id = 9, day = 2, startNode = 1, step = 2)
        val lanes = ConflictLayoutEngine.weekLaneSegments(listOf(a1, b1, solo2))
        val byId = lanes.associateBy { it.course.id }
        assertEquals(2, byId.getValue(1L).laneCount)
        assertEquals(1, byId.getValue(9L).laneCount)
    }

    @Test
    fun v710_lane_disjoint_conflict_regions_independent_lanes() {
        // 同天两个独立冲突区域(节1-2 区 + 节7-8 区)互不共享 laneCount
        val a = course(id = 1, day = 1, startNode = 1, step = 2)
        val b = course(id = 2, day = 1, startNode = 1, step = 2)
        val x = course(id = 3, day = 1, startNode = 7, step = 2)
        val y = course(id = 4, day = 1, startNode = 7, step = 2)
        val lanes = ConflictLayoutEngine.weekLaneSegments(listOf(a, b, x, y))
        val byId = lanes.associateBy { it.course.id }
        // 两区域各自两栏; lane index 各自从 0 起
        assertEquals(2, byId.getValue(1L).laneCount)
        assertEquals(2, byId.getValue(3L).laneCount)
        assertEquals(byId.getValue(1L).lane, byId.getValue(3L).lane) // 各区域 lane0
    }

    @Test
    fun v710_lane_three_courses_two_groups_two_lanes_not_three_rows() {
        // 用户分组规则: 3 课分 2 组 = 2 栏(不是 3 栏)。{A+C} 组 + B 单 = 2 lanes
        // 三课全叠(1-3/1-3/1-3) → 各自一组 = 3 lanes
        val a = course(id = 1, day = 1, startNode = 1, step = 3)
        val b = course(id = 2, day = 1, startNode = 1, step = 3)
        val c = course(id = 3, day = 1, startNode = 1, step = 3)
        val lanes = ConflictLayoutEngine.weekLaneSegments(listOf(a, b, c))
        assertEquals(3, lanes.map { it.lane }.toSet().size)
        assertEquals(3, lanes[0].laneCount)
    }

    // ==================== v7.10.6 周视图行分组(用户真实课表 bug 修复) ====================
    // 用户 2026-09-02 报障(默认3 表): 周一 9-11 课消失 / 周二 2-4 课渲染两次。
    // 根因 = UI 层行分组用直接重叠判区域(非传递闭包) + firstOrNull 每栏只留一门课。
    // weekLaneRows 把行分组下沉引擎: mergeOverlapping 区域本身就是划分(每课恰属一区域),
    // 每行携带 laneOf 映射,栏内多门课由 UI 纵向堆叠。

    /** 用户「默认3」表 day1 七课: 1-4 / 1-3 / 4-6 / 5-7 / 7-8 / 8-9 / 9-11 — 全连通一区域。 */
    private fun userDay1() = listOf(
        course(id = 1, day = 1, startNode = 1, step = 4, courseName = "工科数学分析"),
        course(id = 2, day = 1, startNode = 1, step = 3, courseName = "大学英语"),
        course(id = 3, day = 1, startNode = 4, step = 3, courseName = "课3"),
        course(id = 4, day = 1, startNode = 5, step = 3, courseName = "课4"),
        course(id = 5, day = 1, startNode = 7, step = 2, courseName = "课5"),
        course(id = 6, day = 1, startNode = 8, step = 2, courseName = "课6"),
        course(id = 7, day = 1, startNode = 9, step = 3, courseName = "课7")
    )

    /** 用户「默认3」表 day2 三课: 1-3 / 2-4 / 4-3(step=3 → 节4..6)。 */
    private fun userDay2() = listOf(
        course(id = 8, day = 2, startNode = 1, step = 3, courseName = "课8"),
        course(id = 9, day = 2, startNode = 2, step = 3, courseName = "课9"),
        course(id = 10, day = 2, startNode = 4, step = 3, courseName = "课10")
    )

    @Test
    fun v7106_rows_day1_chain7_every_course_exactly_once() {
        // 七课链式全连通 → 恰好 1 行;每课恰出现一次(id7=9-11 不得丢失)
        val rows = ConflictLayoutEngine.weekLaneRows(userDay1())
        assertEquals(1, rows.size)
        val row = rows[0]
        assertEquals(7, row.courses.size)
        assertEquals((1L..7L).toSet(), row.courses.map { it.id }.toSet())
        assertEquals(7, row.laneOf.size)
    }

    @Test
    fun v7106_rows_day1_lane_assignment_matches_weekLaneSegments() {
        // 行内 laneOf 与 weekLaneSegments 一致: lane0={2,3,5,7} lane1={1,4,6}
        val rows = ConflictLayoutEngine.weekLaneRows(userDay1())
        val laneOf = rows[0].laneOf
        assertEquals(setOf(2L, 3L, 5L, 7L), laneOf.filterValues { it == 0 }.keys)
        assertEquals(setOf(1L, 4L, 6L), laneOf.filterValues { it == 1 }.keys)
        assertEquals(2, rows[0].laneCount)
    }

    @Test
    fun v7106_rows_day2_single_region_no_duplicate() {
        // 1-3 / 2-4 / 4-6 三课链式全连通 → 1 行 3 课;2-4(课9)恰出现一次
        val rows = ConflictLayoutEngine.weekLaneRows(userDay2())
        assertEquals(1, rows.size)
        val ids = rows[0].courses.map { it.id }
        assertEquals(listOf(8L, 9L, 10L).toSet(), ids.toSet())
        assertEquals(3, ids.size)
    }

    @Test
    fun v7106_rows_solo_course_own_row_preserves_order() {
        // 无冲突课独占行、按 startNode 升序;与冲突行穿插时按行首节点排
        val solo = course(id = 20, day = 1, startNode = 12, step = 1, courseName = "独行课")
        val rows = ConflictLayoutEngine.weekLaneRows(userDay1() + solo)
        assertEquals(2, rows.size)
        assertEquals(listOf(20L), rows[1].courses.map { it.id })
        assertEquals(1, rows[1].laneCount)
    }

    @Test
    fun v7106_rows_empty_input() {
        assertTrue(ConflictLayoutEngine.weekLaneRows(emptyList()).isEmpty())
    }

    // ==================== v7.10.8 WeekGrid 小组件冲突分栏 ====================

    @Test
    fun v7108_grid_two_overlap_split_half_width() {
        // 两课全叠 → 各占半栏: (0, 0.5) + (0.5, 0.5)
        val a = course(id = 1, day = 1, startNode = 1, step = 2)
        val b = course(id = 2, day = 1, startNode = 1, step = 2)
        val rects = ConflictLayoutEngine.gridDayLanes(listOf(a, b))
        val ra = rects.first { it.course.id == 1L }
        val rb = rects.first { it.course.id == 2L }
        assertEquals(0f, ra.laneStartFraction, 0.001f)
        assertEquals(0.5f, ra.laneWidthFraction, 0.001f)
        assertEquals(0.5f, rb.laneStartFraction, 0.001f)
        assertEquals(0.5f, rb.laneWidthFraction, 0.001f)
    }

    @Test
    fun v7108_grid_solo_course_full_width() {
        // 无冲突课独占整列宽
        val solo = course(id = 5, day = 1, startNode = 3, step = 2)
        val rects = ConflictLayoutEngine.gridDayLanes(listOf(solo))
        assertEquals(0f, rects[0].laneStartFraction, 0.001f)
        assertEquals(1f, rects[0].laneWidthFraction, 0.001f)
    }

    @Test
    fun v7108_grid_chain7_user_real_data_two_lanes() {
        // 用户真实课表 day1 七课链式区域 → 2 栏各半宽; 栏内课同 lane 同 x 同宽(纵向堆叠语义
        // 在网格上=上下不重叠的卡各自画自己区域, 网格天然按节次定位不需要堆叠)
        val rects = ConflictLayoutEngine.gridDayLanes(userDay1())
        assertEquals(7, rects.size)
        val byId = rects.associateBy { it.course.id }
        // lane0 = {2,3,5,7}, lane1 = {1,4,6}
        for (id in listOf(2L, 3L, 5L, 7L)) {
            assertEquals(0f, byId.getValue(id).laneStartFraction, 0.001f)
            assertEquals(0.5f, byId.getValue(id).laneWidthFraction, 0.001f)
        }
        for (id in listOf(1L, 4L, 6L)) {
            assertEquals(0.5f, byId.getValue(id).laneStartFraction, 0.001f)
            assertEquals(0.5f, byId.getValue(id).laneWidthFraction, 0.001f)
        }
    }

    @Test
    fun v7108_grid_mixed_conflict_and_solo() {
        // 同日: 冲突对(1-2 两门) + 独行课(节5) — 独行课整宽, 冲突课各半
        val a = course(id = 1, day = 1, startNode = 1, step = 2)
        val b = course(id = 2, day = 1, startNode = 1, step = 2)
        val solo = course(id = 3, day = 1, startNode = 5, step = 1)
        val rects = ConflictLayoutEngine.gridDayLanes(listOf(a, b, solo))
        val byId = rects.associateBy { it.course.id }
        assertEquals(1f, byId.getValue(3L).laneWidthFraction, 0.001f)
        assertEquals(0.5f, byId.getValue(1L).laneWidthFraction, 0.001f)
        assertEquals(0.5f, byId.getValue(2L).laneWidthFraction, 0.001f)
    }
}
