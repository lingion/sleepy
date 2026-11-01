package com.lingion.sleepy.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * IntRangeSlider 核心纯函数契约 — RangeSlider 回调的浮点区间 → 整数 (start, end)。
 * 锁三件事: 四舍五入吸附到整数档位; 两端夹进 [min, max]; start 不越过 end
 * (导入脏数据 startWeek>endWeek 时滑杆显示口径与摘要一致, 落库校验仍按原值报错)。
 * UI 结构走源码级守卫先例 (IrregularOptionsCollapseTest)。
 */
class IntRangeSliderSnapTest {

    @Test
    fun fractional_range_snaps_to_nearest_integers() {
        val (s, e) = snapRangeToInt(1.6f..3.4f, minValue = 1, maxValue = 10)
        assertEquals(2, s)
        assertEquals(3, e)
    }

    @Test
    fun both_ends_clamped_to_domain() {
        val (s, e) = snapRangeToInt(0.2f..11.9f, minValue = 1, maxValue = 10)
        assertEquals(1, s)
        assertEquals(10, e)
    }

    @Test
    fun start_never_exceeds_end_after_clamp() {
        // 脏数据: start=30, end=1, 域 1..16 — start 夹到 16 后 end 不得小于 start
        val (s, e) = snapRangeToInt(30f..1f, minValue = 1, maxValue = 16)
        assertEquals(16, s)
        assertEquals(16, e)
    }

    @Test
    fun exact_integer_range_passes_through() {
        val (s, e) = snapRangeToInt(2f..4f, minValue = 1, maxValue = 12)
        assertEquals(2, s)
        assertEquals(4, e)
    }

    @Test
    fun single_stop_domain_clamps_everything_to_min() {
        // maxStd=1 的作息表域退化 — 所有值钉在 1 (滑杆本体已退化成静态读数)
        val (s, e) = snapRangeToInt(1f..9f, minValue = 1, maxValue = 1)
        assertEquals(1, s)
        assertEquals(1, e)
    }
}
