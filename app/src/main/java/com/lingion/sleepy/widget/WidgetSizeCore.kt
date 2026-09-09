package com.lingion.sleepy.widget

/**
 * 小组件尺寸选择核心 (Android-free, 纯 JVM) — OPTION_APPWIDGET_SIZES 定向选择 + 回退语义。
 *
 * 背景: API31+ SIZES 在横竖双向 widget 上返回 portrait/landscape 两份。
 * 面积最大 ≠ 当前方向 (横 320x140 面积 > 竖 120x300) — 选错方向则 shell bitmap
 * 按错误宽高画, launcher fitXY 强拉 → 内容变形。
 *
 * 策略: 宽度优先 — 渲染宽度决定顶栏排版/换行, 宽度对了一半以上;
 * 高度选偏小无妨 (高度不足自动进滚动分支, 视觉仍正确), 选偏大才会把滚动内容压扁。
 * 因此同宽时取面积大者 (更高), 不同宽时取宽度大者 — 但两份尺寸宽度差只有
 * launcher 横竖排版的镜像关系时, 宽度大者对应横放; 用户竖放 (常态) 时另一份才对。
 * 结论: 在"宽度接近"的两份里取高度小者 (高度小 = 竖态短边正确, 滚动分支兜底)。
 */
object WidgetSizeCore {

    /** 尺寸候选 (宽, 高) dp。 */
    fun pickSizeDp(sizes: List<Pair<Float, Float>>): Pair<Float, Float>? {
        val valid = sizes.filter { (w, h) -> w > 0f && h > 0f }
        if (valid.isEmpty()) return null
        if (valid.size == 1) return valid[0]
        // 排序: 宽度降序; 同宽 (±2dp 容差, 视为同一方向的镜像尺寸) 时高度升序
        val sorted = valid.sortedWith(
            compareByDescending<Pair<Float, Float>> { it.first }
                .thenBy { it.second }
        )
        val (first, second) = sorted
        return if (kotlin.math.abs(first.first - second.first) <= 2f) {
            // 宽度接近 = 同一方向两份 (OEM 镜像) — 取高度小者, 高度不足滚动分支兜底
            if (first.second <= second.second) first else second
        } else {
            first
        }
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
