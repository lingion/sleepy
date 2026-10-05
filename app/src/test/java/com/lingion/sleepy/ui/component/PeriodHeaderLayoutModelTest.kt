package com.lingion.sleepy.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodHeaderLayoutModelTest {
    private val metrics = PeriodHeaderMetrics(40f, 36f, 28f, false)

    @Test
    fun hide_time_pref_key_literal_is_locked() {
        // C1: 键字面量是 prefs/网格/widget/设置页四处共享的契约, 钉死防漂移。
        assertEquals("period_header_hide_time", com.lingion.sleepy.util.AppPrefs.KEY_PERIOD_HEADER_HIDE_TIME)
    }

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
        // 预览宽 ink 不触发墨迹缩字 (预览本身按内容宽度撑卡);
        // 但高度封顶仍生效 — 精确公式 (52+2.5)/3.75 = 14.53sp, 老值 16 只在
        // 估算比例 2.6 下凑巧不触顶。预览与网格同源同字号, 三行永不重叠。
        val v = font.forPreview(cardWidthSp = 240f, cardHeightSp = 52f)
        assertEquals((52f + 2.5f) / 3.75f, v.labelSize, 0.001f)
        assertEquals((52f + 2.5f) / 3.75f - 1f, v.timeSize, 0.001f)
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
 * 用户 2026-10-03 第二轮定稿 (老式表头 dash 对齐):
 *   - 对齐轴 = 时间串 "08:00-09:35" 中间那个 "-" 的中点 (非字符串整体中点 —
 *     start/end 宽度不等, 串居中≠dash对齐, 上轮"更不齐"的根因);
 *   - 整列所有行的 dash 落在同一 X; 标签也以该轴居中;
 *   - 列宽 = 左右两侧最大延伸 + pad×2 — 最宽行的墨迹两侧同样流出 pad 余量。
 */
class LegacyDashColumnGeometryTest {

    private val pad = 3f

    // A: 对称短行; B: 长标签 + 右半长
    // 显式传 dashPx=0 (默认), 让 rowB 的"完整时间串"=start+end=20+45=65; 真实渲染
    // dashPx>0 由 legacyRowInk 测得, 见 column_width_accommodates_full_time_string_*
    private val rowA = LegacyDashRowInk(labelPx = 16f, timeLeftPx = 30f, dashPx = 0f, timeRightPx = 30f)
    private val rowB = LegacyDashRowInk(labelPx = 80f, timeLeftPx = 20f, dashPx = 0f, timeRightPx = 45f)
    private val rows = listOf(rowA, rowB)

    @Test
    fun every_row_dash_falls_on_shared_dash_center() {
        val g = legacyDashColumnPx(rows, pad)
        for ((i, row) in rows.withIndex()) {
            // 渲染事实 (2026-10-04 设备实锤): dash Text 盒以 startW 为偏移起点,
            // dash 字形中心 = timeLeft + timeLeftPx + dashPx/2 (盒左缘 + 半 dash 宽)。
            // 旧断言把 dash 盒左缘当轴心 → 实机 dash 全列偏右 dashPx/2 ≈ 4px。
            assertEquals(
                "行$i dash 中心应落在共享轴上",
                g.dashCenterX, g.placements[i].timeLeft + row.timeLeftPx + row.dashPx / 2f, 0.001f,
            )
        }
    }

    @Test
    fun label_centers_on_shared_dash_axis() {
        val g = legacyDashColumnPx(rows, pad)
        for ((i, row) in rows.withIndex()) {
            assertEquals(
                "行$i 标签应以 dash 轴居中",
                g.dashCenterX, g.placements[i].labelLeft + row.labelPx / 2f, 0.001f,
            )
        }
    }

    @Test
    fun widest_row_keeps_padding_margin_on_both_sides() {
        val g = legacyDashColumnPx(rows, pad)
        // 左侧最大延伸 = max(80/2, 30, 20) = 40 → dash 轴 = pad + 40
        assertEquals(pad + 40f, g.dashCenterX, 0.001f)
        // 渲染事实: dash 坐在轴上, 时间串左缘 = 轴 − timeLeftPx →
        // 轴右侧只需要 max(label/2, dashPx + timeRightPx)。
        // 旧断言用整串宽 timeLeft+dash+right 当右延伸 = timeLeftPx 双算,
        // 卡片凭空宽出一个"开始时间"宽 (2026-10-04 设备实锤右侧空白 25dp)。
        // 右侧 = max(40, 0+45) = 45 → 列宽 = 40 + 45 + 2*pad = 91
        assertEquals(40f + 45f + 2f * pad, g.columnWidthPx, 0.001f)
        // 用户原话: "要让最宽的那一行，余量也流出来" — B 行左墨迹缘=pad, 右墨迹缘=列宽-pad
        assertEquals(pad, g.placements[1].labelLeft, 0.001f)
        assertEquals(
            g.columnWidthPx - pad,
            g.dashCenterX + rowB.dashPx + rowB.timeRightPx,
            0.001f,
        )
    }

    @Test
    fun all_ink_stays_inside_column() {
        val g = legacyDashColumnPx(rows, pad)
        for ((i, row) in rows.withIndex()) {
            val p = g.placements[i]
            assertTrue("行$i 标签越左", p.labelLeft >= -0.001f)
            assertTrue("行$i 时间串越左", p.timeLeft >= -0.001f)
            assertTrue("行$i 标签越右", p.labelLeft + row.labelPx <= g.columnWidthPx + 0.001f)
            assertTrue(
                "行$i 时间串越右",
                p.timeLeft + row.timeLeftPx + row.timeRightPx <= g.columnWidthPx + 0.001f,
            )
        }
    }

    @Test
    fun empty_column_returns_zero_geometry() {
        val g = legacyDashColumnPx(emptyList(), pad)
        assertEquals(0f, g.columnWidthPx, 0.001f)
        assertTrue(g.placements.isEmpty())
    }

    // 上轮(2026-10-03)真翻车: timeString 被拆成 (start, end) 传给模型,
    // 中间那个 "-" 字符本身的宽度丢了 → 列宽 < 完整时间串, 渲染时尾部被 .clip 截掉.
    // 修: 模型必须收三段 (start, dash, end), 用完整串宽做右延伸.
    @Test
    fun column_width_accommodates_full_time_string_including_dash() {
        val rows = listOf(
            LegacyDashRowInk(labelPx = 16f, timeLeftPx = 30f, dashPx = 6f, timeRightPx = 30f),
            LegacyDashRowInk(labelPx = 60f, timeLeftPx = 32f, dashPx = 6f, timeRightPx = 45f),
        )
        val g = legacyDashColumnPx(rows, pad)
        // 渲染事实 (2026-10-04 设备实锤 dash 比标签中心偏右 dashPx/2):
        // dash 字形中心 = timeLeft + timeLeftPx + dashPx/2, 与标签中心同轴 →
        // 左延伸 = max(label/2, timeLeft + dashPx/2) = max(30, 33, 35) = 35;
        // 右延伸 = max(label/2, dashPx/2 + timeRight) = max(30, 48) = 48;
        // 列宽 = 35 + 48 + 2*pad = 95
        assertEquals(pad + 35f, g.dashCenterX, 0.001f)
        assertEquals(35f + 48f + 2f * pad, g.columnWidthPx, 0.001f)
        // 每行 dash 字形中心都落在共享轴上
        for ((i, row) in rows.withIndex()) {
            val p = g.placements[i]
            assertEquals(
                "行$i dash 字形中心应=共享轴",
                g.dashCenterX, p.timeLeft + row.timeLeftPx + row.dashPx / 2f, 0.001f,
            )
        }
        // 第 2 行时间串完整右缘应 <= 列宽 (含 pad)
        val p = g.placements[1]
        val fullTimeRight = p.timeLeft + rows[1].timeLeftPx + rows[1].dashPx + rows[1].timeRightPx
        assertTrue("完整时间串右缘必须不越列: $fullTimeRight vs ${g.columnWidthPx}",
            fullTimeRight <= g.columnWidthPx + 0.001f)
    }
}

/**
 * 用户 2026-10-03 定稿续 (union 包络垂直):
 *   ① 两行墨迹是一个整体矩形 — 卡片对这个整体做水平 + 垂直居中,
 *      不是每行各自居中;
 *   ② 垂直永不重叠 — 每行高度 ≥ 该行字体行高;
 *   ③ 高度不够 → 两行同比例缩小, 不许压缩行距或让文字重叠。
 */
class LegacyDashVerticalFitTest {

    // 实测行高: label 14sp×density / time 13sp×density 的典型形状
    private val row = LegacyDashRowInk(
        labelPx = 20f, timeLeftPx = 30f, dashPx = 6f, timeRightPx = 30f,
        labelHeightPx = 14f, timeHeightPx = 13f,
    )

    @Test
    fun tall_card_centers_union_vertically() {
        // 卡高 54 → union 27 → 上下余量 (54-27)/2 = 13.5
        val fit = legacyVerticalFit(row, cardContentHeightPx = 54f)
        assertEquals(13.5f, fit.labelTopPx, 0.001f)
        assertEquals(13.5f + 14f, fit.timeTopPx, 0.001f)
        assertEquals(1f, fit.fontScale, 0.001f)
    }

    @Test
    fun short_card_shrinks_both_lines_by_same_factor() {
        // 卡高 20 < union 27 → scale = 20/27 = 0.74
        val fit = legacyVerticalFit(row, cardContentHeightPx = 20f)
        assertEquals(0f, fit.labelTopPx, 0.001f)  // 顶对齐
        assertEquals(14f * (20f / 27f), fit.timeTopPx, 0.001f)  // labelTop + scaled labelH
        assertEquals(20f / 27f, fit.fontScale, 0.01f)
    }

    @Test
    fun column_uses_widest_row_for_scale() {
        // 列级: 第 2 行 label 更宽
        val rows = listOf(
            LegacyDashRowInk(16f, 30f, 6f, 30f, 14f, 13f),
            LegacyDashRowInk(80f, 30f, 6f, 30f, 14f, 13f),  // 最宽
        )
        // max labelH = 14, max timeH = 13 → union = 27
        val col = legacyColumnVerticalFit(rows, cardContentHeightPx = 27f)
        assertEquals(1f, col.fontScale, 0.001f)  // 刚好 fit
        assertEquals(0f, col.labelTopPx, 0.001f)  // 刚好居中
    }

    @Test
    fun column_shrinks_to_tightest_row() {
        val rows = listOf(
            LegacyDashRowInk(16f, 30f, 6f, 30f, 14f, 13f),
            LegacyDashRowInk(80f, 30f, 6f, 30f, 14f, 13f),
        )
        // card 20 < union 27 → scale = 20/27
        val col = legacyColumnVerticalFit(rows, cardContentHeightPx = 20f)
        assertEquals(20f / 27f, col.fontScale, 0.01f)
    }

    // 行间语义间距 (用户原话 2026-10-04: 行间距不对) —
    // label 与 time 之间恒留一个 gap (px), 缩字不压行距,
    // 高度不够时 gap 优先被吃掉 (gap ≤ 缩字前的总余量)。
    @Test
    fun row_gap_holds_when_card_fits() {
        // cardH 54, union 27, gap 4 → 可用 = 54-27-4 = 23, 上下均分 = 11.5
        val col = legacyColumnVerticalFit(
            listOf(row), cardContentHeightPx = 54f, rowGapPx = 4f,
        )
        assertEquals(1f, col.fontScale, 0.001f)
        assertEquals(11.5f, col.labelTopPx, 0.001f)
        assertEquals(11.5f + 14f + 4f, col.timeTopPx, 0.001f)
    }

    @Test
    fun row_gap_shrinks_before_text_when_card_tight() {
        // cardH 30, union 27, gap 4 → 余量 -1: gap 被吞 1px 剩 3,
        // 字号仍 1.0; 居中余量 (30-27-3)/2 = 0 → labelTop=0。
        val col = legacyColumnVerticalFit(
            listOf(row), cardContentHeightPx = 30f, rowGapPx = 4f,
        )
        assertEquals(1f, col.fontScale, 0.001f)
        assertEquals(0f, col.labelTopPx, 0.001f)
        assertEquals(0f + 14f + 3f, col.timeTopPx, 0.001f)
    }

    @Test
    fun row_gap_zero_when_card_too_short_even_after_gap_shrink() {
        // cardH 20, union 27, gap 4 → 余量 -11 → 先吞 gap 至 0, 再触发缩字
        // 等效缩字按 union=27, 卡 20 → scale = 20/27 (用户定稿不压行距)
        val col = legacyColumnVerticalFit(
            listOf(row), cardContentHeightPx = 20f, rowGapPx = 4f,
        )
        assertEquals(20f / 27f, col.fontScale, 0.001f)
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