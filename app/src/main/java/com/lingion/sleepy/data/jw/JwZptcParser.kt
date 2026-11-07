package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/** Declared ZPTC layout: the x coordinate selects a weekday header, not the weekday number. */
class JwZptcParser(source: String) : JwParser(source) {
    private val tables by lazy { Jsoup.parse(source).select("#kb") }

    override fun generateCourseList(): List<JwCourse> = mergeCourseWeeks(buildList {
        for (table in tables) {
            val header = table.select("tr").firstOrNull { it.closest("#kb") == table } ?: continue
            val headings = header.children()
            for (block in table.select(".pbtd")) {
                if (block.closest("#kb") != table || block.select(".pbtd").any { it != block }) continue
                val coordinate = Regex("""[^x]*x([0-9]+)_[^\s]+""").matchEntire(block.id()) ?: continue
                val column = coordinate.groupValues[1].toIntOrNull() ?: continue
                val heading = headings.getOrNull(column)?.takeIf { it.tagName() == "th" } ?: continue
                val day = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日").indexOf(heading.text().trim())
                if (day !in 1..7) continue

                val fields = block.text().trim().split('/', limit = 3).map(String::trim)
                if (fields.size != 3 || fields[0].isEmpty()) continue
                val parity = when (fields[1]) { "每周" -> 0; "单周" -> 1; "双周" -> 2; else -> continue }
                val details = fields[2]
                val node = Regex("""第\s*([0-9]+)\s*-\s*([0-9]+)\s*节?""")
                    .matchEntire(details.takeWhile { it != '[' && it != '【' }.trim()) ?: continue
                val start = node.groupValues[1].toIntOrNull() ?: continue
                val end = node.groupValues[2].toIntOrNull() ?: continue
                // The source declares adjacent pairs. A contradictory explicit end must not be ignored.
                if (start !in 1..29 || end != start + 1) continue
                val bracketFields = Regex("""\[([^\[\]]*)]""").findAll(details).toList()
                val weekText = bracketFields.getOrNull(1)?.groupValues?.get(1) ?: continue
                val metadata = Regex("""【([^【】]*)】""").findAll(details).toList()
                if (metadata.size != 2) continue
                val teacher = metadata[0].groupValues[1].trim()
                val room = metadata[1].groupValues[1].trim()
                val weeks = schoolWeekRanges(weekText)
                if (weeks.any { it.third != 0 && it.third != parity }) continue
                for ((from, to, _) in weeks) {
                    add(JwCourse(fields[0], room, teacher, day, start, end, from, to, parity))
                }
            }
        }
    })

    override fun confidence(): Int = if (tables.any { it.select(".pbtd").isNotEmpty() }) 100 else 0
    override fun matchedFeatures(): List<String> = if (confidence() > 0) listOf("ZPTC/kb-pbtd-header") else emptyList()
}
