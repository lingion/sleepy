package com.lingion.sleepy.util

/**
 * 用户反馈 2026-09-16 (第二轮): 弹窗动作键布局降级策略。
 *
 * 用户优先级: ①文字精炼 ②文字完整展示 ③一排美观。
 * "不是一定要一排, 最好是一排" — 行宽够就一行, 不够整组降级竖排,
 * 任何情况下不截断按钮文字。
 *
 * 宽度估算对 CJK 是精确的(等宽字); 对拉丁字母偏保守(估宽), 只会
 * 更早触发竖排, 不会误判"放得下"却截断 — fail-safe 方向正确。
 */
object DialogButtonsLayoutPolicy {

    /** M3 Button 默认水平 contentPadding = 24dp×2 + 少量余量。 */
    const val BUTTON_H_PADDING_DP = 32f

    /** 按钮间距(与 DialogActionButtons Row spacedBy(8.dp) 同值)。 */
    const val ROW_GAP_DP = 8f

    /** 单键文字宽估算 = 字符数 × 单字宽 + 水平内边距。 */
    fun buttonTextWidth(label: String, fontDpPerChar: Float, buttonHPaddingDp: Float = BUTTON_H_PADDING_DP): Float =
        label.length * fontDpPerChar + buttonHPaddingDp

    /**
     * 行宽够不够容纳整组按钮(text 总宽 + 键间 gap)。
     * 不够 → 调用方换竖排, 不截断文字。
     */
    fun fitsInRow(
        labels: List<String>,
        availableWidthDp: Float,
        fontDpPerChar: Float,
        buttonHPaddingDp: Float = BUTTON_H_PADDING_DP,
        rowGapDp: Float = ROW_GAP_DP
    ): Boolean {
        if (labels.isEmpty()) return true
        val totalNeeded = labels.sumOf { buttonTextWidth(it, fontDpPerChar, buttonHPaddingDp).toDouble() } +
            (labels.size - 1) * rowGapDp
        return totalNeeded <= availableWidthDp
    }

    /**
     * 横排每键的必需按钮宽(text 宽 = 字符数×单字宽 + 水平内边距)。
     * 2026-09-18 用户: "一行能搞定的就一行" — 横排时若按 weight(1f) 等分,
     * 长标签键分到的宽度 < 必需宽 → Text 自动换行成两行。所以横排必须按
     * 必需宽比例分配, 让每键至少拿到自己的必需宽。
     */
    fun requiredButtonWidths(
        labels: List<String>,
        fontDpPerChar: Float,
        buttonHPaddingDp: Float = BUTTON_H_PADDING_DP
    ): List<Float> = labels.map { buttonTextWidth(it, fontDpPerChar, buttonHPaddingDp) }

    /**
     * Compose Row.weight 用的归一化权重 = 必需宽 / 总必需宽 (和恒 1)。
     * 等分是错的: 权重比必须等于宽度需求比。
     */
    fun normalizedWeights(
        labels: List<String>,
        fontDpPerChar: Float,
        buttonHPaddingDp: Float = BUTTON_H_PADDING_DP
    ): List<Float> {
        val widths = requiredButtonWidths(labels, fontDpPerChar, buttonHPaddingDp)
        val total = widths.sum()
        if (total <= 0f || widths.isEmpty()) return widths.map { 1f / maxOf(widths.size, 1) }
        return widths.map { it / total }
    }
}

/**
 * 桥接默认字号(labelLarge 14sp → CJK 单字 ≈14dp), 供调用方少传参数。
 * 独立 object 便于纯 JVM 测试注入(不引 Compose 依赖)。
 */
object DialogActionsLayoutPolicyBridge {
    const val LABEL_LARGE_FONT_DP_PER_CJK_CHAR = 14f

    fun fit(labels: List<String>, availableWidthDp: Float): Boolean =
        DialogButtonsLayoutPolicy.fitsInRow(
            labels = labels,
            availableWidthDp = availableWidthDp,
            fontDpPerChar = LABEL_LARGE_FONT_DP_PER_CJK_CHAR
        )
}
