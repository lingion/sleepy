package com.lingion.sleepy.ui.screen.edit

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.util.CourseOccurrence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * OccurrenceEditor 节次行双头滑杆接线管线 (spec-slider-ghost-appwide-2026-10-29
 * §3.2/§4 第八站点; 载体 = 真实 OccurrenceEditor — 先例
 * CourseOccurrenceGestureTest.occurrenceEditorOpensStraightIntoTheEditFormWhenStartInEdit:
 * startInEdit=true 打开即表单态, ModalBottomSheet 在 Robolectric compose rule 下可直接寻址)。
 *
 * 锁 (用户 2026-10-29 裁决「跟编辑节次完全对齐」「连续节数当然删掉」):
 * 1. 标准区间课程: testTag("occurrence-node-slider") 在; 「起始节次」「连续节数」
 *    两个文本框整体消失 (Row 替换非并列);
 * 2. 基线 = 打开弹窗时刻 course.startNode..startNode+step-1 → 静置无幽灵;
 *    拖离 → testTag("slider-ghost") 出现; 原路拖回 → 消失;
 * 3. 离页重开 (shown 翻转拆重建子树, keyless remember 重算) → 幽灵不残留
 *    (「直到退出页面或保存才消失」的用户语义; 同法先例
 *    DisplaySliderGhostPipelineTest.re_entry_recomputes_baseline_ghost_gone);
 * 4. 非常规节次卡 / 起始节落在标准连续域外 → 滑杆不存在、两文本框保留
 *    (用户原话:「一旦使用非常规截词了, 就不归这个滑块管了」)。
 *
 * 拖动坐标: 复刻 material3 RangeSlider 摆放 (先例 IntRangeSliderGhostPipelineTest
 * endThumbX), frac = (v-1)/(maxStd-1), 域 1..12 (DEFAULT_TIME_JSON)。
 * 手势形态 = down 于当前终点 thumb x → 绝对 moveTo 目标档位 x → up: RangeSlider
 * 按下即吸附最近 thumb, 目标显式化 (fromValue→toValue) 避免 60px 相对位移
 * 落进哪个档位不可预知的老坑 (Task 2 记档教训)。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rUS-w360dp-h640dp-port-xhdpi")
class OccurrenceNodeSliderPipelineTest {

    @get:Rule val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val table = TimeTableEntity(id = 1, name = "Table", startDate = "2026-09-07", maxWeek = 16)
    private val date = java.time.LocalDate.parse("2026-09-08") // 开学第一周周二

    private fun course(startNode: Int, step: Int, irregular: Boolean = false) = CourseEntity(
        tableId = 1, groupId = "math", courseName = "GestureMath", day = 2,
        startNode = startNode, step = step, startWeek = 1, endWeek = 16,
        color = "#FF6750A4", isIrregularNode = irregular,
    )

    private fun rangeSummary(start: Int, end: Int) =
        app.getString(R.string.slot_summary_period_range, start, end, end - start + 1)

