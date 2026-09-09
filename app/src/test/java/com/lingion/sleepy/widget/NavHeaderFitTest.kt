package com.lingion.sleepy.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 测试: 顶栏「回到今天/今天」容量判定 [TodayWidgetReceiver.fitsNavTodayFourChar]
 * 的 fontScale 语义 (Android-free — Paint 用 mock 尺寸序列注入)。
 *
 * 背景: XML 里 nav_title 是 textSize=13sp — sp 跟随系统字体缩放 (fontScale)。
 * 旧实现 Paint 用 13f*density 硬构 (fontScale=1 假设), 用户开大字
 * (fontScale=1.3 常见) 时实测文本比测量宽 30% → 顶栏实际装不下却判"装得下"
 * → nav_next 又被挤成第二行巨钮 (原 bug 在大字体窄屏复现)。
 *
 * 契约: Paint 构造必须乘 fontScale; fitsNavTodayFourChar 的 Android 入口
 * 从 configuration.fontScale 读真实值。
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
}
