package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Declared school layouts from the fixed 6.6.2 baseline. */
class JwPostgradSchoolParser(source: String, private val protocol: String) : JwParser(source) {
    private val document by lazy { Jsoup.parse(source) }

    override fun generateCourseList(): List<JwCourse> = when (protocol) {
        "xjtu_post" -> xjtu()
        "uestc_post" -> uestc()
        "bfa_post" -> bfa()
        "gdbyxy" -> gdbyxy()
        "cupl_post" -> cupl()
        "ysu_post" -> ysu()
        "whu_post" -> whu()
        "hrbeu_post" -> hrbeu()
        "shtu_post" -> shtu()
        "ustc_post" -> ustc()
        "xauat_post" -> xauat()
        "zju_post" -> zju()
        "csv" -> csv()
        "shuwei_old" -> shuweiOld()
        else -> emptyList()
    }.distinct()

    override fun confidence(): Int = when (protocol) {
        "xjtu_post" -> if (document.select("#tbl td[id]").any { "课程" in it.text() }) 100 else 0
        "uestc_post" -> if (document.select("#tbl td").any { it.text().count { c -> c == '/' } >= 7 }) 100 else 0
        "bfa_post" -> if (document.select("#table .pages-sec-student-scheme-pages-student-timetable-query-timetable-index-tableContent td[_nk]").isNotEmpty()) 100 else 0
        "gdbyxy" -> if (document.getElementById("pageRpt") != null) 100 else 0
        "cupl_post" -> if (document.getElementById("tabCT") != null) 100 else 0
        "ysu_post" -> if (document.select("#DataGrid1, #MainWork_DataGrid1").isNotEmpty()) 100 else 0
        "whu_post" -> if (document.select(".table_con").isNotEmpty()) 100 else 0
        "hrbeu_post" -> if (document.select("#StuCul_TimetableQry_TimeTable .WtbodyZlistS").isNotEmpty()) 100 else 0
        "shtu_post" -> if (document.getElementById("div-table") != null) 100 else 0
        "ustc_post" -> if (document.getElementById("tablexskbcx-index-table") != null) 100 else 0
        "xauat_post" -> if (Regex("td_[1-7]_[0-9]{2}").containsMatchIn(source) && "td.innerHTML" in source) 100 else 0
        "zju_post" -> if ("kcbForm" in source && runCatching { JSONArray(source) }.isSuccess) 100 else 0
        "csv" -> if (source.lineSequence().firstOrNull()?.let { "课程名称" in it && "开始节数" in it } == true) 100 else 0
        "shuwei_old" -> if (Regex("var\\s+activity\\s*=\\s*null;").containsMatchIn(source) && "table0.marshalTable" in source) 100 else 0
        else -> 0
    }

    override fun matchedFeatures(): List<String> = if (confidence() > 0) listOf("postgrad/$protocol") else emptyList()

    private fun xjtu(): List<JwCourse> = buildList {
        for (cell in document.select("#tbl td[id]")) {
            val day = cell.id().getOrNull(3)?.digitToIntOrNull()?.takeIf { it in 1..7 } ?: continue
            for (block in blocks(cell.html())) {
                val fields = lines(block).dropWhile(String::isBlank)
                if (fields.size < 6 || !fields[0].contains("课程")) continue
                val name = afterLabel(fields[0]) + afterLabel(fields[1])
                addSlots(name, afterLabel(fields[3]), afterLabel(fields[2]), day,
                    afterLabel(fields[4]), afterLabel(fields[5]))
            }
        }
    }

    private fun uestc(): List<JwCourse> = buildList {
        for (row in document.select("#tbl tr").drop(1)) {
            for ((day, cell) in row.children().filter { it.tagName() == "td" }.withIndex()) {
                if (day !in 1..7) continue
                for (entry in cell.text().split(Regex("，\\s*"))) {
                    val fields = entry.split('/')
                    if (fields.size < 8 || fields[1].isBlank()) continue
                    val nodes = Regex("[（(]([^()（）]+)[）)]").find(fields[6])?.groupValues?.get(1) ?: continue
                    addSlots(fields[1], fields[7], fields[4], day, nodes.replace('~', '-'), fields[5])
                }
            }
        }
    }

