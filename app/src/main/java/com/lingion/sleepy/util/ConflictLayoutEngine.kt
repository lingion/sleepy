package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity

/** 同一天的冲突簇 — 簇内课程节点区间两两经传递闭包相连(直接或间接共享节次)。 */
data class ConflictCluster(val day: Int, val courses: List<CourseEntity>)

/** 变体标记类型 — NONE=无标记(真卡自然露出),STACK/FOLD/RAIL 见设计文档 §3。 */
enum class ConflictVariant { NONE, STACK, FOLD, RAIL }

/** 单课布局结果 — zRank 0=顶层;hidden=零露出;variant 仅 hidden 课非 NONE。
 *  chainFront(v7.2)= 该课属于链式多课组且该组当前在顶层链(全尺寸拼条渲染依据)。 */
data class LaidOutCourse(
    val course: CourseEntity,
    val zRank: Int,
    val hidden: Boolean,
    val variant: ConflictVariant,
    val chainFront: Boolean = false
)

/**
 * 网格视图冲突布局引擎 — 纯函数,零 Android/Compose 依赖。
 *
 * 聚簇规则: 仅同一天内,节点区间 [startNode, startNode+step-1](闭区间)相交的课程
 * 经传递闭包归为一簇(链式相邻亦传播,如 1-2 / 2-3 / 3-4 三课同簇)。
 * 跨天永不聚簇;size<2 的簇不返回。
 *
 * 主课判定序(primaryOrder): step 降 > startNode 升 > id 升。
 *
 * 零露出(hidden): 课 X 的节点区间减去所有 z 序高于 X 的课覆盖区间并集后的剩余节点集
 * 为空。顶层课(zRank 0)永不为 hidden——除非其区间在裁剪空间内为空(整课出界,fix wave
 * 1b)。hidden 状态每次调用现算,不缓存。
 *
 * 变体分配: hidden 课按 style 直配(stack+N=2=STACK,stack+N≥3=FOLD 合流,fold/rail 直配),
 * 非 hidden 课(含顶层)一律 NONE。简化裁定: 变体按「簇内是否存在 hidden 课」整体决定,
 * hidden 课统一拿该 style 对应的 variant 值。同起不同止的短课按完全包含路径判 hidden=true
 * 拿标记(短课的「长段」在被覆盖语义下仍归零露出,标记保证其可见可达,与设计 §2.3/§9.1
 * 一致);只有存在独占节次的课(露出集非空)才非 hidden → NONE → 不画标记。
 */
object ConflictLayoutEngine {

    /**
     * 找出全部冲突簇。输出簇间按 day 升序,簇内课程已按主课判定序排好。
     */
    fun findClusters(courses: List<CourseEntity>): List<ConflictCluster> {
        // 按 day 分组 → 簇内按 startNode 升序,线性扫相邻区间合并 → 仅保留 size≥2 的簇
        return courses.groupBy { it.day }
            .toSortedMap()
            .flatMap { (day, dayCourses) ->
                val sorted = dayCourses.sortedWith(compareBy({ it.startNode }, { it.step }, { it.id }))
                mergeOverlapping(sorted)
                    .filter { it.size >= 2 }
                    .map { ConflictCluster(day, it.sortedWith(primaryComparator)) }
            }
    }

    /**
     * 主课判定序: step 降 > startNode 升 > id 升。
     */
    fun primaryOrder(courses: List<CourseEntity>): List<CourseEntity> =
        courses.sortedWith(primaryComparator)

