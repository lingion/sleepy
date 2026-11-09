package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/** Declared legacy GLIET grid: row index 5 is period 11, index 6 is period 13. */
class JwGlietParser(source: String) : JwParser(source) {
    private val tables by lazy { Jsoup.parse(source).select("table[role=presentation]").filter { it.text().contains("第1、2节") } }
    override fun generateCourseList(): List<JwCourse> = buildList {
        for (table in tables) {
            val rows = table.select("tr").filter { it.closest("table") == table }
            for ((rowIndex, row) in rows.withIndex()) {
                val first = explicitSchedulePair(rowIndex + 1)?.first ?: continue
                val last = if (first > 10) first else first + 1
                for ((day, cell) in row.children().filter { it.tagName() == "td" }.withIndex()) {
                    if (day !in 1..7) continue
                    // Each outer course container is flattened once; descendant divs must not be parsed twice.
                    for (block in cell.select("div").filter { div ->
                        div.parents().takeWhile { it != cell }.none { it.tagName() == "div" }
                    }) {
                        val lines = block.html().split(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE))
                            .map { Jsoup.parseBodyFragment(it).text().trim() }
                        val starts = lines.indices.filter { lines[it].startsWith("课号:") }
                        for ((position, start) in starts.withIndex()) {
                            val end = starts.getOrNull(position + 1) ?: lines.size
                            val fields = lines.subList(start, end)
                            val name = fields.getOrNull(1)?.takeIf { it.isNotBlank() } ?: continue
                            val teacher = fields[0].replaceFirst(Regex("""^课号:[0-9]+"""), "").trim()
                            for (line in fields.drop(2)) {
                                val match = Regex("""^\(([^()]*)\)(.*)$""").matchEntire(line) ?: continue
                                for ((from, to, parity) in schoolWeekRanges(match.groupValues[1])) {
                                    add(JwCourse(name, match.groupValues[2].trim(), teacher, day, first, last, from, to, parity))
                                }
                            }
                        }
                    }
                }
            }
        }
    }.distinct()
    override fun confidence(): Int = if (tables.isNotEmpty()) 100 else 0
    override fun matchedFeatures(): List<String> = if (tables.isNotEmpty()) listOf("GLIET/presentation-grid") else emptyList()
}
