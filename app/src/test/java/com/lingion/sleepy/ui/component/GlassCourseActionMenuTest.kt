package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.ui.theme.UiStyle
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Production grid state changes only; these do not exercise complex course dragging. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-mdpi")
class GlassCourseActionMenuTest {
    @get:Rule val compose = createComposeRule()
    private val course = CourseEntity(id = 7, tableId = 1, groupId = "math", courseName = "Math",
        day = 1, startNode = 1, step = 2, startWeek = 1, endWeek = 16, color = "#FF6750A4")
    private val state = CourseFloatState()
    private var edits = 0
    private var deletes = 0

    private fun advanceGridFrames(milliseconds: Long) {
        val target = compose.mainClock.currentTime + milliseconds
        // CardsGridView subcomposes during Android measure/layout. Pump that
        // host work between Compose frames, as a real window does each vsync.
        while (compose.mainClock.currentTime < target) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private fun showGrid() {
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        Box(Modifier.size(360.dp, 640.dp)) {
                            CardsGridView(courses = listOf(course),
                                timeSlots = TimeTableUtils.timeSlotsFor(TimeTableUtils.DEFAULT_TIME_JSON),
                                currentWeek = 1, floatState = state,
                                onCourseClick = {},
                                onOccurrenceMove = { _, _, _, _, _, result -> result(true) },
                                onOccurrenceEdit = { edits++ }, onOccurrenceDelete = { deletes++ })
                        }
                    }
                }
            }
        }
        // CardsGridView clears a previous float when the initial course set is
        // loaded. A real lift happens after that load, as it does here.
        compose.runOnIdle { state.lift(course, 1, 1, 1, 2, 80.dp, 0.dp, 60.dp, 80.dp, false) }
        compose.onNodeWithTag("float-edit").assertIsDisplayed()
    }

    @Test fun editingRetainsTheExitButDisablesBothActionsUntilItFinishes() {
        showGrid()
        compose.mainClock.autoAdvance = false
        val staleDelete = compose.onNodeWithTag("float-delete").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        compose.onNodeWithTag("float-edit").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("float-edit").assertIsNotEnabled()
        compose.onNodeWithTag("float-delete").assertIsNotEnabled()
        compose.runOnIdle {
            staleDelete()
            assertEquals(1, edits)
            assertEquals(0, deletes)
            assertNull(state.popupBounds)
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("float-popup").assertExists()
        compose.mainClock.advanceTimeBy(1_500)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("float-popup").assertDoesNotExist()
        compose.runOnIdle { assertTrue(!state.active) }
    }

    @Test fun aQuickReturnFromMovingRetainsTheMenuAndRestoresItsActionBounds() {
        showGrid()
        val originalId = compose.onNodeWithTag("float-popup").fetchSemanticsNode().id
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.dragging = true }
        advanceGridFrames(100)
        compose.runOnIdle {
            assertTrue(state.active)
            assertTrue(state.dragging)
        }
        compose.onNodeWithTag("float-edit").assertIsNotEnabled()
        compose.runOnIdle { assertNull(state.popupBounds) }
        compose.runOnIdle { state.dragging = false }
        advanceGridFrames(1_500)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("float-edit").assertIsEnabled()
        assertEquals(originalId, compose.onNodeWithTag("float-popup").fetchSemanticsNode().id)
        compose.runOnIdle {
            assertTrue(state.popupBounds != null)
            assertEquals(0, edits)
            assertEquals(0, deletes)
        }
        compose.onNodeWithTag("float-edit").performClick()
        compose.runOnIdle { assertEquals(1, edits) }
    }
}
