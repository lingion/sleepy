package com.lingion.sleepy.data.parser

import com.lingion.sleepy.data.entity.CourseEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.LocalTime
import java.util.TreeMap

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

        // 兼容性：导出端常在 JSON 前加 "【来自Sleepy】\n课程分享：\n\n" 前缀。
        // 如果 trimmed 不以 { 开头但包含 {，剥掉前缀再判别。
        val body = if (!trimmed.startsWith("{") && trimmed.contains("{")) {
            trimmed.substring(trimmed.indexOf("{"))
        } else trimmed

        return runCatching {
            when {
                body.contains("\"courseDetailJson\"") -> parseWakeUpShareText(body, defaultTableId)
                body.startsWith("{") && (body.contains("\"courses\"") || body.contains("\"tableInfo\"")) -> parseWakeUpJson(body, defaultTableId, defaultColor)
                body.startsWith("BEGIN:VCALENDAR") || body.startsWith("BEGIN:VEVENT") -> parseIcs(body, defaultTableId, defaultColor)
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
                groupId = "",
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
                groupId = "",
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
     *
     * 支持两类来源:
     * 1. WakeUp 课程表导出 — SUMMARY=课名, DESCRIPTION="第X - Y节\n教室\n教师"(字面 \n 转义),
     *    每个实际上课时段一条 VEVENT(双周课拆成 N 条 INTERVAL=1 的短事件),
     *    UNTIL 日期为最后一次发生的日历日(可能比该次晚 6 天,按同星期几对齐回推)。
     * 2. Sleepy 自家导出 — DESCRIPTION="老师：X", 单双周用 INTERVAL=2 表达。
     *
     * 语义:
     * - 学期锚点 = 最早 DTSTART 所在周的周一 → startDate(周数编号基准)
     * - 周区间 = [DTSTART 周, UNTIL 对齐星期几后的最后一次发生周]
     * - 节次优先读 DESCRIPTION "第X - Y节", 无则按 55min/节估算
     * - 同(课名,星期,节次,教师)的多个 VEVENT 按周序列合并:
     *   连续拼合→type=0; 全同奇偶且间距2→单/双周; 否则(散周)保持独立行
     */
    private fun parseIcs(text: String, defaultTableId: Long, defaultColor: String): ParseResult {
        data class Event(
            val name: String, val day: Int, val startNode: Int, val step: Int,
            val teacher: String, val room: String,
            val firstDate: java.time.LocalDate, val lastDate: java.time.LocalDate, val interval: Int
        )

        val events = mutableListOf<Event>()
        // 全校作息收割: 节次 → (start,end)。每个 VEVENT 直接给出
        // node[首节].start=DTSTART, node[末节].end=DTEND, 中间节次按边界推导。
        val nodeTimes = TreeMap<Int, Pair<LocalTime, LocalTime>>()
        for (raw in text.split("BEGIN:VEVENT").drop(1)) {
            val end = raw.indexOf("END:VEVENT")
            val block = if (end > 0) raw.substring(0, end) else raw

            val summary = extractIcsField(block, "SUMMARY") ?: continue
            val location = extractIcsField(block, "LOCATION") ?: ""
            val description = extractIcsField(block, "DESCRIPTION") ?: ""
            val descLines = description.split("\\n")

            // WakeUp: DESCRIPTION="第X - Y节\n教室\n教师", LOCATION="教室 教师" / Sleepy: DESCRIPTION="老师：X"
            val teacher = when {
                descLines.size >= 3 -> descLines[2].trim()
                description.startsWith("老师：") -> description.substringAfter("老师：").trim()
                description.startsWith("老师:") -> description.substringAfter("老师:").trim()
                else -> ""
            }
            val room = when {
                descLines.size >= 2 && descLines[1].isNotBlank() -> descLines[1].trim()
                location.isNotBlank() && teacher.isNotBlank() && location.endsWith(teacher) ->
                    location.removeSuffix(teacher).trim()
                else -> location
            }

            val day = extractIcsDayOfWeek(block) ?: continue
            val dtstart = extractIcsDate(block) ?: continue
            val (startNode, step) = extractIcsNode(description) ?: extractIcsTime(block) ?: continue

            // 作息收割: 有节次行 + 有起止钟点才有贡献(Sleepy 自家导出也满足)
            harvestNodeTimes(block, startNode, step, nodeTimes)

            val rrule = extractIcsField(block, "RRULE") ?: ""
            val interval = if (rrule.contains("INTERVAL=2")) 2 else 1
            val untilDate = Regex("UNTIL=(\\d{8})").find(rrule)?.groupValues?.get(1)
                ?.let { parseIcsDate(it) } ?: dtstart
            // UNTIL 是最后一次发生的日历日(可能晚于该次 0-6 天) → 对齐回同星期几
            val deltaDays = java.time.temporal.ChronoUnit.DAYS.between(dtstart, untilDate).toInt()
            val lastOccurrence = dtstart.plusDays((deltaDays - deltaDays % 7).toLong())

            events += Event(summary, day, startNode, step, teacher, room, dtstart, lastOccurrence, interval)
        }

        if (events.isEmpty()) {
            return ParseResult("导入的 ICS 课表", java.time.LocalDate.now().toString(), emptyList())
        }

        // 锚点: 最早 DTSTART 所在周的周一 → 周数编号基准
        val anchor = events.minOf { it.firstDate }.with(java.time.DayOfWeek.MONDAY)

        fun weekOf(d: java.time.LocalDate): Int =
            (java.time.temporal.ChronoUnit.DAYS.between(anchor, d) / 7).toInt() + 1

        // 按 (课名,星期,节次,教师) 聚合各周区间 — room 不进键:
        // WakeUp 会把"每周换教室的同一门课"拆成多条 VEVENT,若 room 进键会把同一时段拆成
        // 两组各自"同奇偶间距2"的散周 → 被误判成两个假单双周(实证: 24sp 管理心理学)。
        // room 变化交给散周 room-run 合并处理。
        data class SlotKey(val name: String, val day: Int, val node: Int, val step: Int, val teacher: String)

        val groups = LinkedHashMap<SlotKey, MutableList<Triple<Int, Int, String>>>() // (startW, endW, room)
        val groupInterval = HashMap<SlotKey, Int>()
        for (e in events) {
            val key = SlotKey(e.name, e.day, e.startNode, e.step, e.teacher)
            groups.getOrPut(key) { mutableListOf() }.add(Triple(weekOf(e.firstDate), weekOf(e.lastDate), e.room))
            groupInterval[key] = e.interval
        }

        val courses = mutableListOf<CourseEntity>()
        for ((key, chunks) in groups) {
            val sorted = chunks.sortedWith(compareBy({ it.first }, { it.second }, { it.third }))
            val interval = groupInterval[key] ?: 1

            fun emit(startWeek: Int, endWeek: Int, type: Int, room: String) {
                courses += CourseEntity(
                    id = 0,
                    groupId = "",
                    tableId = defaultTableId,
                    courseName = key.name,
                    teacher = key.teacher,
                    room = room,
                    note = "",
                    day = key.day,
                    startNode = key.node,
                    step = key.step,
                    startWeek = startWeek,
                    endWeek = endWeek,
                    type = type,
                    color = defaultColor
                )
            }

            val spans = sorted.map { it.first to it.second }

            fun contiguous() = spans.zipWithNext().all { (a, b) -> b.first - a.second == 1 }
            fun allSingleSameParitySpaced2(): Boolean {
                if (spans.any { it.first != it.second }) return false
                val starts = spans.map { it.first }
                if (starts.map { it % 2 }.toSet().size != 1) return false
                return starts.zipWithNext().all { (a, b) -> b - a == 2 }
            }

            when {
                spans.size == 1 ->
                    emit(spans[0].first, spans[0].second, 0, sorted[0].third)
                // 1) 逐周连续 → 合并为每周区间(room 取首个 chunk)
                contiguous() ->
                    emit(spans.first().first, spans.last().second, 0, sorted[0].third)
                // 2) 全部单周且同奇偶且间距 2 → 单/双周序列(INTERVAL=2 单条事件同理)
                interval == 2 || allSingleSameParitySpaced2() -> {
                    val startW = spans.first().first
                    emit(startW, spans.last().first, if (startW % 2 == 1) 1 else 2, sorted[0].third)
                }
                // 3) 散周: 按教室分段合并连续段,每段一行(换教室课程按实际分段输出)
                else -> {
                    var curRoom: String? = null
                    var curStart = 0; var curEnd = 0
                    for ((a, b, r) in sorted) {
                        if (r == curRoom && a <= curEnd + 1) {
                            curEnd = maxOf(curEnd, b)
                        } else {
                            if (curRoom != null) emit(curStart, curEnd, 0, curRoom)
                            curRoom = r; curStart = a; curEnd = b
                        }
                    }
                    if (curRoom != null) emit(curStart, curEnd, 0, curRoom)
                }
            }
        }

        return ParseResult(
            tableName = "导入的 ICS 课表",
            startDate = anchor.toString(),
            courses = courses,
            timeJson = buildTimeJson(nodeTimes),
            nodesPerDay = if (nodeTimes.isEmpty()) 0 else nodeTimes.lastKey()
        )
    }

    /**
     * 从单个 VEVENT 收割节次边界: 事件占 [startNode, startNode+step-1] 节,
     * DTSTART=首节start, DTEND=末节end, 中间节次边界从相邻块推导(块内均匀)。
     * 冲突时后写覆盖 — 同校作息一致,不同事件只是补充对方缺的节次。
     */
    private fun harvestNodeTimes(
        block: String, startNode: Int, step: Int,
        out: TreeMap<Int, Pair<LocalTime, LocalTime>>
    ) {
        val dtstart = extractIcsField(block, "DTSTART") ?: return
        val dtend = extractIcsField(block, "DTEND") ?: return
        val start = runCatching { parseIcsTimeOfDay(dtstart.substringAfter("T").take(6)) }.getOrNull() ?: return
        val end = runCatching { parseIcsTimeOfDay(dtend.substringAfter("T").take(6)) }.getOrNull() ?: return
        if (!start.isBefore(end)) return

        val endNode = startNode + step - 1
        // 跨 lunch/晚上的多节块(如 1-4 @ 08:20-12:00)不能均匀切 — 只锚定两端,
        // 中间节次交给其他恰好落界的块(如 1-2/3-4)去补,补不上就保持 gap。
        if (step <= 2) {
            for (n in startNode..endNode) {
                val s = if (n == startNode) start else null
                val e = if (n == endNode) end else null
                val prev = out[n]
                out[n] = (s ?: prev?.first ?: start) to (e ?: prev?.second ?: end)
            }
        } else {
            val prevFirst = out[startNode]
            val prevLast = out[endNode]
            out[startNode] = start to (prevFirst?.second ?: end)
            out[endNode] = (prevLast?.first ?: start) to end
        }
    }

    /** nodeTimes → timeJson。空 → 空串(调用方用默认)。非连续节次照样输出存在的那些。 */
    private fun buildTimeJson(nodeTimes: TreeMap<Int, Pair<LocalTime, LocalTime>>): String {
        if (nodeTimes.isEmpty()) return ""
        val sb = StringBuilder("[")
        for ((n, t) in nodeTimes) {
            if (sb.length > 1) sb.append(',')
            sb.append("""{"node":$n,"start":"${t.first}","end":"${t.second}"}""")
        }
        sb.append(']')
        return sb.toString()
    }

    private fun parseIcsDate(s: String): java.time.LocalDate? = try {
        java.time.LocalDate.of(s.substring(0, 4).toInt(), s.substring(4, 6).toInt(), s.substring(6, 8).toInt())
    } catch (_: Exception) { null }

    /** 从 DTSTART 值提取日期部分(形如 20260831T082000 / 20260831) */
    private fun extractIcsDate(block: String): java.time.LocalDate? {
        val dtstart = extractIcsField(block, "DTSTART") ?: return null
        return parseIcsDate(dtstart.substringBefore("T").take(8))
    }

    /** 从 DESCRIPTION "第X - Y节" 提取节次; 兼容 Sleepy 自家导出(无此行,返回 null 走时间估算) */
    private fun extractIcsNode(description: String): Pair<Int, Int>? {
        val m = Regex("第\\s*(\\d+)\\s*[-–]\\s*(\\d+)\\s*节").find(description) ?: return null
        val a = m.groupValues[1].toInt()
        val b = m.groupValues[2].toInt()
        if (a < 1 || b < a) return null
        return a to (b - a + 1)
    }

    private fun extractIcsField(block: String, name: String): String? {
        // ICS 字段可能折行 (下一行以空格开头)
        val regex = Regex("(?m)^$name(?:;[^:]*)?:(.*(?:\\n .*)*)")
        return regex.find(block)?.groupValues?.get(1)
            ?.replace(Regex("\\n "), "")
            ?.trim()
    }

    /** 从 DTSTART/DTEND 提取节次（按 45min课+5min课间≈50min/节估算；DESCRIPTION 无"第X-Y节"时的兜底） */
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
            // 8:00 = 第 1 节; 节边界按 50min 周期近似(45课+5课间), +10min 防边界抖动
            val startNode = ((startMin - 480 + 10) / 50).toInt() + 1
            val step = Math.round(duration / 50.0).toInt().coerceAtLeast(1)
            Pair(startNode.coerceAtLeast(1), step)
        } catch (e: Exception) { null }
    }

    private fun parseIcsTimeOfDay(s: String): java.time.LocalTime =
        java.time.LocalTime.of(s.substring(0, 2).toInt(), s.substring(2, 4).toInt())

    /** 从 DTSTART 或 BYDAY 提取星期几。优先从 DTSTART 推算（最可靠） */
    private fun extractIcsDayOfWeek(block: String): Int? {
        // 1) 优先从 DTSTART 的日期推算星期几
        val dtstart = extractIcsField(block, "DTSTART")
        if (dtstart != null) {
            val dateStr = dtstart.substringBefore("T").take(8)  // yyyyMMdd
            if (dateStr.length == 8) {
                try {
                    val date = java.time.LocalDate.of(
                        dateStr.substring(0, 4).toInt(),
                        dateStr.substring(4, 6).toInt(),
                        dateStr.substring(6, 8).toInt()
                    )
                    val dow = date.dayOfWeek.value  // 1=Mon..7=Sun
                    if (dow in 1..7) return dow
                } catch (_: Exception) {}
            }
        }
        // 2) 退而求其次：找 RRULE.BYDAY
        val rrule = extractIcsField(block, "RRULE") ?: return null
        val match = Regex("BYDAY=([A-Z]{2})").find(rrule) ?: return null
        return when (match.groupValues[1]) {
            "MO" -> 1; "TU" -> 2; "WE" -> 3; "TH" -> 4
            "FR" -> 5; "SA" -> 6; "SU" -> 7
            else -> null
        }
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
            // 支持 tab / 多空格 / 全角逗号
            val parts = line
                .trim()
                .split(Regex("\\s+|，"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
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
                groupId = "",
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
        if (parts.size == 1) {
            // 单个数字: e.g. "5" → (5, 5)
            val n = parts[0].trim().toIntOrNull() ?: return null
            return n to n
        }
        if (parts.size != 2) return null
        val start = parts[0].toIntOrNull() ?: return null
        val end = parts[1].toIntOrNull() ?: return null
        return start to end
    }

    /**
     * 解析 CSV 格式课表。
     * 自动识别表头，常见列名（中文/英文）都可以：
     *   课程名 / 课程 / 名称 / course / name
     *   教师 / 老师 / teacher
     *   教室 / 位置 / 地点 / room / position
     *   星期 / 周几 / day
     *   节次（单列 1-2 格式） / 节点 / node / 节 / class
     *   开始节数 + 结束节数（两列）  — 教务处常见导出
     *   周次 / 周数 / weeks / week — 支持多区间 "2-5,7-9,11-14" / 离散周 "11,13,15" / 单周 "5"
     *   类型 / type
     *   备注 / note
     *
     * 多区间的周数会被展开成多条 CourseEntity（同一课程名在不同周上可以是不同教师/教室，
     * 实际是分多行表示的，展开后保持原始行数）
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
        // 节次列三种兼容模式
        val nodeStartIdx = findCol("开始节数", "开始节次", "起节", "节次起", "start node")
        val nodeEndIdx = findCol("结束节数", "结束节次", "止节", "节次止", "end node")
        val nodeIdx = if (nodeStartIdx == null && nodeEndIdx == null) {
            findCol("节次", "节点", "上课节次", "node", "节", "class")
        } else null
        val weekIdx = findCol("周次", "周数", "weeks", "week")
            ?: throw IllegalArgumentException("找不到周次列")
        val typeIdx = findCol("类型", "type", "周类型")
        val noteIdx = findCol("备注", "note", "remark")

        if (nodeStartIdx == null && nodeEndIdx == null && nodeIdx == null) {
            throw IllegalArgumentException("找不到节次列（需要 '节次' 或 '开始节数'+'结束节数'）")
        }

        val courses = mutableListOf<CourseEntity>()
        for (i in 1 until rows.size) {
            val row = rows[i]
            if (row.isEmpty() || row.all { it.isBlank() }) continue

            fun cell(idx: Int?): String = idx?.let { row.getOrNull(it)?.trim() ?: "" } ?: ""

            val name = cell(nameIdx)
            if (name.isBlank()) continue

            val dayRaw = cell(dayIdx)
            val day = parseDay(dayRaw) ?: continue

            // 解析节次 (start, end)
            val (nodeStart, nodeEnd) = if (nodeStartIdx != null && nodeEndIdx != null) {
                val s = cell(nodeStartIdx).toIntOrNull()
                val e = cell(nodeEndIdx).toIntOrNull()
                if (s == null || e == null) continue
                Pair(s, e)
            } else {
                parseRange(cell(nodeIdx)) ?: continue
            }
            val step = (nodeEnd - nodeStart + 1).coerceAtLeast(1)

            // 解析周次——支持多区间 "2-5,7-9,11-14" / 离散 "11,13,15" / 单周 "5" / 区间 "2-16"
            val weekRanges = parseWeekRanges(cell(weekIdx))
            if (weekRanges.isEmpty()) continue

            val teacher = cell(teacherIdx)
            val room = cell(roomIdx)
            val note = cell(noteIdx)
            val type = cell(typeIdx).let { parseType(it) }

            // 每个区间展开为一条 CourseEntity
            for ((startWeek, endWeek) in weekRanges) {
                courses += CourseEntity(
                    id = 0,
                    groupId = "",
                    tableId = defaultTableId,
                    courseName = name,
                    teacher = teacher,
                    room = room,
                    note = note,
                    day = day,
                    startNode = nodeStart,
                    step = step,
                    startWeek = startWeek,
                    endWeek = endWeek,
                    type = type,
                    color = defaultColor
                )
            }
        }

        if (courses.isEmpty()) throw IllegalArgumentException("未能解析任何课程")

        return ParseResult(
            tableName = "导入的 CSV 课表",
            startDate = java.time.LocalDate.now().toString(),
            courses = courses
        )
    }

    /**
     * 解析周数字段，支持:
     *   "5"              → [(5, 5)]
     *   "2-16"           → [(2, 16)]
     *   "2-5,7-9,11-14"  → [(2, 5), (7, 9), (11, 14)]
     *   "11,13,15,17"    → [(11, 11), (13, 13), (15, 15), (17, 17)]
     *   "2-5,11"         → [(2, 5), (11, 11)]
     */
    private fun parseWeekRanges(s: String): List<Pair<Int, Int>> {
        if (s.isBlank()) return emptyList()
        val result = mutableListOf<Pair<Int, Int>>()
        s.split(',', '，', ';', '；').forEach { part ->
            val t = part.trim()
            if (t.isEmpty()) return@forEach
            val pair = parseRange(t) ?: return@forEach
            result += pair
        }
        return result
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
        val nodeStartIdx = findCol("开始节数", "开始节次", "起节", "节次起", "start node")
        val nodeEndIdx = findCol("结束节数", "结束节次", "止节", "节次止", "end node")
        val nodeIdx = if (nodeStartIdx == null && nodeEndIdx == null) {
            findCol("节次", "节点", "node", "上课节次")
        } else null
        val weekIdx = findCol("周次", "周数", "weeks", "week")
        val typeIdx = findCol("类型", "type")
        val noteIdx = findCol("备注", "note")

        if (nodeStartIdx == null && nodeEndIdx == null && nodeIdx == null) {
            return emptyList()
        }
        if (weekIdx == null) {
            return emptyList()
        }

        val courses = mutableListOf<CourseEntity>()
        for (i in (headerIdx + 1) until rows.size) {
            val row = rows[i]
            if (row.isEmpty() || row.all { it.isBlank() }) continue
            fun cell(idx: Int?): String = idx?.let { row.getOrNull(it)?.trim() ?: "" } ?: ""

            val name = cell(nameIdx)
            if (name.isBlank()) continue

            val day = parseDay(cell(dayIdx))
            if (day == null) continue
            // 节点：开始/结束两列 或 单列 range
            val (nodeStart, nodeEnd) = if (nodeStartIdx != null && nodeEndIdx != null) {
                val s = cell(nodeStartIdx).toIntOrNull()
                val e = cell(nodeEndIdx).toIntOrNull()
                if (s == null || e == null) continue
                Pair(s, e)
            } else {
                parseRange(cell(nodeIdx)) ?: continue
            }
            val step = (nodeEnd - nodeStart + 1).coerceAtLeast(1)
            val weekRanges = parseWeekRanges(cell(weekIdx))
            if (weekRanges.isEmpty()) continue
            val type = parseType(cell(typeIdx))

            val teacher = cell(teacherIdx)
            val room = cell(roomIdx)
            val note = cell(noteIdx)
            for ((startWeek, endWeek) in weekRanges) {
                courses += CourseEntity(
                    id = 0,
                    groupId = "",
                    tableId = defaultTableId,
                    courseName = name,
                    teacher = teacher,
                    room = room,
                    note = note,
                    day = day,
                    startNode = nodeStart,
                    step = step,
                    startWeek = startWeek,
                    endWeek = endWeek,
                    type = type,
                    color = defaultColor
                )
            }
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
        val courses: List<CourseEntity>,
        /** ICS 事件里收割出的节次时间表 JSON; 非ICS/收割不到 → 空串(用默认) */
        val timeJson: String = "",
        /** timeJson 覆盖的最大节次; 无 → 0 */
        val nodesPerDay: Int = 0
    )
}