package com.lingion.sleepy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PeriodHeaderFormatterTest {
    @Test
    fun range_keeps_multi_period_labels_on_one_line() {
        assertEquals("1-2节", PeriodHeaderFormatter.range(1, 2, "arabic"))
        assertEquals("一-二", PeriodHeaderFormatter.range(1, 2, "chinese"))
    }

    @Test
    fun full_label_replaces_x_with_the_selected_style() {
        assertEquals("第12节", PeriodHeaderFormatter.fullLabel(12, "arabic"))
        assertEquals("第十二节", PeriodHeaderFormatter.fullLabel(12, "chinese"))
        assertEquals("第拾贰节", PeriodHeaderFormatter.fullLabel(12, "financial"))
        assertEquals("第⑫节", PeriodHeaderFormatter.fullLabel(12, "circled"))
        assertEquals("第Ⅻ节", PeriodHeaderFormatter.fullLabel(12, "roman"))
    }

    @Test
    fun single_period_range_does_not_create_a_range() {
        assertEquals("12", PeriodHeaderFormatter.range(12, 12, "arabic"))
    }

    @Test
    fun supported_styles_produce_distinct_labels() {
        val labels = setOf(
            PeriodHeaderFormatter.label(1, "arabic"),
            PeriodHeaderFormatter.label(1, "chinese"),
            PeriodHeaderFormatter.label(1, "financial"),
            PeriodHeaderFormatter.label(1, "circled"),
            PeriodHeaderFormatter.label(1, "roman"),
        )
        assertEquals(5, labels.size)
        assertNotEquals("1", PeriodHeaderFormatter.label(1, "chinese"))
    }
}
