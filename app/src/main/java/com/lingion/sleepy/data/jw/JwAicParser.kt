package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/** AIC semester grid: one period per row, including empty rows. */
class JwAicParser(source: String) : JwParser(source) {
    private val table by lazy {
        // The semester marker may be in a script after the table. Keep it scoped to
        // that table's source segment; the page may also contain a weekly table.
        val starts = Regex("""<table\b[^>]*\bid\s*=\s*(?:"table"|'table'|table(?=[\s>]))[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(source).toList()
        starts.indices.firstNotNullOfOrNull { index ->
            val fragment = source.substring(starts[index].range.first, starts.getOrNull(index + 1)?.range?.first ?: source.length)
            if (fragment.contains("getScheduleNew.do")) Jsoup.parse(fragment).selectFirst("table#table") else null
        }
    }

    override fun generateCourseList(): List<JwCourse> = buildList {
        val root = table ?: throw IllegalArgumentException("请选择学期课表后再导入")
        val rows = root.select("tr").filter { it.closest("table") == root }
        for ((node, row) in rows.withIndex()) {
            if (node !in 1..30) continue
            val cells = row.children().filter { it.tagName() == "td" }
            for ((day, cell) in cells.withIndex()) {
                if (day !in 1..7) continue
                for (item in cell.select(".courseInfo")) {
                    val name = item.children().firstOrNull()?.text()?.trim().orEmpty()
                    if (name.isEmpty()) continue
                    val weekDetail = item.selectFirst(".weekDetail")
                        ?: throw IllegalArgumentException("请选择学期课表后再导入")
                    val weeks = schoolWeekRanges(weekDetail.text())
                    val teacher = item.select(".teacher").text().trim()
                    val room = item.select(".place").text().trim()
                    for ((from, to, type) in weeks) add(JwCourse(name, room, teacher, day, node, node, from, to, type))
                }
            }
        }
    }.distinct()

    override fun confidence(): Int = if (table != null) 100 else 0
    override fun matchedFeatures(): List<String> = if (table != null) listOf("table/getScheduleNew.do") else emptyList()
}
