package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/** ZTVTIT uses English weekday attributes and an explicit single-period lesson attribute. */
class JwZtvtitParser(source: String) : JwParser(source) {
    private val tables by lazy { Jsoup.parse(source).select("table.scheduleTable") }
    override fun generateCourseList(): List<JwCourse> = buildList {
        for (table in tables) for (info in table.select(".courseInfo")) {
            val owner = info.closest("[week]") ?: continue
            val day = listOf("", "mon", "tue", "wed", "thu", "fri", "sat", "sun").indexOf(owner.attr("week"))
            val node = owner.selectFirst("[lesson]")?.attr("lesson")?.toIntOrNull() ?: continue
            val name = info.select("span:not([class])").joinToString(" ") { it.text() }.trim()
            if (name.isEmpty() || day !in 1..7 || node !in 1..30) continue
            val room = info.selectFirst(".place")?.text()?.trim().orEmpty()
            val teacher = info.selectFirst(".teacher")?.text()?.trim().orEmpty()
            val weeks = info.selectFirst(".WeekDetail")?.text() ?: continue
            for ((from, to, type) in schoolWeekRanges(weeks)) {
                add(JwCourse(name, room, teacher, day, node, node, from, to, type))
            }
        }
    }.distinct()
    override fun confidence(): Int = if (tables.isNotEmpty()) 100 else 0
    override fun matchedFeatures(): List<String> = if (tables.isNotEmpty()) listOf("scheduleTable", "week-lesson") else emptyList()
}
