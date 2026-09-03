package com.lingion.sleepy.data.jw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 重庆大学 my.cqu.edu.cn 课表 JSON 解析器。
 *
 * 适配学校：重庆大学 (my.cqu.edu.cn)。
 * 与 [JwWiseduParser] 同类：source 不是 HTML，而是课表 API 的 JSON 响应
 * （在 WebView 内通过 fetch 拿到，见 JwWebViewLoginScreen 的 CQU 分支）。
 *
 * 数据来源：POST /api/timetable/class/timetable/student/my-table-detail?sessionId=…
 *   headers: Authorization: Bearer <cqu_edu_ACCESS_TOKEN>（localStorage 取）
 *   body: JSON 数组 [学号]
 * 返回结构：{"classTimetableVOList":[{…}]}
 *
 * 字段映射（教务 → JwCourse）：
 *   courseName   课程名   → name
 *   instructorName "张三-数学与统计学院" → teacher（取首个 '-' 前段，多段职称/院系截断）
 *   position ?: roomName  → room
 *   weekDay      星期(1=周一..7=周日) → day
 *   periodFormat "3-4" / "5" → startNode/endNode
 *   teachingWeek "111…0" bitmap → 周次段（与金智 SKZC 同一套压缩规则）
 *   wholeWeekOccupy=true（军训/实习整周占）→ 照常按 periodFormat 输出节次段
 *
 * 外部佐证：时光课程表 cqu.js（茵符草）、321CQU/pymycqu course_timetable.py。
 */
class JwCquParser(source: String) : JwParser(source) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun generateCourseList(): List<JwCourse> {
        val rows = runCatching {
            json.parseToJsonElement(source).jsonObject["classTimetableVOList"]?.jsonArray
        }.getOrNull() ?: return emptyList()

        val result = mutableListOf<JwCourse>()
        for (el in rows) {
            val o = runCatching { el.jsonObject }.getOrNull() ?: continue
            fun str(k: String): String =
                o[k]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.trim().orEmpty()
            fun int(k: String): Int? = str(k).toIntOrNull()

            val name = str("courseName")
            if (name.isBlank()) continue

            // instructorName "张三-数学与统计学院-教授" → "张三"（首个 '-' 前段；空/null → ""）
            val teacherRaw = str("instructorName")
            val teacher = if (teacherRaw.isBlank()) "" else teacherRaw.substringBefore('-').trim()

            // position 优先（我的课表常填），缺位回退 roomName；两者皆 null → ""
            val room = str("position").ifBlank { str("roomName") }

            val day = int("weekDay") ?: continue

            // periodFormat "3-4" / "5"（单节起止相同）；非数字整体 → 丢弃该行
            val pf = str("periodFormat")
            val startNode = if (pf.contains('-')) pf.substringBefore('-').trim().toIntOrNull() ?: continue
            else pf.trim().toIntOrNull() ?: continue
            val endNode = if (pf.contains('-')) pf.substringAfter('-').trim().toIntOrNull() ?: startNode
            else startNode

            for ((sw, ew, type) in weekRuns(str("teachingWeek"))) {
                result += JwCourse(
                    name = name,
                    room = room,
                    teacher = teacher,
                    day = day.coerceIn(1, 7),
                    startNode = startNode.coerceAtLeast(1),
                    endNode = endNode.coerceAtLeast(startNode),
                    startWeek = sw,
                    endWeek = ew,
                    type = type
                )
            }
        }
        return result
    }

    /**
     * teachingWeek 周次 bitmap → 连续段列表 [(startWeek, endWeek, type)]。
     * 与 [JwWiseduParser] SKZC 同一套压缩规则（单段 0=每周；整体 step=2 → 1=单周/2=双周；否则拆段）。
     */
    internal fun weekRuns(bitmap: String): List<Triple<Int, Int, Int>> {
        val weeks = bitmap.mapIndexedNotNull { i, c -> if (c == '1') i + 1 else null }
        if (weeks.isEmpty()) return emptyList()

        val runs = mutableListOf<Pair<Int, Int>>()
        var start = weeks[0]
        var prev = weeks[0]
        for (w in weeks.drop(1)) {
            if (w == prev + 1) {
                prev = w
            } else {
                runs += start to prev
                start = w
                prev = w
            }
        }
        runs += start to prev

        if (runs.size == 1) {
            return listOf(Triple(runs[0].first, runs[0].second, 0))
        }
        if (weeks.size >= 2 && (1 until weeks.size).all { weeks[it] - weeks[it - 1] == 2 }) {
            val type = if (weeks.first() % 2 == 1) 1 else 2
            return listOf(Triple(weeks.first(), weeks.last(), type))
        }
        return runs.map { Triple(it.first, it.second, 0) }
    }

    /** my-table-detail = 100; classTimetableVOList = 90 */
    override fun confidence(): Int = when {
        source.contains("classTimetableVOList") -> 90
        source.contains("my-table-detail") -> 80
        else -> 0
    }

    override fun matchedFeatures(): List<String> = buildList {
        if (source.contains("classTimetableVOList")) add("classTimetableVOList.rows")
        if (source.contains("my-table-detail")) add("my-table-detail")
    }
}
