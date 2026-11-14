package com.lingion.sleepy.ui.component

import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToString
import com.lingion.sleepy.ui.component.liquidglass.GlassDatePickerDialog
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Focus and Compose geometry coverage; real IME/WindowManager timing needs device verification. */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w400dp-h800dp-mdpi")
class DatePickerFocusTransitionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var state: DatePickerState
    private val selectedDate = 1767225600000L

    private fun show(style: UiStyle, mode: DisplayMode = DisplayMode.Input) {
        compose.setContent {
            MaterialExpressiveTheme {
                CompositionLocalProvider(LocalUiStyle provides style) {
                    GlassHost {
                        state = rememberDatePickerState(
                            initialSelectedDateMillis = selectedDate,
                            initialDisplayMode = mode,
                        )
                        GlassDatePickerDialog({}, confirmButton = {}) {
                            SleepyDatePicker(state)
                        }
                    }
                }
            }
        }
    }

    @Test fun materialCalendarButtonReleasesTheOutgoingInputBeforeItsAnimationFinishes() =
        checkCalendarButton(UiStyle.Classic)

    @Test fun glassCalendarButtonReleasesTheOutgoingInputBeforeItsAnimationFinishes() =
        checkCalendarButton(UiStyle.LiquidGlass)

    private fun checkCalendarButton(style: UiStyle) {
        show(style)
        compose.onNode(hasSetTextAction()).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNode(hasSetTextAction()).assertIsFocused()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithContentDescription("Switch to calendar input mode").performClick()
        compose.mainClock.advanceTimeByFrame()

        // The input is still present during AnimatedContent's exit, but must no
        // longer hold focus while the calendar expands behind the keyboard.
        compose.onNode(hasSetTextAction()).assertIsNotFocused()
        compose.runOnIdle {
            assertEquals(DisplayMode.Picker, state.displayMode)
            assertEquals(selectedDate, state.selectedDateMillis)
        }
    }

    @Test fun materialCalendarExpansionKeepsTheDayGridRelativeToMonthNavigation() =
        checkCalendarGridGeometry(UiStyle.Classic)

    @Test fun glassCalendarExpansionKeepsTheDayGridRelativeToMonthNavigation() =
        checkCalendarGridGeometry(UiStyle.LiquidGlass)

    private fun checkCalendarGridGeometry(style: UiStyle) {
        show(style)
        val dayLabel = compose.runOnIdle {
            requireNotNull(DatePickerDefaults.dateFormatter().formatDate(
                selectedDate, state.locale, forContentDescription = true,
            ))
        }
        compose.onNode(hasSetTextAction()).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNode(hasSetTextAction()).assertIsFocused()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Switch to calendar input mode").performClick()

        val offsets = mutableListOf<Float>()
        var previousTime = 0L
        for (sampleTime in listOf(16L, 100L, 200L, 400L)) {
            compose.mainClock.advanceTimeBy(sampleTime - previousTime)
            previousTime = sampleTime
            if (sampleTime == 16L) {
                // One diagnostic dump per style keeps the real incoming date
                // semantics available if a future Material upgrade changes them.
                val roots = compose.onAllNodes(isRoot(), useUnmergedTree = true)
                roots.fetchSemanticsNodes().indices.forEach { index ->
                    println("Calendar geometry $style, day '$dayLabel', root $index:\n" +
                        roots[index].printToString())
                }
            }
            // Unclipped bounds inspect actual measured children while the incoming
            // calendar is still inside AnimatedContent's changing clip. Root/window
            // centering may move; the grid must keep its position within the month.
            // Material 1.5 clears the visible numeral/selection semantics and
            // exposes its formatter's complete date as Text on the day button.
            val day = compose.onNodeWithText(dayLabel).getUnclippedBoundsInRoot()
            val navigation = compose.onNodeWithContentDescription("Change to next month")
                .getUnclippedBoundsInRoot()
            assertTrue("day 1 must be measured at ${sampleTime}ms", day.bottom > day.top)
            assertTrue("month navigation must be measured at ${sampleTime}ms", navigation.bottom > navigation.top)
            offsets += (day.top - navigation.bottom).value
            compose.runOnIdle {
                assertEquals(DisplayMode.Picker, state.displayMode)
                assertEquals(selectedDate, state.selectedDateMillis)
            }
        }
        assertTrue("day grid must remain below month navigation", offsets.first() > 0f)
        offsets.forEachIndexed { index, offset ->
            assertEquals("day 1/month navigation offset at sample $index", offsets.first(), offset, 1f)
        }
        // This catches Compose grid repositioning. It does not simulate Android's
        // real IME insets, WM's two-pass window sizing, or prove animation duration.
    }

    @Test fun externallyChangingTheModeAlsoReleasesInputFocusAndRetainsTheSelection() {
        show(UiStyle.LiquidGlass)
        compose.onNode(hasSetTextAction()).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNode(hasSetTextAction()).assertIsFocused()
        compose.mainClock.autoAdvance = false

        compose.runOnIdle {
            state.displayMode = DisplayMode.Picker
            // runOnIdle waits before the external write, not after it. Publish
            // that snapshot before advancing the calendar's first frame.
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithContentDescription("Switch to text input mode").assertExists()
        compose.onNode(hasSetTextAction()).assertIsNotFocused()
        compose.onAllNodes(hasSetTextAction() and isFocused()).assertCountEquals(0)
        compose.runOnIdle { assertEquals(selectedDate, state.selectedDateMillis) }
    }

    @Test fun aRapidReturnToCalendarCannotRunADelayedInputFocusRequest() {
        show(UiStyle.LiquidGlass, mode = DisplayMode.Picker)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Switch to text input mode").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNode(hasSetTextAction()).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNode(hasSetTextAction()).assertIsFocused()

        compose.onNodeWithContentDescription("Switch to calendar input mode").performClick()
        compose.mainClock.advanceTimeByFrame()

        // Inspect the outgoing field before it is disposed, then keep sampling
        // across Material's former 300ms auto-focus deadline, not only at rest.
        compose.onNode(hasSetTextAction()).assertIsNotFocused()
        repeat(4) {
            compose.mainClock.advanceTimeBy(100)
            compose.onAllNodes(hasSetTextAction() and isFocused()).assertCountEquals(0)
            compose.runOnIdle {
                assertEquals(DisplayMode.Picker, state.displayMode)
                assertEquals(selectedDate, state.selectedDateMillis)
            }
        }

        compose.mainClock.advanceTimeBy(400)
        compose.onAllNodes(hasSetTextAction() and isFocused()).assertCountEquals(0)
        compose.runOnIdle {
            assertEquals(DisplayMode.Picker, state.displayMode)
            assertEquals(selectedDate, state.selectedDateMillis)
        }
    }
}
