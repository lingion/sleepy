package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rUS-w360dp-h640dp-port-xhdpi")
class CourseOccurrenceGestureTest {
    @get:Rule val compose = createComposeRule()
    private val course = CourseEntity(id = 7, tableId = 1, groupId = "math", courseName = "GestureMath",
        day = 1, startNode = 1, step = 2, startWeek = 1, endWeek = 16, color = "#FF6750A4")

    @Test fun gridLongPressDoesNotClickOrAddAndBlankCellRequiresCapsuleConfirmation() {
        var clicks = 0
        var longPressed: Long? = null
        var added: Triple<Int, Int, Int>? = null
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                Box(Modifier.size(360.dp, 640.dp)) {
                    CardsGridView(courses = listOf(course), timeSlots = TimeTableUtils.timeSlotsFor(TimeTableUtils.DEFAULT_TIME_JSON),
                        onCourseClick = { clicks++ }, onCourseLongClick = { longPressed = it.id },
                        onQuickAdd = { day, node, step -> added = Triple(day, node, step) })
                }
            }
        }
        compose.onNodeWithText("GestureMath").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(7L, longPressed); assertEquals(0, clicks); assertNull(added) }
        compose.onNodeWithTag("add-course-2-1").performTouchInput { click() }
        compose.runOnIdle { assertNull(added) }
        compose.onNodeWithTag("quick-add-capsule").assertIsDisplayed()
        compose.onNodeWithTag("add-course-3-1").performTouchInput { click() }
        compose.onNodeWithTag("quick-add-capsule").assertDoesNotExist()
        compose.runOnIdle { assertNull(added) }
        compose.onNodeWithTag("add-course-2-1").performTouchInput { click() }
        compose.onNodeWithText("GestureMath").performTouchInput { click() }
        compose.onNodeWithTag("quick-add-capsule").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, clicks); assertNull(added) }
        compose.onNodeWithTag("add-course-2-1").performTouchInput { click() }
        compose.onNodeWithTag("quick-add-capsule").performTouchInput { click() }
        compose.runOnIdle { assertEquals(Triple(2, 1, 1), added) }
        compose.onNodeWithTag("quick-add-capsule").assertDoesNotExist()
        compose.onNodeWithText("GestureMath").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun capsuleHandlesResizeUsingScaledRowsAndPassTheWholeRange() {
        var added: Triple<Int, Int, Int>? = null
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                Box(Modifier.size(360.dp, 640.dp)) {
                    CardsGridView(courses = listOf(course),
                        timeSlots = TimeTableUtils.timeSlotsFor(TimeTableUtils.DEFAULT_TIME_JSON),
                        rowHeightScale = 1.4f, onCourseClick = {},
                        onQuickAdd = { day, node, step -> added = Triple(day, node, step) })
                }
            }
        }
        val rowHeight = compose.onNodeWithTag("add-course-2-3").fetchSemanticsNode().boundsInRoot.top -
            compose.onNodeWithTag("add-course-2-2").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("add-course-2-3").performTouchInput { click() }
        compose.onNodeWithTag("quick-add-top", useUnmergedTree = true).performTouchInput {
            swipe(center, center - androidx.compose.ui.geometry.Offset(0f, rowHeight), 300)
        }
        compose.onNodeWithTag("quick-add-bottom", useUnmergedTree = true).performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(0f, rowHeight), 300)
        }
        compose.runOnIdle { assertNull(added) }
        compose.onNodeWithTag("quick-add-capsule").performTouchInput { click() }
        compose.runOnIdle { assertEquals(Triple(2, 2, 3), added) }
    }

    @Test fun capsuleCanShrinkToOnePeriodAndWeekChangesDiscardIt() {
        val week = androidx.compose.runtime.mutableIntStateOf(1)
        var added: Triple<Int, Int, Int>? = null
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                Box(Modifier.size(360.dp, 640.dp)) {
                    CardsGridView(courses = listOf(course), currentWeek = week.intValue,
                        timeSlots = TimeTableUtils.timeSlotsFor(TimeTableUtils.DEFAULT_TIME_JSON),
                        onCourseClick = {}, onQuickAdd = { day, node, step -> added = Triple(day, node, step) })
                }
            }
        }
        val rowHeight = compose.onNodeWithTag("add-course-2-2").fetchSemanticsNode().boundsInRoot.top -
            compose.onNodeWithTag("add-course-2-1").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("add-course-2-1").performTouchInput { click() }
        compose.onNodeWithTag("quick-add-bottom", useUnmergedTree = true).performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(0f, rowHeight * 2), 300)
        }
        compose.onNodeWithTag("quick-add-top", useUnmergedTree = true).performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(0f, rowHeight * 4), 300)
        }
        compose.onNodeWithTag("quick-add-capsule").performTouchInput { click() }
        compose.runOnIdle { assertEquals(Triple(2, 3, 1), added); added = null }
        compose.onNodeWithTag("add-course-2-1").performTouchInput { click() }
        compose.runOnIdle { week.intValue = 2 }
        compose.onNodeWithTag("quick-add-capsule").assertDoesNotExist()
        compose.runOnIdle { assertNull(added) }
    }

    @Test fun capsuleResizeIncludesTheVisibleLunchGap() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val previous = com.lingion.sleepy.util.AppPrefs.isGridLongBreakSpacing(app)
        com.lingion.sleepy.util.AppPrefs.setGridLongBreakSpacing(app, true)
        try {
            var added: Triple<Int, Int, Int>? = null
            compose.setContent {
                SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                    Box(Modifier.size(360.dp, 640.dp)) {
                        CardsGridView(courses = listOf(course), timeJson = TimeTableUtils.DEFAULT_TIME_JSON,
                            timeSlots = TimeTableUtils.timeSlotsFor(TimeTableUtils.DEFAULT_TIME_JSON),
                            onCourseClick = {}, onQuickAdd = { day, node, step -> added = Triple(day, node, step) })
                    }
                }
            }
            val fourth = compose.onNodeWithTag("add-course-2-4").fetchSemanticsNode().boundsInRoot
            val fifth = compose.onNodeWithTag("add-course-2-5").fetchSemanticsNode().boundsInRoot
            val third = compose.onNodeWithTag("add-course-2-3").fetchSemanticsNode().boundsInRoot
            assertTrue("Fixture must contain a wider lunch gap", fifth.top - fourth.top > fourth.top - third.top)
            compose.onNodeWithTag("add-course-2-4").performTouchInput { click() }
            compose.onNodeWithTag("quick-add-bottom", useUnmergedTree = true).performTouchInput {
                swipe(center, center + androidx.compose.ui.geometry.Offset(0f, fifth.bottom - fourth.bottom), 300)
            }
            assertEquals(fifth.bottom, compose.onNodeWithTag("quick-add-capsule").fetchSemanticsNode().boundsInRoot.bottom, 1f)
            compose.onNodeWithTag("quick-add-capsule").performTouchInput { click() }
            compose.runOnIdle { assertEquals(Triple(2, 4, 2), added) }
        } finally {
            com.lingion.sleepy.util.AppPrefs.setGridLongBreakSpacing(app, previous)
        }
    }

    @Test fun fullWeekViewPropagatesLongPressToTheSelectedCourse() {
        var selected: Long? = null
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                Box(Modifier.size(360.dp, 640.dp)) {
                    FullWeekView(courses = listOf(course), onCourseClick = {}, onCourseLongClick = { selected = it.id })
                }
            }
        }
        compose.onAllNodesWithText("GestureMath")[0].performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(7L, selected); selected = null }
        compose.onAllNodesWithText("GestureMath")[1].performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(7L, selected) }
    }

    @Test fun datePickerRestrictsSemesterAndOnlyAppliesConfirmedSelection() {
        val table = com.lingion.sleepy.data.entity.TimeTableEntity(id = 1, name = "Table", startDate = "2026-10-05", maxWeek = 1)
        val date = java.time.LocalDate.parse("2026-10-05")
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                com.lingion.sleepy.ui.screen.edit.OccurrenceEditor(
                    com.lingion.sleepy.util.CourseOccurrence(course, date, date), table, {}
                )
            }
        }
        compose.onNodeWithText(app.getString(com.lingion.sleepy.R.string.occurrence_adjust)).performClick()
        compose.onNodeWithText(app.getString(com.lingion.sleepy.R.string.occurrence_date)).assertExists()
        compose.onNodeWithText(app.getString(com.lingion.sleepy.R.string.occurrence_save)).assertIsDisplayed()
        val formatter = java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL)
            .withLocale(app.resources.configuration.locales[0])
        compose.onNodeWithTag("occurrence-date").performClick()
        compose.onNodeWithText("2026年10月5日 星期一").assertIsSelected()
        compose.onNodeWithText("2026年10月4日 星期日").assertIsNotEnabled()
        compose.onNodeWithText("2026年10月12日 星期一").assertIsNotEnabled()
        compose.onNodeWithText("2026年10月6日 星期二").performClick()
        compose.onNodeWithText(app.getString(com.lingion.sleepy.R.string.cancel)).performClick()
        compose.onNodeWithTag("occurrence-date").assertTextContains(date.format(formatter))
        compose.onNodeWithTag("occurrence-date").performClick()
        compose.onNodeWithText("2026年10月5日 星期一").assertIsSelected()
        compose.onNodeWithText("2026年10月6日 星期二").performClick()
        compose.onNodeWithText(app.getString(com.lingion.sleepy.R.string.ok)).performClick()
        compose.onNodeWithTag("occurrence-date").assertTextContains(date.plusDays(1).format(formatter))
        compose.onNodeWithTag("occurrence-date").performClick()
        compose.onNodeWithText("2026年10月6日 星期二").assertIsSelected()
        compose.onNodeWithText(app.getString(com.lingion.sleepy.R.string.cancel)).performClick()
    }

    @Test fun longPressOnStackedCourseBadgeLetsTheUserChooseTheOccurrence() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.lingion.sleepy.util.AppPrefs.setConflictStyle(app, "stack")
        var selected: Long? = null
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                Box(Modifier.size(360.dp, 640.dp)) {
                    CardsGridView(courses = listOf(course, course.copy(id = 8, groupId = "other", courseName = "Other"),
                        course.copy(id = 9, groupId = "third", courseName = "Third")),
                        timeSlots = TimeTableUtils.timeSlotsFor(TimeTableUtils.DEFAULT_TIME_JSON),
                        onCourseClick = {}, onCourseLongClick = { selected = it.id })
                }
            }
        }
        compose.onNodeWithText("+1").performTouchInput { longClick() }
        compose.runOnIdle { assertNull(selected) }
        compose.onNode(hasText("Other") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { assertEquals(8L, selected) }
        compose.onNode(isDialog()).assertDoesNotExist()
    }
}
