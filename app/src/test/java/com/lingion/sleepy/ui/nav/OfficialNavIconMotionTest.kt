package com.lingion.sleepy.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.Tab
import com.lingion.sleepy.ui.component.NavIconMotionKey
import com.lingion.sleepy.ui.component.OfficialNavIconMotion
import com.lingion.sleepy.ui.component.rememberOfficialNavIconMotion
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 官方形态(NavigationBar/NavigationRail)动效接线契约 — 逐像素。
 *
 * 报障根因(2026-11-08 同学反馈"更新后底栏图标不动"): 默认贴底走官方
 * NavigationBar, 图标经 NavigationTabIcon 绘制, 此前从未接 NavIconDriver —
 * 动效真源全在(NavIconMotionTest 逐值锁定), 官方分支一个消费者都没有。
 * dock 分支不在本测试范围(PillNavigationBar 自有接线); 这里锁的是
 * rememberOfficialNavIconMotion → NavigationTabIcon 这条生产链路本身:
 *
 * 1. 选中边沿 → 该 tab 图标必有像素变化(接线生效);
 * 2. 动画结束 → 像素停止变化(调度收敛, 无空转);
 * 3. 非 GearSpin key → 回到与静止全等(恒等恢复; 齿轮按设计停在 +90°,
 *    恒等断言只适用于会回零的 key);
 * 4. 每个 tab 都必须有按压跟踪接线(贴底生产代码对 pressTrackerFor 用 !!,
 *    null 即线上崩溃);
 * 5. press 信号必须被 effects 活读消费: 置 true → 齿轮持续缓转,
 *    置 false → 停在当前角度不再动。
 *
 * 手法与 TabContentTransitionPixelTest 一致: 手动帧时钟 + captureToImage,
 * 静止帧两两全等(确定性), 中间帧允许抗锯齿差异但"必须有差异"是硬断言。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-port-xhdpi")
class OfficialNavIconMotionTest {
    @get:Rule val compose = createComposeRule()

    private val motionKeys = Tab.entries.map { it.motionKey }

    private fun capture(tag: String): List<IntArray> {
        val px = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        return List(px.height) { y -> IntArray(px.width) { x -> px[x, y].toArgb() } }
    }

    private fun framesEqual(a: List<IntArray>, b: List<IntArray>): Boolean =
        a.size == b.size && a.zip(b).all { (r, s) -> r.contentEquals(s) }

