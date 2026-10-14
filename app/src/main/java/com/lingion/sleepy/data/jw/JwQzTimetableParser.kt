package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Strong-Wisdom's newer #timetable view carries explicit sections in each course. */
class JwQzTimetableParser(source: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> {
        val table = Jsoup.parse(source).selectFirst("table#timetable") ?: return emptyList()
        val courses = linkedSetOf<JwCourse>()
        var days = (1..7).toList()
        for (row in table.select("tr").filter { it.closest("table") == table }) {
            val cells = row.children().filter { it.tagName() == "td" || it.tagName() == "th" }
            val headerDays = cells.mapNotNull { weekday(it.text()) }
            if (headerDays.size == 7 && headerDays.toSet().size == 7) {
                days = headerDays
                continue
            }
            // Left-side section labels may be either th or td; they are not weekdays.
            if (cells.size !in 7..9) continue
            val dayCells = cells.takeLast(7)
            if (dayCells.any { it.attr("colspan").toIntOrNull()?.let { n -> n > 1 } == true }) continue
            dayCells.forEachIndexed { index, cell ->
                for (content in cell.getElementsByClass("kbcontent")) {
                    if (content.parents().any { it != cell && it.hasClass("kbcontent") }) continue
                    // The detailed kbcontent can be hidden by the school's view switch.
                    // kbcontent1 is the alternate condensed view and has no section data.
                    for (fragment in content.html().split(Regex("-{5,}"))) {
                        parseBlock(fragment, days[index], courses)
                    }
                }
            }
        }
        return courses.toList()
    }

    private fun parseBlock(html: String, day: Int, courses: MutableSet<JwCourse>) {
        val block = Jsoup.parseBodyFragment(html).body()
        block.select(".item-box, script, style").remove()
        val time = block.selectFirst("[title='周次(节次)']")?.text() ?: return
        val sectionMatch = Regex("\\[([0-9\\s,，、-]+)节\\]").find(time) ?: return
        val nodes = sectionMatch.groupValues[1].split(Regex("[-,，、\\s]+"))
            .filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: return }
        if (nodes.isEmpty() || nodes.any { it <= 0 } || nodes.zipWithNext().any { it.first >= it.second }) return
        val nodeRanges = if (nodes.size <= 2 && !sectionMatch.value.contains(Regex("[,，、]"))) {
            listOf(nodes.first() to nodes.last())
        } else {
            val runs = mutableListOf<Pair<Int, Int>>()
            var start = nodes.first()
            var end = start
            for (node in nodes.drop(1)) {
                if (node != end + 1) { runs += start to end; start = node }
                end = node
            }
            runs + (start to end)
        }
        // The course name is a font without title, before the metadata fields.
        val name = block.select("font").takeWhile { !it.hasAttr("title") }
            .filterNot { it.isHidden() }.joinToString("") { it.text() }.trim()
        if (name.isBlank()) return
        val teacher = block.select("[title='老师']").text().trim()
            .ifEmpty { block.select("[title='教师']").text().trim() }
        val room = block.select("[title='教室']").text().trim() +
            block.select("[title='分组']").text().trim()
        val weekText = time.substring(0, sectionMatch.range.first)
            .replace('（', '(').replace('）', ')').replace('，', ',')
        val segments = weekText.split(',')
        // A trailing shared suffix applies to bare ranges, but local annotations
        // must not leak into a later unmarked range: 1-4(单),6(周).
        val hasLocalParity = segments.dropLast(1).any { it.contains('单') || it.contains('双') }
        val globalParity = when {
            hasLocalParity -> 0
            weekText.contains('单') && !weekText.contains('双') -> 1
            weekText.contains('双') && !weekText.contains('单') -> 2
            else -> 0
        }
        for (segment in segments) {
            val type = when { segment.contains('单') -> 1; segment.contains('双') -> 2; else -> globalParity }
            val normalized = segment.replace(Regex("[()周单双\\s]"), "")
            val range = Regex("(\\d+)(?:-(\\d+))?").matchEntire(normalized) ?: continue
            val start = range.groupValues[1].toIntOrNull() ?: continue
            val end = range.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: start
            if (start <= 0 || end < start) continue
            val first = if (type != 0 && start % 2 != type % 2) start + 1 else start
            if (first > end) continue
            for ((startNode, endNode) in nodeRanges) {
                courses += JwCourse(name, room, teacher, day, startNode, endNode, first, end, type)
            }
        }
    }

    private fun weekday(text: String): Int? {
        val label = text.trim().removePrefix("星期").removePrefix("周")
        return when (label) {
            "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6; "日", "天" -> 7
            else -> null
        }
    }

    private fun Element.isHidden(): Boolean = hasAttr("hidden") ||
        Regex("display\\s*:\\s*none", RegexOption.IGNORE_CASE).containsMatchIn(attr("style"))
}
