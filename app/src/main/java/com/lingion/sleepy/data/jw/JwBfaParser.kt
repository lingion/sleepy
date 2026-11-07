package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/** BFA studentRecordTable has six explicit fields and may list separate period ranges. */
class JwBfaParser(source: String) : JwParser(source) {
    private val body by lazy { Jsoup.parse(source).selectFirst("#studentRecordTable tbody") }
    override fun generateCourseList(): List<JwCourse> = buildList {
        val root = body ?: return@buildList
        for (row in root.children().filter { it.tagName() == "tr" }) {
            val cells = row.children().filter { it.tagName() == "td" }
            if (cells.size != 6) continue
            val name = cells[0].text().trim()
            val day = cells[3].text().trim().toIntOrNull() ?: continue
            if (name.isEmpty() || day !in 1..7) continue
            val room = cells[5].text().trim().takeUnless { it == "-" }.orEmpty()
            val weeks = schoolWeekRanges(cells[2].text())
            for ((first, last) in schoolNodeRanges(cells[4].text())) for ((from, to, type) in weeks) {
                add(JwCourse(name, room, cells[1].text().trim(), day, first, last, from, to, type))
            }
        }
    }.distinct()
    override fun confidence(): Int = if (body != null) 100 else 0
    override fun matchedFeatures(): List<String> = if (body != null) listOf("studentRecordTable") else emptyList()
}
