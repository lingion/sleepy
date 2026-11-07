package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.Locale
import java.util.concurrent.CancellationException

/** Compatibility parsers for WakeUp protocol variants. */
internal object WakeUpCompat {
    fun parse(source: String, markers: Set<String>, jsonHint: Boolean = false): List<JwCourse> {
        parseJson(source)?.let { root ->
            parseJsonCourses(root).takeIf { it.isNotEmpty() }?.let { return it }
            parseJzJson(root).takeIf { it.isNotEmpty() }?.let { return it }
        }
        parseJzSuper(source).takeIf { it.isNotEmpty() }?.let { return it }
        parseJzNativeChain(source).takeIf { it.isNotEmpty() }?.let { return it }
        parseMarkedTable(source, markers).takeIf { it.isNotEmpty() }?.let { return it }
        return emptyList()
    }

    /** Native variants isolate failures and stop at the first nonempty schedule. */
    internal fun parseJzNativeChain(source: String): List<JwCourse> {
        val errors = mutableListOf<Pair<String, Exception>>()
        val parsers = listOf(
            "JinZhiCourseFormTable" to ::parseJzHtml,
            "JinZhiMtt" to ::parseJzMtt,
            "WIST" to ::parseJzWist,
            "JinZhiX" to ::parseJzX,
            "JinZhiPost" to ::parseJzPost,
            "JinZhiXTable" to ::parseJzXTable,
            "XMU" to ::parseJzXmu,
        )
        for ((name, parser) in parsers) {
            try {
                parser(source).takeIf { it.isNotEmpty() }?.let { return it }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errors += name to error
            }
        }
        if (errors.isNotEmpty()) throw IllegalStateException(errors.joinToString("\n") { (name, error) ->
            "$name: ${error.message.orEmpty()}"
        }).apply { errors.forEach { (_, error) -> addSuppressed(error) } }
        return emptyList()
    }

    /** JZHandCourseInfoItem JSON: xqj=day, djj=起始节, qmz="1-8;10-16", dsz==1→单 / dsz!=2→双 / else 每周 (WakeUp 反转语义). */
    private fun parseJzJson(root: Any): List<JwCourse> {
        if (root !is JSONArray) return emptyList()
        return JwXbellsoftParser(root.toString()).generateCourseList()
    }

    /** 新强智 Njw2017 SPA (2026-10) HTML parser.
     *  入口: `.el-table__body-wrapper` 容器; tbody 6 行 (1-2/3-5/6-7/8-9/10-12/13-15 节);
     *  每行 8 td (col=0 节次标签, col=1..7 周一..周日);
     *  每格用 `div > div > div` 选出若干 `<div style="position: relative;">` 课程包装器;
     *  每个包装器含 8 个子 div, 字段位置 (实测样例 sample2021-2.html):
     *    [0] all-timetable-check-bz 空, [1] 空, [2] display:none 摘要,
     *    [3] 课程名 (前导 `*` 表考试/特殊课), [4] 教师,
     *    [5] 班级, [6] "节次(周次)" 串, [7] 教室.
     *  row → 默认节次 (与 QZParser.java#getSectionStart 共识, 无节字时降级):
     *    0→1, 1→3, 2→6, 3→8, 4→10, 5→13. 持续节数 end-start+1.
     *  跨仓验证 19 仓 (greyovo QZParser 主参考 + 旁证), 见 scope.md. */
    fun parseJzSuper(source: String, errorList: MutableList<String>? = null): List<JwCourse> {
        val errors = errorList ?: mutableListOf()
        val doc = Jsoup.parse(source)
        val wrapper = doc.selectFirst(".el-table__body-wrapper")
            ?: doc.selectFirst("div.el-table__body-wrapper")
            ?: run {
                errors += "INV-MARKER-MISS 失败: 未找到 el-table__body-wrapper 容器 (新强智 Njw2017 SPA 主入口)"
                return emptyList()
            }
        val tbody = wrapper.selectFirst("tbody")
            ?: run {
                errors += "INV-TBODY-MISS 失败: el-table__body-wrapper 缺 tbody"
                return emptyList()
            }
        val rows = tbody.select("tr")
        if (rows.isEmpty()) {
            errors += "INV-ROW-COUNT 失败: tbody 无 tr 行"
            return emptyList()
        }
        if (rows.size < 6) {
            errors += "INV-ROW-COUNT-WARN: tbody tr 数 ${rows.size} (<6 行, 仅用于单元测试)"
        }
        // 行 → 默认起始节 (与 QZParser.java#getSectionStart 共识, 仅当 div[6] 不含"节"字时降级)
        val defaultStartByRow = intArrayOf(1, 3, 6, 8, 10, 13)
        val defaultEndByRow = intArrayOf(2, 5, 7, 9, 12, 15)
        return buildList {
            for ((rowIdx, row) in rows.withIndex()) {
                val tds = row.children().filter { it.tagName() == "td" }
                if (tds.size < 8) continue // 0=节次标签, 1..7=周一..周日
                val rowLabel = tds[0].text().trim()
                val rowSections = Regex("""^(\d{1,2})(?:\s*[-~至]\s*(\d{1,2}))?节$""")
                    .matchEntire(rowLabel)?.let { match ->
                        val first = match.groupValues[1].toIntOrNull()
                        val last = match.groupValues[2].takeIf(String::isNotBlank)?.toIntOrNull() ?: first
                        if (first != null && last != null && first in 1..30 && last in first..30) first to last else null
                    } ?: if (rowLabel.isBlank() && rows.size == 6 && rowIdx < defaultStartByRow.size)
                        defaultStartByRow[rowIdx] to defaultEndByRow[rowIdx] else null
                for (col in 1..7) {
                    val td = tds[col]
                    // INV-POSITION-WRAPPER: 用 `div > div > div` 选出 position: relative 课程包装器
                    val courseWrappers = td.select("div[style]").filter {
                        Regex("""(?:^|;)\s*position\s*:\s*relative\s*(?:;|$)""", RegexOption.IGNORE_CASE)
                            .containsMatchIn(it.attr("style"))
                    }
                    if (courseWrappers.isEmpty()) continue
                    for (wrapperEl in courseWrappers) {
                        val info = wrapperEl.children().filter { it.tagName() == "div" }
                        // 8 字段包装器 (实测), 不足 8 视为非课程包装器 (e.g. 空 div)
                        if (info.size < 8) continue
                        val nameRaw = info.get(3).text().trim()
                        if (nameRaw.isBlank()) continue
                        // INV-NAME-STRIP-ASTERISK: QZParser.java#getClassName char[0]=='*' → skip 前 2 字符
                        val name = nameRaw.removePrefix("*").trim()
                        if (name.isBlank()) continue
                        val teacher = info.get(4).text().trim()
                        val weeksText = info.get(6).text().trim()
                        val room = info.get(7).text().trim()
                        // INV-SECTIONS-FALLBACK: 无"节"字时按行号用 defaultStartByRow/defaultEndByRow
                        val startNode: Int
                        val endNode: Int
                        if (weeksText.contains("节")) {
                            val section = Regex("""^(\d{1,2})(?:\s*[-~至]\s*(\d{1,2}))?节$""")
                                .matchEntire(weeksText.substringBefore('(').substringBefore('（').trim()) ?: continue
                            startNode = section.groupValues[1].toIntOrNull() ?: continue
                            endNode = section.groupValues[2].takeIf(String::isNotBlank)?.toIntOrNull() ?: startNode
                            if (startNode !in 1..30 || endNode !in startNode..30) continue
                        } else {
                            val fallback = rowSections ?: continue
                            startNode = fallback.first
                            endNode = fallback.second
                        }
                        // INV-WEEKS-EXTRACT-PAREN: QZParser.java#getWeeksList 取括号内周次 (含"周"字)
                        //    格式 "周" + "1-16" 或 "1-16,18-20" 或 "1-8单" 等
                        val weeksRaw = Regex("[\\(（]([^\\)）]+)[\\)）]").find(weeksText)?.groupValues?.get(1).orEmpty()
                        val weekPattern = Regex("""^(\d{1,2})(?:\s*[-~至]\s*(\d{1,2}))?\s*周?\s*(单|双)?\s*周?$""")
                        val weeks = weeksRaw.split(',', '，', ';', '；').mapNotNull { token ->
                            val match = weekPattern.matchEntire(token.trim()) ?: return@mapNotNull null
                            val from = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                            val to = match.groupValues[2].takeIf(String::isNotBlank)?.toIntOrNull() ?: from
                            if (from !in 1..60 || to !in from..60) return@mapNotNull null
                            Triple(from, to, when (match.groupValues[3]) { "单" -> 1; "双" -> 2; else -> 0 })
                        }
                        if (weeks.isEmpty()) continue
                        weeks.forEach { (from, to, type) ->
                            add(JwCourse(name, room, teacher, col, startNode, endNode, from, to, type))
                        }
                    }
                }
            }
        }
    }

