package com.lingion.sleepy.util

/** Pure geometry shared by the Compose timetable and the Canvas-backed week-grid widget. */
object GridSeparatorGeometry {
    data class Span(val start: Float, val end: Float)

    /** Returns the portions of [start, end] left after excluding the supplied vertical gaps. */
    fun verticalSegments(start: Float, end: Float, excluded: List<Span>): List<Span> {
        if (!start.isFinite() || !end.isFinite() || end <= start) return emptyList()

        val result = mutableListOf<Span>()
        var cursor = start
        for (span in excluded.sortedBy { it.start }) {
            if (!span.start.isFinite() || !span.end.isFinite()) continue
            val gapStart = span.start.coerceIn(start, end)
            val gapEnd = span.end.coerceIn(start, end)
            if (gapEnd <= gapStart || gapEnd <= cursor) continue
            if (gapStart > cursor) result += Span(cursor, gapStart)
            cursor = maxOf(cursor, gapEnd)
        }
        if (cursor < end) result += Span(cursor, end)
        return result
    }
}
