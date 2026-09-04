package com.lingion.sleepy.data.jw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 中国科学技术大学新版自研教务 (jw.ustc.edu.cn) studentTableVm 解析器。
 *
 * 数据源: 学在浙大 / 我的课表 / 课表 JSON 接口, 路径 x.studentTableVm.activities[]。
 * 字段集: {courseName, campus, customPlace, room, building, teachers[],
 *          weeksStr, weekday, startDate, endDate, lessonCode, credits}。
 * weeksStr 一次性给完整周次串 (EAMS5 同款优势): "1-16" / "1-16单" / "1-16双" /
 * "2,4,6,8,10,12,14,16"。lessonCode 4 位数字, 1-2 位=startNode, 3-4 位=endNode。
 *
 * 上游协议形态参考: 1970633640/USTC-timetable-to-ics (无 license) json_version.py
 * (https://github.com/1970633640/USTC-timetable-to-ics/blob/master/json_version.py)
 * 周次串解析逻辑代码自写, 字段映射参考 (courseName/room/teachers/weeksStr 等)。
 */
class JwUstcParser(source: String) : JwParser(source) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun generateCourseList(): List<JwCourse> {
        val root = runCatching { json.parseToJsonElement(source).jsonObject }
            .getOrNull() ?: return emptyList()
        val tableVm = root["studentTableVm"] as? JsonObject ?: return emptyList()
        val activities = tableVm["activities"] as? JsonArray ?: return emptyList()
        val out = mutableListOf<JwCourse>()
        for (el in activities) {
            val obj = el as? JsonObject ?: continue
            val name = obj.str("courseName")
            if (name.isEmpty()) continue
            val room = obj.fallbackRoom()
            val teacher = obj.joinTeachers()
            // lessonCode 4 位定宽是自创解读 (上游 json_version.py 只把它拼进
            // description, 节次实际由 startDate/endDate 时间表达); 不合式时用
            // startTime/endTime 推断兜底, 不整行丢弃。
            val fromCode = obj.parseLessonCode()
            val (startNode, endNode) = if (fromCode.first > 0) fromCode
                else inferNodesFromTime(obj.str("startDate"), obj.str("endDate")) ?: continue
            val weekday = obj.str("weekday").toIntOrNull() ?: continue
            val weeksStr = obj.str("weeksStr")
            if (weeksStr.isEmpty()) continue
            val (typeInt, cleanStr) = parseWeeksType(weeksStr)
            for ((segStart, segEnd) in parseWeekSegments(cleanStr)) {
                val (sw, ew) = JwParity.adjustedRange(segStart, segEnd, typeInt)
                out += JwCourse(
                    name = name,
                    room = room,
                    teacher = teacher,
                    day = weekday,
                    startNode = startNode,
                    endNode = endNode,
                    startWeek = sw,
                    endWeek = ew,
                    type = typeInt,
                )
            }
        }
        return out
    }

    /**
     * weeksStr 形态: "1-16" / "1-16单" / "1-16双" / "2,4,6,8,10,12,14,16"。
     * 返回 (type, cleanStr) — type 0=每周 1=单 2=双, cleanStr 已剥离 "单/双" 后缀。
     */
    internal fun parseWeeksType(s: String): Pair<Int, String> {
        return when {
            s.contains("单") -> 1 to s.replace("单", "").trim()
            s.contains("双") -> 2 to s.replace("双", "").trim()
            else -> 0 to s.trim()
        }
    }

    /**
     * 解析 "1-16" / "2,4,6,8,10,12,14,16" 形式的周次串 (已剥 "单/双")。
     */
    internal fun parseWeekSegments(s: String): List<Pair<Int, Int>> {
        if (s.isBlank()) return emptyList()
        val out = mutableListOf<Pair<Int, Int>>()
        for (seg in s.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }) {
            if (seg.contains("-")) {
                val parts = seg.split("-", limit = 2).map { it.trim() }
                val a = parts.getOrNull(0)?.toIntOrNull() ?: continue
                val b = parts.getOrNull(1)?.toIntOrNull() ?: a
                out += a to b
            } else {
                val v = seg.toIntOrNull() ?: continue
                out += v to v
            }
        }
        return out
    }

    private fun JsonObject.fallbackRoom(): String {
        // room 优先; room 为空/缺失时回退到 customPlace; 两者皆空则空串
        val room = str("room")
        if (room.isNotEmpty()) return room
        val custom = str("customPlace")
        return custom
    }

    private fun JsonObject.joinTeachers(): String {
        val arr = this["teachers"] as? JsonArray ?: return str("teachers")
        return arr.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }

    private fun JsonObject.parseLessonCode(): Pair<Int, Int> {
        val s = str("lessonCode")
        if (s.length < 4 || !s.all { it.isDigit() }) return 0 to 0
        val start = s.substring(0, 2).toIntOrNull() ?: 0
        val end = s.substring(2, 4).toIntOrNull() ?: start
        return start to end
    }

    /**
     * lessonCode 缺失/不合式时的节次推断兜底: startDate/endDate (HH:mm) 按
     * 标准 985 节次布局映射 (同 [JwEams5Parser] 段起点表, ±10 分钟吸附)。
     * 推断不出返回 null (调用方跳过该行)。
     */
    private fun inferNodesFromTime(startDate: String, endDate: String): Pair<Int, Int>? {
        fun parseHHmm(s: String): Int? {
            val parts = s.split(":")
            val h = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
            val m = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return null
            if (h !in 0..23 || m !in 0..59) return null
            return h * 60 + m
        }
        val startMins = parseHHmm(startDate) ?: return null
        val endMins = parseHHmm(endDate) ?: return null
        val slots = listOf(
            8 * 60 to 1,          // 08:00 → node 1
            10 * 60 + 10 to 3,    // 10:10 → node 3
            14 * 60 to 5,         // 14:00 → node 5
            16 * 60 + 10 to 7,    // 16:10 → node 7
            19 * 60 to 9,         // 19:00 → node 9
        )
        fun nodeOf(mins: Int): Int? {
            for ((slotMins, node) in slots) {
                if (kotlin.math.abs(mins - slotMins) <= 10) return node
            }
            return null
        }
        val startNode = nodeOf(startMins) ?: return null
        val endNode = (nodeOf(endMins) ?: (startNode + 1)).coerceAtLeast(startNode)
        return startNode to endNode
    }

    override fun confidence(): Int {
        if (source.isBlank()) return 0
        return runCatching {
            val root = json.parseToJsonElement(source).jsonObject
            if (root["studentTableVm"] is JsonObject) 90 else 0
        }.getOrDefault(0)
    }

    override fun matchedFeatures(): List<String> {
        val out = mutableListOf<String>()
        if (runCatching { json.parseToJsonElement(source).jsonObject["studentTableVm"] is JsonObject }
                .getOrDefault(false)) {
            out += "studentTableVm"
        }
        return out
    }
}

private fun JsonObject.str(key: String): String =
    runCatching { (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty() }
        .getOrDefault("")
