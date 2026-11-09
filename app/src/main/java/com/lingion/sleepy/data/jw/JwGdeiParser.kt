package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/** GDEI row periods are defaults only for that row; each course may override them. */
class JwGdeiParser(source: String) : JwParser(source) {
    private val body by lazy { Jsoup.parse(source).selectFirst("tbody") }
    private fun periods(text: String, rowLabel: Boolean = false): List<Pair<Int, Int>> {
        val pattern = if (rowLabel) Regex("""第(.+?)节(?:[（(][^()（）]*[）)])?""") else Regex("""第(.+?)节""")
        val match = pattern.matchEntire(text.trim()) ?: return emptyList()
        return schoolNodeRanges(match.groupValues[1])
    }
    override fun generateCourseList(): List<JwCourse> = buildList {
        for (row in body?.children().orEmpty().filter { it.tagName() == "tr" }) {
            val cells = row.children().filter { it.tagName() == "td" }
            if (cells.size < 2) continue
            val defaults = periods(cells[0].text(), rowLabel = true)
            for ((day, cell) in cells.withIndex()) {
                if (day !in 1..7) continue
                for (course in cell.children()) {
                    val fields = course.children()
                    if (fields.size < 5) continue
                    val name = fields[0].text().trim()
                    if (name.isEmpty()) continue
                    val time = fields[1].select("span")
                    if (time.isEmpty()) continue
                    val nodes = if (time.size > 1) periods(time[1].text()) else defaults
                    val weeks = schoolWeekRanges(time[0].text().replace(';', ',').replace('；', ','))
                    val room = fields[3].text().trim().replace(Regex("""\([^()]*\)$"""), "").trim()
                    val teacher = fields[4].text().trim()
                    for ((first, last) in nodes) for ((from, to, type) in weeks) {
                        add(JwCourse(name, room, teacher, day, first, last, from, to, type))
                    }
                }
            }
        }
    }.distinct()
    override fun confidence(): Int = if (body != null) 80 else 0
    override fun matchedFeatures(): List<String> = if (body != null) listOf("declared-gdei-tbody") else emptyList()
}
