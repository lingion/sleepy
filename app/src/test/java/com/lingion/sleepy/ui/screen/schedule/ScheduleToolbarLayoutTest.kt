package com.lingion.sleepy.ui.screen.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.data.undo.UndoManager
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.After
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
@Config(sdk = [35], qualifiers = "zh-rCN-w900dp-h1800dp-mdpi")
class ScheduleToolbarLayoutTest {
    @get:Rule val compose = createComposeRule()
    @After fun clearHistory() { UndoManager.clear() }

    private fun render(width: Int, font: Float, history: Boolean) {
        if (history) UndoManager.capture(emptyList(), emptyList(), null)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass, LocalDensity provides Density(1f, font)) {
                    GlassHost {
                        Box(Modifier.width(width.dp)) {
                            TopBar(2, 20, "2099-09-01", null, {}, {}, {}, history, {}, {}, {}, {}, {}, {}, {})
                        }
                    }
                }
            }
        }
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun assertSeparated(history: Boolean) {
        val tags = listOf("schedule_switch_table", "schedule_prev_week", "schedule_week_label", "schedule_next_week", "schedule_add_course", "schedule_share") +
            if (history) listOf("schedule_undo", "schedule_redo", "schedule_scale_keep") else emptyList()
        val boxes = tags.map(::bounds)
        for (a in boxes.indices) for (b in a + 1 until boxes.size) {
            assertTrue("${tags[a]} must not overlap ${tags[b]}: ${boxes[a]}, ${boxes[b]}", !boxes[a].overlaps(boxes[b]))
        }
        listOf("schedule_switch_table", "schedule_prev_week", "schedule_next_week", "schedule_add_course", "schedule_share").forEach {
            compose.onNodeWithTag(it).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test fun narrowLargeFontKeepsEveryActionClearWithUndoAndScaleConfirmation() {
        render(280, 1.5f, true)
        assertSeparated(true)
        assertTrue("week navigation needs its own row when actions occupy the width", bounds("schedule_week_label").top > bounds("schedule_add_course").bottom)
    }

    @Test fun ordinaryPhoneKeepsLongSemesterStatusAndActionsClear() {
        render(360, 1f, false)
        assertSeparated(false)
        assertEquals("week navigation stays centered", 180f, (bounds("schedule_prev_week").left + bounds("schedule_next_week").right) / 2, 1f)
    }

    @Test fun wideToolbarKeepsTheOriginalSingleCenteredRow() {
        render(840, 1f, true)
        assertSeparated(true)
        assertEquals(bounds("schedule_add_course").center.y, bounds("schedule_prev_week").center.y, 1f)
        assertEquals(420f, (bounds("schedule_prev_week").left + bounds("schedule_next_week").right) / 2, 1f)
    }
    @Test fun disabledRedoCannotLiftTheSharedCapsuleButEnabledUndoCan() {
        compose.mainClock.autoAdvance = false
        render(360, 1f, true)
        val resting = bounds("schedule_undo")
        compose.onNodeWithTag("schedule_redo").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(600)
        assertEquals("disabled half cannot raise its shared pane", resting.width, bounds("schedule_undo").width, .2f)
        compose.onNodeWithTag("schedule_redo").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("schedule_undo").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(600)
        assertTrue("enabled half retains the shared optical feedback", bounds("schedule_undo").width > resting.width)
        compose.onNodeWithTag("schedule_undo").performTouchInput { up() }
    }

}
