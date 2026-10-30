package com.lingion.sleepy.ui.screen.mine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 表头悬挂偏移滑杆幽灵接线的源码契约 (spec-slider-ghost-appwide-2026-10-29 §3.1
 * 单头变体第七站点; 先例 PeriodHeaderHideTimeContractTest / DisplaySliderGhostContractTest):
 *
 * 1. 进页基线一次性捕获: hangingBaseline = remember { AppPrefs.getPeriodHeaderHanging(context) },
 *    且必须在现态 hangingUnits 之前声明 (基线锚进页时刻, 不追 setter — 松手即落库)。
 * 2. 消费共享件: 判据 singleSliderGhostVisible(hangingUnits, hangingBaseline, 0.05f)
 *    (0.1 步进取半档), 渲染 SingleSliderGhostMark(baselineFrac = (hangingBaseline - (-1f)) / 2f)
 *    — 域 -1..1 的 frac 换算字面量锁死。
 * 3. 落库节奏一克不动: setPeriodHeaderHanging 仍只出现在 onValueChangeFinished,
 *    onValueChange 拖动路径零 AppPrefs.set。
 */
class HeaderHangingGhostContractTest {

    private fun loadSource(vararg relPaths: String): String =
        sequenceOf(
            java.io.File("app/src/main/java/com/lingion/sleepy/"),
            java.io.File("src/main/java/com/lingion/sleepy/"),
        ).firstOrNull { it.isDirectory }?.let { root ->
            relPaths.map { java.io.File(root, it) }.firstOrNull { it.isFile }?.readText()
        } ?: error("Unable to load sources: ${relPaths.joinToString()}")

    private val src by lazy { loadSource("ui/screen/mine/PeriodHeaderSettingsScreen.kt") }

    @Test
    fun page_entry_baseline_captured_before_state() {
        assertTrue(
            "缺进页基线 remember 捕获",
            Regex("""val\s+hangingBaseline\s*=\s*remember\s*\{\s*AppPrefs\.getPeriodHeaderHanging\(context\)""")
                .containsMatchIn(src),
        )
        val baselineIdx = src.indexOf("val hangingBaseline")
        val stateIdx = src.indexOf("var hangingUnits")
        assertTrue("基线声明必须在现态之前 (现态从基线初始化的位置证明)", baselineIdx in 0 until stateIdx)
        assertTrue(
            "现态必须初始化自基线",
            Regex("""var\s+hangingUnits\s+by\s+remember\s*\{\s*mutableStateOf\(hangingBaseline\)""").containsMatchIn(src),
        )
    }

    @Test
    fun consumes_shared_ghost_pieces_with_domain_frac() {
        assertTrue("未消费共享判据", src.contains("singleSliderGhostVisible("))
        assertTrue(
            "判据参必须挂基线与半档容差 0.05f",
            Regex("""singleSliderGhostVisible\(\s*hangingUnits,\s*hangingBaseline,\s*0\.05f\s*\)""").containsMatchIn(src),
        )
        assertTrue("未消费共享渲染件", src.contains("SingleSliderGhostMark("))
        assertTrue(
            "渲染件必须挂与真 Slider 同档 steps = 19 (删则幽灵走连续分支, 内部刻度偏至 ~7px)",
            Regex("""SingleSliderGhostMark\([\s\S]{0,400}?steps\s*=\s*19""").containsMatchIn(src),
        )
        assertTrue(
            "frac 换算必须按域 -1..1: (hangingBaseline - (-1f)) / 2f",
            Regex("""\(\s*hangingBaseline\s*-\s*\(\s*-1f\s*\)\s*\)\s*/\s*2f""").containsMatchIn(src),
        )
    }

    @Test
    fun setter_only_in_on_value_change_finished() {
        assertFalse(
            "onValueChange 拖动路径出现了 AppPrefs.set",
            Regex("""onValueChange\s*=\s*\{[^}]*AppPrefs\.set""").containsMatchIn(src),
        )
        assertTrue(
            "setPeriodHeaderHanging 必须仍挂在 onValueChangeFinished",
            Regex("""onValueChangeFinished\s*=\s*\{\s*AppPrefs\.setPeriodHeaderHanging\(context,\s*hangingUnits\)""")
                .containsMatchIn(src),
        )
    }
}
