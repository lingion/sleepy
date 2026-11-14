package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import androidx.compose.ui.unit.dp
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
 * HueSlider 色相条幽灵真实派发管线 (spec-slider-ghost-appwide-2026-10-29 §3.1
 * 色相变体, Review Focus 4 专证):
 *
 * 1. initialHue=null (默认, 既有调用形态) → 怎么拖都永无 testTag("hue-ghost");
 * 2. 基线=现值 → 初始不显示;
 * 3. 拖离 → 出现; 拖回 ±0.5 度内 → 消失;
 * 4. 拖动过程 onHueCommit 一次不触发 (每个 onHueChange 帧快照 commit 数, 全程须 0),
 *    松手恰一次 — 节流分工归调用点 (HueSlider 自身语义, 先例注释 :201-203)。
 *
 * Material keeps the original full-width rainbow strip and circular indicator.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rUS-w360dp-h640dp-port-xhdpi")
class HueSliderGhostPipelineTest {

    @get:Rule val compose = createComposeRule()
    private lateinit var recorder: Recorder

    private class Recorder {
        var hue = 180f
        val commits = mutableListOf<Float>()
        val commitsAtDragFrame = mutableListOf<Int>()
    }

    private fun page(rec: Recorder, initialHue: Float?) {
        recorder = rec
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                var hue by remember { mutableStateOf(rec.hue) }
                HueSlider(
                    hue = hue,
                    onHueChange = {
                        rec.commitsAtDragFrame.add(rec.commits.size)
                        hue = it
                        rec.hue = it
                    },
                    onHueCommit = { rec.commits.add(it) },
                    initialHue = initialHue,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("hue"),
                )
            }
        }
        compose.waitForIdle()
    }

    /** 从历史彩虹条的当前色相位置分 6 步横移，保留真实拖动/提交断言。 */
    private fun drag(fromHue: Float, toHue: Float) {
        compose.onNodeWithTag("hue").performTouchInput {
            val delta = (toHue - fromHue) / 360f * width
            down(Offset(width * fromHue / 360f, centerY))
            repeat(6) { moveBy(Offset(delta / 6f, 0f)) }
            up()
        }
        compose.waitForIdle()
        assertEquals("手势必须到达目标色相", toHue, recorder.hue, .01f)
    }

    // The historical rainbow decoration retains its original tagged ghost.
    private fun ghostExists(): Boolean =
        compose.onAllNodesWithTag("hue-ghost", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test fun null_initial_hue_never_shows_ghost() {
        val rec = Recorder()
        page(rec, initialHue = null)
        drag(180f, 300f)
        assertTrue("前置: 拖动生效 (hue=${rec.hue})", rec.hue > 250f)
        assertFalse("initialHue=null 路径永不得出现幽灵", ghostExists())
    }

    @Test fun baseline_equal_current_no_ghost_at_rest() {
        page(Recorder(), initialHue = 180f)
        assertFalse("基线=现值时不得渲染幽灵", ghostExists())
    }

    @Test fun dragging_away_shows_ghost() {
        val rec = Recorder()
        page(rec, initialHue = 180f)
        drag(180f, 300f)
        assertTrue("拖离基线后幽灵必须出现 (hue=${rec.hue})", ghostExists())
    }

    @Test fun dragging_back_within_half_degree_hides_ghost() {
        page(Recorder(), initialHue = 180f)
        drag(180f, 300f)
        assertTrue(ghostExists())
        drag(300f, 180.4f)
        assertFalse("拖回基线 0.5 度容差内幽灵必须消失", ghostExists())
    }

    @Test fun drag_commits_zero_midway_exactly_once_on_release() {
        // Review Focus 4: 拖动只动本地值, 松手恰一次提交 — 幽灵接线不得改节流分工
        val rec = Recorder()
        page(rec, initialHue = 180f)
        drag(180f, 300f)
        assertTrue("前置: 拖动必须产生逐帧回调", rec.commitsAtDragFrame.isNotEmpty())
        assertEquals("拖动过程 commit 必须全程为 0 (每帧快照: ${rec.commitsAtDragFrame})",
            emptyList<Int>(), rec.commitsAtDragFrame.filter { it != 0 })
        assertEquals("松手必须恰提交一次", 1, rec.commits.size)
    }
}
