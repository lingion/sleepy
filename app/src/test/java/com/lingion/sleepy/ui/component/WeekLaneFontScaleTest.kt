package com.lingion.sleepy.ui.component

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v7.10.4 周视图冲突栏压缩比 — 用户 2026-09-02:
 * 「不选左右两栏时分栏呈现比较好;选了两栏字全挤到一起 → 调整画面/字体比例」。
 *
 * 契约:
 *   - lane 宽 ≥ 150dp(单栏半宽基准,实测"比较好"的现状量级)→ 1.0 不缩(保现状)
 *   - 150dp 以下线性压缩
 *   - 0.6 封底(再窄也不无限缩,由隐藏侧栏/meta 兜底可读性)
 */
class WeekLaneFontScaleTest {

    @Test
    fun wide_lane_keeps_full_scale() {
        // 单栏模式 lane ≈ 150-200dp → 完全不缩(用户认可的现状)
        assertEquals(1f, weekLaneFontScale(150.dp))
        assertEquals(1f, weekLaneFontScale(300.dp))
    }

    @Test
    fun narrow_lane_scales_linearly() {
        // 两栏模式 lane ≈ 100dp → 线性 100/150
        assertEquals(100f / 150f, weekLaneFontScale(100.dp), 0.0001f)
    }

    @Test
    fun very_narrow_lane_floors_at_0_6() {
        // 两栏极窄(360dp 屏 lane≈66dp)→ 封底 0.6,不无限缩
        assertEquals(0.6f, weekLaneFontScale(66.dp))
        assertEquals(0.6f, weekLaneFontScale(10.dp))
    }
}
