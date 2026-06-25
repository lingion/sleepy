package com.lingion.sleepy.data.parser

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 课表导出器 — 支持两种格式：
 *
 * 1. **WakeUp 兼容 JSON** — 可以被原版 WakeUp app 导入（兼容 schema: name/position/type/day/startNode/step/startWeek/endWeek/color）
 * 2. **ICS 日历** — 可以导入到系统日历 / Google Calendar / Apple Calendar
 */
object ScheduleExporter {

    private val json = kotlinx.serialization.json.Json {
        prettyPrint = true
        encodeDefaults = true
    }

    /** 导出 WakeUp 兼容 JSON */
    fun exportWakeUpJson(table: TimeTableEntity, courses: List<CourseEntity>): String {
        val courseArr = buildJsonArray {
            courses.forEach { c ->
                add(buildJsonObject {
                    put("name", c.courseName)
                    put("teacher", c.teacher)
                    put("position", c.room)
                    put("day", c.day)
                    put("startNode", c.startNode)
                    put("step", c.step)
                    put("startWeek", c.startWeek)
                    put("endWeek", c.endWeek)
                    put("type", c.type)
                    put("color", c.color)
                })
            }
        }

        val obj = buildJsonObject {
            put("name", table.name)
            put("startDate", table.startDate)
            put("tableInfo", buildJsonObject {
                put("name", table.name)
                put("startDate", table.startDate)
                put("maxWeek", table.maxWeek)
                put("nodesPerDay", table.nodesPerDay)
                put("time", table.timeJson)
            })
            put("courses", courseArr)
        }

        return json.encodeToString(JsonObject.serializer(), obj)
    }

    /** 导出 WakeUp 分享文本格式 (URL 编码的 JSON 字符串) */
    fun exportWakeUpShareText(table: TimeTableEntity, courses: List<CourseEntity>): String {
        val courseArr = buildJsonArray {
            courses.forEach { c ->
                add(buildJsonObject {
                    put("name", c.courseName)
                    put("teacher", c.teacher)
                    put("position", c.room)
                    put("day", c.day)
                    put("startNode", c.startNode)
                    put("step", c.step)
                    put("startWeek", c.startWeek)
                    put("endWeek", c.endWeek)
                    put("type", c.type)
                    put("color", c.color)
                })
            }
        }
        val courseDetailJson = json.encodeToString(JsonArray.serializer(), courseArr)
        val encoded = java.net.URLEncoder.encode(courseDetailJson, "UTF-8")

        val root = buildJsonObject {
            put("name", table.name)
            put("startDate", table.startDate)
            put("courseDetailJson", encoded)
        }

        return "【来自Sleepy】\n课程分享：\n\n" +
                json.encodeToString(JsonObject.serializer(), root)
    }

    /** 导出 ICS 日历 */
    fun exportIcs(table: TimeTableEntity, courses: List<CourseEntity>): String {
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//Sleepy//课程表//ZH")
        sb.appendLine("CALSCALE:GREGORIAN")
        sb.appendLine("METHOD:PUBLISH")
        sb.appendLine("X-WR-CALNAME:${table.name}")

        // 简化：每门课生成一个 VEVENT，根据节次推算时间
        val start = java.time.LocalDate.parse(table.startDate)
        val nodeStartTimes = parseNodeTimes(table.timeJson)

        for (c in courses) {
            val startDate = start.plusWeeks((c.startWeek - 1).toLong())
                .plusDays((c.day - 1).toLong())
            val endDate = start.plusWeeks((c.endWeek - 1).toLong())
                .plusDays((c.day - 1).toLong())

            val startTime = nodeStartTimes.getOrNull(c.startNode - 1)?.first ?: "080000"
            val endTime = nodeStartTimes.getOrNull(c.startNode + c.step - 2)?.second ?: "090000"

            val dtStart = "${startDate.toString().replace("-", "")}T$startTime"
            val dtEnd = "${startDate.toString().replace("-", "")}T$endTime"

            val byDay = when (c.day) {
                1 -> "MO"; 2 -> "TU"; 3 -> "WE"; 4 -> "TH"
                5 -> "FR"; 6 -> "SA"; 7 -> "SU"; else -> null
            }

            sb.appendLine("BEGIN:VEVENT")
            sb.appendLine("UID:${c.id}-${c.courseName.hashCode()}@wakeup-pure")
            sb.appendLine("DTSTAMP:${java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).toString().replace(Regex("[^0-9TZ]"), "").take(15)}Z")
            sb.appendLine("DTSTART:$dtStart")
            sb.appendLine("DTEND:$dtEnd")
            if (byDay != null) {
                sb.appendLine("RRULE:FREQ=WEEKLY;BYDAY=$byDay;UNTIL=${endDate.toString().replace("-", "")}T235959Z")
            } else {
                sb.appendLine("RRULE:FREQ=WEEKLY;UNTIL=${endDate.toString().replace("-", "")}T235959Z")
            }
            sb.appendLine("SUMMARY:${escapeIcs(c.courseName)}")
            if (c.room.isNotBlank()) sb.appendLine("LOCATION:${escapeIcs(c.room)}")
            if (c.teacher.isNotBlank()) sb.appendLine("DESCRIPTION:老师：${escapeIcs(c.teacher)}")
            sb.appendLine("END:VEVENT")
        }
        sb.appendLine("END:VCALENDAR")
        return sb.toString()
    }

    private fun escapeIcs(s: String): String =
        s.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n")

    private fun parseNodeTimes(jsonStr: String): List<Pair<String, String>> {
        return try {
            val arr = kotlinx.serialization.json.Json.parseToJsonElement(jsonStr).let { (it as JsonArray) }
            arr.map { obj ->
                val o = obj as JsonObject
                val start = (o["start"]?.let { (it as JsonPrimitive).content } ?: "00:00").replace(":", "")
                val end = (o["end"]?.let { (it as JsonPrimitive).content } ?: "00:00").replace(":", "")
                val startCompact = if (start.length == 4) "${start}00" else start
                val endCompact = if (end.length == 4) "${end}00" else end
                startCompact to endCompact
            }
        } catch (e: Exception) { emptyList() }
    }
}