package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/** Declared CNU curriculum-item layout; eight columns have one gutter, nine have two. */
class JwCnuParser(source: String) : JwParser(source) {
    private val tables by lazy { Jsoup.parse(source).select("tbody").filter { it.selectFirst(".curriculum-item") != null } }

    override fun generateCourseList(): List<JwCourse> = mergeCourseWeeks(buildList {
        for (table in tables) {
            for (row in table.children().filter { it.tagName() == "tr" }) {
                val cells = row.children().filter { it.tagName() == "td" }
                val gutters = when (cells.size) { 8 -> 1; 9 -> 2; else -> continue }
                for ((index, cell) in cells.withIndex()) {
                    val day = index - gutters + 1
                    if (day !in 1..7) continue
                    for (item in cell.select(".curriculum-item").filter { item ->
                        item.parents().takeWhile { it != cell }.none { it.hasClass("curriculum-item") }
                    }) {
                        // Scope all fields to this item; the APK's whole-cell selection mixes multiple lessons.
                        val fields = item.select("div")
                        if (fields.size != 5) continue
                        val name = item.select("span").text().substringBefore(" 查看更多").trim()
                        if (name.isEmpty()) continue
                        val timing = Regex("""(.+)周\[([^\[\]]+)节]""").matchEntire(fields[3].text().trim()) ?: continue
                        val weeks = schoolWeekRanges(timing.groupValues[1])
                        val nodes = schoolNodeRanges(timing.groupValues[2])
                        for ((from, to, parity) in weeks) for ((first, last) in nodes) {
                            add(JwCourse(name, fields[4].text().trim(), fields[2].text().trim(), day, first, last, from, to, parity))
                        }
                    }
                }
            }
        }
    })

    override fun confidence(): Int = if (tables.isNotEmpty()) 100 else 0
    override fun matchedFeatures(): List<String> = if (tables.isNotEmpty()) listOf("CNU/curriculum-item") else emptyList()
}
