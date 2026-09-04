package com.lingion.sleepy.data.jw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 浙江大学正方新版 UGR 本研课表 JSON 解析器。
 *
 * 数据源: zdbk.zju.edu.cn /classroom-web/classroom/searchTimetable 返回 kbList 数组。
 * 字段集: {xkkh, xqj, dsz, djj, skcd, kcb, xxq} — kcb 是 "课名<br>周次串<br>老师<br>教室"
 *         形式拼接的 HTML 片段, dsz="0"单/"1"双/"2"每周, djj=起始节, skcd=节次长度。
 *
 * 上游协议形态参考: Xecades/zju-ical-py (LGPL-2.1) course/ugrs_course.py
 * (https://github.com/Xecades/zju-ical-py/blob/master/course/ugrs_course.py)
 * 解析逻辑代码自写, 仅复用字段映射 (xkkh/xqj/dsz/djj/skcd/kcb/xxq) 与 kcb
 * 拆分方式 (zwf 截断 + <br> 分段)。
 */
class JwZjuParser(source: String) : JwParser(source) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun generateCourseList(): List<JwCourse> {
        val root = runCatching { json.parseToJsonElement(source).jsonObject }
            .getOrNull() ?: return emptyList()
        val kbList = root["kbList"] as? JsonArray ?: return emptyList()
        val out = mutableListOf<JwCourse>()
        for (el in kbList) {
            val obj = el as? JsonObject ?: continue
            val xqj = obj.str("xqj").toIntOrNull() ?: continue
            val dsz = obj.str("dsz")
            val typeInt = when (dsz) {
                "0" -> 1
                "1" -> 2
                else -> 0
            }
            val djj = obj.str("djj").toIntOrNull() ?: continue
            val skcd = obj.str("skcd").toIntOrNull() ?: 1
            val rawKcb = obj.str("kcb")
            val kcb = rawKcb.split("zwf")[0].split("<br>")
            if (kcb.isEmpty()) continue
            val name = kcb.getOrNull(0)?.replace("(", "（")?.replace(")", "）")?.trim() ?: continue
            val timeString = kcb.getOrNull(1)?.trim().orEmpty()
            val teacher = kcb.getOrNull(2)?.trim().orEmpty()
            val location = kcb.getOrNull(3)?.trim().orEmpty()
            val segments = parseWeekSegments(timeString)
            for ((startW, endW) in segments) {
                val adjustedStart = when (typeInt) {
                    1 -> if (startW % 2 == 0) startW + 1 else startW
                    2 -> if (startW % 2 != 0) startW + 1 else startW
                    else -> startW
                }
                out += JwCourse(
                    name = name,
                    room = location,
                    teacher = teacher,
                    day = xqj,
                    startNode = djj,
                    endNode = djj + skcd - 1,
                    startWeek = adjustedStart,
                    endWeek = endW,
                    type = typeInt,
                )
            }
        }
        return out
    }

    /**
     * 解析 "周次串" 字段。形态样例:
     *   "1-16周"         → [(1, 16)]
     *   "2-15周(单)"     → [(2, 15)]
     *   "1-16周(双)"     → [(1, 16)]
     *   "2,4,6,8,10,12,14,16周" → [(2,2),(4,4),...,(16,16)]
     *   "1-8周,10-16周"  → [(1,8),(10,16)]
     * 周数串必带"周"后缀, 端点本身已定型, 不动调整 (单/双周端点修正交 generateCourseList 负责)。
     */
    internal fun parseWeekSegments(s: String): List<Pair<Int, Int>> {
        if (s.isBlank()) return emptyList()
        val clean = s.replace("周", "").replace("(单)", "").replace("(双)", "").trim()
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
            if (root["kbList"] is JsonArray) 90 else 0
        }.getOrDefault(0)
    }

    override fun matchedFeatures(): List<String> {
        val out = mutableListOf<String>()
        if (runCatching { json.parseToJsonElement(source).jsonObject["kbList"] is JsonArray }
                .getOrDefault(false)) {
            out += "kbList"
        }
        return out
    }
}

private fun JsonObject.str(key: String): String =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.trim().orEmpty()