    private fun bfa(): List<JwCourse> = buildList {
        for (row in document.select("#table .pages-sec-student-scheme-pages-student-timetable-query-timetable-index-tableContent tr")) {
            // Empty _nk cells occupy a weekday too. Filtering them first shifts later courses.
            val cells = row.children().filter { it.tagName() == "td" && it.hasAttr("_nk") }
            for ((column, cell) in cells.withIndex()) {
                val day = column + 1
                if (day !in 1..7 || cell.attr("_nk").isBlank()) continue
                val body = cell.children().getOrNull(0) ?: continue
                val fields = body.children().map(Element::text)
                if (fields.size < 5) continue
                val roomWeeks = Regex("^(.*?)\\[([^]]+)]\\s*$").matchEntire(fields[4]) ?: continue
                val nodeLabel = cell.children().getOrNull(1)?.text() ?: continue
                val numbers = Regex("[0-9]+").findAll(nodeLabel).map { it.value.toIntOrNull() }.filterNotNull().toList()
                if (numbers.isEmpty()) continue
                // This source only declares endpoints. Do not treat arbitrary sparse labels as a span.
                val nodeText = when {
                    Regex("\\s*[0-9]+\\s*(?:[-~至到]\\s*[0-9]+\\s*)?(?:节)?").matches(nodeLabel) ->
                        "${numbers.first()}-${numbers.last()}"
                    else -> nodeLabel.removeSuffix("节")
                }
                val weekText = roomWeeks.groupValues[2]
                val weekRanges = weeks(weekText)
                val nodeRanges = nodes(nodeText)
                // Preserve the baseline's declared derived workload value; it is not a claimed transcript credit.
                val credit = if (weekRanges.size == 1 && nodeRanges.size == 1) {
                    val (first, last, _) = weekRanges.single()
                    val (start, end) = nodeRanges.single()
                    (((last - first + 1) * (end - start + 1)) / 16).toFloat()
                } else 0f
                addSlots(fields[1], roomWeeks.groupValues[1], fields[3], day, nodeText, weekText,
                    credit = credit, note = fields[2])
            }
        }
    }

    private fun gdbyxy(): List<JwCourse> {
        // Captures may concatenate full documents. The baseline uses the last nested table in pageRpt.
        for (fragment in source.split(Regex("(?i)</?head[^>]*>"))) {
            val table = Jsoup.parse(fragment).getElementById("pageRpt")?.getElementsByTag("table")?.lastOrNull() ?: continue
            val result = buildList {
                for (cell in table.getElementsByTag("td")) {
                    val fields = lines(cell.html())
                    for (index in fields.indices) {
                        val title = Regex("^\\[[A-Z][0-9]+](.+)$").matchEntire(fields[index]) ?: continue
                        val timing = fields.getOrNull(index + 2)?.let {
                            Regex("^\\[(.+)周]([一二三四五六日天])\\[(.+)节]([单双]?)$").matchEntire(it)
                        } ?: continue
                        val roomPart = fields.getOrNull(index + 3).orEmpty()
                        val room = when {
                            roomPart.isBlank() -> ""
                            '_' in roomPart -> roomPart.split('_').take(2).joinToString("-")
                            else -> roomPart + fields.getOrNull(index + 4).orEmpty()
                        }
                        addSlots(title.groupValues[1], room, fields.getOrNull(index + 1).orEmpty(),
                            chineseDay(timing.groupValues[2]), timing.groupValues[3],
                            timing.groupValues[1] + timing.groupValues[4])
                    }
                }
            }
            if (result.isNotEmpty()) return result
        }
        return emptyList()
    }

    private fun cupl(): List<JwCourse> = buildList {
        val table = document.getElementById("tabCT") ?: return@buildList
        val grid = grid(table) ?: return@buildList
        val days = headerDays(grid)
        for (item in grid) {
            val cell = item.cell
            if (item.row == 0 || item.width != 1 || cell.tagName() != "td") continue
            val day = days[item.column] ?: item.column
            for (block in cell.html().split(Regex("(?i)<b(?:\\s[^>]*)?>"))) {
                val fields = lines(block)
                val name = fields.firstOrNull()?.trim().orEmpty()
                for ((index, line) in fields.withIndex()) {
                    if (!line.startsWith("周次：") && !Regex("^第.+周$").matches(line)) continue
                    val campus = fields.getOrNull(index + 1)?.startsWith("校区") == true
                    val room = fields.getOrNull(index + if (campus) 2 else 1).orEmpty().removePrefix("教室：")
                    val teacher = fields.getOrNull(index + if (campus) 3 else 2).orEmpty().removePrefix("教师：")
                    addSlots(name, room, teacher, day, "${item.row}-${item.row + item.height - 1}", line.removePrefix("周次："))
                }
            }
        }
    }

