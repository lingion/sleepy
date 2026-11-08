package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/** Kingosoft positive-selection report and legacy weekday grid. */
class JwKingoZxParser(source: String) : JwParser(source) {
    private val doc by lazy { Jsoup.parse(source) }
    private val weekToken = Regex("^(\\d+)(?:-(\\d+))?\\s*(单|双)?$")
    private val sectionToken = Regex("^(?:\\[\\s*)?(?:第)?([0-9]+(?:\\s*[-,]\\s*[0-9]+)*)节\\s*]?")
    private val bracketSection = Regex("\\[(\\d+)(?:-(\\d+))?节?]")
    private val dayToken = Regex("(?:星期|周)([一二三四五六日天])")
    private val dayNumbers = mapOf('一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '日' to 7, '天' to 7)

    override fun generateCourseList(): List<JwCourse> {
        for ((selector, timingColumn) in listOf("#pageRpt" to 10, "#reportArea" to 12)) {
            val outer = doc.selectFirst(selector) ?: continue
            val reports = outer.select("table").flatMap { parseReport(it, timingColumn) }.distinct()
            if (reports.isNotEmpty()) return reports
        }
        if (doc.selectFirst("table#mytable") != null) {
            return WakeUpCompat.parseKingoSuperHtml(source)
        }
        val grid = doc.selectFirst("#xkinfo table")
            ?: doc.selectFirst("#reportArea table")
        return grid?.let(::parseGrid).orEmpty()
    }

    override fun confidence(): Int = when {
        doc.selectFirst("#pageRpt table, #reportArea table") != null -> 90
        doc.selectFirst("table#mytable td.td, #xkinfo table td.td, #reportArea table td.td") != null -> 85
        else -> 0
    }

    override fun matchedFeatures(): List<String> = buildList {
        if (doc.selectFirst("#pageRpt table") != null) add("pageRpt-report")
        if (doc.selectFirst("#reportArea table") != null) add("reportArea-report")
        if (doc.selectFirst("table#mytable td.td") != null) add("mytable-grid")
        if (doc.selectFirst("#xkinfo table td.td") != null) add("xkinfo-grid")
        if (doc.selectFirst("#reportArea table td.td") != null) add("reportArea-grid")
    }

    private fun parseReport(table: Element, timingColumn: Int): List<JwCourse> = buildList {
        for (row in table.select("tr").filter { it.parents().firstOrNull { parent -> parent.tagName() == "table" } === table }) {
            val cells = row.children().filter { it.normalName() == "td" }
            if (cells.size <= timingColumn) continue
            val name = cells[1].text().substringAfter(']', "").trim()
            if (name.isBlank()) continue
            val teachers = cells[4].select("a")
            val teacher = if (teachers.isEmpty()) cells[4].text().substringBefore('[').trim()
                else teachers.joinToString(" & ") { it.text().substringBefore('[').trim() }
            val credit = cells[2].text().trim().toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f } ?: 0f
            val schedule = cells[timingColumn].clone()
            schedule.select("br").forEach { it.replaceWith(TextNode("\n")) }
            for (line in schedule.wholeText().lines().map(String::trim).filter(String::isNotBlank)) {
                val timing = line.substringBefore('/').substringBefore(' ').trim()
                val dayMatch = dayToken.find(timing) ?: Regex("^([一二三四五六日天])(?=\\s)").find(timing) ?: continue
                val day = dayMatch.groupValues[1].firstOrNull()?.let(dayNumbers::get) ?: continue
                val afterDay = timing.substring(dayMatch.range.last + 1).trim()
                val section = sectionToken.find(afterDay) ?: continue
                val sections = schoolNodeRanges(section.groupValues[1])
                if (sections.isEmpty() || sections.any { it.first !in 1..30 || it.second !in it.first..30 }) continue
                // Source code supports both [weeks] day [sections] and day sections weeks.
                val beforeDay = timing.substring(0, dayMatch.range.first).trim('[', ']', ' ')
                val weekSpec = if (beforeDay.any(Char::isDigit)) beforeDay
                    else afterDay.substring(section.range.last + 1).trim('[', ']', ' ')
                val room = (if ('/' in line) line.substringAfter('/') else line.substringAfter(' ', ""))
                    .trim().ifBlank { if (name.endsWith('m')) "在线" else "" }
                for ((first, last, type) in weekRuns(weekSpec, singletonIsExplicit = dayMatch.value.length == 1)) {
                    for ((start, end) in sections) add(JwCourse(name, room, teacher, day, start, end, first, last, type, credit = credit))
                }
            }
        }
    }

