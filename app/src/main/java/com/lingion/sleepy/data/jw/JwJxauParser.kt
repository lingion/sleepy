package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

class JwJxauParser(source: String) : JwParser(source) {
    private val table by lazy { Jsoup.parse(source).getElementById("KebiaoTable1") }

    override fun generateCourseList(): List<JwCourse> = mergeCourseWeeks(buildList {
        val grid = table ?: return@buildList
        val rows = grid.select("tr").filter { it.closest("table") == grid }.drop(2).take(7)
        if (rows.any { row -> row.children().any { cell ->
                listOf("rowspan", "colspan").any { cell.hasAttr(it) && cell.attr(it).toIntOrNull() != 1 }
            } }) return@buildList
        for (row in rows) {
            val label = row.selectFirst(".left1")?.text()?.trim().orEmpty().removePrefix("星期")
            val day = when (label) { "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6; "日", "天" -> 7; else -> continue }
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

    override fun confidence(): Int = if (table != null) 100 else 0
    override fun matchedFeatures(): List<String> = if (table != null) listOf("JXAU/KebiaoTable1") else emptyList()
}
