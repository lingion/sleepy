package com.lingion.sleepy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用户反馈 2026-09-16 (第二轮): 弹窗三键排一行后文字被截断
 * ("退出并保留草稿" 7 个 CJK 字, 三等分每键 ~88dp 放不下 → 显示不全)。
 *
 * 用户优先级明确: ①文字精炼 ②文字完整展示 ③然后才是一排美观。
 * "不是一定要一排, 最好是一排" — 一排放不下就换竖排, 文字必须完整。
 *
 * 本文件锁布局降级策略(纯函数): 行宽够 → 一行; 不够 → 竖排。
 */
class DialogButtonsLayoutPolicyTest {

    /** labelLarge 14sp 下一个 CJK 字 ≈ 14dp + 按钮左右内边距 32dp (M3 Button contentPadding)。 */
    private companion object {
        const val FONT_DP_PER_CJK_CHAR = 14f
        const val FONT_DP_PER_CHAR = 14f
        const val BUTTON_H_PADDING_DP = 32f
        const val ROW_GAP_DP = 8f
    }

    private fun btnTextWidth(label: String): Float =
        label.length * FONT_DP_PER_CJK_CHAR + BUTTON_H_PADDING_DP

    /** 退化场景(用户报障原样): 三键 4/7/7 字, 弹窗内容区 280dp。 */
    @Test
    fun threeLongLabels_280dpRow_fallsBackToVertical() {
        val fits = DialogButtonsLayoutPolicy.fitsInRow(
            labels = listOf("继续导入", "退出并保留草稿", "退出并删除草稿"),
            availableWidthDp = 280f,
            fontDpPerChar = FONT_DP_PER_CJK_CHAR,
            buttonHPaddingDp = BUTTON_H_PADDING_DP,
            rowGapDp = ROW_GAP_DP
        )
        assertFalse("88dp×3 + 2×8dp gap = 280dp 恰好挤爆, 7 字键必须竖排", fits)
    }

    /** 两键短标签("删除/取消"类)在同样宽度下一行放得下 → 保持一行。 */
    @Test
    fun twoShortLabels_280dpRow_staysHorizontal() {
        val fits = DialogActionsLayoutPolicyBridge.fit(
            labels = listOf("取消", "删除"),
            availableWidthDp = 280f
        )
        assertTrue("两键短标签必须保住一行布局", fits)
    }

    /** 精炼后的三键(4/4/4 字)重新放进一行 — 文案瘦身直接救回一行布局。 */
    @Test
    fun threeConciseLabels_afterCopyDiet_fitRow() {
        val fits = DialogActionsLayoutPolicyBridge.fit(
            labels = listOf("继续导入", "保留草稿", "删除草稿"),
            availableWidthDp = 280f
        )
        assertTrue("精炼后 3×(4×14+32)+16 = 232dp ≤ 280dp → 一行", fits)
    }

    /** 竖排策略下按钮全宽: 任意长度标签都可完整展示 — 策略输出竖排时调用方不再截断。 */
    @Test
    fun verticalFallback_alwaysShowsFullText_noTruncation() {
        val veryLong = listOf("继续导入当前教务系统", "退出并保留当前草稿", "退出并删除所有草稿数据")
        val fitsInRow = DialogActionsLayoutPolicyBridge.fit(veryLong, availableWidthDp = 280f)
        assertFalse("超长标签必须触发竖排", fitsInRow)
        // 竖排 = 每键独占一行(全宽), 只要窗口 ≥ 最长标签宽即可完整显示
        val widest = veryLong.maxOf { DialogButtonsLayoutPolicy.buttonTextWidth(it, FONT_DP_PER_CJK_CHAR, BUTTON_H_PADDING_DP) }
        assertTrue("竖排全宽按钮 280dp ≥ 最长标签 142dp → 无截断", 280f >= widest)
    }

    /** 单键弹窗永不受影响 — 恒一行。 */
    @Test
    fun singleButton_alwaysFits() {
        val fits = DialogActionsLayoutPolicyBridge.fit(listOf("确定"), availableWidthDp = 100f)
        assertTrue(fits)
    }

    /** 等宽假设: 文字宽度按字符数线性估算 — 精确策略对 CJK 是准确的(等宽), 对拉丁是保守近似。 */
    @Test
    fun buttonTextWidth_linearInCjkChars() {
        val w4 = DialogButtonsLayoutPolicy.buttonTextWidth("保留草稿", FONT_DP_PER_CJK_CHAR, BUTTON_H_PADDING_DP)
        val w7 = DialogButtonsLayoutPolicy.buttonTextWidth("退出并保留草稿", FONT_DP_PER_CJK_CHAR, BUTTON_H_PADDING_DP)
        assertEquals(4 * FONT_DP_PER_CJK_CHAR + BUTTON_H_PADDING_DP, w4, 0.01f)
        assertEquals(7 * FONT_DP_PER_CJK_CHAR + BUTTON_H_PADDING_DP, w7, 0.01f)
    }

    /** 横排不能再等分: 长标签必须得到更多宽度, 否则 Android Text 会自动换行。 */
    @Test
    fun horizontalWeights_areProportionalToRequiredButtonWidths() {
        val widths = DialogButtonsLayoutPolicy.requiredButtonWidths(
            labels = listOf("知道了", "导出排查全量包"),
            fontDpPerChar = FONT_DP_PER_CJK_CHAR,
            buttonHPaddingDp = BUTTON_H_PADDING_DP
        )
        assertTrue("长按钮必须比短按钮获得更大 weight", widths[1] > widths[0])
        assertEquals(3 * FONT_DP_PER_CJK_CHAR + BUTTON_H_PADDING_DP, widths[0], 0.01f)
        assertEquals(7 * FONT_DP_PER_CHAR + BUTTON_H_PADDING_DP, widths[1], 0.01f)
    }

    /** 权重必须按需求宽度归一化, 供 Compose Row.weight 使用。 */
    @Test
    fun horizontalWeights_sumToOne_andPreserveRatio() {
        val weights = DialogButtonsLayoutPolicy.normalizedWeights(
            listOf("知道了", "导出排查全量包"), FONT_DP_PER_CHAR, BUTTON_H_PADDING_DP
        )
        assertEquals(1f, weights.sum(), 0.001f)
        assertEquals((3 * FONT_DP_PER_CHAR + BUTTON_H_PADDING_DP) /
            (7 * FONT_DP_PER_CHAR + BUTTON_H_PADDING_DP), weights[0] / weights[1], 0.001f)
    }
}