    private fun ysu(): List<JwCourse> {
        for (fragment in source.split(Regex("(?i)</?head[^>]*>"))) {
            val doc = Jsoup.parse(fragment)
            val table = doc.selectFirst("#DataGrid1, #MainWork_DataGrid1") ?: continue
            val grid = grid(table) ?: continue
            val days = headerDays(grid)
            val rows = grid.map { it.row }.distinct().filter { index ->
                grid.firstOrNull { it.row == index }?.cell?.parent()?.attr("align")?.equals("center", true) != true
            }
            val nodeAtRow = rows.withIndex().associate { it.value to it.index + 1 }
            val result = buildList {
                for (item in grid) {
                    if (item.width != 1 || item.cell.tagName() != "td" || item.cell.attr("align").equals("center", true)) continue
                    val node = nodeAtRow[item.row] ?: continue
                    val day = days[item.column] ?: item.column
                    for (block in blocks(item.cell.html())) {
                        val fields = lines(block)
                        for ((index, line) in fields.withIndex()) {
                            val token = Regex("\\[([^]]+)周]").find(line) ?: continue
                            val name = fields.getOrNull(index - 1).orEmpty()
                            val room = Regex("教室[:：]([^)]*)").find(line)?.groupValues?.get(1).orEmpty()
                            val teacher = Regex("教师[:：]([^)]*)").find(line)?.groupValues?.get(1).orEmpty()
                            addSlots(name, room, teacher, day, "$node-${node + item.height - 1}", token.groupValues[1].replace("单双", ""))
                        }
                    }
                }
            }
            if (result.isNotEmpty()) return result
        }
        return emptyList()
    }

    private fun whu(): List<JwCourse> = buildList {
        val container = document.selectFirst(".table_con") ?: return@buildList
        val table = if (container.tagName() == "table") container else container.selectFirst("table") ?: return@buildList
        for (item in grid(table).orEmpty()) {
            if (item.width != 1 || item.cell.tagName() != "td") continue
            val day = item.column - 1
            for (line in lines(item.cell.html())) {
                val match = Regex("^(.+?)\\s+([^\\s]+周)([^\\s]+)节(?:\\s+(.*))?$").matchEntire(line) ?: continue
                val extras = match.groupValues[4].split(Regex("\\s+")).filter(String::isNotBlank)
                if (extras.size > 2) continue
                val room = if (extras.size == 2) extras[0].replace('，', ',') else ""
                val teacher = extras.lastOrNull().orEmpty()
                // Digits are part of the explicit course name. The baseline's unconditional trim is lossy.
                addSlots(match.groupValues[1].replace(" ", ""), room, teacher, day,
                    match.groupValues[3], match.groupValues[2])
            }
        }
    }

    private fun hrbeu(): List<JwCourse> = buildList {
        document.outputSettings().prettyPrint(false)
        val container = document.selectFirst("#StuCul_TimetableQry_TimeTable .WtbodyZlistS") ?: return@buildList
        val englishDays = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        for (row in container.select("tr")) {
            val cells = row.children().filter { it.tagName() == "td" }
            for ((column, cell) in cells.withIndex()) {
                if (column == 0) continue
                val explicit = englishDays.indexOf(cell.attr("field").lowercase()) + 1
                val day = if (explicit > 0) explicit else column
                for (block in blocks(cell.html().replace(Regex("\\n\\s*\\n"), "<br><br>"))) {
                    val original = lines(block.replace("\n", "<br>")).dropWhile(String::isBlank).dropLastWhile(String::isBlank)
                    if (original.size < 5) continue
                    val fields = original.map(::afterLabel)
                    val classLabel = Regex("[0-9]+班").find(fields[1])?.value
                    val name = fields[0] + if (classLabel != null) "($classLabel)" else ""
                    val note = original.drop(5).joinToString(", ")
                    if ("一班多师" in fields[1]) {
                        for (teaching in fields[3].split(';', '；')) {
                            val teacher = Regex("[（(]([^()（）]*)[）)]").find(teaching)?.groupValues?.get(1) ?: continue
                            val weekText = teaching.substringBefore('(').substringBefore('（')
                            addSlots(name, fields[4], teacher, day, fields[2], weekText, note = note)
                        }
                    } else {
                        val teacher = fields[1].replace(Regex("\\s*[0-9]+班"), "").trim()
                        addSlots(name, fields[4], teacher, day, fields[2], fields[3], note = note)
                    }
                }
            }
        }
    }

