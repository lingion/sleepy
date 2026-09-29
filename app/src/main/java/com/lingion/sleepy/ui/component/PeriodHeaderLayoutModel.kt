package com.lingion.sleepy.ui.component

import kotlin.math.max
import kotlin.math.min

/**
 * 三行表头几何模型(纯数据, 无 Compose 依赖, 可单测)。
 *
 * 用户定稿 2026-09-27: 三行文字是唯一呈现元素, 元素矩形 =
 *   top = 第一行顶, bottom = 第三行底,
 *   left = min(时间行左缘, 标签左缘), right = max(时间行右缘, 标签右缘)。
 * 卡片自适应这个矩形 — 有字的地方就是卡片, 禁止用轨道宽度撑出空白。
 *
 * u ∈ [-1, +1]: 滑杆值。端点异锚:
 *   u=-1 标签右缘贴时间块左缘; u=+1 标签左缘贴时间块右缘; u=0 标签中心=时间块中心。
 * 3W 只是滑杆行程 (标签可移动总量), 与卡片尺寸无关。
 */
data class PeriodHeaderMetrics(
    val startWidth: Float,
    val endWidth: Float,
    val labelWidth: Float,
    val showX: Boolean,
) {
    val timeMax: Float get() = max(startWidth, endWidth)
    val W: Float get() = startWidth
    val trackWidth: Float get() = 3f * startWidth

    /** 标签左缘原始坐标 (时间块左缘=0)。u=-1 → -labelWidth, u=+1 → timeMax, 线性。 */
    private fun rawLabelLeft(u: Float): Float =
        -labelWidth + (u + 1f) / 2f * (timeMax + labelWidth)

    /** 当前 u 的元素矩形 (实际墨迹) 宽度 = 卡片内容区应取的宽度。 */
    fun inkWidth(u: Float): Float {
        val labelLeft = rawLabelLeft(u)
        val left = min(0f, labelLeft)
        val right = max(timeMax, labelLeft + labelWidth)
        return right - left
    }
}

data class PeriodHeaderPlacement(
    /** L/R 时间块左缘最终 x (px, 相对元素矩形左缘)。 */
    val timeBaseLeft: Float,
    /** M 标签左缘最终 x (px)。 */
    val labelLeft: Float,
    /** 元素矩形总宽 (px) = 当前 u 的 [PeriodHeaderMetrics.inkWidth]。 */
    val contentWidth: Float,
)

/** 元素矩形左缘归零的排布: 谁在最左谁贴 0, 卡片宽 = 矩形宽。 */
internal fun PeriodHeaderMetrics.solvePlacement(u: Float): PeriodHeaderPlacement {
    val rawLabelLeft = -labelWidth + (u + 1f) / 2f * (timeMax + labelWidth)
    val inkLeft = min(0f, rawLabelLeft)
    val inkRight = max(timeMax, rawLabelLeft + labelWidth)
    return PeriodHeaderPlacement(
        timeBaseLeft = -inkLeft,
        labelLeft = rawLabelLeft - inkLeft,
        contentWidth = inkRight - inkLeft,
    )
}

/**
 * 三行表头自适应字号(纯函数, Compose / Widget 共享单一事实来源)。
 *
 * 用户 2026-09-29 v3 连续自适应:
 *   - 字号随卡宽/卡高连续变化, 上下限钳制可读区间 [label 11~16, time 10~14]
 *   - 圈圈/罗马/大写/阿拉伯/汉字 五样式统一字号 (汉字作参考, 字宽用 ink 实测)
 *   - 内容更长 → 卡片变宽 (上游 widthBudget 负责), 禁缩字号迁就
 *   - 卡宽不够装墨迹 (最坏兜底) → 等比回缩
 *
 * 算法:
 *   - cardH / 2.6 = 标签字号上限 (三行文字 + 内边距 + 上下呼吸)
 *   - cardW / labelWidth  ≈ 实测当前字号; (cardW/labelWidth) × scale_per_char
 *     其中 label 字宽 / 字号 ≈ 1.0 (汉字参考, 其它样式 ≤ 1.0)
 *   - 取 min(高上限, 宽上限), 钳到 [MIN, MAX]
 *   - time = label - 1 (时间永远比标签小 1sp, 等距梯度)
 *
 * 输入/输出单位: **sp** — Compose 消费端直接 `.sp`, Widget 消费端用
 * `TypedValue.applyDimension(COMPLEX_UNIT_SP, ..., displayMetrics)` 自带 density 转换。
 * 三链在数学上等价, 与 density 无关。
 */
data class PeriodHeaderAdaptiveFont(val timeSize: Float, val labelSize: Float) {
    /** 总行高 = 行间距行高 + 内边距, 保留三行距总和 (sp). */
    val lineHeightSp: Float get() = max(timeSize * 1.25f, timeSize + 1f)

    /** 单行的字号约束输入 — 整列取最紧约束统一字号 (用户 2026-09-29 令). */
    data class RowConstraint(
        val inkWidthSp: Float,
        val timeMaxWidthSp: Float,
        val labelWidthSp: Float,
    )

