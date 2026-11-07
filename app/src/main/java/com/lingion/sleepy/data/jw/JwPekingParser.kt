package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/** PKU datagrid schedules; each br block carries its own week, day and node coordinates. */
class JwPekingParser(source: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> {
        val result = mutableListOf<JwCourse>()
        val table = Jsoup.parse(source).selectFirst("table[class=datagrid]") ?: return result
        for (row in table.selectFirst("tbody")?.getElementsByTag("tr").orEmpty()) {
            val cells = row.getElementsByTag("td")
            if (cells.size < 11 || '未' in cells[8].text()) continue
            for (block in cells[7].html().split(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE))) {
                val fields = Jsoup.parseBodyFragment(block).text().trim().split(Regex("\\s+"))
                if (fields.size < 2) continue
                val rawWeeks = fields[0].removeSuffix("周")
                val parsedWeeks = rawWeeks.split(',').mapNotNull { part ->
                    val m = Regex("([0-9]+)(?:~([0-9]+))?").matchEntire(part) ?: return@mapNotNull null
                    val first = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                    val last = m.groupValues[2].ifBlank { m.groupValues[1] }.toIntOrNull() ?: return@mapNotNull null
                    (first to last).takeIf { first > 0 && last >= first }
                }
                val timing = fields[1]
                val m = Regex("周([一二三四五六日天])([0-9]+)(?:~([0-9]+))?节(?:[（(]([^）)]*)[）)])?").matchEntire(timing) ?: continue
                val day = "一二三四五六日".indexOf(m.groupValues[1].replace('天', '日')) + 1
                val firstNode = m.groupValues[2].toIntOrNull() ?: continue
                val lastNode = m.groupValues[3].ifBlank { m.groupValues[2] }.toIntOrNull() ?: continue
                val bracket = m.groupValues[4]
                val parity = when { "单" in bracket -> 1; "双" in bracket -> 2; else -> 0 }
                val room = fields.getOrNull(2) ?: bracket.takeUnless { it in setOf("单", "双", "单周", "双周") }.orEmpty()
                for ((first, last) in parsedWeeks) {
                    val c = JwCourse(cells[0].text().trim(), room, cells[4].text().trim(), day,
                        firstNode, lastNode, first, last, parity)
                    if (JwUndergradBHtmlParser.valid(c)) result += c
                }
            }
        }
        return result
    }

    override fun confidence(): Int = runCatching {
        when {
            Jsoup.parse(source).selectFirst("table[class=datagrid]") != null -> 100
            "elective.pku" in source -> 90
            else -> 0
        }
    }.getOrDefault(0)

    override fun matchedFeatures(): List<String> = buildList {
        if ("datagrid" in source) add("class=datagrid")
        if ("elective.pku" in source) add("URL=elective.pku")
    }
}
