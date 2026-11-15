package com.lingion.sleepy.ui.screen.schedule

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-w360dp-h640dp-mdpi")
class TableSwitcherDialogLayoutTest {
    @get:Rule val compose = createComposeRule()

    @After fun restoreFontScale() { RuntimeEnvironment.setFontScale(1f) }

    @Test fun glassSelectorKeepsMaterialWindowAndListGeometry() = checkLayout(1f)

    @Test fun glassSelectorKeepsMaterialGeometryWithLargeText() = checkLayout(2f)

    private fun checkLayout(fontScale: Float) {
        RuntimeEnvironment.setFontScale(fontScale)
        val style = mutableStateOf(UiStyle.Classic)
        var selected = 0L
        val tables = listOf(
            TimeTableEntity(id = 1, name = "Morning timetable", startDate = "2026-09-01"),
            TimeTableEntity(id = 2, name = "Evening timetable", startDate = "2026-09-01"),
        )
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides style.value) {
                    GlassHost(Modifier.fillMaxSize()) {
                        TableSwitcherDialog(tables, 1, { selected = it }, {})
                    }
                }
            }
        }
        val material = geometry()
        compose.runOnIdle { style.value = UiStyle.LiquidGlass }
        val glass = geometry()
        assertEquals(2, material.rows.size)
        assertEquals(2, glass.rows.size)
        assertEquals("pane width", material.pane.width, glass.pane.width, 1f)
        assertEquals("pane height", material.pane.height, glass.pane.height, 1f)
        material.rows.zip(glass.rows).forEachIndexed { index, (md, lg) ->
            assertEquals("row $index width", md.width, lg.width, 1f)
            assertEquals("row $index height", md.height, lg.height, 1f)
            assertEquals("row $index leading inset", md.left - material.pane.left,
                lg.left - glass.pane.left, 1f)
            assertEquals("row $index top inset", md.top - material.pane.top,
                lg.top - glass.pane.top, 1f)
        }
        compose.onNodeWithText("Evening timetable").performClick()
        compose.runOnIdle { assertEquals(2L, selected) }
    }

    private fun geometry() = Geometry(
        compose.onNodeWithTag("schedule_table_dialog").fetchSemanticsNode().boundsInRoot,
        compose.onAllNodesWithTag("schedule_table_item").fetchSemanticsNodes().map { it.boundsInRoot },
    )

    private data class Geometry(val pane: Rect, val rows: List<Rect>)
}