    private fun parseGrid(table: Element): List<JwCourse> = buildList {
        for (row in table.select("tr")) {
            val columns = row.children().filter { it.normalName() == "td" }
            if (columns.size < 2) continue
            val firstDayIndex = if (!columns.first().hasClass("td") &&
                (columns.first().text().trim().startsWith("第") ||
                    columns.first().text().trim() == "节次" ||
                    columns.first().text().trim() == "时间")) 1 else 0
            for ((index, cell) in columns.drop(firstDayIndex).withIndex()) {
                val day = index + 1
                if (day > 7) break
                if (!cell.hasClass("td")) continue
                for (block in cell.children().filter { it.normalName() == "div" }) {
                    val copy = block.clone()
                    copy.select("br").forEach { it.replaceWith(TextNode("\n")) }
                    val parts = copy.wholeText().lines().map(String::trim).filter(String::isNotBlank)
                    if (parts.size != 4) continue
                    val section = bracketSection.find(parts[2])
                    val firstSection = if (parts[2].substringAfter('[', "").startsWith("傍晚")) 9
                        else section?.groupValues?.get(1)?.toIntOrNull() ?: continue
                    val lastSection = section?.groupValues?.get(2)?.toIntOrNull() ?: firstSection
                    if (firstSection !in 1..30 || lastSection !in firstSection..30) continue
                    for ((first, last, type) in weekRuns(parts[2].substringBefore('['))) {
                        add(JwCourse(parts[0], parts[3], parts[1], day, firstSection, lastSection, first, last, type))
                    }
                }
            }
        }
    }.distinct()

    private fun weekRuns(spec: String, singletonIsExplicit: Boolean = false): List<Triple<Int, Int, Int>> {
        val weeks = sortedSetOf<Int>()
        var cleanSpec = spec.replace("周", "").replace('，', ',').filterNot(Char::isWhitespace)
            .replace("[", "").replace("]", "")
        val globalParity = if (cleanSpec.count { it == '单' || it == '双' } == 1) {
            val marker = cleanSpec.firstOrNull()?.takeIf { it == '单' || it == '双' }
                ?: cleanSpec.lastOrNull()?.takeIf { it == '单' || it == '双' }
            if (marker != null) cleanSpec = cleanSpec.replace(marker.toString(), "")
            marker?.toString().orEmpty()
        } else ""
        for (part in cleanSpec.split(',')) {
            val match = weekToken.matchEntire(part) ?: return emptyList()
            val first = match.groupValues[1].toIntOrNull() ?: return emptyList()
            val last = match.groupValues[2].takeIf(String::isNotBlank)?.toIntOrNull()
                ?: if (match.groupValues[2].isBlank()) first else return emptyList()
            val parity = if (singletonIsExplicit && first == last) "" else match.groupValues[3].ifBlank { globalParity }
            if (first !in 1..366 || last !in first..366) return emptyList()
            for (week in first..last) {
                if (parity == "单" && week % 2 == 0) continue
                if (parity == "双" && week % 2 == 1) continue
                weeks.add(week)
            }
        }
        if (weeks.isEmpty()) return emptyList()
        val ordered = weeks.toList()
        val runs = mutableListOf<Triple<Int, Int, Int>>()
        var i = 0
        while (i < ordered.size) {
            val step = if (i + 1 < ordered.size && ordered[i + 1] - ordered[i] == 2) 2 else 1
            var j = i
            while (j + 1 < ordered.size && ordered[j + 1] - ordered[j] == step) j++
            runs += Triple(ordered[i], ordered[j], if (step == 2) if (ordered[i] % 2 == 1) 1 else 2 else 0)
            i = j + 1
        }
        return runs
    }
}
