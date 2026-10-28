package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 原位置幽灵占位的真实派发管线契约 (需求 2026-10-28):
 * initialStart/initialEnd 与当前显示区间重合 → 不渲染 testTag("slider-ghost");
 * 拖离 → 幽灵出现; 拖回 → 幽灵消失; 初值为 null (新建课程路径) → 永不可见。
 *
 * 载体是受控状态: start/end 由测试持有, IntRangeSlider 只回写 — 与
 * AddCourseScreen 生产形态一致 (draft 可变状态 → 滑杆 → 回写)。
 * 幽灵本体是纯装饰 Canvas (无 pointerInput、无语义文本), 存在性用 testTag 判定;
 * 它绝不能吃掉手势 — 拖动改值本身即回归断言 (改值成功 = 幽灵层未干扰 RangeSlider)。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rUS-w360dp-h640dp-port-xhdpi")
class IntRangeSliderGhostPipelineTest {

    @get:Rule val compose = createComposeRule()

    private class Holder {
        var start = 1
        var end = 8
    }

    private fun page(holder: Holder, initialStart: Int?, initialEnd: Int?) {
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                LazyColumn(modifier = Modifier.fillMaxSize().testTag("page")) {
                    items(List(8) { "filler-$it" }) { Text(it, modifier = Modifier.padding(24.dp)) }
                    item {
                        Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                            var start by remember { mutableIntStateOf(holder.start) }
                            var end by remember { mutableIntStateOf(holder.end) }
                            IntRangeSlider(
                                summary = "第${start}–${end}周",
                                minValue = 1,
                                maxValue = 16,
                                startValue = start,
                                endValue = end,
                                onValueChange = { s, e ->
                                    start = s; end = e
                                    holder.start = s; holder.end = e
                                },
                                initialStart = initialStart,
                                initialEnd = initialEnd,
                                modifier = Modifier.testTag("slider"),
                            )
                        }
                    }
                    items(List(15) { "tail-$it" }) { Text(it, modifier = Modifier.padding(24.dp)) }
                }
            }
        }
    }

    /** 终点滑块在轨道上的 x (节点局部系): 复刻 material3 摆放公式 —
     *  轨道内缩一个半 thumb 宽, 非端点档位再按 corner (轨道高/2) 内缩。 */
    private fun endThumbX(nodeWidthPx: Float, endValue: Int): Float {
        val density = 2f // xhdpi (qualifiers)
        val thumbW = 4f * density          // SliderTokens.HandleWidth = 4dp
        val trackH = 16f * density         // SliderTokens.InactiveTrackHeight = 16dp
        val trackW = nodeWidthPx - thumbW
        val frac = (endValue - 1) / 15f    // 域 1..16
        val corner = trackH / 2f
        val xInTrack = corner + (trackW - 2f * corner) * frac
        return thumbW / 2f + xInTrack
    }

    private fun ghostExists(): Boolean =
        compose.onAllNodesWithTag("slider-ghost").fetchSemanticsNodes().isNotEmpty()

    private fun dragEndThumb(fromValue: Int, deltaX: Float) {
        // 摘要文本随值变化 — 每次都要按当前值的 contentDescription 找节点
        val slider = compose.onNodeWithContentDescription("第1–${fromValue}周")
        val node = slider.fetchSemanticsNode()
        val x = endThumbX(node.boundsInRoot.width, fromValue)
        val y = node.boundsInRoot.height / 2f
        slider.performTouchInput {
            down(Offset(x, y))
            repeat(6) { moveBy(Offset(deltaX / 6f, 0f)) }
            up()
        }
    }

    @Test fun initial_equal_current_no_ghost_at_rest() {
        val holder = Holder()
        page(holder, initialStart = 1, initialEnd = 8)
        compose.waitForIdle()
        assertFalse("初值与当前重合时不得渲染幽灵", ghostExists())
    }

    @Test fun dragging_away_reveals_ghost_and_keeps_slider_working() {
        val holder = Holder()
        page(holder, initialStart = 1, initialEnd = 8)
        compose.waitForIdle()
        assertFalse(ghostExists())
        // 终点 8 → 拖出 (幽灵要同时证明"不改值被幽灵层挡住": 值必须真的变了)
        dragEndThumb(fromValue = 8, deltaX = 60f)
        compose.waitForIdle()
        assertTrue(holder.end != 8)
        assertIsDisplayedViaTag()
    }

    private fun assertIsDisplayedViaTag() {
        compose.onNodeWithTag("slider-ghost").assertIsDisplayed()
    }

    @Test fun dragging_back_to_initial_hides_ghost_again() {
        val holder = Holder()
        page(holder, initialStart = 1, initialEnd = 8)
        dragEndThumb(fromValue = 8, deltaX = 60f)
        compose.waitForIdle()
        assertIsDisplayedViaTag()
        // 原路拖回: 落点 frac 复原 → snap 回 8 → 与初值重合 → 幽灵消失
        dragEndThumb(fromValue = holder.end, deltaX = -60f)
        compose.waitForIdle()
        assertFalse("拖回原区间后幽灵必须消失 (end=${holder.end})", ghostExists())
    }

    @Test fun no_initial_range_never_shows_ghost_after_drag() {
        val holder = Holder()
        page(holder, initialStart = null, initialEnd = null)
        dragEndThumb(fromValue = 8, deltaX = 60f)
        compose.waitForIdle()
        assertTrue("拖动本身仍须生效", holder.end != 8)
        assertFalse("新建课程 (无初值) 永不得出现幽灵", ghostExists())
    }
}
