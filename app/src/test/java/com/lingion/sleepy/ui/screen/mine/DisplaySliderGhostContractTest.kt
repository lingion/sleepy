package com.lingion.sleepy.ui.screen.mine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DisplaySlider 六站点幽灵接线的源码契约 (spec-slider-ghost-appwide-2026-10-29 §2/§3.1,
 * 先例 PeriodHeaderHideTimeContractTest — 声明式接线读源头文件等价于读编译产物):
 *
 * 锁四件事:
 * 1. 六个进页基线各一条 remember 一次性捕获 (gridScale/weekScale/cornerRatio/
 *    conflictStackInset/conflictRailInset/conflictFoldSize), 现值态初始化自基线 —
 *    基线锚"进页时刻"而非"最近一次落库 (Review Focus 5, 防实现者把基线误接 setter)。
 * 2. DisplaySlider 体内消费单点判据 singleSliderGhostVisible 与单点渲染
 *    SingleSliderGhostMark, 幽灵 Canvas 挂 testTag("display-slider-ghost") 供管线定位。
 * 3. 落库节奏一克不动: 六个 AppPrefs setter 只允许出现在 onFinished 回调里,
 *    onValueChange 拖动路径内出现任何 AppPrefs.set 即违宪 (Review Focus 3 —
 *    幽灵是纯渲染零副作用)。
 * 4. epsilon 与最小步进取半档对齐锁字面量: 缩放/圆角三条 = 0.025f (1/20 量化半档),
 *    dp 三条 = 0.5f (roundToInt 整数档半档)。
 */
class DisplaySliderGhostContractTest {

    private fun loadSource(vararg relPaths: String): String =
        sequenceOf(
            java.io.File("app/src/main/java/com/lingion/sleepy/"),
            java.io.File("src/main/java/com/lingion/sleepy/"),
        ).firstOrNull { it.isDirectory }?.let { root ->
            relPaths.map { java.io.File(root, it) }.firstOrNull { it.isFile }?.readText()
        } ?: error("Unable to load sources: ${relPaths.joinToString()}")

    private val src by lazy { loadSource("ui/screen/mine/ScheduleDisplayScreen.kt") }

    @Test
    fun six_page_entry_baselines_are_captured_once() {
        listOf(
            "gridScale" to "getGridScale",
            "weekScale" to "getWeekScale",
            "cornerRatio" to "getGridCornerRatio",
            "conflictStackInset" to "getConflictStackInset",
            "conflictRailInset" to "getConflictRailInset",
            "conflictFoldSize" to "getConflictFoldSize",
        ).forEach { (state, getter) ->
            assertTrue(
                "缺 $state 的进页基线 remember 捕获",
                Regex("""val\s+${state}Baseline\s*=\s*remember\s*\{\s*AppPrefs\.$getter\(context\)""")
                    .containsMatchIn(src),
            )
            assertTrue(
                "$state 现值态必须初始化自基线 (两行式), 不得另行读 AppPrefs",
                Regex("""var\s+$state\s+by\s+remember\s*\{\s*mutableStateOf\(${state}Baseline\)""")
                    .containsMatchIn(src),
            )
        }
    }

    @Test
    fun display_slider_consumes_shared_ghost_pieces() {
        assertTrue(
            "DisplaySlider 签名缺 initial 参",
            Regex("""initial:\s*Float""").containsMatchIn(src),
        )
        assertTrue(
            "DisplaySlider 签名缺 epsilon 参",
            Regex("""epsilon:\s*Float""").containsMatchIn(src),
        )
        assertTrue(
            "未消费共享判据 singleSliderGhostVisible (禁手抄判据)",
            src.contains("singleSliderGhostVisible("),
        )
        assertTrue(
            "未消费共享渲染件 SingleSliderGhostMark (禁手抄几何)",
            src.contains("SingleSliderGhostMark("),
        )
        assertTrue(
            "幽灵未挂管线定位锚 testTag",
            src.contains("""Modifier.testTag("display-slider-ghost")"""),
        )
    }

    @Test
    fun setters_appear_only_in_on_finished_not_in_drag_path() {
        // 拖动路径零副作用: onValueChange 的 lambda 体内不得出现任何 AppPrefs.set
        assertFalse(
            "onValueChange 拖动路径里出现了 AppPrefs.set (落库必须只在松手)",
            Regex("""onValueChange\s*=\s*\{[^}]*AppPrefs\.set""").containsMatchIn(src),
        )
        // 六个滑杆 setter 各自必须挂在 onFinished 回调内 (落库节奏原样保留)
        listOf(
            "setGridScale",
            "setWeekScale",
            "setGridCornerRatio",
            "setConflictStackInset",
            "setConflictRailInset",
            "setConflictFoldSize",
        ).forEach { setter ->
            assertTrue(
                "$setter 未出现在 onFinished 回调里 (落库时机漂移或接线丢失)",
                Regex("""onFinished\s*=\s*\{\s*AppPrefs\.$setter\(context""").containsMatchIn(src),
            )
        }
    }

    @Test
    fun epsilon_locked_to_half_step_per_site() {
        assertEquals("缩放/圆角三条应为 1/20 量化半档 0.025f", 3, Regex("""epsilon = 0\.025f""").findAll(src).count())
        assertEquals("dp 三条应为整数档半档 0.5f", 3, Regex("""epsilon = 0\.5f""").findAll(src).count())
    }
}
