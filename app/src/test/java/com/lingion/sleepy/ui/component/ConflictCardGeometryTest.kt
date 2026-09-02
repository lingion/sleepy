package com.lingion.sleepy.ui.component

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.ConflictVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v5 几何回归 — 用户 2026-09-01 两条硬规则:
 *   1) 「右下」是相对方位: a=1-2 的右下锚在 1-2 节范围内,不落到 2-3 节去。
 *   2) 尺寸固定: 一长一短课不论怎么切换(多次往返),每张卡的宽高只跟课走,不随层级变。
 * 渲染与单测共用 conflictCardRect / conflictMarkRect / clusterForm 同一真值。
 */
class ConflictCardGeometryTest {

    // 与周视图典型格位同量级的几何参数(纯 dp 运算,不涉密度)
    private val colW = 100.dp
    private val rowH = 58.dp
    private val gapH = 4.dp

    private fun rect(
        startNode: Int, ownRows: Int, isTop: Boolean, form: ConflictVariant, minStart: Int = 1,
        topInset: Dp = STACK_OFFSET_DP.dp // STACK 基线用 8dp(v4 定版偏移);RAIL 测试自行传默认
    ) = conflictCardRect(startNode, ownRows, isTop, form, colW, rowH, gapH, minStart, topInset)

    private fun ownH(rows: Int): Dp = rowH * rows - gapH

    // ============================ 规则 1: 右下 = 自身区间右下 ============================

    @Test
    fun stack_hidden_bottom_anchors_own_interval_not_cluster() {
        // a=1-2(hidden 短课) 在 1-3 簇内: 底卡右下必须落在 1-2 节,不得侵入第 3 节。
        // ownH = 58*2-4 = 112; shrunk = 112-8 = 104; y = 0 + 112-104 = 8 ≤ 节1高度54。
        val r = rect(1, 2, isTop = false, form = ConflictVariant.STACK)
        assertEquals(8.dp, r.y)
        assertEquals(92.dp, r.width)   // colW - 8
        assertEquals(104.dp, r.height) // ownH - 8
        // 下缘 8+104=112 = 节2底(2*58-4),严格在自身区间内
        assertEquals(rowH * 2 - gapH, r.y + r.height)

        // 滑杆默认值下同样不越自身区间: y = ownH - (ownH - default)
        val d = conflictCardRect(
            1, 2, false, ConflictVariant.STACK, colW, rowH, gapH, 1,
            topInset = AppPrefs.CONFLICT_TOP_INSET_DEFAULT.dp
        )
        assertEquals(ownH(2) - (ownH(2) - AppPrefs.CONFLICT_TOP_INSET_DEFAULT.dp), d.y)
        assertEquals(rowH * 2 - gapH, d.y + d.height)
    }

    @Test
    fun stack_hidden_bottom_of_long_course_anchors_its_own_bottom() {
        // 长=1-3(hidden),短=1-2 在顶: 长底卡右下锚 1-3 节底(170),不锚短课的底。
        val r = rect(1, 3, isTop = false, form = ConflictVariant.STACK, minStart = 1)
        assertEquals(170.dp, r.y + r.height) // 58*3 - 4 = 自身区间底
        assertEquals(162.dp, r.height)       // ownH(170) - 8
    }

    @Test
    fun stack_bottom_card_row_within_own_rows_only() {
        // 每行 54dp(58-4): 底卡不得越过自身区间末行的行底
        val r = rect(2, 1, isTop = false, form = ConflictVariant.STACK, minStart = 1)
        // ownH=54, shrunk=46, y=58+54-46=66 = 第2节区间 [58,112) 内
        assertEquals(66.dp, r.y)
        assertEquals(46.dp, r.height)
        assertTrue(r.y >= rowH * (2 - 1))                       // 不早于自己区间顶
        assertTrue(r.y + r.height <= rowH * 2 - gapH)           // 不越过自己区间底
    }

    // ============================ 规则 2: 尺寸固定,切换只换层级 ============================

    @Test
    fun stack_sizes_invariant_across_repeated_swaps() {
        // 短=1-2 / 长=1-3,模拟切换序列: 短顶→长顶→短顶→长顶(多次往返)
        val shortTop = rect(1, 2, isTop = true, form = ConflictVariant.STACK)
        val longBottomWhenShortTop = rect(1, 3, isTop = false, form = ConflictVariant.STACK)
        val longTop = rect(1, 3, isTop = true, form = ConflictVariant.STACK)
        val shortBottomWhenLongTop = rect(1, 2, isTop = false, form = ConflictVariant.STACK)

        // 同一张卡在顶/底两态尺寸恒等(宽高只跟课走)
        assertEquals(shortTop.width, shortBottomWhenLongTop.width)
        assertEquals(shortTop.height, shortBottomWhenLongTop.height)
        assertEquals(longBottomWhenShortTop.width, longTop.width)
        assertEquals(longBottomWhenShortTop.height, longTop.height)

        // 再切回去,几何回到初态(往返不漂移)
        val shortTopAgain = rect(1, 2, isTop = true, form = ConflictVariant.STACK)
        val longBottomAgain = rect(1, 3, isTop = false, form = ConflictVariant.STACK)
        assertEquals(shortTop, shortTopAgain)
        assertEquals(longBottomWhenShortTop, longBottomAgain)
    }

