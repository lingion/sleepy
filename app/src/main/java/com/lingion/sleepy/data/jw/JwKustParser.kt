package com.lingion.sleepy.data.jw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Parser for KMUST unified portal weekly timetable grid. */
class JwKustParser(source: String) : JwParser(source) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun generateCourseList(): List<JwCourse> {
        val root = runCatching { json.parseToJsonElement(source).jsonObject }.getOrNull() ?: return emptyList()
        val data = root["data"]?.jsonObject ?: return emptyList()
        val grid = data["resultsJsonArr"] as? JsonArray ?: return emptyList()
        val week = data.string("zs").toIntOrNull() ?: return emptyList()
        val out = linkedMapOf<String, JwCourse>()
        for ((rowIndex, rowElement) in grid.withIndex()) {
            val row = rowElement as? JsonArray ?: continue
            if (rowIndex < 2) continue
            val section = rowIndex - 1
            for ((columnIndex, cellElement) in row.withIndex()) {
                if (columnIndex == 0) continue
                val cell = cellElement.jsonPrimitive.contentOrNull?.trim().orEmpty()
                if (cell.isBlank()) continue
                val day = columnIndex.coerceIn(1, 7)
                val fields = cell.substringBefore(";").split(",")
                val name = fields.getOrNull(0).orEmpty().trim()
                if (name.isBlank()) continue
                val room = fields.getOrNull(1).orEmpty().trim()
                val teacher = cell.substringAfter(';', "").trim()
                val key = listOf(name, room, teacher, day, week).joinToString("|")
                val previous = out[key]
                if (previous == null) {
                    out[key] = JwCourse(name, room, teacher, day, section, section, week, week)
                } else if (section == previous.endNode + 1) {
                    out[key] = previous.copy(endNode = section)
                }
            }
        }
        return out.values.toList()
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
    this[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
