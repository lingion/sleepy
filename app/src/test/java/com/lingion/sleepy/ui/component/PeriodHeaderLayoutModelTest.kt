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
    fun standard_card_52dp_size_is_exact_fit_not_comfortable_bound() {
        val v = font.compute(
            cardWidthSp = 240f, cardHeightSp = 52f,
            inkWidthSp = 86f, timeMaxWidthSp = 58f, labelWidthSp = 72f,
        )
        // 用户令 2026-10-03: 三行严格分隔是最低下限, 字号让位于不重叠。
        // h=52 → label = (52+2.5)/3.75 = 14.533, 三行行盒恰装卡高 (旧 2.6 公式给 16/14,
        // 实际渲染占 55sp > 52sp → 重叠 — 本次修复的正是这个)。
        assertEquals((52f + 2.5f) / 3.75f, v.labelSize, 0.001f)
        assertEquals(v.labelSize - 1f, v.timeSize, 0.001f)
    }

    @Test
    fun widget_card_44dp_fits_three_rows_exactly() {
        val v = font.compute(
            cardWidthSp = 200f, cardHeightSp = 44f,
            inkWidthSp = 80f, timeMaxWidthSp = 56f, labelWidthSp = 60f,
        )
        // h=44 → label = (44+2.5)/3.75 = 12.4, 三行行盒 2.5×11.4+1.25×12.4 = 44 ≤ 卡高
        assertEquals((44f + 2.5f) / 3.75f, v.labelSize, 0.001f)
        assertEquals(v.labelSize - 1f, v.timeSize, 0.001f)
    }

    @Test
    fun mid_card_39dp_uses_proportional_size_not_clamped() {
        val v = font.compute(
            cardWidthSp = 100f, cardHeightSp = 39f,
            inkWidthSp = 60f, timeMaxWidthSp = 30f, labelWidthSp = 36f,
        )
        // h=39 → label = (39+2.5)/3.75 = 11.067, 落 [11,16] 区间下沿附近
        assertEquals((39f + 2.5f) / 3.75f, v.labelSize, 0.001f)
        assertEquals(v.labelSize - 1f, v.timeSize, 0.001f)
    }

    @Test
    fun tiny_card_30dp_prefers_no_overlap_over_minimum_size() {
        val v = font.compute(
            cardWidthSp = 60f, cardHeightSp = 30f,
            inkWidthSp = 40f, timeMaxWidthSp = 18f, labelWidthSp = 24f,
        )
        // h=30 → label = (30+2.5)/3.75 = 8.667 < MIN_LABEL_SP → 比例缩不硬钳
        // (用户令 2026-10-03: 宁缩字不重叠, 硬钳 11sp 必致三行咬合)
        assertEquals((30f + 2.5f) / 3.75f, v.labelSize, 0.001f)
        assertTrue(v.labelSize < 11f)
    }

    @Test
    fun extreme_tiny_card_shrinks_proportionally_not_floored() {
        // 极矮卡 (小组件低高度头行) — 不硬钳 11sp, 否则三行文字会竖向溢出
        val v = font.compute(
            cardWidthSp = 30f, cardHeightSp = 18f,
            inkWidthSp = 24f, timeMaxWidthSp = 12f, labelWidthSp = 16f,
        )
        // h=18 → label = (18+2.5)/3.75 = 5.467, < 11 → 按比例缩放
        assertTrue("labelSize should be < MIN_LABEL_SP when card too tiny: got ${v.labelSize}",
            v.labelSize < 11f)
        assertTrue(v.labelSize > 5f) // 仍 > 0.1 兜底
    }

    @Test
    fun three_rows_never_overlap_for_any_card_height() {
        // 用户令 2026-10-03 最低下限: 每行行盒 ≥ 字体占高, 三行总占高 ≤ 卡高。
        // 渲染行高式: 总占高 = 2.5×timeSize + 1.25×labelSize (lineHeight=1.25)。
        for (h in listOf(14f, 18f, 22f, 26f, 30f, 36f, 44f, 52f, 60f, 80f, 96f)) {
            val v = font.compute(
                cardWidthSp = 200f, cardHeightSp = h,
                inkWidthSp = 0f, timeMaxWidthSp = 0f, labelWidthSp = 0f,
            )
            val occupied = v.timeSize * 2.5f + v.labelSize * 1.25f
            assertTrue(
                "h=$h: 三行行盒总高 $occupied 超过卡高 → 必重叠 (label=${v.labelSize}, time=${v.timeSize})",
                occupied <= h + 0.001f,
            )
        }
    }

    @Test
    fun width_shrink_also_preserves_no_overlap() {
        // 墨迹闸等比回缩只会让字号更小 → 行盒更矮, 不重叠不变量自动保持
        val v = font.compute(
            cardWidthSp = 20f, cardHeightSp = 30f,
            inkWidthSp = 80f, timeMaxWidthSp = 40f, labelWidthSp = 60f,
        )
        val occupied = v.timeSize * 2.5f + v.labelSize * 1.25f
        assertTrue("width-shrunk font must still fit h=30: occupied=$occupied", occupied <= 30.001f)
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
        // 预览宽 ink 也不缩 (预览本身按内容宽度撑卡);
        // 字号仍是高度驱动 — 52dp 卡 → (52+2.5)/3.75 = 14.533, 三行严格分隔 (2026-10-04)
        val v = font.forPreview(cardWidthSp = 240f, cardHeightSp = 52f)
        assertEquals((52f + 2.5f) / 3.75f, v.labelSize, 0.001f)
        assertEquals(v.labelSize - 1f, v.timeSize, 0.001f)
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

/**
 * 用户定稿 2026-09-30 (网格表头列统一):
 *   ① 同列所有卡片宽 = 最宽行的包络宽 (不再逐行收口);
 *   ② 每行三元素 (开始时间/标签/结束时间) 各自以最宽行对应元素的
 *      middle point 对齐 — 保持行间固有错开关系, 整列一致。
 *
 * solveColumnPlacement 输入全列各行的 metrics, 输出每行的最终排布
 * (统一卡片宽 + 各元素对齐基准行中点)。
 */
class PeriodHeaderColumnUniformPlacementTest {

    private val u = 0f

    /** 典型列: 第 1000 节行最宽, 其余行窄。 */
    private val wideRow = PeriodHeaderMetrics(
        startWidth = 34f, endWidth = 34f, labelWidth = 60f, showX = false,
    )
    private val narrowRow = PeriodHeaderMetrics(
        startWidth = 30f, endWidth = 30f, labelWidth = 16f, showX = false,
    )

    @Test
    fun all_rows_share_the_widest_row_card_width() {
        val placements = solveColumnPlacement(listOf(wideRow, narrowRow), u)
        assertEquals(2, placements.size)
        assertEquals(
            "窄行卡片宽必须等于最宽行卡片宽",
            placements[0].contentWidth, placements[1].contentWidth, 0.001f,
        )
    }

    @Test
    fun widest_row_placement_unchanged_from_per_row_solution() {
        val placements = solveColumnPlacement(listOf(wideRow, narrowRow), u)
        val alone = wideRow.solvePlacement(u)
        assertEquals(alone.timeBaseLeft, placements[0].timeBaseLeft, 0.001f)
        assertEquals(alone.labelLeft, placements[0].labelLeft, 0.001f)
        assertEquals(alone.contentWidth, placements[0].contentWidth, 0.001f)
    }

    @Test
    fun every_row_element_midpoints_align_to_widest_row() {
        val placements = solveColumnPlacement(listOf(wideRow, narrowRow), u)
        val base = placements[0]
        val other = placements[1]
        // 基准行 (最宽) 自身中点
        val baseTimeMid = base.timeBaseLeft + wideRow.startWidth / 2f
        val baseEndMid = base.timeBaseLeft + wideRow.endWidth / 2f
        val baseLabelMid = base.labelLeft + wideRow.labelWidth / 2f
        // 窄行中点 = 各自左缘 + 自身宽 / 2
        assertEquals(baseTimeMid, other.timeBaseLeft + narrowRow.startWidth / 2f, 0.001f)
        assertEquals(baseEndMid, other.timeBaseLeft + narrowRow.endWidth / 2f, 0.001f)
        assertEquals(baseLabelMid, other.labelLeft + narrowRow.labelWidth / 2f, 0.001f)
    }

    @Test
    fun staggered_relationship_is_preserved_not_flattened_to_center() {
        // 悬挂 u=-1: 标签右缘贴时间块左缘 — 标签中点在时间中点左侧。
        // 整列对齐后各元素中点仍逐元素对应, 不等于全部压到同一中点。
        val placements = solveColumnPlacement(listOf(wideRow, narrowRow), -1f)
        val base = placements[0]
        val other = placements[1]
        val baseTimeMid = base.timeBaseLeft + wideRow.startWidth / 2f
        val baseLabelMid = base.labelLeft + wideRow.labelWidth / 2f
        val otherTimeMid = other.timeBaseLeft + narrowRow.startWidth / 2f
        val otherLabelMid = other.labelLeft + narrowRow.labelWidth / 2f
        // u=-1 下标签中点 ≠ 时间中点 (错开保留)
        assertTrue(baseLabelMid < baseTimeMid)
        // 窄行同样错开, 且错开方向一致 (两侧标签都在时间中点左侧)
        assertTrue(otherLabelMid < otherTimeMid)
    }

    @Test
    fun empty_column_returns_empty_list() {
        assertTrue(solveColumnPlacement(emptyList(), u).isEmpty())
    }

    @Test
    fun single_row_degenerates_to_per_row_placement() {
        val placements = solveColumnPlacement(listOf(narrowRow), u)
        val alone = narrowRow.solvePlacement(u)
        assertEquals(alone.timeBaseLeft, placements[0].timeBaseLeft, 0.001f)
        assertEquals(alone.labelLeft, placements[0].labelLeft, 0.001f)
        assertEquals(alone.contentWidth, placements[0].contentWidth, 0.001f)
    }
}