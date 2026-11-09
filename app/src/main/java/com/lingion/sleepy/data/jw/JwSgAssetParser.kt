package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject

/**
 * sg_asset 通路解析器 — 上游协议形态: shiguang_warehouse (MIT, 星河欲转)。
 *
 * 拾光适配脚本 (window.shiguangBridgePromise.saveImportedCourses) 提交的是
 * 课程 JSON 数组字符串, 字段合同见 vendor 仓 scripts/types/shiguang-bridge.d.ts:
 *   name / teacher / position / day(1-7) / startSection / endSection /
 *   weeks[int] / isCustomTime / customStartTime / customEndTime / credit
 *
 * 本类是唯一解码落点 (跨语言 invariant: shim JS 只做传输, 不解析课程)。
 * 映射规则:
 *   - day → day; startSection/endSection → startNode/endNode
 *   - weeks 集合仅压缩无缺口的区间，不补充原文不存在的周次。
 *   - 自定义时间保留 HH:mm。未提供节次时使用占位节次，显示时按作息表映射。
 *   - 无效星期、节次、时间区间或空周次跳过。
 */
class JwSgAssetParser(source: String) : JwParser(source) {

    override fun generateCourseList(): List<JwCourse> {
        val arr = try {
            JSONArray(source.trim())
        } catch (_: Exception) {
            return emptyList()
        }
        val out = mutableListOf<JwCourse>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val name = item.optString("name").trim()
            if (name.isEmpty()) continue
            val customTime = item.optBoolean("isCustomTime", false)
            val startTime = if (customTime) item.optString("customStartTime").trim() else ""
            val endTime = if (customTime) item.optString("customEndTime").trim() else ""
            if (customTime && !JwCourse.isValidTimeRange(startTime, endTime)) continue
            val day = item.optInt("day", 0)
            val ps = item.optInt("startSection", if (customTime) 1 else 0)
            val pe = item.optInt("endSection", if (customTime) ps else 0)
            if (day !in 1..7) continue
            if (ps <= 0 || pe <= 0 || pe < ps) continue
            val weeks = item.optJSONArray("weeks")?.let { w ->
                (0 until w.length()).mapNotNull { j -> w.optInt(j, -1).takeIf { it > 0 } }
            }.orEmpty().distinct().sorted()
            if (weeks.isEmpty()) continue
            out += toCourses(name, item.optString("teacher"), item.optString("position"), day, ps, pe, weeks)
                .map { it.copy(startTime = startTime, endTime = endTime) }
        }
        return out
    }

    private fun toCourses(
        name: String, teacher: String, room: String,
        day: Int, ps: Int, pe: Int, weeks: List<Int>,
    ): List<JwCourse> {
        val start = weeks.first()
        val end = weeks.last()
        return when {
            weeks.size == end - start + 1 -> listOf(course(name, teacher, room, day, ps, pe, start, end, 0))
            weeks.size == (end - start) / 2 + 1 && weeks.all { it % 2 == 1 } ->
                listOf(course(name, teacher, room, day, ps, pe, start, end, 1))
            weeks.size == (end - start) / 2 + 1 && weeks.all { it % 2 == 0 } ->
                listOf(course(name, teacher, room, day, ps, pe, start, end, 2))
            else -> contiguousRuns(weeks).map { (a, b) -> course(name, teacher, room, day, ps, pe, a, b, 0) }
        }
    }

    private fun contiguousRuns(weeks: List<Int>): List<Pair<Int, Int>> {
        val runs = mutableListOf<Pair<Int, Int>>()
        var runStart = weeks.first()
        var runEnd = runStart
        for (w in weeks.drop(1)) {
            if (w == runEnd + 1) {
                runEnd = w
            } else {
                runs += runStart to runEnd
                runStart = w; runEnd = w
            }
        }
        runs += runStart to runEnd
        return runs
    }

    private fun course(
        name: String, teacher: String, room: String,
        day: Int, ps: Int, pe: Int, a: Int, b: Int, type: Int,
    ) = JwCourse(
        name = name,
        room = room.trim(),
        teacher = teacher.trim(),
        day = day,
        startNode = ps,
        endNode = pe,
        startWeek = a,
        endWeek = b,
        type = type,
    )

    override fun confidence(): Int =
        if (source.trim().startsWith("[") && source.contains("\"day\"") && source.contains("\"weeks\"")) 90 else 0

    override fun matchedFeatures(): List<String> =
        if (confidence() >= 80) listOf("sg-asset-courses-json") else emptyList()
}
