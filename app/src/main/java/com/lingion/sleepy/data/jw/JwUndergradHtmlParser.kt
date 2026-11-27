package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal class JwUndergradHtmlParser(source: String, private val protocol: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> = when (protocol) {
        "jmucy" -> firstSemester("#ctl00_ContentPlaceHolder3_ScheduleTable", ::parseJmucy)
        "nuaajc" -> courseTableCandidates(Jsoup.parse(source), "#GrwCourseTablePrint", ::parseNuaajc)
        "qmu" -> firstSemester("table[frame=box]", ::parseQmu)
        "javtc" -> courseTableCandidates(Jsoup.parse(source), javtcSelector, ::parseJavtc)
        "hitsz" -> parseHitsz()
        else -> emptyList()
    }

    private fun text(raw: String) = Jsoup.parseBodyFragment(raw).text().trim()

    private fun firstSemester(selector: String, parse: (Element) -> List<JwCourse>): List<JwCourse> {
        for (fragment in source.split("<head>", "</head>")) {
            var hasCourses = false
            val result = courseTableCandidates(Jsoup.parse(fragment), selector) { table ->
                parse(table).also { if (it.isNotEmpty()) hasCourses = true }
            }
            // Conflicting layouts in this semester must not select a later semester.
            if (hasCourses) return result
        }
        return emptyList()
    }

    private fun Element.rows() = select("> tr, > thead > tr, > tbody > tr, > tfoot > tr")

    private fun Element.cells() = children().filter { it.tagName() == "td" }

    private fun Element.isMerged() = (hasAttr("rowspan") && attr("rowspan").toIntOrNull() != 1) ||
        (hasAttr("colspan") && attr("colspan").toIntOrNull() != 1)

    private fun parseJmucy(table: Element): List<JwCourse> = buildList {
        val named = table.id() == "ctl00_ContentPlaceHolder3_ScheduleTable"
        val rows = table.rows()
        if (!named && rows.drop(1).any { row -> row.cells().any { it.isMerged() } }) return@buildList
        for ((rowIndex, row) in rows.withIndex()) {
            val nodes = explicitSchedulePair(rowIndex) ?: continue
            val cells = row.cells()
            val label = cells.firstOrNull()?.text() ?: continue
            if (!named && label != "节次" && label != pairLabels.getOrNull(rowIndex - 1)) continue
            for ((day, cell) in cells.withIndex()) {
                if (day !in 1..7 || cell.selectFirst("table") != null) continue
                for (block in cell.text().split('★')) {
                    val fields = block.trim().split(' ')
                    if (fields.size < 3 || (!named && fields.size > 4)) continue
                    val room = if (fields.size > 3) fields[1] else ""
                    val teacher = fields[fields.lastIndex - 1]
                    val weeks = fields.last()
                    if (!named && '周' !in weeks) continue
                    for ((start, end, parity) in schoolWeekRanges(weeks)) add(JwCourse(
                        fields[0], room, teacher, day, nodes.first, nodes.last, start, end, parity,
                    ))
                }
            }
        }
    }

    private fun parseNuaajc(table: Element): List<JwCourse> {
        val rows = table.rows()
        val header = rows.singleOrNull { row ->
            row.children().any { it.tagName() == "th" && it.text() in nuaajcColumns }
        } ?: return emptyList()
        if (header.children().any { it.isMerged() }) return emptyList()
        val columns = mutableMapOf<String, Int>()
        for ((index, cell) in header.children().withIndex()) {
            if (cell.tagName() != "th" || cell.selectFirst("table") != null) continue
            val key = cell.text()
            if (key in nuaajcColumns && columns.put(key, index) != null) return emptyList()
        }
        if (!columns.keys.containsAll(listOf("kcm", "week", "unit", "weekly"))) return emptyList()
        val output = mutableListOf<JwCourse>()
        for (row in rows.drop(rows.indexOf(header) + 1)) {
            val cells = row.cells()
            if (cells.any { it.isMerged() }) return emptyList()
            if (cells.size != row.childrenSize()) continue
            if (cells.any { it.selectFirst("table") != null }) continue
            fun value(key: String): String = columns[key]?.let { cells.getOrNull(it)?.text()?.trim() }.orEmpty()
            val name = value("kcm")
            if (name.isEmpty()) continue
            val day = value("week").toIntOrNull()?.takeIf { it in 1..7 } ?: continue
            val node = value("unit").toIntOrNull() ?: continue
            val length = value("lsjs").ifEmpty { "1" }.toIntOrNull() ?: continue
            val last = explicitScheduleEnd(node, length) ?: continue
            for ((start, end, parity) in schoolWeekRanges(value("weekly"))) output += JwCourse(
                name, value("roomid"), value("jsm"), day, node, last, start, end, parity,
            )
        }
        return output
    }

    private fun parseQmu(table: Element): List<JwCourse> = buildList {
        var group = 0
        val named = table.attr("frame").equals("box", ignoreCase = true)
        for (row in table.rows()) {
            val cells = row.cells().filter { it.attr("valign").equals("top", ignoreCase = true) }
            if (cells.isEmpty()) continue
            if (!named && cells.any { it.isMerged() }) return emptyList()
            val nodes = explicitSchedulePair(++group) ?: break
            for ((dayIndex, cell) in cells.take(7).withIndex()) {
                if (cell.selectFirst("table") != null) continue
                val lines = cell.html().split("<br>").map(::text)
                for (index in 3 until lines.size) {
                    val timing = qmuTiming.matchEntire(lines[index]) ?: continue
                    if (lines[index - 3].isEmpty()) continue
                    if (!named && timing.groupValues[2] !in qmuParity) continue
                    val length = timing.groupValues[3].toIntOrNull()?.coerceAtMost(2) ?: continue
                    val last = explicitScheduleEnd(nodes.first, length) ?: continue
                    val parity = when { "单" in timing.groupValues[2] -> 1; "双" in timing.groupValues[2] -> 2; else -> 0 }
                    for ((start, end) in schoolWeekRanges(timing.groupValues[1].replace('.', ','))) add(JwCourse(
                        lines[index - 3], lines[index - 1], lines[index - 2], dayIndex + 1,
                        nodes.first, last, start, end, parity,
                    ))
                }
            }
        }
    }

    private fun parseJavtc(table: Element): List<JwCourse> {
        val output = mutableListOf<JwCourse>()
        val named = table.`is`(javtcSelector)
        for (row in table.rows().drop(1)) {
            val cells = row.cells()
            if (!named && cells.any { it.isMerged() }) return emptyList()
            if (cells.size < 8) continue
            val pair = pairLabels.indexOfFirst { it in cells[0].text() }
            if (pair < 0) continue
            for (day in 1..7) for (link in cells[day].select("a[title]")) {
                if (link.closest("table") != table) continue
                val fields = link.attr("title").split('\n').map(String::trim)
                if (fields.size < 8) continue
                if (!named &&
                    !(fields[2].startsWith("课程名称：") && fields[3].startsWith("授课教师：") &&
                        fields[5].startsWith("上课周次：") && fields[6].startsWith("开课地点："))) continue
                val name = fields[2].substringAfterLast("课程名称：")
                if (name.isBlank()) continue
                val teacher = fields[3].substringAfterLast("授课教师：")
                val room = fields[6].substringAfterLast("开课地点：")
                for ((start, end, parity) in schoolWeekRanges(fields[5].substringAfterLast("上课周次："))) output += JwCourse(
                    name, room, teacher, day, pair * 2 + 1, pair * 2 + 2, start, end, parity,
                )
            }
        }
        return output
    }

    private fun parseHitsz(): List<JwCourse> {
        // Each tbody is a complete layout. Give sibling bodies separate tables for candidate selection.
        val layouts = Element("div")
        for (body in Jsoup.parse(source).select("tbody")) {
            val table = layouts.appendElement("table")
            if (body.closest(".ivu-table-tbody") != null) table.addClass("ivu-table-tbody")
            table.appendChild(body)
        }
        return courseTableCandidates(layouts, "table.ivu-table-tbody", ::hitszTable)
    }

    private fun hitszTable(table: Element): List<JwCourse> {
        val output = mutableListOf<JwCourse>()
        val rows = table.rows()
        val namedBody = table.hasClass("ivu-table-tbody")
        if (!namedBody && rows.any { row -> row.cells().any { it.isMerged() } }) return emptyList()
        for (row in rows) for ((day, cell) in row.cells().withIndex()) {
            if (day !in 1..7 || cell.selectFirst("table") != null) continue
            for (card in cell.select(".ivu-card-body, div")) {
                val namedCard = card.hasClass("ivu-card-body")
                if (!namedCard && card.childrenSize() != 0) continue
                if (card.parent()?.closest(".ivu-card-body") != null) continue
                val fields = card.text().split(' ')
                if (fields.size < 3) continue
                val lab = fields[0].startsWith("【实验】")
                val timing = fields[fields.lastIndex - 1]
                val bracketValues = bracket.findAll(timing).map { it.groupValues[1] }.toList()
                if (bracketValues.size < 2) continue
                val nodeText = if (lab) bracketValues.first() else fields.last()
                val weekText = if (lab) bracketValues.last() else bracketValues.first()
                if (!namedBody || !namedCard) {
                    if (!hitszTiming.matches(timing) || '周' !in weekText ||
                        !nodeText.startsWith("第") || !nodeText.endsWith("节")) continue
                    if (lab && (fields.size != 3 || !bracket.matches(fields.last()))) continue
                    if (!lab && (fields.size != 4 || !bracket.matches(fields[1]))) continue
                }
                val nodes = schoolNodeRanges(nodeText.removePrefix("第").removeSuffix("节"))
                    .filter { it.second <= EXPLICIT_SCHEDULE_RESOURCE_BOUND }
                val room = if (lab) fields.last().removeSurrounding("[", "]") else bracketValues.last()
                val teacher = if (lab) "" else fields.getOrNull(fields.lastIndex - 2)?.removeSurrounding("[", "]").orEmpty()
                for ((start, end, parity) in schoolWeekRanges(weekText)) for ((first, last) in nodes) output += JwCourse(
                    fields[0], room, teacher, day, first, last, start, end, parity,
                )
            }
        }
        return output
    }

    private companion object {
        const val javtcSelector = "table[border=1][width=98%]"
        val pairLabels = listOf("第一二节", "第三四节", "第五六节", "第七八节")
        val nuaajcColumns = setOf("kcm", "week", "unit", "weekly", "lsjs", "roomid", "jsm")
        val qmuTiming = Regex("""([0-9.\-]+)周\s+([^ ]*)\s+([0-9]+)节""")
        val qmuParity = setOf("单周", "双周", "全周", "单", "双")
        val bracket = Regex("""\[([^]]*)]""")
        val hitszTiming = Regex("""\[[^\[\]]+]\[[^\[\]]*]""")
    }
}
