package com.lingion.sleepy.data.jw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 东北大学 (NEU) 强智新版教务 mobile JSON 解析器。
 *
 * 数据源: jwxt.neu.edu.cn mobile 接口
 *   /jwapp/sys/homeapp/api/home/student/getMyScheduleDetail.do
 * JSON 路径: x.datas.arrangedList[]
 * 字段集: {courseName, dayOfWeek, beginSection, endSection, weeksAndTeachers, titleDetail[]}
 *   weeksAndTeachers: "周数串/老师[主讲]" 形式
 *   titleDetail[0]: 汇总字符串 (无 location 信息)
 *   titleDetail[1..]: "周数串 教室" 形式, 按空格 split 末段为 location
 *
 * 上游协议形态参考: CreamPig233/neu_wisedu2wakeup (无 license) extract_schedule.js
 * (https://github.com/CreamPig233/neu_wisedu2wakeup/blob/master/extract_schedule.js)
 * teacher 提取规则 (split "/", 取末段剥 [主讲]) 与 weeksAndTeachers 解析参考,
 * 代码自写, 仅复用字段映射。
 *
 * v1 限制 (上游协议缺陷):
 *   - upstream 协议层丢失单/双信息 (replace /[()]/g 剥中文括号),
 *     本 parser 强制 type=0, 但端点修正仍按 Sleepy 现有语义 (上游文本剥离后
 *     "1-16双周" 退化为 "1-16周", 视为每周 type=0)。
 */
class JwNeuParser(source: String) : JwParser(source) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun generateCourseList(): List<JwCourse> {
        val root = runCatching { json.parseToJsonElement(source).jsonObject }
            .getOrNull() ?: return emptyList()
        val datas = root["datas"] as? JsonObject ?: return emptyList()
        val arranged = datas["arrangedList"] as? JsonArray ?: return emptyList()
        val out = mutableListOf<JwCourse>()
        for (el in arranged) {
            val obj = el as? JsonObject ?: continue
            val name = obj.str("courseName")
            if (name.isEmpty()) continue
            val day = obj.str("dayOfWeek").toIntOrNull() ?: continue
            val begin = obj.str("beginSection").toIntOrNull() ?: continue
            val end = obj.str("endSection").toIntOrNull() ?: begin
            val weeksAndTeachers = obj.str("weeksAndTeachers")
            val teacher = extractTeacher(weeksAndTeachers)
            // 优先从 titleDetail[1..] 拿 location; 无则空串
            val titleDetail = obj["titleDetail"] as? JsonArray
            val (weeksStr, room) = extractWeeksAndRoom(titleDetail, weeksAndTeachers)
            if (weeksStr.isEmpty()) continue
            for ((sw, ew) in parseWeeks(weeksStr)) {
                out += JwCourse(
                    name = name,
                    room = room,
                    teacher = teacher,
                    day = day,
                    startNode = begin,
                    endNode = end,
                    startWeek = sw,
                    endWeek = ew,
                    type = 0,
                )
            }
        }
        return out
    }

    /**
     * 从 "1-16周/王教授[主讲]" 提取 teacher: 取 "/" 末段, 剥 [主讲] 标记。
     * 若无 "/" 或剥离后为空, 返回空串。
     */
    internal fun extractTeacher(s: String): String {
        if (s.isBlank()) return ""
        val parts = s.split("/").map { it.trim() }
        val last = parts.lastOrNull() ?: return ""
        return last.replace("[主讲]", "").replace("[主讲 ", "").trim()
    }

    /**
     * 从 titleDetail 提取 (weeksStr, room):
     *   - 若 titleDetail 有 >= 2 条且 [1..] 中有以数字开头的, 取首条 split " " 第一段作 weeks, 末段作 room
     *   - 否则回退到 weeksAndTeachers "/" 前段作 weeks, room 空串
     */
    internal fun extractWeeksAndRoom(titleDetail: JsonArray?, weeksAndTeachers: String): Pair<String, String> {
        if (titleDetail != null) {
            for (i in 1 until titleDetail.size) {
                val s = (titleDetail[i] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                if (s.isEmpty() || !s.first().isDigit()) continue
                val parts = s.split(" ").filter { it.isNotEmpty() }
                if (parts.size >= 2) {
                    val weeks = parts.first().trim()
                    val room = parts.last().trim()
                    if (room.endsWith("校区")) continue  // upstream 视 "X校区" 为待定, 跳过取下一条
                    return weeks to room
                } else if (parts.size == 1) {
                    return parts.first().trim() to ""
                }
            }
        }
        // 回退: weeksAndTeachers "/" 前段
        val fallbackWeeks = weeksAndTeachers.split("/").firstOrNull()?.trim().orEmpty()
        return fallbackWeeks to ""
    }

    /**
     * 解析周次串 (已剥中文括号): "1-16周" / "2,4,6,8" / "2-15单周" (剥"(单)"后剩"2-15单周")
     * 形如 "X-Y周" / "X-Y单周" / "X-Y双周" / "X,Y,Z周"。
     * 单/双信息 upstream 已丢失, type 强制 0; 端点修正仍按 weekly 语义 (无意义, 不动)。
     */
    internal fun parseWeeks(s: String): List<Pair<Int, Int>> {
        if (s.isBlank()) return emptyList()
        val clean = s.replace("周", "").replace("单周", "").replace("双周", "")
            .replace("单", "").replace("双", "").trim()
        if (clean.isBlank()) return emptyList()
        val out = mutableListOf<Pair<Int, Int>>()
        for (seg in clean.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }) {
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

    override fun confidence(): Int {
        if (source.isBlank()) return 0
        return runCatching {
            val root = json.parseToJsonElement(source).jsonObject
            val datas = root["datas"] as? JsonObject
            if ((datas?.get("arrangedList")) is JsonArray) 90 else 0
        }.getOrDefault(0)
    }

    override fun matchedFeatures(): List<String> {
        val out = mutableListOf<String>()
        val ok = runCatching {
            val root = json.parseToJsonElement(source).jsonObject
            val datas = root["datas"] as? JsonObject
            (datas?.get("arrangedList")) is JsonArray
        }.getOrDefault(false)
        if (ok) out += "arrangedList"
        return out
    }
}

private fun JsonObject.str(key: String): String =
    runCatching { (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty() }
        .getOrDefault("")