    private fun shtu(): List<JwCourse> = buildList {
        val container = document.getElementById("div-table") ?: return@buildList
        val table = if (container.tagName() == "table") container else container.selectFirst("table") ?: return@buildList
        for (item in grid(table).orEmpty()) {
            if (item.row !in 1..13 || item.column !in 1..7 || item.width != 1 || item.cell.tagName() != "td") continue
            val fields = lines(item.cell.html())
            for (index in 0 until fields.size / 4) {
                val offset = index * 4
                val teacher = fields[offset + 1]
                val room = fields[offset + 2]
                val label = fields[offset]
                val name = Regex("^(.*?)[0-9]+班.*$").matchEntire(label)?.groupValues?.get(1)
                    ?: "班级:$label,教师:$teacher"
                val rawWeeks = fields[offset + 3].replace('（', '(').replace('）', ')')
                val included = rawWeeks.substringBefore("(除")
                val excluded = if ("(除" in rawWeeks) {
                    val excludedText = Regex("\\(除(.+)\\)$").find(rawWeeks)?.groupValues?.get(1) ?: continue
                    val ranges = weeks(excludedText)
                    if (ranges.isEmpty()) continue
                    expandedWeeks(ranges).toSet()
                } else emptySet()
                for (week in expandedWeeks(weeks(included)).filterNot { it in excluded }) {
                    addSlots(name, room, teacher, item.column, "${item.row}-${item.row + item.height - 1}", week.toString())
                }
            }
        }
    }

    private fun ustc(): List<JwCourse> = buildList {
        val starts = listOf("07:50", "08:40", "09:45", "10:35", "11:25", "14:00", "14:50", "15:55", "16:45", "17:35", "19:30", "20:20", "21:10")
        val periods = starts.map { minute(it)!! to minute(it)!! + 45 }
        for (row in document.select("#tablexskbcx-index-table tr")) {
            val fields = row.children().filter { it.tagName() == "td" }.map { it.selectFirst("span")?.text() ?: it.text() }
            if (fields.size < 9) continue
            val weekGroups = fields[6].split(';')
            val arrangements = fields[8].split(';')
            if (weekGroups.size != arrangements.size) continue
            for ((index, arrangement) in arrangements.withIndex()) {
                val match = Regex("^(.*?):\\s*([1-7])\\(([^()]*)\\)\\s*$").matchEntire(arrangement.trim()) ?: continue
                val time = match.groupValues[3].trim()
                val weekText = weekGroups[index].replace(Regex("\\(([单双])[^)]*\\)"), "($1)")
                val clock = Regex("^([0-9]{1,2}:[0-9]{2})[~～-]([0-9]{1,2}:[0-9]{2})$").matchEntire(time)
                if (clock == null) {
                    addSlots(fields[5], match.groupValues[1], fields[7], match.groupValues[2].toInt(), time, weekText, note = fields[1])
                } else {
                    val start = minute(clock.groupValues[1]) ?: continue
                    val end = minute(clock.groupValues[2]) ?: continue
                    if (end <= start) continue
                    val covered = periods.indices.filter { periods[it].first < end && periods[it].second > start }
                    if (covered.isEmpty() || start < periods.first().first || end > periods.last().second) continue
                    val clockStart = "%02d:%02d".format(start / 60, start % 60)
                    val clockEnd = "%02d:%02d".format(end / 60, end % 60)
                    for ((firstWeek, lastWeek, parity) in weeks(weekText)) {
                        add(JwCourse(fields[5], match.groupValues[1].trim(), fields[7], match.groupValues[2].toInt(),
                            covered.first() + 1, covered.last() + 1, firstWeek, lastWeek, parity,
                            clockStart, clockEnd, note = fields[1]))
                    }
                }
            }
        }
    }

