package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
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
 * 2. 拖离 → 细条出现且拖动真的改值 (幽灵层零手势回归: Canvas 不拦触摸);
 * 3. 松手后 AppPrefs 已落新值, 幽灵必须仍在 — 基线锚进页时刻、不追 setter
 *    (Review Focus 5 专断言, 防实现把基线误接最近一次落库);
 * 4. 拖回基线刻度 (落点在 snap 半档窗 [1.0,1.05) 内) → 幽灵消失;
 * 5. 退出重进 (Robolectric 单 Activity 禁二次 setContent → 用 if(shown) 销毁/重建
 *    子树模拟离页/进页) → 基线重算=现值 → 幽灵消失
 *    — "直到退出这个页面才消失"的管线闭环。
 *
 * 手势法 (探针实测 TempSliderDragProbeTest, 已删): Robolectric 下 performTouchInput
 * 的 moveBy 相对量落在 material3 档位截断窗边缘时不可靠 (raw 1.0~1.05 全 snap 回
 * 1.0, 位移不足即"拖动没生效"假象); 故一律 down 后分步 moveTo 到**绝对像素目标**,
 * 目标坐标按实测轨道系数标定 — 拖离 = 基线位 +120px (raw≥1.05, 必跨一档),
 * 拖回 = 基线位 +15px (窗内中部, 对轨道系数 ±60% 漂移都落在 snap 窗)。
 * 存在性只用 testTag (locale 文案不可靠, 先例 TimeSlotEditorMotionPipelineTest);
 * 轨道几何复刻先例 IntRangeSliderGhostPipelineTest。
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

    /** gridScale 值域 → 轨道 frac。 */
    private fun frac(v: Float) = (v - 0.7f) / 0.6f

    /** 拇指中心 x (节点局部 px): 复刻 material3 摆放公式 (同双头管线先例)。
     *  只用于取"基线刻度位置"与抓握起点的近似 — 判据不依赖公式精度,
     *  落点一律走绝对 moveTo 标定 (见类 KDoc 手势法)。 */
    private fun thumbX(nodeWidthPx: Float, v: Float): Float {
        val density = 2f // xhdpi (qualifiers)
        val thumbW = 4f * density          // SliderTokens.HandleWidth = 4dp
        val trackH = 16f * density         // SliderTokens.InactiveTrackHeight = 16dp
        val trackW = nodeWidthPx - thumbW
        val corner = trackH / 2f
        val xInTrack = corner + (trackW - 2f * corner) * frac(v)
        return thumbW / 2f + xInTrack
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
        compose.onAllNodesWithTag("display-slider-ghost").fetchSemanticsNodes().isNotEmpty()

    /** 基线 1.0 刻度的近似 x (页面布局固定, 直接现读节点宽)。 */
    private fun baselineX(): Float =
        thumbX(gridScaleSlider().fetchSemanticsNode().boundsInRoot.width, 1.0f)

    /** 从 fromValue 的近似拇指位 down, 分 6 步绝对 moveTo 到 targetX, 松手。 */
    private fun dragTo(fromValue: Float, targetX: Float) {
        val slider = gridScaleSlider()
        val node = slider.fetchSemanticsNode()
        val y = node.boundsInRoot.height / 2f
        val startX = thumbX(node.boundsInRoot.width, fromValue)
        slider.performTouchInput {
            down(Offset(startX, y))
            repeat(6) { moveTo(Offset(startX + (targetX - startX) * (it + 1) / 6f, y)) }
            up()
        }
        compose.waitForIdle()
    }

    /** 拖离基线一档以上 (探针实测: +120px → 1.05 或 1.1, 两值 |Δ|>0.025 → 幽灵必现)。 */
    private fun dragAway() = dragTo(currentGridScale(), baselineX() + 120f)

    /** 拖回基线刻度: 落点基线位+15px → raw≈1.01~1.03, 半档窗 [1.0,1.05) 中部。 */
    private fun dragBack() = dragTo(currentGridScale(), baselineX() + 15f)

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
