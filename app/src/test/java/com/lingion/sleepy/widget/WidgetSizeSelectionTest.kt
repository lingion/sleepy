package com.lingion.sleepy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 测试: [WidgetSizeCore] — OPTION_APPWIDGET_SIZES 的定向选择与回退语义。
 *
 * 背景 (resize 稳定性根因之一): API31+ SIZES 列表在横竖双向 widget 上含两份
 * portrait/landscape 尺寸。旧代码 maxByOrNull{面积} 在"横宽大 vs 竖高更大"时
 * 挑的不是当前方向 — shell bitmap 按错误方向画, fitXY 强拉 → 顶栏下内容变形。
 *
 * 策略 (不依赖方向监听, 保持 launcher 兼容): SIZES 多份时选"宽度最大"那份 —
 * widget 渲染宽度决定换行/截断, 宁可高估内容高度(触发滚动分支)也不横向挤压;
 * 单份时直接用它。回退路径不再混拼 MIN_W×MAX_H 两个语义不同的边界,
 * 而是优先"MIN_W×MIN_H 中较大边配对同源边界", 并把所有候选钳制到正数。
 */
class WidgetSizeSelectionTest {

    // ---- 单份 SIZES: 原样采用 ----

    @Test
    fun `single size entry is used as-is`() {
        assertEquals(80f to 60f, WidgetSizeCore.pickSizeDp(listOf(80f to 60f)))
    }

    // ---- 多份 SIZES: 定向选择 (portrait/landscape 两份) ----

    @Test
    fun `prefers widest entry when sizes list has both orientations`() {
        // 竖 120x300 (面积 36000) vs 横 320x140 (面积 44800) — 面积法会选横,
        // 但本契约选"宽最大" (320x140): 内容按宽排版, 高度不足走滚动分支。
        val picked = WidgetSizeCore.pickSizeDp(listOf(120f to 300f, 320f to 140f))
        assertEquals(320f to 140f, picked)
    }

    @Test
    fun `ties on width prefer smaller height with scroll fallback`() {
        // 同宽 200x100 vs 200x200 = 同方向镜像尺寸 → 高度小者;
        // 内容真有 200dp 高时滚动分支兜底, 比把短内容画成长图再压扁安全。
        assertEquals(200f to 100f, WidgetSizeCore.pickSizeDp(listOf(200f to 100f, 200f to 200f)))
    }

    @Test
    fun `non-positive entries are ignored`() {
        // launcher 偶发塞 0/负值 (半墙 OEM bug 见 memory) — 只挑正数项
        assertEquals(80f to 60f, WidgetSizeCore.pickSizeDp(listOf(0f to 0f, 80f to 60f, -5f to 10f)))
    }

    @Test
    fun `empty or all-invalid sizes yield null`() {
        assertEquals(null, WidgetSizeCore.pickSizeDp(emptyList()))
        assertEquals(null, WidgetSizeCore.pickSizeDp(listOf(0f to 0f)))
    }

    // ---- 回退: API<31 或 launcher 未给 SIZES ----

    @Test
    fun `fallback uses min width with min height not max height`() {
        // 契约: MIN_W × MIN_H — MIN_H 是"当前高度下界", MAX_H 是"能拖到的最大高度"
        // (API29/30 javadoc), 用 MAX_H 会把 1 行 widget 画成 5 行长图再 fitXY 压扁。
        val fb = WidgetSizeCore.fallbackSizeDp(minW = 80, minH = 60, maxW = 320, maxH = 580)
        assertEquals(80 to 60, fb)
    }

    @Test
    fun `fallback clamps non-positive inputs to safe defaults`() {
        assertEquals(250 to 100, WidgetSizeCore.fallbackSizeDp(minW = 0, minH = 0, maxW = 0, maxH = 0))
    }

    @Test
    fun `fallback uses max bounds only when min bounds are absent`() {
        // MIN 缺失但 MAX 在 (部分 OEM 只写一半): 用 MAX 而不是默认值硬猜
        assertEquals(320 to 580, WidgetSizeCore.fallbackSizeDp(minW = 0, minH = 0, maxW = 320, maxH = 580))
    }
}
