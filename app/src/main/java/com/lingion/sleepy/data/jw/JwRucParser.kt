package com.lingion.sleepy.data.jw

import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup

/** RUC's declared var data object: quittingTime is a duration, schoolHours is indexed by period. */
class JwRucParser(source: String) : JwParser(source) {
    private val data: JSONObject? by lazy {
        fun read(text: String): JSONObject? = runCatching { JSONTokener(text).nextValue() as? JSONObject }
            .getOrNull()?.takeIf { it.optJSONArray("course") != null && it.optJSONArray("schoolHours") != null }
        if (source.trimStart().startsWith('{')) read(source) else {
            val scripts = Jsoup.parse(source).select("script").map { it.data() }
            scripts.asSequence().flatMap { script ->
                Regex("""\bvar\s+data\s*=\s*""").findAll(script).mapNotNull { assignment ->
                    read(script.substring(assignment.range.last + 1))
                }
            }.firstOrNull()
        }
    }

    override fun generateCourseList(): List<JwCourse> = buildList {
        val root = data ?: return@buildList
        val hours = root.optJSONArray("schoolHours") ?: return@buildList
        val clocks = (0 until hours.length()).map { index ->
            val parts = hours.optString(index, "").split('~').map { it.trim().padStart(5, '0') }
            parts.takeIf { it.size == 2 && JwCourse.isValidTimeRange(it[0], it[1]) }?.let { it[0] to it[1] }
        }
        val courses = root.optJSONArray("course") ?: return@buildList
        for (index in 0 until courses.length()) {
            val row = courses.optJSONObject(index) ?: continue
            val name = row.optString("title", "").trim()
            val day = row.optString("week", "").trim().toIntOrNull() ?: continue
            val first = row.optString("start", "").trim().toIntOrNull() ?: continue
            val length = row.optString("quittingTime", "").trim().toIntOrNull() ?: continue
            if (name.isEmpty() || day !in 1..7 || first !in 1..30 || length !in 1..30 || first > 31 - length) continue
            val last = first + length - 1
            val start = clocks.getOrNull(first - 1)?.first.orEmpty()
            val end = clocks.getOrNull(last - 1)?.second.orEmpty()
            val completeClock = (first..last).all { clocks.getOrNull(it - 1) != null } && JwCourse.isValidTimeRange(start, end)
            val room = row.optString("place", "").substringAfter("地点：").trim()
            val teacher = row.optString("teacher", "").substringAfter("教师：").trim()
            for ((from, to, type) in schoolWeekRanges(row.optString("weekly", ""), trailingParityAppliesToAll = true)) {
                add(JwCourse(name, room, teacher, day, first, last, from, to, type,
                    if (completeClock) start else "", if (completeClock) end else ""))
            }
        }
    }.distinct()

    override fun confidence(): Int = if (data != null) 100 else 0
    override fun matchedFeatures(): List<String> = if (data != null) listOf("ruc-course", "schoolHours") else emptyList()
}
