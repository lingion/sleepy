package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity

/** 同一天的冲突簇 — 簇内课程节点区间两两经传递闭包相连(直接或间接共享节次)。 */
data class ConflictCluster(val day: Int, val courses: List<CourseEntity>)

/**
 * 网格视图冲突布局引擎 — 纯函数,零 Android/Compose 依赖。
 *
 * 聚簇规则: 仅同一天内,节点区间 [startNode, startNode+step-1](闭区间)相交的课程
 * 经传递闭包归为一簇(链式相邻亦传播,如 1-2 / 2-3 / 3-4 三课同簇)。
 * 跨天永不聚簇;size<2 的簇不返回。
 *
 * 主课判定序(primaryOrder): step 降 > startNode 升 > id 升。
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
