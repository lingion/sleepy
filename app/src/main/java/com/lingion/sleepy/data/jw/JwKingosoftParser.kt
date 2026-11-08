package com.lingion.sleepy.data.jw

import org.json.JSONArray

/** Kingosoft JSON: array position is the semester week, week1..week7 are weekdays. */
class JwKingosoftParser(source: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> {
        val buckets = try { JSONArray(source) } catch (_: Exception) { return emptyList() }
        val occurrences = linkedMapOf<JwCourse, java.util.SortedSet<Int>>()
        for (index in 0 until buckets.length()) {
            // Null and empty-string buckets still occupy their week number.
            val bucket = buckets.optJSONObject(index) ?: continue
            for (day in 1..7) {
                val rows = bucket.optJSONArray("week$day") ?: continue
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val name = row.optString("kcmc", "").trim()
                    val nodes = Regex("""([0-9]+)(?:\s*-\s*([0-9]+))?""")
                        .matchEntire(row.optString("jcxx", "").trim()) ?: continue
                    val first = nodes.groupValues[1].toIntOrNull() ?: continue
                    val last = nodes.groupValues[2].ifEmpty { nodes.groupValues[1] }.toIntOrNull() ?: continue
                    if (name.isEmpty() || first <= 0 || last < first) continue
                    val credit = row.optString("xf", "").toFloatOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: 0f
                    val identity = JwCourse(name, row.optString("skdd", "").trim(), row.optString("rkjs", "").trim(),
                        day, first, last, 0, 0, credit = credit)
                    occurrences.getOrPut(identity) { sortedSetOf() }.add(index + 1)
                }
            }
        }
        return occurrences.flatMap { (course, weeks) ->
            JwChengFangParser.weekIntList2WeekBeanList(weeks.toList()).map { (from, to, type) ->
                course.copy(startWeek = from, endWeek = to, type = type)
            }
        }
    }

    override fun confidence(): Int = if (Regex(""""week[1-7]"\s*:""").containsMatchIn(source) && source.contains("\"jcxx\"")) 100 else 0
    override fun matchedFeatures(): List<String> = if (confidence() > 0) listOf("kingosoft-week-array", "jcxx") else emptyList()
}
