package com.lingion.sleepy.ui.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * IntRangeSlider 原位置幽灵占位的可见性纯函数契约 (需求 2026-10-28):
 * 编辑课程滑杆被拖离打开页面时的初值区间 → 显示浅色幽灵; 拖回初值区间 →
 * 重合不显示; 初值为 null (新课程/新时段) → 永不显示。
 * 初值夹取规则与显示口径 (displayStart/displayEnd) 完全同构:
 * os = initialStart.coerceIn(min, max); oe = initialEnd.coerceIn(os, max) —
 * 导入脏数据 (越界/交叉周次) 时幽灵比较按夹取后的读数走, 不会画出与摘要矛盾、
 * 或实际整段重合却判可见的幽灵。
 */
class IntRangeSliderGhostTest {

    @Test
    fun null_initial_range_never_shows_ghost() {
        assertFalse(
            sliderGhostVisible(
                currentStart = 3, currentEnd = 7,
                initialStart = null, initialEnd = null,
                minValue = 1, maxValue = 16,
            )
        )
    }

    @Test
    fun current_equal_to_initial_hides_ghost() {
        // 打开表单未拖动 (或拖回原位) — 当前区间与初值区间重合, 不画幽灵
        assertFalse(
            sliderGhostVisible(
                currentStart = 2, currentEnd = 4,
                initialStart = 2, initialEnd = 4,
                minValue = 1, maxValue = 16,
            )
        )
    }

    @Test
    fun dragged_away_shows_ghost() {
        assertTrue(
            sliderGhostVisible(
                currentStart = 3, currentEnd = 5,
                initialStart = 1, initialEnd = 2,
                minValue = 1, maxValue = 16,
            )
        )
    }

    @Test
    fun partially_overlapping_range_still_shows_ghost() {
        // 只动了一端 (另一端仍重合) 也算拖离 — 幽灵要标出未重合的那端
        assertTrue(
            sliderGhostVisible(
                currentStart = 2, currentEnd = 6,
                initialStart = 2, initialEnd = 4,
                minValue = 1, maxValue = 16,
            )
        )
    }

    @Test
    fun dirty_initial_clamped_to_equal_current_hides_ghost() {
        // 脏数据: 初值 30..40 越界, 域 1..16 → 夹取成 16..16;
        // 用户拖到 16..16 后与夹取后的初值读数重合 → 不画幽灵
        assertFalse(
            sliderGhostVisible(
                currentStart = 16, currentEnd = 16,
                initialStart = 30, initialEnd = 40,
                minValue = 1, maxValue = 16,
            )
        )
    }

    @Test
    fun crossed_dirty_initial_clamps_start_first_like_display() {
        // 初值交叉 (start>end): 夹取与 displayEnd.coerceIn(displayStart, max) 同构 —
        // os=coerceIn(9,1..7)=7, oe=coerceIn(2, 7..7)=7 → 7..7; 当前 7..7 重合不显示
        assertFalse(
            sliderGhostVisible(
                currentStart = 7, currentEnd = 7,
                initialStart = 9, initialEnd = 2,
                minValue = 1, maxValue = 7,
            )
        )
        // 当前 5..6 与夹取后 7..7 不同 → 显示
        assertTrue(
            sliderGhostVisible(
                currentStart = 5, currentEnd = 6,
                initialStart = 9, initialEnd = 2,
                minValue = 1, maxValue = 7,
            )
        )
    }

    @Test
    fun single_stop_domain_hides_ghost() {
        // maxStd=1 作息表退化域: 滑杆本体已退化成静态读数 (组件不渲染 RangeSlider);
        // 纯函数层面任何值夹取后都重合, 双保险
        assertFalse(
            sliderGhostVisible(
                currentStart = 1, currentEnd = 1,
                initialStart = 1, initialEnd = 1,
                minValue = 1, maxValue = 1,
            )
        )
    }
}
