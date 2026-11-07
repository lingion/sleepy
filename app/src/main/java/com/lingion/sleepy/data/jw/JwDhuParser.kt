package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Declared legacy DHU HTML. The row label, not the number of preceding rows, supplies the period. */
class JwDhuParser(source: String) : JwParser(source) {
    private val table by lazy { Jsoup.parse(source).selectFirst("table") }

    override fun generateCourseList(): List<JwCourse> {
        val sourceTable = table ?: return emptyList()
        val rows = legacyDhuEcjtuRows(sourceTable) ?: return emptyList()
        return mergeCourseWeeks(buildList {
            for (row in rows) {
                val label = row.firstOrNull { it.column == 0 } ?: continue
                val first = period(label.element.text()) ?: continue
                for (cell in row) {
                    if (cell.column !in 1..7 || cell.columnSpan != 1) continue
                    val last = first + cell.rowSpan - 1
                    if (last > 30) continue
                    val fields = cell.element.text().trim().split(Regex("\\s+"))
                    for (start in fields.indices step 4) {
                        val name = fields[start].takeIf { it.isNotBlank() } ?: continue
                        val weeks = fields.getOrNull(start + 1)?.let { schoolWeekRanges(it) } ?: continue
                        for ((from, to, parity) in weeks) {
                            add(JwCourse(name, fields.getOrElse(start + 3) { "" }, fields.getOrElse(start + 2) { "" },
                                cell.column, first, last, from, to, parity))
                        }
                    }
                }
            }
        })
    }

    private fun period(raw: String): Int? {
        val text = raw.trim().removeSuffix("节").trim()
        return when (text) {
            "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5
            "六" -> 6; "七" -> 7; "八" -> 8; "九" -> 9; "十" -> 10
            "十一" -> 11; "十二" -> 12; "十三" -> 13; "十四" -> 14; "十五" -> 15
            "十六" -> 16; "十七" -> 17; "十八" -> 18; "十九" -> 19; "二十" -> 20
            else -> null
        }
    }

    override fun confidence(): Int = if (table?.select("tr")?.any { row ->
        row.closest("table") == table && row.children().firstOrNull()?.let { period(it.text()) } != null
    } == true) 80 else 0

    override fun matchedFeatures(): List<String> = if (confidence() > 0) listOf("DHU/chinese-period-grid") else emptyList()
}

/** Origin cells only. A row-spanning course is parsed once, while its occupied columns remain reserved. */
internal data class DhuEcjtuCell(val element: Element, val column: Int, val rowSpan: Int, val columnSpan: Int)

internal fun legacyDhuEcjtuRows(table: Element): List<List<DhuEcjtuCell>>? {
    val occupiedThrough = IntArray(8) { -1 }
    val result = mutableListOf<List<DhuEcjtuCell>>()
    val rows = table.select("tr").filter { it.closest("table") == table }
    for ((rowIndex, row) in rows.withIndex()) {
        var column = 0
        val cells = mutableListOf<DhuEcjtuCell>()
        for (element in row.children().filter { it.tagName() == "td" || it.tagName() == "th" }) {
            while (column < 8 && occupiedThrough[column] >= rowIndex) column++
            fun span(name: String, limit: Int): Int? {
                if (!element.hasAttr(name)) return 1
                val text = element.attr(name).trim()
                if (!Regex("[0-9]+").matches(text)) return null
                return text.toIntOrNull()?.takeIf { it in 1..limit }
            }
            val width = span("colspan", 8) ?: return null
            val height = span("rowspan", 366) ?: return null
            if (column + width > 8 || (column until column + width).any { occupiedThrough[it] >= rowIndex }) return null
            cells += DhuEcjtuCell(element, column, height, width)
            for (position in column until column + width) occupiedThrough[position] = rowIndex + height - 1
            column += width
        }
        // Incomplete rows have no reliable seven-day layout. Do not move their courses into guessed days.
        if (occupiedThrough.all { it >= rowIndex }) result += cells
    }
    return result
}
