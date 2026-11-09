package com.lingion.sleepy.ui.screen.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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

    private fun render(width: Int, font: Float, history: Boolean, prev: () -> Unit = {}, next: () -> Unit = {}, jump: () -> Unit = {}, rtl: Boolean = false, confirm: Boolean = history, screenDensity: Float = 1f) {
        if (history) UndoManager.capture(emptyList(), emptyList(), null)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass, LocalDensity provides Density(screenDensity, font),
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    GlassHost {
                        Box(Modifier.width(width.dp)) {
                            TopBar(2, 20, "2099-09-01", null, {}, {}, {}, confirm, {}, prev, next, jump, {}, {}, {})
                        }
                    }
                }
            }
        }
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun assertSeparated(history: Boolean, confirm: Boolean = history, minimum: Int = 32) {
        val tags = listOf("schedule_switch_table", "schedule_prev_week", "schedule_week_label", "schedule_next_week", "schedule_add_course", "schedule_share") +
            (if (history) listOf("schedule_undo", "schedule_redo") else emptyList()) +
            if (confirm) listOf("schedule_scale_keep") else emptyList()
        val boxes = tags.map(::bounds)
        for (a in boxes.indices) for (b in a + 1 until boxes.size) {
            assertTrue("${tags[a]} must not overlap ${tags[b]}: ${boxes[a]}, ${boxes[b]}", !boxes[a].overlaps(boxes[b]))
        }
        val previous = bounds("schedule_prev_week")
        val next = bounds("schedule_next_week")
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals("week navigation is centered on the entire toolbar", root.center.x, (previous.left + next.right) / 2f, .6f)
        boxes.forEach {
            assertTrue("every action stays inside the toolbar: $it, $root", it.left >= root.left - .6f && it.right <= root.right + .6f)
            assertEquals("all actions always share a single row", previous.center.y, it.center.y, 1f)
        }
        listOf("schedule_switch_table", "schedule_prev_week", "schedule_next_week", "schedule_add_course", "schedule_share").forEach {
            compose.onNodeWithTag(it).assertWidthIsAtLeast(minimum.dp).assertHeightIsAtLeast(minimum.dp)
        }
    }

    @Test fun narrowLargeFontCompressesTheWholeCapsuleWithoutWrapping() {
        var previous = 0
        var next = 0
        var jump = 0
        render(280, 1.5f, true, { previous++ }, { next++ }, { jump++ })
        assertSeparated(true, minimum = 1)
        compose.onNodeWithTag("schedule_week_group").assertIsDisplayed()
        compose.onNodeWithTag("schedule_week_label").assertTextEquals("2")
        val group = bounds("schedule_week_group")
        listOf("schedule_prev_week", "schedule_week_label", "schedule_next_week").forEach {
            val child = bounds(it)
            assertTrue("all three actions stay inside the shared capsule", child.left >= group.left && child.right <= group.right)
            compose.onNodeWithTag(it).performClick()
        }
        assertEquals(1, previous)
        assertEquals(1, jump)
        assertEquals(1, next)
    }

    @Test fun ordinaryPhoneKeepsFullWeekWordsRegardlessOfSemesterStatus() {
        render(360, 1f, false)
        assertSeparated(false)
        compose.onNodeWithTag("schedule_week_label").assertTextEquals("第 2 周")
        compose.onNodeWithTag("schedule_week_group").assertDoesNotExist()
        assertEquals("all actions stay on the same row", bounds("schedule_add_course").center.y, bounds("schedule_prev_week").center.y, 1f)
        compose.onNodeWithTag("schedule_switch_table").assertWidthIsAtLeast(32.dp)
    }

    @Test fun busyPhoneKeepsSeparateWeekActionsAfterShrinkingTheSides() {
        render(360, 1f, true, confirm = false)
        assertSeparated(true, confirm = false)
        compose.onNodeWithTag("schedule_week_group").assertDoesNotExist()
        compose.onNodeWithTag("schedule_week_label").assertTextEquals("第 2 周")
        assertEquals("side actions yield before changing the week label", bounds("schedule_add_course").center.y, bounds("schedule_prev_week").center.y, 1f)
        assertTrue(bounds("schedule_prev_week").right < bounds("schedule_week_label").left)
        assertTrue(bounds("schedule_week_label").right < bounds("schedule_next_week").left)
    }

    @Test fun groupedWeekControlKeepsThreeIndependentActionsAndItsFullDescription() {
        var previous = 0
        var next = 0
        var jump = 0
        render(320, 1f, true, { previous++ }, { next++ }, { jump++ })
        compose.onNodeWithTag("schedule_prev_week").performClick()
        compose.onNodeWithTag("schedule_week_label").performClick()
        compose.onNodeWithTag("schedule_next_week").performClick()
        assertEquals(1, previous)
        assertEquals(1, jump)
        assertEquals(1, next)
        val description = compose.onNodeWithTag("schedule_week_label").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].joinToString()
        assertTrue("short visible label keeps the semester and full week description", description.contains("学期未开始") && description.contains("第 2 周"))
    }

    @Test fun wideToolbarKeepsTheOriginalSingleCenteredRow() {
        render(840, 1f, true)
        assertSeparated(true)
        assertEquals(bounds("schedule_add_course").center.y, bounds("schedule_prev_week").center.y, 1f)
        assertEquals(420f, (bounds("schedule_prev_week").left + bounds("schedule_next_week").right) / 2, 1f)
        compose.onNodeWithTag("schedule_week_label").assertTextContains("第", substring = true)
    }
    @Test fun sideActionsShrinkBeforeTheFullWeekLabelIsShortened() {
        render(520, 1f, true)
        assertSeparated(true)
        compose.onNodeWithTag("schedule_week_label").assertTextContains("第", substring = true)
        compose.onNodeWithTag("schedule_week_group").assertDoesNotExist()
    }
    @Test fun rightToLeftKeepsTheSameScreenCenterAndPriority() {
        render(520, 1f, true, rtl = true)
        assertSeparated(true)
        compose.onNodeWithTag("schedule_week_label").assertTextContains("第", substring = true)
    }
    @Test fun smallestToolbarKeepsTheWholeCapsuleOnTheSameRow() {
        render(180, 2f, true)
        assertSeparated(true, minimum = 1)
        compose.onNodeWithTag("schedule_week_group").assertIsDisplayed()
    }
    @Test fun sideActionsScaleBeforeThreeSeparateWeekButtonsBecomeGrouped() {
        render(440, 1f, true)
        assertSeparated(true)
        compose.onNodeWithTag("schedule_week_label").assertTextEquals("第 2 周")
        compose.onNodeWithTag("schedule_week_group").assertDoesNotExist()
        assertTrue("side actions scale first", bounds("schedule_switch_table").width < 48f)
    }
    @Test fun extremeWidthStillKeepsEveryControlInOneRowWithoutOverlap() {
        render(120, 2f, true)
        assertSeparated(true, minimum = 1)
        compose.onNodeWithTag("schedule_week_group").assertIsDisplayed()
        compose.onNodeWithTag("schedule_week_label").assertTextEquals("2")
    }
    @Test fun fractionalDensityKeepsTheEntireFullWeekTextVisible() {
        render(520, 1f, false, screenDensity = 1.25f)
        compose.onNodeWithTag("schedule_week_label").assertTextEquals("第 2 周")
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("schedule_week_label").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult,
        ) { it(layouts) }
        assertTrue("the full week label must have been laid out", layouts.isNotEmpty())
        assertTrue("fractional density must not truncate full week words", layouts.none { it.hasVisualOverflow || it.isLineEllipsized(0) })
    }
    @Test fun fractionalPlacementAtExtremeWidthNeverOverlapsAndRemainsTouchable() {
        var previous = 0
        var next = 0
        var jump = 0
        render(109, 2f, true, { previous++ }, { next++ }, { jump++ })
        assertSeparated(true, minimum = 1)
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val group = bounds("schedule_week_group")
        assertEquals("centering also preserves fractional pixels", root.center.x, group.center.x, .001f)
        listOf("schedule_prev_week", "schedule_week_label", "schedule_next_week").forEach {
            compose.onNodeWithTag(it).performTouchInput { click(center) }
        }
        assertEquals(1, previous)
        assertEquals(1, jump)
        assertEquals(1, next)
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
