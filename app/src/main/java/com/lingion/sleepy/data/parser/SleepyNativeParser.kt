package com.lingion.sleepy.data.parser

import com.lingion.sleepy.data.entity.CourseEntity
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * sleepy-v1 解析器(规范 §3/§4/§5/§7)。
 *
 * 三态处置: 空=默认(不上报) · 形状合法但越界=钳制+上报 · 形状非法=整行丢弃+上报。
 * 上报双通道: droppedLines(行级) + ParseResult.warnings(表级)。
 * groupId 分区: 非空 token 按分区; 空 token 按归一化课名(复刻 assignGroupIds 语义);
 * 生成的最终 groupId 由 groupIdsAuthoritative=true 声明权威, 落库绕过再分配(§3.4 契约一)。
 */
internal object SleepyNativeParser {

    /** 两遍解析: 先扫 T 行(表级), 再解析其余行 */
    fun parse(trimmed: String, defaultTableId: Long, defaultColor: String): ScheduleParser.ParseResult {
        val lines = trimmed.lineSequence().map { it.trimEnd('\r') }.toList()
        val dropped = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // ---- pass 1: T 行 ----
        var tSeen = false
        var tableName = ""
        var startDateStr = ""
        var maxWeekRaw: Int? = null
        var nodesPerDayRaw: Int? = null
        var declaredCount: Int? = null
        var bodyStart = 0
        var foundMagic = false

        for ((idx, raw) in lines.withIndex()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (!foundMagic) {
                if (SleepyNativeFormat.detectVersion(line) in 1..Int.MAX_VALUE && idx < 32) {
                    // detectVersion 逐行调用时窗口语义由外层控制; 这里简单判定单行
                }
                // 单行探测: 该行本身是不是 magic
                if (isMagicLine(line)) { foundMagic = true; bodyStart = idx + 1 }
                continue
            }
            if (!tSeen && line.uppercase().startsWith("T") && !line.uppercase().startsWith("ND")) {
                // 显式记录已被 pass1 消化; pass2 走到同前缀时按"二次 T"语义入 dropped
                tSeen = true
                val cols = splitCols(line, 1, dropped, warnings)
                if (cols != null) {
                    tableName = unescapeCol(cols.getOrNull(0)) ?: ""
                    startDateStr = cols.getOrNull(1)?.trim() ?: ""
                    maxWeekRaw = cols.getOrNull(2)?.trim()?.toIntOrNull()
                    nodesPerDayRaw = cols.getOrNull(3)?.trim()?.toIntOrNull()
                    // 扩展键区: key=value
                    for (kv in cols.drop(4)) {
                        val eq = kv.indexOf('=')
                        if (eq > 0) {
                            val k = kv.substring(0, eq).trim()
                            val v = kv.substring(eq + 1).trim()
                            if (k == "n") declaredCount = v.toIntOrNull()
                            // 未知键静默忽略(v2 契约)
                        }
                    }
                }
            }
        }
        if (!foundMagic) throw IllegalArgumentException("内部错误: sleepy-v1 解析器被无 magic 文本调用")

        // ---- 表级默认与钳制(§3.5) ----
        val startDate: String = when {
            startDateStr.isEmpty() -> todayMonday()
            else -> SleepyNativeFormat.parseDate(startDateStr) ?: run {
                warnings.add("开始日期「$startDateStr」无法解析，已使用今天")
                todayMonday()
            }
        }
        val maxWeekClamped = clampField(maxWeekRaw, 20, 1, 60, "总周数", warnings)
        val declaredNodes = clampField(nodesPerDayRaw, null, 1, 30, "每天节数", warnings)

        // ---- pass 2: 正文行 ----
        val courses = mutableListOf<CourseEntity>()
        val nodeTimes = sortedMapOf<Int, Pair<LocalTime, LocalTime>>()
        val ndApplied = mutableSetOf<Int>()
        var ndSeen = false
        val seenExactLines = mutableSetOf<String>()
        var seenCourseLines = false
        var secondTableHeader = false
        var chkExpected: String? = null
        var chkLine: String? = null

        for (raw in lines.drop(bodyStart)) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#")) {
                // 注释行(非 magic)忽略 — 但 magic 行恰好以 # 开头, 必须先判定
                if (isMagicLine(line)) {
                    dropped.add(shorten(line))
                    secondTableHeader = true
                    continue
                }
                continue
            }
            if (isMagicLine(line)) {
                // 二次 magic: 不硬拒, 整行上报 + warning(§6.3-P)
                dropped.add(shorten(line))
                secondTableHeader = true
                continue
            }
            if (seenExactLines.contains(line)) { dropped.add(shorten(line)); continue }  // 字节级重复(§7.9)
            val prefix = line.substring(0, 1).uppercase()

