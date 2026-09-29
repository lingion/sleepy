package com.lingion.sleepy.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodHeaderLayoutModelTest {
    private val metrics = PeriodHeaderMetrics(40f, 36f, 28f, false)

    @Test
    fun left_endpoint_uses_actual_outer_rectangle() {
        val p = metrics.solvePlacement(-1f)
        assertEquals(68f, p.contentWidth, 0.001f)
        assertEquals(28f, p.timeBaseLeft, 0.001f)
        assertEquals(0f, p.labelLeft, 0.001f)
    }

    @Test
    fun right_endpoint_uses_actual_outer_rectangle() {
        val p = metrics.solvePlacement(1f)
        assertEquals(68f, p.contentWidth, 0.001f)
        assertEquals(0f, p.timeBaseLeft, 0.001f)
        assertEquals(40f, p.labelLeft, 0.001f)
    }

    @Test
    fun current_rectangle_contains_time_rows_and_label() {
        for (u in floatArrayOf(-1f, 0f, 1f)) {
            val p = metrics.solvePlacement(u)
            assertTrue(p.timeBaseLeft >= -0.001f)
            assertTrue(p.timeBaseLeft + metrics.timeMax <= p.contentWidth + 0.001f)
            assertTrue(p.labelLeft >= -0.001f)
            assertTrue(p.labelLeft + metrics.labelWidth <= p.contentWidth + 0.001f)
        }
    }

    @Test
    fun three_w_is_motion_range_not_card_width() {
        assertEquals(120f, metrics.trackWidth, 0.001f)
        assertTrue(metrics.inkWidth(-1f) < metrics.trackWidth)
        assertTrue(metrics.inkWidth(1f) < metrics.trackWidth)
    }

    @Test
    fun label_reference_moves_with_endpoint_edges() {
        val left = metrics.solvePlacement(-1f)
        val right = metrics.solvePlacement(1f)
        assertEquals(metrics.labelWidth, left.timeBaseLeft, 0.001f)
        assertEquals(metrics.timeMax, right.labelLeft, 0.001f)
    }
}

class PeriodHeaderAdaptiveFontTest {
    private val font = PeriodHeaderAdaptiveFont

    @Test
    fun larger_card_gives_larger_text() {
        val small = font.compute(
            cardWidthPx = 80f, cardHeightPx = 60f,
            inkWidthPx = 60f, timeMaxWidthPx = 30f, labelWidthPx = 26f,
        )
        val big = font.compute(
            cardWidthPx = 200f, cardHeightPx = 120f,
            inkWidthPx = 120f, timeMaxWidthPx = 60f, labelWidthPx = 56f,
        )
        assertTrue(big.timeSize > small.timeSize)
        assertTrue(big.labelSize > small.labelSize)
    }

    @Test
    fun tiny_card_floors_to_minimum() {
        val v = font.compute(
            cardWidthPx = 12f, cardHeightPx = 12f,
            inkWidthPx = 12f, timeMaxWidthPx = 6f, labelWidthPx = 6f,
        )
        assertEquals(PeriodHeaderAdaptiveFont.TIME_MIN_PX, v.timeSize, 0.001f)
        assertEquals(PeriodHeaderAdaptiveFont.LABEL_MIN_PX, v.labelSize, 0.001f)
    }

    @Test
    fun font_shrinks_when_ink_exceeds_card_width() {
        val v = font.compute(
            cardWidthPx = 50f, cardHeightPx = 60f,
            inkWidthPx = 100f, timeMaxWidthPx = 30f, labelWidthPx = 26f,
        )
        // inkWidth 100 > cardWidth 50 → scale 0.5, 字号应回缩
        val unscaled = font.compute(
            cardWidthPx = 200f, cardHeightPx = 60f,
            inkWidthPx = 60f, timeMaxWidthPx = 30f, labelWidthPx = 26f,
        )
        assertTrue(v.timeSize < unscaled.timeSize)
        assertTrue(v.labelSize < unscaled.labelSize)
    }

    @Test
    fun label_size_is_larger_than_time_size() {
        val v = font.compute(
            cardWidthPx = 80f, cardHeightPx = 60f,
            inkWidthPx = 60f, timeMaxWidthPx = 30f, labelWidthPx = 26f,
        )
        assertTrue(v.labelSize > v.timeSize)
    }
}