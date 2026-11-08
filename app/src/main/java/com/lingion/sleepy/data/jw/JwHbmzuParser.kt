package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/** HBMZU grid starts at Monday and periods 1–2 without a header offset. */
class JwHbmzuParser(source: String) : JwParser(source) {
    private val table by lazy { Jsoup.parse(source).selectFirst(".list_table.tac") }
    override fun generateCourseList(): List<JwCourse> = buildList {
        val root = table ?: return@buildList
        val rows = root.select("tr").filter { it.closest("table") == root }
        for ((index, row) in rows.withIndex()) {
            val nodes = explicitSchedulePair(index + 1) ?: continue
            for ((column, cell) in row.children().filter { it.tagName() == "td" }.withIndex()) {
                val day = column + 1
                if (day !in 1..7) continue
                for (item in cell.select("p")) {
                    val spans = item.select("span")
                    if (spans.size < 4) continue
                    val name = spans[0].text().trim()
                    if (name.isEmpty()) continue
                    for ((from, to, type) in schoolWeekRanges(spans[1].text())) {
                        add(JwCourse(name, spans[2].text().trim(), spans[3].text().trim(), day,
                            nodes.first, nodes.last, from, to, type))
                    }
                }
            }
        }
    }.distinct()
    override fun confidence(): Int = if (table != null) 100 else 0
    override fun matchedFeatures(): List<String> = if (table != null) listOf("list_table/tac") else emptyList()
}
