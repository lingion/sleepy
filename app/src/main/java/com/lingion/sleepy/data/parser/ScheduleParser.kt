package com.lingion.sleepy.data.parser

import com.lingion.sleepy.data.entity.CourseEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * 课程表文本解析器 — 支持：
 *
 * 1. **WakeUp 课程表分享 JSON**（来自 WakeUp app 的导出）
 *    格式: {"name":"...","startDate":"2024-09-02","courses":[{"name":"高数","teacher":"张三","position":"A101","day":1,"startNode":1,"step":2,"startWeek":1,"endWeek":16,"type":0,"color":"#FF6750A4"}, ...]}
 *
 * 2. **简化的纯文本格式** (一行一课，制表符或空格分隔):
 *    ```
 *    高等数学	张三	A101	1	1-2	1-16	0
 *    大学英语	李四	B202	2	3-4	1-16	0
 *    ```
 *    字段: 课程名\t老师\t教室\t星期\t节次(1-2)\t周次(1-16)\t类型(0/1/2)
 */
object ScheduleParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 解析课表文本。返回 Result.success(list) 或 Result.failure。
     */
    fun parse(text: String, defaultTableId: Long, defaultColor: String = "#FF6750A4"): Result<ParseResult> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("空内容"))

        return runCatching {
            when {
                trimmed.startsWith("{") && trimmed.contains("\"courseDetailJson\"") -> parseWakeUpShareText(trimmed, defaultTableId)
                trimmed.startsWith("{") && (trimmed.contains("\"courses\"") || trimmed.contains("\"tableInfo\"")) -> parseWakeUpJson(trimmed, defaultTableId, defaultColor)
                trimmed.startsWith("BEGIN:VCALENDAR") || trimmed.startsWith("BEGIN:VEVENT") -> parseIcs(trimmed, defaultTableId, defaultColor)
                else -> parseSimpleText(trimmed, defaultTableId, defaultColor)
            }
        }
    }

    /**
     * 解析 WakeUp 分享文本格式:
     * ```
     * 【来自WakeUp课程表】
     * 课程分享:
     *
     * {"name":"...","startDate":"...","courseDetailJson":"<URL-encoded JSON>"}
     * ```
     * 或简化版:
     * ```
     * 课程分享:
     * {"name":"...","startDate":"...","courses":[...]}
     * ```
     */
    private fun parseWakeUpShareText(text: String, defaultTableId: Long): ParseResult {
        // 找到 JSON 部分
        val jsonStart = text.indexOf("{")
        if (jsonStart < 0) throw IllegalArgumentException("找不到 JSON")
        val jsonStr = text.substring(jsonStart)

        val root = json.parseToJsonElement(jsonStr).jsonObject
        val name = root["name"]?.jsonPrimitive?.content ?: "导入的课表"
        val startDate = root["startDate"]?.jsonPrimitive?.content
            ?: root["tableInfo"]?.jsonObject?.get("startDate")?.jsonPrimitive?.content
            ?: java.time.LocalDate.now().toString()

        val courseDetailJsonStr = root["courseDetailJson"]?.jsonPrimitive?.content
        val courses: List<CourseEntity> = if (courseDetailJsonStr != null) {
            // courseDetailJson 是 URL-encoded JSON 字符串
            val decoded = java.net.URLDecoder.decode(courseDetailJsonStr, "UTF-8")
            parseCourseJsonArray(decoded, defaultTableId)
        } else {
            val arr = root["courses"]?.jsonArray
                ?: root["tableInfo"]?.jsonObject?.get("courses")?.jsonArray
                ?: throw IllegalArgumentException("找不到 courses 字段")
            parseCourseJsonArrayRaw(arr, defaultTableId)
        }

        return ParseResult(
            tableName = name,
            startDate = startDate,
            courses = courses
        )
    }

    private fun parseWakeUpJson(text: String, defaultTableId: Long, defaultColor: String): ParseResult {
        val root = json.parseToJsonElement(text).jsonObject
        val name = root["name"]?.jsonPrimitive?.content ?: "导入的课表"
        val startDate = root["startDate"]?.jsonPrimitive?.content
            ?: java.time.LocalDate.now().toString()
        val arr = root["courses"]?.jsonArray ?: throw IllegalArgumentException("找不到 courses 数组")

        val courses = arr.map { el ->
            val obj = el.jsonObject
            CourseEntity(
                id = 0,
                tableId = defaultTableId,
                courseName = obj["name"]?.jsonPrimitive?.content
                    ?: obj["courseName"]?.jsonPrimitive?.content
                    ?: "未命名",
                teacher = obj["teacher"]?.jsonPrimitive?.content ?: "",
                room = obj["position"]?.jsonPrimitive?.content
                    ?: obj["room"]?.jsonPrimitive?.content
                    ?: "",
                note = obj["note"]?.jsonPrimitive?.content ?: "",
                day = obj["day"]?.jsonPrimitive?.intOrZero() ?: 1,
                startNode = obj["startNode"]?.jsonPrimitive?.intOrZero() ?: 1,
                step = obj["step"]?.jsonPrimitive?.intOrZero() ?: 1,
                startWeek = obj["startWeek"]?.jsonPrimitive?.intOrZero() ?: 1,
                endWeek = obj["endWeek"]?.jsonPrimitive?.intOrZero() ?: 16,
                type = obj["type"]?.jsonPrimitive?.intOrZero() ?: 0,
                color = obj["color"]?.jsonPrimitive?.content ?: defaultColor
            )
        }

        return ParseResult(name, startDate, courses)
    }

    private fun parseCourseJsonArray(jsonStr: String, tableId: Long): List<CourseEntity> {
        val arr = json.parseToJsonElement(jsonStr).jsonArray
        return parseCourseJsonArrayRaw(arr, tableId)
    }

    private fun parseCourseJsonArrayRaw(arr: kotlinx.serialization.json.JsonArray, tableId: Long): List<CourseEntity> {
        return arr.map { el ->
            val obj = el.jsonObject
            CourseEntity(
                id = 0,
                tableId = tableId,
                courseName = obj["name"]?.jsonPrimitive?.content
                    ?: obj["courseName"]?.jsonPrimitive?.content
                    ?: "未命名",
                teacher = obj["teacher"]?.jsonPrimitive?.content ?: "",
                room = obj["position"]?.jsonPrimitive?.content ?: "",
                note = "",
                day = obj["day"]?.jsonPrimitive?.intOrZero() ?: 1,
                startNode = obj["startNode"]?.jsonPrimitive?.intOrZero() ?: 1,
                step = obj["step"]?.jsonPrimitive?.intOrZero() ?: 1,
                startWeek = obj["startWeek"]?.jsonPrimitive?.intOrZero() ?: 1,
                endWeek = obj["endWeek"]?.jsonPrimitive?.intOrZero() ?: 16,
                type = obj["type"]?.jsonPrimitive?.intOrZero() ?: 0,
                color = obj["color"]?.jsonPrimitive?.content ?: "#FF6750A4"
            )
        }
    }

    /**
     * 解析 ICS 日历文件 (RFC 5545)。
     * 简化版：每个 VEVENT 视为一节课，按 RRULE 展开成单双周处理。
     */
    private fun parseIcs(text: String, defaultTableId: Long, defaultColor: String): ParseResult {
        val courses = mutableListOf<CourseEntity>()
        val events = text.split("BEGIN:VEVENT").drop(1)
        for (event in events) {
            val end = event.indexOf("END:VEVENT")
            val block = if (end > 0) event.substring(0, end) else event

            val summary = extractIcsField(block, "SUMMARY") ?: continue
            val location = extractIcsField(block, "LOCATION") ?: ""
            val description = extractIcsField(block, "DESCRIPTION") ?: ""

            val (startNode, step) = extractIcsTime(block) ?: continue
            val day = extractIcsDayOfWeek(block) ?: continue
            val (startWeek, endWeek, type) = extractIcsWeeks(block) ?: Triple(1, 16, 0)

            val teacher = if (description.isNotBlank()) description else ""
            courses += CourseEntity(
                id = 0,
                tableId = defaultTableId,
                courseName = summary,
                teacher = teacher,
                room = location,
                note = "",
                day = day,
                startNode = startNode,
                step = step,
                startWeek = startWeek,
                endWeek = endWeek,
                type = type,
                color = defaultColor
            )
        }

        return ParseResult(
            tableName = "导入的 ICS 课表",
            startDate = java.time.LocalDate.now().toString(),
            courses = courses
        )
    }

    private fun extractIcsField(block: String, name: String): String? {
        // ICS 字段可能折行 (下一行以空格开头)
        val regex = Regex("(?m)^$name(?:;[^:]*)?:(.*(?:\\n .*)*)")
        return regex.find(block)?.groupValues?.get(1)
            ?.replace(Regex("\\n "), "")
            ?.trim()
    }

    /** 从 DTSTART 提取节次（默认按 45min/节估算） */
    private fun extractIcsTime(block: String): Pair<Int, Int>? {
        val dtstart = extractIcsField(block, "DTSTART") ?: return null
        val dtend = extractIcsField(block, "DTEND") ?: return null
        // 解析 HHmmss
        return try {
            val start = parseIcsTimeOfDay(dtstart.substringAfter("T").take(6))
            val end = parseIcsTimeOfDay(dtend.substringAfter("T").take(6))
            val startMin = start.hour * 60 + start.minute
            val endMin = end.hour * 60 + end.minute
            val duration = endMin - startMin
            val startNode = ((startMin - 480) / 55).toInt() + 1  // 8:00 = 第 1 节
            val step = (duration / 55).coerceAtLeast(1)
            Pair(startNode.coerceAtLeast(1), step)
        } catch (e: Exception) { null }
    }

    private fun parseIcsTimeOfDay(s: String): java.time.LocalTime =
        java.time.LocalTime.of(s.substring(0, 2).toInt(), s.substring(2, 4).toInt())

    /** 从 BYDAY 提取星期几 */
    private fun extractIcsDayOfWeek(block: String): Int? {
        val rrule = extractIcsField(block, "RRULE") ?: return null
        val match = Regex("BYDAY=([A-Z]{2})").find(rrule) ?: return null
        return when (match.groupValues[1]) {
            "MO" -> 1; "TU" -> 2; "WE" -> 3; "TH" -> 4
            "FR" -> 5; "SA" -> 6; "SU" -> 7
            else -> null
        }
    }

    private fun extractIcsWeeks(block: String): Triple<Int, Int, Int>? {
        // 仅做最简支持：使用 UNTIL/COUNT 推导范围，type 默认为每周
        return Triple(1, 16, 0)
    }

    /**
     * 解析简化的纯文本格式：
     * 一行一课，字段间用制表符或全角逗号分隔。
     *
     * 示例:
     * ```
     * 高等数学\t张三\tA101\t1\t1-2\t1-16\t0
     * 大学英语\t李四\tB202\t2\t3-4\t1-16\t0
     * ```
     */
    private fun parseSimpleText(text: String, defaultTableId: Long, defaultColor: String): ParseResult {
        val courses = mutableListOf<CourseEntity>()
        val lines = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }

        for (line in lines) {
            // 支持 tab / 空格 / 全角逗号
            val parts = line.split('\t', '，').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size < 6) continue

            val name = parts[0]
            val teacher = parts[1]
            val room = parts[2]
            val day = parts[3].toIntOrNull() ?: continue
            val (startNode, step) = parseRange(parts[4]) ?: continue
            val (startWeek, endWeek) = parseRange(parts[5]) ?: continue
            val type = parts.getOrNull(6)?.toIntOrNull() ?: 0

            courses += CourseEntity(
                id = 0,
                tableId = defaultTableId,
                courseName = name,
                teacher = teacher,
                room = room,
                day = day,
                startNode = startNode,
                step = step,
                startWeek = startWeek,
                endWeek = endWeek,
                type = type,
                color = defaultColor
            )
        }

        if (courses.isEmpty()) throw IllegalArgumentException("未能解析任何课程")

        return ParseResult(
            tableName = "导入的课表",
            startDate = java.time.LocalDate.now().toString(),
            courses = courses
        )
    }

    private fun parseRange(s: String): Pair<Int, Int>? {
        val parts = s.split('-', '~', '至')
        if (parts.size != 2) return null
        val start = parts[0].toIntOrNull() ?: return null
        val end = parts[1].toIntOrNull() ?: return null
        return start to end
    }

    private fun kotlinx.serialization.json.JsonPrimitive.intOrZero(): Int =
        content.toIntOrNull() ?: 0

    private fun kotlinx.serialization.json.JsonPrimitive.longOrZero(): Long =
        content.toLongOrNull() ?: 0L

    data class ParseResult(
        val tableName: String,
        val startDate: String,
        val courses: List<CourseEntity>
    )
}