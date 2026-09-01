package com.lingion.sleepy.util

/** 判断两个周次区间（含单双周类型）是否有公共上课周。
 *  type: 0=每周 1=单周 2=双周 3=按周次列实际指定的周; 奇偶按绝对周号判定, 与 CourseEntity.inWeek 一致。 */
fun weekRangesOverlap(
    aStart: Int, aEnd: Int, aType: Int,
    bStart: Int, bEnd: Int, bType: Int
): Boolean {
    val lo = maxOf(aStart, bStart)
    val hi = minOf(aEnd, bEnd)
    if (lo > hi) return false
    fun hits(week: Int, type: Int): Boolean = when (type) {
        1 -> week % 2 == 1
        2 -> week % 2 == 0
        else -> true  // 0/3 及其他: 只要在该周区间内就算命中
    }
    // 任一公共周同时命中两类课即相交
    for (week in lo..hi) {
        if (hits(week, aType) && hits(week, bType)) return true
    }
    return false
}
