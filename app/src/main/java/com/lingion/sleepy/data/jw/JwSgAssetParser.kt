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
 *   - weeks 集合: 全奇 → type=1, 全偶 → type=2, 其余按连续区间拆分 type=0
 *   - isCustomTime=true 或节次缺失/颠倒/空周次 → 跳过该条 (不崩、不假装支持)
 *   - credit/自定义时间不映射 (JwCourse 无对应字段)
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
            if (item.optBoolean("isCustomTime", false)) continue
            val day = item.optInt("day", 0)
            val ps = item.optInt("startSection", 0)
            val pe = item.optInt("endSection", 0)
            if (day !in 1..7) continue
            if (ps <= 0 || pe <= 0 || pe < ps) continue
            val weeks = item.optJSONArray("weeks")?.let { w ->
                (0 until w.length()).mapNotNull { j -> w.optInt(j, -1).takeIf { it > 0 } }
            }.orEmpty().distinct().sorted()
            if (weeks.isEmpty()) continue
            out += toCourses(name, item.optString("teacher"), item.optString("position"), day, ps, pe, weeks)
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
            weeks.all { it % 2 == 1 } -> listOf(course(name, teacher, room, day, ps, pe, start, end, 1))
            weeks.all { it % 2 == 0 } -> listOf(course(name, teacher, room, day, ps, pe, start, end, 2))
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
