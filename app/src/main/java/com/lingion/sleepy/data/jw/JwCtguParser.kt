package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Legacy CTGU GridScore HTML. The separate jwapp JSON collector has different semantics. */
class JwCtguParser(source: String) : JwParser(source) {
    private val table by lazy { Jsoup.parse(source).getElementById("ctl00_MainContentPlaceHolder_GridScore") }

    override fun generateCourseList(): List<JwCourse> = mergeCourseWeeks(buildList {
        val grid = table ?: return@buildList
        val rows = grid.select("tr").filter { it.closest("table") == grid }
        val header = rows.firstOrNull() ?: return@buildList
        // Without a header there is no evidence for the source's row-minus-one period offset.
        if (header.children().none { it.tagName() == "th" } &&
            header.children().drop(1).none { it.text().trim() in weekdayLabels }) return@buildList
        // This legacy format provides positions, not a merged-cell grid. Reject unknown layouts.
        if (rows.drop(1).any { row -> row.children().any { cell ->
                cell.tagName() != "td" || listOf("rowspan", "colspan").any { attr ->
                    cell.hasAttr(attr) && cell.attr(attr).trim().toIntOrNull() != 1
                }
            } }) return@buildList
        for ((rowIndex, row) in rows.withIndex()) {
            if (rowIndex !in 1..15) continue
            val start = rowIndex * 2 - 1
            for ((day, cell) in row.children().filter { it.tagName() == "td" }.withIndex()) {
                if (day !in 1..7 || cell.text().isBlank()) continue
                // Wrapper-only divs are transparent. Each course container is consumed exactly once.
                for (block in courseBlocks(cell) ?: continue) {
                    val fields = block.html().split(fieldSeparator)
                        .map { Jsoup.parseBodyFragment(it).text().trim() }
                    val records = splitRecords(fields) ?: continue
                    for (record in records) {
                        val name = record[0].substringBefore('(').trim()
                        if (name.isEmpty()) continue
                        val parity = parityOf(record[3]) ?: continue
                        val weeks = schoolWeekRanges(record[2])
                        if (weeks.any { it.third != 0 && it.third != parity }) continue
                        for ((from, to, _) in weeks) {
                            add(JwCourse(name, record[1], record.last(), day, start, start + 1, from, to, parity))
                        }
                    }
                }
            }
        }
    })

    override fun confidence(): Int = if (table != null) 100 else 0
    override fun matchedFeatures(): List<String> = if (table != null) listOf("CTGU/GridScore") else emptyList()

    private fun courseBlocks(element: Element): List<Element>? {
        val children = element.children()
        if (element.select("div").none { it != element }) return listOf(element)
        if (element.ownText().isNotBlank() || children.any { it.tagName() != "div" }) return null
        return buildList {
            for (child in children) addAll(courseBlocks(child) ?: return null)
        }
    }

    private fun splitRecords(fields: List<String>): List<List<String>>? {
        if (fields.size < 5) return null
        val starts = mutableListOf(0)
        for (index in 5..fields.size - 5) {
            if (fields[index].isBlank()) continue
            val week = fields[index + 2]
            val weekLike = week.startsWith("第") && week.endsWith("周") ||
                schoolWeekRanges(week).isNotEmpty() || numericWeek.matches(week)
            if (!weekLike) {
                if (fields[index + 3].isNotBlank() && parityOf(fields[index + 3]) != null) return null
                continue
            }
            if (index - starts.last() < 5 || fields[index - 1].isBlank()) return null
            starts += index
        }
        return (starts + fields.size).zipWithNext { from, to -> fields.subList(from, to) }
    }

    private fun parityOf(text: String): Int? = when (text) {
        "", "每周", "全周" -> 0
        "单", "单周" -> 1
        "双", "双周" -> 2
        else -> null
    }

    private companion object {
        val fieldSeparator = Regex("""<br\s*/?>|&nbsp;|\u00a0""", RegexOption.IGNORE_CASE)
        val numericWeek = Regex("""-?\d[\d.,，、;；\-~～\s]*周?""")
        val weekdayLabels = setOf("周一", "周二", "周三", "周四", "周五", "周六", "周日",
            "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
    }
}
