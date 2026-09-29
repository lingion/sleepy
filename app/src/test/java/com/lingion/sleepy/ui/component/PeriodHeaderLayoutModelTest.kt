package com.lingion.sleepy.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodHeaderLayoutModelTest {
    private val metrics = PeriodHeaderMetrics(40f, 36f, 28f, false)

    @Test
    fun left_endpoint_uses_actual_outer_rectangle() {
        val p = metrics.solvePlacement(-1f)
        assertEquals(68f, p.contentWidth, 0.001f)
        assertEquals(28f, p.timeBaseLeft, 0.001f)
        assertEquals(0f, p.labelLeft, 0.001f)
    }

    @Test
    fun right_endpoint_uses_actual_outer_rectangle() {
        val p = metrics.solvePlacement(1f)
        assertEquals(68f, p.contentWidth, 0.001f)
        assertEquals(0f, p.timeBaseLeft, 0.001f)
        assertEquals(40f, p.labelLeft, 0.001f)
    }

    @Test
    fun current_rectangle_contains_time_rows_and_label() {
        for (u in floatArrayOf(-1f, 0f, 1f)) {
            val p = metrics.solvePlacement(u)
            assertTrue(p.timeBaseLeft >= -0.001f)
            assertTrue(p.timeBaseLeft + metrics.timeMax <= p.contentWidth + 0.001f)
            assertTrue(p.labelLeft >= -0.001f)
            assertTrue(p.labelLeft + metrics.labelWidth <= p.contentWidth + 0.001f)
        }
    }

    @Test
    fun three_w_is_motion_range_not_card_width() {
        assertEquals(120f, metrics.trackWidth, 0.001f)
        assertTrue(metrics.inkWidth(-1f) < metrics.trackWidth)
        assertTrue(metrics.inkWidth(1f) < metrics.trackWidth)
    }

    @Test
    fun label_reference_moves_with_endpoint_edges() {
        val left = metrics.solvePlacement(-1f)
        val right = metrics.solvePlacement(1f)
        assertEquals(metrics.labelWidth, left.timeBaseLeft, 0.001f)
        assertEquals(metrics.timeMax, right.labelLeft, 0.001f)
    }
}

class PeriodHeaderAdaptiveFontTest {
    private val font = PeriodHeaderAdaptiveFont

    /**
     * v3 连续自适应: 字号随卡高连续变化, 上下限钳制可读区间.
     * 52dp 卡高 → label=16 (上界), 30dp → label≈11.5, 极小卡按比例不硬钳.
     */

    @Test
    fun preview_and_grid_use_the_same_font_for_the_same_card() {
        val preview = font.forPreview(
            cardWidthSp = 240f,
            cardHeightSp = 52f,
        )
        val grid = font.forColumn(
            cardWidthSp = 240f,
            cardHeightSp = 52f,
            rows = listOf(
                PeriodHeaderAdaptiveFont.RowConstraint(
                    inkWidthSp = 86f,
                    timeMaxWidthSp = 58f,
                    labelWidthSp = 72f,
                ),
            ),
        )
        assertEquals(preview.timeSize, grid.timeSize, 0.001f)
        assertEquals(preview.labelSize, grid.labelSize, 0.001f)
    }

    @Test
    fun standard_card_52dp_hits_comfortable_upper_bound() {
        val v = font.compute(
            cardWidthSp = 240f, cardHeightSp = 52f,
            inkWidthSp = 86f, timeMaxWidthSp = 58f, labelWidthSp = 72f,
        )
        // h=52 → cap = 52/2.6 = 20 → 锁 16/14, 老花眼舒适
        assertEquals(16f, v.labelSize, 0.001f)
        assertEquals(14f, v.timeSize, 0.001f)
    }

    @Test
    fun widget_card_44dp_still_at_comfortable_bound() {
        val v = font.compute(
            cardWidthSp = 200f, cardHeightSp = 44f,
            inkWidthSp = 80f, timeMaxWidthSp = 56f, labelWidthSp = 60f,
        )
        // h=44 → cap = 44/2.6 = 16.92 → 锁 16/14
        assertEquals(16f, v.labelSize, 0.001f)
        assertEquals(14f, v.timeSize, 0.001f)
    }

    @Test
    fun mid_card_39dp_uses_proportional_size_not_clamped() {
        val v = font.compute(
            cardWidthSp = 100f, cardHeightSp = 39f,
            inkWidthSp = 60f, timeMaxWidthSp = 30f, labelWidthSp = 36f,
        )
        // h=39 → cap = 39/2.6 = 15 → 落 [11,16] 区间, 不触底也不触顶
        assertEquals(15f, v.labelSize, 0.001f)
        assertEquals(14f, v.timeSize, 0.001f)
    }

