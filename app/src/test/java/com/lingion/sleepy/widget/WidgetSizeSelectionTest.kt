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
    fun `no hint defaults to portrait entry for genuine orientation pairs`() {
        // 竖 120x300 vs 横 320x140 = 真方向对 (宽度不接近): 旧"宽度优先"契约返回横份,
        // 但竖放 (常态) 的实际 cell 是竖份 — shell 按横份画 → launcher fitXY 强拉变形。
        // 无摆放 hint 时默认竖放, 取竖态 (h>=w) 份。
        val picked = WidgetSizeCore.pickSizeDp(listOf(120f to 300f, 320f to 140f))
        assertEquals(120f to 300f, picked)
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

    // ---- 摆放 hint (OPTION_APPWIDGET_MIN_WIDTH/HEIGHT 口径) 定向 ----

    @Test
    fun `hint portrait picks portrait entry from genuine orientation pair`() {
        // MIN_W×MIN_H = 竖放 cell (80x250): 竖 120x300 才是当前方向, 横 320x140 丢弃
        assertEquals(
            120f to 300f,
            WidgetSizeCore.pickSizeDp(listOf(120f to 300f, 320f to 140f), hint = 80f to 250f)
        )
    }

    @Test
    fun `hint landscape picks landscape entry`() {
        // MIN_W×MIN_H = 横放 cell (300x100): 横份才对
        assertEquals(
            320f to 140f,
            WidgetSizeCore.pickSizeDp(listOf(120f to 300f, 320f to 140f), hint = 300f to 100f)
        )
    }

    @Test
    fun `mirror pair ignores hint and keeps smaller-height tie-break`() {
        // 宽度接近 (±2dp) = 同方向 OEM 镜像: 与旧契约一致取高度小者, hint 不扰动
        assertEquals(
            200f to 100f,
            WidgetSizeCore.pickSizeDp(listOf(200f to 100f, 200f to 200f), hint = 200f to 100f)
        )
        // ±2dp 容差边界: 201 vs 199 差 2dp 仍算镜像 → 高度小者 (100)
        assertEquals(
            201f to 100f,
            WidgetSizeCore.pickSizeDp(listOf(201f to 100f, 199f to 200f), hint = 199f to 200f)
        )
    }

    // ---- >2 份 SIZES (折叠态/多 cell launcher 会塞 3+ 份) ----

    @Test
    fun `more than two entries picks best for hint not blind top-2`() {
        // 4 份: 竖两档 + 横两档。hint=竖放 cell → 落与 cell 最贴合的竖档 (100x220 距
        // (80,250) 最近), 旧 top-2-of-width-sort 直接取横 320x140 (sort 后前二都是横份)。
        val sizes = listOf(120f to 300f, 100f to 220f, 320f to 140f, 240f to 110f)
        assertEquals(100f to 220f, WidgetSizeCore.pickSizeDp(sizes, hint = 80f to 250f))
        assertEquals(320f to 140f, WidgetSizeCore.pickSizeDp(sizes, hint = 300f to 100f))
    }

    @Test
    fun `more than two entries without hint defaults to portrait-ish entry`() {
        // 无 hint 默认竖放: 取 h>=w 份里与"典型竖放"最贴合者 — 契约: 禁盲取宽度冠军
        val sizes = listOf(120f to 300f, 100f to 220f, 320f to 140f)
        val picked = WidgetSizeCore.pickSizeDp(sizes)
        assertTrue(
            "无 hint 时须取竖态 (h>=w) 份, 实取 $picked",
            picked!!.second >= picked.first
        )
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
