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
 * 字段集: {courseName, dayOfWeek, beginSection, endSection, weeksAndTeachers, titleDetail[], placeName}
 *   weeksAndTeachers: "周数串/老师[主讲]" 形式
 *   titleDetail[0]: 汇总字符串 (无 location 信息)
 *   titleDetail[1..]: "周数串 教室" 形式, 按空格 split 末段为 location
 *   实验课 ([实] 前缀): 教师取 titleDetail[1] 第二段, 地点取 placeName 首段
 *
 * 上游协议形态参考: CreamPig233/neu_wisedu2wakeup (无 license) extract_schedule.js
 * (https://github.com/CreamPig233/neu_wisedu2wakeup/blob/master/extract_schedule.js)
 * teacher 提取规则 (split "/", 取末段剥 [主讲]) 与 weeksAndTeachers 解析参考,
 * 代码自写, 仅复用字段映射。
 *
 * 单/双周: 线协议 JSON 的周次串自带单/双信息 ("1-16双周" / "1-16周(双)" /
 * "3-15周（单）"), 上游 CSV 导出才 replace(/[()]/g) 剥括号丢弃 — Sleepy 不丢,
 * 保留 type=1/2 并按 Sleepy 语义做端点修正 (单周奇数化/双周偶数化)。
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
            val titleDetail = obj["titleDetail"] as? JsonArray
            val isLab = name.startsWith("[实]")
            val entries = if (isLab) {
                // 实验课的接口结构不同：脚本从 titleDetail[1] 取教师，
                // 从 placeName 取地点，并从 weeksAndTeachers 中剥出周次。
                listOf(extractLabWeeksAndRoom(obj, weeksAndTeachers))
            } else {
                // 普通课程每一条 titleDetail 明细都是独立的周次/地点安排。
                extractWeeksAndRooms(titleDetail, weeksAndTeachers)
            }
            val teacher = if (isLab) {
                extractLabTeacher(titleDetail)
            } else {
                extractTeacher(weeksAndTeachers)
            }
            for ((weeksStr, room) in entries) {
                if (weeksStr.isEmpty()) continue
                val (parity, cleanedWeeks) = extractParity(weeksStr)
                for ((sw, ew) in parseWeeks(cleanedWeeks)) {
                    // Sleepy 语义: 单周(1)起点须奇数/双周(2)起点须偶数，
                    // 同时避免单条周次在修正后出现倒挂区间。
                    val (adjustedStart, adjustedEnd) = JwParity.adjustedRange(sw, ew, parity)
                    out += JwCourse(
                        name = name,
                        room = room,
                        teacher = teacher,
                        day = day,
                        startNode = begin,
                        endNode = end,
                        startWeek = adjustedStart,
                        endWeek = adjustedEnd,
                        type = parity,
                    )
                }
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

    /** Returns every independently scheduled week-range and room in titleDetail[1..]. */
    internal fun extractWeeksAndRooms(titleDetail: JsonArray?, weeksAndTeachers: String): List<Pair<String, String>> {
        val details = mutableListOf<Pair<String, String>>()
        if (titleDetail != null) {
            for (i in 1 until titleDetail.size) {
                val s = (titleDetail[i] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                if (s.isEmpty() || !s.first().isDigit()) continue
                val parts = s.split(" ").filter { it.isNotEmpty() }
                if (parts.size >= 2) {
                    val weeks = parts.first().trim()
                    val rawRoom = parts.last().trim()
                    val room = if (rawRoom.endsWith("校区")) "待定" else rawRoom
                    details += weeks to room
                } else {
                    details += parts.first().trim() to ""
                }
            }
        }
        if (details.isNotEmpty()) return details
        val fallbackWeeks = weeksAndTeachers.split("/").firstOrNull()?.trim().orEmpty()
        return listOf(fallbackWeeks to "")
    }

    /** 实验课的教师规则与上游脚本保持一致：titleDetail[1] 的第二个空白字段。 */
    private fun extractLabTeacher(titleDetail: JsonArray?): String {
        val detail = (titleDetail?.getOrNull(1) as? JsonPrimitive)
            ?.contentOrNull?.trim().orEmpty()
        return detail.split(Regex("\\s+")).getOrNull(1).orEmpty()
    }

    /** 实验课：weeksAndTeachers 取 '[' 前内容，地点取 placeName 的首段。 */
    private fun extractLabWeeksAndRoom(
        obj: JsonObject,
        weeksAndTeachers: String,
    ): Pair<String, String> {
        val rawPlace = obj.str("placeName")
        val place = rawPlace.split(Regex("\\s+")).firstOrNull().orEmpty()
        val room = if (place.endsWith(")")) "暂未安排教室" else place
        val weeks = weeksAndTeachers.substringBefore("[").trim()
        return weeks to room
    }

    /**
     * 提取单/双周限定并剥净: 返回 (parity, 已剥限定词与括号的串)。
     * parity: 0=每周 1=单周 2=双周。线协议形态 "1-16双周" / "1-16周(双)" /
     * "3-15周（单）" / "2-15单周" — ASCII/全角括号与"单/双"标记全部剥除。
     */
    internal fun extractParity(s: String): Pair<Int, String> {
        val parity = when {
            "单" in s -> 1
            "双" in s -> 2
            else -> 0
        }
        val clean = s.replace("（", "(").replace("）", ")")
            .replace("(单)", "").replace("(双)", "")
        return parity to clean
    }

    /**
     * 解析周次串 (已剥单/双标记与括号): "1-16周" / "2,4,6,8"。
     * 形如 "X-Y周" / "X,Y,Z周"。单/双限定由 [extractParity] 先行剥离。
     */
    internal fun parseWeeks(s: String): List<Pair<Int, Int>> {
        if (s.isBlank()) return emptyList()
        val clean = s.replace("周", "").replace("单周", "").replace("双周", "")
            .replace("单", "").replace("双", "").trim()
        if (clean.isBlank()) return emptyList()
        val out = mutableListOf<Pair<Int, Int>>()
        for (seg in clean.split(",", "，", "、").map { it.trim() }.filter { it.isNotEmpty() }) {
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
