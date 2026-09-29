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
 * 用户 2026-09-27: 字号随容器宽高自动缩放, 不能再钉死 sp/dp。
 * 同步依赖:
 *   - cardWidthPx  → 实际卡片宽度(已扣内边距)
 *   - cardHeightPx → 实际卡片高度
 *   - inkWidthPx   → 当前 u 下的墨迹宽度(最长一行)
 *   - startWidthPx / labelWidthPx / endWidthPx → 文字宽
 *
 * 算法: 行高 = cardHeight / 3 (三行垂直均分), 字号由行高驱动同时不被文字宽撑出;
 *   timeSize = 行高 × ratio_t, labelSize = 行高 × ratio_m, endSize 与 timeSize 同号;
 *   上限/下限钳制, 保证 4dp~10dp 可读区间, 单元件 3dp 时走 legacy 兜底。
 *
 * 返回 (timeSize, labelSize), 单位 px (与 paint / density 单位一致, Compose 由 .toSp 转换)。
 */
data class PeriodHeaderAdaptiveFont(val timeSize: Float, val labelSize: Float) {
    /** 总行高 = 行间距行高 + 内边距, 保留三行距总和。 */
    val lineHeightPx: Float get() = (timeSize * 1.25f).coerceAtLeast(timeSize + 1f)

    companion object {
        const val TIME_ROW_RATIO = 0.28f
        const val LABEL_ROW_RATIO = 0.40f
        const val TIME_MIN_PX = 5f
        const val TIME_MAX_PX = 11f
        const val LABEL_MIN_PX = 6f
        const val LABEL_MAX_PX = 14f

        /**
         * @param cardWidthPx 卡片内容宽(已扣内边距)
         * @param cardHeightPx 卡片内容高
         * @param inkWidthPx 当前 u 下的墨迹宽(标签与时间块合并的包络)
         * @param timeMaxWidthPx 起始/结束时间二者中较宽者(用作时间字号宽度约束)
         * @param labelWidthPx 标签宽度(用作标签字号宽度约束)
         */
        fun compute(
            cardWidthPx: Float,
            cardHeightPx: Float,
            inkWidthPx: Float,
            timeMaxWidthPx: Float,
            labelWidthPx: Float,
        ): PeriodHeaderAdaptiveFont {
            // 行高 = 卡片内容高 / 3(垂直三行均分), 下限 6dp 等价 = 6*density px
            val rowHeight = (cardHeightPx / 3f).coerceAtLeast(1f)
            val baseTime = rowHeight * TIME_ROW_RATIO
            val baseLabel = rowHeight * LABEL_ROW_RATIO
            // 宽度约束: 文字宽 / 行高 ≈ 字符宽高比, 单字中文 ≈ 1.0, 加 0.92 安全余量。
            // 时间: 一行只一个 "HH:mm", 字号上限 = timeMaxWidthPx / 5 (5 chars).
            // 标签: "第 X 节" ≈ 4 字, 字号上限 = labelWidthPx / 4.
            val timeByWidth = if (timeMaxWidthPx > 0f) timeMaxWidthPx / 5f else baseTime
            val labelByWidth = if (labelWidthPx > 0f) labelWidthPx / 4f else baseLabel
            val timeSize = baseTime
                .coerceAtMost(timeByWidth * 1.05f)
                .coerceIn(TIME_MIN_PX, TIME_MAX_PX)
            val labelSize = baseLabel
                .coerceAtMost(labelByWidth * 1.05f)
                .coerceIn(LABEL_MIN_PX, LABEL_MAX_PX)
            // inkWidth 必须 ≤ cardWidth; 不然缩短字号按比例再缩一次(最坏兜底)。
            val scale = if (inkWidthPx > cardWidthPx && cardWidthPx > 0f)
                cardWidthPx / inkWidthPx else 1f
            return PeriodHeaderAdaptiveFont(
                timeSize = (timeSize * scale).coerceIn(TIME_MIN_PX, TIME_MAX_PX),
                labelSize = (labelSize * scale).coerceIn(LABEL_MIN_PX, LABEL_MAX_PX),
            )
        }
    }
}

/**
 * 由卡片宽度 + 行高推导卡片高度(Compose / Widget 共享, 保证垂直节奏一致)。
 * 三行垂直均分, timeSize + labelSize + timeSize = 三行行高, 上下各加 1dp 内边距。
 */
internal fun adaptiveCardHeightPx(rowHeightPx: Float): Float =
    (rowHeightPx * 3f).coerceAtLeast(18f)