    /** 生产接线渲染: 每 tab 独立 48dp 白底视口, 图标经 NavigationTabIcon+驱动表。 */
    private fun setHarness(motion: MutableRef) {
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                motion.value = rememberOfficialNavIconMotion(motionKeys, motion.selected.value)
                Column {
                    Tab.entries.forEach { tab ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White)
                                .testTag(tab.name),
                        ) {
                            NavigationTabIcon(tab, showUpdateDot = false, motion = motion.value)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    /** 跨 setContent lambda 取驱动表句柄(与 lambda 捕获同寿命, 非全局态)。 */
    private class MutableRef {
        var value: OfficialNavIconMotion? = null
        val selected = mutableStateOf(Tab.Schedule.ordinal)
    }

    /** 负对照: 与正测试同构(选中状态照样切), 但图标 motion=null —— 复现报障态。 */
    private class NullHarness {
        val selected = mutableStateOf(Tab.Schedule.ordinal)
    }

    @Test
    fun negativeControl_unwiredIcons_doNotMove() {
        // 证明 selectionEdge 的"必须有差异"不是空转: 报障态(motion=null)下
        // 切选中后 10 帧内任何图标都不许有像素变化。
        val h = NullHarness()
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                Column {
                    Tab.entries.forEach { tab ->
                        Box(
                            modifier = Modifier.size(48.dp).background(Color.White).testTag(tab.name),
                        ) {
                            NavigationTabIcon(tab, showUpdateDot = false, motion = null)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val ref = Tab.entries.associateWith { capture(it.name) }
        for (tab in listOf(Tab.Today, Tab.Manage, Tab.Mine, Tab.Schedule)) {
            compose.runOnIdle { h.selected.value = tab.ordinal }
            repeat(10) {
                compose.mainClock.advanceTimeByFrame()
                Tab.entries.forEach { other ->
                    assertTrue(
                        "未接线态切到 ${tab.name} 时 ${other.name} 出现像素变化 = 负对照失效",
                        framesEqual(capture(other.name), ref.getValue(other)),
                    )
                }
            }
        }
    }

    @Test
    fun selectionEdge_animatesEachIcon_andSettlesPerKey() {
        val ref = MutableRef()
        setHarness(ref)
        val rest = Tab.entries.associateWith { capture(it.name) }
        // 静息确定性: 再抓一次逐像素全等, 否则下面的"必须有差异"断言不成立。
        Tab.entries.forEach { assertTrue("${it.name} 静止帧不确定", framesEqual(rest.getValue(it), capture(it.name))) }

        // 顺序必须保证每次都是"本次选中且上次未选中"边沿; 起点 Schedule=初始选中。
        val order = listOf(Tab.Today, Tab.Manage, Tab.Mine, Tab.Schedule)
        for (tab in order) {
            compose.runOnIdle { ref.selected.value = tab.ordinal }
            var moved = false
            repeat(10) {
                compose.mainClock.advanceTimeByFrame()
                if (!moved && !framesEqual(capture(tab.name), rest.getValue(tab))) moved = true
            }
            assertTrue(
                "${tab.name} 选中边沿图标没动 = 官方形态接线回归(2026-11-08 报障场景)",
                moved,
            )

            compose.mainClock.advanceTimeBy(2500)
            compose.waitForIdle()
            val settled = capture(tab.name)
            compose.mainClock.advanceTimeBy(500)
            assertTrue("${tab.name} 动画结束后仍在空转", framesEqual(settled, capture(tab.name)))

            if (tab.motionKey != NavIconMotionKey.GearSpin) {
                // pulse 回 0 → 本图标必须回到静止(齿轮例外: 按设计停在累计角)。
                assertTrue(
                    "${tab.name} 边沿后未回到恒等",
                    framesEqual(rest.getValue(tab), capture(tab.name)),
                )
            }
            // 兄弟隔离: 边沿只动被选中的那个, 其它非齿轮图标必须纹丝不动
            // (齿轮一旦转过就停在累计角, 不进本断言)。
            Tab.entries.filter { it != tab && it.motionKey != NavIconMotionKey.GearSpin }
                .forEach { other ->
                    assertTrue(
                        "${tab.name} 边沿串动了 ${other.name}",
                        framesEqual(rest.getValue(other), capture(other.name)),
                    )
                }
        }
    }

    @Test
    fun everyTab_exposesPressTracker() {
        val ref = MutableRef()
        setHarness(ref)
        // 贴底生产接线对 pressTrackerFor(tab.ordinal)!! — null 即线上崩溃。
        Tab.entries.forEach { tab ->
            val tracker = ref.value!!.pressTrackerFor(tab.ordinal)
            assertTrue("$tab 缺按压跟踪接线", tracker != null)
        }
    }

    @Test
    fun pressSignal_isLiveRead_gearSpinsWhileHeld_andStopsOnRelease() {
        val ref = MutableRef()
        setHarness(ref)
        val manage = Tab.Manage

        val tracker = ref.value!!.pressTrackerFor(manage.ordinal)!!
        val beforePress = capture(manage.name)
        compose.runOnIdle { tracker.value = true }
        var spinning = false
        repeat(10) {
            compose.mainClock.advanceTimeByFrame()
            if (!spinning && !framesEqual(capture(manage.name), beforePress)) spinning = true
        }
        assertTrue(
            "按住齿轮不缓转 = press 闭包读到的是重组快照而非状态对象(活读契约回归)",
            spinning,
        )

        compose.runOnIdle { tracker.value = false }
        compose.mainClock.advanceTimeBy(1000)
        assertTrue(
            "松开后齿轮仍在继续转(循环未被取消)",
            framesEqual(capture(manage.name), run {
                compose.mainClock.advanceTimeBy(500)
                capture(manage.name)
            }),
        )
    }
}