    private fun xauat(): List<JwCourse> = buildList {
        val script = document.selectFirst("script")?.data() ?: return@buildList
        val clean = script.lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
        val coordinates = Regex("td_([1-7])_([0-9]{2})").findAll(clean).toList()
        val assignments = Regex("td\\.innerHTML\\s*\\+=\\s*(\"(?:\\\\.|[^\"\\\\])*\")").findAll(clean).toList()
        if (coordinates.size != assignments.size) return@buildList
        for ((coordinate, assignment) in coordinates.zip(assignments)) {
            val html = runCatching { JSONTokener(assignment.groupValues[1]).nextValue() as? String }.getOrNull() ?: continue
            if (html.isBlank()) continue
            val fields = lines(html)
            if (fields.size < 4 || !fields[0].startsWith("课程:")) continue
            val timing = Regex("\\(([^)]+)\\)").find(fields[3])?.groupValues?.get(1) ?: continue
            val week = Regex("第([^周]+)周").find(timing)?.groupValues?.get(1) ?: continue
            val parity = when { "单" in timing -> "单"; "双" in timing -> "双"; else -> "" }
            val node = coordinate.groupValues[2].toInt() - 40
            val teacher = fields[2].removePrefix("主讲教师：").removePrefix("教师：").substringBefore('（')
            val room = fields[3].substringBefore('(').substringAfter('-')
            addSlots(fields[0].removePrefix("课程:"), room, teacher, coordinate.groupValues[1].toInt(), node.toString(), week + parity)
        }
    }

    private fun zju(): List<JwCourse> = buildList {
        val pages = runCatching { JSONArray(source) }.getOrNull() ?: return@buildList
        for (pageIndex in 0 until pages.length()) {
            val html = pages.opt(pageIndex) as? String ?: continue
            val form = Jsoup.parse(html).getElementById("kcbForm") ?: continue
            val table = form.parent()?.getElementsByTag("table")?.firstOrNull() ?: continue
            val occupiedUntil = IntArray(7)
            for ((rowIndex, row) in table.getElementsByTag("tr").withIndex()) {
                if (rowIndex == 0) continue
                var column = 0
                for (cell in row.children().filter { it.tagName() == "td" && it.ownText().isBlank() }) {
                    while (column < 7 && occupiedUntil[column] > rowIndex) column++
                    if (column >= 7) break
                    val height = if (cell.hasAttr("rowspan")) cell.attr("rowspan").toIntOrNull()?.takeIf { it in 1..30 } else 1
                    if (height == null) return@buildList
                    occupiedUntil[column] = rowIndex + height
                    for (anchor in cell.getElementsByTag("a")) {
                        val name = anchor.selectFirst("strong")?.text().orEmpty()
                        val tail = anchor.html().split(Regex("(?i)<br\\s*/?>\\s*<br\\s*/?>")).last()
                        val fields = lines(tail)
                        val teacherIndex = if (fields.size == 4) 2 else 3
                        if (fields.size <= teacherIndex + 1) continue
                        val token = Regex("\\(([^()]*)周\\)").find(fields.getOrNull(1).orEmpty().substringAfter("||"))?.groupValues?.get(1) ?: continue
                        val weekText = token.split(',', '，').map { part ->
                            val trimmed = part.trim()
                            when {
                                trimmed == "每" -> "1-20"
                                trimmed.startsWith("前") -> chineseNumber(trimmed.removePrefix("前"))?.let { "1-$it" } ?: trimmed
                                else -> chineseNumber(trimmed)?.toString() ?: trimmed
                            }
                        }.joinToString(",")
                        addSlots(name, fields[teacherIndex + 1], fields[teacherIndex], column + 1,
                            "$rowIndex-${rowIndex + height - 1}", weekText)
                    }
                    column++
                }
            }
        }
    }

    private fun csv(): List<JwCourse> {
        val delimiter = when {
            source.lineSequence().firstOrNull().orEmpty().contains("课程名称;星期") -> ';'
            source.lineSequence().firstOrNull().orEmpty().contains("课程名称\t星期") -> '\t'
            else -> ','
        }
        val rows = csvRows(source.removePrefix("\uFEFF"), delimiter) ?: return emptyList()
        val header = rows.firstOrNull() ?: return emptyList()
        if (header.size < 7 || header.take(4) != listOf("课程名称", "星期", "开始节数", "结束节数")) return emptyList()
        return buildList {
            for (row in rows.drop(1)) {
                if (row.all(String::isBlank)) continue
                if (row.size < 7 || row[0].isBlank()) continue
                val day = row[1].trim().toIntOrNull() ?: chineseDay(row[1])
                val start = row[2].trim().toIntOrNull()?.takeIf { it in 1..60 } ?: continue
                val end = row[3].trim().toIntOrNull()?.takeIf { it in start..60 } ?: continue
                if (day !in 1..7) continue
                for ((first, last, parity) in weeks(row[6])) add(JwCourse(row[0],row[5],row[4],day,start,end,first,last,parity))
            }
        }
    }

