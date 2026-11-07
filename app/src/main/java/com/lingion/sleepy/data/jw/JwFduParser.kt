package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/** FDU uses six-column course headers and four-column continuation arrangements. */
class JwFduParser(source: String) : JwParser(source) {
    private val tables by lazy { Jsoup.parse(source).select("table.table.table-hover") }
    override fun generateCourseList(): List<JwCourse> = buildList {
        for (table in tables) {
            var name = ""
            val body = table.selectFirst("tbody") ?: continue
            for (row in body.children().filter { it.tagName() == "tr" }) {
                val cells = row.children().filter { it.tagName() == "td" }
                when (cells.size) {
                    6 -> name = cells[1].text().trim()
                    4 -> Unit
                    else -> { name = ""; continue }
                }
                if (name.isEmpty()) continue
                val fields = cells.takeLast(4)
                val timing = Regex("""星期\s*([一二三四五六日天])\s*[：:]?\s*第\s*([0-9,，\s-]+)\s*节""")
                    .matchEntire(fields[1].text().trim()) ?: continue
                val day = when (timing.groupValues[1]) {
                    "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6; else -> 7
                }
                val weeks = schoolWeekRanges(fields[2].text())
                for ((first, last) in schoolNodeRanges(timing.groupValues[2])) for ((from, to, type) in weeks) {
                    add(JwCourse(name, fields[0].text().trim(), "", day, first, last, from, to, type))
                }
            }
        }
    }.distinct()
    override fun confidence(): Int = if (tables.isNotEmpty()) 100 else 0
    override fun matchedFeatures(): List<String> = if (tables.isNotEmpty()) listOf("declared-fdu-list") else emptyList()
}
