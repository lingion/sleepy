package com.lingion.sleepy.ui.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 周视图顶部胶囊等高契约锁。
 *
 * 事故史 (两连败, 本测试锁死两侧):
 * - PR#191: onSizeChanged 上报"应用约束后的终态"高度 → height(maxH) 回灌构成
 *   正反馈棘轮, 守卫条件还挡住回落写入 → 切换视图时高度单调膨胀撑屏崩溃。
 * - PR#192 初修: 改 Row(height(IntrinsicSize.Min)) → DaySummaryCell 内含
 *   BoxWithConstraints (SubcomposeLayout), 不支持 intrinsic 测量询问, 首帧
 *   直接抛 IllegalStateException, 进周视图即崩 (比棘轮更快)。
 *
 * 现行方案: layout modifier 两段测量 —— 第一遍以 maxHeight=Infinity 测纯自然
 * 高度 (只由内容决定, 与拉伸无关, 数学上无正反馈); 第二遍以 max(自然, 全局最高)
 * 强制等高。全程 px 计算, 无 dp 混算。
 *
 * 契约: ① 自然高度必须经 "maxHeight = Constraints.Infinity" 宽松测量取得;
 * ② 禁现 .height(IntrinsicSize.Min) (SubcomposeLayout 崩溃);
 * ③ 禁现 onSizeChanged (受约束终态上报, 棘轮源头)。
 */
class WeekStripEqualHeightContractTest {

    private val weekStripSource: String by lazy {
        val full = File("src/main/java/com/lingion/sleepy/ui/component/CourseTableView.kt").readText()
        val start = full.indexOf("private fun WeekStrip(").also {
            assertTrue("WeekStrip 函数必须存在", it >= 0)
        }
        val nextTopLevel = full.indexOf("\n@Composable", start + 1).let {
            if (it < 0) full.length else it
        }
        full.substring(start, nextTopLevel)
    }

    @Test
    fun natural_height_must_be_measured_under_relaxed_max_constraints() {
        assertTrue(
            "自然高度必须用 maxHeight = Constraints.Infinity 宽松测量 (受约束终态上报=棘轮)",
            weekStripSource.contains("maxHeight = Constraints.Infinity")
        )
        assertTrue(
            "等高拉伸必须经第二遍 minHeight=maxHeight 强制测量",
            weekStripSource.contains("minHeight = boxH, maxHeight = boxH")
        )
    }

    @Test
    fun week_strip_forbids_intrinsic_and_report_ratchet() {
        assertFalse(
            "禁 .height(IntrinsicSize.Min): BoxWithConstraints 是 SubcomposeLayout, intrinsic 询问即崩",
            weekStripSource.contains(".height(IntrinsicSize.Min)")
        )
        assertFalse(
            "禁 onSizeChanged: 其上报值是应用约束后的终态, 会构成棘轮",
            weekStripSource.contains("onSizeChanged")
        )
    }
}
