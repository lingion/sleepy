package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

internal class JwUndergradHtmlParser(source: String, private val protocol: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> = (when (protocol) {
        "jmucy" -> parseJmucy()
        "nuaajc" -> parseNuaajc()
        "qmu" -> parseQmu()
        "javtc" -> parseJavtc()
        "hitsz" -> parseHitsz()
        else -> emptyList()
    }).filter { course ->
        course.name.isNotBlank() && course.day in 1..7 && course.startNode in 1..EXPLICIT_SCHEDULE_RESOURCE_BOUND &&
            course.endNode in course.startNode..EXPLICIT_SCHEDULE_RESOURCE_BOUND && course.startWeek in 1..366 &&
            course.endWeek in course.startWeek..366 && course.type in 0..2
    }

    private fun text(raw: String) = Jsoup.parseBodyFragment(raw).text().trim()

    private fun fragments(): List<String> = source.split("<head>", "</head>")

    private fun parseJmucy(): List<JwCourse> {
        for (fragment in fragments()) {
            val table = Jsoup.parse(fragment).getElementById("ctl00_ContentPlaceHolder3_ScheduleTable") ?: continue
            val output = mutableListOf<JwCourse>()
            for ((rowIndex, row) in table.select("tr").withIndex()) {
                val nodes = explicitSchedulePair(rowIndex) ?: continue
                for ((day, cell) in row.select("td").withIndex()) {
                    if (day !in 1..7) continue
                    for (block in cell.text().split('★')) {
                        val fields = block.trim().split(' ')
                        if (fields.size < 3) continue
                        val room = if (fields.size > 3) fields[1] else ""
                        val teacher = fields[fields.lastIndex - 1]
                        val weeks = fields.last()
                        for ((start, end, parity) in schoolWeekRanges(weeks)) output += JwCourse(
                            fields[0], room, teacher, day, nodes.first, nodes.last, start, end, parity,
                        )
                    }
                }
            }
            if (output.isNotEmpty()) return output
        }
        return emptyList()
    }

    private fun parseNuaajc(): List<JwCourse> {
        val table = Jsoup.parse(source).getElementById("GrwCourseTablePrint") ?: return emptyList()
        val columns = table.select("th").mapIndexed { index, header -> header.text().trim() to index }.toMap()
        val output = mutableListOf<JwCourse>()
        for (row in table.select("tr").drop(1)) {
            val cells = row.select("td")
            fun value(key: String): String = columns[key]?.let { cells.getOrNull(it)?.text()?.trim() }.orEmpty()
            val name = value("kcm")
            val day = value("week").toIntOrNull() ?: continue
            val node = value("unit").toIntOrNull() ?: continue
            val length = value("lsjs").toIntOrNull() ?: 1
            val last = explicitScheduleEnd(node, length) ?: continue
            for ((start, end, parity) in schoolWeekRanges(value("weekly"))) output += JwCourse(
                name, value("roomid"), value("jsm"), day, node, last, start, end, parity,
            )
        }
        return output
    }

    private fun parseQmu(): List<JwCourse> {
        for (fragment in fragments()) {
            val table = Jsoup.parse(fragment).selectFirst("table[frame=box]") ?: continue
            val output = mutableListOf<JwCourse>()
            var rowIndex = 0
            for (row in table.select("tr")) {
                val cells = row.select("td[valign=top]")
                if (cells.isEmpty()) continue
                val nodes = explicitSchedulePair(rowIndex + 1) ?: break
                for ((dayIndex, cell) in cells.withIndex()) {
                    val lines = cell.html().split("<br>").map(::text)
                    for ((index, line) in lines.withIndex()) {
                        if (index < 3) continue
                        val timing = Regex("^([0-9.\\-]+)周\\s+([^ ]*)\\s+([0-9]+)节$").matchEntire(line) ?: continue
                        val length = timing.groupValues[3].toIntOrNull()?.coerceAtMost(2) ?: continue
                        val last = explicitScheduleEnd(nodes.first, length) ?: continue
                        val parity = when { "单" in timing.groupValues[2] -> 1; "双" in timing.groupValues[2] -> 2; else -> 0 }
                        for ((start, end) in schoolWeekRanges(timing.groupValues[1].replace('.', ','))) output += JwCourse(
                            lines[index - 3], lines[index - 1], lines[index - 2], dayIndex + 1,
                            nodes.first, last, start, end, parity,
                        )
                    }
                }
                rowIndex++
            }
            if (output.isNotEmpty()) return output
        }
        return emptyList()
    }

    private fun parseJavtc(): List<JwCourse> {
        val table = Jsoup.parse(source).selectFirst("table[border=1][width=98%]") ?: return emptyList()
        val output = mutableListOf<JwCourse>()
        val labels = listOf("第一二节", "第三四节", "第五六节", "第七八节")
        for (row in table.select("tr").drop(1)) {
            val cells = row.select("td")
            if (cells.size < 8) continue
            val pair = labels.indexOfFirst { it in cells[0].text() }
            if (pair < 0) continue
            for (day in 1..7) for (link in cells[day].select("a[title]")) {
                val fields = link.attr("title").split('\n').map(String::trim)
                if (fields.size < 8) continue
                val name = fields[2].substringAfterLast("课程名称：")
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
        val table = Jsoup.parse(source).selectFirst(".ivu-table-tbody") ?: return emptyList()
        val output = mutableListOf<JwCourse>()
        for (row in table.select("tr")) for ((day, cell) in row.select("td").withIndex()) {
            if (day !in 1..7) continue
            for (card in cell.select(".ivu-card-body")) {
                val fields = card.text().split(' ')
                if (fields.size < 3) continue
                val lab = fields[0].startsWith("【实验】")
                val timing = fields[fields.lastIndex - 1]
                val bracketValues = Regex("\\[([^]]*)]").findAll(timing).map { it.groupValues[1] }.toList()
                if (bracketValues.size < 2) continue
                val nodes = if (lab) schoolNodeRanges(bracketValues.first().removePrefix("第").removeSuffix("节"))
                    else schoolNodeRanges(fields.last().removePrefix("第").removeSuffix("节"))
                val weekText = if (lab) bracketValues.last() else bracketValues.first()
                val room = if (lab) fields.last().removeSurrounding("[", "]") else bracketValues.last()
                val teacher = if (lab) "" else fields.getOrNull(fields.lastIndex - 2)?.removeSurrounding("[", "]").orEmpty()
                for ((start, end, parity) in schoolWeekRanges(weekText)) for ((first, last) in nodes) output += JwCourse(
                    fields[0], room, teacher, day, first, last, start, end, parity,
                )
            }
        }
        return output
    }
}
