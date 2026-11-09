package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

internal class JwUndergradLegacyParser(source: String, private val protocol: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> = (when (protocol) {
        "jnu" -> parseJnu()
        "swust" -> parseSwust()
        "bjtu" -> parseBjtuLegacy()
        else -> emptyList()
    }).filter { course ->
        course.name.isNotBlank() && course.day in 1..7 && course.startNode in 1..EXPLICIT_SCHEDULE_RESOURCE_BOUND &&
            course.endNode in course.startNode..EXPLICIT_SCHEDULE_RESOURCE_BOUND && course.startWeek in 1..366 &&
            course.endWeek in course.startWeek..366 && course.type in 0..2
    }

    private fun parseJnu(): List<JwCourse> {
        val boundary = Regex("</html\\s*>", RegexOption.IGNORE_CASE).find(source)
        val remainder = boundary?.let { source.substring(it.range.last + 1) }.orEmpty()
        val document = if (remainder.isBlank()) source else remainder
        val endOfCapturedDocument = Regex("</html\\s*>", RegexOption.IGNORE_CASE).find(document)
        if (endOfCapturedDocument != null && document.substring(endOfCapturedDocument.range.last + 1).isNotBlank()) return emptyList()
        val doc = Jsoup.parse(document)
        val rows = doc.getElementById("oReportCell")?.selectFirst(".a8")?.select("tr") ?: return emptyList()
        if (rows.size < 10) return emptyList()
        val output = mutableListOf<JwCourse>()
        for ((dayIndex, row) in rows.subList(3, 10).withIndex()) {
            for ((node, cell) in row.select("td").withIndex()) {
                if (node !in 1..EXPLICIT_SCHEDULE_RESOURCE_BOUND) continue
                val text = cell.select("div").text().trim()
                if (text.isEmpty() || "课程：" !in text || '(' !in text) continue
                val weeks = Regex("从第(\\d+)至(\\d+)").find(text)
                if ("从第" in text && weeks == null) continue
                val startWeek = if (weeks == null) 1 else weeks.groupValues[1].toIntOrNull() ?: continue
                val endWeek = if (weeks == null) 18 else weeks.groupValues[2].toIntOrNull() ?: continue
                val parity = when { "单周" in text -> 1; "双周" in text -> 2; else -> 0 }
                val room = if (parity != 0) text.substringAfterLast("单周").substringAfterLast("双周").trim().substringBefore(' ')
                    else text.substringBeforeLast(' ').substringAfterLast(' ')
                val course = JwCourse(
                    text.substringAfter("课程：").substringBeforeLast('('), room, "", dayIndex + 1,
                    node, node, startWeek, endWeek, parity,
                )
                val previous = output.lastOrNull()
                if (previous != null && previous.name == course.name && previous.day == course.day &&
                    previous.room == course.room && previous.teacher == course.teacher &&
                    previous.startWeek == course.startWeek && previous.endWeek == course.endWeek &&
                    previous.type == course.type && previous.endNode == course.startNode - 1
                ) output[output.lastIndex] = previous.copy(endNode = node)
                else output += course
            }
        }
        return output
    }

    private fun parseSwust(): List<JwCourse> {
        val table = Jsoup.parse(source).selectFirst(".UICourseTable tbody") ?: return emptyList()
        val output = mutableListOf<JwCourse>()
        for ((index, row) in table.select("tr").withIndex()) {
            val cells = row.select("td")
            if (cells.size < 7) continue
            for ((column, cell) in cells.takeLast(7).withIndex()) {
                for (lecture in cell.select(".lecture")) {
                    val name = lecture.select(".course").text().trim()
                    val room = lecture.select(".place").text().trim()
                    val weeksText = lecture.select(".week").text().trim().substringBefore('(').trim()
                    if (!Regex("\\d+(?:-\\d+)?").matches(weeksText)) continue
                    val weeks = weeksText.split('-')
                    val start = weeks.firstOrNull()?.trim()?.toIntOrNull() ?: continue
                    val end = weeks.lastOrNull()?.trim()?.toIntOrNull() ?: continue
                    // The pinned page has no independent teacher field.
                    output += JwCourse(name, room, "", column + 1, index + 1, index + 1, start, end)
                }
            }
        }
        return output
    }

    private fun parseBjtuLegacy(): List<JwCourse> {
        val table = Jsoup.parse(source).selectFirst("table.table.table-bordered") ?: return emptyList()
        val blockPattern = Regex("\\w{7}\\s\\S+\\s[^\\[]+\\[.]\\s[^A-Z]+\\w{5}")
        val output = mutableListOf<JwCourse>()
        for ((node, row) in table.select("tr").withIndex()) {
            var firstNonempty = true
            var day = 1
            for (cell in row.select("td")) {
                val text = cell.text().trim()
                if (firstNonempty && text.isNotEmpty()) {
                    firstNonempty = false
                    continue
                }
                for (block in blockPattern.findAll(text)) {
                    val raw = block.value
                    val name = Regex("]\\s.+\\s\\[").find(raw)?.value?.let { it.substring(2, it.length - 2) } ?: continue
                    val room = Regex("[A-Z]{2}\\d\\d\\d").find(raw)?.value.orEmpty()
                    val teacher = Regex("\\d\\d周\\s\\S+").find(raw)?.value?.substringAfter(' ').orEmpty()
                    val weeksText = Regex("第([0-9,\\-]+)周").find(raw)?.groupValues?.get(1) ?: continue
                    for ((start, end, parity) in schoolWeekRanges(weeksText)) {
                        output += JwCourse(name, room, teacher, day, node, node, start, end, parity)
                    }
                }
                day++
            }
        }
        return output
    }
}
