package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject

/**
 * WakeUp `NewUrpSuperParser` 第二变体 (smali 类名 `o0O0o`).
 *
 * 协议特征：
 *  1. 顶层是 JSON **数组** (与第一变体 `o0` 的 `dateList[].selectCourseList[].timeAndPlaceList[]` 嵌套结构互斥),
 *  2. 每项 `NewUrpClassListItem` 含:
 *     - `kcm`: 课名
 *     - `jsm`: 教师
 *     - `jxlm` + `jasm`: 教学楼 + 教室 (拼成 room)
 *     - `cxjc`: 持续节次数 (endSection = skjc + cxjc - 1)
 *     - `id.skxq`: 星期 1..7
 *     - `id.skjc`: 起始节
 *     - `id.skzc`: 周次位图字符串 ('0'/'1' 字符序列, 长度 = 学期周数)
 *  3. 周次位图按 1-based 索引: 第 i 位 (i=1..N) = 是否第 i 周上课.
 *
 * WakeUp `NewUrpSuperParser.OooO0o0` 串联调用两个子 parser:
 *    1. `o0` (dateList/selectCourseList/timeAndPlaceList) - Sleepy 已有 `JwNewUrpParser`
 *    2. `o0O0o` (本 parser, flat array + skzc bitmap)
 * 失败回退: 两者任一成功即返回结果; 都失败抛 Exception.
 *
 * 真源: `tools/reverse/wakeup-fresh/smali_classes4/com/suda/yzune/wakeupschedule/schedule_parser/parser/o0O0o.smali`
 * 反编译: `NewUrpClassListItem` (`kcm`, `jsm`, `jxlm`, `jasm`, `cxjc`, `id: {skxq, skjc, skzc, ...}`)
 *
 * 适配 (2026-10-07):
 *  - 从源文本里抠出顶层 JSON 数组 (类似 NewUrp 解析 `dateList` 的启发式, 但锚点改为 `id` + `skzc`)
 *  - 解析为 List<JwCourse>, 复用 JwNewUrpParser 的 `weekBitsToRanges` 把位图归并成 (start, end, type)
 */
class JwNewUrpSuperParser(source: String) : JwParser(source) {