    /** WakeUp 6.6.2 CourseFormTable: gutters do not consume days; rowspan reserves them. */
    internal fun parseJzHtml(source: String): List<JwCourse> {
        val doc = Jsoup.parse(source)
        val table = doc.getElementById("1") ?: doc.selectFirst(".CourseFormTable, #CourseFormTable")
            ?: return emptyList()
        val occupiedUntil = IntArray(7)
        return buildList {
            for ((rowIndex, row) in table.select("tr").withIndex()) {
                var dayIndex = 0
                for (td in row.children().filter { it.tagName() == "td" }) {
                    if (td.attr("style").contains("center", true) || td.hasClass("firstHiddenTd")) continue
                    if (td.hasClass("NotFixCourseTableTd") || td.hasClass("commentAlign")) return@buildList
                    while (dayIndex < 7 && occupiedUntil[dayIndex] > rowIndex) dayIndex++
                    if (dayIndex >= 7) break
                    val day = dayIndex + 1
                    occupiedUntil[dayIndex] = rowIndex + (td.attr("rowspan").toIntOrNull() ?: 1).coerceAtLeast(1)
                    dayIndex++
                    if (td.text().isBlank()) continue
                    val html = td.html().replace(Regex("(?i)&nbsp;\\s*<br\\s*/?>|<br\\s*/?>\\s*&nbsp;"), "<br>")
                    for (chunk in html.split(Regex("(?i)<hr\\b[^>]*>"))) {
                        // Keep interior empty fields: a blank teacher must not shift the room.
                        val fields = chunk.split(Regex("(?i)<br\\s*/?>|&nbsp;|\u00a0"))
                            .map { Jsoup.parse(it).text().trim() }
                            .dropWhile(String::isBlank).dropLastWhile(String::isBlank)
                        if (fields.size < 5) continue
                        val name = fields[0].substringBefore('[').trim()
                        if (name.isBlank()) continue
                        val weekIndex = if (fields[1].firstOrNull()?.isDigit() == true) 1 else 2
                        val nodeIndex = (2..3).firstOrNull { fields[it].contains('节') } ?: continue
                        val nodes = Regex("第\\s*(\\d+)(?:\\s*[-~至]\\s*(\\d+))?\\s*节").find(fields[nodeIndex]) ?: continue
                        val fromNode = nodes.groupValues[1].toIntOrNull() ?: continue
                        val toNode = nodes.groupValues[2].toIntOrNull() ?: fromNode
                        if (fromNode < 1 || toNode < fromNode) continue
                        val teacherIndex = (1..3).firstOrNull { it != weekIndex && it != nodeIndex } ?: continue
                        val note = fields[nodeIndex].substringAfter(')', "").removeSurrounding("(", ")").trim()
                        parseWeekTokens(fields[weekIndex]).forEach { (from, to, type) ->
                            if (from > 0 && to >= from) add(JwCourse(name, fields[4], fields[teacherIndex], day,
                                fromNode, toNode, from, to, type, note = note))
                        }
                    }
                }
            }
        }
    }

    /** WakeUp 6.6.2 jz.OooOO0: data-week is the weekday, not the teaching week. */
    internal fun parseJzX(source: String): List<JwCourse> {
        val table = Jsoup.parse(source).selectFirst(".wut_tablex") ?: return emptyList()
        return buildList {
            for (cell in table.select("td[data-role][data-week]")) {
                val day = cell.attr("data-week").toIntOrNull() ?: continue
                if (day !in 1..7) continue
                for (block in cell.children()) {
                    val children = block.children()
                    if (children.size < 3) continue
                    val name = children[1].text().substringAfter('《').substringBefore('》').trim()
                    val fields = children[2].html().split(Regex("&nbsp;|\u00a0"))
                    val shift = if (fields.getOrNull(3)?.contains('节') == true) 0 else 1
                    if (fields.size < 5 - shift || name.isBlank()) continue
                    val nodes = parseJzNodes(stripMarkup(fields[3 - shift])) ?: continue
                    val teacher = stripMarkup(fields[1 - shift])
                    val room = stripMarkup(fields.drop(4 - shift).joinToString(""))
                    for ((from, to, type) in parseWeekTokens(stripMarkup(fields[2 - shift]))) {
                        if (from > 0 && to >= from) add(JwCourse(name, room, teacher, day,
                            nodes.first, nodes.second, from, to, type))
                    }
                }
            }
        }
    }

    /** WakeUp 6.6.2 jz.OooOO0O: repeated name, teacher, explicit arrangement lines. */
    internal fun parseJzXTable(source: String): List<JwCourse> {
        val table = Jsoup.parse(source).selectFirst(".x-table") ?: return emptyList()
        return buildList {
            for (block in table.select("div")) {
                if (block.children().any { it.tagName() == "div" }) continue
                val fields = block.html().split(Regex("(?i)<br\\s*/?>"))
                    .map(::stripMarkup)
                for (i in 0 until (fields.size - 2).coerceAtLeast(0)) {
                    if (i != 0 && !fields[i].startsWith(',')) continue
                    val name = fields[i].substringAfter(',').substringBefore('[').trim()
                    val arrangement = fields[i + 2].split('，').map(String::trim)
                    if (name.isBlank() || arrangement.size < 4) continue
                    val day = extractDay(arrangement[1]) ?: continue
                    val nodes = parseJzNodes(arrangement[2]) ?: continue
                    for ((from, to, type) in parseWeekTokens(arrangement[0])) {
                        if (from > 0 && to >= from) add(JwCourse(name, arrangement.last(), fields[i + 1],
                            day, nodes.first, nodes.second, from, to, type))
                    }
                }
            }
        }.distinct()
    }

