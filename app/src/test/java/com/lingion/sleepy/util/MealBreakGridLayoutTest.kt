package com.lingion.sleepy.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MealBreakGridLayoutTest {
    @Test
    fun maps_detected_breaks_to_sorted_display_rows_by_node() {
        val timeJson = TimeTableUtils.insertEdgeNode(
            TimeTableUtils.DEFAULT_TIME_JSON,
            TimeTableUtils.EdgeClass.Before,
            "07:30",
            "07:55"
        )
        val displayedNodeEnds = TimeTableUtils.timeSlotsFor(timeJson).map { it.nodeEnd }

        assertEquals(
            setOf(4, 8),
            MealBreakDetector.detectDisplayRowIndexes(timeJson, emptyList(), displayedNodeEnds)
        )
    }

    @Test
    fun vertical_grid_lines_are_split_around_meal_break_bands() {
        assertEquals(
            listOf(
                GridSeparatorGeometry.Span(0f, 20f),
                GridSeparatorGeometry.Span(30f, 40f),
                GridSeparatorGeometry.Span(50f, 60f)
            ),
            GridSeparatorGeometry.verticalSegments(
                0f,
                60f,
                listOf(GridSeparatorGeometry.Span(20f, 30f), GridSeparatorGeometry.Span(40f, 50f))
            )
        )
    }

    @Test
    fun vertical_grid_line_exclusions_are_clipped_and_merged() {
        assertEquals(
            listOf(GridSeparatorGeometry.Span(25f, 30f)),
            GridSeparatorGeometry.verticalSegments(
                10f,
                30f,
                listOf(
                    GridSeparatorGeometry.Span(0f, 12f),
                    GridSeparatorGeometry.Span(11f, 17f),
                    GridSeparatorGeometry.Span(17f, 25f)
                )
            )
        )
    }
}
