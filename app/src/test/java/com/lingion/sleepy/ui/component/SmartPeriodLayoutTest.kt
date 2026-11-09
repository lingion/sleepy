package com.lingion.sleepy.ui.component

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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.lingion.sleepy.data.entity.DurationOption
import com.lingion.sleepy.data.entity.SmartPeriodConfig
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w320dp-h1800dp-mdpi")
class SmartPeriodLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun narrowGridReservesReadableCellsAndSeparatesDeleteFromInput() {
        compose.setContent {
            MaterialTheme(typography = com.lingion.sleepy.ui.theme.SleepyTypography) {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass, LocalDensity provides Density(1f, 1.5f)) {
                    GlassHost {
                        Box(Modifier.width(264.dp)) {
                            SmartPeriodEditor(
                                config = SmartPeriodConfig(totalPeriods = 12, durations = listOf(DurationOption(30, false))),
                                onConfigChange = {}, dbgTagPrefix = "smart",
                            )
                        }
                    }
                }
            }
        }
        val cells = (0..11).map { compose.onNodeWithTag("smart_dur#0_pos#$it", true).fetchSemanticsNode().boundsInRoot }
        cells.forEach { assertTrue("readable cell width: $it", it.width >= 47.9f && it.height >= 47.9f) }
        assertTrue("narrow grids must wrap before the eighth cell", cells[7].top > cells[0].top)
        for (a in cells.indices) for (b in a + 1 until cells.size) {
            assertTrue("cells $a and $b cannot overlap", !cells[a].overlaps(cells[b]))
        }
        val number = compose.onNodeWithTag("smart_dur#0_min", true).fetchSemanticsNode().boundsInRoot
        val delete = compose.onNodeWithTag("smart_dur#0_del", true).fetchSemanticsNode().boundsInRoot
        assertTrue("field and deletion action need an 8dp gutter", delete.left - number.right >= 7.9f)
        assertTrue("delete touch target", delete.width >= 47.9f && delete.height >= 47.9f)
    }

    @Test fun narrowInputsKeepLabelsAndValuesWhole() {
        compose.setContent {
            MaterialTheme(typography = com.lingion.sleepy.ui.theme.SleepyTypography) {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass, LocalDensity provides Density(1f, 1.5f)) {
                    GlassHost { Box(Modifier.width(216.dp)) {
                        SmartPeriodEditor(SmartPeriodConfig(), {}, dbgTagPrefix = "smart")
                    } }
                }
            }
        }
        for (tag in listOf("smart_dur_short", "smart_dur_long", "smart_break_short", "smart_break_long")) {
            val layouts = compose.onAllNodes((hasAnyAncestor(hasTestTag(tag)) or hasTestTag(tag)) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), true)
                .fetchSemanticsNodes().flatMap { node -> mutableListOf<TextLayoutResult>().also { node.config[SemanticsActions.GetTextLayoutResult].action!!(it) } }
            assertTrue("$tag exposes actual text layouts", layouts.isNotEmpty())
            layouts.forEach { assertWholeLine(tag, it) }
        }
        for (label in listOf("每节时长", "总节数")) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertWholeLine(label, layouts.single())
        }
    }

    @Test fun narrowManualRowsDisplayCompleteTimes() {
        compose.setContent {
            MaterialTheme(typography = com.lingion.sleepy.ui.theme.SleepyTypography) {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass, LocalDensity provides Density(1f, 1.5f)) {
                    GlassHost { Box(Modifier.width(216.dp)) {
                        TimeSlotEditor(
                            rows = listOf(com.lingion.sleepy.util.TimeTableUtils.TimeSlotRow(1, "08:00", "08:45")),
                            onRowsChange = {}, dbgTagPrefix = "manual",
                        )
                    } }
                }
            }
        }
        for (time in listOf("08:00", "08:45")) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(time, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertWholeLine(time, layouts.single())
        }
    }

    @Test fun narrowNumberFieldsKeepTheirEditableIdentity() {
        var changed = 0
        compose.setContent {
            MaterialTheme(typography = com.lingion.sleepy.ui.theme.SleepyTypography) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                    Box(Modifier.width(216.dp)) {
                        SmartPeriodEditor(SmartPeriodConfig(), { changed = it.periodMinutes }, dbgTagPrefix = "smart")
                    }
                }
            }
        }
        compose.onNodeWithTag("smart_duration").performTextReplacement("50")
        org.junit.Assert.assertEquals(50, changed)
    }
    private fun assertWholeLine(tag: String, layout: TextLayoutResult) {
        assertTrue("$tag must display all of ${layout.layoutInput.text}",
            layout.lineCount == 1 && layout.getLineEnd(0, visibleEnd = true) == layout.layoutInput.text.length &&
                layout.getLineRight(0) <= layout.size.width + 0.5f)
    }

}
