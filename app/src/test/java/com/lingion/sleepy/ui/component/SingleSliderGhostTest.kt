package com.lingion.sleepy.ui.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 单头滑杆原值幽灵的可见性纯函数契约 (需求 2026-10-29 用户定义原话语义):
 * 拖离进页基线 (例如原设 7dp 改成 8dp/6dp) → 基线位置出现细条提醒"上一次的结果";
 * 拖回基线刻度点 → 消失; 退出重进基线重算 → 页面重建自然消失。
 * 容差比较 (epsilon) 由调用点按各自最小步进取半档 (0.1 步进 → 0.05, 1/20 量化 →
 * 0.025, 整数 dp 档 → 0.5, 色相 1 度档 → 0.5), 保证"拖回原刻度点"在浮点误差下判得住;
 * 差恰等于 epsilon 视为回到原档, 不可见 (spec 3.1)。
 * 非有限浮点 (NaN/Inf, 脏数据或换算意外) 一律不可见 — 幽灵宁可不画不可乱画。
 */
class SingleSliderGhostTest {

    @Test
    fun null_baseline_never_shows_ghost() {
        assertFalse(singleSliderGhostVisible(current = 8f, baseline = null, epsilon = 0.5f))
    }

    @Test
    fun current_equal_to_baseline_hides_ghost() {
        assertFalse(singleSliderGhostVisible(current = 7f, baseline = 7f, epsilon = 0.5f))
    }

    @Test
    fun dragged_beyond_epsilon_shows_ghost() {
        // 0.1 步进档: 7.0 → 7.06 差 0.06 > 0.05 → 已拖离
        assertTrue(singleSliderGhostVisible(current = 7.06f, baseline = 7f, epsilon = 0.05f))
        // 反方向同理
        assertTrue(singleSliderGhostVisible(current = 6.94f, baseline = 7f, epsilon = 0.05f))
    }

    @Test
    fun difference_exactly_epsilon_hides_ghost() {
        // 边界: 差恰等于容差 = 判"回到原刻度点", 不可见。
        // 用二进制精确值 (0.75-0.5=0.25 无浮点误差) 验判据本身的等号归向 —
        // 十进制字面量 (7.05f=7.0500002f) 的"恰好"在 float32 里根本不存在,
        // 真实调用点靠 epsilon=步进半档吸收该误差, 边界语义本身在这组值上锁
        assertFalse(singleSliderGhostVisible(current = 0.75f, baseline = 0.5f, epsilon = 0.25f))
        assertFalse(singleSliderGhostVisible(current = 0.25f, baseline = 0.5f, epsilon = 0.25f))
        // 刚过界 1 ulp 即反号可见 — 证等号确实在"不可见"一侧
        assertTrue(singleSliderGhostVisible(current = 0.7500001f, baseline = 0.5f, epsilon = 0.25f))
    }

    @Test
    fun non_finite_inputs_hide_ghost() {
        assertFalse(singleSliderGhostVisible(current = Float.NaN, baseline = 7f, epsilon = 0.05f))
        assertFalse(singleSliderGhostVisible(current = Float.POSITIVE_INFINITY, baseline = 7f, epsilon = 0.05f))
        assertFalse(singleSliderGhostVisible(current = 7f, baseline = Float.NaN, epsilon = 0.05f))
        assertFalse(singleSliderGhostVisible(current = 7f, baseline = Float.NEGATIVE_INFINITY, epsilon = 0.05f))
        // abs(NaN - x) <= epsilon 在 IEEE754 恒 false → 会误判"可见", 显式挡掉
        assertFalse(singleSliderGhostVisible(current = Float.NaN, baseline = Float.NaN, epsilon = 0.05f))
    }
}
