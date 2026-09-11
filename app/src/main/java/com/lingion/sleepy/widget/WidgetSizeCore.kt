package com.lingion.sleepy.widget

/**
 * 小组件尺寸选择核心 (Android-free, 纯 JVM) — OPTION_APPWIDGET_SIZES 定向选择 + 回退语义。
 *
 * 背景: API31+ SIZES 在横竖双向 widget 上返回 portrait/landscape 两份。
 * 面积最大 ≠ 当前方向 (横 320x140 面积 > 竖 120x300) — 选错方向则 shell bitmap
 * 按错误宽高画, launcher fitXY 强拉 → 内容变形。
 *
 * 策略 (2026-09-10 方向契约): 用摆放 hint (OPTION_APPWIDGET_MIN_WIDTH/MIN_HEIGHT,
 * 当前 cell 的宽高下界) 解析当前方向, 不再宽度优先 — 旧行为在真方向对上永远取宽者
 * (= 横份), 竖放 (常态) 全部拿到横 w/h。判定序:
 *   1. 宽度接近 (±2dp) 的镜像对 → 与 hint 无关, 取高度小者 (滚动分支兜底);
 *   2. 有 hint → 取与 hint 长宽比同向 (portrait/landscape) 且贴合 hint 的份;
 *   3. 无 hint → 默认竖放, 取 h>=w 的份。
 * hint 贴合度 = 两边差的最小绝对值 (与当前 cell 最近), 而非面积 — 折叠态/多 cell
 * launcher 可能给 3+ 份, 逐份比较不丢弃任何候选。
 */
object WidgetSizeCore {

    private const val MIRROR_TOLERANCE_DP = 2f

    /** 尺寸候选 (宽, 高) dp; [hint] = 当前 cell (宽, 高) dp, 缺省 null。 */
    fun pickSizeDp(
        sizes: List<Pair<Float, Float>>,
        hint: Pair<Float, Float>? = null
    ): Pair<Float, Float>? {
        val valid = sizes.filter { (w, h) -> w > 0f && h > 0f }
        if (valid.isEmpty()) return null
        if (valid.size == 1) return valid[0]
        // 镜像判据用宽度接近的两份 (排序仅用于取"最宽 vs 次宽"作对参照)
        val sortedByWidth = valid.sortedByDescending { it.first }
        val widest = sortedByWidth.first()
        val second = sortedByWidth[1]
        val mirrorPair = valid.size == 2 &&
            kotlin.math.abs(widest.first - second.first) <= MIRROR_TOLERANCE_DP
        if (mirrorPair) {
            // 宽度接近 = 同一方向两份 (OEM 镜像) — 取高度小者, 高度不足滚动分支兜底
            return if (widest.second <= second.second) widest else second
        }
        val hDp = hint?.takeIf { it.first > 0f && it.second > 0f }
        return valid.minWithOrNull(
            if (hDp != null) {
                // 有 hint: 与当前 cell 两边差之和最小者 (贴合度); 同距取面积小者
                // (高度选偏小走滚动分支安全, 选偏大会压扁内容)
                compareBy<Pair<Float, Float>> {
                    kotlin.math.abs(it.first - hDp.first) + kotlin.math.abs(it.second - hDp.second)
                }.thenBy { it.first * it.second }
            } else {
                // 无 hint 默认竖放: h>=w 的份里取宽度最大 (排版空间优先);
                // 全是横态 (h<w) 时取高度最小 (滚动分支兜底)
                compareBy<Pair<Float, Float>> { if (it.second >= it.first) 0 else 1 }
                    .thenByDescending { it.first }
                    .thenBy { it.second }
            }
        )
    }

    /**
     * 回退 (API<31 / launcher 未给 SIZES)。
     *
     * 契约 (API29/30 javadoc): MIN_W/MIN_H = 当前尺寸下界, MAX_W/MAX_H = 能达到的上界。
     * 旧实现混拼 MIN_W × MAX_H = 下界宽配上界高, 1 行 widget 被画成 5 行长图再压扁。
     * 新语义: MIN 在用 MIN (同源 = 当前尺寸近似); MIN 缺失退 MAX; 全缺用安全默认。
     */
    fun fallbackSizeDp(minW: Int, minH: Int, maxW: Int, maxH: Int): Pair<Int, Int> {
        val w = minW.takeIf { it > 0 } ?: maxW.takeIf { it > 0 } ?: 250
        val h = minH.takeIf { it > 0 } ?: maxH.takeIf { it > 0 } ?: 100
        return w to h
    }
}
