package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/** SEIG's declared grid has a label column and two periods per data row. */
class JwSeigParser(source: String) : JwParser(source) {
    private val tables by lazy { Jsoup.parse(source).select("table").filter { table ->
        listOf("星期一", "星期二", "星期三").all { table.text().contains(it) }
    } }

    override fun generateCourseList(): List<JwCourse> = buildList {
        for (table in tables) {
            // This positional form has no evidence for merged cells; do not shift neighbours by guessing.
            if (table.select("td, th").any { cell ->
                listOf("rowspan", "colspan").any { cell.hasAttr(it) && cell.attr(it) != "1" }
            }) continue
            for ((index, row) in table.select("tr").filter { it.closest("table") == table }.drop(1).withIndex()) {
                val last = (index + 1) * 2
                if (last > 30) continue
                val cells = row.children().filter { it.tagName() == "td" }
                for ((day, cell) in cells.withIndex()) {
                    if (day !in 1..7) continue
                    val text = cell.text().trim()
                    val opening = text.lastIndexOf('(')
                    if (opening <= 0 || !text.endsWith(')')) continue
                    val name = text.take(opening).trim()
                    val fields = text.substring(opening + 1, text.length - 1).trim().split(Regex("\\s+"))
                    if (name.isEmpty() || fields.size < 4) continue
                    val room = Regex("""\[(.*)]""").matchEntire(fields.last())?.groupValues?.get(1) ?: continue
                    val weeks = fields.subList(2, fields.lastIndex).map { token ->
                        Regex("""([0-9]+)周?""").matchEntire(token)?.groupValues?.get(1)?.toIntOrNull()
                    }
                    if (weeks.any { it == null || it !in 1..366 }) continue
                    for ((from, to, type) in JwChengFangParser.weekIntList2WeekBeanList(weeks.filterNotNull().distinct().sorted())) {
                        add(JwCourse(name, room, fields[1], day, last - 1, last, from, to, type))
                    }
                }
            }
        }
    }.distinct()

    override fun confidence(): Int = if (tables.isNotEmpty()) 100 else 0
    override fun matchedFeatures(): List<String> = if (tables.isNotEmpty()) listOf("declared-seig-grid") else emptyList()
}
