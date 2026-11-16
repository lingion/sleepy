package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** SDU's ysjddDataTableId is a list of single-period teaching arrangements. */
class JwSduParser(source: String) : JwParser(source) {
    private val document by lazy { Jsoup.parse(source) }
    private val courses by lazy { courseTableCandidates(document, "#ysjddDataTableId", ::parseTable) }
    override fun generateCourseList(): List<JwCourse> = courses

    private fun parseTable(root: Element): List<JwCourse> = buildList {
        for (row in root.select("tr").filter { it.closest("table") == root }) {
            val cells = row.children().filter { it.tagName() == "td" }
            if (cells.size < 12) continue
            val name = cells[2].text().trim()
            val day = cells[9].text().trim().toIntOrNull() ?: continue
            val node = cells[10].text().trim().toIntOrNull() ?: continue
            if (name.isEmpty() || day !in 1..7 || node !in 1..EXPLICIT_SCHEDULE_RESOURCE_BOUND) continue
            for ((from, to, type) in schoolWeekRanges(cells[8].text())) {
                add(JwCourse(name, cells.last().text().trim(), cells[7].text().trim(), day, node, node, from, to, type))
            }
        }
    }.distinct()

    override fun confidence(): Int = if (document.getElementById("ysjddDataTableId") != null || courses.isNotEmpty()) 100 else 0
    override fun matchedFeatures(): List<String> = if (document.getElementById("ysjddDataTableId") != null || courses.isNotEmpty()) listOf("ysjddDataTableId") else emptyList()
}
