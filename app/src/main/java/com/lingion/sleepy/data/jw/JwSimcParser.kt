package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/** SIMC's TaskActivity variant has its own week origin, section map and extended sessions. */
class JwSimcParser(source: String) : JwParser(source) {
    private data class Identity(
        val name: String, val teacher: String, val room: String, val day: Int,
        val credit: Float, val startTime: String = "", val endTime: String = "",
    )

    private val periodTimes = listOf(
        "08:30" to "09:15", "09:15" to "10:00", "10:15" to "11:00", "11:00" to "11:45",
        "13:00" to "13:45", "13:45" to "14:30", "14:45" to "15:30", "15:30" to "16:15",
        "16:15" to "17:00", "17:00" to "17:45",
    )

    private val marshal = Regex("""\btable\s*\.\s*marshalTable\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)\s*;""")
        .find(source)?.groupValues?.drop(1)?.map { it.toIntOrNull() }

    private fun weeks(bitmap: String): String {
        val values = marshal ?: return ""
        val origin = values[0] ?: return ""
        val from = values[1] ?: return ""
        val to = values[2] ?: return ""
        if (bitmap.isEmpty() || bitmap.any { it != '0' && it != '1' } || origin <= 0 || from <= 0 || to < from) return ""
        val first = origin.toLong() + from - 2
        val last = origin.toLong() + to - 2
        if (first < 0 || last >= bitmap.length.toLong() * 2) return ""
        // The original helper is repeat(2), not drop(2). Prefix index zero for the shared decoder.
        val repeated = bitmap.repeat(2)
        return buildString {
            append('0')
            for (week in 1..to) append(if (week < from) '0' else repeated[origin + week - 2])
        }
    }

    override fun generateCourseList(): List<JwCourse> {
        if (marshal == null) return emptyList()
        val creditRows = Jsoup.parse(source).selectFirst(".grid > table")?.select("tr")?.drop(1).orEmpty()
            .mapNotNull { row ->
                val cells = row.select("td")
                if (cells.size < 4) return@mapNotNull null
                val credit = cells[3].text().toFloatOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return@mapNotNull null
                val code = cells[0].text().trim()
                val name = cells[2].text().trim()
                if (name.isEmpty()) null else (name + if (code.isEmpty()) "" else "($code)") to credit
            }
        val schedules = linkedMapOf<Identity, java.util.SortedMap<Int, java.util.SortedSet<Int>>>()
        // SIMC declares fourteen raw slots even when unitCount is supplied by an external script.
        for (activity in JwClassicEamsParser(source, ::weeks).decodedActivities(defaultUnitCount = 14)) {
            val credit = creditRows.lastOrNull { activity.name.startsWith(it.first) }?.second ?: 0f
            for ((rawDay, indices) in activity.indices.groupBy { it.first }.toSortedMap()) {
                val nodes = indices.map { (_, raw) -> if (raw == 13) 10 else if (raw < 9) raw + 1 else raw + 2 }.toSortedSet()
                val special = when (nodes.singleOrNull()) {
                    11 -> Triple(1..5, "07:00", "13:30")
                    12 -> Triple(6..10, "14:00", "20:30")
                    13 -> Triple(1..8, "08:15", "15:30")
                    14 -> Triple(9..10, "16:00", "22:00")
                    else -> null
                }
                val key = Identity(activity.name, activity.teacher, activity.room, rawDay + 1, credit,
                    special?.second.orEmpty(), special?.third.orEmpty())
                val weeks = schedules.getOrPut(key) { sortedMapOf() }
                for (week in activity.weeks) weeks.getOrPut(week) { sortedSetOf() }.addAll(special?.first?.toList() ?: nodes)
            }
        }
        return buildList {
            for ((key, weeks) in schedules) for ((week, nodes) in weeks) {
                val ranges = mutableListOf<Pair<Int, Int>>()
                for (node in nodes) {
                    val previous = ranges.lastOrNull()
                    if (previous != null && previous.second != 4 && node == previous.second + 1)
                        ranges[ranges.lastIndex] = previous.first to node
                    else ranges += node to node
                }
                for ((first, last) in ranges) {
                    val clock = if (key.startTime.isNotEmpty() && key.endTime.isNotEmpty()) {
                        key.startTime to key.endTime
                    } else if (first in 1..periodTimes.size && last in 1..periodTimes.size) {
                        periodTimes[first - 1].first to periodTimes[last - 1].second
                    } else "" to ""
                    add(JwCourse(
                    name = key.name.replace(Regex("""\(([0-9]{4})\)\((.*?)\)"""), ""),
                    teacher = key.teacher, room = key.room, day = key.day,
                    startNode = first, endNode = last, startWeek = week, endWeek = week,
                    // Preserve SIMC's timetable through Sleepy's existing per-course clock fields.
                    startTime = clock.first,
                    endTime = clock.second,
                    credit = key.credit,
                ))
                }
            }
        }
    }

    override fun confidence(): Int = if (marshal != null && source.contains("TaskActivity")) 100 else 0
    override fun matchedFeatures(): List<String> = if (marshal != null)
        listOf("declared-simc", "table.marshalTable(origin,from,to)") else emptyList()
}
