package com.lingion.sleepy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 测试: 顶栏三级降级判定 [TodayWidgetReceiver.navHeaderTier]
 * 与「回到今天/今天」容量判定 [TodayWidgetReceiver.fitsNavTodayFourChar]
 * 的 fontScale 语义 (Android-free — Paint 用 mock 尺寸序列注入)。
 *
 * 背景: XML 里 nav_title 是 textSize=13sp — sp 跟随系统字体缩放 (fontScale)。
 * 旧实现 Paint 用 13f*density 硬构 (fontScale=1 假设), 用户开大字
 * (fontScale=1.3 常见) 时实测文本比测量宽 30% → 顶栏实际装不下却判"装得下"
 * → nav_next 又被挤成第二行巨钮 (原 bug 在大字体窄屏复现)。
 *
 * 真机根因 (OPPO PKX110, 2026-09-09 uiautomator 坐标取证):
 * SIZES=148dp 实际渲染 129.7dp 的窄档上,「9/8 · 周二」+两钮+「今天」固定宽
 * ≈187dp > 148dp → LinearLayout 横向溢出, nav_next 被推出可视区 (层级中消失)。
 * 旧判定只决定四字/两字, 两字也装不下时无降级 → 必须补标题去星期 (TIER_SHORT_TITLE)
 * 与隐藏 nav_today (TIER_HIDE_TODAY) 两档。契约: 全程真实度量, 无固定 dp 阈值。
 */
class NavHeaderFitTest {

    /** 记录构造时收到的 textSize (px), 其余行为无关紧要的 Paint 替身。 */
    private class RecordingPaint(var pxAtConstruction: Float = -1f) {
        fun measureText(s: String): Float = s.length * pxAtConstruction
    }

    private fun makePaints(density: Float, fontScale: Float): Pair<RecordingPaint, RecordingPaint> {
        // 与实现的构造公式镜像: textSize = (sp 值) * density * fontScale
        return RecordingPaint(13f * density * fontScale) to RecordingPaint(11f * density * fontScale)
    }

    // ── 旧契约保持: 四字/两字判定 ──

    @Test
    fun `wide widget fits four-char even with big font scale`() {
        val (tp, np) = makePaints(density = 2f, fontScale = 1.3f)
        // 400dp 宽 + 大字 (required≈325dp): 装得下
        assertTrue(
            TodayWidgetReceiver.fitsNavTodayFourChar(
                density = 2f, wDp = 400, titleText = "9/12 · 周六",
                titleMeasure = { tp.measureText(it) }, navTodayMeasure = { np.measureText(it) }
            )
        )
    }

    @Test
    fun `narrow widget with big font scale must fall back to two-char`() {
        val (tp, np) = makePaints(density = 2f, fontScale = 1.3f)
        // 80dp (1-2 列) + 大字: 四字必装不下 → 必须 false (旧公式按 fontScale=1 算会漏判)
        assertFalse(
            TodayWidgetReceiver.fitsNavTodayFourChar(
                density = 2f, wDp = 80, titleText = "9/12 · 周六",
                titleMeasure = { tp.measureText(it) }, navTodayMeasure = { np.measureText(it) }
            )
        )
    }

    @Test
    fun `unknown width defaults to four-char safe path`() {
        val (tp, np) = makePaints(density = 2f, fontScale = 1f)
        assertTrue(
            TodayWidgetReceiver.fitsNavTodayFourChar(
                density = 2f, wDp = 0, titleText = "9/12 · 周六",
                titleMeasure = { tp.measureText(it) }, navTodayMeasure = { np.measureText(it) }
            )
        )
    }

    @Test
    fun `boundary exactly required width fits`() {
        // stub: 每字符宽 = sp 值 px (density=1, fontScale=1)
        // titleW = 9字×13 = 117dp; navToday 四字 = 4×11 = 44dp
        // required = 10+117+4+40+(6+44+6)+40+10 = 277 → wDp=277 恰好装下, 276 装不下
        val (tp, np) = makePaints(density = 1f, fontScale = 1f)
        assertTrue(
            TodayWidgetReceiver.fitsNavTodayFourChar(
                density = 1f, wDp = 277, titleText = "9/12 · 周六",
                titleMeasure = { tp.measureText(it) }, navTodayMeasure = { np.measureText(it) }
            )
        )
        assertFalse(
            TodayWidgetReceiver.fitsNavTodayFourChar(
                density = 1f, wDp = 276, titleText = "9/12 · 周六",
                titleMeasure = { tp.measureText(it) }, navTodayMeasure = { np.measureText(it) }
            )
        )
    }