    @Test
    fun tiny_card_30dp_floors_near_minimum() {
        val v = font.compute(
            cardWidthSp = 60f, cardHeightSp = 30f,
            inkWidthSp = 40f, timeMaxWidthSp = 18f, labelWidthSp = 24f,
        )
        // h=30 → cap = 30/2.6 = 11.54, 接近下限但仍有视觉余地
        assertTrue("labelSize should be near 11.5: got ${v.labelSize}",
            v.labelSize in 11f..12f)
        assertTrue(v.timeSize >= 10f)
    }

    @Test
    fun extreme_tiny_card_shrinks_proportionally_not_floored() {
        // 极矮卡 (小组件低高度头行) — 不硬钳 11sp, 否则三行文字会竖向溢出
        val v = font.compute(
            cardWidthSp = 30f, cardHeightSp = 18f,
            inkWidthSp = 24f, timeMaxWidthSp = 12f, labelWidthSp = 16f,
        )
        // h=18 → cap = 18/2.6 = 6.92, < 11 → 按比例 6.92, 不硬钳
        assertTrue("labelSize should be < MIN_LABEL_SP when card too tiny: got ${v.labelSize}",
            v.labelSize < 11f)
        assertTrue(v.labelSize > 5f) // 仍 > 0.1 兜底
    }

    @Test
    fun font_shrinks_when_ink_exceeds_card_width() {
        // 装不下时等比回缩, label/15.5, time/13.5 之后 × scale
        val v = font.compute(
            cardWidthSp = 20f, cardHeightSp = 52f,
            inkWidthSp = 80f, timeMaxWidthSp = 40f, labelWidthSp = 60f,
        )
        val unscaled = font.compute(
            cardWidthSp = 200f, cardHeightSp = 52f,
            inkWidthSp = 80f, timeMaxWidthSp = 40f, labelWidthSp = 60f,
        )
        // scale = 20/80 = 0.25, 16×0.25 = 4, 14×0.25 = 3.5
        assertTrue(v.labelSize < unscaled.labelSize)
        assertTrue(v.timeSize < unscaled.timeSize)
    }

    @Test
    fun label_size_is_always_larger_than_time_size() {
        // 配对梯度: label > time, 差 1~2sp (16/14 顶格差 2, 中间区差 1)
        for (h in listOf(36f, 44f, 52f, 60f)) {
            val v = font.compute(
                cardWidthSp = 200f, cardHeightSp = h,
                inkWidthSp = 80f, timeMaxWidthSp = 50f, labelWidthSp = 60f,
            )
            assertTrue("label should exceed time at h=$h: ${v.labelSize} vs ${v.timeSize}",
                v.labelSize > v.timeSize)
            assertTrue("gap should stay small at h=$h: ${v.labelSize - v.timeSize}",
                v.labelSize - v.timeSize <= 2.01f)
        }
    }

    @Test
    fun preview_skips_ink_guard_keeping_baseline() {
        // 预览宽 ink 也不缩 (预览本身按内容宽度撑卡)
        val v = font.forPreview(cardWidthSp = 240f, cardHeightSp = 52f)
        assertEquals(16f, v.labelSize, 0.001f)
        assertEquals(14f, v.timeSize, 0.001f)
    }

    @Test
    fun column_picks_widest_row_to_decide_label_size() {
        // forColumn 取最紧约束 — 行内字号统一
        val shared = font.forColumn(
            cardWidthSp = 240f,
            cardHeightSp = 52f,
            rows = listOf(
                PeriodHeaderAdaptiveFont.RowConstraint(80f, 58f, 72f),
                PeriodHeaderAdaptiveFont.RowConstraint(120f, 58f, 90f),  // 最宽
            ),
        )
        val widestRow = font.compute(
            cardWidthSp = 240f, cardHeightSp = 52f,
            inkWidthSp = 120f, timeMaxWidthSp = 58f, labelWidthSp = 90f,
        )
        assertEquals(widestRow.timeSize, shared.timeSize, 0.001f)
        assertEquals(widestRow.labelSize, shared.labelSize, 0.001f)
    }

    @Test
    fun legacy_column_width_is_the_widest_measured_row() {
        assertEquals(52f, legacyColumnWidthDp(listOf(48f, 52f, 45f)), 0.001f)
    }
}