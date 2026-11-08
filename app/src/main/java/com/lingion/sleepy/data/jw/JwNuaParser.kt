package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/** NUA has an extra midday period between its printed period numbers 4 and 5. */
class JwNuaParser(source: String) : JwParser(source) {
    private val table by lazy { Jsoup.parse(source).select("table").lastOrNull() }
    override fun generateCourseList(): List<JwCourse> = buildList {
        val root = table ?: return@buildList
        for (row in root.select("tr").filter { it.closest("table") == root }.drop(1)) {
            val cells = row.children().filter { it.tagName() == "td" }
            if (cells.size < 5) continue
            val name = cells[0].text().trim()
            if (name.isEmpty()) continue
            val parts = cells[3].text().trim().split(Regex("\\s+"))
            if (parts.size < 2) continue
            val weeks = schoolWeekRanges(parts[0])
            for (part in parts.drop(1)) {
                val match = Regex("""周([一二三四五六日天])(.+)""").matchEntire(part) ?: continue
                val day = "一二三四五六日".indexOf(match.groupValues[1].replace('天', '日')) + 1
                val nodes = match.groupValues[2].split(',', '，').map { token ->
                    if (token == "中午") 5 else token.toIntOrNull()?.takeIf { it in 1..29 }?.let { if (it >= 5) it + 1 else it }
                }
                if (nodes.any { it == null }) continue
                for ((first, last) in schoolNodeRanges(nodes.filterNotNull().joinToString(","))) {
                    for ((from, to, type) in weeks) add(JwCourse(name, cells[4].text().trim(), cells[1].text().trim(),
                        day, first, last, from, to, type))
                }
            }
        }
    }.distinct()
    override fun confidence(): Int = if (table != null) 80 else 0
    override fun matchedFeatures(): List<String> = if (table != null) listOf("declared-nua-last-table") else emptyList()
}