    // ── 三级降级 (真机窄档根因修复) ──

    /** 降级档位: 数值序 = 恓牲度递增。 */
    private fun tier(
        density: Float, wDp: Int, fullTitle: String, dateOnly: String,
        titleMeasure: (String) -> Float, navTodayMeasure: (String) -> Float
    ): NavTier =
        TodayWidgetReceiver.navHeaderTier(
            density, wDp, fullTitle, dateOnly,
            titleMeasure = titleMeasure, navTodayMeasure = navTodayMeasure
        )

    @Test
    fun `tier wide widget keeps full title and four-char`() {
        val (tp, np) = makePaints(density = 2f, fontScale = 1f)
        assertEquals(
            NavTier.FULL,
            tier(2f, 400, "9/12 · 周六", "9/12",
                { tp.measureText(it) }, { np.measureText(it) })
        )
    }

    @Test
    fun `tier unknown width keeps full title`() {
        val (tp, np) = makePaints(density = 2f, fontScale = 1f)
        assertEquals(
            NavTier.FULL,
            tier(2f, 0, "9/12 · 周六", "9/12",
                { tp.measureText(it) }, { np.measureText(it) })
        )
    }

    @Test
    fun `tier four-char does not fit but two-char does → two-char keeps full title`() {
        val (tp, np) = makePaints(density = 1f, fontScale = 1f)
        // required(四字) = 10+117+4+40+(6+44+6)+40+10 = 277; required(两字) = 255
        // wDp=255: 四字装不下, 两字 + 完整标题恰好装得下 → TWO_CHAR
        assertEquals(
            NavTier.TWO_CHAR,
            tier(1f, 255, "9/12 · 周六", "9/12",
                { tp.measureText(it) }, { np.measureText(it) })
        )
    }

    @Test
    fun `tier real-device narrow width drops weekday first`() {
        val (tp, np) = makePaints(density = 1f, fontScale = 1f)
        // 真机 148dp 档 (OPPO SIZES 口径): 「9/8 · 周二」(8字×13=104) + 今天(22)
        //   full: 10+104+4+40+(6+22+6)+40+10 = 242 > 148 → 连两字+满标题都溢出
        //   date-only 「9/8」(3字×13=39): 10+39+4+40+(6+22+6)+40+10 = 177 > 148 仍溢出?
        //   → 本例连 L3 都不够 → HIDE_TODAY (148dp 实机档: nav_today 必须隐藏)
        assertEquals(
            NavTier.HIDE_TODAY,
            tier(1f, 148, "9/8 · 周二", "9/8",
                { tp.measureText(it) }, { np.measureText(it) })
        )
    }

    @Test
    fun `tier drops weekday when short title plus two-char fits`() {
        val (tp, np) = makePaints(density = 1f, fontScale = 1f)
        // 满标题 8字×13=104 → 242 > 200; date-only 3字×13=39 → 10+39+4+40+34+40+10=177 ≤ 200
        assertEquals(
            NavTier.SHORT_TITLE,
            tier(1f, 200, "9/12 · 周六", "9/12",
                { tp.measureText(it) }, { np.measureText(it) })
        )
    }

    @Test
    fun `tier hide today keeps nav buttons and date-only title`() {
        val (tp, np) = makePaints(density = 1f, fontScale = 1f)
        // 极窄: date-only 也装不下 → 隐藏 nav_today (prev/next 保留)
        assertEquals(
            NavTier.HIDE_TODAY,
            tier(1f, 120, "9/12 · 周六", "9/12",
                { tp.measureText(it) }, { np.measureText(it) })
        )
    }

    @Test
    fun `tier boundary between two-char and short title is exact`() {
        val (tp, np) = makePaints(density = 1f, fontScale = 1f)
        // 「9/8 · 周二」=8字×13=104; required(两字) = 10+104+4+40+34+40+10 = 242
        assertEquals(
            NavTier.SHORT_TITLE,
            tier(1f, 241, "9/8 · 周二", "9/8",
                { tp.measureText(it) }, { np.measureText(it) })
        )
        assertEquals(
            NavTier.TWO_CHAR,
            tier(1f, 242, "9/8 · 周二", "9/8",
                { tp.measureText(it) }, { np.measureText(it) })
        )
    }
}