    /** Small RFC-style reader: escaped quotes and newlines stay inside their quoted field. */
    private fun csvRows(text: String, separator: Char): List<List<String>>? {
        val rows = mutableListOf<List<String>>()
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var closed = false
        var index = 0
        fun finishField() { fields += field.toString(); field.setLength(0); closed = false }
        fun finishRow() { finishField(); rows += fields.toList(); fields.clear() }
        while (index < text.length) {
            val character = text[index]
            if (quoted) {
                if (character == '"') {
                    if (text.getOrNull(index + 1) == '"') { field.append('"'); index++ }
                    else { quoted = false; closed = true }
                } else field.append(character)
            } else when (character) {
                '"' -> { if (field.isNotEmpty() || closed) return null; quoted = true }
                separator -> finishField()
                '\n', '\r' -> { finishRow(); if (character == '\r' && text.getOrNull(index + 1) == '\n') index++ }
                else -> { if (closed) return null; field.append(character) }
            }
            index++
        }
        if (quoted) return null
        if (field.isNotEmpty() || fields.isNotEmpty() || closed) finishRow()
        return rows
    }

    private fun shuweiOld(): List<JwCourse> = buildList {
        val section = Regex("var\\s+activity\\s*=\\s*null;([\\s\\S]*?)(?=table0\\.marshalTable)").find(source)?.groupValues?.get(1) ?: return@buildList
        val statements = Regex("(?:\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|[^;\"'])+;").findAll(section)
        var declaredName = ""
        var name = ""
        var teacher = ""
        var room = ""
        var weekList = emptyList<Int>()
        for (statement in statements) {
            val text = statement.value.trim()
            if (Regex("^var\\s+courseName\\s*=").containsMatchIn(text)) declaredName = jsStrings(text).firstOrNull().orEmpty()
            else if (Regex("^courseName\\s*\\+=").containsMatchIn(text)) declaredName += jsStrings(text).firstOrNull().orEmpty()
            if (Regex("^var\\s+actTeachers\\s*=").containsMatchIn(text)) {
                val teachers = runCatching { JSONArray(text.substringAfter('=').trim().removeSuffix(";")) }.getOrNull()
                teacher = if (teachers == null) "" else (0 until teachers.length()).mapNotNull { teachers.optJSONObject(it)?.optString("name")?.trim()?.takeIf(String::isNotBlank) }.joinToString(", ")
            }
            if (Regex("new\\s+TaskActivity\\s*\\(").containsMatchIn(text)) {
                val args = jsStrings(text)
                if (args.size < 5) { name = ""; weekList = emptyList(); continue }
                val group = if (args.size == 11) args[8] else args.getOrNull(6).orEmpty()
                name = (declaredName.ifBlank { args[1] }).substringBefore('(').substringBefore('[')
                if (declaredName.isBlank() && group.isNotBlank()) name += "(${group.removeSuffix("组")}组)"
                room = args[3]
                val bitmap = args[4]
                weekList = if (bitmap.all { it == '0' || it == '1' } && bitmap.firstOrNull() != '1')
                    (1 until bitmap.length).filter { bitmap[it] == '1' } else emptyList()
            }
            val coordinate = Regex("^index\\s*=\\s*([0-9]+)\\s*\\*\\s*unitCount\\s*\\+\\s*([0-9]+)\\s*;$").matchEntire(text) ?: continue
            val day = coordinate.groupValues[1].toIntOrNull()?.plus(1) ?: continue
            val node = coordinate.groupValues[2].toIntOrNull()?.plus(1) ?: continue
            for (week in weekList) addSlots(name,room,teacher,day,node.toString(),week.toString())
            declaredName = ""
        }
    }

    private fun jsStrings(text: String): List<String> = Regex("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'").findAll(text)
        .mapNotNull { runCatching { JSONTokener(it.value).nextValue() as? String }.getOrNull() }.toList()

    private fun expandedWeeks(ranges: List<Triple<Int, Int, Int>>): List<Int> = ranges.flatMap { (start,end,parity) ->
        (start..end).filter { parity == 0 || it % 2 == parity % 2 }
    }.distinct().sorted()

