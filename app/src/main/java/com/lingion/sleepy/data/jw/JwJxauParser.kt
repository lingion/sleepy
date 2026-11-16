package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class JwJxauParser(source: String) : JwParser(source) {
    private val document by lazy { Jsoup.parse(source) }
    private val courses by lazy { courseTableCandidates(document, "#KebiaoTable1", ::parseTable) }
    override fun generateCourseList(): List<JwCourse> = courses

    private fun parseTable(grid: Element): List<JwCourse> = mergeCourseWeeks(buildList {
        val rows = grid.select("tr").filter { it.closest("table") == grid }.filter { weekday(it) != null }
        if (rows.any { row -> row.children().any { cell ->
                listOf("rowspan", "colspan").any { cell.hasAttr(it) && cell.attr(it).toIntOrNull() != 1 }
            } }) return@buildList
        for (row in rows) {
            val day = weekday(row) ?: continue
            var node = 1
            for ((index, cell) in row.children().filter { it.tagName() == "td" }.drop(1).withIndex()) {
                if (index == 4 || index == 6) continue
                if (node >= EXPLICIT_SCHEDULE_RESOURCE_BOUND) break
                for (lesson in cell.select("dl")) {
                    val name = lesson.selectFirst("a")?.text()?.trim().orEmpty()
                    val details = lesson.select("dd")
                    if (name.isEmpty() || details.size < 2) continue
                    val raw = details[1].text().trim().replace("(周)", "").replace("（周）", "")
                    val weeks = schoolWeekRanges(raw, trailingParityAppliesToAll = true)
                    val teacher = details[0].text().trim()
                    val room = lesson.selectFirst("b")?.text()?.trim().orEmpty()
                    for ((first, last, parity) in weeks) {
                        add(JwCourse(name, room, teacher, day, node, node + 1, first, last, parity))
                    }
                }
                node += 2
            }
        }
    })

    private fun weekday(row: Element): Int? {
        val label = (row.selectFirst(".left1") ?: row.children().firstOrNull())?.text()?.trim()
            ?.removePrefix("星期")?.removePrefix("周") ?: return null
        return when (label) { "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6; "日", "天" -> 7; else -> null }
    }

    override fun confidence(): Int = if (document.getElementById("KebiaoTable1") != null || courses.isNotEmpty()) 100 else 0
    override fun matchedFeatures(): List<String> = if (document.getElementById("KebiaoTable1") != null || courses.isNotEmpty()) listOf("JXAU/KebiaoTable1") else emptyList()
}
