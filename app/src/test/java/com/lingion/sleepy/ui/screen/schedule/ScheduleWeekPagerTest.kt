package com.lingion.sleepy.ui.screen.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.MainTabContent
import com.lingion.sleepy.Tab
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.component.CardsGridView
import com.lingion.sleepy.ui.component.FullWeekView
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-port-xhdpi")
class ScheduleWeekPagerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun gridReturnBuildsOnlySelectedWeek() = verifyReturn(ViewMode.Cards)
    @Test fun fullWeekReturnBuildsOnlySelectedWeek() = verifyReturn(ViewMode.Full)

    private fun verifyReturn(mode: ViewMode) {
        val tab = mutableStateOf(Tab.Today)
        val dragging = mutableStateOf(false)
        val mounted = mutableSetOf<Int>()
        val built = mutableSetOf<Int>()
        lateinit var pager: PagerState
        val courses = (1..7).map { day ->
            CourseEntity(id = day.toLong(), tableId = 1, groupId = "day$day",
                courseName = "Course $day", day = day, startNode = 1, step = 2,
                startWeek = 1, endWeek = 20, color = "#FF6750A4")
        }
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                val holder = rememberSaveableStateHolder()
                Box(Modifier.size(360.dp, 640.dp)) {
                    MainTabContent(tab.value) { current ->
                        holder.SaveableStateProvider(current.name) {
                            if (current == Tab.Schedule) {
                                pager = rememberPagerState(initialPage = 9) { 20 }
                                ScheduleWeekPager(pager, dragging.value, Modifier.fillMaxSize().testTag("weeks")) { page ->
                                    DisposableEffect(page) {
                                        mounted.add(page)
                                        built.add(page)
                                        onDispose { mounted.remove(page) }
                                    }
                                    if (mode == ViewMode.Cards) {
                                        CardsGridView(courses = courses,
                                            timeSlots = TimeTableUtils.timeSlotsFor(TimeTableUtils.DEFAULT_TIME_JSON),
                                            currentWeek = page + 1, onCourseClick = {})
                                    } else {
                                        FullWeekView(courses = courses, currentWeek = page + 1,
                                            timeJson = TimeTableUtils.DEFAULT_TIME_JSON, onCourseClick = {})
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.runOnIdle { tab.value = Tab.Schedule }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("Opening the timetable must not build offscreen weeks", setOf(9), built)
            tab.value = Tab.Today
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(mounted.isEmpty())
            built.clear()
            tab.value = Tab.Schedule
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("Returning must not rebuild three weeks", setOf(9), built) }

        compose.onNodeWithTag("weeks").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(10, pager.settledPage) }
        compose.onNodeWithTag("weeks").performTouchInput { swipeRight() }
        compose.runOnIdle {
            assertEquals(9, pager.settledPage)
            dragging.value = true
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("Edge dragging still needs both adjacent weeks", mounted.containsAll(setOf(8, 9, 10)))
            dragging.value = false
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("Ending a drag must release extra pages", mounted.size <= 2) }
    }
}
