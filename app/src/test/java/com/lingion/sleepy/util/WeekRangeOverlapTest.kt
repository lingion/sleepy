package com.lingion.sleepy.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekRangeOverlapTest {

    // 核心场景：1-5周每周 vs 6-10周每周 — 不相交（用户报告的课）
    @Test
    fun disjointWeeklyRanges_noOverlap() {
        assertFalse(weekRangesOverlap(1, 5, 0, 6, 10, 0))
    }

    // 区间重叠 + 都是每周 → 冲突
    @Test
    fun overlappingWeeklyRanges_overlap() {
        assertTrue(weekRangesOverlap(1, 5, 0, 3, 8, 0))
    }

    // 区间相交但奇偶错开（1-5单周 vs 2-8双周 公共周 2,4 都不命中）→ 不冲突
    @Test
    fun oddVsEven_disjoint() {
        assertFalse(weekRangesOverlap(1, 5, 1, 2, 8, 2))
    }

    // 区间相交且同为单周 → 冲突（公共奇数周 3,5）
    @Test
    fun oddVsOdd_overlap() {
        assertTrue(weekRangesOverlap(1, 5, 1, 3, 8, 1))
    }

    // 1-5每周 vs 3-8单周：公共周 3,5 是奇数命中 → 冲突
    @Test
    fun weeklyVsOdd_overlap() {
        assertTrue(weekRangesOverlap(1, 5, 0, 3, 8, 1))
    }

    // 2-6每周 vs 1-5双周：公共周 2,4 偶数命中 → 冲突
    @Test
    fun weeklyVsEven_overlap() {
        assertTrue(weekRangesOverlap(2, 6, 0, 1, 5, 2))
    }

    // 1-5双周 vs 6-10单周 区间不相交
    @Test
    fun evenVsOdd_disjointRanges() {
        assertFalse(weekRangesOverlap(1, 5, 2, 6, 10, 1))
    }

    // 边界：5-5 与 5-10 相交于第5周
    @Test
    fun singleWeekBoundary_overlap() {
        assertTrue(weekRangesOverlap(5, 5, 0, 5, 10, 0))
    }
}
