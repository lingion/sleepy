package com.lingion.sleepy.data.jw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Public curriculum share LessonArray, distinct from the authenticated academic rows API. */
class JwChaoxingShareParser(source: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> {
        val rows = runCatching { Json.parseToJsonElement(source) as? JsonArray }.getOrNull()
            ?: return emptyList()
        return mergeCourseWeeks(buildList {
            for (element in rows) {
                val row = element as? JsonObject ?: continue
                fun text(key: String) = (row[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val name = text("name")
                val day = text("dayOfWeek").toIntOrNull() ?: continue
                val begin = text("beginNumber").toIntOrNull() ?: continue
                val length = text("length").toIntOrNull() ?: continue
                val end = begin.toLong() + length - 1
                if (name.isBlank() || day !in 1..7 || begin <= 0 || length <= 0 || end > Int.MAX_VALUE) continue
                val weeks = text("weeks").split(',').map { it.trim().toIntOrNull() }
                if (weeks.any { it == null || it !in 1..366 }) continue
                // The shared run splitter preserves step changes and holes in the explicit week set.
                for (run in JwUcasParser.splitWeekRuns(weeks.filterNotNull())) {
                    add(JwCourse(
                        name = name, room = text("location") + text("onlineLocation"),
                        teacher = text("teacherName"), day = day, startNode = begin, endNode = end.toInt(),
                        startWeek = run.startWeek, endWeek = run.endWeek, type = run.type,
                    ))
                }
            }
        })
    }
}
