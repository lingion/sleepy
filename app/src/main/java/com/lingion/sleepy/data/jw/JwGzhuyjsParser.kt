package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Declared Guangzhou University postgraduate table with explicit per-course week/period text. */
class JwGzhuyjsParser(source: String) : JwParser(source) {
    private val table by lazy { Jsoup.parse(source).selectFirst("table.tab_0") }

    override fun generateCourseList(): List<JwCourse> {
        val grid = table ?: return emptyList()
        val body = grid.children().firstOrNull { it.tagName() == "tbody" } ?: return emptyList()
        val rows = body.children().filter { it.tagName() == "tr" }
        val occupied = mutableMapOf<Int, MutableSet<Int>>()
        val result = mutableListOf<JwCourse>()
        for ((rowIndex, row) in rows.withIndex()) {
            var column = 0
            for (cell in row.children().filter { it.tagName() == "td" || it.tagName() == "th" }) {
                while (column in occupied[rowIndex].orEmpty()) column++
                val width = positiveSpan(cell, "colspan") ?: return emptyList()
                val height = positiveSpan(cell, "rowspan") ?: return emptyList()
                if (column > 128 - width || height > 128 - rowIndex) return emptyList()
                val columns = column until column + width
                if (columns.any { it in occupied[rowIndex].orEmpty() }) return emptyList()
                for (coveredRow in rowIndex + 1 until rowIndex + height) {
                    occupied.getOrPut(coveredRow) { mutableSetOf() }.addAll(columns)
                }
                // A course spanning weekdays has no unambiguous weekday in this source format.
                if (rowIndex in 4..6 && column in 1..7 && width == 1 && cell.tagName() == "td") {
                    result += coursesInCell(cell, column)
                }
                column += width
            }
        }
        return mergeCourseWeeks(result)
    }

    private fun coursesInCell(cell: Element, day: Int): List<JwCourse> {
        val candidates = cell.select("div").mapNotNull { block ->
            val rows = parseBlock(block, day)
            if (rows.isEmpty()) null else block to rows
        }
        // Valid inner blocks own their courses. An outer wrapper must not parse them a second time.
        return candidates.filter { (block, _) ->
            candidates.none { (other, _) -> other != block && other.parents().contains(block) }
        }.flatMap { it.second }
    }

    private fun parseBlock(block: Element, day: Int): List<JwCourse> {
        val fields = block.html().split(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE))
            .map { Jsoup.parseBodyFragment(it).text().trim() }
        if (fields.size < 6 || fields[0].isBlank()) return emptyList()
        val time = Regex("""^(.+?周(?:[（(][单双]周?[）)])?)\s*(.+?)\s*小节$""")
            .matchEntire(fields[4]) ?: return emptyList()
        val weeks = schoolWeekRanges(time.groupValues[1])
        val nodes = schoolNodeRanges(time.groupValues[2].trim().replace("到", "-"))
        return weeks.flatMap { (startWeek, endWeek, parity) ->
            nodes.map { (startNode, endNode) ->
                JwCourse(fields[0], fields[5], fields[3], day, startNode, endNode, startWeek, endWeek, parity)
            }
        }
    }

    private fun positiveSpan(cell: Element, attribute: String): Int? {
        if (!cell.hasAttr(attribute)) return 1
        val text = cell.attr(attribute).trim()
        if (!Regex("[0-9]+").matches(text)) return null
        return text.toIntOrNull()?.takeIf { it in 1..128 }
    }

    override fun confidence(): Int = if (table != null) 100 else 0
    override fun matchedFeatures(): List<String> = if (table != null) listOf("GZHU-post/tab_0") else emptyList()
}
