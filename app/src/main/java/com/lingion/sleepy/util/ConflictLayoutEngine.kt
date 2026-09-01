package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity

/** 同一天的冲突簇 — 簇内课程节点区间两两经传递闭包相连(直接或间接共享节次)。 */
data class ConflictCluster(val day: Int, val courses: List<CourseEntity>)

/** 变体标记类型 — NONE=无标记(真卡自然露出),STACK/FOLD/RAIL 见设计文档 §3。 */
enum class ConflictVariant { NONE, STACK, FOLD, RAIL }

/** 单课布局结果 — zRank 0=顶层;hidden=零露出;variant 仅 hidden 课非 NONE。 */
data class LaidOutCourse(
    val course: CourseEntity,
    val zRank: Int,
    val hidden: Boolean,
    val variant: ConflictVariant
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

        // zRank 0 = 顶层。override 命中 → 该课提前,其余保持主课判定序相对顺序。
        val zOrdered = when (topOverrideId) {
            null -> ordered
            else -> ordered.filter { it.id == topOverrideId } + ordered.filter { it.id != topOverrideId }
        }

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
                variant = if (!hidden) ConflictVariant.NONE else variantFor(style, n)
            )
        }
    }

    /** hidden 课的 variant 映射: fold/rail 直配;stack 在 N=2 出 STACK,N≥3 合流 FOLD。 */
    private fun variantFor(style: String, clusterSize: Int): ConflictVariant = when (style) {
        "fold" -> ConflictVariant.FOLD
        "rail" -> ConflictVariant.RAIL
        else -> if (clusterSize >= 3) ConflictVariant.FOLD else ConflictVariant.STACK
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
