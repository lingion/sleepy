package com.lingion.sleepy.data.jw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
            appointments(details).flatMap { parseDetail(name, it) }
        }
    }

    /** Find each complete four-field prefix before considering commas in its room. */
    private fun appointments(details: String): List<String> {
        val prefix = Regex("""(?:^|[,，])\s*([0-9][^/]*?)/\s*星期[一二三四五六日]\s*/\s*第\d+节-第\d+节\s*/\s*[^/]+?\[主讲]\s*/""")
        val starts = prefix.findAll(details).toList()
        return starts.mapIndexed { index, match ->
            details.substring(match.range.first, starts.getOrNull(index + 1)?.range?.first ?: details.length)
                .trimStart(',', '，').trim()
        }
    }

    private fun parseDetail(name: String, detail: String): List<JwCourse> {
        val match = Regex("""(.+?)\s*/\s*星期([一二三四五六日])\s*/\s*第(\d+)节-第(\d+)节\s*/\s*(.+?)\[主讲\]\s*/\s*(.+)""").matchEntire(detail.trim()) ?: return emptyList()
        val weeks = parseWeeks(match.groupValues[1])
        val day = dayMap[match.groupValues[2]] ?: return emptyList()
        val start = match.groupValues[3].toIntOrNull() ?: return emptyList()
        val end = match.groupValues[4].toIntOrNull() ?: return emptyList()
        if (start !in 1..EXPLICIT_SCHEDULE_RESOURCE_BOUND || end !in start..EXPLICIT_SCHEDULE_RESOURCE_BOUND) return emptyList()
        val teacher = match.groupValues[5].trim()
        val room = match.groupValues[6].trim()
        return weeks.map { week -> JwCourse(name, room, teacher, day, start, end, week, week) }
    }

    private fun parseWeeks(text: String): List<Int> = schoolWeekRanges(
        text.replace(Regex("\\[[^]]*]"), ""),
    ).flatMap { (first, last, parity) -> (first..last).filter { parity == 0 || it % 2 == parity % 2 } }

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
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
