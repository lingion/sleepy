package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JwParity 共享端点修正单测 (审计 2026-09-04)。
 *
 * 背景三处复制 (ZJU/USTC/SEU) 都只抬 start 不动 end: "6-6(单)" → (7,6)
 * 倒挂, 下游 coerceAtLeast 抬成 (7,7) — 课落在错误周。共享 helper 修正
 * 后把 end 抬回 start, 课落回首个合法周。
 */
class JwParityTest {

    @Test
    fun weekly_parity_zero_returns_range_untouched() {
        assertEquals(3 to 16, JwParity.adjustedRange(3, 16, 0))
        assertEquals(6 to 6, JwParity.adjustedRange(6, 6, 0))
    }

    @Test
    fun odd_parity_bumps_even_start_to_odd() {
        assertEquals(3 to 15, JwParity.adjustedRange(2, 15, 1))
        assertEquals(3 to 15, JwParity.adjustedRange(3, 15, 1))
    }

    @Test
    fun even_parity_bumps_odd_start_to_even() {
        assertEquals(2 to 16, JwParity.adjustedRange(1, 16, 2))
        assertEquals(2 to 16, JwParity.adjustedRange(2, 16, 2))
    }

    @Test
    fun single_week_range_with_mismatched_parity_does_not_invert() {
        // 审计核心场景: "6-6周(单)" 起点 6 偶 → 抬 7, 原 (7,6) 倒挂
        // 共享 helper 把 end 抬回 start → (7,7), 课落回首个合法周
        assertEquals(7 to 7, JwParity.adjustedRange(6, 6, 1))
        // "5-5周(双)" 起点 5 奇 → 抬 6
        assertEquals(6 to 6, JwParity.adjustedRange(5, 5, 2))
    }

    @Test
    fun discrete_week_series_with_parity_each_segment_stays_ordered() {
        // USTC 离散段 + parity: "2,4,6,8周" type=2 → 每段抬 start
        // 旧实现产出 (3,2)(5,4)(7,6)(9,8) 全倒挂
        val out = listOf(2, 4, 6, 8).map { JwParity.adjustedRange(it, it, 2) }
        assertEquals(listOf(2 to 2, 4 to 4, 6 to 6, 8 to 8), out)
    }
}