    /**
     * 布局一簇: 返回全簇课,输出顺序 = zRank 升序(主课判定序;topOverrideId 命中时该课
     * 提到 zRank 0,其余保持主课判定序相对顺序)。
     *
     * 对每门课算「节点区间减去 z 序更高课的覆盖并集」得露出集;露出空 = hidden。
     * 顶层课(zRank 0)永不为 hidden。
     *
     * style ∈ "stack"/"fold"/"rail";stack 在 N≥3 时合流为 FOLD(4dp 边承载不了三层语义,
     * 见设计文档 §3)。
     *
     * maxNode(可空,final fix wave Important): UI 渲染把每课区间 clamp 进 [1, maxNode]
     * (startNode > maxNode 的课整课不画,step 截到不越界)。hidden 必须在同一裁剪空间算,
     * 否则「界外尾部节次独占」的课(如 maxNode=12 时 X=11-13 被 Y=10-12 压住,仅节 13 界外)
     * 会被判非 hidden 拿不到标记,UI 裁剪后却零视觉零 tap——不可达课。非 null 时先 clamp
     * 再算露出(全被 clamp 出区间 → 露出集空 → hidden);null 时行为与不裁剪完全一致。
     */
    fun layoutCluster(
        cluster: ConflictCluster,
        style: String,
        topOverrideId: Long? = null,
        maxNode: Int? = null
    ): List<LaidOutCourse> {
        val ordered = primaryOrder(cluster.courses)
        val n = ordered.size

        // v7.2/v7.3 链式分层: 分组决定 z 序——
        //   默认态(无 override): 链组(多课组)整组置前(组内 startNode 升序拼条),
        //     其余课(重叠者/单课组)按主课判定序垫后。
        //     用户实测缺陷修复: 原默认 z 序按 step 降把重叠长课排最前,链组被遮。
        //   override 命中(v7.3): 命中链组任一成员 → 该链组整组前置(拼接序保持),
        //     其余课垫后——【组是一个整体切换单元】,组内课不能单独置顶
        //     (否则组代表置顶、其他成员留底层 = 用户看到的"4-6 永远切不上来")。
        //   override 命中单课组/非链成员: 该课置顶,其余按序垫后(经典单课语义)。
        val chainGroupsOfCluster = chainGroups(cluster.courses)
        val groupOfId: Map<Long, List<CourseEntity>> =
            chainGroupsOfCluster.flatMap { g -> g.map { it.id to g } }.toMap()
        val multiGroupIds: Set<Long> = chainGroupsOfCluster
            .filter { it.size >= 2 }
            .flatMap { g -> g.map { it.id } }
            .toSet()
        val overrideGroup = topOverrideId?.let { groupOfId[it] }
        val zOrdered = when {
            topOverrideId != null && overrideGroup != null && overrideGroup.size >= 2 ->
                overrideGroup.sortedBy { it.startNode } +
                    ordered.filter { it.id !in overrideGroup.map { c -> c.id }.toSet() }
            topOverrideId != null ->
                ordered.filter { it.id == topOverrideId } + ordered.filter { it.id != topOverrideId }
            multiGroupIds.isNotEmpty() ->
                chainGroupsOfCluster.filter { it.size >= 2 }
                    .flatMap { g -> g.sortedBy { it.startNode } } +
                    ordered.filter { it.id !in multiGroupIds }
            else -> ordered
        }
        // 链前置标记: 当前置顶单元是链组(默认态或 override 命中链组)时,组员全部标 chainFront
        val frontGroupIds: Set<Long>? = when {
            topOverrideId != null && overrideGroup != null && overrideGroup.size >= 2 ->
                overrideGroup.map { it.id }.toSet()
            topOverrideId == null && multiGroupIds.isNotEmpty() -> multiGroupIds
            else -> null
        }
        val chainFrontActive = frontGroupIds != null

        // 露出计算区间: maxNode 非 null 时先 clamp 进 [1, maxNode](与 UI 裁剪空间一致);
        // clamp 后为空(整课出界)→ 空区间,露出集恒空 → hidden(UI 本就不渲染该课)。
        fun nodesOf(course: CourseEntity): IntRange {
            if (maxNode == null) return course.startNode until course.startNode + course.step
            val start = maxOf(course.startNode, 1)
            val endIncl = (course.startNode + course.step - 1).coerceAtMost(maxNode)
            return if (start > endIncl) IntRange.EMPTY else start..endIncl
        }

        return zOrdered.mapIndexed { rank, course ->
            val ownNodes = nodesOf(course)
            val hidden = if (ownNodes.isEmpty()) {
                // 裁剪空间内区间为空(如尾向整课出界)→ 界内零可见露出,无论 zRank 一律 hidden
                // (UI 对 startNode>maxNode 的课本就不渲染,标记派生自 drawList 无锚定风险)
                true
            } else if (rank == 0) {
                false // 顶层课永不为 hidden
            } else {
                // 本课区间减去所有更高层(zRank 更小)课的覆盖并集 → 露出集;
                // 区间内所有节点都已被覆盖 → 零露出
                val covered = zOrdered.take(rank)
                    .flatMap { nodesOf(it) }
                    .toSet()
                ownNodes.all { it in covered }
            }
            LaidOutCourse(
                course = course,
                zRank = rank,
                hidden = hidden,
                variant = if (!hidden) ConflictVariant.NONE
                else variantFor(
                    style, n,
                    sameStartWithAbove(rank, zOrdered, ::nodesOf),
                    chainMode = chainGroupsOfCluster.any { it.size >= 2 } || multiGroupIds.isNotEmpty()
                ),
                chainFront = chainFrontActive && frontGroupIds.orEmpty().contains(course.id)
            )
        }
    }

