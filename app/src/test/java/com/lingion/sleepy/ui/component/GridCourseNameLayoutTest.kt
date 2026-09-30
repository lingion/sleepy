package com.lingion.sleepy.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Width sweep for the Cards grid text box.
 * The text box is the card width minus the scaled outer and inner padding.
 */
class GridCourseNameLayoutTest {

    @Test
    fun width_sweep_keeps_three_character_threshold_monotonic() {
        val characterWidthPx = 29f
        val widthsPx = (58..145 step 1).map { it.toFloat() }

        val charsPerLine = widthsPx.map { width ->
            (width / characterWidthPx).toInt().coerceAtLeast(1)
        }

        assertEquals(2, charsPerLine.first())
        assertTrue(charsPerLine[29] >= 3) // 87px: three characters fit
        assertTrue(charsPerLine.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun two_character_line_is_centered_inside_text_box() {
        val textBoxWidthPx = 72f
        val twoCharacterInkWidthPx = 58f

        assertEquals(7f, (textBoxWidthPx - twoCharacterInkWidthPx) / 2f, 0.001f)
    }
}
