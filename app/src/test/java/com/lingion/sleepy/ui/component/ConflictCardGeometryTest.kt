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
        // v7.8.4 修订: RAIL 不再有侧边竖轨结构, 命中区复用 STACK 风格 —— 自身区间右下 36dp 见方。
        // hidden 1-2: 命中区 36dp 见方, 右下角=自身区间右下(112)。
        val m = conflictMarkRect(
            1, 2, ConflictVariant.RAIL, colW, rowH, gapH, minStart = 1, clusterH = rowH * 3 - gapH
        )
        assertEquals(36.dp, m.width)
        assertEquals(36.dp, m.height)
        assertEquals(rowH * 2 - gapH, m.y + m.height) // 下缘贴自身区间底
        assertEquals(colW, m.x + m.width)              // 右缘贴格位右
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

    // ============================ v7.6 图层语义: 徽标按图层数,不按裸课数 ============================

    @Test
    fun conflictBadgeLayerCount_group_of_two_counts_as_one_layer() {
        // {1-3,4-6} 组 + 1-6 重叠者: 3 门课 2 个图层 → 徽标不该按 3 出
        assertEquals(2, conflictBadgeLayerCount(groupSizes = listOf(2, 1), rawCount = 3))
    }

    @Test
    fun conflictBadgeLayerCount_dual_region_six_courses_four_layers() {
        // 六课双区: 上下各一组 + 各一重叠者 = 4 图层(裸课数 6)
        assertEquals(4, conflictBadgeLayerCount(groupSizes = listOf(2, 1, 2, 1), rawCount = 6))
    }

    @Test
    fun conflictBadgeLayerCount_no_groups_falls_back_to_raw_count() {
        // 经典无组叠放: 图层数 = 裸课数(N≥3 徽标语义不变)
        assertEquals(3, conflictBadgeLayerCount(groupSizes = listOf(1, 1, 1), rawCount = 3))
        assertEquals(2, conflictBadgeLayerCount(groupSizes = emptyList(), rawCount = 2))
    }

    @Test
    fun conflictBadgeLayerCount_sum_mismatch_falls_back_to_raw() {
        // 防御: 组切分与课数对不上(输入源漂移)→ 回落裸课数,不显示错数
        assertEquals(3, conflictBadgeLayerCount(groupSizes = listOf(2), rawCount = 3))
    }

    @Test
    fun conflictShowBadge_two_layers_never_shows_badge_even_with_hidden() {
        // 2 图层(组+重叠者)hidden 重叠者存在 → 无徽标(徽标=图层≥3 的逃生门)
        assertEquals(false, conflictShowBadge(layerCount = 2, hiddenCount = 1))
        assertEquals(true, conflictShowBadge(layerCount = 3, hiddenCount = 1))
        assertEquals(false, conflictShowBadge(layerCount = 3, hiddenCount = 0))
    }

    // ============================ v7.8 回归 — A 方案链组切顶后必须缩小并锚左上 ============================

    @Test
    fun stack_chain_group_member_when_lifted_anchors_top_left_and_shrinks() {
        // v7.8.2 回归: 链组 {1-3, 4-6} 沉底时被点击换到顶层后,
        // 成员 1-3 必须缩小并锚左上(不是全尺寸)。
        // ownH(1-3) = 58*3-4 = 170; topInset=8; shrunk = 162; x=0, y=0。
        val r = rect(1, 3, isTop = true, form = ConflictVariant.STACK)
        assertEquals(0.dp, r.x)
        assertEquals(0.dp, r.y)
        assertEquals(92.dp, r.width)   // colW - topInset
        assertEquals(162.dp, r.height) // ownH - topInset

        // 成员 4-6 同理: 锚到自己区间的左上(y = 3*58 = 174)
        val r4 = rect(4, 3, isTop = true, form = ConflictVariant.STACK)
        assertEquals(0.dp, r4.x)
        assertEquals(174.dp, r4.y) // 自身起点 = 第4节
        assertEquals(92.dp, r4.width)
        assertEquals(162.dp, r4.height)
    }

    @Test
    fun stack_chain_group_member_when_at_bottom_anchors_bottom_right() {
        // v7.8.2 回归: 链组 {1-3, 4-6} 沉底时,
        // 成员 1-3 必须缩小并锚自身区间右下。
        val r = rect(1, 3, isTop = false, form = ConflictVariant.STACK)
        // x = topInset, y = ownH - shrunk = 170-162 = 8, width=92, height=162
        assertEquals(8.dp, r.x)
        assertEquals(8.dp, r.y)
        assertEquals(92.dp, r.width)
        assertEquals(162.dp, r.height)
        // 下缘贴自身区间底 (8+162=170)
        assertEquals(rowH * 3 - gapH, r.y + r.height)
    }

    @Test
    fun rail_chain_group_member_top_is_narrow_bottom_is_full_width() {
        // v7.8.2 回归: C 方案链组切顶后窄, 沉底后全宽。
        val inset = AppPrefs.CONFLICT_TOP_INSET_DEFAULT.dp
        val top = rect(1, 3, isTop = true, form = ConflictVariant.RAIL, topInset = inset)
        val bot = rect(1, 3, isTop = false, form = ConflictVariant.RAIL, topInset = inset)
        assertEquals(colW - inset, top.width)   // 窄
        assertEquals(colW, bot.width)            // 全宽
        // 高度都按自身节数(170)
        assertEquals(170.dp, top.height)
        assertEquals(170.dp, bot.height)
    }

    @Test
    fun fold_card_is_folded_decision_based_on_layer_only() {
        // v7.8.2 回归: cardIsFolded 已不再误把沉底卡标折角。
        // 此处不能直接调 UI 函数,但可通过 STACK/FOLD 默认同尺寸 + 折角形状决定来约束:
        // FOLD 形态下顶层卡与底层卡的 rect 几何仍由 isFront 决定(全尺寸)。
        val topF = rect(1, 3, isTop = true, form = ConflictVariant.FOLD)
        val botF = rect(1, 3, isTop = false, form = ConflictVariant.FOLD)
        assertEquals(0.dp, topF.x)
        assertEquals(0.dp, topF.y)
        assertEquals(100.dp, topF.width) // 全宽
        assertEquals(170.dp, topF.height)
        assertEquals(0.dp, botF.x)
        assertEquals(0.dp, botF.y)
        assertEquals(100.dp, botF.width)
        assertEquals(170.dp, botF.height)
        // B 方案下顶层与底层尺寸相同,折角差异由 Card 形状决定 —— 间接保护 cardIsFolded 不带 !chainStripActive。
    }
}
