package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal class JwUndergradBGridParser(source: String, private val protocol: String) : JwParser(source) {
    private val doc by lazy { Jsoup.parse(source).also { it.outputSettings().prettyPrint(false) } }
    override fun generateCourseList(): List<JwCourse> = (when (protocol) {
        "sicnu" -> sicnu()
        "nuist" -> nuist()
        "sit" -> sit()
        "sysu" -> sysu()
        else -> emptyList()
    }).filter(JwUndergradBHtmlParser::valid)

    private fun text(raw: String) = Jsoup.parseBodyFragment(raw).text().trim()
    private fun lines(raw: String) = raw.split(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)).map(::text)

    private fun sicnu(): List<JwCourse> {
        val table = doc.selectFirst(".PintTableStyle") ?: return emptyList()
        val occupied = IntArray(7)
        val output = mutableListOf<JwCourse>()
        var node = 1
        for (row in table.select("tr")) {
            val cells = row.children().filter { it.tagName() == "td" }
            // The school inserts period separators with colspan; they consume no node.
            if (cells.any { it.hasAttr("colspan") }) continue
            var column = 0
            for (cell in cells) {
                if (cell.hasAttr("width")) continue
                while (column < 7 && occupied[column] >= node) column++
                if (column >= 7) break
                val span = cell.attr("rowspan").ifBlank { "1" }.toIntOrNull() ?: break
                val lastNode = JwUndergradBHtmlParser.endNode(node, span) ?: break
                val day = column + 1
                occupied[column] = lastNode
                column++
                val raw = cell.selectFirst("span")?.html() ?: cell.html()
                var name = ""
                var parity = 0
                for (piece in raw.split(';')) {
                    val parts = lines(piece.replace(Regex("\\[\\d+]"), "")).dropWhile(String::isBlank)
                    if (parts.isEmpty()) continue
                    val teacher: String
                    val detail: String
                    when (parts.size) {
                        1 -> {
                            if (name.isBlank() || '[' !in parts[0]) continue
                            teacher = parts[0].substringBefore('[')
                            detail = parts[0].substringAfter('[')
                        }
                        2 -> {
                            name = parts[0]
                            parity = when { name.endsWith("**") -> 2; name.endsWith("*") -> 1; else -> 0 }
                            name = name.removeSuffix("**").removeSuffix("*")
                            if ('[' !in parts[1]) continue
                            teacher = parts[1].substringBefore('[')
                            detail = parts[1].substringAfter('[')
                        }
                        else -> {
                            name = parts[0]
                            parity = when { name.endsWith("**") -> 2; name.endsWith("*") -> 1; else -> 0 }
                            name = name.removeSuffix("**").removeSuffix("*")
                            teacher = parts[1]
                            detail = parts[2]
                        }
                    }
                    if ('(' !in detail) continue
                    val room = detail.substringBefore('(')
                    val weekText = detail.substringAfter('(').substringBefore('周').substringBefore(')')
                    for ((first, last, _) in JwUndergradBHtmlParser.weeks(weekText)) output += JwCourse(name, room, teacher,
                        day, node, lastNode, first, last, parity)
                }
            }
            node++
        }
        return output
    }

    private fun nuist(): List<JwCourse> {
        val rows = doc.getElementById("TABLE1")?.select("tr") ?: return emptyList()
        val leading = rows.firstOrNull()?.select("td")?.count { !it.text().trim().startsWith("星期") } ?: return emptyList()
        val output = mutableListOf<JwCourse>()
        var doubleRow = false
        for ((rowIndex, row) in rows.withIndex()) {
            if (rowIndex == 0) continue
            for ((column, cell) in row.select("td").withIndex()) {
                if (column < leading) {
                    if ('～' in cell.text()) doubleRow = true
                    continue
                }
                val day = column - leading + 1
                for (piece in cell.html().split(Regex("◆|(?:<br\\s*/?>)+", RegexOption.IGNORE_CASE))) {
                    val fields = text(piece).split('◇').map(String::trim)
                    val name = fields.firstOrNull().orEmpty()
                    if (name.isBlank() || fields.size < 2) continue
                    fun field(i: Int) = fields.getOrNull(i).orEmpty()
                    fun parenthesized(value: String) = value.substringAfter('(', "").substringBefore(')').trim()
                    val teacher = field(1).substringBefore('(').trim()
                    val firstWeeks = parenthesized(field(1))
                    val metadataWeeks = if (Regex("^\\d.*\\d$").matches(firstWeeks)) firstWeeks else parenthesized(field(3))
                    val room = field(4).substringAfter('}', "").trim().ifBlank { field(2).substringAfter('}', "").trim() }
                    for (timing in fields.last().split(',')) {
                        if (timing.isBlank()) continue
                        val encoded = timing.substringAfter('{', "").substringBefore('}').removeSuffix("节")
                        val packed = encoded.takeIf { Regex("[0-9]{1,4}").matches(it) }
                        val nodes = when (packed?.length) {
                            1, 2 -> (packed.first().digitToInt() to packed.last().digitToInt())
                            3 -> (packed.first().digitToInt() to packed.substring(1).toInt())
                            4 -> (packed.substring(0, 2).toInt() to packed.substring(2).toInt())
                            else -> if (doubleRow) (rowIndex * 2 - 1 to rowIndex * 2) else (rowIndex to rowIndex)
                        }
                        val rawWeeks = if (Regex("^\\d.*\\d$").matches(metadataWeeks)) metadataWeeks else timing.substringBefore('{')
                        val parity = when { "单" in timing -> 1; "双" in timing -> 2; else -> 0 }
                        val weekRanges = JwUndergradBHtmlParser.weeks(rawWeeks.replace("单", "").replace("双", ""))
                        for ((first, last, _) in weekRanges) output += JwCourse(name, room, teacher, day,
                            nodes.first, nodes.second, first, last, parity)
                    }
                }
            }
        }
        return output
    }

    private fun sit(): List<JwCourse> {
        val body = Jsoup.parse(source.substringAfterLast("<body>")).also { it.outputSettings().prettyPrint(false) }
        val table = body.selectFirst("form")?.selectFirst("table") ?: return emptyList()
        val output = mutableListOf<JwCourse>()
        val occupied = IntArray(7)
        for ((rowIndex, row) in table.select("tr").withIndex()) {
            if (rowIndex < 2) continue
            val cells = row.children().filter { it.tagName() == "td" }
            val node = cells.firstOrNull()?.text()?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: continue
            var column = 0
            for (cell in cells.drop(1)) {
                while (column < 7 && occupied[column] >= node) column++
                if (column >= 7) break
                val day = column + 1
                val span = cell.attr("rowspan").ifBlank { "1" }.toIntOrNull() ?: break
                val lastNode = JwUndergradBHtmlParser.endNode(node, span) ?: break
                occupied[column] = lastNode
                column++
                for (entry in cell.select("[name=d1]")) {
                    val fields = entry.text().split(' ').filter(String::isNotBlank)
                    if (fields.size < 5) continue
                    val raw = fields[1]
                    val parity = when { "**" in raw -> 2; "*" in raw -> 1; else -> 0 }
                    val stripped = raw.replace("*", "")
                    if (stripped.firstOrNull() !in listOf('第', '(', '（', '[')) continue
                    val weekText = stripped.drop(1).removeSuffix("周")
                    for ((first, last, _) in JwUndergradBHtmlParser.weeks(weekText)) output += JwCourse(fields[0], fields[2], fields[4],
                        day, node, lastNode, first, last, parity)
                }
            }
        }
        return output
    }

    private fun sysu(): List<JwCourse> {
        val tables = doc.select(".com-table.class-schedule-table2")
        if (tables.size < 2) return emptyList()
        val headers = tables[0].select("tr").firstOrNull()?.select("th") ?: return emptyList()
        val sunday = headers.indexOfFirst { "星期日" in it.text() }
        val monday = headers.indexOfFirst { "星期一" in it.text() }
        val sundayFirst = sunday >= 0 && monday >= 0 && sunday < monday
        val widths = headers.filter { it.className() != "th2" }.map {
            it.attr("colspan").ifBlank { "1" }.toIntOrNull()?.takeIf { n -> n > 0 } ?: return emptyList()
        }
        if (widths.size !in 1..7) return emptyList()
        val boundaries = widths.runningFold(0L) { total, width -> total + width }
        data class Occupied(val first: Long, val last: Long, val untilNode: Int)
        val occupied = mutableListOf<Occupied>()
        val output = mutableListOf<JwCourse>()
        val rows = tables[1].select("tbody").firstOrNull()?.select("tr") ?: return emptyList()
        for ((rowIndex, row) in rows.withIndex()) {
            val node = rowIndex + 1
            occupied.removeAll { it.untilNode < node }
            var offset = 0L
            for (cell in row.select("td")) {
                if (cell.className() == "td2-1") continue
                while (true) {
                    val previous = occupied.firstOrNull { offset >= it.first && offset < it.last } ?: break
                    offset = previous.last
                }
                val colspan = cell.attr("colspan").ifBlank { "1" }.toIntOrNull()?.takeIf { it > 0 } ?: break
                val dayIndex = widths.indices.firstOrNull { offset in boundaries[it] until boundaries[it + 1] }
                val last = offset + colspan
                if (dayIndex == null || last > boundaries[dayIndex + 1] || occupied.any { offset < it.last && last > it.first }) break
                val span = cell.attr("rowspan").ifBlank { "1" }.toIntOrNull() ?: break
                val lastNode = JwUndergradBHtmlParser.endNode(node, span) ?: break
                occupied += Occupied(offset, last, lastNode)
                offset = last
                if (cell.className() == "11111" || cell.text().isBlank()) continue
                val parts = cell.select("span").map { it.text().trim().replace("/", "") }
                if (parts.size < 4) continue
                val name = parts[1].split(Regex("[()]" )).getOrNull(2) ?: continue
                val day = if (sundayFirst) if (dayIndex == 0) 7 else dayIndex else dayIndex + 1
                val parity = when { "单周" in parts[0] -> 1; "双周" in parts[0] -> 2; else -> 0 }
                val rawWeeks = parts[0].replace("每周", "").replace("单周", "").replace("双周", "")
                for ((first, end, _) in JwUndergradBHtmlParser.weeks(rawWeeks)) output += JwCourse(name, parts[3], parts[2], day,
                    node, lastNode, first, end, parity)
            }
        }
        return output
    }
}