    private fun parseJzNodes(text: String): Pair<Int, Int>? {
        val match = Regex("第?\\s*([0-9一二三四五六七八九十]+)\\s*节?(?:\\s*[-~至]\\s*第?\\s*([0-9一二三四五六七八九十]+)\\s*节?)?")
            .matchEntire(text.trim()) ?: return null
        fun number(value: String): Int? {
            value.toIntOrNull()?.let { return it }
            val digit = mapOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5,
                "六" to 6, "七" to 7, "八" to 8, "九" to 9)
            if (value == "十") return 10
            if (value == "二十") return 20
            if (value.startsWith("十")) return digit[value.drop(1)]?.plus(10)
            return digit[value]
        }
        val first = number(match.groupValues[1]) ?: return null
        val last = if (match.groupValues[2].isEmpty()) first else number(match.groupValues[2]) ?: return null
        return (first to last).takeIf { first > 0 && last >= first }
    }

    /** WakeUp 6.6.2 JZ standard, HQU and XJTU layouts use the same item classes. */
    internal fun parseJzMtt(source: String): List<JwCourse> {
        val table = Jsoup.parse(source.replace("divclass=", "div class=")).selectFirst(".wut_table")
            ?: return emptyList()
        return buildList {
            for (cell in table.select("td")) {
                for (item in cell.select(".mtt_arrange_item")) {
                    val nameElement = item.selectFirst(".mtt_item_kcmc") ?: continue
                    val heading = nameElement.html().split(Regex("""&nbsp;|\u00a0""")).map(::stripMarkup)
                    val headingWeeks = heading.firstOrNull { Regex("""\d周""").containsMatchIn(it) }
                    val roomText = item.selectFirst(".mtt_item_room")?.text()?.trim() ?: continue
                    val spaceLayout = !roomText.contains(',') && headingWeeks == null
                    val arrangement = if (spaceLayout) roomText.split(Regex("""\s+""")) else roomText.split(',')
                    val fields = arrangement.map(String::trim)
                    val dayIndex = fields.indexOfFirst { it.startsWith("星期") }
                    val day = cell.attr("data-week").toIntOrNull()
                        ?: fields.getOrNull(dayIndex)?.let(::extractDay) ?: continue
                    val nodeIndex = fields.indexOfFirst { it.contains('节') && parseJzNodes(it) != null }
                    val nodes = if (nodeIndex >= 0) parseJzNodes(fields[nodeIndex]) else {
                        val from = cell.attr("data-begin-unit").toIntOrNull()
                        val to = cell.attr("data-end-unit").toIntOrNull()
                        if (from != null && to != null && from > 0 && to >= from) from to to else null
                    } ?: continue
                    if (day !in 1..7) continue
                    val ownName = nameElement.ownText().trim()
                    var name = when {
                        headingWeeks != null -> fields.first().substringAfter(' ').trim()
                        Regex("""\[\d+]""").matches(ownName) -> nameElement.children().firstOrNull()?.text().orEmpty()
                        else -> ownName.replace(Regex("""^\[\d+]\s*"""), "")
                    }
                    val creditMatch = if (spaceLayout) Regex("""\(([0-9.]+)学分\)""").find(name) else null
                    val credit = creditMatch?.groupValues?.get(1)?.toFloatOrNull()?.takeIf { it.isFinite() } ?: 0f
                    if (creditMatch != null) name = name.removeRange(creditMatch.range).trim()
                    if (name.isBlank()) continue
                    val teacher = if (headingWeeks != null) heading.last().substringBefore('[').trim()
                        else item.select(".mtt_item_jxbmc").text().trim()
                    val weekEnd = if (dayIndex >= 0) dayIndex else nodeIndex
                    val weeks = when {
                        headingWeeks != null -> headingWeeks
                        spaceLayout -> fields.drop(1).dropLast(1).joinToString(",")
                        weekEnd > 0 -> fields.take(weekEnd).joinToString(",")
                        dayIndex < 0 && nodeIndex < 0 -> fields.dropLast(1).joinToString(",")
                        else -> continue
                    }
                    val room = when {
                        spaceLayout -> fields.first()
                        dayIndex >= 0 && fields.size - dayIndex > 3 -> fields[fields.lastIndex - 1]
                        else -> fields.last()
                    }
                    for ((from, to, type) in parseMttWeeks(weeks)) {
                        if (from > 0 && to >= from) add(JwCourse(name, room, teacher, day,
                            nodes.first, nodes.second, from, to, type, credit=credit))
                    }
                }
            }
        }
    }

    private fun parseMttWeeks(value: String): List<Triple<Int, Int, Int>> {
        val result = mutableListOf<Triple<Int, Int, Int>>()
        for (raw in value.split(',', '，')) {
            val token = raw.trim().replace('（', '(').replace('）', ')')
            val match = Regex("""第?([0-9]+)(?:-([0-9]+))?([单双])?周?(?:\(([单双])周?\))?""")
                .matchEntire(token) ?: return emptyList()
            val first = match.groupValues[1].toIntOrNull() ?: return emptyList()
            val last = match.groupValues[2].ifEmpty { match.groupValues[1] }.toIntOrNull() ?: return emptyList()
            if (first < 1 || last < first) return emptyList()
            val markers = listOf(match.groupValues[3], match.groupValues[4]).filter(String::isNotEmpty).distinct()
            if (markers.size > 1) return emptyList()
            val parity = when (markers.firstOrNull()) { "单" -> 1; "双" -> 2; else -> 0 }
            result += Triple(first, last, parity)
        }
        return result
    }

    /** jz.OooO0o has two branches reversed in JADX output; field order follows its DEX. */
    internal fun parseJzPost(source: String): List<JwCourse> {
        val table = Jsoup.parse(source).selectFirst("[id^=jsTbl_], table.MsoNormalTable.mtt_table")
            ?: return emptyList()
        return buildList {
            for (cell in table.select("td[xq][jc]")) {
                if (Regex("""display\s*:\s*none""", RegexOption.IGNORE_CASE).containsMatchIn(cell.attr("style"))) continue
                val day = cell.attr("xq").trim().toIntOrNull() ?: continue
                val node = cell.attr("jc").trim().toIntOrNull() ?: continue
                val span = cell.attr("rowspan").trim().toIntOrNull() ?: 1
                if (day !in 1..7 || node < 1 || span < 1) continue
                for (item in cell.select(".arrage")) {
                    val rawFields = item.children().map { it.text().trim() }
                    val fields = if (rawFields.firstOrNull() in listOf("", "本", "研", "本研")) rawFields.drop(1) else rawFields
                    if (fields.size < 3) continue
                    val name = fields[1].substringBefore('(').trim()
                    if (name.isBlank()) continue
                    val mode = fields.getOrNull(3).orEmpty()
                    val teacher = if (mode == "面授讲课")
                        fields[0].substringAfterLast('(', "").substringBefore(')') else fields[2]
                    val room = if (mode == "面授讲课") fields[2] else mode
                    val weeksText = fields[0].split(',', '，', '、', '\\').joinToString(",") { token ->
                        val parity = when { token.contains("单") -> "单"; token.contains("双") -> "双"; else -> "" }
                        token.substringBefore('(').replace("研", "") + parity
                    }
                    for ((from, to, type) in parseWeekTokens(weeksText)) {
                        if (from > 0 && to >= from) add(JwCourse(name, room, teacher, day,
                            node, node + span - 1, from, to, type))
                    }
                }
            }
        }
    }

    /** WIST uses explicit item coordinates and has two named noon periods. */
    internal fun parseJzWist(source: String): List<JwCourse> = mergeAdjacentJzNodes(buildList {
        val numericNodes = Regex("""^(中?[1-9]\d?)(-(中?[1-9]\d?))?$""")
        fun node(value: String): Int? = when (value) {
            "中1" -> 5
            "中2" -> 6
            else -> value.toIntOrNull()
        }
        for (cell in Jsoup.parse(source).select("td[data-role=item]")) {
            val day = cell.attr("data-week").toIntOrNull() ?: continue
            if (day !in 1..7) continue
            for (item in cell.select("div.mtt_arrange_item")) {
                val name = item.selectFirst(".mtt_item_kcmc")?.ownText()?.trim().orEmpty()
                if (name.isBlank()) continue
                val teacher = item.selectFirst(".mtt_item_jxbmc")?.text()?.trim().orEmpty()
                val fields = item.selectFirst(".mtt_item_room")?.text()?.split(',', '，')
                    ?.map(String::trim) ?: continue
                val explicitStart = cell.attr("data-begin-unit").toIntOrNull()
                val explicitEnd = cell.attr("data-end-unit").toIntOrNull()
                val nodes = if (explicitStart != null && explicitEnd != null) explicitStart to explicitEnd else {
                    val token = fields.firstOrNull { numericNodes.matches(it) } ?: continue
                    val bounds = token.split('-')
                    (node(bounds.first()) ?: continue) to (node(bounds.last()) ?: continue)
                }
                if (nodes.first < 1 || nodes.second < nodes.first) continue
                val rooms = fields.filter { !it.contains('周') && !numericNodes.matches(it) }
                val room = rooms.firstOrNull { Regex("实验室|教室|机房").containsMatchIn(it) }
                    ?: rooms.firstOrNull().orEmpty()
                val weeks = schoolWeekRanges(fields.filter { it.contains('周') }.joinToString(","))
                    .flatMap { (from, to, type) ->
                        if (from < 1 || to < from) emptyList() else
                            (from..to).filter { type == 0 || it % 2 == type % 2 }
                    }.distinct().sorted()
                for (week in weeks) add(JwCourse(name, room, teacher, day, nodes.first, nodes.second, week, week))
            }
        }
    }.distinct())

    private fun mergeAdjacentJzNodes(courses: List<JwCourse>): List<JwCourse> =
        courses.groupBy { it.copy(startNode = 0, endNode = 0) }.values.flatMap { group ->
            buildList {
                var current: JwCourse? = null
                for (course in group.sortedBy { it.startNode }) {
                    val previous = current
                    if (previous != null && course.startNode.toLong() <= previous.endNode.toLong() + 1) {
                        current = previous.copy(endNode = maxOf(previous.endNode, course.endNode))
                    } else {
                        if (previous != null) add(previous)
                        current = course
                    }
                }
                current?.let(::add)
            }
        }

    /** XMU embeds an ordinary JSON array; SKZC has no leading week-zero sentinel. */
    internal fun parseJzXmu(source: String): List<JwCourse> {
        val start = Regex("""var\s+scheduleList\s*=""").find(source)?.range?.last?.plus(1)
            ?: return emptyList()
        val remainder = source.substring(start)
        val end = Regex(""";\s*var\s+notArrangedList""").find(remainder)?.range?.first
            ?: return emptyList()
        val rows = parseJson(remainder.substring(0, end)) as? JSONArray ?: return emptyList()
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val name = row.optString("KCM")
                val day = firstInt(row, "SKXQ") ?: continue
                val fromNode = firstInt(row, "KSJC") ?: continue
                val toNode = firstInt(row, "JSJC") ?: continue
                if (name.isBlank() || day !in 1..7 || fromNode < 1 || toNode < fromNode) continue
                val room = row.optString("SKDD").ifBlank { row.optString("YPSJDD") }
                val bitmap = row.optString("SKZC")
                if (bitmap.any { it != '0' && it != '1' }) continue
                for ((index, bit) in bitmap.withIndex()) if (bit == '1') {
                    add(JwCourse(name, room, row.optString("SKJS"), day, fromNode, toNode, index + 1, index + 1))
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
        val root = parseJson(source)
        val rows = when (root) {
            is JSONArray -> root
            is JSONObject -> root.optJSONObject("data")?.optJSONArray("kckbData") ?: root.optJSONArray("kckbData")
            else -> null
        } ?: return emptyList()
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val name = stripMarkup(row.optString("kcmc"))
                val day = row.optString("xq").toIntOrNull() ?: continue
                val node = row.optInt("djc", 0)
                if (name.isBlank() || day !in 1..7 || node < 1) continue
                val type = row.optString("zctype").toIntOrNull() ?: 0
                row.optString("zc").split(',').forEach { token ->
                    val weeks = Regex("\\d+").findAll(token.substringBefore('(').substringBefore('（'))
                        .mapNotNull { it.value.toIntOrNull() }.toList()
                    if (weeks.isNotEmpty()) add(JwCourse(name, stripMarkup(row.optString("croommc")), stripMarkup(row.optString("tmc")), day, node, node, weeks.first(), weeks.getOrNull(1) ?: weeks.first(), type))
                }
            }
        }
    }

    fun parseCumtb(source: String): List<JwCourse> {
        val payload = (parseJson(source) as? JSONObject)?.optJSONObject("result") ?: return emptyList()
        val names = mutableMapOf<Int, String>()
        val lessons = payload.optJSONArray("lessonList") ?: JSONArray()
        for (i in 0 until lessons.length()) lessons.optJSONObject(i)?.let { lesson ->
            lesson.opt("id")?.toString()?.toIntOrNull()?.let { id ->
                names[id] = if (lesson.isNull("courseName")) "" else lesson.optString("courseName")
            }
        }
        val schedules = payload.optJSONArray("scheduleList") ?: return emptyList()
        return buildList {
            for (i in 0 until schedules.length()) {
                val row = schedules.optJSONObject(i) ?: continue
                // WakeUp 6.6.2 o000O0: unknown lessons keep their occurrence and clock times.
                fun integer(key: String): Int? = row.opt(key)?.toString()?.toIntOrNull()
                val lessonId = integer("lessonId") ?: continue
                val name = names[lessonId].orEmpty().ifBlank { "未知" }
                val day = integer("weekday") ?: continue
                val week = integer("weekIndex") ?: continue
                val startTime = integer("startTime") ?: continue
                val endTime = integer("endTime") ?: continue
                fun clock(value: Int) = value.toString().padStart(4, '0').let {
                    it.take(2) + ":" + it.drop(2)
                }
                val actualStart = clock(startTime)
                val actualEnd = clock(endTime)
                if (!JwCourse.isValidTimeRange(actualStart, actualEnd)) continue
                val start = timeToNode(startTime)
                if (day !in 1..7 || week < 1 || start < 1) continue
                val count = durationToNodes(startTime, endTime)
                val room = row.optJSONObject("room")?.let { if (it.isNull("nameZh")) "" else it.optString("nameZh") }.orEmpty()
                val teacher = if (row.isNull("personName")) "" else row.optString("personName")
                add(JwCourse(name, room, teacher, day, start, start + count - 1, week, week,
                    startTime = actualStart, endTime = actualEnd))
            }
        }
    }

    fun parseSouthSoft(source: String): List<JwCourse> {
        val body = Jsoup.parse(source).selectFirst("#kb tbody")
        if (body != null) return buildList {
            var unnumberedRows = 0
            for ((rowIndex, row) in body.select("tr").withIndex()) {
                val cells = row.children().filter { it.tagName() == "td" }
                if (cells.firstOrNull()?.text()?.contains("无节次") == true) unnumberedRows++
                val node = rowIndex + 1 - unnumberedRows
                if (node < 1) continue
                for ((day, cell) in cells.withIndex()) {
                    if (day !in 1..7) continue
                    for (block in cell.select("div")) {
                        val fields = block.html().split(Regex("(<[/]*((?!br).)*?> *)+"))
                            .map { Jsoup.parse(it).text().trim() }
                        for ((index, field) in fields.withIndex()) {
                            val weeks = Regex("(\\d+)-(\\d+)周").find(field) ?: continue
                            fun nameAt(offset: Int) = fields.getOrNull(index + offset).orEmpty()
                                .substringBefore("班级：").substringBefore("班级:").trim()
                            val name = nameAt(-1).ifBlank { nameAt(-2) }
                                .substringAfter("课程名称:").replace("||##", "").substringBefore("||").trim()
                            val teacher = fields.getOrNull(index + 1).orEmpty()
                                .replace("(连续周)", "").replace("连续周", "").trim()
                            val room = fields.getOrNull(index + 2).orEmpty()
                            val from = weeks.groupValues[1].toInt()
                            val to = weeks.groupValues[2].toInt()
                            if (name.isNotBlank() && from >= 1 && to >= from) {
                                add(JwCourse(name, room, teacher, day, node, node, from, to))
                            }
                        }
                    }
                }
            }
        }.distinct()
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
                    if (weeks.isNotEmpty()) add(JwCourse(row.optString("KCWZSM").ifBlank { fields[0] }, fields[3], fields[2], day, start, end, weeks.first(), weeks.getOrNull(1) ?: weeks.first(), when { token.contains("单") -> 1; token.contains("双") -> 2; else -> 0 }))
                }
            }
        }
    }

    /** New Kingosoft HTML layouts, in upstream OpenWakeUp order: #mytable, format two, #kbDiv. */
    fun parseKingoSuperHtml(source: String, errorList: MutableList<String>? = null): List<JwCourse> {
        val errors = errorList ?: mutableListOf()
        // Jsoup handles a head-less fragment and tables concatenated from same-origin frames.
        val documents = listOf(Jsoup.parse(source))
        fun nodes(text: String): Pair<Int, Int>? {
            if (text.trim() == "傍晚") return 9 to 9
            val match = Regex("^\\s*(\\d{1,2})(?:\\s*[-~至]\\s*(\\d{1,2}))?\\s*(?:节)?\\s*$").matchEntire(text) ?: return null
            val first = match.groupValues[1].toIntOrNull() ?: return null
            val last = match.groupValues[2].takeIf(String::isNotBlank)?.toIntOrNull() ?: first
            return if (first in 1..30 && last in first..30) first to last else null
        }
        fun emit(name: String, teacher: String, room: String, day: Int, nodeText: String, weeksText: String, credit: Float = 0f): List<JwCourse> {
            val (start, end) = nodes(nodeText) ?: return emptyList()
            if (name.isBlank() || day !in 1..7) return emptyList()
            // Keep this source's week grammar strict; the generic compatibility parser is deliberately permissive.
            val weekPattern = Regex("""^([0-9]+)(?:[-~至]([0-9]+))?周?(?:[\(（]([单双])[）\)]|([单双])周?)?$""")
            val weeks = weeksText.split(',', '，', ';', '；').map { raw ->
                val match = weekPattern.matchEntire(raw.filterNot(Char::isWhitespace)) ?: return emptyList()
                val first = match.groupValues[1].toIntOrNull() ?: return emptyList()
                val last = match.groupValues[2].takeIf(String::isNotBlank)?.let { it.toIntOrNull() ?: return emptyList() } ?: first
                if (first !in 1..366 || last !in first..366) return emptyList()
                val parity = match.groupValues[3].ifBlank { match.groupValues[4] }
                Triple(first, last, when (parity) { "单" -> 1; "双" -> 2; else -> 0 })
            }
            val expanded = weeks.flatMap { (from, to, type) ->
                (from..to).filter { type == 0 || it % 2 == type % 2 }
            }
            // Legacy positive-selection pages can repeat weeks in overlapping tokens.
            // Merge their set only when necessary; retain explicit parity ranges otherwise.
            val normalized = if (expanded.size == expanded.distinct().size) weeks else {
                val ordered = expanded.distinct().sorted()
                buildList {
                    var i = 0
                    while (i < ordered.size) {
                        val step = if (i + 1 < ordered.size && ordered[i + 1] - ordered[i] == 2) 2 else 1
                        var j = i
                        while (j + 1 < ordered.size && ordered[j + 1] - ordered[j] == step) j++
                        add(Triple(ordered[i], ordered[j], if (step == 1) 0 else if (ordered[i] % 2 == 1) 1 else 2))
                        i = j + 1
                    }
                }
            }
            return normalized
                .map { (from, to, type) -> JwCourse(name, room, teacher, day, start, end, from, to, type, credit = credit) }
        }

        val myTable = buildList {
            for (doc in documents) for (table in doc.select("#mytable")) {
                fun isDayHeader(text: String, day: Int): Boolean {
                    val t = text.trim()
                    if (t.length > 3) return false
                    val name = when (day) {
                        1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; 6 -> "六"; 7 -> "日"
                        else -> return false
                    }
                    return t == name || (day == 7 && t == "天") ||
                        t.contains("星期$name") || (day == 7 && t.contains("星期天")) ||
                        t.contains("周$name") || (day == 7 && t.contains("周天")) ||
                        t.contains("星期$day") || t.contains("周$day")
                }

                val rows = table.select("tr").filter { row ->
                    row.parents().firstOrNull { it.tagName() == "table" } === table
                }
                val grid = List(rows.size) { sortedMapOf<Int, Element>() }
                for ((rowIndex, row) in rows.withIndex()) {
                    var column = 0
                    for (cell in row.children().filter { it.tagName() == "td" || it.tagName() == "th" }) {
                        while (grid[rowIndex].containsKey(column)) column++
                        val rowSpan = (cell.attr("rowspan").toIntOrNull() ?: 1).coerceIn(1, rows.size - rowIndex)
                        val colSpan = (cell.attr("colspan").toIntOrNull() ?: 1).coerceIn(1, 1000)
                        for (r in rowIndex until rowIndex + rowSpan) {
                            for (c in column until column + colSpan) grid[r][c] = cell
                        }
                        column += colSpan
                    }
                }
                val dayColumns = grid.firstNotNullOfOrNull { row ->
                    row.mapNotNull { (column, cell) ->
                        val text = cell.text().trim()
                        val day = (1..7).firstOrNull { day ->
                            isDayHeader(text, day) && text.length <= 3
                        }
                        day?.let { column to it }
                    }.toMap().takeIf { it.size >= 2 }
                }.orEmpty()
                val visited = mutableSetOf<Element>()

                for ((rowIndex, row) in rows.withIndex()) {
                    if (row.hasClass("H")) continue
                    if (row.select("th").isNotEmpty() || row.children().any { isDayHeader(it.text(), 1) }) continue
                    val allCells = row.children().filter { it.tagName() == "td" }
                    if (allCells.isEmpty()) continue

                    for ((column, cell) in grid[rowIndex]) {
                        if (!visited.add(cell)) continue
                        val day = if (dayColumns.isNotEmpty()) {
                            dayColumns[column] ?: continue
                        } else {
                            // Empty weekday cells remain columns even without a td class.
                            // Only explicit leading period/time labels are gutters.
                            val gutters = grid[rowIndex].entries.takeWhile { (_, leading) ->
                                !leading.hasClass("td") && (leading.text().trim() in
                                    setOf("节次", "时间", "时段", "上午", "下午", "晚上") ||
                                    Regex("第?\\d+(?:[-~至]\\d+)?节").matches(leading.text().trim()))
                            }.size
                            column - gutters + 1
                        }
                        if (day !in 1..7) continue

                        // The div1 marker applies to subsequent blocks in this cell only.
                        var compact = false
                        val blocks = cell.children().filter { it.tagName() == "div" }.ifEmpty {
                            if (Regex("\\[\\s*(?:\\d+|傍晚)").containsMatchIn(cell.text())) listOf(cell) else emptyList()
                        }
                        for (block in blocks) {
                            if (block.hasClass("div1")) {
                                compact = block.html().contains("xkinfo") && block.html().contains("&nbsp;")
                                continue
                            }
                            // In compact output NBSP separates fields, not words. A normal
                            // space inside the timing line separates the teacher from weeks.
                            val html = if (compact) block.html().replace(Regex("(?i)(?:&nbsp;)+"), "<br>") else block.html()
                            val lines = html.split(Regex("(?i)<br\\s*/?>")).map { Jsoup.parse(it).text().trim() }.filter { it.isNotEmpty() }
                            val timingIdx = lines.indexOfFirst { line ->
                                ('[' in line && ']' in line) && Regex("\\[\\s*(?:\\d+|傍晚)").containsMatchIn(line)
                            }
                            if (timingIdx < 0) continue

                            val name = lines.firstOrNull().orEmpty()
                            val detail = lines[timingIdx]
                            val beforeBracket = detail.substringBefore('[').trim()
                            val nodeText = detail.substringAfter('[', "").substringBefore(']').trim()

                            val compactMode = compact || (timingIdx == 1 && ' ' in beforeBracket && ',' !in beforeBracket && '，' !in beforeBracket)
                            val (teacher, weekText) = if (compactMode && ' ' in beforeBracket) {
                                detail.substringBefore(' ').trim() to detail.substringAfter(' ', "").substringBefore('[').trim()
                            } else {
                                lines.subList(1, timingIdx).joinToString(" ").trim() to beforeBracket
                            }

                            val room = lines.drop(timingIdx + 1).joinToString(" ").trim()
                            if (name.isNotEmpty() && weekText.isNotEmpty() && nodeText.isNotEmpty()) {
                                addAll(emit(name, teacher, room, day, nodeText, weekText))
                            }
                        }
                    }
                }
            }
        }
        if (myTable.isNotEmpty()) return myTable
        errors += "chain1 #mytable: no valid weekday cell"

        val formatTwo = buildList {
            for (doc in documents) for (table in doc.select("table")) {
                val headers = table.selectFirst("thead tr")?.select("th,td")?.map { it.text().trim() } ?: continue
                val nameIndex = headers.indexOfFirst { it == "课程" }
                val nodeIndex = headers.indexOfFirst { it.contains("节次") }
                if (nameIndex < 0 || nodeIndex < 0) continue
                val teacherIndex = headers.indexOfFirst { it.contains("教师") }
                val roomIndex = headers.indexOfFirst { it.contains("地点") }
                val weeksIndex = headers.indexOfFirst { it.contains("周次") }
                val parityIndex = headers.indexOfFirst { it.contains("单双周") }
                val creditIndex = headers.indexOfFirst { it == "学分" }
                for (row in table.select("tbody tr")) {
                    val cells = row.children()
                    val name = cells.getOrNull(nameIndex)?.text()?.substringAfter(']')?.trim().orEmpty()
                    val timing = cells.getOrNull(nodeIndex)?.text()?.trim().orEmpty()
                    val day = extractDay(timing.substringBefore('[')) ?: continue
                    val nodeText = timing.substringAfter('[', "").substringBefore(']')
                    val weeks = if (weeksIndex >= 0) cells.getOrNull(weeksIndex)?.text().orEmpty() else timing.substringAfter('(', "").substringBefore('周')
                    val parityText = cells.getOrNull(parityIndex)?.text().orEmpty()
                    val parity = when {
                        parityText.contains('单') -> "单"
                        parityText.contains('双') -> "双"
                        else -> ""
                    }
                    val credit = cells.getOrNull(creditIndex)?.text()?.trim()?.toFloatOrNull()
                        ?.takeIf { it.isFinite() && it >= 0f } ?: 0f
                    val typedWeeks = weeks.replace('，', ',').split(',').joinToString(",") { part ->
                        val t = part.trim()
                        if (t.contains('单') || t.contains('双') || parity.isBlank()) t else "$t$parity"
                    }
                    addAll(emit(name, cells.getOrNull(teacherIndex)?.text()?.substringAfter(']').orEmpty(),
                        cells.getOrNull(roomIndex)?.text().orEmpty(), day, nodeText, typedWeeks, credit))
                }
            }
        }
        if (formatTwo.isNotEmpty()) return formatTwo
        errors += "chain2 thead: no valid course row"

        val kbDiv = buildList {
            for (doc in documents) for (table in doc.select("#kbDiv")) {
                val body = table.select("tbody").lastOrNull() ?: continue
                for ((rowIndex, row) in body.select("tr").withIndex()) {
                    val allCells = row.select("td")
                    val cells = allCells.takeLast(7)
                    for ((dayIndex, cell) in cells.withIndex()) {
                        val text = cell.text().trim()
                        // Teaching arrangements append timing after the course title;
                        // the title itself may already contain parentheses.
                        val name = text.substringBeforeLast('(').trim()
                        val detail = text.substringAfterLast('(', "").substringBefore(')', "")
                        val parts = detail.split(Regex("\\s+")).filter(String::isNotBlank)
                        if (name.isBlank() || parts.isEmpty()) continue
                        addAll(emit(name, "", if (parts.size > 1) parts.last() else "", dayIndex + 1,
                            (rowIndex + 1).toString(), parts[0]))
                    }
                }
            }
        }
        if (kbDiv.isNotEmpty()) return kbDiv
        errors += "chain3 #kbDiv: no valid timetable cell"
        return emptyList()
    }

    /** Older catalog declarations may route a classic EAMS page here.
     * TaskActivity has seven positional fields. SIMC's special periods are a separate protocol.
     */
    fun parseKingoTaskActivity(source: String): List<JwCourse> =
        JwClassicEamsParser(source).generateCourseList()

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
                val cs = if (td.hasAttr("colspan")) td.attr("colspan").toIntOrNull() else 1
                val rs = if (td.hasAttr("rowspan")) td.attr("rowspan").toIntOrNull() else 1
                if (cs == null || cs !in 1..ths.size || rs == null || rs !in 1..dataRows.size - rowIdx) return emptyList()
                while (col in occupiedNow) col += 1
                if (col + cs > ths.size) {
                    if (td.text().isBlank() && cs == 1 && rs == 1) continue
                    return emptyList()
                }
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
            if (dayColIdx !in 0..6) continue
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
                        parseXjuWeeksWithBrackets(inner).forEach { (from, to, teacher, room, type) ->
                            out += JwCourse(name, room, teacher, day, startNode, endNode, from, to, type)
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
            }
        }
        return out
    }

    /** 形态 B 块内解析: "12-19周[教师:孙冬璞,地点:待定]" */
    private fun parseXjuWeeksWithBrackets(inner: String): List<XjuWeekSlot> {
        val out = mutableListOf<XjuWeekSlot>()
        val segments = Regex("([^\\[\\]]+)\\[([^\\[\\]]*)\\]").findAll(inner).toList()
        if (segments.isEmpty() || inner.substring(segments.last().range.last + 1).isNotBlank()) return emptyList()
        for (segment in segments) {
            val weeks = segment.groupValues[1].trim().trimStart('、', '，', ',').trim()
            val bracket = segment.groupValues[2]
            val teacher = Regex("教师[:：]([^,，\\]]*)").find(bracket)?.groupValues?.get(1)?.trim().orEmpty()
            val room = Regex("地点[:：]([^,，\\]]*)").find(bracket)?.groupValues?.get(1)?.trim().orEmpty()
            for ((from, to, type) in schoolWeekRanges(weeks, trailingParityAppliesToAll = true)) {
                out += XjuWeekSlot(from, to, teacher, room, type)
            }
        }
        return out
    }

    private data class XjuWeekSlot(val from: Int, val to: Int, val teacher: String, val room: String, val type: Int)


    /** Suzhou graduate grid: non-centered rows are periods, centered cells are gutters. */
    fun parseSuda(source: String): List<JwCourse> {
        val doc = Jsoup.parse(source)
        val table = doc.getElementById("DataGrid1") ?: doc.getElementById("MainWork_DataGrid1")
            ?: return emptyList()
        val occupiedUntil = IntArray(7)
        return buildList {
            var node = 0
            for (row in table.select("tr").filter { it.parents().firstOrNull { p -> p.tagName() == "table" } === table }) {
                if (row.attr("align").equals("center", true) || row.children().any { it.tagName() == "th" }) continue
                if (row.children().any { Regex("(?:星期|周)[一二三四五六日天]").matches(it.text().trim()) }) continue
                node++
                var column = 0
                for (cell in row.children().filter { it.tagName() == "td" }) {
                    if (cell.attr("align").equals("center", true)) continue
                    while (column < 7 && occupiedUntil[column] >= node) column++
                    if (column >= 7) break
                    val day = column + 1
                    val span = (cell.attr("rowspan").toIntOrNull() ?: 1).coerceIn(1, 30)
                    occupiedUntil[column] = node + span - 1
                    column++
                    if (cell.text().isBlank()) continue
                    val blocks = cell.html().split(Regex("(?i)(?:<br\\s*/?>\\s*){2,}"))
                    for (block in blocks) {
                        val lines = block.split(Regex("(?i)<br\\s*/?>"))
                            .map { Jsoup.parse(it).text().trim() }.filter(String::isNotBlank)
                        val name = lines.firstOrNull { it.startsWith("课程:") || it.startsWith("课程：") }
                            ?.substringAfter(':')?.substringAfter('：')?.trim() ?: lines.firstOrNull().orEmpty()
                        val room = lines.firstOrNull { it.startsWith('(') && !it.contains("辅讲教师") }
                            ?.substringAfter('(')?.substringBeforeLast(')')?.trim().orEmpty()
                        val teacher = lines.firstOrNull { it.startsWith("主讲教师:") || it.startsWith("主讲教师：") }
                            ?.substringAfter(':')?.substringAfter('：')?.trim().orEmpty()
                        if (name.isBlank()) continue
                        for (line in lines.filter { Regex("\\d周").containsMatchIn(it) }) {
                            for (token in line.split(';', ',', '；', '，')) {
                                val week = Regex("第?\\s*(\\d+)(?:\\s*-\\s*(\\d+))?\\s*周(?:单双|[单双])?")
                                    .find(token) ?: continue
                                val from = week.groupValues[1].toIntOrNull() ?: continue
                                val to = week.groupValues[2].toIntOrNull() ?: from
                                val type = when {
                                    token.contains("单双") -> 0
                                    token.contains('单') -> 1
                                    token.contains('双') -> 2
                                    else -> 0
                                }
                                if (from > 0 && to >= from) add(JwCourse(name, room, teacher, day, node, node + span - 1, from, to, type))
                            }
                        }
                    }
                }
            }
        }.distinct()
    }

    fun confidence(source: String, markers: Set<String>): Int = when {
        markers.any { source.contains(it, ignoreCase = true) } -> 90
        source.trimStart().startsWith("{") || source.trimStart().startsWith("[") -> 70
        else -> 0
    }

    fun features(source: String, markers: Set<String>): List<String> = markers.filter { source.contains(it, true) }.map { "marker=$it" }

    fun parseShuwei(source: String): List<JwCourse> {
        val root = parseJson(source.substringAfter("index.js');", source)) ?: return emptyList()
        // table0.activities is a flattened day-by-period matrix. Its array position is
        // authoritative; course objects intentionally have no weekday or startSection.
        if (root is JSONObject) {
            val slots = root.optJSONArray("activities")
            if (slots != null && (0 until slots.length()).any { slots.opt(it) is JSONArray }) {
                val units = if (root.has("unitCount")) root.optInt("unitCount", 0)
                    else root.optJSONArray("courseUnits")?.length() ?: 12
                if (units <= 0) return emptyList()
                return buildList {
                    for (index in 0 until slots.length()) {
                        val day = index / units + 1
                        if (day !in 1..7) continue
                        val node = index % units + 1
                        val items = slots.optJSONArray(index) ?: continue
                        for (i in 0 until items.length()) {
                            val item = items.optJSONObject(i) ?: continue
                            val name = item.optString("courseName").trim()
                            val bitmap = item.optString("vaildWeeks").trim()
                            if (name.isBlank() || bitmap.any { it != '0' && it != '1' }) continue
                            for (week in 1 until bitmap.length) {
                                if (bitmap[week] == '1') add(JwCourse(name, item.optString("roomName"),
                                    item.optString("teacherName"), day, node, node, week, week))
                            }
                        }
                    }
                }.distinct()
            }
        }
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
        return result.distinct()
    }

    /** UMOOC 教务系统 HTML 解析器。
     *  特征: timetable 元素, infolist_hr_common 类。
     *  解析逻辑: 查找 timetable 容器, 遍历行提取课程数据。 */
    fun parseUmooc(source: String): List<JwCourse> {
        val table = Jsoup.parse(source).getElementById("timetable") ?: return emptyList()
        return mergeCourseWeeks(buildList {
            for (row in table.getElementsByClass("infolist_hr_common")) {
                var headerNode: Int? = null
                for (cell in row.children()) {
                    if (cell.tagName() == "th") {
                        headerNode = Regex("第\\s*(\\d+)\\s*节").find(cell.text())
                            ?.groupValues?.get(1)?.toIntOrNull()
                    }
                    if (!cell.html().contains("&lt;&lt;")) continue
                    val identity = cell.id().split('-')
                    if (identity.size != 2) continue
                    val day = identity.firstOrNull()?.trim()?.toIntOrNull() ?: continue
                    val node = headerNode ?: identity.getOrNull(1)?.trim()?.toIntOrNull() ?: continue
                    if (day !in 1..7 || node !in 1..30) continue
                    for (block in cell.html().split("&lt;&lt;").drop(1)) {
                        val lines = block.split(Regex("(?i)<br\\s*/?>"))
                            .map { Jsoup.parse(it).text().trim() }.dropLastWhile(String::isBlank)
                        val name = lines.firstOrNull()?.substringBefore(">>")?.trim().orEmpty()
                        if (name.isEmpty() || lines.size < 3) continue
                        val room = lines[1]
                        val teacher = if (lines.size < 5) lines[1] else lines[2]
                        val weeksText = lines.asReversed().firstOrNull { line ->
                            (Regex("\\d").containsMatchIn(line) &&
                                !line.contains("学时") && !line.contains("补课") &&
                                !Regex("\\d.*节|T\\d").containsMatchIn(line)) ||
                                Regex("[全双单]周").containsMatchIn(line)
                        }?.substringBefore('【')?.trim().orEmpty()
                        for (week in umoocWeeks(weeksText)) {
                            add(JwCourse(name, room, teacher, day, node, node, week, week))
                        }
                    }
                }
            }
        })
    }

    /** YL uses nested course boxes. The first table column is a period gutter. */
    fun parseYl(source: String): List<JwCourse> {
        val table = Jsoup.parse(source).selectFirst("div[class*=app-components-CourseTimeTable-styles-timeTable]")
            ?: return emptyList()
        val body = table.selectFirst("tbody") ?: return emptyList()
        return buildList {
            for (row in body.select("tr")) {
                for ((day, cell) in row.children().filter { it.tagName() == "td" }.withIndex()) {
                    if (day !in 1..7) continue
                    for (box in cell.select("div[class*=app-components-CourseTimeTable-styles-courseBox]")) {
                        val fields = box.children()
                        if (fields.size < 5) continue
                        val name = fields[0].text().trim()
                        val timing = fields[1].children()
                        if (name.isEmpty() || timing.size < 3) continue
                        val nodes = Regex("\\d+").findAll(timing[2].text()).map { it.value.toInt() }.toList()
                        val start = nodes.firstOrNull() ?: continue
                        val end = nodes.last()
                        if (start < 1 || end < start) continue
                        val room = fields[3].text().substringBefore('(').trim()
                        val teacher = fields[4].text().trim()
                        for ((from, to, type) in parseWeekTokens(timing[1].text())) {
                            if (from >= 1 && to >= from) add(JwCourse(name, room, teacher, day, start, end, from, to, type))
                        }
                    }
                }
            }
        }.distinct()
    }

    private fun parseActivity(row: JSONObject): List<JwCourse>? {
        val name = first(row, "courseName", "course", "name", "kcmc", "KCM")
        val day = firstInt(row, "weekday", "weekDay", "dayOfWeek", "classDay", "xqj", "day", "xq")
        val start = firstInt(row, "startSection", "beginSection", "beginNumber", "startNode", "ksjc", "start")
        if (name.isBlank() || day == null || day !in 1..7 || start == null || start < 1) return null

        val end = firstInt(row, "endSection", "endNumber", "endNode", "jsjc", "end") ?: start
        if (end < start) return null
        val weeks = if (row.has("startWeek") && row.has("endWeek")) {
            val from = firstInt(row, "startWeek") ?: return null
            val to = firstInt(row, "endWeek") ?: return null
            val type = if (row.has("type")) firstInt(row, "type") ?: return null else 0
            if (type !in 0..3) return null
            listOf(Triple(from, to, type))
        } else parseWeekTokens(first(row, "weeksStr", "weekDescription", "week", "weeks", "zc"))
        if (weeks.any { (from, to, _) -> from < 1 || to < from }) return null
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
                    if (item is JSONObject) parseActivity(item)?.let(result::addAll)
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
            val weeks = parseWeekTokens(row.attr("data-weeks"))
            val end = row.attr("data-end-node").toIntOrNull() ?: node
            if (name.isBlank() || day !in 1..7 || node < 1 || end < node) continue
            for ((from, to, type) in weeks) if (from > 0 && to >= from) {
                add(JwCourse(name, row.attr("data-room").ifBlank { cells.getOrNull(3)?.text().orEmpty() },
                    row.attr("data-teacher").ifBlank { cells.getOrNull(2)?.text().orEmpty() }, day, node, end, from, to, type))
            }
        }
    }

    private fun first(o: JSONObject, vararg keys: String): String = keys.firstNotNullOfOrNull { o.optString(it).takeIf(String::isNotBlank) }.orEmpty()
    private fun firstInt(o: JSONObject, vararg keys: String): Int? = keys.firstNotNullOfOrNull { o.optString(it).trim().toIntOrNull() }
    private fun stripMarkup(value: String): String = Jsoup.parse(value).text().trim()
    private fun extractDay(text: String): Int? = Regex("(?:星期|周)\\s*([一二三四五六日天1-7])").find(text)?.groupValues?.get(1)?.let { when (it) { "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6; "日", "天" -> 7; else -> it.toIntOrNull() } }
    private fun extractNode(text: String): Int? = Regex("(?:第\\s*)?(\\d{1,2})\\s*(?:[-~至](\\d{1,2}))?\\s*节").find(text)?.groupValues?.get(1)?.toIntOrNull()
    private fun timeToNode(time: Int): Int = when { time < 1230 -> ((time - 800) / 100) + 1; time < 1800 -> ((time - 1400) / 100) + 5; else -> ((time - 1900) / 100) + 9 }
    private fun durationToNodes(start: Int, end: Int): Int = when (end - start) { in 50..99 -> 1; in Int.MIN_VALUE..200 -> 2; in 210..340 -> 3; else -> 4 }
}

abstract class WakeUpMarkerParser(source: String, private val markers: Set<String>) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> = WakeUpCompat.parse(source, markers)
    override fun confidence(): Int = WakeUpCompat.confidence(source, markers)
    override fun matchedFeatures(): List<String> = WakeUpCompat.features(source, markers)
}
class JwKingoParser(source: String) : WakeUpMarkerParser(source, setOf("kingosoft", "courseTableForStd", "courseTableStudent", "new TaskActivity", "CourseTableForStd", "ScheduleTaskBean", "xkinfo", "kbDiv")) {
    override fun confidence(): Int = when {
        source.contains("id=\"kbDiv\"") || source.contains("id='kbDiv'") -> 95
        source.contains("<thead") && source.contains("课程") && source.contains("节次") &&
            Regex("(?:星期|周)[一二三四五六日天1-7]").containsMatchIn(source) -> 93
        source.contains("id=\"mytable\"") || source.contains("id='mytable'") -> 91
        else -> super.confidence()
    }
    override fun generateCourseList(): List<JwCourse> {
        // Kingo Super 2026-10 — 内部叠加 HTML 3 链降级 (不新建独立类, 与 TYPE_KINGO_NEW 共用)
        // 阶段 1: HTML 3 链降级 (新青果 super 主入口) — 见 parseKingoSuperHtml
        val errors = mutableListOf<String>()
        val superHtml = WakeUpCompat.parseKingoSuperHtml(source, errors)
        if (superHtml.isNotEmpty()) return superHtml
        // Older catalog declarations can point at a classic EAMS page.
        val taskActivity = WakeUpCompat.parseKingoTaskActivity(source)
        if (taskActivity.isNotEmpty()) return taskActivity
        // 阶段 3: 父类 WakeUpMarkerParser (markers 命中回退)
        val parent = super.generateCourseList()
        if (parent.isNotEmpty()) return parent
        // 全失败 — INV-ERROR-AGGREGATE: 错误列表聚合抛出
        errors += "Classic EAMS TaskActivity 回退解析为空"
        errors += "父类 WakeUpMarkerParser 链 (markers 命中回退) 解析为空"
        throw IllegalStateException("JwKingoParser (新青果 super 2026-10) 全链降级失败:\n" + errors.joinToString("\n"))
    }
}
class JwJzParser(source: String) : WakeUpMarkerParser(source, setOf("courseTableForStd", "courseTableStudent", "wisedu", "JinZhi")) {
    private val nativeFeatures: List<String> by lazy {
        val doc = Jsoup.parse(source.replace("divclass=", "div class="))
        buildList {
            for (selector in listOf(
                "table[id=1]", "table#CourseFormTable", "table.CourseFormTable",
                ".wut_table .mtt_arrange_item", ".wut_tablex td[data-role][data-week]",
                ".x-table", "table[id^=jsTbl_] td[xq][jc]",
                "td[data-role=item] .mtt_arrange_item",
            )) if (doc.selectFirst(selector) != null) add("jz=$selector")
            if (Regex("""\bvar\s+scheduleList\s*=""").containsMatchIn(source) &&
                Regex("""\bvar\s+notArrangedList\s*=""").containsMatchIn(source)) add("jz=scheduleList")
        }
    }
    override fun confidence(): Int = if (nativeFeatures.isNotEmpty()) 95 else super.confidence()
    override fun matchedFeatures(): List<String> = nativeFeatures + super.matchedFeatures()
}
class JwJzSuperParser(source: String) : WakeUpMarkerParser(source, setOf("el-table__body-wrapper", "Njw2017", "course-list", "v-jsxsd", "studentTableVms", "JinZhi")) {
    override fun generateCourseList() = WakeUpCompat.parseJzSuper(source).ifEmpty { super.generateCourseList() }
}
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
    override fun generateCourseList(): List<JwCourse> {
        val courses = WakeUpCompat.parseXju(source)
        if (source.contains("contentParent_dgData")) return courses
        return courses.ifEmpty { super.generateCourseList() }
    }
}

/** UMOOC 教务系统 HTML 解析器。
 *  特征: /academic/common/security/login.jsp, timetable 元素, infolist_hr_common 类。
 *  解析逻辑: 查找 timetable 容器, 遍历行提取课程数据。 */
class JwUmoocParser(source: String) : WakeUpMarkerParser(source, setOf("timetable", "infolist_hr_common", "academic/common/security")) {
    override fun generateCourseList(): List<JwCourse> = WakeUpCompat.parseUmooc(source)
}

/** 奕联教务系统 HTML 解析器。
 *  特征: /lyuapServer/login, app-components-CourseTimeTable-styles-timeTable 类。
 *  解析逻辑: 查找 timeTable 容器, 解析 tbody/tr 结构提取课程数据。 */
class JwYlParser(source: String) : WakeUpMarkerParser(source, setOf("CourseTimeTable-styles-timeTable", "lyuapServer", "奕联")) {
    override fun generateCourseList(): List<JwCourse> {
        val parsed = WakeUpCompat.parseYl(source)
        return parsed.ifEmpty { super.generateCourseList() }
    }
}
