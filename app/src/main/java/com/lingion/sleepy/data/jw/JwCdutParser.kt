package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Declared legacy CDUT weekly grid; its sixth daily column has no verified course meaning. */
class JwCdutParser(source: String) : JwParser(source) {
    private val document by lazy { Jsoup.parse(source) }
    private val table by lazy { document.selectFirst(".tab1") }

    override fun generateCourseList(): List<JwCourse> {
        val grid = table ?: return emptyList()
        val rows = grid.select("tr").filter { it.closest(".tab1") == grid }
        // A rowspan changes the next week's coordinates. The source has no rule for this layout.
        if (rows.any { row -> row.children().any { cell -> positiveSpan(cell, "rowspan") != 1 } }) return emptyList()
        val metadata = courseMetadata()
        return mergeCourseWeeks(rows.flatMap { row -> parseRow(row, metadata) })
    }

    private fun parseRow(row: Element, metadata: Map<String, CourseMetadata>): List<JwCourse> {
        val cells = row.children().filter { it.tagName() == "td" || it.tagName() == "th" }
        val label = cells.firstOrNull()?.takeIf { it.hasClass("td1") } ?: return emptyList()
        if (positiveSpan(label, "colspan") != 1) return emptyList()
        val week = Regex("""(?:第\s*)?([0-9]+)\s*周?""").matchEntire(label.text().trim())
            ?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..366 } ?: return emptyList()
        val courses = mutableListOf<JwCourse>()
        var column = 0
        for (cell in cells.drop(1)) {
            val span = positiveSpan(cell, "colspan") ?: return emptyList()
            if (span > 84 - column) return emptyList()
            val startColumn = column
            column += span
            // The source reads direct text only. A room element alone is an empty timetable cell.
            val code = cell.ownText().trim()
            if (code.isEmpty()) continue
            if (startColumn / 12 != (column - 1) / 12) return emptyList()
            val startInDay = startColumn % 12
            val endInDay = (column - 1) % 12
            // WakeUp maps both the fifth and sixth columns to period five. Do not guess a
            // course spanning that unresolved column; subsequent cells retain their actual positions.
            if (5 in startInDay..endInDay) continue
            val startNode = if (startInDay < 5) startInDay + 1 else startInDay
            val endNode = if (endInDay < 5) endInDay + 1 else endInDay
            val info = metadata[code]
            courses += JwCourse(
                name = info?.name ?: code,
                room = cell.selectFirst(".roomcss")?.text()?.trim().orEmpty(),
                teacher = info?.teacher.orEmpty(),
                day = startColumn / 12 + 1,
                startNode = startNode,
                endNode = endNode,
                startWeek = week,
                endWeek = week,
                credit = info?.credit ?: 0f,
            )
        }
        return courses
    }

    private data class CourseMetadata(val name: String, val teacher: String, val credit: Float)

    private fun courseMetadata(): Map<String, CourseMetadata> {
        val metadataTable = document.selectFirst(".tab2") ?: return emptyMap()
        val result = mutableMapOf<String, CourseMetadata>()
        val conflictingCodes = mutableSetOf<String>()
        for (cell in metadataTable.select("td")) {
            if (cell.closest(".tab2") != metadataTable) continue
            val title = cell.selectFirst("b")?.text() ?: continue
            val header = Regex("""\(([^()]+)\)\s*(.*?)\(ID""").find(title) ?: continue
            val baseCode = header.groupValues[1].trim()
            val name = header.groupValues[2].trim().takeIf { it.isNotEmpty() } ?: continue
            val credit = Regex("""学分\[([^]]*)]""").find(cell.text())?.groupValues?.get(1)
                ?.trim()?.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: 0f
            val details = cell.html().substringAfter("</b>", "")
                .split(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE))
            for (detail in details) {
                val text = Jsoup.parseBodyFragment(detail).text().trim()
                val group = Regex("""-\s*([^()]+)\(""").find(text)?.groupValues?.get(1)?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: continue
                val code = baseCode + group.first() + group.last()
                val teacher = Regex("""师\[([^]]*)]""").find(text)?.groupValues?.get(1)?.trim().orEmpty()
                val value = CourseMetadata(name, teacher, credit)
                if (code in conflictingCodes) continue
                val prior = result[code]
                if (prior != null && prior != value) {
                    result.remove(code)
                    conflictingCodes += code
                } else result[code] = value
            }
        }
        return result
    }

    private fun positiveSpan(cell: Element, attribute: String): Int? {
        if (!cell.hasAttr(attribute)) return 1
        val text = cell.attr(attribute).trim()
        if (!Regex("[0-9]+").matches(text)) return null
        return text.toIntOrNull()?.takeIf { it in 1..84 }
    }

    override fun confidence(): Int = if (table != null) 100 else 0
    override fun matchedFeatures(): List<String> = if (table != null) listOf("CDUT/weekly-tab1") else emptyList()
}
