package com.lingion.sleepy.widget
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WidgetCompactWindowOrderTest {
    private val sun = DayData(LocalDate.of(2026, 10, 18), 7, emptyList(), "")
    private val mon = DayData(LocalDate.of(2026, 10, 19), 1, emptyList(), "")
    private val tue = DayData(LocalDate.of(2026, 10, 20), 2, emptyList(), "")
    private val allVisible = setOf(1, 2, 3, 4, 5, 6, 7)

    @Test
    fun `keepDateOrder preserves window order across the week boundary`() {
        val out = WidgetBitmapRenderers.visibleDaysForRender(
            listOf(sun, mon, tue), allVisible, keepDateOrder = true
        )
        assertEquals(listOf(sun.date, mon.date, tue.date), out.map { it.date })
    }

    @Test
    fun `regular tier still sorts columns by dayOfWeek`() {
        val out = WidgetBitmapRenderers.visibleDaysForRender(
            listOf(sun, mon, tue), allVisible, keepDateOrder = false
        )
        assertEquals(listOf(mon.date, tue.date, sun.date), out.map { it.date })
    }

    @Test
    fun `visible-day filter still applies in date order mode`() {
        val out = WidgetBitmapRenderers.visibleDaysForRender(
            listOf(sun, mon, tue), setOf(1, 2, 3, 4, 5), keepDateOrder = true
        )
        assertEquals(listOf(mon.date, tue.date), out.map { it.date })
    }
}
