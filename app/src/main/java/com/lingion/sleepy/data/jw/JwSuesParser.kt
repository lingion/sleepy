package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject

/** SUES south-soft activities with room-dependent period 3 through 5 clock times. */
internal class JwSuesParser(source: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> {
        val root = runCatching { JSONObject(source) }.getOrNull() ?: return emptyList()
        val tables = mutableListOf<JSONObject>()
        root.optJSONArray("studentTableVms")?.let { values ->
            for (index in 0 until values.length()) values.optJSONObject(index)?.let(tables::add)
        }
        root.optJSONObject("studentTableVm")?.let(tables::add)
        val output = mutableListOf<JwCourse>()
        for (table in tables) {
            val activities = table.optJSONArray("activities") ?: continue
            for (index in 0 until activities.length()) {
                val item = activities.optJSONObject(index) ?: continue
                val weeks = item.optJSONArray("weekIndexes") ?: continue
                val actualWeeks = (0 until weeks.length()).map { slot ->
                    weeks.opt(slot)?.toString()?.toIntOrNull()?.takeIf { it > 0 }
                }
                if (actualWeeks.isEmpty() || actualWeeks.any { it == null }) continue
                val day = item.integer("weekday") ?: continue
                val start = item.integer("startUnit") ?: continue
                val end = item.integer("endUnit") ?: continue
                val name = item.string("courseName")
                val room = item.string("room").trim()
                val teacher = item.optJSONArray("teachers")?.let { values ->
                    (0 until values.length()).mapNotNull { values.opt(it) as? String }.joinToString(",")
                }.orEmpty()
                val firstTime = adjustedTime(room, start, false, item.string("startTime"))
                val lastTime = adjustedTime(room, end, true, item.string("endTime"))
                val validTime = JwCourse.isValidTimeRange(firstTime, lastTime)
                val credit = item.opt("credits")?.toString()?.toFloatOrNull()?.takeIf(Float::isFinite) ?: 0f
                val note = item.string("lessonCode") + item.string("lessonRemark")
                for (week in actualWeeks.filterNotNull().distinct().sorted()) output += JwCourse(name, room, teacher, day,
                    start, end, week, week, startTime = if (validTime) firstTime else "",
                    endTime = if (validTime) lastTime else "", credit = credit, note = note)
            }
        }
        return output.filter(JwUndergradBHtmlParser::valid).distinct()
    }

    private fun JSONObject.integer(key: String): Int? = opt(key)?.toString()?.toIntOrNull()
    private fun JSONObject.string(key: String): String = opt(key)?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty()

    companion object {
        internal fun adjustedTime(room: String, node: Int, end: Boolean, fallback: String): String {
            val early = Regex("([AF][0-9]{3}|J301)多").matches(room)
            val late = Regex("([DE][0-9]{3}|J303)(多|\\(中外教室）)").matches(room)
            val value = when (node) {
                3 -> if (late) "10:15" to "10:55" else "09:55" to "10:35"
                4 -> when { late -> "10:55" to "11:35"; early -> "10:40" to "11:20"; else -> "10:35" to "11:15" }
                5 -> if (late) "11:40" to "12:20" else "11:20" to "12:00"
                else -> null
            }
            if (value != null) return if (end) value.second else value.first
            val m = Regex("([0-9]{1,2}):([0-9]{2})").matchEntire(fallback.trim()) ?: return ""
            val hour = m.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return ""
            val minute = m.groupValues[2].toIntOrNull()?.takeIf { it in 0..59 } ?: return ""
            return "%02d:%02d".format(java.util.Locale.ROOT, hour, minute)
        }

        val periods = listOf("08:15-08:55", "08:55-09:35", "09:55-10:40", "10:40-11:35", "11:35-12:20",
            "13:20-14:00", "14:00-14:40", "15:00-15:40", "15:40-16:20", "16:35-17:15", "17:15-17:55",
            "18:10-18:50", "18:50-19:30", "19:35-20:15", "20:20-21:00")
            .mapIndexed { index, value -> Triple(index + 1, value.substringBefore('-'), value.substringAfter('-')) }
    }
}
