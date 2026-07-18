package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.jsoup.Jsoup

/**
 * 正方教务（新版）课表解析器。
 *
 * 适配 zf_new 协议学校（52所）。正方新版教务（基于 SpringMVC / Vue）课表页：
 *   1. 数据可能嵌入在 `<script>` 标签 JSON 中（API 响应直出 / Vue data）
 *   2. 或渲染为标准 HTML 表格（`<div class="kbcontent">` + `<font title="...">`）
 *
 * 解析策略：JSON 优先 → HTML 表格兜底（兼容 QZ 结构 + zf_new kbgrid 结构）。
 *
 * 参考：
 *   - dIT8Zv/WakeupSchedule_BUPT NewZfParser.kt
 *   - nKEatonxuan/CourseAdapter 正方协议适配
 */
class JwNewZfParser(source: String) : JwParser(source) {

    override fun generateCourseList(): List<JwCourse> {
        // 1. 尝试从页面提取嵌入的 JSON
        parseEmbeddedJson().takeIf { it.isNotEmpty() }?.let { return it }

        // 2. HTML 表格解析
        return parseHtmlTable()
    }

    // ─── JSON 提取 ────────────────────────────────────────────

    /**
     * 从 HTML 中提取正方新版课表 JSON。
     *
     * 常见嵌入方式：
     *   - `<script>var kbxx = [...];</script>`
     *   - `<script>window.__INITIAL_STATE__ = {…"kbxx":[…]…};</script>`
     *   - API 响应直接嵌入（纯 JSON 页面 `{"kbxx":[…]}`）
     *   - `var xskbcx_json = {"tmp_list":[…]};`（部分版本）
     */
    private fun parseEmbeddedJson(): List<JwCourse> {
        // 标记关键字 → 多种正方版本的字段名
        val markers = listOf("\"kbxx\"", "\"tmp_list\"", "\"xskbcx\"", "xskbcx_json")

        for (marker in markers) {
            val idx = source.indexOf(marker)
            if (idx < 0) continue

            // 找到 marker 后的数组开始位置 '['
            var arrStart = idx + marker.length
            while (arrStart < source.length && source[arrStart] !in "[{") arrStart++
            if (arrStart >= source.length) continue

            val jsonStr = extractBalanced(source, arrStart) ?: continue
            val courses = parseCourseJsonArray(jsonStr)
            if (courses.isNotEmpty()) return courses
        }

        // 尝试纯 JSON 页面（API 响应被 WebView 直接渲染）
        val trimmed = source.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val courses = parseCourseJsonArray(trimmed)
            if (courses.isNotEmpty()) return courses
        }

