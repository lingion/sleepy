package com.lingion.sleepy.data.jw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Parser for NUIT Wisedu course objects and their classDateAndPlace strings. */
class JwNuitParser(source: String) : JwParser(source) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val dayMap = mapOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7)

    override fun generateCourseList(): List<JwCourse> {
        val root = runCatching { json.parseToJsonElement(source).jsonObject }.getOrNull() ?: return emptyList()
        val rows = root["datas"] as? JsonArray ?: return emptyList()
        return rows.flatMap { element ->
            val obj = element as? JsonObject ?: return@flatMap emptyList()
            val name = obj.string("courseName")
            val details = obj.string("classDateAndPlace")
            if (name.isBlank() || details.isBlank()) return@flatMap emptyList()
            details.split('，', ',').flatMap { parseDetail(name, it) }
        }
    }

    private fun parseDetail(name: String, detail: String): List<JwCourse> {
        val match = Regex("""(.+?)\s*/\s*星期([一二三四五六日])\s*/\s*第(\d+)节-第(\d+)节\s*/\s*(.+?)\[主讲\]\s*/\s*(.+)""").matchEntire(detail.trim()) ?: return emptyList()
        val weeks = parseWeeks(match.groupValues[1])
        val day = dayMap[match.groupValues[2]] ?: return emptyList()
        val start = match.groupValues[3].toIntOrNull() ?: return emptyList()
        val end = match.groupValues[4].toIntOrNull() ?: start
        val teacher = match.groupValues[5].trim()
        val room = match.groupValues[6].trim()
        return weeks.map { week -> JwCourse(name, room, teacher, day, start, end, week, week) }
    }

    private fun parseWeeks(text: String): List<Int> = text.replace(Regex("\\[[^]]*]"), "")
        .replace("周", "").replace(" ", "")
        .split(',', '，')
        .flatMap { segment ->
            val odd = segment.contains("(单)")
            val even = segment.contains("(双)")
            val clean = segment.replace("(单)", "").replace("(双)", "")
            val bounds = clean.split('-', limit = 2).mapNotNull { it.toIntOrNull() }
            val range = when (bounds.size) { 2 -> bounds[0]..bounds[1]; 1 -> bounds[0]..bounds[0]; else -> IntRange.EMPTY }
            range.filter { (!odd || it % 2 == 1) && (!even || it % 2 == 0) }
        }

    override fun confidence(): Int = runCatching {
        val rows = json.parseToJsonElement(source).jsonObject["datas"]
        if (rows is JsonArray && source.contains("classDateAndPlace")) 90 else 0
    }.getOrDefault(0)

    override fun matchedFeatures(): List<String> = buildList {
        if (source.contains("student/courses.do")) add("student/courses.do")
        if (source.contains("classDateAndPlace")) add("classDateAndPlace")
        if (source.contains("courseName")) add("courseName")
    }
}

private fun JsonObject.string(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
