package com.lingion.sleepy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 双指捏合锚点 (2026-11-02 用户令):
 * 捏合缩放行高的基准线从"顶端第一节课"改为"两指中间"。
 *
 * 契约 (与 CourseTableView yOfRows 同口径, px 版):
 *  - rowCoordinateFromBodyY 是 yOfRows 的逆: 往返误差 < 0.5px
 *  - 锚点滚动目标 = 起点滚动 + 锚点上方加权行数 × (新行高 − 旧行高)
 *  - 表头/长休带厚不随行高缩放 → 不进补偿量
 *  - 捏合中心落在表头区 → acc=0 → 退化为顶端固定 (旧行为)
 */
class TimetableViewportPolicyPinchAnchorTest {

    private val uniform = List(12) { 1f }

    private fun bodyYOf(
        r: Float,
        rowPx: Float,
        mealGapExtraPx: Float,
        breakRows: List<Int>,
        weights: List<Float>
    ): Float = rowPx * TimetableViewportPolicy.weightedRowsAbove(r, weights) +
        mealGapExtraPx * TimetableViewportPolicy.crossedMealBreaks(r, breakRows)

    @Test
    fun `row coordinate inversion round-trips without breaks`() {
        val rowPx = 52f * 3f // density 3
        for (r in listOf(0.5f, 1f, 3.25f, 6.5f, 11.9f)) {
            val y = bodyYOf(r, rowPx, 0f, emptyList(), uniform)
            val back = TimetableViewportPolicy.rowCoordinateFromBodyY(
                y, rowPx, 0f, emptyList(), uniform
            )
            assertEquals("r=$r", r, back, 0.01f)
        }
    }

    @Test
    fun `row coordinate inversion round-trips with meal breaks`() {
        val rowPx = 40f
        val extra = 24f * 2f
        val breaks = listOf(3, 7)
        for (r in listOf(0.5f, 3.5f, 4f, 7.5f, 9f, 11.99f)) {
            val y = bodyYOf(r, rowPx, extra, breaks, uniform)
            val back = TimetableViewportPolicy.rowCoordinateFromBodyY(
                y, rowPx, extra, breaks, uniform
            )
            assertEquals("r=$r", r, back, 0.01f)
        }
    }

    @Test
    fun `anchor at body midpoint pins content under pinch center`() {
        // 12 行 × 40px, 表头 100px, 视口 600px; 滚到 300px, 捏合中心在视口 200px
        // → 内容坐标 500px = 表头下 400px = 第 10 行边界 → acc = 10
        val anchor = TimetableViewportPolicy.capturePinchAnchor(
            scrollPx = 300f,
            centerYPx = 200f,
            headerPx = 100f,
            rowPx = 40f,
            mealGapExtraPx = 0f,
            breakRows = emptyList(),
            weights = uniform
        )
        assertEquals(300f, anchor.startScrollPx, 0.01f)
        assertEquals(10f, anchor.weightedRowsAbove, 0.01f)
        // 行高 40→60: 锚点上方长高 10×20=200 → 目标滚动 500
        val target = TimetableViewportPolicy.pinchTargetScrollPx(anchor, 40f, 60f)
        assertEquals(500f, target, 0.01f)
    }

    @Test
    fun `anchor above body degenerates to top-fixed`() {
        // 捏合中心在表头区 (scroll 0 + center 50 < header 100) → acc=0 → 滚动不补偿
        val anchor = TimetableViewportPolicy.capturePinchAnchor(
            scrollPx = 0f,
            centerYPx = 50f,
            headerPx = 100f,
            rowPx = 40f,
            mealGapExtraPx = 24f,
            breakRows = listOf(3),
            weights = uniform
        )
        assertEquals(0f, anchor.weightedRowsAbove, 0.001f)
        val target = TimetableViewportPolicy.pinchTargetScrollPx(anchor, 40f, 80f)
        assertEquals(0f, target, 0.001f)
    }

    @Test
    fun `meal gap thickness excluded from compensation`() {
        // 有长休带时 acc 仍只数行数: 锚点在第 4 行边界 (带前), acc=4
        val rowPx = 40f
        val extra = 24f * 2f
        val breaks = listOf(3)
        // bodyY 手工构造: 4 行 + 跨过 1 条带 (r=4 → crossed=1)
        val bodyY = bodyYOf(4f, rowPx, extra, breaks, uniform)
        val anchor = TimetableViewportPolicy.capturePinchAnchor(
            scrollPx = 0f,
            centerYPx = bodyY + 100f, // +表头
            headerPx = 100f,
            rowPx = rowPx,
            mealGapExtraPx = extra,
            breakRows = breaks,
            weights = uniform
        )
        assertEquals(4f, anchor.weightedRowsAbove, 0.01f)
        // 补偿 = 4 × (60−40) = 80; 带厚不变不进补偿
        val target = TimetableViewportPolicy.pinchTargetScrollPx(anchor, rowPx, 60f)
        assertEquals(80f, target, 0.01f)
    }

    @Test
    fun `shrinking below zero clamps to top`() {
        val anchor = TimetableViewportPolicy.capturePinchAnchor(
            scrollPx = 100f,
            centerYPx = 150f,
            headerPx = 100f,
            rowPx = 40f,
            mealGapExtraPx = 0f,
            breakRows = emptyList(),
            weights = uniform
        )
        // 锚点上 3.75 行; 行高 40→10 → 3.75×(−30) = −112.5 → 100−112.5 < 0 → 0
        val target = TimetableViewportPolicy.pinchTargetScrollPx(anchor, 40f, 10f)
        assertTrue("target=$target", target >= 0f)
        assertEquals(0f, target, 0.01f)
    }

    @Test
    fun `weighted rows above matches placeholder expansion`() {
        // 占位行权重 0.25: acc(1.5) = 1 + 0.25×0.5 = 1.125
        val weights = listOf(1f, 0.25f, 1f, 1f)
        assertEquals(1.125f, TimetableViewportPolicy.weightedRowsAbove(1.5f, weights), 0.001f)
        // crossed: 带在行 1 后 → r=1.5 (full=1) 未跨; r=2.0 (full=2) 已跨
        val breaks = listOf(1)
        assertEquals(0, TimetableViewportPolicy.crossedMealBreaks(1.5f, breaks))
        assertEquals(1, TimetableViewportPolicy.crossedMealBreaks(2f, breaks))
    }
}
