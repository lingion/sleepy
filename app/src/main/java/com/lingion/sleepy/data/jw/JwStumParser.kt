package com.lingion.sleepy.data.jw

import org.json.JSONArray

/** STUM collects individual weeks. Weekday values themselves contain JSON array strings. */
class JwStumParser(source: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> = buildList {
        val pages = JSONArray(source)
        val days = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        for (pageIndex in 0 until pages.length()) {
            val data = pages.optJSONObject(pageIndex)?.optJSONObject("data") ?: continue
            val week = data.opt("week")?.toString()?.toIntOrNull()?.takeIf { it in 1..366 } ?: continue
            val rows = data.optJSONArray("data") ?: continue
            for (rowIndex in 0 until rows.length()) {
                val row = rows.optJSONObject(rowIndex) ?: continue
                val node = when (val name = row.optString("name")) {
                    "A" -> 11
                    "B" -> 12
                    "C" -> 13
                    else -> name.toIntOrNull()
                }?.takeIf { it in 1..30 } ?: continue
                val start = row.optString("start_time")
                val end = row.optString("end_time")
                val hasTime = JwCourse.isValidTimeRange(start, end)
                for ((index, day) in days.withIndex()) {
                    val encoded = row.opt(day) as? String ?: continue
                    val items = runCatching { JSONArray(encoded) }.getOrNull() ?: continue
                    for (itemIndex in 0 until items.length()) {
                        val item = items.optJSONObject(itemIndex) ?: continue
                        val name = (item.opt("f2") as? String)?.trim().orEmpty()
                        if (name.isEmpty()) continue
                        add(JwCourse(name, (item.opt("f4") as? String)?.trim().orEmpty(),
                            (item.opt("f3") as? String)?.trim().orEmpty(), index + 1, node, node, week, week,
                            startTime = if (hasTime) start else "", endTime = if (hasTime) end else ""))
                    }
                }
            }
        }
    }.distinct()
    override fun confidence(): Int = if (source.trimStart().startsWith("[") && source.contains("\"week\"")) 90 else 0
    override fun matchedFeatures(): List<String> = if (confidence() > 0) listOf("declared-stum-week-pages") else emptyList()
}
