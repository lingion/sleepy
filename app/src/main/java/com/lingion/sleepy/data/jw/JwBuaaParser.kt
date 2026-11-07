package com.lingion.sleepy.data.jw

import org.json.JSONObject

/** BUAA arrangedList layout, preserving each teacher's own week and place segment. */
internal class JwBuaaParser(source: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> {
        val records = runCatching { JSONObject(source).optJSONObject("datas")?.optJSONArray("arrangedList") }.getOrNull() ?: return emptyList()
        val output = mutableListOf<JwCourse>()
        for (index in 0 until records.length()) {
            val record = records.optJSONObject(index) ?: continue
            val name = record.text("courseName")
            val day = record.text("dayOfWeek").toIntOrNull() ?: continue
            val first = record.text("beginSection").toIntOrNull() ?: continue
            val last = record.text("endSection").toIntOrNull() ?: continue
            if (name.isEmpty() || day !in 1..7 || first !in 1..30 || last !in first..30) continue
            val startTime = record.text("beginTime")
            val endTime = record.text("endTime")
            if ((startTime.isNotEmpty() || endTime.isNotEmpty()) && !JwCourse.isValidTimeRange(startTime, endTime)) continue
            val credit = record.text("credit").toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f } ?: 0f
            val details = record.optJSONArray("cellDetail") ?: continue
            val title = record.optJSONArray("titleDetail")
            val note = if (title == null || title.isNull(8)) "" else title.optString(8).trim()
            for (detailIndex in 0 until details.length()) {
                val text = details.optJSONObject(detailIndex)?.text("text") ?: continue
                val segments = if (Regex("^.+\\[[0-9].*周(?:\\([单双]\\))*]").containsMatchIn(text)) {
                    val matches = Regex("([^\\[\\]]+)\\[([^]]+)]").findAll(text).toList()
                    val remainder = Regex("([^\\[\\]]+)\\[([^]]+)]").replace(text, "").trim()
                    if (remainder.isNotEmpty()) emptyList() else matches.map { match ->
                        Segment(match.groupValues[1].trim(), "", match.groupValues[2])
                    }
                } else if (Regex("^[0-9].*周").containsMatchIn(text)) {
                    val lastWeek = Regex("[0-9]周(?:\\([单双]\\))?").findAll(text).lastOrNull() ?: continue
                    val weeks = text.substring(0, lastWeek.range.last + 1)
                    val rest = text.substring(lastWeek.range.last + 1).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                    if (rest.isEmpty()) emptyList() else listOf(Segment(rest[0].substringBefore('['), rest.getOrNull(1).orEmpty(), weeks))
                } else emptyList()
                for (segment in segments) for ((start, end, parity) in schoolWeekRanges(segment.weeks)) {
                    if ((start..end).none { parity == 0 || it % 2 == parity % 2 }) continue
                    output += JwCourse(name, segment.room.ifBlank { record.text("placeName") }, segment.teacher,
                        day, first, last, start, end, parity, startTime, endTime, credit, note)
                }
            }
        }
        return output.distinct()
    }

    private data class Segment(val teacher: String, val room: String, val weeks: String)
    private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key).trim()
}