    private fun show(c: CourseEntity) {
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                OccurrenceEditor(
                    CourseOccurrence(c, date, date), table,
                    onDismiss = {}, startInEdit = true,
                )
            }
        }
        compose.waitForIdle()
    }

    /** 终点滑块 x (滑杆节点局部系): material3 摆放公式, 域 1..12。 */
    private fun endThumbX(nodeWidthPx: Float, endValue: Int): Float {
        val density = 2f // xhdpi (qualifiers)
        val thumbW = 4f * density
        val trackH = 16f * density
        val trackW = nodeWidthPx - thumbW
        val frac = (endValue - 1) / 11f
        val corner = trackH / 2f
        return thumbW / 2f + corner + (trackW - 2f * corner) * frac
    }

    /** 终点从 fromValue 拖到 toValue: 寻址 = RangeSlider 本体 (组件内部把摘要文本
     *  写进 contentDescription; 调用方 testTag 落在外层 Column 上, 其 bounds 含
     *  摘要行, 直接按它算 y 会偏 — 先例 IntRangeSliderGhostPipelineTest 同法)。
     *  按下即吸附终点 thumb, 绝对 moveTo 落档。 */
    private fun dragEndThumb(fromValue: Int, toValue: Int) {
        // 起点恒 3: 本用例域内只有起点 3 的卡被拖 (起点 13/非常规不拖)
        val slider = compose.onNodeWithContentDescription(rangeSummary(3, fromValue))
        val node = slider.fetchSemanticsNode()
        val y = node.boundsInRoot.height / 2f
        val x0 = endThumbX(node.boundsInRoot.width, fromValue)
        val x1 = endThumbX(node.boundsInRoot.width, toValue)
        slider.performTouchInput {
            down(Offset(x0, y))
            repeat(6) { i -> moveTo(Offset(x0 + (x1 - x0) * (i + 1) / 6f, y)) }
            up()
        }
        compose.waitForIdle()
    }

    private fun ghostExists(): Boolean =
        compose.onAllNodesWithTag("slider-ghost").fetchSemanticsNodes().isNotEmpty()

    @Test fun standard_course_replaces_both_fields_with_slider() {
        show(course(startNode = 3, step = 2))
        compose.onNodeWithTag("occurrence-node-slider").assertExists()
        compose.onNodeWithText(app.getString(R.string.occurrence_node)).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.occurrence_step)).assertDoesNotExist()
        // 摘要读数 = 显示口径 3..4 (双头滑杆顶行文本, 即 contentDescription 本体)
        compose.onNodeWithContentDescription(rangeSummary(3, 4)).assertExists()
    }

    @Test fun no_ghost_at_rest_appears_on_drag_away_and_back_hides() {
        show(course(startNode = 3, step = 2))
        assertFalse("基线=打开时刻区间, 静置不得有幽灵", ghostExists())
        dragEndThumb(4, 6)
        // 手势必须真的改值 (幽灵层不吃手势) — 摘要翻到 3..6
        compose.onNodeWithContentDescription(rangeSummary(3, 6)).assertExists()
        assertTrue("拖离基线后幽灵必须出现", ghostExists())
        dragEndThumb(6, 4)
        assertFalse("原路拖回基线幽灵必须消失", ghostExists())
    }

    @Test fun reopen_recomputes_baseline_ghost_gone() {
        val shown = androidx.compose.runtime.mutableStateOf(true)
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                if (shown.value) {
                    OccurrenceEditor(
                        CourseOccurrence(course(startNode = 3, step = 2), date, date), table,
                        onDismiss = {}, startInEdit = true,
                    )
                } else {
                    Text("已离页", modifier = Modifier.testTag("off-page"))
                }
            }
        }
        compose.waitForIdle()
        dragEndThumb(4, 6)
        assertTrue(ghostExists())
        shown.value = false
        compose.waitForIdle()
        compose.onNodeWithTag("off-page").assertExists()
        assertFalse("离页后整棵滑杆子树必须真的拆走 (防空转假绿)",
            compose.onAllNodesWithTag("occurrence-node-slider").fetchSemanticsNodes().isNotEmpty())
        shown.value = true
        compose.waitForIdle()
        // 重开时 course 对象不变 (测试侧不模拟落库), 基线 remember(course) 重算
        // = 现值区间 → 静置无幽灵; 生产语义同构: 保存落库后重进, 基线随新值重算。
        assertFalse("重开即基线重算, 幽灵不得残留", ghostExists())
    }

    @Test fun irregular_node_keeps_text_fields_no_slider() {
        show(course(startNode = 3, step = 2, irregular = true))
        compose.onNodeWithTag("occurrence-node-slider").assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.occurrence_node)).assertExists()
        compose.onNodeWithText(app.getString(R.string.occurrence_step)).assertExists()
        assertFalse("非常规路径永不接幽灵", ghostExists())
    }

    @Test fun out_of_domain_start_keeps_text_fields_no_slider() {
        // 起始节落在标准连续域外 (默认表 1..12, 取 13) — 域外保留文本框 (spec §3.2)
        show(course(startNode = 13, step = 1))
        compose.onNodeWithTag("occurrence-node-slider").assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.occurrence_node)).assertExists()
        compose.onNodeWithText(app.getString(R.string.occurrence_step)).assertExists()
    }
}
