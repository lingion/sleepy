package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.Locale

/** Compatibility parsers for WakeUp protocol variants. */
private object WakeUpCompat {
    fun parse(source: String, markers: Set<String>, jsonHint: Boolean = false): List<JwCourse> {
        parseJson(source)?.let { root ->
            parseJsonCourses(root).takeIf { it.isNotEmpty() }?.let { return it }
            parseJzJson(root).takeIf { it.isNotEmpty() }?.let { return it }
        }
        parseJzHtml(source).takeIf { it.isNotEmpty() }?.let { return it }
        parseMarkedTable(source, markers).takeIf { it.isNotEmpty() }?.let { return it }
        return if (jsonHint) emptyList() else parseDelimited(source)
    }

    /** JZHandCourseInfoItem JSON: xqj=day, djj=起始节, qmz="1-8;10-16", dsz==1→单 / dsz!=2→双 / else 每周 (WakeUp 反转语义). */
    private fun parseJzJson(root: Any): List<JwCourse> {
        val rows = root as? JSONArray ?: return emptyList()
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val name = row.optString("kcmc")
                val day = row.optInt("xqj", 0)
                val start = row.optInt("djj", 0)
                if (name.isBlank() || day !in 1..7 || start < 1) continue
                val type = when (row.optInt("dsz", 0)) { 1 -> 1; 2 -> 0; else -> 2 }
                row.optString("qmz").split(';', '；').forEach { token ->
                    val nums = Regex("\\d+").findAll(token).map { it.value.toInt() }.toList()
                    if (nums.isNotEmpty()) add(JwCourse(name, row.optString("skdd"), row.optString("jsxm"), day, start, start, nums.first(), nums.getOrNull(1) ?: nums.first(), type))
                }
            }
        }
    }

    /** JZ HTML fallback: table#CourseFormTable; 节次行 td 按 <hr> 分块, 每块 <br> 分字段: [0]课名, [1]周次, [2]教师, "第a-b节", 末位地点. */
    private fun parseJzHtml(source: String): List<JwCourse> {
        val table = Jsoup.parse(source).selectFirst("#CourseFormTable") ?: return emptyList()
        return buildList {
            for (row in table.select("tr")) {
                val tds = row.select("td")
                if (tds.any { it.text().contains("星期") }) continue
                for ((col, td) in tds.withIndex()) {
                    if (td.attr("style").contains("center")) continue
                    val day = col + 1
                    for (chunk in td.html().split("<hr>")) {
                        val f = chunk.split("<br>").map { Jsoup.parse(it).text().trim() }.filter { it.isNotEmpty() }
                        if (f.size < 2) continue
                        val name = f[0].substringBefore('(').trim()
                        if (name.isBlank()) continue
                        val weeksText = f.firstOrNull { it.firstOrNull()?.isDigit() == true } ?: continue
                        val nodeField = f.firstOrNull { Regex("第\\s*\\d+").containsMatchIn(it) && it != weeksText }
                        val nums = nodeField?.let { Regex("\\d+").findAll(it).map { m -> m.value.toInt() }.toList() }
                        val start = nums?.getOrNull(0) ?: 1
                        val end = nums?.getOrNull(1) ?: start
                        val room = f.lastOrNull { it != name && it != weeksText && it != nodeField } ?: ""
                        val teacher = f.firstOrNull { it != name && it != weeksText && it != nodeField && it != room } ?: ""
                        parseWeekTokens(weeksText).forEach { (from, to, type) ->
                            add(JwCourse(name, room, teacher, day, start, end, from, to, type))
                        }
                    }
                }
            }
        }
    }

    /** KingoInfo JSON: 顶层数组, 行 week1..week7 → day=列号, 值=[{jcxx:"a-b", kcmc, skdd, rkjs, xf}], week=row 索引. */
    private fun parseKingoInfo(root: Any): List<JwCourse> {
        val rows = root as? JSONArray ?: return emptyList()
        if (rows.length() == 0) return emptyList()
        return buildList {
            for (r in 0 until rows.length()) {
                val row = rows.optJSONObject(r) ?: continue
                for (d in 1..7) {
                    val cells = row.optJSONArray("week$d") ?: continue
                    for (c in 0 until cells.length()) {
                        val cell = cells.optJSONObject(c) ?: continue
                        val name = cell.optString("kcmc")
                        val bounds = cell.optString("jcxx").split('-')
                        val start = bounds.firstOrNull()?.trim()?.toIntOrNull() ?: continue
                        if (name.isBlank() || start < 1) continue
                        add(JwCourse(name, cell.optString("skdd"), cell.optString("rkjs"), d, start, bounds.getOrNull(1)?.trim()?.toIntOrNull() ?: start, r, r, 0))
                    }
                }
            }
        }
    }

    fun parseChaoxing(source: String): List<JwCourse> {
        val root = parseJson(source) as? JSONObject ?: return emptyList()
        val rows = root.optJSONObject("data")?.optJSONArray("kckbData") ?: root.optJSONArray("kckbData") ?: return emptyList()
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val name = stripMarkup(row.optString("kcmc"))
                val day = row.optString("xq").toIntOrNull() ?: continue
                val node = row.optInt("djc", 0)
                if (name.isBlank() || day !in 1..7 || node < 1) continue
                val type = row.optString("zctype").toIntOrNull() ?: 0
                row.optString("zc").split(',').forEach { token ->
                    val weeks = Regex("\\d+").findAll(token).map { it.value.toInt() }.toList()
                    if (weeks.isNotEmpty()) add(JwCourse(name, stripMarkup(row.optString("croommc")), stripMarkup(row.optString("tmc")), day, node, node, weeks.first(), weeks.getOrNull(1) ?: weeks.first(), type))
                }
            }
        }
    }

    fun parseCumtb(source: String): List<JwCourse> {
        val payload = (parseJson(source) as? JSONObject)?.optJSONObject("result") ?: return emptyList()
        val names = mutableMapOf<Int, String>()
        val lessons = payload.optJSONArray("lessonList") ?: JSONArray()
        for (i in 0 until lessons.length()) lessons.optJSONObject(i)?.let { names[it.optInt("id", -1)] = it.optString("courseName") }
        val schedules = payload.optJSONArray("scheduleList") ?: return emptyList()
        return buildList {
            for (i in 0 until schedules.length()) {
                val row = schedules.optJSONObject(i) ?: continue
                // WakeUp oo000o.java:45 未知 lesson → "未知" 仍导入, 不丢行
                val name = names[row.optInt("lessonId", -1)].orEmpty().ifBlank { "未知" }
                val day = row.optInt("weekday", 0)
                val week = row.optInt("weekIndex", 0)
                val startTime = row.optInt("startTime", 0)
                val start = timeToNode(startTime)
                if (day !in 1..7 || week < 1 || start < 1) continue
                val count = durationToNodes(startTime, row.optInt("endTime", 0))
                add(JwCourse(name, row.optJSONObject("room")?.optString("nameZh").orEmpty(), row.optString("personName"), day, start, start + count - 1, week, week))
            }
        }
    }

    fun parseSouthSoft(source: String): List<JwCourse> {
        val root = parseJson(source)
        val rows = when (root) {
            is JSONArray -> root
            is JSONObject -> root.optJSONArray("value") ?: root.optJSONArray("data") ?: return emptyList()
            else -> return emptyList()
        }
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                if (row.optString("KEY") == "bz") continue
                val day = Regex("xq(\\d+)_jc\\d+").find(row.optString("KEY"))?.groupValues?.get(1)?.toIntOrNull() ?: continue
                val fields = Regex("\\[(.*?)\\]").findAll(row.optString("SKSJ")).map { it.groupValues[1].trim() }.toList()
                if (fields.size < 5) continue
                val nodes = fields[4].replace("第", "").replace("节", "").split('-')
                val start = nodes.firstOrNull()?.toIntOrNull() ?: continue
                val end = nodes.getOrNull(1)?.toIntOrNull() ?: start
                fields[1].split(',').forEach { token ->
                    val weeks = Regex("\\d+").findAll(token).map { it.value.toInt() }.toList()
                    if (weeks.isNotEmpty()) add(JwCourse(row.optString("KCWZSM").ifBlank { fields[0] }, fields[4], fields[2], day, start, end, weeks.first(), weeks.getOrNull(1) ?: weeks.first(), when { token.contains("单") -> 1; token.contains("双") -> 2; else -> 0 }))
                }
            }
        }
    }

    /** Kingo TaskActivity JS grid: "activity = new TaskActivity(name,teacher,room,weeksBinary)"; index=a*unitCount+b → day=a+1, node=b+1; '1'@p → week p+1, 连续段合并成组, 全奇→单周/全偶→双周. */
    fun parseKingoTaskActivity(source: String): List<JwCourse> {
        if (!source.contains("TaskActivity")) return emptyList()
        val block = Regex("var[\\s]*activity[\\s]*=[\\s]*null;[\\w\\W]*(?=table0\\.marshalTable)").find(source)?.value ?: return emptyList()
        data class Pending(val name: String, val room: String, val teacher: String, val weeks: List<Int>)
        val pending = ArrayDeque<Pending>()
        return buildList {
            var name = ""
            var teacher = ""
            for (stmt in block.split('\n').map { it.trim() }) {
                Regex("courseName[\\s]*\\+?=[\\s]*[\"']([^\"']*)[\"']").find(stmt)?.let { name += it.groupValues[1] }
                Regex("[\"']name[\"']\\s*:\\s*[\"']([^\"']*)[\"']").findAll(stmt).toList().takeIf { it.isNotEmpty() }?.let { ms -> teacher = ms.joinToString(",") { it.groupValues[1] } }
                if (Regex("new[\\s]*TaskActivity[\\s]*\\(").containsMatchIn(stmt)) {
                    val args = Regex("['\"]([^'\"]*)['\"]").findAll(stmt).toList()
                    if (args.size >= 4) {
                        val tName = args[0].groupValues[1].substringBefore('(').ifBlank { name }
                        args.getOrNull(1)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.let { teacher = it }
                        val room = args[2].groupValues[1]
                        val weeksBinary = args[3].groupValues[1]
                        val weeks = weeksBinary.mapIndexedNotNull { pos, ch -> if (ch == '1') pos + 1 else null }
                        if (tName.isNotBlank() && weeks.isNotEmpty()) pending += Pending(tName, room, teacher, weeks)
                    }
                }
                Regex("index[\\s]*=[\\s]*(\\d+)[\\s]*\\*[\\s]*unitCount[\\s]*\\+[\\s]*(\\d+)").find(stmt)?.let { m ->
                    // 跨仓 4 源一致: WakeUp o0000OO0.java i10=parseInt(g1)+1 / CourseHelper Swift dayOfWeek=match[1]
                    // / shiguang HUNNU+UESTC day=D+1 — index 首因子是 0-based day, +1 才是星期几
                    val day = m.groupValues[1].toInt() + 1
                    val node = m.groupValues[2].toInt().let { if (it == 13) 10 else if (it < 9) it + 1 else it + 2 }
                    pending.removeLastOrNull()?.let { p ->
                        var i = 0
                        while (i < p.weeks.size) {
                            var j = i
                            while (j + 1 < p.weeks.size && (p.weeks[j + 1] == p.weeks[j] + 1 || (p.weeks[j + 1] == p.weeks[j] + 2 && p.weeks[j] % 2 == 1 && p.weeks[j + 1] % 2 == 1))) j++
                            val run = p.weeks[i]..p.weeks[j]
                            val type = when {
                                run.first % 2 == 1 && run.last % 2 == 1 -> 1
                                run.first % 2 == 0 && run.last % 2 == 0 -> 2
                                else -> 0
                            }
                            add(JwCourse(p.name, p.room, p.teacher, day, node, node, run.first, run.last, type))
                            i = j + 1
                        }
                    }
                }
            }
        }
    }

    /** XJU dgData: 兼容两种表头形态。
     *  - 形态 A: th="节次|周X" 无时间列, 节次=阿拉伯数字, 单元格 "｛名(周次)[教师:X,地点:Y]｝"
     *  - 形态 B (真实 Gwork 学期课表信息查询, 2026-09-17 用户报修): th="时间|节次|周X",
     *    时间列 rowspan=4, 节次=中文数字"一"..."十二", 课程名在花括号**外**,
     *    单元格 "名｛周次[教师:X,地点:Y]｝", 课程行用 rowspan 跨节次合并.
     *  通用通路: 还原 (col, row) 网格, rowspan 残留补齐, 映射 (day, node). */
    fun parseXju(source: String): List<JwCourse> {
        val table = Jsoup.parse(source).selectFirst("#ctl00_contentParent_dgData")
            ?: Jsoup.parse(source).selectFirst("#contentParent_dgData")
            ?: return emptyList()
        val rows = table.select("tr")
        if (rows.isEmpty()) return emptyList()
        val ths = rows.first()!!.select("th")
        if (ths.isEmpty()) return emptyList()
        val firstDayIdx = ths.indexOfFirst { Regex("星期[一二三四五六日天]").containsMatchIn(it.text()) }
        if (firstDayIdx < 0) return emptyList()
        val firstDayLabel = ths[firstDayIdx].text()
        val dayOfFirstCol = when {
            firstDayLabel.contains("星期一") -> 1
            firstDayLabel.contains("星期二") -> 2
            firstDayLabel.contains("星期三") -> 3
            firstDayLabel.contains("星期四") -> 4
            firstDayLabel.contains("星期五") -> 5
            firstDayLabel.contains("星期六") -> 6
            else -> 7
        }

        fun nodeLabelToInt(raw: String): Int? {
            val trimmed = raw.trim()
            trimmed.toIntOrNull()?.let { if (it in 1..20) return it }
            val cn = mapOf(
                "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6,
                "七" to 7, "八" to 8, "九" to 9, "十" to 10, "十一" to 11, "十二" to 12
            )
            cn[trimmed]?.let { return it }
            // 形态 A 行标 "第1-2节": 取区间起点 (连堂由 rowspan/垂直合并补足终点)
            Regex("""第(\d+)\s*[-–~]\s*\d+节""").find(trimmed)?.groupValues?.get(1)?.toIntOrNull()?.let {
                if (it in 1..20) return it
            }
            return null
        }

        val dataRows = rows.drop(1)

        // 第一遍: rowIdx → 节次号 (行内第一个 align=center 数字/中文数字 td)
        val rowToNode = IntArray(dataRows.size) { 0 }
        var lastNode = 0
        for ((rowIdx, row) in dataRows.withIndex()) {
            var thisNode = 0
            for (td in row.select("td")) {
                if (td.attr("align").lowercase() == "center") {
                    val n = nodeLabelToInt(td.text().trim())
                    if (n != null) { thisNode = n; break }
                }
            }
            if (thisNode == 0) thisNode = lastNode else lastNode = thisNode
            rowToNode[rowIdx] = thisNode
        }

        // 第二遍: (col, rowIdx) → text (数据格)
        // 关键: 每行的 col 游标必须先跳过"上方 rowspan 残留占据的列" — 否则带时间列/rowspan 的
        // 课表会错位 (e.g. rowIdx=2 上一行的 "上午" rowspan=4 仍占 col 0; 行内首个 td 应落在 col 1)
        val cellText = HashMap<Pair<Int, Int>, String>()
        // colExpire[col] = baseRow + rs - 1; 走到该行时本列从 occupied 移除
        val colExpire = HashMap<Int, Int>()
        for ((rowIdx, row) in dataRows.withIndex()) {
            val occupiedNow = colExpire.filterValues { it >= rowIdx }.keys.toHashSet()
            var col = 0
            for (td in row.select("td")) {
                val cs = td.attr("colspan").toIntOrNull() ?: 1
                val rs = td.attr("rowspan").toIntOrNull() ?: 1
                while (col in occupiedNow) col += 1
                val isCenter = td.attr("align").lowercase() == "center"
                val raw = td.text().replace(' ', ' ').replace('{', '｛').replace('}', '｝').trim()
                for (i in 0 until cs) {
                    val c = col + i
                    if (!isCenter && raw.isNotEmpty()) {
                        // 课程格: 写入本行; rowspan>1 时向下方 (rs-1) 行延展同文本 (连堂)
                        cellText[c to rowIdx] = raw
                        for (k in 1 until rs) cellText[c to (rowIdx + k)] = raw
                    }
                    if (rs > 1) colExpire[c] = rowIdx + rs - 1
                }
                col += cs
            }
        }

        // 写入 (day, node) 网格 — col 是从"第一个数据列"起算的; dayOfFirstCol 是该列对应的星期.
        // 但我们的 cellText[col] 是从 col=0 起算的, col=0/1 可能是「时间」「节次」等非日列.
        // 推断"第一个日列的 col 索引": 取 th 行里第一个"星期X" th 的 col 索引. th 列总数 = ths.size.
        val firstDayCol = ths.indexOfFirst { Regex("星期[一二三四五六日天]").containsMatchIn(it.text()) }
        val grid = HashMap<Pair<Int, Int>, String>()
        for ((key, raw) in cellText) {
            val (col, rowIdx) = key
            if (raw.isEmpty()) continue
            val node = rowToNode[rowIdx]
            if (node !in 1..20) continue
            if (col < firstDayCol) continue // 时间/节次列, 跳过
            val dayColIdx = col - firstDayCol
            val day = ((dayOfFirstCol - 1 + dayColIdx) % 7) + 1
            grid[day to node] = raw
        }
        if (grid.isEmpty()) return emptyList()

        // 合并连续相同文本的相邻 (day, node) → rowspan
        return buildList {
            val emitted = HashSet<Pair<Int, Int>>()
            for (node in 1..20) {
                for (day in 1..7) {
                    if ((day to node) in emitted) continue
                    val raw = grid[day to node] ?: continue
                    if (raw.isEmpty()) continue
                    var endNode = node
                    while (endNode + 1 <= 20 && grid[day to (endNode + 1)] == raw) endNode += 1
                    for (k in node..endNode) emitted += day to k
                    for (course in extractXjuCourses(raw, day, node, endNode)) add(course)
                }
            }
        }
    }

    /** 把单格文本切成 JwCourse 列表。优先匹配形态 B「名｛周次[教师:…,地点:…],…｝」,
     *  回退形态 A「｛名(周次)[教师:…,地点:…]｝」(旧 fixture). */
    private fun extractXjuCourses(cellText: String, day: Int, startNode: Int, endNode: Int): List<JwCourse> {
        val out = mutableListOf<JwCourse>()
        if (cellText.isEmpty()) return out
        val blocks = cellText.split("；", ";").map { it.trim() }.filter { it.isNotEmpty() }
        for (block in blocks) {
            if (block.contains("｛")) {
                val braceRegex = Regex("｛([^｝]+)｝")
                val matches = braceRegex.findAll(block).toList()
                if (matches.isNotEmpty()) {
                    // 先检测形态 A: 块整体以｛开头, 内含 (周次) + [教师/地点] — 此时"花括号前"无内容
                    val firstInner = matches.first().groupValues[1]
                    val namePrefix = block.substring(0, matches.first().range.first).trim()
                    val isLegacy = namePrefix.isEmpty() && firstInner.contains('(') && firstInner.contains('[')
                    if (isLegacy) {
                        // 形态 A: 课程名在第一个 ( 之前, 周次在第一个 ( 里, 教师/地点在第一个 [ ]
                        val name = firstInner.substringBefore('(').trim()
                        val weeksSection = Regex("\\(([^)]*)\\)").find(firstInner)?.groupValues?.get(1).orEmpty()
                        val bracket = Regex("\\[([^\\]]*)\\]").find(firstInner)?.groupValues?.get(1).orEmpty()
                        val teacher = Regex("教师[:：]([^,，\\]]*)").find(bracket)?.groupValues?.get(1)?.trim().orEmpty()
                        val room = Regex("地点[:：]([^,，\\]]*)").find(bracket)?.groupValues?.get(1)?.trim().orEmpty()
                        for (weekEntry in weeksSection.split('、', '，', ',')) {
                            parseWeekTokens(weekEntry).forEach { (from, to, type) ->
                                out += JwCourse(name, room, teacher, day, startNode, endNode, from, to, type)
                            }
                        }
                        continue
                    }
                    // 形态 B: 花括号前是课程名, 内含周次 + [教师/地点]
                    val name = namePrefix
                    for (m in matches) {
                        val inner = m.groupValues[1]
                        parseXjuWeeksWithBrackets(inner).forEach { (from, to, teacher, room) ->
                            out += JwCourse(name, room, teacher, day, startNode, endNode, from, to, 0)
                        }
                    }
                    continue
                }
            }
            if (block.startsWith("｛") && block.endsWith("｝")) {
                val inner = block.trim('｛', '｝')
                val name = inner.substringBefore('(').trim()
                val weeksSection = Regex("\\(([^)]*)\\)").find(inner)?.groupValues?.get(1) ?: continue
                val bracket = Regex("\\[([^\\]]*)\\]").find(inner)?.groupValues?.get(1).orEmpty()
                val teacher = Regex("教师[:：]([^,，\\]]*)").find(bracket)?.groupValues?.get(1)?.trim().orEmpty()
                val room = Regex("地点[:：]([^,，\\]]*)").find(bracket)?.groupValues?.get(1)?.trim().orEmpty()
                for (weekEntry in weeksSection.split('、', '，', ',')) {
                    parseWeekTokens(weekEntry).forEach { (from, to, type) ->
                        out += JwCourse(name, room, teacher, day, startNode, endNode, from, to, type)
                    }
                }
            } else if (block.isNotBlank()) {
                val name = block.substringBefore('(').substringBefore('｛').trim()
                val nums = Regex("\\d+").findAll(block).map { it.value.toInt() }.toList()
                if (name.isNotBlank()) out += JwCourse(name, "", "", day, startNode, endNode, nums.firstOrNull() ?: 1, nums.getOrNull(1) ?: 20, 0)
            }
        }
        return out
    }

    /** 形态 B 块内解析: "12-19周[教师:孙冬璞,地点:待定]" */
    private fun parseXjuWeeksWithBrackets(inner: String): List<XjuWeekSlot> {
        val bracket = Regex("\\[([^\\]]*)\\]").find(inner)?.groupValues?.get(1).orEmpty()
        val teacher = Regex("教师[:：]([^,，\\]]*)").find(bracket)?.groupValues?.get(1)?.trim().orEmpty()
        val room = Regex("地点[:：]([^,，\\]]*)").find(bracket)?.groupValues?.get(1)?.trim().orEmpty()
        val beforeBracket = inner.substringBefore('[')
        val out = mutableListOf<XjuWeekSlot>()
        for (token in beforeBracket.split('、', '，', ',')) {
            parseWeekTokens(token).forEach { (from, to, _) ->
                out += XjuWeekSlot(from, to, teacher, room)
            }
        }
        return out
    }

    private data class XjuWeekSlot(val from: Int, val to: Int, val teacher: String, val room: String)


    /** Suda DataGrid1/MainWork_DataGrid1: th 行含 星期X 跳过; 数据行 align=center td=node 行标, 其余 td 索引+1=day; 单元格 "课程:"-名, "(x)"-教师(非辅讲), "主讲教师:"-教师, "第a-b周[单/双]"(以;/,分). */
    fun parseSuda(source: String): List<JwCourse> {
        val table = Jsoup.parse(source).selectFirst("#DataGrid1") ?: Jsoup.parse(source).selectFirst("#MainWork_DataGrid1") ?: return emptyList()
        return buildList {
            for (row in table.select("tr")) {
                val tds = row.select("td")
                if (tds.any { it.text().contains("星期") }) continue
                var node = 1
                for ((col, td) in tds.withIndex()) {
                    if (td.attr("align").lowercase(Locale.ROOT) == "center") {
                        node = Regex("\\d+").find(td.text())?.value?.toIntOrNull() ?: node
                        continue
                    }
                    val day = col + 1
                    if (td.text().isBlank()) continue
                    val lines = td.html().split("<br>").map { Jsoup.parse(it).text().trim() }.filter { it.isNotEmpty() }
                    var name = ""
                    var teacher = ""
                    var room = ""
                    val weeks = mutableListOf<Triple<Int, Int, Int>>()
                    for (line in lines) {
                        when {
                            line.startsWith("课程:") || line.startsWith("课程：") -> name = line.dropWhile { it != ':' && it != '：' }.drop(1).trim()
                            line.startsWith("(") && !line.contains("辅讲教师") -> teacher = line.trim('(', ')').trim()
                            line.startsWith("主讲教师:") || line.startsWith("主讲教师：") -> teacher = line.dropWhile { it != ':' && it != '：' }.drop(1).trim()
                            Regex("第\\s*\\d+.*周").containsMatchIn(line) -> Regex("第\\s*(\\d+)\\s*(-\\s*(\\d+))?\\s*周\\s*(单|双)?").findAll(line).forEach { m ->
                                val from = m.groupValues[1].toInt()
                                val to = m.groupValues[3].toIntOrNull() ?: from
                                val type = when (m.groupValues[4]) { "单" -> 1; "双" -> 2; else -> 0 }
                                weeks += Triple(from, to, type)
                            }
                            else -> if (name.isBlank()) name = line else room = line
                        }
                    }
                    if (name.isBlank()) name = lines.first()
                    if (weeks.isEmpty()) weeks += Triple(1, 20, 0)
                    // WakeUp dex L00e9 rowspan: 纵向合并单元格占多节 — endNode = node + rowspan - 1
                    val rowspan = td.attr("rowspan").toIntOrNull() ?: 1
                    val endNode = node + (rowspan - 1).coerceAtLeast(0)
                    weeks.forEach { (from, to, type) -> add(JwCourse(name, room, teacher, day, node, endNode, from, to, type)) }
                }
            }
        }
    }

    fun confidence(source: String, markers: Set<String>): Int = when {
        markers.any { source.contains(it, ignoreCase = true) } -> 90
        source.trimStart().startsWith("{") || source.trimStart().startsWith("[") -> 70
        else -> 0
    }

    fun features(source: String, markers: Set<String>): List<String> = markers.filter { source.contains(it, true) }.map { "marker=$it" }

    fun parseShuwei(source: String): List<JwCourse> {
        val root = parseJson(source) ?: return emptyList()
        val result = mutableListOf<JwCourse>()
        fun visit(value: Any) {
            when (value) {
                is JSONArray -> for (i in 0 until value.length()) value.opt(i)?.let(::visit)
                is JSONObject -> {
                    listOf("activities", "courseUnits", "unitList").forEach { key ->
                        value.optJSONArray(key)?.let { array ->
                            for (i in 0 until array.length()) {
                                val row = array.optJSONObject(i) ?: continue
                                parseActivity(row)?.let(result::addAll)
                            }
                        }
                    }
                    for (key in value.keys()) value.opt(key)?.let(::visit)
                }
            }
        }
        visit(root)
        return result.distinctBy { listOf(it.name, it.day, it.startNode, it.startWeek, it.endWeek, it.type) }
    }

    private fun parseActivity(row: JSONObject): List<JwCourse>? {
        val name = first(row, "courseName", "course", "name", "kcmc")
        val day = firstInt(row, "weekday", "weekDay", "dayOfWeek", "classDay", "xqj", "day")
        val start = firstInt(row, "startSection", "beginSection", "beginNumber", "startNode", "ksjc", "start")
        if (name.isBlank() || day == null || day !in 1..7 || start == null || start < 1) return null

        val end = firstInt(row, "endSection", "endNumber", "endNode", "jsjc", "end") ?: start
        val weeks = parseWeekTokens(first(row, "weeksStr", "weekDescription", "week", "weeks", "zc"))
        if (weeks.isEmpty()) return null
        val room = first(row, "room", "classroom", "classroomName", "location", "JASMC", "cdmc")
        val teacher = first(row, "teacher", "teacherName", "teachers", "attendClassTeacher", "SKJS", "xm")
        return weeks.map { (from, to, type) -> JwCourse(name, room, teacher, day, start, end, from, to, type) }
    }

    private fun parseWeekTokens(value: String): List<Triple<Int, Int, Int>> {
        if (value.isBlank()) return emptyList()
        return value.replace("周", "").split(',', '，', ';', '；').mapNotNull { raw ->
            val token = raw.trim()
            if (token.isBlank()) return@mapNotNull null
            val type = when {
                token.contains("单") -> 1
                token.contains("双") -> 2
                else -> 0
            }
            val numbers = Regex("\\d+").findAll(token).map { it.value.toInt() }.toList()
            if (numbers.isEmpty()) null else Triple(numbers.first(), numbers.getOrNull(1) ?: numbers.first(), type)
        }
    }

    private fun parseJson(source: String): Any? = try {
        when (val text = source.trim()) {
            else -> when {
                text.startsWith("{") -> JSONObject(text)
                text.startsWith("[") -> JSONArray(text)
                else -> null
            }
        }
    } catch (_: Exception) { null }

    private fun parseJsonCourses(root: Any): List<JwCourse> {
        val result = mutableListOf<JwCourse>()
        fun walk(value: Any, depth: Int) {
            if (depth > 5) return
            when (value) {
                is JSONArray -> for (i in 0 until value.length()) value.opt(i)?.let { item ->
                    if (item is JSONObject) {
                        val name = first(item, "courseName", "kcmc", "KCM", "name", "course")
                        val day = firstInt(item, "weekday", "dayOfWeek", "xqj", "day", "classDay", "xq")
                        val node = firstInt(item, "beginNumber", "beginSection", "startNode", "ksjc", "start")
                        if (name.isNotBlank() && day != null && node != null) result += JwCourse(name, first(item, "location", "room", "classroomName", "JASMC", "cdmc"), first(item, "teacherName", "teacher", "teachers", "SKJS", "xm"), day.coerceIn(1, 7), node, firstInt(item, "endNumber", "endSection", "endNode", "jsjc", "end") ?: node, 1, 16)
                    }
                    walk(item, depth + 1)
                }
                is JSONObject -> for (key in value.keys()) value.opt(key)?.let { walk(it, depth + 1) }
            }
        }
        walk(root, 0)
        return result
    }

    private fun parseMarkedTable(source: String, markers: Set<String>): List<JwCourse> = buildList {
        for (row in Jsoup.parse(source).select("tr")) {
            val cells = row.select("td,th")
            if (cells.size < 3) continue
            val text = cells.joinToString(" | ") { it.text().trim() }
            if (markers.isNotEmpty() && markers.none { source.contains(it, true) } && row.attr("data-day").isBlank()) continue
            val name = row.attr("data-course").ifBlank { cells.firstOrNull()?.text()?.trim().orEmpty() }
            val day = row.attr("data-day").toIntOrNull() ?: extractDay(text) ?: continue
            val node = row.attr("data-node").toIntOrNull() ?: extractNode(text) ?: continue
            if (name.isNotBlank()) add(JwCourse(name, row.attr("data-room").ifBlank { cells.getOrNull(3)?.text().orEmpty() }, row.attr("data-teacher").ifBlank { cells.getOrNull(2)?.text().orEmpty() }, day, node, node, 1, 16))
        }
    }

    private fun parseDelimited(source: String): List<JwCourse> = source.lineSequence().mapNotNull { line ->
        val p = line.split(',', '\t').map { it.trim() }
        val day = p.getOrNull(1)?.toIntOrNull()
        val node = p.getOrNull(2)?.toIntOrNull()
        if (p.size >= 5 && day != null && day in 1..7 && node != null) JwCourse(p[0], p.getOrNull(3).orEmpty(), p.getOrNull(4).orEmpty(), day, node, node, 1, 16) else null
    }.toList()

    private fun first(o: JSONObject, vararg keys: String): String = keys.firstNotNullOfOrNull { o.optString(it).takeIf(String::isNotBlank) }.orEmpty()
    private fun firstInt(o: JSONObject, vararg keys: String): Int? = keys.firstNotNullOfOrNull { o.optString(it).filter(Char::isDigit).toIntOrNull() }
    private fun stripMarkup(value: String): String = Jsoup.parse(value).text().trim()
    private fun extractDay(text: String): Int? = Regex("(?:星期|周)\\s*([一二三四五六日天1-7])").find(text)?.groupValues?.get(1)?.let { when (it) { "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6; "日", "天" -> 7; else -> it.toIntOrNull() } }
    private fun extractNode(text: String): Int? = Regex("(?:第\\s*)?(\\d{1,2})\\s*(?:[-~至](\\d{1,2}))?\\s*节").find(text)?.groupValues?.get(1)?.toIntOrNull()
    private fun timeToNode(time: Int): Int = when { time < 1230 -> ((time - 800) / 100) + 1; time < 1800 -> ((time - 1400) / 100) + 5; else -> ((time - 1900) / 100) + 9 }
    private fun durationToNodes(start: Int, end: Int): Int = when (end - start) { in 50..99 -> 1; in 100..200 -> 2; in 210..340 -> 3; else -> 4 }
}

abstract class WakeUpMarkerParser(source: String, private val markers: Set<String>) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> = WakeUpCompat.parse(source, markers)
    override fun confidence(): Int = WakeUpCompat.confidence(source, markers)
    override fun matchedFeatures(): List<String> = WakeUpCompat.features(source, markers)
}
class JwKingoParser(source: String) : WakeUpMarkerParser(source, setOf("kingosoft", "courseTableForStd", "courseTableStudent", "new TaskActivity")) {
    override fun generateCourseList() = WakeUpCompat.parseKingoTaskActivity(source).ifEmpty { super.generateCourseList() }
}
class JwJzParser(source: String) : WakeUpMarkerParser(source, setOf("courseTableForStd", "courseTableStudent", "wisedu", "JinZhi"))
class JwSouthSoftParser(source: String) : WakeUpMarkerParser(source, setOf("studentTableVms", "studentTableVm", "activities", "south_soft")) {
    override fun generateCourseList() = WakeUpCompat.parseSouthSoft(source).ifEmpty { super.generateCourseList() }
}
class JwChaoxingLegacyParser(source: String) : WakeUpMarkerParser(source, setOf("kckbData", "LessonArray", "queryKbForGrdb", "Powered by ChaoXing")) {
    override fun generateCourseList() = WakeUpCompat.parseChaoxing(source).ifEmpty { super.generateCourseList() }
}
class JwShuweiParser(source: String) : WakeUpMarkerParser(source, setOf("courseUnits", "unitCount", "activities", "wut_table", "shuwei")) {
    override fun generateCourseList() = WakeUpCompat.parseShuwei(source).ifEmpty { super.generateCourseList() }
}
class JwSudaParser(source: String) : WakeUpMarkerParser(source, setOf("print-schedule-table", "default2.aspx", "xskbcx.aspx", "suda")) {
    override fun generateCourseList() = WakeUpCompat.parseSuda(source).ifEmpty { super.generateCourseList() }
}
class JwCumtbParser(source: String) : WakeUpMarkerParser(source, setOf("eams5-student", "schedule-table", "course-table", "cumtb")) {
    override fun generateCourseList() = WakeUpCompat.parseCumtb(source).ifEmpty { super.generateCourseList() }
}
class JwXjuParser(source: String) : WakeUpMarkerParser(source, setOf("xjtu", "xju", "courseTable", "课程表")) {
    override fun generateCourseList() = WakeUpCompat.parseXju(source).ifEmpty { super.generateCourseList() }
}