    /**
     * hidden 课的 variant 映射(视觉修订 v3,用户 2026-09-01 定版;v7.2 链式分支):
     * rail 直配(A/C 全场景通用);FOLD 仅当与 z 序紧邻上层课**同起点**可用
     * (起点对不齐 → 缺角处露不出对齐的角,只有 A/C 能用)→ 否则回落 STACK;
     * stack 样式 N≥3 合流 FOLD 的规则保留,但同样受同起点闸门约束。
     * 链式模式(chainMode=true): hidden 课是被链组全遮的重叠者,与用户 2026-09-01
     * 链式折角定版一致——「哪怕 AC 连起来 B 待在后面,A/C 都折角,B 折角点击切换」,
     * 折角不再要求同起点(fold 样式与 N≥3 合流均直配 FOLD)。
     */
    private fun variantFor(
        style: String,
        clusterSize: Int,
        sameStartWithAbove: Boolean,
        chainMode: Boolean = false
    ): ConflictVariant = when {
        style == "rail" -> ConflictVariant.RAIL
        // 链式模式: hidden 课=被链组全遮的重叠者,直接按样式走(stack→STACK 条带,
        // fold→FOLD),不做 N≥3 合流、不看同起点——链式形态由链组决定
        chainMode -> if (style == "fold") ConflictVariant.FOLD else ConflictVariant.STACK
        style == "fold" -> if (sameStartWithAbove) ConflictVariant.FOLD else ConflictVariant.STACK
        clusterSize >= 3 -> if (sameStartWithAbove) ConflictVariant.FOLD else ConflictVariant.STACK
        else -> ConflictVariant.STACK
    }

    /** hidden 课与其 z 序紧邻上层课起点是否一致(clamp 后;无上层课 → false)。 */
    private fun sameStartWithAbove(
        rank: Int,
        zOrdered: List<CourseEntity>,
        nodesOf: (CourseEntity) -> IntRange
    ): Boolean {
        val above = zOrdered.getOrNull(rank - 1) ?: return false
        return nodesOf(above).first == nodesOf(zOrdered[rank]).first
    }