        return emptyList()
    }

    /** 从 start 位置提取配对的 JSON 数组/对象（括号匹配） */
    private fun extractBalanced(s: String, start: Int): String? {
        if (start >= s.length) return null
        val open = s[start]
        val close = when (open) {
            '[' -> ']'
            '{' -> '}'
            else -> return null
        }
        var depth = 0
        var inStr = false
        var esc = false
        for (i in start until s.length) {
            val c = s[i]
            if (esc) { esc = false; continue }
            if (c == '\\') { esc = true; continue }
            if (c == '"') { inStr = !inStr; continue }
            if (inStr) continue
            when (c) {
                open -> depth++
                close -> { depth--; if (depth == 0) return s.substring(start, i + 1) }
            }
        }
        return null
    }

    private fun parseCourseJsonArray(jsonStr: String): List<JwCourse> {
        val result = mutableListOf<JwCourse>()
        try {
            // 可能是数组直接开始，也可能包在对象里
            val arr: JSONArray = when (jsonStr[0]) {
                '[' -> JSONArray(jsonStr)
                '{' -> {
                    val obj = org.json.JSONObject(jsonStr)
                    // 找第一个 JSONArray 属性
                    var found: JSONArray? = null
                    for (key in obj.keys()) {
                        val v = obj.optJSONArray(key)
                        if (v != null && v.length() > 0) { found = v; break }
                    }
                    found ?: return emptyList()
                }
                else -> return emptyList()
            }

            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue

                // 课程名（正方多版本字段名）
                val name = firstStr(o, "kcmc", "kcm", "kc_mc", "courseName", "rlkcmc", "jxbmc")
                if (name.isBlank()) continue

                val teacher = firstStr(o, "jsxm", "jsmc", "teacher", "attendClassTeacher", "skjs")
                val room = firstStr(o, "jasmc", "jsmc", "classroomName", "jxlh", "jasdm")

                // 星期
                val day = firstInt(o, "kcxq", "xq", "xqj", "classDay", "skxq") ?: continue

                // 节次
                val startNode = firstInt(o, "ksjcsd", "ksjc", "jc", "classSessions", "ksjcd")
                    ?: firstInt(o, "ksjc") ?: continue
                val endNode = firstInt(o, "jsjcsd", "jsjc", "jsjssd", "continuingSession")
                    ?.let { if (it < startNode) startNode else it }
                    ?: (startNode + 1)

                // 周次
                val zcStr = firstStr(o, "zcd", "kkzc", "zc", "classWeek", "skzc")
                val ranges = parseWeekStr(zcStr)

                for (r in ranges) {
                    result += JwCourse(
                        name = name,
                        room = room,
                        teacher = teacher,
                        day = day.coerceIn(1, 7),
                        startNode = startNode.coerceAtLeast(1),
                        endNode = endNode.coerceAtLeast(startNode),
                        startWeek = r.first,
                        endWeek = r.second,
                        type = r.third
                    )
                }
            }
        } catch (e: Exception) {
            // JSON 解析失败，静默
        }
        return result
    }

    private fun firstStr(o: org.json.JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = o.optString(k, "").trim()
            if (v.isNotBlank()) return v
        }
        return ""
    }

    private fun firstInt(o: org.json.JSONObject, vararg keys: String): Int? {
        for (k in keys) {
            val v = o.optString(k, "").trim()
            if (v.isNotBlank()) return v.toIntOrNull()
        }
        return null
    }

    /** 周次字符串 → (start, end, type) 范围列表 */
    private fun parseWeekStr(s: String): List<Triple<Int, Int, Int>> {
        if (s.isBlank()) return listOf(Triple(1, 20, 0))
        val result = mutableListOf<Triple<Int, Int, Int>>()

        // bitmap 模式：11111111111100000（每位 = 第 N 周）
        if (s.length >= 10 && s.all { it == '0' || it == '1' }) {
            val weeks = s.mapIndexedNotNull { i, c -> if (c == '1') i + 1 else null }
            return bitsToRanges(weeks)
        }

        // 范围/列表模式："1-16" / "1-16周" / "1-16周(单)" / "1,3,5,7"
        s.split(",", "，", ";", "；").forEach { part ->
            val cleaned = part.replace("周", "").replace("(", "").replace(")", "").trim()
            val type = when {
                part.contains("单") -> 1
                part.contains("双") -> 2
                else -> 0
            }
            if (cleaned.contains("-")) {
                val parts = cleaned.split("-")
                val start = parts.getOrNull(0)?.filter { it.isDigit() }?.toIntOrNull() ?: 1
                val end = parts.getOrNull(1)?.filter { it.isDigit() }?.toIntOrNull() ?: start
                result += Triple(start, end, type)
            } else {
                val v = cleaned.filter { it.isDigit() }.toIntOrNull() ?: return@forEach
                result += Triple(v, v, type)
            }
        }
        return if (result.isEmpty()) listOf(Triple(1, 20, 0)) else result
    }

    private fun bitsToRanges(weeks: List<Int>): List<Triple<Int, Int, Int>> {
        if (weeks.isEmpty()) return emptyList()
        val result = mutableListOf<Triple<Int, Int, Int>>()
        var i = 0
        while (i < weeks.size) {
            val start = weeks[i]
            var end = start
            if (i + 1 < weeks.size && weeks[i + 1] - start == 2) {
                // 单/双周模式
                end = weeks[i + 1]
                var k = i + 1
                while (k + 1 < weeks.size && weeks[k + 1] - weeks[k] == 2) { k++; end = weeks[k] }
                val type = if (start % 2 == 1) 1 else 2
                result += Triple(start, end, type)
                i = k + 1
            } else if (i + 1 < weeks.size && weeks[i + 1] - start == 1) {
                // 连续周
                end = weeks[i + 1]
                var k = i + 1
                while (k + 1 < weeks.size && weeks[k + 1] - weeks[k] == 1) { k++; end = weeks[k] }
                result += Triple(start, end, 0)
                i = k + 1
            } else {
                result += Triple(start, end, 0)
                i++
            }
        }
        return result
    }

    // ─── HTML 表格解析（兜底） ─────────────────────────────────

    /**
     * 正方新版渲染后的 HTML 与强智类似但容器 ID 可能不同。
     * 尝试 "kbtable" / "kbgrid" / class 选择器。
     */
    private fun parseHtmlTable(): List<JwCourse> {
        val doc = Jsoup.parse(source)

        // 多种容器选择器
        val container = doc.getElementById("kbtable")
            ?: doc.getElementById("kbgrid")
            ?: doc.selectFirst("table.el-table__body")
            ?: doc.selectFirst(".kbcapi-table")
            ?: doc.selectFirst("[id*=kb]")
            ?: return parseHtmlTableFromQz()  // 完全 fallback 到 QZ 逻辑

        val result = mutableListOf<JwCourse>()
        val trs = container.getElementsByTag("tr")
        var nodeCount = 0

        for (tr in trs) {
            val tds = tr.getElementsByTag("td")
            if (tds.isEmpty()) continue
            nodeCount++

            var day = 0
            for (td in tds) {
                day++
                val cells = td.getElementsByClass("kbcontent")
                if (cells.isEmpty()) continue

                for (cell in cells) {
                    val html = cell.html()
                    if (html.isBlank()) continue
                    // 同格多门课用 "-----" 分隔（与 QZ 一致）
                    val parts = html.split("-----")
                    for (part in parts) {
                        parseCell(part.trim(), day, nodeCount)?.let { result += it }
                    }
                }
            }
        }

        return if (result.isEmpty()) parseHtmlTableFromQz() else result
    }

    private fun parseCell(html: String, day: Int, nodeCount: Int): JwCourse? {
        val cellDoc = Jsoup.parse(html)
        val name = try {
            Jsoup.parse(html.substringBefore("<font").trim()).text()
        } catch (e: Exception) {
            html.substringBefore("<font").trim()
        }
        if (name.isBlank()) return null

        val teacher = cellDoc.getElementsByAttributeValue("title", "老师").text().trim()
        val room = cellDoc.getElementsByAttributeValue("title", "教室").text().trim()
        val weekStr = cellDoc.getElementsByAttributeValue("title", "周次(节次)")
            .text().substringBefore("(周)")

        val ranges = parseWeekStr(weekStr)
        val node = nodeCount * 2 - 1

        return JwCourse(
            name = name,
            room = room,
            teacher = teacher,
            day = day,
            startNode = node,
            endNode = node + 1,
            startWeek = ranges.first().first,
            endWeek = ranges.first().second,
            type = ranges.first().third
        )
    }

    /** 完全 fallback 到 QZ 解析逻辑 */
    private fun parseHtmlTableFromQz(): List<JwCourse> {
        return JwQzParser(source).generateCourseList()
    }
}