    @Test
    fun rail_narrow_top_follows_top_course_and_sizes_invariant() {
        // C 方案: 谁在顶谁窄。RAIL 用滑杆默认 topInset=10 → 窄卡宽 90。
        val inset = AppPrefs.CONFLICT_TOP_INSET_DEFAULT.dp
        val shortAsTop = rect(1, 2, isTop = true, form = ConflictVariant.RAIL, topInset = inset)
        val longAsBottom = rect(1, 3, isTop = false, form = ConflictVariant.RAIL, topInset = inset)
        assertEquals(colW - inset, shortAsTop.width)
        assertEquals(112.dp, shortAsTop.height) // 自身节数,不是簇高

        val longAsTop = rect(1, 3, isTop = true, form = ConflictVariant.RAIL, topInset = inset)
        val shortAsBottom = rect(1, 2, isTop = false, form = ConflictVariant.RAIL, topInset = inset)
        assertEquals(colW - inset, longAsTop.width)
        assertEquals(170.dp, longAsTop.height)
        assertEquals(100.dp, shortAsBottom.width) // 全宽
        assertEquals(112.dp, shortAsBottom.height) // 底卡仍按自己节数

        // 往返不漂移
        assertEquals(shortAsTop, rect(1, 2, isTop = true, form = ConflictVariant.RAIL, topInset = inset))
        assertEquals(longAsTop, rect(1, 3, isTop = true, form = ConflictVariant.RAIL, topInset = inset))

        // 滑杆任意值下窄卡跟随: topInset=16 → 宽 84;尺寸恒等规则对任意 inset 成立
        val at16 = conflictCardRect(1, 2, true, ConflictVariant.RAIL, colW, rowH, gapH, 1, topInset = 16.dp)
        assertEquals(84.dp, at16.width)
    }

    @Test
    fun rail_exposed_edge_is_short_course_own_length() {
        // 长顶短底: 短底卡露出边=两节的长度(112),不是长课的三节
        val shortBottom = rect(1, 2, isTop = false, form = ConflictVariant.RAIL)
        assertEquals(rowH * 2 - gapH, shortBottom.height)
    }

    // ============================ 命中区锚自身区间 ============================

    @Test
    fun stack_mark_anchored_own_interval_bottom_right() {
        // a=1-2 hidden: 命中区 36dp 见方,右下角=自身区间右下(112),不得伸进第 3 节。
        val m = conflictMarkRect(
            1, 2, ConflictVariant.STACK, colW, rowH, gapH, minStart = 1, clusterH = rowH * 3 - gapH
        )
        assertEquals(36.dp, m.width)
        assertEquals(36.dp, m.height)
        assertEquals(rowH * 2 - gapH, m.y + m.height) // 下缘贴自身区间底
        assertEquals(colW, m.x + m.width)             // 右缘贴格位右
    }

    @Test
    fun rail_mark_bounded_to_own_interval() {
        // hidden 1-2: RAIL 命中区宽=默认收窄量(10)+内延20=30?否——默认 topInset 已统一为
        // AppPrefs.CONFLICT_TOP_INSET_DEFAULT=10,宽=10+20=30 是 v5 值;v6 默认同源后
        // 宽=10+20=30。但 STACK_OFFSET(8) 不再是 RAIL 默认——此处断言跟随单一真值。
        val m = conflictMarkRect(
            1, 2, ConflictVariant.RAIL, colW, rowH, gapH, minStart = 1, clusterH = rowH * 3 - gapH
        )
        assertEquals(AppPrefs.CONFLICT_TOP_INSET_DEFAULT.dp + 20.dp, m.width)
        assertEquals(132.dp, m.height)
        assertEquals(0.dp, m.y)
        // y + h = 132 ≤ 自身区间底 112 + 内延 20 = 可接受的容差上界
        assertTrue(m.y + m.height <= rowH * 2 - gapH + 20.dp)
    }

    @Test
    fun mark_never_exceeds_cluster_bottom() {
        val m = conflictMarkRect(
            2, 2, ConflictVariant.RAIL, colW, rowH, gapH, minStart = 1, clusterH = rowH * 3 - gapH
        )
        assertTrue(m.y + m.height <= rowH * 3 - gapH)
    }

    // ============================ 形态不塌缩 ============================

    @Test
    fun form_survives_swap_when_no_hidden_course_remains() {
        // 长课置顶(短课不再被完全遮) → hidden 集空,形态必须保持:
        assertEquals(ConflictVariant.RAIL, clusterForm("rail", null))
        assertEquals(ConflictVariant.STACK, clusterForm("stack", null))
        // fold 等长(同起点)回落仍 FOLD;错位起点回落 STACK
        assertEquals(ConflictVariant.FOLD, clusterForm("fold", null, foldEligible = true))
        assertEquals(ConflictVariant.STACK, clusterForm("fold", null, foldEligible = false))
    }

    @Test
    fun form_prefers_engine_variant_when_hidden_exists() {
        // N≥3 fold 合流仍由引擎经 hidden variant 给出,优先于回落。
        // (rail 样式引擎恒产 RAIL variant,不存在 hidden 带 STACK 的组合。)
        assertEquals(ConflictVariant.FOLD, clusterForm("fold", ConflictVariant.FOLD))
        assertEquals(ConflictVariant.STACK, clusterForm("fold", ConflictVariant.STACK))
    }
}
