package com.lingion.sleepy.data.jw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Parser for KMUST unified portal weekly timetable grid. */
class JwKustParser(source: String) : JwParser(source) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun generateCourseList(): List<JwCourse> {
        val root = runCatching { json.parseToJsonElement(source).jsonObject }.getOrNull() ?: return emptyList()
        val data = root["data"] as? JsonObject ?: return emptyList()
        val grid = data["resultsJsonArr"] as? JsonArray ?: return emptyList()
        val week = data.string("zs").toIntOrNull() ?: return emptyList()
        val out = mutableListOf<JwCourse>()
        val lastByKey = mutableMapOf<JwCourse, Int>()
        for ((rowIndex, rowElement) in grid.withIndex()) {
            val row = rowElement as? JsonArray ?: continue
            if (rowIndex < 2 || rowIndex - 1 > EXPLICIT_SCHEDULE_RESOURCE_BOUND) continue
            val section = rowIndex - 1
            for ((columnIndex, cellElement) in row.withIndex()) {
                if (columnIndex !in 1..7) continue
                val cell = (cellElement as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                if (cell.isBlank()) continue
                val day = columnIndex
                val fields = cell.substringBefore(";").split(",")
                val name = fields.getOrNull(0).orEmpty().trim()
                if (name.isBlank()) continue
                val room = fields.getOrNull(1).orEmpty().trim()
                val teacher = cell.substringAfter(';', "").trim()
                val key = JwCourse(name, room, teacher, day, 0, 0, week, week)
                val previousIndex = lastByKey[key]
                val previous = previousIndex?.let(out::get)
                if (previousIndex != null && previous != null && section == previous.endNode + 1) {
                    out[previousIndex] = previous.copy(endNode = section)
                } else {
                    lastByKey[key] = out.size
                    out += JwCourse(name, room, teacher, day, section, section, week, week)
                }
            }
        }
        return out.toList()
    }

    override fun confidence(): Int = runCatching {
        val data = json.parseToJsonElement(source).jsonObject["data"]?.jsonObject
        if (data?.get("resultsJsonArr") is JsonArray) 90 else 0
    }.getOrDefault(0)

    override fun matchedFeatures(): List<String> = buildList {
        if (source.contains("queryAWeekSchedule")) add("queryAWeekSchedule")
        if (source.contains("resultsJsonArr")) add("resultsJsonArr")
        if (source.contains("weekcount")) add("weekcount")
    }
}

private fun JsonObject.string(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