    override fun generateCourseList(): List<JwCourse> {
        val result = arrayListOf<JwCourse>()

        // 1. 从源里抠出 JSON 数组字符串
        val jsonText = extractJsonArray(source) ?: return result

        // 2. 解析 JSON 数组
        val arr = try {
            JSONArray(jsonText)
        } catch (e: Exception) {
            return result
        }
        if (arr.length() == 0) return result

        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val name = item.optString("kcm", "").trim()
            if (name.isBlank()) continue

            val teacher = item.optString("jsm", "").trim()
            val building = item.optString("jxlm", "").trim()
            val room = item.optString("jasm", "").trim()
            val fullRoom = (building + room).trim()

            val id = item.optJSONObject("id") ?: continue
            val day = id.optInt("skxq", 0)
            if (day !in 1..7) continue

            val startNode = id.optInt("skjc", 0)
            if (startNode < 1) continue

            val continuing = item.optInt("cxjc", 1)
            if (continuing < 1) continue
            val realEndNode = startNode + continuing - 1

            val skzc = id.optString("skzc", "")
            if (skzc.isBlank()) continue

            // 解析 skzc 位图: '1' 的位置 = 上课的周
            val weekBits = parseWeekBits(skzc)
            if (weekBits.isEmpty()) continue

            val ranges = weekBitsToRanges(weekBits)
            for (r in ranges) {
                result += JwCourse(
                    name = name,
                    room = fullRoom,
                    teacher = teacher,
                    day = day,
                    startNode = startNode,
                    endNode = realEndNode,
                    startWeek = r.first,
                    endWeek = r.second,
                    type = r.third
                )
            }
        }
        return result
    }

    /**
     * 从 HTML/JSON 源里抠顶层 JSON 数组
     * 启发式: 找 `id` + `skzc` 关键字 (新 URP 数组变体的唯一锚点组合), 截取最大合法 JSON 数组.
     * 也兼容裸 JSON 数组文本.
     */
    fun extractJsonArrayForTest(source: String): String? = extractJsonArray(source)

    private fun extractJsonArray(source: String): String? {
        // 优先检测纯 JSON 数组输入 (无 HTML 包裹)
        val trimmed = source.trim()
        if (trimmed.startsWith("[")) {
            var depth = 0
            var inString = false
            var escape = false
            for (i in trimmed.indices) {
                val c = trimmed[i]
                if (escape) { escape = false; continue }
                if (c == '\\') { escape = true; continue }
                if (c == '"' && !escape) { inString = !inString; continue }
                if (inString) continue
                when (c) {
                    '[' -> depth++
                    ']' -> { depth--; if (depth == 0) return trimmed.substring(0, i + 1) }
                }
            }
            return null
        }

        // HTML 包裹: 找 'skzc' 关键字 (数组变体必有), 往前找最近的 '['
        val markerIdx = source.indexOf("\"skzc\"")
        if (markerIdx < 0) return null
        var start = markerIdx
        while (start > 0 && source[start] != '[') start--
        if (source[start] != '[') return null

        var depth = 0
        var inString = false
        var escape = false
        var end = start
        for (i in start until source.length) {
            val c = source[i]
            if (escape) { escape = false; continue }
            if (c == '\\') { escape = true; continue }
            if (c == '"' && !escape) { inString = !inString; continue }
            if (inString) continue
            when (c) {
                '[' -> depth++
                ']' -> { depth--; if (depth == 0) { end = i; break } }
            }
        }
        if (depth != 0) return null
        return source.substring(start, end + 1)
    }

    private fun parseWeekBits(s: String): List<Int> {
        val out = arrayListOf<Int>()
        for (i in s.indices) {
            if (s[i] == '1') out.add(i + 1)
        }
        return out
    }

    /**
     * 把周次数组归并成 (start, end, type) 范围 (复制自 JwNewUrpParser).
     * type: 0=每周 1=单周 2=双周
     */
    private fun weekBitsToRanges(weeks: List<Int>): List<Triple<Int, Int, Int>> {
        if (weeks.isEmpty()) return emptyList()
        val result = mutableListOf<Triple<Int, Int, Int>>()
        var i = 0
        while (i < weeks.size) {
            val start = weeks[i]
            var end = start
            if (i + 1 < weeks.size) {
                val gap = weeks[i + 1] - start
                when (gap) {
                    1 -> {
                        var k = i + 1
                        while (k + 1 < weeks.size && weeks[k + 1] - weeks[k] == 1) { k++; end = weeks[k] }
                        result += Triple(start, end, 0)
                        i = k + 1
                    }
                    2 -> {
                        var k = i + 1
                        while (k + 1 < weeks.size && weeks[k + 1] - weeks[k] == 2) { k++; end = weeks[k] }
                        val type = if (start % 2 != 0) 1 else 2
                        result += Triple(start, end, type)
                        i = k + 1
                    }
                    else -> { result += Triple(start, end, 0); i++ }
                }
            } else {
                result += Triple(start, end, 0)
                i++
            }
        }
        return result
    }

    /**
     * 置信度 (按锚点强度):
     *  - skzc + id + kcm (三件套) = 100 (变体唯一锚点组合)
     *  - skzc + id = 70 (周次位图 + 嵌套 id 对象, 强信号)
     *  - 仅 skzc = 30 (位图本身不能断言变体归属)
     */
    override fun confidence(): Int {
        val hasSkzc = source.contains("\"skzc\"")
        val hasId = source.contains("\"id\"")
        val hasKcm = source.contains("\"kcm\"")
        return when {
            hasSkzc && hasId && hasKcm -> 100
            hasSkzc && hasId -> 70
            hasSkzc -> 30
            else -> 0
        }
    }

    override fun matchedFeatures(): List<String> = buildList {
        if (source.contains("\"skzc\"")) add("skzc")
        if (source.contains("\"id\"")) add("id")
        if (source.contains("\"kcm\"")) add("kcm")
        if (source.contains("\"jxlm\"")) add("jxlm")
        if (source.contains("\"cxjc\"")) add("cxjc")
    }
}