    private fun minute(time: String): Int? {
        val match = Regex("([0-9]{1,2}):([0-9]{2})").matchEntire(time) ?: return null
        val hour = match.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val minute = match.groupValues[2].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        return hour * 60 + minute
    }

    private fun chineseNumber(text: String): Int? {
        val digits = mapOf('一' to 1,'二' to 2,'三' to 3,'四' to 4,'五' to 5,'六' to 6,'七' to 7,'八' to 8,'九' to 9)
        text.toIntOrNull()?.let { return it }
        if (text.length == 1) return if (text == "十") 10 else digits[text[0]]
        val split = text.split('十')
        if (split.size != 2) return null
        val tens = if (split[0].isEmpty()) 1 else split[0].singleOrNull()?.let(digits::get) ?: return null
        val ones = if (split[1].isEmpty()) 0 else split[1].singleOrNull()?.let(digits::get) ?: return null
        return tens * 10 + ones
    }

    private data class GridCell(val cell: Element, val row: Int, val column: Int, val width: Int, val height: Int)

    private fun grid(table: Element): List<GridCell>? {
        val rows = table.getElementsByTag("tr").filter { it.parents().firstOrNull { p -> p.tagName() == "table" } == table }
        val occupied = mutableMapOf<Int, MutableSet<Int>>()
        val result = mutableListOf<GridCell>()
        for ((rowIndex, row) in rows.withIndex()) {
            var column = 0
            for (cell in row.children().filter { it.tagName() in setOf("td", "th") }) {
                while (column in occupied[rowIndex].orEmpty()) column++
                fun span(key: String): Int? = if (!cell.hasAttr(key)) 1 else
                    cell.attr(key).trim().toIntOrNull()?.takeIf { it in 1..128 }
                val width = span("colspan") ?: return null
                val height = span("rowspan") ?: return null
                if (column + width > 128 || rowIndex + height > 128) return null
                val columns = column until column + width
                if (columns.any { it in occupied[rowIndex].orEmpty() }) return null
                for (covered in rowIndex + 1 until rowIndex + height) occupied.getOrPut(covered) { mutableSetOf() }.addAll(columns)
                result += GridCell(cell, rowIndex, column, width, height)
                column += width
            }
        }
        return result
    }

    private fun headerDays(grid: List<GridCell>): Map<Int, Int> = grid.filter { it.cell.tagName() == "th" || it.cell.parent()?.attr("align").equals("center", true) }
        .mapNotNull { item -> chineseDay(item.cell.text()).takeIf { it in 1..7 }?.let { item.column to it } }.toMap()

    private fun MutableList<JwCourse>.addSlots(name: String, room: String, teacher: String, day: Int,
        nodeText: String, weekText: String, credit: Float = 0f, note: String = "") {
        if (name.isBlank() || day !in 1..7) return
        val weeks = weeks(weekText)
        val nodes = nodes(nodeText)
        for ((firstWeek, lastWeek, parity) in weeks) for ((firstNode, lastNode) in nodes) {
            add(JwCourse(name.trim(), room.trim(), teacher.trim(), day, firstNode, lastNode,
                firstWeek, lastWeek, parity, credit = credit, note = note.trim()))
        }
    }

    private fun weeks(raw: String): List<Triple<Int, Int, Int>> = schoolWeekRanges(
        raw.replace('、', ',').replace(';', ',').replace('；', ',').replace('~', '-').replace('～', '-'), true)

    private fun nodes(raw: String): List<Pair<Int, Int>> = schoolNodeRanges(
        raw.replace('、', ',').replace('~', '-').replace('～', '-').replace("至", "-").replace("到", "-")
            .removePrefix("第").removeSuffix("节").trim())

    private fun lines(html: String): List<String> = html.split(Regex("(?i)<br\\s*/?>"))
        .map { Jsoup.parseBodyFragment(it).text().trim() }

    private fun blocks(html: String): List<String> = html.split(Regex("(?i)<br\\s*/?>\\s*<br\\s*/?>"))
    private fun afterLabel(value: String): String = value.substringAfter('：', value.substringAfter(':', value)).trim()
    private fun chineseDay(text: String): Int = when (text.trim().removePrefix("星期").removePrefix("周")) {
        "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6; "日", "天", "七" -> 7
        else -> 0
    }
}