    /**
     * 链式分组(v7.1 定版 / v7.5 判据修正, 纯函数可测) —
     * 用户原话(2026-09-01/02 两次定版): 「有一节课与剩下两节课重叠,并且那两节课之间
     * 没有重叠部分,这样那两节课可以作为一个分组」。
     *
     * 判据: p、q 同组 ⇔ 存在共同重叠者 o(o 与 p 重叠 且 o 与 q 重叠)且 p、q 互不重叠。
     * v7.1 的「纯零重叠贪心装箱」是过度推广——它把毫无关系的跨区零重叠课也收进同组
     * (上半 1-3/4-6 与下半 7-9/10-12 并存时,7-9 被误收进上半组),导致上下半切换
     * 逻辑串组。v7.5 改为字面实现用户定义: 零重叠只是必要条件,**共同重叠者**才是
     * 成组的充分条件。
     *
     * 典型形态: 1-9 / 1-3 / 5-9 → [1-3,5-9](共同重叠者 1-9) + [1-9] 独立;
     * 两个冲突区并存各成一组互不干扰(1-3,4-6|1-4 + 7-9,10-12|7-12 → 4 组)。
     * 硬案例 1-3/2-3/2-4: 两两直接重叠,无零重叠对 → 每课一组(N≥3 特殊讨论)。
     * 同段双课 1-2/1-2 + 3-4/3-4: 无任何课跨段重叠 → 不成组,退化为经典叠放。
     *
     * 传递歧义: 一课已入组不再加入后续组(先到先得,按主课判定序枚举)。
     *
     * 注意: 纯零重叠(相邻/隔洞)不成簇——本函数只处理已聚簇课程,簇的进出仍由
     * findClusters 的「区间相交+传递闭包」把守。
     */
    fun chainGroups(courses: List<CourseEntity>): List<List<CourseEntity>> {
        if (courses.isEmpty()) return emptyList()
        val sorted = courses.sortedWith(primaryComparator)
        val spans = sorted.associate { c ->
            c.id to c.startNode..(c.startNode + c.step - 1)
        }
        fun overlaps(p: CourseEntity, q: CourseEntity): Boolean {
            val a = spans.getValue(p.id); val b = spans.getValue(q.id)
            return a.first <= b.last && b.first <= a.last
        }
        val groupOf = mutableMapOf<Long, MutableList<CourseEntity>>()
        // 枚举共同重叠者 o 的每一对被挤课(p,q): p,q 互不重叠且都未定组 → {p,q} 成组
        for (o in sorted) {
            for (p in sorted) {
                if (p.id == o.id || !overlaps(o, p)) continue
                for (q in sorted) {
                    if (q.id <= p.id) continue
                    if (q.id == o.id || !overlaps(o, q)) continue
                    if (overlaps(p, q)) continue
                    if (p.id in groupOf || q.id in groupOf) continue
                    val g = mutableListOf(p, q)
                    groupOf[p.id] = g
                    groupOf[q.id] = g
                }
            }
        }
        // 输出: 已定组按组(组内主序),未定组课各自单课组;整体按各首课主序位置排列
        val emitted = mutableSetOf<MutableList<CourseEntity>>()
        val result = mutableListOf<List<CourseEntity>>()
        for (c in sorted) {
            val g = groupOf[c.id]
            if (g == null) result.add(listOf(c))
            else if (emitted.add(g)) result.add(g.sortedWith(primaryComparator))
        }
        return result
    }

    /** 主课三分量比较器,供聚簇输出与 primaryOrder 共用。 */
    private val primaryComparator =
        compareByDescending<CourseEntity> { it.step }
            .thenBy { it.startNode }
            .thenBy { it.id }

    /**
     * 线性扫已按 startNode 排序的区间,相邻相交则合并为一簇。
     * 返回 List<List<CourseEntity>>,每个子列表是一个原始簇(可能 size==1,由调用方过滤)。
     */
    private fun mergeOverlapping(sorted: List<CourseEntity>): List<List<CourseEntity>> {
        if (sorted.isEmpty()) return emptyList()
        val clusters = mutableListOf<MutableList<CourseEntity>>(mutableListOf(sorted[0]))
        var currentEnd = sorted[0].startNode + sorted[0].step - 1
        for (c in sorted.drop(1)) {
            // 当前课的区间起点 ≤ 上一簇右端 → 相交,归入同簇(传递闭包)
            if (c.startNode <= currentEnd) {
                clusters.last().add(c)
                currentEnd = maxOf(currentEnd, c.startNode + c.step - 1)
            } else {
                clusters.add(mutableListOf(c))
                currentEnd = c.startNode + c.step - 1
            }
        }
        return clusters
    }
}
