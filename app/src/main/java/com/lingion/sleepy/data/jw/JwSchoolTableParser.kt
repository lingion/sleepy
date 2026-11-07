package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

internal class JwSchoolTableParser(source: String, private val protocol: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> = when (protocol) {
        "hbut" -> hbut()
        "hnjm" -> hnjm()
        "lz" -> lz()
        "nau" -> nau()
        "nju" -> nju()
        "cqupt" -> cqupt()
        else -> emptyList()
    }.filter { it.name.isNotBlank() && it.day in 1..7 && it.startNode >= 1 && it.endNode >= it.startNode &&
        it.startWeek in 1..366 && it.endWeek in it.startWeek..366 && it.type in 0..2 &&
        (it.startWeek..it.endWeek).any { week -> it.type == 0 || week % 2 == it.type % 2 } }

    private fun hbut(): List<JwCourse> {
        val document = Jsoup.parse(source)
        val output = mutableListOf<JwCourse>()
        val table = document.selectFirst(".table-Schedule table")
        if (table != null) for ((rowIndex, row) in table.select("tr").withIndex()) {
            if (rowIndex == 0) continue
            for ((dayIndex, cell) in row.select("td").withIndex()) {
                for (part in cell.html().split(Regex("<br\\s*/?>\\s*<br\\s*/?>", RegexOption.IGNORE_CASE))) {
                    val fields = lines(part).filter { it.isNotEmpty() }
                    if (fields.size < 2) continue
                    val last = fields.last().split(Regex("\\s+"))
                    val weeks = last.filter { it.startsWith("第") && it.endsWith("周") }
                    val room = last.firstOrNull { !it.startsWith("第") || !it.endsWith("周") }.orEmpty()
                    for (week in weeks) for ((start, end, parity) in schoolWeekRanges(week, true)) output += JwCourse(
                        fields[0], room, if (fields.size > 2) fields[1] else "", dayIndex + 1,
                        (rowIndex - 1) * 2 + 1, (rowIndex - 1) * 2 + 2, start, end, parity)
                }
            }
        }
        for (row in document.getElementById("weekSchedule")?.select("tr").orEmpty()) {
            val cells = row.select("td")
            if (cells.size < 5) continue
            val dayRange = cells[2].text().trim().split('~', '～')
            if (dayRange.size !in 1..2 || dayRange.any { !Regex("(?:星期|周)[一二三四五六日天]").matches(it.trim()) }) continue
            val firstDay = day(dayRange.first())
            val lastDay = day(dayRange.last())
            if (firstDay !in 1..7 || lastDay !in firstDay..7) continue
            val nodes = schoolNodeRanges(cells[3].text().trim().removePrefix("第").removeSuffix("节").replace('~', '-').replace('～', '-'))
            val weeks = schoolWeekRanges(cells[1].text().trim().replace('~', '-').replace('～', '-'), true)
            for (weekday in firstDay..lastDay) for ((first, last) in nodes) for ((start, end, parity) in weeks) output += JwCourse(
                cells[0].text().substringBeforeLast(',').trim(), cells[4].text().substringAfter("地点:", "").trim(), "", weekday, first, last, start, end, parity)
        }
        return output
    }

    private fun hnjm(): List<JwCourse> {
        val document = Jsoup.parse(source)
        val tables = document.select("table")
        val output = mutableListOf<JwCourse>()
        var tableIndex = 0
        for (header in document.select(".Sub-kcbt")) {
            if (header.nextElementSibling()?.tagName() == "br") continue
            val weekday = day(header.text())
            val table = tables.getOrNull(tableIndex++) ?: continue
            if (weekday !in 1..7) continue
            for (row in table.select("tr")) {
                val cells = row.select("td")
                if (cells.size < 3) continue
                val nodes = schoolNodeRanges(cells[0].text().trim().removeSuffix("节"))
                if (nodes.any { it.second > 10 }) continue
                val raw = cells[1].text()
                val week = Regex("([0-9]+(?:-[0-9]+)?)周").find(raw) ?: continue
                val name = raw.substring(0, week.range.first).trim()
                val room = Regex("[（(]([图0-9][0-9]{1,3})[）)]").find(raw)?.groupValues?.get(1)
                    ?: Regex("；([^】]+)】").find(raw)?.groupValues?.get(1)?.trim().orEmpty()
                for ((first, last) in nodes) for ((start, end, parity) in schoolWeekRanges(week.groupValues[1])) output += JwCourse(
                    name, room, cells[2].text().trim(), weekday, first, last, start, end, parity,
                    hnjmPeriods[first - 1].first, hnjmPeriods[last - 1].second)
            }
        }
        return output
    }

    private fun lz(): List<JwCourse> {
        val table = Jsoup.parse(source).getElementById("TableLCRoomOccupy") ?: return emptyList()
        val output = mutableListOf<JwCourse>()
        for (row in table.select("tr")) for ((dayIndex, cell) in row.select(".PuTongCell").withIndex()) {
            val node = Regex("jieci([0-9]+)day.*").matchEntire(cell.id())?.groupValues?.get(1)?.toIntOrNull()
            if (node != null) {
                for (card in cell.select("div").filter { it.children().none { child -> child.tagName() == "div" } }) {
                    val fields = lines(card.html())
                    if (fields.size < 5) continue
                    val room = fields[2].substringAfter("教室:", "").substringBefore("(教室人数").trim()
                    val raw = fields[4].substringAfter("周次:", "").replace(Regex("\\[([单双])(?:周)?]"), "($1)")
                    for ((start, end, parity) in schoolWeekRanges(raw)) output += JwCourse(fields[0], room, fields[1], dayIndex + 1, node, node, start, end, parity)
                }
            } else {
                val fields = lines(cell.html())
                if (fields.size < 4) continue
                val timing = Regex("([^\\[]+)\\[([0-9,，\\-]+)节]").matchEntire(fields[3]) ?: continue
                val rawWeeks = timing.groupValues[1].replace(",单周", "(单)").replace(",双周", "(双)")
                for ((first, last) in schoolNodeRanges(timing.groupValues[2])) for ((start, end, parity) in schoolWeekRanges(rawWeeks))
                    output += JwCourse(fields[0], fields[2], fields[1], dayIndex + 1, first, last, start, end, parity)
            }
        }
        return output
    }

    private fun nau(): List<JwCourse> {
        val content = Jsoup.parse(source).getElementById("content") ?: return emptyList()
        val rows = content.select("tr")
        val headers = rows.firstOrNull()?.select("th")?.map { it.text().trim() } ?: return emptyList()
        fun column(text: String) = headers.indexOfFirst { text in it }
        val nameColumn = column("课程名称")
        val timingColumn = column("上课时间及地点")
        val creditColumn = column("学分")
        val teacherColumn = column("任课教师")
        if (listOf(nameColumn, timingColumn, creditColumn, teacherColumn).any { it < 0 }) return emptyList()
        val output = mutableListOf<JwCourse>()
        for (row in rows.drop(1)) {
            val cells = row.select("td")
            if (cells.size < 8 || cells.size <= maxOf(nameColumn, timingColumn, creditColumn, teacherColumn)) continue
            val credit = cells[creditColumn].text().toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f } ?: 0f
            for (part in cells[timingColumn].text().split("上课地点：").drop(1)) {
                val fields = part.split("上课时间：", limit = 2)
                if (fields.size != 2) continue
                val match = Regex("(\\S+)\\s+\\S+\\s+([1-7])\\s+\\S+\\s+([0-9,，\\-]+)节").matchEntire(fields[1].trim()) ?: continue
                val rawWeeks = match.groupValues[1].replace(Regex("之([单双])周"), "($1)")
                for ((first, last) in schoolNodeRanges(match.groupValues[3])) for ((start, end, parity) in schoolWeekRanges(rawWeeks))
                    output += JwCourse(cells[nameColumn].text().trim(), fields[0].trim(), cells[teacherColumn].text().trim(),
                        match.groupValues[2].toInt(), first, last, start, end, parity, credit = credit)
            }
        }
        return output
    }

    private fun nju(): List<JwCourse> {
        val rows = Jsoup.parse(source).select("#kcb_container .wut_table tbody tr").drop(1)
        val periods = mutableMapOf<Int, Pair<String, String>>()
        for (row in rows) {
            val label = row.selectFirst("td.mtt_bgcolor_grey[data-unit]") ?: continue
            val node = label.attr("data-unit").toIntOrNull() ?: continue
            val match = Regex("\\(([0-9]{2}:[0-9]{2})~([0-9]{2}:[0-9]{2})\\)").find(label.text()) ?: continue
            if (JwCourse.isValidTimeRange(match.groupValues[1], match.groupValues[2])) periods[node] = match.groupValues[1] to match.groupValues[2]
        }
        val output = mutableListOf<JwCourse>()
        for (row in rows) {
            val label = row.selectFirst("td.mtt_bgcolor_grey[data-unit]") ?: continue
            val node = label.attr("data-unit").toIntOrNull() ?: continue
            for (card in row.select("td[data-role=item] .mtt_arrange_item")) {
                val labelText = card.selectFirst(".mtt_item_kcmc")?.text()?.replace(Regex("^\\S+\\s+"), "")?.trim().orEmpty()
                val name = Regex("^(.+?)(?:\\s+[0-9]+班|[（(][0-9]+班[）)])$").matchEntire(labelText)?.groupValues?.get(1)
                    ?.trim() ?: labelText
                val text = card.selectFirst(".mtt_item_sksj")?.text().orEmpty()
                val weekday = Regex("周[一二三四五六日天]").find(text)?.value?.let { day(it) } ?: continue
                val nodeMatch = Regex("(?:第)?([0-9,，\\-]+)节").find(text)
                val explicitNodes = nodeMatch?.groupValues?.get(1)
                val nodes = if (explicitNodes == null) listOf(node to node) else schoolNodeRanges(explicitNodes)
                val weeksText = text.replace(Regex("周[一二三四五六日天]"), "").let { value ->
                    if (nodeMatch == null) value else value.replace(nodeMatch.value, "")
                }.trim()
                for ((first, last) in nodes) for ((start, end, parity) in schoolWeekRanges(weeksText)) {
                    val startTime = periods[first]?.first.orEmpty()
                    val endTime = periods[last]?.second.orEmpty()
                    val validTime = JwCourse.isValidTimeRange(startTime, endTime)
                    // Preserve source field mapping until authenticated page evidence disambiguates jxbmc.
                    output += JwCourse(name, card.selectFirst(".mtt_item_room")?.text()?.trim().orEmpty(), card.selectFirst(".mtt_item_jxbmc")?.text()?.trim().orEmpty(), weekday,
                        first, last, start, end, parity, if (validTime) startTime else "", if (validTime) endTime else "")
                }
            }
        }
        return output
    }

    private fun cqupt(): List<JwCourse> {
        val rows = Jsoup.parse(source).getElementById("stuPanel")?.select("tr[style=text-align:center]") ?: return emptyList()
        val starts = mapOf(0 to 1, 1 to 3, 3 to 5, 4 to 7, 6 to 9, 7 to 11)
        val output = mutableListOf<JwCourse>()
        for ((index, row) in rows.withIndex()) {
            val node = starts[index] ?: continue
            for ((weekday, cell) in row.select("td").withIndex()) {
                if (weekday !in 1..7) continue
                for (card in cell.select("div[zc]")) {
                    val mask = card.attr("zc")
                    if (mask.length !in 1..366 || mask.any { it !in "01" }) continue
                    val weeks = Regex("1+").findAll(mask).map { Triple(it.range.first + 1, it.range.last + 1, 0) }.toList()
                    val fields = card.html().split(Regex("(<[^>]+>)+")).map { Jsoup.parse(it).text().trim() }.filter { it.isNotBlank() }
                    if (fields.size < 2) continue
                    val name = fields[0].substringAfter('-', fields[0]).trim()
                    val room = fields[1].substringAfter("地点：", "").trim()
                    val creditField = fields.lastOrNull { "学分" in it }
                    val teacher = creditField?.substringBefore(' ')?.trim().orEmpty()
                    val credit = creditField?.substringAfterLast(' ')?.substringBefore("学分")?.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f } ?: 0f
                    val durationText = fields.firstNotNullOfOrNull { Regex("([0-9]+)节连上").matchEntire(it)?.groupValues?.get(1) }
                    val duration = if (durationText == null) 2 else durationText.toIntOrNull() ?: continue
                    if (duration < 1 || node.toLong() + duration - 1 > Int.MAX_VALUE) continue
                    for ((start, end, parity) in weeks) output += JwCourse(name, room, teacher, weekday, node, node + duration - 1, start, end, parity, credit = credit)
                }
            }
        }
        return output
    }

    private fun lines(html: String): List<String> = html.split(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)).map { Jsoup.parse(it).text().trim() }
    private fun day(value: String): Int {
        val token = Regex("(?:星期|周)([一二三四五六日天])").find(value)?.groupValues?.get(1)
            ?: value.trim().takeIf { it.length == 1 && it[0] in "一二三四五六日天" } ?: return 0
        return "一二三四五六日".indexOf(token.replace('天', '日')) + 1
    }

    private companion object {
        val hnjmPeriods = listOf("08:10" to "08:55", "09:05" to "09:50", "10:15" to "11:00", "11:10" to "11:55",
            "14:40" to "15:25", "15:35" to "16:20", "16:40" to "17:25", "17:35" to "18:20", "19:00" to "19:45", "19:55" to "20:40")
    }
}
