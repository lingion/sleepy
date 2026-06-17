package com.lingion.sleepy.data.parser

import com.lingion.sleepy.data.entity.CourseEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * 课程表文本解析器 — 支持：
 *
 * 1. **WakeUp 课程表分享 JSON**（来自 WakeUp app 的导出）
 *    格式: {"name":"...","startDate":"2024-09-02","courses":[{"name":"高数","teacher":"张三","position":"A101","day":1,"startNode":1,"step":2,"startWeek":1,"endWeek":16,"type":0,"color":"#FF6750A4"}, ...]}
 *
 * 2. **简化的纯文本格式** (一行一课，制表符或空格分隔):
 *    ```
 *    高等数学	张三	A101	1	1-2	1-16	0
 *    大学英语	李四	B202	2	3-4	1-16	0
 *    ```
 *    字段: 课程名\t老师\t教室\t星期\t节次(1-2)\t周次(1-16)\t类型(0/1/2)
 */
object ScheduleParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 解析课表文本。返回 Result.success(list) 或 Result.failure。
     */
    fun parse(text: String, defaultTableId: Long, defaultColor: String = "#FF6750A4"): Result<ParseResult> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("空内容"))

        return runCatching {
            when {
                trimmed.startsWith("{") && trimmed.contains("\"courseDetailJson\"") -> parseWakeUpShareText(trimmed, defaultTableId)
                trimmed.startsWith("{") && (trimmed.contains("\"courses\"") || trimmed.contains("\"tableInfo\"")) -> parseWakeUpJson(trimmed, defaultTableId, defaultColor)
                trimmed.startsWith("BEGIN:VCALENDAR") || trimmed.startsWith("BEGIN:VEVENT") -> parseIcs(trimmed, defaultTableId, defaultColor)
                // HTML: 必须以 <!DOCTYPE / <html / <table / <body / <div 开头（先 trim 掉 BOM）
                startsWithAnyTag(trimmed, "html", "body", "table", "div", "section", "article") -> parseHtml(trimmed, defaultTableId, defaultColor)
                // CSV: 含有 CSV 表头 (课程名/名称/course/name + 教师/teacher 等)，并且至少 1 个换行
                isLikelyCsv(trimmed) -> parseCsv(trimmed, defaultTableId, defaultColor)
                else -> parseSimpleText(trimmed, defaultTableId, defaultColor)
            }
        }
    }

    private fun startsWithAnyTag(s: String, vararg tags: String): Boolean {
        val t = s.lowercase()
        if (t.startsWith("<!doctype") || t.startsWith("<?xml")) return true
        return tags.any { tag -> t.startsWith("<$tag") }
    }

    /**
     * 判断是否为 CSV：
     * 1) 第一行包含逗号，且第二行也存在
     * 2) 包含常见表头：课程/课程名/名称/course/name + 教师/老师/teacher
     */
    private fun isLikelyCsv(s: String): Boolean {
        if (s.count { it == '\n' } < 1) return false
        val firstLine = s.lineSequence().firstOrNull()?.lowercase() ?: return false
        if (!firstLine.contains(',')) return false
        val hasCourse = firstLine.contains("课程") || firstLine.contains("course") || firstLine.contains("name")
        val hasTeacher = firstLine.contains("教师") || firstLine.contains("老师") || firstLine.contains("teacher")
        val hasDay = firstLine.contains("星期") || firstLine.contains("周几") || firstLine.contains("day") || firstLine.contains("周次")
        return hasCourse && hasTeacher && hasDay
    }

    /**
     * 解析 WakeUp 分享文本格式:
     * ```
     * 【来自WakeUp课程表】
     * 课程分享:
     *
     * {"name":"...","startDate":"...","courseDetailJson":"<URL-encoded JSON>"}
     * ```
     * 或简化版:
     * ```
     * 课程分享:
     * {"name":"...","startDate":"...","courses":[...]}
     * ```
     */
    private fun parseWakeUpShareText(text: String, defaultTableId: Long): ParseResult {
        // 找到 JSON 部分
        val jsonStart = text.indexOf("{")
        if (jsonStart < 0) throw IllegalArgumentException("找不到 JSON")
        val jsonStr = text.substring(jsonStart)

        val root = json.parseToJsonElement(jsonStr).jsonObject
        val name = root["name"]?.jsonPrimitive?.content ?: "导入的课表"
        val startDate = root["startDate"]?.jsonPrimitive?.content
            ?: root["tableInfo"]?.jsonObject?.get("startDate")?.jsonPrimitive?.content
            ?: java.time.LocalDate.now().toString()

        val courseDetailJsonStr = root["courseDetailJson"]?.jsonPrimitive?.content
        val courses: List<CourseEntity> = if (courseDetailJsonStr != null) {
            // courseDetailJson 是 URL-encoded JSON 字符串
            val decoded = java.net.URLDecoder.decode(courseDetailJsonStr, "UTF-8")
            parseCourseJsonArray(decoded, defaultTableId)
        } else {
            val arr = root["courses"]?.jsonArray
                ?: root["tableInfo"]?.jsonObject?.get("courses")?.jsonArray
                ?: throw IllegalArgumentException("找不到 courses 字段")
            parseCourseJsonArrayRaw(arr, defaultTableId)
        }

        return ParseResult(
            tableName = name,
            startDate = startDate,
            courses = courses
        )
    }

    private fun parseWakeUpJson(text: String, defaultTableId: Long, defaultColor: String): ParseResult {
        val root = json.parseToJsonElement(text).jsonObject
        val name = root["name"]?.jsonPrimitive?.content ?: "导入的课表"
        val startDate = root["startDate"]?.jsonPrimitive?.content
            ?: java.time.LocalDate.now().toString()
        val arr = root["courses"]?.jsonArray ?: throw IllegalArgumentException("找不到 courses 数组")

        val courses = arr.map { el ->
            val obj = el.jsonObject
            CourseEntity(
                id = 0,
                tableId = defaultTableId,
                courseName = obj["name"]?.jsonPrimitive?.content
                    ?: obj["courseName"]?.jsonPrimitive?.content
                    ?: "未命名",
                teacher = obj["teacher"]?.jsonPrimitive?.content ?: "",
                room = obj["position"]?.jsonPrimitive?.content
                    ?: obj["room"]?.jsonPrimitive?.content
                    ?: "",
                note = obj["note"]?.jsonPrimitive?.content ?: "",
                day = obj["day"]?.jsonPrimitive?.intOrZero() ?: 1,
                startNode = obj["startNode"]?.jsonPrimitive?.intOrZero() ?: 1,
                step = obj["step"]?.jsonPrimitive?.intOrZero() ?: 1,
                startWeek = obj["startWeek"]?.jsonPrimitive?.intOrZero() ?: 1,
                endWeek = obj["endWeek"]?.jsonPrimitive?.intOrZero() ?: 16,
                type = obj["type"]?.jsonPrimitive?.intOrZero() ?: 0,
                color = obj["color"]?.jsonPrimitive?.content ?: defaultColor
            )
        }

        return ParseResult(name, startDate, courses)
    }

    private fun parseCourseJsonArray(jsonStr: String, tableId: Long): List<CourseEntity> {
        val arr = json.parseToJsonElement(jsonStr).jsonArray
        return parseCourseJsonArrayRaw(arr, tableId)
    }

    private fun parseCourseJsonArrayRaw(arr: kotlinx.serialization.json.JsonArray, tableId: Long): List<CourseEntity> {
        return arr.map { el ->
            val obj = el.jsonObject
            CourseEntity(
                id = 0,
                tableId = tableId,
                courseName = obj["name"]?.jsonPrimitive?.content
                    ?: obj["courseName"]?.jsonPrimitive?.content
                    ?: "未命名",
                teacher = obj["teacher"]?.jsonPrimitive?.content ?: "",
                room = obj["position"]?.jsonPrimitive?.content ?: "",
                note = "",
                day = obj["day"]?.jsonPrimitive?.intOrZero() ?: 1,
                startNode = obj["startNode"]?.jsonPrimitive?.intOrZero() ?: 1,
                step = obj["step"]?.jsonPrimitive?.intOrZero() ?: 1,
                startWeek = obj["startWeek"]?.jsonPrimitive?.intOrZero() ?: 1,
                endWeek = obj["endWeek"]?.jsonPrimitive?.intOrZero() ?: 16,
                type = obj["type"]?.jsonPrimitive?.intOrZero() ?: 0,
                color = obj["color"]?.jsonPrimitive?.content ?: "#FF6750A4"
            )
        }
    }

    /**
     * 解析 ICS 日历文件 (RFC 5545)。
     * 简化版：每个 VEVENT 视为一节课，按 RRULE 展开成单双周处理。
     */
    private fun parseIcs(text: String, defaultTableId: Long, defaultColor: String): ParseResult {
        val courses = mutableListOf<CourseEntity>()
        val events = text.split("BEGIN:VEVENT").drop(1)
        for (event in events) {
            val end = event.indexOf("END:VEVENT")
            val block = if (end > 0) event.substring(0, end) else event

            val summary = extractIcsField(block, "SUMMARY") ?: continue
            val location = extractIcsField(block, "LOCATION") ?: ""
            val description = extractIcsField(block, "DESCRIPTION") ?: ""

            val (startNode, step) = extractIcsTime(block) ?: continue
            val day = extractIcsDayOfWeek(block) ?: continue
            val (startWeek, endWeek, type) = extractIcsWeeks(block) ?: Triple(1, 16, 0)

            val teacher = if (description.isNotBlank()) description else ""
            courses += CourseEntity(
                id = 0,
                tableId = defaultTableId,
                courseName = summary,
                teacher = teacher,
                room = location,
                note = "",
                day = day,
                startNode = startNode,
                step = step,
                startWeek = startWeek,
                endWeek = endWeek,
                type = type,
                color = defaultColor
            )
        }

        return ParseResult(
            tableName = "导入的 ICS 课表",
            startDate = java.time.LocalDate.now().toString(),
            courses = courses
        )
    }

    private fun extractIcsField(block: String, name: String): String? {
        // ICS 字段可能折行 (下一行以空格开头)
        val regex = Regex("(?m)^$name(?:;[^:]*)?:(.*(?:\\n .*)*)")
        return regex.find(block)?.groupValues?.get(1)
            ?.replace(Regex("\\n "), "")
            ?.trim()
    }

    /** 从 DTSTART 提取节次（默认按 45min/节估算） */
    private fun extractIcsTime(block: String): Pair<Int, Int>? {
        val dtstart = extractIcsField(block, "DTSTART") ?: return null
        val dtend = extractIcsField(block, "DTEND") ?: return null
        // 解析 HHmmss
        return try {
            val start = parseIcsTimeOfDay(dtstart.substringAfter("T").take(6))
            val end = parseIcsTimeOfDay(dtend.substringAfter("T").take(6))
            val startMin = start.hour * 60 + start.minute
            val endMin = end.hour * 60 + end.minute
            val duration = endMin - startMin
            val startNode = ((startMin - 480) / 55).toInt() + 1  // 8:00 = 第 1 节
            val step = (duration / 55).coerceAtLeast(1)
            Pair(startNode.coerceAtLeast(1), step)
        } catch (e: Exception) { null }
    }

    private fun parseIcsTimeOfDay(s: String): java.time.LocalTime =
        java.time.LocalTime.of(s.substring(0, 2).toInt(), s.substring(2, 4).toInt())

    /** 从 BYDAY 提取星期几 */
    private fun extractIcsDayOfWeek(block: String): Int? {
        val rrule = extractIcsField(block, "RRULE") ?: return null
        val match = Regex("BYDAY=([A-Z]{2})").find(rrule) ?: return null
        return when (match.groupValues[1]) {
            "MO" -> 1; "TU" -> 2; "WE" -> 3; "TH" -> 4
            "FR" -> 5; "SA" -> 6; "SU" -> 7
            else -> null
        }
    }

    private fun extractIcsWeeks(block: String): Triple<Int, Int, Int>? {
        // 仅做最简支持：使用 UNTIL/COUNT 推导范围，type 默认为每周
        return Triple(1, 16, 0)
    }

    /**
     * 解析简化的纯文本格式：
     * 一行一课，字段间用制表符或全角逗号分隔。
     *
     * 示例:
     * ```
     * 高等数学\t张三\tA101\t1\t1-2\t1-16\t0
     * 大学英语\t李四\tB202\t2\t3-4\t1-16\t0
     * ```
     */
    private fun parseSimpleText(text: String, defaultTableId: Long, defaultColor: String): ParseResult {
        val courses = mutableListOf<CourseEntity>()
        val lines = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }

        for (line in lines) {
            // 支持 tab / 空格 / 全角逗号
            val parts = line.split('\t', '，').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size < 6) continue

            val name = parts[0]
            val teacher = parts[1]
            val room = parts[2]
            val day = parts[3].toIntOrNull() ?: continue
            // 节次列是 start-end 格式 (e.g. "1-2"), 转为 (startNode, step=end-start+1)
            val (nodeStart, nodeEnd) = parseRange(parts[4]) ?: continue
            val step = (nodeEnd - nodeStart + 1).coerceAtLeast(1)
            val (startWeek, endWeek) = parseRange(parts[5]) ?: continue
            val type = parts.getOrNull(6)?.toIntOrNull() ?: 0

            courses += CourseEntity(
                id = 0,
                tableId = defaultTableId,
                courseName = name,
                teacher = teacher,
                room = room,
                day = day,
                startNode = nodeStart,
                step = step,
                startWeek = startWeek,
                endWeek = endWeek,
                type = type,
                color = defaultColor
            )
        }

        if (courses.isEmpty()) throw IllegalArgumentException("未能解析任何课程")

        return ParseResult(
            tableName = "导入的课表",
            startDate = java.time.LocalDate.now().toString(),
            courses = courses
        )
    }

    private fun parseRange(s: String): Pair<Int, Int>? {
        val parts = s.split('-', '~', '至')
        if (parts.size != 2) return null
        val start = parts[0].toIntOrNull() ?: return null
        val end = parts[1].toIntOrNull() ?: return null
        return start to end
    }

    /**
     * 解析 CSV 格式课表。
     * 自动识别表头，常见列名（中文/英文）都可以：
     *   课程名 / 名称 / course / name
     *   教师 / 老师 / teacher
     *   教室 / 位置 / 地点 / room / position
     *   星期 / 周几 / day
     *   节次 / 节点 / node
     *   周次 / weeks / week
     *   类型 / type
     *
     * 支持带引号的字段（"" 转义 "）
     */
    private fun parseCsv(text: String, defaultTableId: Long, defaultColor: String): ParseResult {
        val rows = parseCsvRows(text)
        if (rows.size < 2) throw IllegalArgumentException("CSV 至少需要表头 + 1 行数据")

        val header = rows[0].map { it.trim().lowercase() }

        // 查找列索引
        fun findCol(vararg keys: String): Int? {
            for (k in keys) {
                val idx = header.indexOfFirst { it.contains(k.lowercase()) }
                if (idx >= 0) return idx
            }
            return null
        }

        val nameIdx = findCol("课程名", "课程", "名称", "course", "name")
            ?: throw IllegalArgumentException("找不到课程名列")
        val teacherIdx = findCol("教师", "老师", "teacher")
        val roomIdx = findCol("教室", "位置", "地点", "room", "position")
        val dayIdx = findCol("星期", "周几", "day")
            ?: throw IllegalArgumentException("找不到星期列")
        val nodeIdx = findCol("节次", "节点", "node", "节", "class")
            ?: throw IllegalArgumentException("找不到节次列")
        val weekIdx = findCol("周次", "weeks", "week")
            ?: throw IllegalArgumentException("找不到周次列")
        val typeIdx = findCol("类型", "type", "周类型")
        val noteIdx = findCol("备注", "note", "remark")

        val courses = mutableListOf<CourseEntity>()
        for (i in 1 until rows.size) {
            val row = rows[i]
            if (row.isEmpty() || row.all { it.isBlank() }) continue

            fun cell(idx: Int?): String = idx?.let { row.getOrNull(it)?.trim() ?: "" } ?: ""

            val name = cell(nameIdx)
            if (name.isBlank()) continue

            val dayRaw = cell(dayIdx)
            val day = parseDay(dayRaw) ?: continue
            // 节次列是 start-end 格式 (e.g. "1-2"), 转为 (startNode, step=end-start+1)
            val (nodeStart, nodeEnd) = parseRange(cell(nodeIdx)) ?: continue
            val step = (nodeEnd - nodeStart + 1).coerceAtLeast(1)
            val (startWeek, endWeek) = parseRange(cell(weekIdx)) ?: continue
            val type = cell(typeIdx).let { parseType(it) }

            courses += CourseEntity(
                id = 0,
                tableId = defaultTableId,
                courseName = name,
                teacher = cell(teacherIdx),
                room = cell(roomIdx),
                note = cell(noteIdx),
                day = day,
                startNode = nodeStart,
                step = step,
                startWeek = startWeek,
                endWeek = endWeek,
                type = type,
                color = defaultColor
            )
        }

        if (courses.isEmpty()) throw IllegalArgumentException("未能解析任何课程")

        return ParseResult(
            tableName = "导入的 CSV 课表",
            startDate = java.time.LocalDate.now().toString(),
            courses = courses
        )
    }

    /** 解析 CSV 文本为二维字符串数组，支持引号转义 */
    private fun parseCsvRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var cur = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < n && text[i + 1] == '"' -> { sb.append('"'); i += 2; continue }
                    c == '"' -> { inQuotes = false; i++; continue }
                    else -> { sb.append(c); i++ }
                }
            } else {
                when (c) {
                    '"' -> { inQuotes = true; i++ }
                    ',' -> { cur.add(sb.toString()); sb.setLength(0); i++ }
                    '\n' -> { cur.add(sb.toString()); sb.setLength(0); rows.add(cur); cur = mutableListOf(); i++ }
                    '\r' -> { i++; continue }
                    else -> { sb.append(c); i++ }
                }
            }
        }
        if (sb.isNotEmpty() || cur.isNotEmpty()) {
            cur.add(sb.toString())
            rows.add(cur)
        }
        return rows
    }

    /** 解析 "星期" 列：支持 "周一" "1" "Monday" "mon" */
    private fun parseDay(s: String): Int? {
        val t = s.trim().lowercase()
        if (t.isEmpty()) return null
        // 纯数字
        t.toIntOrNull()?.let { if (it in 1..7) return it }
        // 包含 "周"
        if (t.contains("周")) {
            val map = mapOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7)
            for ((k, v) in map) if (t.contains(k)) return v
        }
        // 英文
        val enMap = mapOf("mon" to 1, "tue" to 2, "wed" to 3, "thu" to 4, "fri" to 5, "sat" to 6, "sun" to 7)
        for ((k, v) in enMap) if (t.startsWith(k)) return v
        return null
    }

    /** 解析 "类型" 列：0=每周 1=单周 2=双周 */
    private fun parseType(s: String): Int {
        val t = s.trim().lowercase()
        if (t.isEmpty()) return 0
        if (t.contains("单") || t == "1" || t == "odd") return 1
        if (t.contains("双") || t == "2" || t == "even") return 2
        return 0
    }

    /** 解析 "节次" 列：支持 "1-2" "第1-2节" "1,2" "1\u00a01" */
    private fun parseRangeOrNode(s: String): Pair<Int, Int>? {
        val t = s.trim().replace("节", "").replace("第", "")
        // 优先 "1-2" / "1~2" / "1至2"
        parseRange(t)?.let { return it }
        // 尝试逗号/空格分隔的列表
        val nums = t.split(',', ' ', '/').mapNotNull { it.toIntOrNull() }.sorted()
        if (nums.isNotEmpty()) {
            val start = nums.first()
            val end = nums.last()
            return start to (end - start + 1)
        }
        return null
    }

    /**
     * 解析 HTML 课表。
     * 处理两种常见格式：
     * 1) WakeUp HTML 导出：含课程名称+老师+教室+节次+周次的 <table>
     * 2) 简单 HTML 表格：<table> 包含 <tr><td>...</td></tr>
     *
     * 策略：抽取所有 <table>，逐行解析，尝试按列匹配。
     */
    private fun parseHtml(text: String, defaultTableId: Long, defaultColor: String): ParseResult {
        // 去掉 HTML 标签得到纯文本，再按 <table> 分段解析
        val tables = extractHtmlTables(text)
        if (tables.isEmpty()) throw IllegalArgumentException("HTML 中未找到表格")

        val courses = mutableListOf<CourseEntity>()
        for (rows in tables) {
            if (rows.isEmpty()) continue
            // 尝试按"表头识别"方式解析
            courses += parseHtmlTableRows(rows, defaultTableId, defaultColor)
        }

        if (courses.isEmpty()) throw IllegalArgumentException("HTML 中未能解析出任何课程")

        return ParseResult(
            tableName = "导入的 HTML 课表",
            startDate = java.time.LocalDate.now().toString(),
            courses = courses
        )
    }

    /** 抽取所有 <table>...</table> 转为 List<List<String>> (按 <td>/<th>) */
    private fun extractHtmlTables(html: String): List<List<List<String>>> {
        val tables = mutableListOf<List<List<String>>>()
        val tableRegex = Regex("(?is)<table[^>]*>(.*?)</table>")
        val trRegex = Regex("(?is)<tr[^>]*>(.*?)</tr>")
        val cellRegex = Regex("(?is)<(td|th)[^>]*>(.*?)</\\1>")
        val tagRegex = Regex("(?is)<[^>]+>")

        for (tMatch in tableRegex.findAll(html)) {
            val tableBody = tMatch.groupValues[1]
            val rows = mutableListOf<List<String>>()
            for (trMatch in trRegex.findAll(tableBody)) {
                val trBody = trMatch.groupValues[1]
                val cells = cellRegex.findAll(trBody).map { cell ->
                    // 解码 HTML 实体
                    tagRegex.replace(cell.groupValues[2], "")
                        .replace("&nbsp;", " ")
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&quot;", "\"")
                        .trim()
                }.toList()
                if (cells.isNotEmpty()) rows.add(cells)
            }
            if (rows.isNotEmpty()) tables.add(rows)
        }
        return tables
    }

    /**
     * 解析一个表格的所有行。
     * 先尝试识别表头行（含 "课程"/"教师"/"星期"/"节次" 等关键字），
     * 找到的话按列解析；找不到就把每行作为非结构化文本走 parseSimpleText 逻辑。
     */
    private fun parseHtmlTableRows(rows: List<List<String>>, defaultTableId: Long, defaultColor: String): List<CourseEntity> {
        // 找表头行
        val headerIdx = rows.indexOfFirst { row ->
            val t = row.joinToString(" ").lowercase()
            t.contains("课程") || t.contains("course") || t.contains("name")
        }
        if (headerIdx < 0) {
            // 退化：按文本行处理
            val text = rows.flatten().joinToString("\n")
            return runCatching { parseSimpleText(text, defaultTableId, defaultColor).courses }
                .getOrElse { emptyList() }
        }
        val header = rows[headerIdx].map { it.trim().lowercase() }
        fun findCol(vararg keys: String): Int? {
            for (k in keys) {
                val idx = header.indexOfFirst { it.contains(k.lowercase()) }
                if (idx >= 0) return idx
            }
            return null
        }
        val nameIdx = findCol("课程", "course", "name") ?: return emptyList()
        val teacherIdx = findCol("教师", "老师", "teacher")
        val roomIdx = findCol("教室", "位置", "room", "position", "地点")
        val dayIdx = findCol("星期", "周几", "day")
        val nodeIdx = findCol("节次", "节点", "node")
        val weekIdx = findCol("周次", "weeks", "week")
        val typeIdx = findCol("类型", "type")
        val noteIdx = findCol("备注", "note")

        val courses = mutableListOf<CourseEntity>()
        for (i in (headerIdx + 1) until rows.size) {
            val row = rows[i]
            if (row.isEmpty() || row.all { it.isBlank() }) continue
            fun cell(idx: Int?): String = idx?.let { row.getOrNull(it)?.trim() ?: "" } ?: ""

            val name = cell(nameIdx)
            if (name.isBlank()) continue

            val day = parseDay(cell(dayIdx))
            if (day == null) continue
            // 节次列是 start-end 格式 (e.g. "1-2"), 转为 (startNode, step=end-start+1)
            val (nodeStart, nodeEnd) = parseRange(cell(nodeIdx)) ?: continue
            val step = (nodeEnd - nodeStart + 1).coerceAtLeast(1)
            val (startWeek, endWeek) = parseRange(cell(weekIdx)) ?: continue
            val type = parseType(cell(typeIdx))

            courses += CourseEntity(
                id = 0,
                tableId = defaultTableId,
                courseName = name,
                teacher = cell(teacherIdx),
                room = cell(roomIdx),
                note = cell(noteIdx),
                day = day,
                startNode = nodeStart,
                step = step,
                startWeek = startWeek,
                endWeek = endWeek,
                type = type,
                color = defaultColor
            )
        }
        return courses
    }


    private fun kotlinx.serialization.json.JsonPrimitive.intOrZero(): Int =
        content.toIntOrNull() ?: 0

    private fun kotlinx.serialization.json.JsonPrimitive.longOrZero(): Long =
        content.toLongOrNull() ?: 0L

    data class ParseResult(
        val tableName: String,
        val startDate: String,
        val courses: List<CourseEntity>
    )
}