package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 真实 Compose 派发管线内的整页冻结契约 (需求 2026-10-27 三次修正; 真机四轮反馈
 * "按住滑块往下扯页面照滚" 的本地复现/回归载体)。
 *
 * 结构照抄 AddCourseScreen 生产形态: 外层 LazyColumn (唯一滚动容器, 内含多个
 * item 保证有得可滚), IntRangeSlider 作为某个 item 的内容。手指按在滑杆
 * (RangeSlider 语义节点 = 轨道本体) 上往下扯 — 期望 firstVisibleItemIndex 与
 * offset 纹丝不动, 滑块值也不动。
 *
 * 这是源码级守卫 (IntRangeSliderVerticalDragTest B 节) 的运行时补强: 守卫只锁
 * 接线形态, 本测试锁"消费真的先于外层读取"这一派发时序假设 — 四轮真机失败说明
 * 假设可能与现实不符, 必须在管线里看真相而不是继续猜。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rUS-w360dp-h640dp-port-xhdpi")
class IntRangeSliderFreezePipelineTest {

    @get:Rule val compose = createComposeRule()

    private fun pageWithSlider(state: LazyListState, onChange: (Int, Int) -> Unit) {
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                LazyColumn(state = state, modifier = Modifier.fillMaxSize().testTag("page")) {
                    items(List(8) { "filler-$it" }) {
                        Text(it, modifier = Modifier.padding(24.dp))
                    }
                    item {
                        Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                            IntRangeSlider(
                                summary = "第1–8周",
                                minValue = 1,
                                maxValue = 16,
                                startValue = 1,
                                endValue = 8,
                                onValueChange = onChange,
                                modifier = Modifier.testTag("slider"),
                            )
                        }
                    }
                    items(List(15) { "tail-$it" }) {
                        Text(it, modifier = Modifier.padding(24.dp))
                    }
                }
            }
        }
    }

    private fun plainPage(state: LazyListState) {
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                LazyColumn(state = state, modifier = Modifier.fillMaxSize().testTag("page")) {
                    items(List(40) { "row-$it" }) {
                        Text(it, modifier = Modifier.padding(24.dp))
                    }
                }
            }
        }
    }

    // ---- 对照: 证明测试管线本身能滚页 (排除测量手段背锅) ----
    // 起始 idx=3: 上下两个方向都有得滚 — 从 0 下拖本来就到顶, 会制造双向假象。
    // 方向口径: 手指向下拖 = 内容跟手向下 = 露出更早的项 = firstVisibleItemIndex 减小。

    @Test fun control_plainLazyColumnScrollsBothDirections() {
        val state = LazyListState(firstVisibleItemIndex = 3)
        plainPage(state)
        compose.onNodeWithTag("page").performTouchInput {
            down(Offset(100f, 100f))
            moveBy(Offset(0f, 150f))
            moveBy(Offset(0f, 150f))
            moveBy(Offset(0f, 150f))
            up()
        }
        compose.runOnIdle {
            assertTrue(
                "对照用例: 普通页下拖必须滚回更早内容 (idx=${state.firstVisibleItemIndex} off=${state.firstVisibleItemScrollOffset})",
                state.firstVisibleItemIndex < 3 ||
                    (state.firstVisibleItemIndex == 3 && state.firstVisibleItemScrollOffset > 0),
            )
        }
        compose.onNodeWithTag("page").performTouchInput {
            down(Offset(100f, 100f))
            moveBy(Offset(0f, -150f))
            moveBy(Offset(0f, -150f))
            moveBy(Offset(0f, -150f))
            up()
        }
        compose.runOnIdle {
            assertTrue(
                "对照用例: 普通页上拖必须滚向更新内容 (idx=${state.firstVisibleItemIndex})",
                state.firstVisibleItemIndex > 3,
            )
        }
    }

    // ---- 冻结契约 ----

    // 列表从第 5 行起步、滑杆在第 8 项: 上方有旧内容 (下拖可滚出), 下方有新内容
    // (上拖可滚出) — 两个方向的"页面纹丝不动"都是可证伪的断言, 不存在到顶假绿。
    private fun midListState() = LazyListState(firstVisibleItemIndex = 5, firstVisibleItemScrollOffset = 0)

    /** 同批大位移帧 (对应真机"往下一扯"的快拨): 页面与滑块值都不许动。 */
    @Test fun fastDownYankOnSliderDoesNotScrollPage() {
        val state = midListState()
        var changes = 0
        pageWithSlider(state) { _, _ -> changes++ }
        compose.onNodeWithContentDescription("第1–8周").performTouchInput {
            down(center)
            moveBy(Offset(0f, 40f))
            moveBy(Offset(0f, 40f))
            moveBy(Offset(0f, 40f))
            moveBy(Offset(0f, 40f))
            up()
        }
        compose.runOnIdle {
            assertEquals("页面被纵向拖动带滚", 5, state.firstVisibleItemIndex)
            assertEquals("页面被纵向拖动带滚(offset)", 0, state.firstVisibleItemScrollOffset)
            assertEquals("纵向拖拽不得改滑块值", 0, changes)
        }
    }

    /** 慢速小步下扯 (每帧远小于 touchSlop, 真机三次修正根因形态): 累计也不许滚。 */
    @Test fun slowSmallVerticalStepsDoNotAccumulateIntoScroll() {
        val state = midListState()
        var changes = 0
        pageWithSlider(state) { _, _ -> changes++ }
        compose.onNodeWithContentDescription("第1–8周").performTouchInput {
            down(center)
            repeat(24) { moveBy(Offset(0f, 3f)) }
            up()
        }
        compose.runOnIdle {
            assertEquals(5, state.firstVisibleItemIndex)
            assertEquals(0, state.firstVisibleItemScrollOffset)
            assertEquals(0, changes)
        }
    }

    /** 横占优斜向左下拖 (真机拇指路径): 滑块照常改值, 页面不滚。
     *  注: 纯 45° 平局在 material3 按压循环两分支皆不命中 (x>y 才进 drag),
     *  那是 RangeSlider 自身行为, 45° 等效性由纯函数层锁定, 不在此重复。 */
    @Test fun diagonalLeftDragMovesSliderWithoutScrollingPage() {
        val state = midListState()
        val seen = mutableListOf<Pair<Int, Int>>()
        pageWithSlider(state) { s, e -> seen += s to e }
        compose.onNodeWithContentDescription("第1–8周").performTouchInput {
            down(center)
            moveBy(Offset(-40f, 25f))
            moveBy(Offset(-40f, 25f))
            moveBy(Offset(-40f, 25f))
            up()
        }
        compose.runOnIdle {
            assertTrue("横向分量必须驱动滑块改值, seen=$seen", seen.isNotEmpty())
            assertEquals(5, state.firstVisibleItemIndex)
            assertEquals(0, state.firstVisibleItemScrollOffset)
        }
    }

    /** 冻结只锁滑杆按压期; 之后手指按在普通 item 上, 页面照常可滚。 */
    @Test fun page_stillScrollsOutsideTheSlider() {
        val state = midListState()
        pageWithSlider(state) { _, _ -> }
        // 先确认滑杆按压冻结生效 (下扯有上方内容可滚还纹丝不动)
        compose.onNodeWithContentDescription("第1–8周").performTouchInput {
            down(center)
            moveBy(Offset(0f, 50f))
            moveBy(Offset(0f, 50f))
            up()
        }
        compose.runOnIdle {
            assertEquals("滑杆按压期必须冻结页面", 5, state.firstVisibleItemIndex)
        }
        // 抬手后在普通行上上拖 (新内容方向) → 页面必须跟手滚
        compose.onNodeWithTag("page").performTouchInput {
            down(Offset(100f, 100f))
            moveBy(Offset(0f, -150f))
            moveBy(Offset(0f, -150f))
            moveBy(Offset(0f, -150f))
            up()
        }
        compose.runOnIdle {
            assertTrue(
                "滑块按压不应把整页滚动手势永久杀死: idx=${state.firstVisibleItemIndex} off=${state.firstVisibleItemScrollOffset}",
                state.firstVisibleItemIndex > 5 || state.firstVisibleItemScrollOffset > 0,
            )
        }
    }

    /** 真机四轮"往下一扯页面照滚"与本地单指全绿的差异锁定: 多指泄漏 (需求
     *  2026-10-28 五次修正)。第二指 (掌缘/边缘误触) 先落在滑杆区域成为主手势键
     *  (旧实现认 down.id), 主指后落在轨道上纵向快拨; 第二指中途抬起 → 旧实现
     *  awaitEachGesture 循环 break → 冻结静默 → 主指的后续下扯帧原样漏给
     *  LazyColumn → 页面滚 (RED 实录: idx 5→0)。修复后: 冻结窗口按"区域内任意
     *  手指"计, 全部抬起才收 → 页面必须纹丝不动 (本用例 = 该修复的回归锁)。 */
    @Test fun strayPointerLiftMidGestureKeepsPageFrozen() {
        val state = midListState()
        val seen = mutableListOf<Pair<Int, Int>>()
        pageWithSlider(state) { s, e -> seen += s to e }
        val pageTopLeft = compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot.topLeft
        val slider = compose.onNodeWithContentDescription("第1–8周").fetchSemanticsNode().boundsInRoot
        // performTouchInput 的坐标是被注入节点 (page) 的局部系: 根坐标减去 page 原点。
        // 掌缘落点在滑杆上方"第1–8周"摘要带 (wrapper Column 内、轨道外) — 落轨道上
        // 会被 RangeSlider 判成合法 tap 跳档, 混淆"纵向拖不改值"的断言对象。
        val palmLocal = Offset(slider.left + 30f, slider.top - 20f) - pageTopLeft
        val thumbLocal = slider.center - pageTopLeft
        compose.onNodeWithTag("page").performTouchInput {
            down(position = palmLocal, pointerId = 0)   // 第二指先落 — wrapper 键在它
            down(position = thumbLocal, pointerId = 1)  // 主指按在轨道上
            repeat(4) { moveBy(pointerId = 1, delta = Offset(0f, 40f)) }
            up(0)                                       // 第二指抬手 (真机: 掌缘离开)
            repeat(4) { moveBy(pointerId = 1, delta = Offset(0f, 40f)) }
            up(1)
        }
        compose.runOnIdle {
            assertEquals("第二指抬起后主指下扯不得带滚页面", 5, state.firstVisibleItemIndex)
            assertEquals("第二指抬起后主指下扯不得带滚页面(offset)", 0, state.firstVisibleItemScrollOffset)
            assertTrue("掌缘落点不得误改滑块值, seen=$seen", seen.isEmpty())
        }
    }
}
