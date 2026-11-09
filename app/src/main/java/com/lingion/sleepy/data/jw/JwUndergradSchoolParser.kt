package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal class JwUndergradSchoolParser(source: String, private val protocol: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> = (when (protocol) {
        "hniu" -> parseHniu()
        "sustech" -> parseSustech()
        "uic" -> parseUic()
        "cityu" -> parseCityu()
        "stu" -> parseStu()
        "nfu" -> parseNfu()
        else -> emptyList()
    }).filter { course ->
        course.name.isNotBlank() && course.day in 1..7 && course.startNode in 1..EXPLICIT_SCHEDULE_RESOURCE_BOUND &&
            course.endNode in course.startNode..EXPLICIT_SCHEDULE_RESOURCE_BOUND && course.startWeek in 1..366 &&
            course.endWeek in course.startWeek..366 && course.type in 0..2
    }

    private fun document() = Jsoup.parse(source).also { it.outputSettings().prettyPrint(false) }

    private fun lines(element: Element): List<String> = element.html().split(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE))
        .map { Jsoup.parseBodyFragment(it).text().trim() }

    private fun range(value: String): Pair<Int, Int>? {
        if (!Regex("[0-9]+(?:\\s*-\\s*[0-9]+)?").matches(value.trim())) return null
        val parts = value.trim().split('-')
        val start = parts.firstOrNull()?.trim()?.toIntOrNull() ?: return null
        val end = parts.lastOrNull()?.trim()?.toIntOrNull() ?: return null
        return (start to end).takeIf { start in 1..366 && end in start..366 }
    }

    private fun parity(value: String): Int = when {
        "单" in value -> 1
        "双" in value -> 2
        else -> 0
    }

    private fun parseHniu(): List<JwCourse> {
        val table = document().selectFirst("[bordercolordark=#FFFFFF]") ?: return emptyList()
        val output = mutableListOf<JwCourse>()
        for (row in table.select("tbody").firstOrNull()?.select("tr").orEmpty()) {
            var day = 0
            for (cell in row.select("td")) {
                if (cell.attr("align") == "center" || cell.attr("valign") != "top") continue
                day++
                val parts = lines(cell)
                for (index in 1 until parts.size) {
                    val scheduling = Regex("\\[([^]]+)周]\\[([^]]+)节]").find(parts[index]) ?: continue
                    val nodes = range(scheduling.groupValues[2]) ?: continue
                    val name = parts[index - 1].substringBefore(' ')
                    val teacher = parts[index].substringBefore(' ')
                    val room = parts.getOrNull(index + 1)?.takeIf { it.isNotBlank() }
                        ?: parts[index].substringAfterLast(' ')
                    for (week in scheduling.groupValues[1].split(',')) {
                        val weeks = range(week) ?: continue
                        output += JwCourse(name, room, teacher, day, nodes.first, nodes.second, weeks.first, weeks.second)
                    }
                }
            }
        }
        return output
    }

    private fun parseSustech(): List<JwCourse> {
        val root = runCatching { JSONArray(source) }.getOrNull() ?: return emptyList()
        val output = mutableListOf<JwCourse>()
        for (index in 0 until root.length()) {
            val item = root.optJSONObject(index) ?: continue
            val key = item.optString("KEY")
            if (key == "bz") continue
            val day = Regex("xq(\\d+)_jc\\d+").find(key)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val text = item.optString("SKSJ")
            val fields = Regex("\\[(.*?)]").findAll(text).map { it.groupValues[1] }.toList()
            if (fields.size != 5) continue
            val nodes = range(fields[4].replace(Regex("[第节]"), "")) ?: continue
            val note = fields[1].takeIf { '-' in it }?.substringAfter('-').orEmpty()
            for (week in fields[2].replace("周", "").split(',')) {
                val weeks = range(week.replace(Regex("[单双]"), "")) ?: continue
                output += JwCourse(
                    text.substringBefore('[').trim(), fields[3], fields[0], day,
                    nodes.first, nodes.second, weeks.first, weeks.second, parity(week), note = note,
                )
            }
        }
        return output
    }

    private fun parseUic(): List<JwCourse> {
        val doc = document()
        val teachers = doc.selectFirst(".tablestyle-2")?.select("tbody tr").orEmpty().mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size < 4) null else cells[2].text().trim() to cells[3].text().trim()
        }.toMap()
        return doc.getElementById("mytimetable")?.select("tr").orEmpty().flatMapIndexed { node, row ->
            if (node == 0) emptyList() else row.select("td").mapIndexedNotNull { column, cell ->
                val parts = lines(cell)
                if (cell.text().isBlank() || parts.size < 2) null else JwCourse(
                    parts[0], parts[1], teachers[parts[0]].orEmpty(), column + 1, node, node, 1, 20,
                )
            }
        }
    }

    private fun parseCityu(): List<JwCourse> {
        val rows = document().selectFirst(".ctt-matrix")?.select("tr") ?: return emptyList()
        val occupied = IntArray(7)
        val output = mutableListOf<JwCourse>()
        for ((node, row) in rows.withIndex()) {
            if (node !in 1..EXPLICIT_SCHEDULE_RESOURCE_BOUND) continue
            var column = 0
            for (cell in row.select("td")) {
                if (cell.className().lowercase() == "ctt-matrix-td-time") continue
                while (column < occupied.size && occupied[column] >= node) column++
                if (column >= occupied.size) break
                val span = cell.attr("rowspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
                val last = explicitScheduleEnd(node, span)
                // An oversized span still occupies its column throughout the supported rows.
                occupied[column] = last ?: EXPLICIT_SCHEDULE_RESOURCE_BOUND
                if (last == null) {
                    column++
                    continue
                }
                val parts = lines(cell)
                if (cell.text().isNotBlank() && parts.size >= 3) {
                    output += JwCourse(parts[1], parts[2], "", column + 1, node, last, 1, 20)
                    column++
                }
            }
        }
        return output
    }

    private fun parseStu(): List<JwCourse> {
        for (fragment in source.split("<head>", "</head>")) {
            val output = mutableListOf<JwCourse>()
            val doc = Jsoup.parse(fragment)
            for (row in doc.getElementById("DataGrid1")?.select("tr").orEmpty()) {
                if (row.className() in setOf("DGHeaderStyle", "DGFooterStyle")) continue
                val cells = row.select("td")
                if (cells.size < 7) continue
                val weeks = range(cells[5].text()) ?: continue
                for (column in 6 until cells.size) {
                    val raw = cells[column].text().trim()
                    val nodes = raw.replace("单", "").replace("双", "")
                    if (nodes.isEmpty() || ("单" in raw && "双" in raw)) continue
                    fun node(value: Char): Int = if (value.isLetter()) value - '6' else value - '0'
                    val advertised = if ('-' in nodes) {
                        if (!Regex("[0-9A-Z]-[0-9A-Z]").matches(nodes)) continue
                        val start = node(nodes.first())
                        val end = node(nodes.last())
                        if (start !in 1..EXPLICIT_SCHEDULE_RESOURCE_BOUND || end !in start..EXPLICIT_SCHEDULE_RESOURCE_BOUND) continue
                        (start..end).toList()
                    } else {
                        if (!Regex("[0-9A-Z]+").matches(nodes)) continue
                        nodes.map(::node).takeIf { values -> values.all { it in 1..EXPLICIT_SCHEDULE_RESOURCE_BOUND } } ?: continue
                    }
                    val runs = mutableListOf<Pair<Int, Int>>()
                    for (section in advertised.distinct().sorted()) {
                        val last = runs.lastOrNull()
                        if (last != null && section == last.second + 1) runs[runs.lastIndex] = last.first to section
                        else runs += section to section
                    }
                    val credit = cells[2].text().trim().toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f } ?: 0f
                    for (sections in runs) output += JwCourse(
                        cells[1].text().substringAfter(']').trim(), cells[4].text().trim(), cells[3].text().trim(),
                        if (column == 6) 7 else column - 6, sections.first, sections.second,
                        weeks.first, weeks.second, parity(raw), credit = credit,
                    )
                }
            }
            if (output.isNotEmpty()) return output
        }
        return emptyList()
    }

    private fun parseNfu(): List<JwCourse> {
        val output = mutableListOf<JwCourse>()
        for (row in document().select("tr.ui-widget-content")) {
            val spans = row.select("span")
            if (spans.size < 10) continue
            for (column in 3..9) {
                for (raw in spans[column].text().replace("/ ", "/").split('/')) {
                    val parts = raw.replace(" 单周", "").replace(" 双周", "").split(' ')
                    if (parts.size !in 3..4 || ("单" in raw && "双" in raw)) continue
                    val weeks = range(parts[0].removePrefix("第").removeSuffix("周")) ?: continue
                    val nodes = range(parts[1].removePrefix("第").removeSuffix("节")) ?: continue
                    output += JwCourse(
                        spans[0].text(), parts.getOrNull(3).orEmpty(), parts[2], column - 2,
                        nodes.first, nodes.second, weeks.first, weeks.second, parity(raw),
                    )
                }
            }
        }
        return output
    }
}
