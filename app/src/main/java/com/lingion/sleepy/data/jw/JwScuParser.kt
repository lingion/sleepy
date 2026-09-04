package com.lingion.sleepy.data.jw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 四川大学 (SCU) 自建门户教务 mobile JSON 解析器。
 *
 * 数据源: scu.edu.cn 移动端 (用户粘 JSON 后 fetch) — 路径 x.dateList[0].selectCourseList[]。
 * 每课程下挂 timeAndPlaceList[] (一个课程可能多次出现在不同时间地点)。
 * 字段集 (timeAndPlace): {classroomName, teachingBuildingName, weekDescription,
 *                            classSessions, continuingSession, classDay, campusName,
 *                            coureNumber, coureSequenceNumber, executiveEducationPlanNumber}
 *
 * 上游协议形态参考: Z-P-J/ScuTimetable (无 license) TimetableHelper.java
 * (https://github.com/Z-P-J/ScuTimetable/blob/master/app/src/main/java/com/scu/timetable/utils/TimetableHelper.java)
 * weekDescription 解析逻辑 (replaceAll 非数字/横/逗) 与 day 0-indexed → 1-indexed 转换参考,
 * 代码自写, 仅复用字段映射。
 *
 * v1 限制 (上游协议缺陷):
 *   - weekDescription 走 replaceAll("[^\\d\\-\\,]", "") 剥 "周(单)/(双)" 等中文后缀,
 *     单/双周信息**协议层丢失**, 本 parser 强制 type=0 (与 EAMS5 v1 一致, KDoc 留口)。
 *   - upstream day 转换有 `if day==8 day=1` (raw 7 → 1) 罕见死路, 本 parser 不复刻 (按 0..6 → 1..7)。
 */
class JwScuParser(source: String) : JwParser(source) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun generateCourseList(): List<JwCourse> {
        val root = runCatching { json.parseToJsonElement(source).jsonObject }
            .getOrNull() ?: return emptyList()
        val dateList = root["dateList"] as? JsonArray ?: return emptyList()
        val first = dateList.firstOrNull() as? JsonObject ?: return emptyList()
        val selectCourseList = first["selectCourseList"] as? JsonArray ?: return emptyList()
        val out = mutableListOf<JwCourse>()
        for (el in selectCourseList) {
            val course = el as? JsonObject ?: continue
            val name = course.str("courseName")
            if (name.isEmpty()) continue
            val teacher = course.str("attendClassTeacher")
            val timeAndPlaceList = course["timeAndPlaceList"] as? JsonArray ?: continue
            for (tpEl in timeAndPlaceList) {
                val tp = tpEl as? JsonObject ?: continue
                val building = tp.str("teachingBuildingName")
                val classroom = tp.str("classroomName")
                val room = building + classroom
                val weekDesc = tp.str("weekDescription")
                val startNode = tp.str("classSessions").toIntOrNull() ?: continue
                val continuing = tp.str("continuingSession").toIntOrNull() ?: 1
                val endNode = startNode + continuing - 1
                val rawDay = tp.str("classDay").toIntOrNull() ?: continue
                val day = rawDay + 1  // 0-indexed Mon..Sun → 1..7
                val weeks = parseWeeks(weekDesc)
                for ((sw, ew) in weeks) {
                    // Sleepy 语义保留 range: "1-16周" → startWeek=1, endWeek=16 (1 entry);
                    // 离散周 "2,4,6,8" → 4 entries (每个 sw=ew=week 单周范围)。
                    out += JwCourse(
                        name = name,
                        room = room,
                        teacher = teacher,
                        day = day,
                        startNode = startNode,
                        endNode = endNode,
                        startWeek = sw,
                        endWeek = ew,
                        type = 0,
                    )
                }
            }
        }
        return out
    }

    /**
     * 解析 weekDescription — 形如 "1-16周" / "2,4,6,8,10,12,14,16" / "1-15周(单)"。
     * 严格按上游 replaceAll("[^\\d\\-\\,]", "") 协议: 剥非数字/横/逗后展开。
     * 单/双周信息丢失, 按每周 (type=0) 输出。
     */
    internal fun parseWeeks(s: String): List<Pair<Int, Int>> {
        if (s.isBlank()) return emptyList()
        val clean = s.replace(Regex("[^\\d\\-,]"), "")
        if (clean.isBlank()) return emptyList()
        val out = mutableListOf<Pair<Int, Int>>()
        for (seg in clean.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
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
            val dl = root["dateList"] as? JsonArray
            if (dl != null && dl.isNotEmpty()) {
                val first = dl[0] as? JsonObject
                if ((first?.get("selectCourseList")) is JsonArray) 90 else 0
            } else 0
        }.getOrDefault(0)
    }

    override fun matchedFeatures(): List<String> {
        val out = mutableListOf<String>()
        val ok = runCatching {
            val root = json.parseToJsonElement(source).jsonObject
            val dl = root["dateList"] as? JsonArray
            dl != null && dl.isNotEmpty() &&
                ((dl[0] as? JsonObject)?.get("selectCourseList")) is JsonArray
        }.getOrDefault(false)
        if (ok) out += "selectCourseList"
        return out
    }
}

private fun JsonObject.str(key: String): String =
    runCatching { (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty() }
        .getOrDefault("")