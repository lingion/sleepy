package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/** The annual timetable puts one period in each header column after the weekday label. */
internal object JwEcuplSourcePeriods {
    fun resolve(source: String): List<Triple<Int, String, String>> {
        val table = Jsoup.parse(source).selectFirst("table.listTable") ?: return emptyList()
        val row = table.selectFirst("tr") ?: return emptyList()
        val cells = row.select("td").drop(1)
        if (cells.isEmpty()) return emptyList()
        val timePair = Regex("""([0-9]{1,2}):([0-9]{2})\s*-\s*([0-9]{1,2}):([0-9]{2})""")
        return cells.mapIndexed { index, cell ->
            val match = timePair.matchEntire(cell.text().trim()) ?: return emptyList()
            val (startHour, startMinute, endHour, endMinute) = match.destructured.toList().map(String::toInt)
            if (startHour !in 0..23 || endHour !in 0..23 || startMinute !in 0..59 || endMinute !in 0..59 ||
                startHour * 60 + startMinute >= endHour * 60 + endMinute) return emptyList()
            fun clock(hour: Int, minute: Int) = hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')
            Triple(index + 1, clock(startHour, startMinute), clock(endHour, endMinute))
        }
    }
}
