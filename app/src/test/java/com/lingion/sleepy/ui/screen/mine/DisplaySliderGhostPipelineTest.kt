package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.util.AppPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DisplaySlider 单头幽灵真实派发管线 (spec-slider-ghost-appwide-2026-10-29 §3.1,
 * Review Focus 3/5 的管线级证据; 载体 ScheduleDisplayContent 首条滑杆 gridScale,
 * 域 0.7..1.3, Robolectric 全新 SharedPreferences 默认 1.0):
 *
 * 1. 进页基线=现值 → 初始无 testTag("display-slider-ghost");
 * 2. 拖离 → 圆形幽灵出现且拖动真的改值 (幽灵层零手势回归: Canvas 不拦触摸);
 * 3. 松手后 AppPrefs 已落新值, 幽灵必须仍在 — 基线锚进页时刻、不追 setter
 *    (Review Focus 5 专断言, 防实现把基线误接最近一次落库);
 * 4. 拖回基线刻度 (落点在 snap 半档窗 [1.0,1.05) 内) → 幽灵消失;
 * 5. 退出重进 (Robolectric 单 Activity 禁二次 setContent → 用 if(shown) 销毁/重建
 *    子树模拟离页/进页) → 基线重算=现值 → 幽灵消失
 *    — "直到退出这个页面才消失"的管线闭环。
 *
 * 手势抓取真实圆形滑片, 位移按实际 rail 的宽度及当前值换算, 不使用扩大的
 * Material 无障碍语义边界作为轨道宽度。目标取截断窗中部, 避免浮点数落在边界。
 * 幽灵是 progressSemantics 合并的装饰子节点, 存在性查询使用 unmerged tree。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rUS-w360dp-h640dp-port-xhdpi")
class DisplaySliderGhostPipelineTest {

    @get:Rule val compose = createComposeRule()

    private fun page() {
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                ScheduleDisplayContent(modifier = Modifier.fillMaxSize().padding(16.dp).testTag("page"))
            }
        }
        compose.waitForIdle()
    }

    /** 文档序第 0 条滑杆 = gridScale。默认 conflictStyle="rail" → 页面恰有四条
     *  (gridScale/weekScale/cornerRatio + 导轨内缩), 计数断言防布局漂移后静默拿错目标。 */
    @OptIn(ExperimentalTestApi::class)
    private fun gridScaleSlider() = compose.onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
    ).apply { assertEquals("载体页面必须恰有四条滑杆 (三缩放 + rail inset)", 4, fetchSemanticsNodes().size) }[0]

    @OptIn(ExperimentalTestApi::class)
    private fun sliderCount(): Int = compose.onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
    ).fetchSemanticsNodes().size

    /** gridScale 现值 (语义读数, 免靠 locale 文案)。 */
    private fun currentGridScale(): Float =
        gridScaleSlider().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current

    private fun ghostExists(): Boolean =
        compose.onAllNodesWithTag("display-slider-ghost", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** 第 0 条仍是 gridScale, 抓取真实滑片并按 rail 的实际宽度分步横移。 */
    private fun dragTo(targetValue: Float) {
        val railWidth = compose.onAllNodesWithTag("slider-track", useUnmergedTree = true)[0]
            .fetchSemanticsNode().boundsInRoot.width
        val delta = (targetValue - currentGridScale()) / .6f * railWidth
        compose.onAllNodesWithTag("glass-slider-end", useUnmergedTree = true)[0].performTouchInput {
            down(center)
            repeat(6) { moveBy(Offset(delta / 6f, 0f)) }
            up()
        }
        compose.waitForIdle()
    }

    /** raw 1.12 落在 [1.1, 1.15) 内, 必须跨两档并离开基线。 */
    private fun dragAway() = dragTo(1.12f)

    /** raw 1.02 落在 [1.0, 1.05) 内, 截断后必须返回基线 1.0。 */
    private fun dragBack() = dragTo(1.02f)

    private fun appPrefsGridScale(): Float = AppPrefs.getGridScale(
        androidx.test.core.app.ApplicationProvider.getApplicationContext(),
    )

    @Test fun page_entry_shows_no_ghost() {
        page()
        assertFalse("进页基线=现值时不得渲染幽灵", ghostExists())
    }

    @Test fun dragging_away_shows_ghost_and_value_changes() {
        page()
        dragAway()
        assertTrue("拖动必须真的改值 (幽灵层不得拦手势)", currentGridScale() != 1.0f)
        assertTrue("拖离基线后幽灵必须出现", ghostExists())
    }

    @Test fun ghost_survives_release_persist_baseline_is_page_entry() {
        // Review Focus 5: 松手落 AppPrefs 后, 幽灵对照的是进页基线不是最近落库 → 必须仍在
        page()
        dragAway()
        val persisted = appPrefsGridScale()
        val current = currentGridScale()
        assertEquals("前置: 松手必须已落库 (persisted=$persisted)", current, persisted, 0f)
        assertTrue("落库后幽灵必须仍对照进页基线 1.0 显示 (基线绝不接 setter)", ghostExists())
    }

    @Test fun dragging_back_to_baseline_hides_ghost() {
        page()
        dragAway()
        assertTrue(ghostExists())
        dragBack()
        assertEquals("前置: 拖回落点必须 snap 回基线刻度 1.0", 1.0f, currentGridScale(), 0f)
        assertFalse("拖回基线刻度点后幽灵必须消失", ghostExists())
    }

    @Test fun re_entry_recomputes_baseline_ghost_gone() {
        // 用户原话语义终章: "退出这个页面或保存, 幽灵才会消失" — 重进基线重算。
        // Robolectric 单 Activity 只能 setContent 一次, 退出/重进用 if(shown) 表达:
        // 关 = 组合子树销毁 (等同离页), 开 = 子树重建、无 key remember 重跑 (等同进页)。
        val shown = androidx.compose.runtime.mutableStateOf(true)
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                if (shown.value) {
                    ScheduleDisplayContent(modifier = Modifier.fillMaxSize().padding(16.dp).testTag("page"))
                } else {
                    androidx.compose.material3.Text("已离页", modifier = Modifier.testTag("off-page"))
                }
            }
        }
        compose.waitForIdle()
        dragAway()
        assertTrue(ghostExists())
        val persisted = appPrefsGridScale()
        assertTrue("前置: 重进前 prefs 已离开基线 (persisted=$persisted)", persisted != 1.0f)
        shown.value = false
        compose.waitForIdle()
        assertTrue("退出态必须成立 (防空转假绿)", compose.onAllNodesWithTag("off-page").fetchSemanticsNodes().isNotEmpty())
        shown.value = true
        compose.waitForIdle()
        assertEquals("重进后载体页面必须重建完好 (四条滑杆)", 4, sliderCount())
        assertEquals("重进基线必须=已落库现值", persisted, currentGridScale(), 0f)
        assertFalse("重进组合基线重算后幽灵必须消失", ghostExists())
    }
}
