package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject

/**
 * 新版 URP 教务系统解析器。
 *
 * URP 综合教务（新正方/URP/wisedu 等厂商）登录后，课表页用 JS 渲染，
 * 课表数据从后端 `/jwglxt/kbcx/xskbcx` 之类 API 拿 JSON 注入页面。
 *
 * 数据结构（wakeup NewUrpParser 协议）：
 * ```
 * {
 *   "dateList": [
 *     {
 *       "selectCourseList": [
 *         {
 *           "courseName": "...",
 *           "attendClassTeacher": "...",
 *           "timeAndPlaceList": [
 *             {
 *               "classDay": 1,
 *               "classSessions": 3,
 *               "continuingSession": 2,
 *               "classWeek": "11111111111111111111"  // 20 位 0/1，第 i 位 = 是否第 i 周
 *               "campusName": "...",
 *               "teachingBuildingName": "...",
 *               "classroomName": "..."
 *             }
 *           ]
 *         }
 *       ]
 *     }
 *   ]
 * }
 * ```
 *
 * 注意：HTML 源码里这段 JSON 通常被嵌入到某个 <script> 标签的赋值里，
 * 形如 `var kbxx_json = {...};`。需要从 HTML 里把它抠出来再 JSON.parse。
 */
class JwNewUrpParser(source: String) : JwParser(source) {

    override fun generateCourseList(): List<JwCourse> {
        val result = arrayListOf<JwCourse>()

        // 1. 从 HTML 里抠出 JSON 字符串
        val jsonText = extractJsonFromHtml(source) ?: return result

        // 2. 解析 JSON
        val root = try {
            JSONObject(jsonText)
        } catch (e: Exception) {
            return result
        }
        val dateList = root.optJSONArray("dateList") ?: return result
        if (dateList.length() == 0) return result

        val firstDate = dateList.getJSONObject(0)
        val courseList = firstDate.optJSONArray("selectCourseList") ?: return result

        for (i in 0 until courseList.length()) {
            val course = courseList.getJSONObject(i)
            val name = course.optString("courseName", "").trim()
            val teacher = course.optString("attendClassTeacher", "").trim()
            val timeAndPlaceList = course.optJSONArray("timeAndPlaceList") ?: continue

            for (j in 0 until timeAndPlaceList.length()) {
                val tp = timeAndPlaceList.getJSONObject(j)
                val day = tp.optInt("classDay", 1)
                val startNode = tp.optInt("classSessions", 1)
                val continuing = tp.optInt("continuingSession", 1)
                val endNode = startNode + continuing - 1
                val classWeek = tp.optString("classWeek", "")
                val campus = tp.optString("campusName", "")
                val building = tp.optString("teachingBuildingName", "")
                val room = tp.optString("classroomName", "")
                val fullRoom = (campus + building + room).trim()

                // 解析 classWeek: 字符串每位 0/1，从左到右对应第 1..N 周
                val weekBits = parseWeekBits(classWeek)
                if (weekBits.isEmpty()) continue

                // 用 wakeup 的 weekIntList2WeekBeanList 思路归并成 (start, end, type) 范围
                val ranges = weekBitsToRanges(weekBits)
                for (r in ranges) {
                    result += JwCourse(
                        name = name,
                        room = fullRoom,
                        teacher = teacher,
                        day = day,
                        startNode = startNode,
                        endNode = endNode,
                        startWeek = r.first,
                        endWeek = r.second,
                        type = r.third
                    )
                }
            }
        }
        return result
    }

    /**
     * 从 HTML 源码里抠 JSON
     * 多种常见嵌入方式：
     *   1. var xxx = {...};
     *   2. <script>...{...}...</script>
     *   3. window.xxx = {...};
     * 启发式：找 `dateList` 关键字，截取最大合法 JSON
     */
    fun extractJsonForTest(html: String): String? = extractJsonFromHtml(html)

    private fun extractJsonFromHtml(html: String): String? {
        // 找包含 "dateList" 那一段
        val marker = "dateList"
        val idx = html.indexOf(marker)
        if (idx < 0) return null

        // 往前找最近的 '{'（JSON 开始）
        var start = idx
        while (start > 0 && html[start] != '{') start--
        if (html[start] != '{') return null

        // 往后数括号配对，找配对的 '}'
        var depth = 0
        var inString = false
        var escape = false
        var end = start
        for (i in start until html.length) {
            val c = html[i]
            if (escape) { escape = false; continue }
            if (c == '\\') { escape = true; continue }
            if (c == '"' && !escape) { inString = !inString; continue }
            if (inString) continue
            when (c) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) { end = i; break } }
            }
        }
        if (depth != 0) return null
        return html.substring(start, end + 1)
    }

    private fun parseWeekBits(s: String): List<Int> {
        val out = arrayListOf<Int>()
        for (i in s.indices) {
            if (s[i] == '1') out.add(i + 1)
        }
        return out
    }

    /**
     * 把周次数组归并成 (start, end, type) 范围
     * type: 0=每周 1=单周 2=双周
     */
    private fun weekBitsToRanges(weeks: List<Int>): List<Triple<Int, Int, Int>> {
        if (weeks.isEmpty()) return emptyList()
        val result = mutableListOf<Triple<Int, Int, Int>>()
        var i = 0
        while (i < weeks.size) {
            val start = weeks[i]
            var end = start
            var step = 1
            // 探测步长：看后一个是不是 start+1（每周）或 start+2（单/双）
            if (i + 1 < weeks.size) {
                val gap = weeks[i + 1] - start
                when (gap) {
                    1 -> { step = 1; end = weeks[i + 1]; var k = i + 1; while (k + 1 < weeks.size && weeks[k + 1] - weeks[k] == 1) { k++; end = weeks[k] }; i = k + 1 }
                    2 -> {
                        // 奇数开头→单周；偶数开头→双周
                        step = 2
                        var k = i + 1
                        while (k + 1 < weeks.size && weeks[k + 1] - weeks[k] == 2) { k++; end = weeks[k] }
                        // 把 end 拓展到这一段最后一个连续 2-step 的位置
                        // 实际：如果 start=1,end=7 步长2 覆盖 1,3,5,7
                        val type = if (start % 2 != 0) 1 else 2
                        i = k + 1
                        result += Triple(start, end, type)
                        continue
                    }
                    else -> { i++; result += Triple(start, end, 0); continue }
                }
                val type = if (step == 1) 0 else if (start % 2 != 0) 1 else 2
                result += Triple(start, end, type)
            } else {
                result += Triple(start, end, 0)
                i++
            }
        }
        return result
    }
}
