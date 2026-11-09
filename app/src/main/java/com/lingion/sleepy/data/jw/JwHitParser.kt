package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

internal class JwHitParser(source: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> {
        for (fragment in source.split(Regex("</?head[^>]*>", RegexOption.IGNORE_CASE))) {
            val document = Jsoup.parse(fragment)
            val table = document.selectFirst(".xfyq_con") ?: document.getElementById("xszp") ?: continue
            val output = mutableListOf<JwCourse>()
            for (row in table.select("tr")) {
                val cells = row.select("td")
                val nodeColumn = when (cells.size) { 8 -> 0; 9 -> 1; else -> continue }
                val nodeText = Regex("第([0-9,，\\-]+)节").find(cells[nodeColumn].text())?.groupValues?.get(1) ?: continue
                val rowNodes = schoolNodeRanges(nodeText)
                for ((dayIndex, cell) in cells.drop(nodeColumn + 1).withIndex()) {
                    if (dayIndex > 6) continue
                    val lines = cell.html().split(Regex("<br\\s*/?>|◇", RegexOption.IGNORE_CASE))
                        .map { Jsoup.parse(it).text().trim() }.filter { it.isNotEmpty() }
                    val starts = lines.indices.filter { '[' in lines[it] && ']' in lines[it] && '周' in lines[it] }
                    var lastName = ""
                    for ((arrangementIndex, begin) in starts.withIndex()) {
                        val preceding = lines.getOrNull(begin - 1).orEmpty()
                        if (preceding.isNotEmpty() && !('[' in preceding && ']' in preceding && '周' in preceding)) lastName = preceding
                        if (lastName.isEmpty()) continue
                        val next = starts.getOrNull(arrangementIndex + 1)
                        val end = if (next != null && next > begin + 1) next - 1 else next ?: lines.size
                        val raw = lines.subList(begin, end).joinToString("")
                        val explicitNodes = Regex("第([0-9,，\\-]+)节").find(raw)?.groupValues?.get(1)
                        val nodes = if (explicitNodes == null) rowNodes else schoolNodeRanges(explicitNodes)
                        val matches = arrangement.findAll(raw).toList()
                        if (matches.isEmpty()) continue
                        for (match in matches) {
                            val teacher = match.groupValues[1].trim()
                            val rawWeeks = match.groupValues[2] + match.groupValues[3]
                            val room = match.groupValues[4].substringBefore("第").trim().removeSurrounding("[", "]")
                            for ((first, last) in nodes) for ((start, finish, parity) in schoolWeekRanges(rawWeeks)) {
                                if ((start..finish).none { parity == 0 || it % 2 == parity % 2 }) continue
                                output += JwCourse(lastName, room, teacher, dayIndex + 1, first, last, start, finish, parity)
                            }
                        }
                    }
                }
            }
            val valid = output.filter { it.day in 1..7 && it.startNode >= 1 && it.endNode >= it.startNode &&
                it.startWeek in 1..366 && it.endWeek in it.startWeek..366 }
            if (valid.isNotEmpty()) return valid
        }
        return emptyList()
    }

    private companion object {
        val arrangement = Regex("([^，\\[\\]]*)\\[([0-9,，\\-单双周()]+)](?:([单双])?周)?([^，]*)")
    }
}