            when (prefix) {
                "T" -> {
                    // 首次 T 行已被 pass 1 消化(tSeen=true), 这里跳过它; 二次 T 行入 dropped
                    if (tSeen) continue
                    dropped.add(shorten(line))
                }
                "Z" -> {
                    // z|chk=crc32:xxxxxxxx — 记录, 结束时校验
                    chkLine = line
                }
                "N" -> {
                    if (line.length >= 2 && (line[1] == 'd' || line[1] == 'D')) {
                        ndSeen = true
                    } else {
                        seenExactLines.add(line)
                        parseNodeLine(line, nodeTimes, dropped)
                    }
                }
                "C" -> {
                    seenCourseLines = true
                    seenExactLines.add(line)
                    parseCourseLine(
                        line, defaultTableId, defaultColor, tableName, courses, dropped
                    )
                }
                else -> {
                    // 未知行类型(v2 信号)或无前缀垃圾 → dropped(诚实上报)
                    dropped.add(shorten(line))
                }
            }
        }

        // Nd 展开(§5: 冻结 12 节常量)
        if (ndSeen) {
            for (i in SleepyNativeFormat.ND_PRESET.indices) {
                val node = i + 1
                if (!nodeTimes.containsKey(node) && node !in ndApplied) {
                    nodeTimes[node] = SleepyNativeFormat.ND_PRESET[i]
                }
            }
        }

        // chk 校验(§6.3-Q: 警告不硬拒)
        if (chkLine != null) {
            val m = Regex("""chk=([a-z0-9]+):([0-9a-fA-F]{8})""").find(chkLine!!)
            if (m != null) {
                val algo = m.groupValues[1]
                if (algo == "crc32") {
                    // 校验范围 = magic 行(含)至 z 行(不含)的全部字节, 即 trimmed 中 z 行之前 + 结尾换行前的原文
                    val zIdx = lines.indexOfFirst { it.trim() == chkLine }
                    if (zIdx > bodyStart - 1) {
                        // 用原文(未 trim 前)行 join —— 导出端对 body(不带尾 LF)计算 crc
                        val bodyToChk = lines.subList(0, zIdx).joinToString("\n")
                        val actual = SleepyNativeFormat.crc32(bodyToChk.toByteArray(Charsets.UTF_8))
                        if (!actual.equals(m.groupValues[2], ignoreCase = true)) {
                            warnings.add("完整性校验不符，文件可能被截断或修改")
                        }
                    }
                } else {
                    warnings.add("未知校验算法 $algo，已跳过校验")
                }
                chkExpected = m.groupValues[2]
            }
        }

        // n= 计数核对(§8.3: 警告不硬拒)
        if (declaredCount != null && declaredCount != courses.size) {
            warnings.add("课程行计数 n=$declaredCount 与实际 ${courses.size} 不符，文件可能被截断")
        }
        if (secondTableHeader) {
            warnings.add("检测到第 2 张表头，其课程已并入当前表")
        }

        // nodesPerDay = max(声明, 课程到达)(§5 优先级)
        val courseReach = courses.maxOfOrNull { it.startNode + it.step - 1 } ?: 0
        var nodesPerDay = declaredNodes ?: maxOf(12, nodeTimes.keys.maxOrNull() ?: 0)
        if (declaredNodes == null && nodeTimes.isEmpty() && courses.isNotEmpty()) {
            nodesPerDay = maxOf(12, courseReach)
        }
        if (courseReach > nodesPerDay) {
            nodesPerDay = courseReach
            warnings.add("课程到达第 $courseReach 节，超过声明的每天节数，已自动扩展")
        }

        // timeJson 序列化(稀疏语义: 只写声明过的节)
        val timeJson = if (nodeTimes.isEmpty()) "" else {
            nodeTimes.entries.joinToString(",", "[", "]") { (node, se) ->
                """{"node":$node,"start":"${SleepyNativeFormat.fmtTime(se.first)}","end":"${SleepyNativeFormat.fmtTime(se.second)}"}"""
            }
        }

        // ---- groupId 分区(§3.4) ----
        assignFinalGroupIds(courses, tableName)

        // ---- 空表/全丢二分(§7.8) ----
        // 仅在 C 前缀行存在但全部被丢时失败; 0 行 C 前缀 = 空表成功
        if (seenCourseLines && courses.isEmpty()) {
            throw IllegalArgumentException(
                "未能解析任何课程（${dropped.size} 行没进去）：${dropped.take(3).joinToString(" / ")}"
            )
        }

        return ScheduleParser.ParseResult(
            tableName = tableName.ifBlank { "" },
            startDate = startDate,
            courses = courses,
            timeJson = timeJson,
            nodesPerDay = nodesPerDay,
            droppedLines = dropped,
            warnings = warnings,
            maxWeek = maxWeekClamped ?: 20,
            groupIdsAuthoritative = true
        )
    }

    private fun isMagicLine(line: String): Boolean =
        SleepyNativeFormat.detectVersion(line) >= 1

    /** 按半角 | 切列; 恰差 1 列时全角｜作次级分隔符重切一次(§7.6) */
    private fun splitCols(line: String, prefixLen: Int, dropped: MutableList<String>, warnings: MutableList<String>): List<String>? {
        val body = line.substring(prefixLen)
        val cols = splitRespectingEscape(body)
        return cols
    }

    /** 反斜杠感知的竖线切分: \| 不是分隔符 */
    private fun splitRespectingEscape(body: String): List<String> {
        val cols = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                c == '\\' && i + 1 < body.length -> { sb.append(c).append(body[i + 1]); i += 2 }
                c == '|' -> { cols.add(sb.toString()); sb.clear(); i++ }
                else -> { sb.append(c); i++ }
            }
        }
        cols.add(sb.toString())
        return cols
    }

    private fun unescapeCol(col: String?): String? = col?.let { SleepyNativeFormat.unescape(it.trim()) }

    private fun todayMonday(): String =
        LocalDate.now().with(java.time.DayOfWeek.MONDAY).toString()

    private fun clampField(raw: Int?, default: Int?, lo: Int, hi: Int, label: String, warnings: MutableList<String>): Int? {
        if (raw == null) return default
        if (raw < lo) { warnings.add("${label} $raw 低于下限，已调整为 $lo"); return lo }
        if (raw > hi) { warnings.add("${label} $raw 超过上限，已调整为 $hi"); return hi }
        return raw
    }

    // ---- N 行 (§5) ----

    private fun parseNodeLine(
        line: String,
        nodeTimes: MutableMap<Int, Pair<LocalTime, LocalTime>>,
        dropped: MutableList<String>
    ) {
        val body = line.substring(1)
        val cols = splitRespectingEscape(body)
        val nodeNo = cols.getOrNull(0)?.trim()?.toIntOrNull()
        val start = cols.getOrNull(1)?.let { SleepyNativeFormat.parseClock(it) }
        val end = cols.getOrNull(2)?.let { SleepyNativeFormat.parseClock(it) }
        val ok = nodeNo != null && nodeNo > 0 && start != null && end != null && start.isBefore(end)
        if (!ok) {
            dropped.add(shorten(line))
            return
        }
        if (nodeTimes.containsKey(nodeNo)) {
            dropped.add(shorten(line))  // 重复节号: 首行生效
            return
        }
        nodeTimes[nodeNo!!] = start!! to end!!
    }

    // ---- C 行 (§3.1, 恒 10 列) ----

    private fun parseCourseLine(
        line: String,
        defaultTableId: Long,
        defaultColor: String,
        tableName: String,
        courses: MutableList<CourseEntity>,
        dropped: MutableList<String>
    ) {
        var cols = splitRespectingEscape(line.substring(1))
        // 全角｜次级分隔符: 仅当半角切分列数 < 10 且全角重切恰好补齐时(§7.6)
        if (cols.size < 10 && cols.none { it.contains('|') }) {
            val alt = splitRespectingEscape(line.substring(1).replace('｜', '|'))
            // 注意: 上面的 replace 也会替换被转义域内的｜ — 罕见且手输容忍, 精确重切仅在 alt.size == 10 时采纳
            if (alt.size == 10) cols = alt
        }

        fun col(i: Int): String = cols.getOrNull(i)?.trim() ?: ""
        fun text(i: Int): String = SleepyNativeFormat.unescape(col(i))

        val name = text(0)
        if (name.isEmpty()) { dropped.add(shorten(line)); return }  // 名称是行存在性唯一充分条件
        if (name.contains('�')) { dropped.add(shorten(line)); return }  // GBK 乱码 → 响亮丢弃(规范 §7.7)

        // day(2列)
        val dayRaw = col(1)
        val dayParsed = SleepyNativeFormat.parseDay(dayRaw)
        val day = when {
            dayRaw.isEmpty() -> 1
            dayParsed == null -> { dropped.add(shorten(line)); return }
            dayParsed < 1 || dayParsed > 7 -> dayParsed.coerceIn(1, 7).also { markClamped(line, dropped) }
            else -> dayParsed
        }

        // nodeSpan(3列)
        val spanRaw = col(2)
        val span = when {
            spanRaw.isEmpty() -> 1 to 1
            else -> SleepyNativeFormat.parseNodeSpan(spanRaw) ?: run { dropped.add(shorten(line)); return }
        }
        var (nodeStart, nodeEnd) = span
        if (nodeEnd < nodeStart) { val t = nodeStart; nodeStart = nodeEnd; nodeEnd = t; markClamped(line, dropped) }
        if (nodeStart < 1) { nodeStart = 1; markClamped(line, dropped) }
        val step = nodeEnd - nodeStart + 1

        // weekSpec(4列)
        val weekRaw = col(3)
        val weekParsed = when {
            weekRaw.isEmpty() -> SleepyNativeFormat.WeekSpec(1, 16, 3)
            else -> SleepyNativeFormat.parseWeekSpec(weekRaw) ?: run { dropped.add(shorten(line)); return }
        }
        var (wStart, wEnd) = weekParsed.start to weekParsed.end
        if (wEnd < wStart) { val t = wStart; wStart = wEnd; wEnd = t; markClamped(line, dropped) }
        if (wStart < 1) { wStart = 1; markClamped(line, dropped) }
        if (wEnd > 300) { wEnd = 300; markClamped(line, dropped) }

        // teacher(5) room(6) — 自由文本无非法态
        val teacher = text(4)
        val room = text(5)

        // color(7)
        val colorRaw = col(6)
        val color = when {
            colorRaw.isEmpty() || colorRaw == "0" -> defaultColor.ifBlank { SleepyNativeFormat.AUTO_COLOR }
            else -> SleepyNativeFormat.colorFromToken(colorRaw).let {
                if (colorRaw.toIntOrNull() != null && colorRaw.toIntOrNull()!! > 9) markClamped(line, dropped)
                it
            }
        }

        // note(8)
        val note = text(7)

        // timeSpan(9)
        val timeRaw = col(8)
        var ownTime = false
        var startTime = ""
        var endTime = ""
        if (timeRaw.isNotEmpty()) {
            val parts = timeRaw.split('-', '～', '~')
            if (parts.size == 2) {
                val st = SleepyNativeFormat.parseClock(parts[0])
                val et = SleepyNativeFormat.parseClock(parts[1])
                if (st != null && et != null && st.isBefore(et)) {
                    ownTime = true
                    startTime = SleepyNativeFormat.fmtTime(st)
                    endTime = SleepyNativeFormat.fmtTime(et)
                } else {
                    markClamped(line, dropped)  // 时间列非法: 课程仍按节点落位
                }
            } else {
                markClamped(line, dropped)
            }
        }

        // group(10)
        val token = text(9)

        courses.add(
            CourseEntity(
                id = 0,
                groupId = "",   // 分区在 pass 结束后统一分配
                tableId = defaultTableId,
                courseName = name,
                teacher = teacher,
                room = room,
                note = note,
                day = day,
                startNode = nodeStart,
                step = step,
                startWeek = wStart,
                endWeek = wEnd,
                type = weekParsed.type,
                color = color,
                ownTime = ownTime,
                startTime = startTime,
                endTime = endTime
            )
        )
        // token 存到临时通道 — 见下方 tokenByIndex
        tokenByIndex[courses.size - 1] = token
    }

    /** C 行第 10 列 token 的临时通道(不污染 CourseEntity; parse 结束时清空) */
    private val tokenByIndex = mutableMapOf<Int, String>()

    /** 已入库行发生过钳制 → 整行原文入 dropped(§7.3) */
    private fun markClamped(line: String, dropped: MutableList<String>) {
        dropped.add(shorten(line))
    }

    private fun shorten(line: String): String = line.take(40)

    /** §3.4: 非空 token 按分区; 空 token 按归一化课名; 确定性 UUID */
    private fun assignFinalGroupIds(courses: MutableList<CourseEntity>, tableName: String) {
        val tokenGroups = mutableMapOf<String, String>()
        val nameGroups = mutableMapOf<String, String>()
        val size = courses.size
        for (idx in 0 until size) {
            val c = courses[idx]
            val token = tokenByIndex[idx] ?: ""
            val gid = if (token.isNotEmpty()) {
                tokenGroups.getOrPut(token) {
                    UUID.nameUUIDFromBytes((tableName + "|" + token).toByteArray()).toString()
                }
            } else {
                val key = c.courseName.trim().replace(Regex("\\s+"), " ").lowercase()
                nameGroups.getOrPut(key) {
                    UUID.nameUUIDFromBytes((tableName + "|" + key).toByteArray()).toString()
                }
            }
            courses[idx] = c.copy(groupId = gid)
        }
        tokenByIndex.clear()
    }
}

/** 解析中间态载体 — 临时 token 附加, 解析后移除 */
private class MetadataToken(val token: String)