    companion object {
        /** 可读区间上限 — Material titleSmall/labelLarge 区域, 老花眼舒适. */
        const val MAX_LABEL_SP = 16f
        const val MAX_TIME_SP = 14f
        /** 可读区间下限 — Material bodySmall 下限, 触底不再缩. */
        const val MIN_LABEL_SP = 11f
        const val MIN_TIME_SP = 10f
        /** 三行总高 = label + lineGap + time + lineGap + time, 时间行略小. */
        private const val HEIGHT_TO_LABEL_RATIO = 2.6f
        /** 基准字号 — 墨迹宽度测量用, 与自适应字号无关. */
        const val BASE_LABEL_SP = 12f
        const val BASE_TIME_SP = 11f

        /**
         * @param cardWidthSp   卡片内容宽 (sp, 已扣内边距)
         * @param cardHeightSp  卡片内容高 (sp)
         * @param inkWidthSp    当前 u 下的墨迹宽 (sp). 0 = 未测, 跳过最坏兜底.
         * @param timeMaxWidthSp 起始/结束时间二者中较宽者 (sp). 0 = 未测, 预览链用.
         * @param labelWidthSp  标签宽度 (sp). 0 = 未测.
         */
        fun compute(
            cardWidthSp: Float,
            cardHeightSp: Float,
            inkWidthSp: Float,
            timeMaxWidthSp: Float,
            labelWidthSp: Float,
        ): PeriodHeaderAdaptiveFont {
            // 高度上限唯一驱动: 三行总高 ≈ label×2.6 (呼吸空间含内边距)。
            // 宽度不做上限 — 宽度由上游按自适应字号实测后撑卡 (内容长→卡变宽, 禁缩字号迁就);
            // inkWidth 超卡宽仅在真装不下时兜底回缩 (见下)。
            val heightCap = cardHeightSp.coerceAtLeast(1f) / HEIGHT_TO_LABEL_RATIO
            // 卡高装不下 MIN 时 (小组件极矮行), 按高度比例缩不硬钳 — 硬钳必竖向溢出
            val labelSize = if (heightCap >= MIN_LABEL_SP)
                heightCap.coerceAtMost(MAX_LABEL_SP)
            else heightCap.coerceAtLeast(0.1f)
            val rawTime = labelSize - 1f
            val timeSize = if (rawTime >= MIN_TIME_SP)
                rawTime.coerceAtMost(MAX_TIME_SP)
            else rawTime.coerceAtLeast(0.1f)
            // 墨迹闸: 传入的 inkWidthSp 是基准字号 (12sp) 下的测量值,
            // 实际渲染用自适应字号 — 按字号比投影到真实墨迹宽度再判溢出.
            // 例: 基准 12sp 测 ink=80sp, 自适应 16sp → 真实 ink = 80×(16/12) = 106.7sp
            val projectedInk = if (inkWidthSp > 0f && labelSize > 0f)
                inkWidthSp * (labelSize / BASE_LABEL_SP)
            else inkWidthSp
            val scale = if (projectedInk > cardWidthSp && cardWidthSp > 0f && projectedInk > 0f)
                cardWidthSp / projectedInk else 1f
            return PeriodHeaderAdaptiveFont(
                timeSize = (timeSize * scale).coerceAtLeast(0.1f),
                labelSize = (labelSize * scale).coerceAtLeast(0.1f),
            )
        }

        /** One font for a whole column; the WIDEST (hardest-to-fit) row decides the size. */
        fun forColumn(
            cardWidthSp: Float,
            cardHeightSp: Float,
            rows: List<RowConstraint>,
        ): PeriodHeaderAdaptiveFont {
            if (rows.isEmpty()) return compute(cardWidthSp, cardHeightSp, 0f, 0f, 0f)
            return compute(
                cardWidthSp = cardWidthSp,
                cardHeightSp = cardHeightSp,
                inkWidthSp = rows.maxOf { it.inkWidthSp },
                timeMaxWidthSp = rows.maxOf { it.timeMaxWidthSp },
                labelWidthSp = rows.maxOf { it.labelWidthSp },
            )
        }

        /** Preview shares the same algorithm; only the ink guard is skipped (it is sized generously). */
        fun forPreview(
            cardWidthSp: Float,
            cardHeightSp: Float,
        ): PeriodHeaderAdaptiveFont = compute(
            cardWidthSp = cardWidthSp,
            cardHeightSp = cardHeightSp,
            inkWidthSp = 0f,
            timeMaxWidthSp = 0f,
            labelWidthSp = 0f,
        )
    }
}

/** Width shared by legacy header rows, based on the widest measured row. */
internal fun legacyColumnWidthDp(rowWidths: List<Float>): Float = rowWidths.maxOrNull() ?: 0f

/**
 * 由卡片宽度 + 行高推导卡片高度(Compose / Widget 共享, 保证垂直节奏一致)。
 * 三行垂直均分, timeSize + labelSize + timeSize = 三行行高, 上下各加 1dp 内边距。
 */
internal fun adaptiveCardHeightPx(rowHeightPx: Float): Float =
    (rowHeightPx * 3f).coerceAtLeast(18f)