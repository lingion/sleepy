package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rUS-w360dp-h640dp-port-xhdpi")
class FloatDragRegressionTest {
    @get:Rule val compose = createComposeRule()
    private val state = CourseFloatState()
    private val flips = mutableListOf<Int>()
    private val course = CourseEntity(id = 7, tableId = 1, groupId = "math", courseName = "Drag",
        day = 3, startNode = 3, step = 2, startWeek = 1, endWeek = 16, color = "#FF6750A4")

    private fun grid() {
        val hostWeek = mutableIntStateOf(1)
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                Box(Modifier.size(360.dp, 640.dp)) {
                    CardsGridView(courses = listOf(course),
                        timeSlots = TimeTableUtils.timeSlotsFor(TimeTableUtils.DEFAULT_TIME_JSON),
                        floatState = state, currentWeek = hostWeek.intValue, onCourseClick = {},
                        onOccurrenceMove = { _, _, _, _, _, _ -> },
                        onEdgeDrag = { flips.add(it); hostWeek.intValue = state.week }, floatMaxWeek = 16)
                }
            }
        }
    }

    @Test fun sundayPlacementDoesNotArmPagingEvenAfterLongHorizontalDrag() {
        grid()
        compose.onNodeWithText("Drag").performTouchInput { longClick() }
        // 用真实落点几何摆到周日，排除手势 slop 对末段位移的干扰。
        val sunday = compose.onNodeWithTag("add-course-7-3").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            state.dragOriginCx = state.x + state.width / 2f
            state.x = with(compose.density) { sunday.left.toDp() }
            state.dragging = true
        }
        compose.mainClock.advanceTimeBy(1200)
        compose.runOnIdle {
            assertEquals("周日是落子区", 0, state.edgeDir)
            assertTrue("驻留周日不能翻页", flips.isEmpty())
        }
    }

    @Test fun fingerAtRightEdgeCanPageWhenCardCenterIsStillInside() {
        grid()
        compose.onNodeWithText("Drag").performTouchInput { longClick() }
        val card = compose.onNodeWithTag("float-card").fetchSemanticsNode().boundsInRoot
        // 抓住卡片右侧，手指停在距屏缘 2dp，中心仍在触发线内侧。
        compose.onNodeWithTag("float-card").performTouchInput {
            down(Offset(width - 2f, height / 2f))
            moveBy(Offset(358.dp.toPx() - (card.right - 2f), 0f), 100)
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(700)
        compose.runOnIdle { assertEquals(listOf(1), flips) }
        compose.onNodeWithTag("float-card").performTouchInput { up() }
    }

    @Test fun freeDragIsPlacedAtTheInputPositionOnTheNextFrame() {
        grid()
        compose.onNodeWithText("Drag").performTouchInput { longClick() }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            state.dragging = true
            state.dropTarget = null
            state.x = 280.dp
            state.y = 180.dp
            state.snapX = state.x
            state.snapY = state.y
        }
        compose.mainClock.advanceTimeByFrame()
        val bounds = compose.onNodeWithTag("float-card").fetchSemanticsNode().boundsInRoot
        assertEquals("跟手位不能再等150ms补间", state.anchorRootLeftPx + with(compose.density) { 280.dp.toPx() }, bounds.left, 1f)
    }

    @Test fun realPagerKeepsOnlyTheGestureSourceAndNearbyPagesAlive() {
        verifyPagerGesture(startOnOverlay = false)
    }

    @Test fun regrabbingFloatingCardSurvivesRealPagerChanges() {
        verifyPagerGesture(startOnOverlay = true)
    }

    private fun verifyPagerGesture(startOnOverlay: Boolean) {
        val composedPages = mutableSetOf<Int>()
        var maxPages = 0
        lateinit var scope: CoroutineScope
        lateinit var pager: androidx.compose.foundation.pager.PagerState
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                pager = rememberPagerState { 100 }
                scope = rememberCoroutineScope()
                HorizontalPager(pager, beyondViewportPageCount = 1, modifier = Modifier.size(360.dp, 640.dp)) { page ->
                    if (!shouldComposeFloatDragPage(page + 1, pager.currentPage + 1, state)) return@HorizontalPager
                    DisposableEffect(page) {
                        composedPages.add(page)
                        maxPages = maxOf(maxPages, composedPages.size)
                        onDispose { composedPages.remove(page) }
                    }
                    CardsGridView(courses = if (page == 0) listOf(course) else emptyList(),
                        timeSlots = TimeTableUtils.timeSlotsFor(TimeTableUtils.DEFAULT_TIME_JSON),
                        currentWeek = page + 1, floatState = state,
                        onCourseClick = {}, onOccurrenceMove = { _, _, _, _, _, _ -> })
                }
            }
        }
        if (startOnOverlay) {
            compose.onNodeWithText("Drag").performTouchInput { longClick() }
            compose.onNodeWithTag("float-card").performTouchInput {
                down(center)
                moveBy(Offset(100f, 0f), 50)
            }
        } else {
            compose.onNodeWithText("Drag").performTouchInput {
                down(center)
                moveTo(center, 900)
                moveBy(Offset(100f, 0f), 50)
            }
        }
        compose.runOnIdle { assertTrue(state.dragging) }
        val initialPixels = compose.onNodeWithTag("float-card").captureToImage().toPixelMap()
        val expectedCardColor = initialPixels[initialPixels.width / 2, initialPixels.height * 3 / 4]
        for (week in 2..20) {
            compose.runOnIdle {
                state.week = week
                scope.launch { pager.animateScrollToPage(week - 1) }
            }
            compose.waitForIdle()
            compose.onNodeWithTag("float-card").assertIsDisplayed()
        }
        compose.runOnIdle {
            assertTrue("起拖页不能被回收", 0 in composedPages)
            assertTrue("100周也只运行起拖页和附近网格: $maxPages", maxPages <= 6)
        }
        val finalPixels = compose.onNodeWithTag("float-card").captureToImage().toPixelMap()
        val actualCardColor = finalPixels[finalPixels.width / 2, finalPixels.height * 3 / 4]
        assertEquals("翻页后浮卡不能被目标页遮罩盖住", expectedCardColor, actualCardColor)
        val before = state.x
        compose.onNodeWithTag("float-card").performTouchInput { moveBy(Offset(-60f, 0f), 50) }
        compose.runOnIdle { assertTrue("连续19次翻页后原手势仍能回拉", state.x < before) }
        compose.onNodeWithTag("float-card").performTouchInput { up() }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse("松手释放来源页", 0 in composedPages) }
    }
}
