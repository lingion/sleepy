package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity

/** 同一天的冲突簇 — 簇内课程节点区间两两经传递闭包相连(直接或间接共享节次)。 */
data class ConflictCluster(val day: Int, val courses: List<CourseEntity>)

/**
 * 图层间排序键 — 与 primaryComparator(step desc, startNode asc, id asc)对齐。
 * a = -step(反转使 desc 变 asc), b = startNode, c = id。
 */
private data class LayerSortKey(val a: Int, val b: Int, val c: Long) : Comparable<LayerSortKey> {
    override fun compareTo(other: LayerSortKey): Int {
        val x = a.compareTo(other.a); if (x != 0) return x
        val y = b.compareTo(other.b); if (y != 0) return y
        return c.compareTo(other.c)
    }
}

/** 变体标记类型 — NONE=无标记(真卡自然露出),STACK/FOLD/RAIL 见设计文档 §3。 */
enum class ConflictVariant { NONE, STACK, FOLD, RAIL }

/**
 * 单课布局结果 — zRank=0 即该课所在图层为顶层;hidden=零露出;variant 仅 hidden 课非 NONE。
 * chainFront(v7.4 保留语义, v7.8 重定义为): 该课属于链式多课层且该层当前为顶层
 * (层内每门成员都拿顶层视觉与编辑交互)。
 */
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
     * 所在图层整体提到 zRank 0,其余保持图层间相对顺序,层内按 startNode 升序拼接)。
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
     *
     * v7.8 图层语义(用户 2026-09-02 权威版):
     *   layers = chainGroups(courses) — 反复从剩余课中提取最大互不重叠集合,
     *     层内课零重叠可并排成一条链, 每层尽量多合并。
     *   z 序: 图层是整体切换单元。
     *   默认态: 全局主序最高课(step降>start升>id升)所在图层置顶, 其余层按
     *     【层首课主序】垫后(1-3/1-4/4-6 → 1-4 顶层, {1-3,4-6} 垫底)。
     *   override: 被点课所在图层整体置顶, 层内拼接序(startNode 升序)保持,
     *     其余层按默认序垫后。
     *   v7.8 关键: 链组态(存在多课层)下所有课 hidden=false——沉底层渲染真卡
     *     (A=缩小右下/B=全尺寸/C=全宽胶囊), 不再藏成 Mark; 置顶层成员全员
     *     置顶形态(A=缩小左上/B=折角/C=缩窄)。经典无链组(全叠)行为不变。
     */
    fun layoutCluster(
        cluster: ConflictCluster,
        style: String,
        topOverrideId: Long? = null,
        maxNode: Int? = null
    ): List<LaidOutCourse> {
        val ordered = primaryOrder(cluster.courses)

        // === v7.8 图层构建 ===
        val layers = chainGroups(cluster.courses)
        val layerOfId: Map<Long, List<CourseEntity>> =
            layers.flatMap { g -> g.map { it.id to g } }.toMap()
        val hasChainLayer = layers.any { it.size >= 2 }

        // 层间默认序: 与 primaryComparator 对齐(asc 升序), 主序最高者的层排最前 —
// LayerSortKey = (-step, startNode, id) — step 反转后升序 ≡ primaryComparator 的 step desc;
// 例 1-3/1-4/4-6: 层 {1-3,4-6} 的 top = 1-3 → key(-3, 1, 1);
//                层 {1-4} 的 top = 1-4 → key(-4, 1, 3)。
// 升序排: (-4, 1, 3) < (-3, 1, 1) → {1-4} 先 → zRank 0 = 1-4 ✓
// 例 1-3/1-3 完全重叠: 两层各一层, top key 都 = (-3, 1, id)。
// 升序排: id=1 (-3,1,1) < id=2 (-3,1,2) → {id=1} 先 → zRank 0 = id=1 ✓
        val defaultLayerOrder: List<List<CourseEntity>> = layers.sortedBy { g: List<CourseEntity> ->
            val top: CourseEntity = g.maxWith(primaryComparator)
            LayerSortKey(-top.step, top.startNode, top.id)
        }
        // z 序构造: override 命中则该层整体前移, 否则按默认序
        val orderedLayers: List<List<CourseEntity>> = when {
            topOverrideId != null && layerOfId.containsKey(topOverrideId) -> {
                val front = layerOfId.getValue(topOverrideId)
                listOf(front) + defaultLayerOrder.filter { it !== front }
            }
            else -> defaultLayerOrder
        }
        val zOrdered: List<CourseEntity> = orderedLayers.flatMap { g -> g.sortedBy { it.startNode } }

        // === 露出区间工具 ===
        // 露出计算区间: maxNode 非 null 时先 clamp 进 [1, maxNode](与 UI 裁剪空间一致);
        // clamp 后为空(整课出界)→ 空区间,露出集恒空 → hidden(UI 本就不渲染该课)。
        fun nodesOf(course: CourseEntity): IntRange {
            if (maxNode == null) return course.startNode..(course.startNode + course.step - 1)
            val start = maxOf(course.startNode, 1)
            val endIncl = (course.startNode + course.step - 1).coerceAtMost(maxNode)
            return if (start > endIncl) IntRange.EMPTY else start..endIncl
        }

        return zOrdered.mapIndexed { rank, course ->
            val ownNodes = nodesOf(course)
            // v7.8: 链组态(存在多课层)→ 所有课 hidden=false, 沉底方渲染真卡。
            // 经典无链组(全叠)→ 原 hidden 判定原样保留。
            val hidden = if (hasChainLayer) {
                // 裁剪出界(整课不可见)仍标 hidden(UI 不渲染它)
                ownNodes.isEmpty()
            } else if (ownNodes.isEmpty()) {
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
            // chainFront(v7.8 重定义): 该课所在层是多课层且该层当前为顶层 → 全员置顶形态
            val ownLayer: List<CourseEntity>? = layerOfId[course.id]
            val isFrontLayer = ownLayer != null && orderedLayers.firstOrNull() === ownLayer
            val chainFront = hasChainLayer && ownLayer!!.size >= 2 && isFrontLayer

            // v7.6 图层语义: N≥3 合流的「N」按图层数——层算 1 层,不是裸课数。
            val layerCount = layers.size
            LaidOutCourse(
                course = course,
                zRank = rank,
                hidden = hidden,
                variant = if (!hidden) ConflictVariant.NONE
                else variantFor(
                    style, layerCount,
                    sameStartWithAbove(rank, zOrdered, ::nodesOf),
                    chainMode = hasChainLayer && ownLayer!!.size >= 2
                ),
                chainFront = chainFront
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
     *
     * v7.6 图层语义: clusterSize 形参 = **图层数**(链组整组算 1 层,单课算 1 层),
     * 不是裸课数——「分组之后这两节就绑定在一个图层了」(用户 2026-09-02)。
     * {1-3,4-6} 组 + 1-6 重叠者 = 2 图层 → N≥3 合流不触发;链式分支优先级本身
     * 也已把组态课与 N≥3 合流隔离(形态由链组决定,不由课数决定)。
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
     * 图层划分(v7.8 定版, 纯函数可测) ——
     * 反复从剩余课中提取最大互不重叠集合: 每轮按 startNode 升序排列剩余课,
     * 用区间图最大独立集经典算法(按右端点贪心)挑出能并排的最多门课作为一个图层,
     * 剩余课继续处理。
     *
     * 输入: 簇内课程(已由 findClusters 保证两两经传递闭包相关, 必有至少一对重叠)。
     * 输出: 多图层列表, 顺序按「每图层内 startNode 升序合并」的全局主序拼接后升序,
     * 层内按 startNode 升序拼接。
     *
     * 经典形态(用户 2026-09-02 案例):
     *   - {1-3, 1-4, 4-6}: 1-3/4-6 零重叠可并排 → 图层1 = {1-3,4-6}, 1-4 自成图层2。
     *   - {1-3, 4-6, 7-9, 2-4, 5-8}: 第一轮 1-3/4-6/7-9 三课可并排 → 图层1,
     *     剩余 2-4/5-8 零重叠 → 图层2。
     *   - 三课完全重叠(1-3/1-3/1-3 或 1-3/2-4/3-5 部分重叠无零重叠对):
     *     每轮最多取 1 课 → 退化为多图层单课。
     */
    fun chainGroups(courses: List<CourseEntity>): List<List<CourseEntity>> {
        if (courses.isEmpty()) return emptyList()

        val remaining = courses.toMutableList()
        val layers = mutableListOf<List<CourseEntity>>()

        while (remaining.isNotEmpty()) {
            // 按 startNode 升序排列剩余课
            val sorted = remaining.sortedWith(compareBy({ it.startNode }, { it.step }, { it.id }))
            // 区间图最大独立集 — 按右端点贪心
            val layer = mutableListOf<CourseEntity>()
            var currentEnd = -1
            for (c in sorted) {
                val start = c.startNode
                val inclEnd = c.startNode + c.step - 1
                if (start > currentEnd) {
                    layer.add(c)
                    currentEnd = inclEnd
                }
            }
            if (layer.isEmpty()) {
                // 兜底: 极端情况(不应发生, 仅防御)→ 把第一门课作为单课图层, 防死循环
                layer.add(sorted.first())
            }
            layers.add(layer.sortedBy { it.startNode })
            remaining.removeAll { c -> layer.any { it.id == c.id } }
        }

        return layers
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

    // =====================================================================================
    // v7.10 Feature 2 — 周视图局部栏位分割
    // =====================================================================================

    /** 周视图栏位布局结果 — 每课一段;无冲突课 laneCount=1(全宽)。 */
    data class WeekLaneSegment(
        val course: CourseEntity,
        val lane: Int,        // 0 起的栏位序号
        val laneCount: Int    // 所在连通冲突区域的总栏数;无冲突 = 1
    )

    /**
     * 周视图局部栏位分割(纯函数,可测) — 用户 2026-09-02 权威语义:
     * 「分栏只适用于周视图 跟网格视图无关」。
     *
     * 算法三步:
     *   1. 按 day 分桶,桶内用 mergeOverlapping 求传递闭包 → 连通冲突区域
     *      (1-2/2-3/3-4 链式相邻 → 整段 节1..4 是一个区域,虽然节1节4 无直接冲突)。
     *   2. 区域内跑 chainGroups(贪心最大独立集,按右端点) = 栏位分组:
     *      零重叠课同栏(1-2 与 3-4 同栏),重叠课异栏(2-3 独占一栏)。
     *      栏数 = 分组数(3 课分 2 组 = 2 栏,不是 3 栏——用户 2026-09-02「50 节课分
     *      两组还是两行」的同构规则)。
     *   3. 区域外课 laneCount=1 = 全宽,不参与分栏。
     *
     * 整课一个 lane 不拆节(连续占两节空间的课整段同栏);课高由调用方按自身节数算,
     * 本函数只给横向栏位几何。每课输出一条 segment,顺序 = 输入顺序无关、按 day+lane 排。
     */
    fun weekLaneSegments(courses: List<CourseEntity>): List<WeekLaneSegment> {
        if (courses.isEmpty()) return emptyList()
        val out = mutableListOf<WeekLaneSegment>()

        for ((day, dayCourses) in courses.groupBy { it.day }) {
            val sorted = dayCourses.sortedWith(compareBy({ it.startNode }, { it.step }, { it.id }))
            // 传递闭包分区域 — 区域 = 直接或经链式相邻共享节次的极大课程集
            val regions = mergeOverlapping(sorted)
            for (region in regions) {
                if (region.size < 2) {
                    // 无冲突: 全宽
                    for (c in region) out.add(WeekLaneSegment(c, 0, 1))
                    continue
                }
                // 区域内分栏: chainGroups 贪心独立集即栏位分组(lane ≡ 图层)
                val lanes = chainGroups(region)
                for ((laneIdx, lane) in lanes.withIndex()) {
                    for (c in lane) out.add(WeekLaneSegment(c, laneIdx, lanes.size))
                }
            }
        }
        return out
    }

    /**
     * 周视图行分组结果 — 一行 = 一个渲染行。冲突区域整区域一行(横向分 laneCount 栏,
     * 同栏多门课纵向堆叠);无冲突课一行一门(全宽)。
     */
    data class WeekLaneRow(
        val courses: List<CourseEntity>,   // 行内全部课,按 startNode 升序
        val laneOf: Map<Long, Int>,        // courseId → 栏位序号(仅冲突行非空)
        val laneCount: Int                 // 冲突行 = 栏数;无冲突行 = 1
    )

    /**
     * 周视图渲染行分组(v7.10.6, 纯函数可测) — 修复用户 2026-09-02 报障:
     * UI 层旧实现用直接重叠收集行成员(非传递闭包) + 每栏 firstOrNull 只留一门课,
     * 导致七课链式区域里 9-11 课被丢、独立区域课被渲染两次。
     *
     * 正确性来自结构本身: mergeOverlapping 区域是**划分**(每课恰属一个区域,
     * 传递闭包保证链式连通课同区域),行成员 = 区域全体,每课恰渲染一次——
     * 丢课与重复在结构上不可能发生。
     *
     * 行序: 各行按行首课 startNode 升序穿插(冲突行行首 = 区域最早课)。
     * 栏位几何复用 weekLaneSegments 的算法(chainGroups 贪心独立集),与 segments 输出
     * 严格一致。
     */
    fun weekLaneRows(courses: List<CourseEntity>): List<WeekLaneRow> {
        if (courses.isEmpty()) return emptyList()
        val rows = mutableListOf<WeekLaneRow>()

        for ((day, dayCourses) in courses.groupBy { it.day }) {
            val sorted = dayCourses.sortedWith(compareBy({ it.startNode }, { it.step }, { it.id }))
            val regions = mergeOverlapping(sorted)
            for (region in regions) {
                if (region.size < 2) {
                    for (c in region) rows.add(WeekLaneRow(listOf(c), emptyMap(), 1))
                    continue
                }
                val lanes = chainGroups(region)
                val laneOf = HashMap<Long, Int>(region.size)
                for ((laneIdx, lane) in lanes.withIndex()) {
                    for (c in lane) laneOf[c.id] = laneIdx
                }
                rows.add(WeekLaneRow(region.sortedBy { it.startNode }, laneOf, lanes.size))
            }
        }
        // 跨天区域自然隔离;同天内区域已按 mergeOverlapping 的扫描序(行首节点升序)输出
        return rows.sortedBy { it.courses.first().startNode }
    }
}