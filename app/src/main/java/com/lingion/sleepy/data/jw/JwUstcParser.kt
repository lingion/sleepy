package com.lingion.sleepy.data.jw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * USTC studentTableVm.activities. lessonCode identifies the course, never its periods.
 * Explicit startUnit/endUnit are authoritative; clock-only records keep their actual
 * times through import instead of guessing a campus clock. weeksStr supplies weeks.
 *
 * Protocol references: 1970633640/USTC-timetable-to-ics and USTC-XeF2/pyustc
 * (50174b8, MIT), eams/client.py and eams/_course.py. Field mapping is implemented here.
 */
class JwUstcParser(source: String) : JwParser(source) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun generateCourseList(): List<JwCourse> {
        val table = runCatching { json.parseToJsonElement(source) as? JsonObject }
            .getOrNull()?.get("studentTableVm") as? JsonObject ?: return emptyList()
        val activities = table["activities"] as? JsonArray ?: return emptyList()
        val weekParser = JwEams5Parser("")
        return activities.flatMap { element ->
            val activity = element as? JsonObject ?: return@flatMap emptyList()
            val name = activity.str("courseName").takeIf { it.isNotEmpty() } ?: return@flatMap emptyList()
            val day = activity.str("weekday").toIntOrNull()?.takeIf { it in 1..7 } ?: return@flatMap emptyList()
            val startTime = activity.str("startDate")
            val endTime = activity.str("endDate")
            val hasTime = JwCourse.isValidTimeRange(startTime, endTime)
            if ((startTime.isNotEmpty() || endTime.isNotEmpty()) && !hasTime) return@flatMap emptyList()
            val nodes = if (activity.containsKey("startUnit") || activity.containsKey("endUnit")) {
                val first = activity.str("startUnit").toIntOrNull() ?: return@flatMap emptyList()
                val last = activity.str("endUnit").toIntOrNull() ?: return@flatMap emptyList()
                if (first !in 1..EXPLICIT_SCHEDULE_RESOURCE_BOUND || last !in first..EXPLICIT_SCHEDULE_RESOURCE_BOUND)
                    return@flatMap emptyList()
                first to last
            } else {
                if (!hasTime) return@flatMap emptyList()
                // These coordinates are placeholders; ownTime retains the source clock.
                1 to 1
            }
            val weeks = weekParser.parseWeekRanges(activity.str("weeksStr").replace(")周", ")"))
            if (weeks.any { it.first !in 1..366 || it.second !in it.first..366 }) return@flatMap emptyList()
            val plainTeachers = (activity["teachers"] as? JsonArray)?.mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
            }.orEmpty()
            val teachers = plainTeachers.ifEmpty {
                (activity["teacherDeepVms"] as? JsonArray)?.mapNotNull {
                    ((it as? JsonObject)?.get("person") as? JsonObject)?.str("nameZh")?.takeIf(String::isNotEmpty)
                }.orEmpty()
            }.joinToString(" ").ifEmpty { activity.str("teachers") }
            val room = activity.str("room").ifEmpty { activity.str("customPlace") }
            weeks.map { (first, last, parity) ->
                val adjusted = JwParity.adjustedRange(first, last, parity)
                JwCourse(name, room, teachers, day, nodes.first, nodes.second,
                    adjusted.first, adjusted.second, parity, startTime, endTime,
                    activity.str("credits").toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f } ?: 0f,
                    activity.str("lessonCode") + activity.str("lessonRemark"))
            }
        }.distinct()
    }

    override fun confidence(): Int = if (hasTable()) 90 else 0

    override fun matchedFeatures(): List<String> = if (hasTable()) listOf("studentTableVm") else emptyList()

    private fun hasTable(): Boolean = runCatching {
        (json.parseToJsonElement(source) as? JsonObject)?.get("studentTableVm") is JsonObject
    }.getOrDefault(false)
}

private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
