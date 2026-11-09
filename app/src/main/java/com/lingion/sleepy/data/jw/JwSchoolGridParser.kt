package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal class JwSchoolGridParser(source: String, private val protocol: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> = when (protocol) {
        "gxic" -> gxic()
        "gxnu" -> gxnu()
        "jxnu" -> jxnu()
        "nnutc" -> nnutc()
        else -> emptyList()
    }.filter { it.name.isNotBlank() && it.day in 1..7 && it.startNode >= 1 &&
        it.endNode >= it.startNode && it.startWeek in 1..366 && it.endWeek in it.startWeek..366 &&
        (it.startWeek..it.endWeek).any { week -> it.type == 0 || week % 2 == it.type % 2 } }

    private fun gxic(): List<JwCourse> {
        val table = Jsoup.parse(source).selectFirst("table[width=98%]") ?: return emptyList()
        val headers = table.select("th").map { it.text() }
        val sunday = headers.indexOfFirst { "星期天" in it || "星期日" in it }
        val monday = headers.indexOfFirst { "星期一" in it }
        val sundayFirst = sunday >= 0 && monday >= 0 && sunday < monday
        val sections = mapOf("第0102节" to 1, "第0304节" to 3, "第0506节" to 5, "第0708节" to 7, "第0910节" to 9)
        val output = mutableListOf<JwCourse>()
        for (row in table.select("tr")) {
            val cells = row.select("td")
            val first = sections[cells.firstOrNull()?.text()?.trim()] ?: continue
            for ((column, cell) in cells.drop(1).withIndex()) {
                if (column > 6) continue
                val day = if (sundayFirst) if (column == 0) 7 else column else column + 1
                for (link in cell.select("a[title]")) {
                    val name = link.childNodes().takeWhile { it.nodeName() != "br" }.joinToString("") { it.toString() }
                        .let { Jsoup.parse(it).text().trim() }
                    val fields = link.attr("title").lineSequence().map { it.trim() }.toList()
                    fun field(label: String): String? = fields.firstOrNull { it.startsWith(label) }?.substringAfter(label)?.trim()
                    val weeks = field("上课周次：") ?: continue
                    for ((start, end, parity) in schoolWeekRanges(weeks)) output += JwCourse(
                        name, field("开课地点：").orEmpty(), field("授课教师：").orEmpty(), day,
                        first, first + 1, start, end, parity,
                        startTime = gxicPeriods[first - 1].first, endTime = gxicPeriods[first].second)
                }
            }
        }
        return output
    }

    private fun gxnu(): List<JwCourse> {
        val table = Jsoup.parse(source).getElementById("xkjbgzszGridIdGrid") ?: return emptyList()
        val output = mutableListOf<JwCourse>()
        for (row in table.select("tbody tr")) {
            fun field(key: String) = row.selectFirst("td[aria-describedby=xkjbgzszGridIdGrid_$key]")?.text()?.trim().orEmpty()
            val name = field("kcmc")
            val teacher = field("rkjs")
            val credit = field("xf").toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f } ?: 0f
            for (arrangement in field("sksjdd").split(';', '；')) {
                val match = Regex("(.+?)\\s*星期([一二三四五六日天])\\s+([0-9,，\\-]+)节\\s*【([^】]*)】").matchEntire(arrangement.trim()) ?: continue
                val day = "一二三四五六日".indexOf(match.groupValues[2].replace('天', '日')) + 1
                val weeks = schoolWeekRanges(match.groupValues[1].trim().removeSuffix("周"))
                val nodes = schoolNodeRanges(match.groupValues[3])
                for ((first, last) in nodes) for ((start, end, parity) in weeks) output += JwCourse(
                    name, match.groupValues[4].trim(), teacher, day, first, last, start, end, parity, credit = credit)
            }
        }
        return output
    }

    private fun jxnu(): List<JwCourse> {
        val document = Jsoup.parse(source)
        val teachers = mutableMapOf<String, String>()
        for (row in document.getElementById("_ctl1_dgStudentLesson")?.select("tr").orEmpty()) {
            val cells = row.select("td")
            val name = cells.getOrNull(1)?.text()?.trim().orEmpty()
            if (name.isNotEmpty()) teachers[name] = cells.getOrNull(3)?.text()?.trim().orEmpty()
                .ifEmpty { cells.getOrNull(4)?.text()?.trim().orEmpty() }
        }
        val table = document.getElementById("_ctl1_NewKcb") ?: return emptyList()
        val blocks = mapOf(1 to (1 to 2), 2 to (3 to 3), 3 to (4 to 4), 4 to (5 to 5), 6 to (6 to 7), 7 to (8 to 9), 8 to (10 to 10))
        val output = mutableListOf<JwCourse>()
        for ((index, row) in table.select("tr").withIndex()) {
            val nodes = blocks[index] ?: continue
            if (row.attr("bgcolor").equals("#00ccff", true)) continue
            var day = 0
            for (cell in row.select("td")) {
                if (cell.attr("bgcolor").lowercase() in listOf("#00ccff", "#cccccc")) continue
                day++
                for (card in cell.select("div").filter { it.children().none { child -> child.tagName() == "div" } }) {
                    val lines = lines(card)
                    val name = lines.firstOrNull().orEmpty()
                    val room = lines.getOrNull(1)?.substringAfter('(', "")?.substringBefore(')')?.trim().orEmpty()
                    output += JwCourse(name, room, teachers[name].orEmpty(), day, nodes.first, nodes.second, 1, 20)
                }
            }
        }
        return output
    }

    private fun nnutc(): List<JwCourse> {
        val body = Jsoup.parse(source).selectFirst("tbody") ?: return emptyList()
        val output = mutableListOf<JwCourse>()
        for (row in body.select("tr")) for ((day, cell) in row.select("td").withIndex()) {
            if (day !in 1..7) continue
            for (card in cell.select("div[class]").filter { it.children().none { child -> child.tagName() == "div" && child.hasAttr("class") } }) {
                val labels = card.select("label").map { it.text().trim() }
                val icons = card.select("svg").map { it.attr("data-icon") }
                if (labels.isEmpty() || labels.size != icons.size) continue
                val rawNodes = Regex("第([0-9,，\\-]+)节").matchEntire(labels.last())?.groupValues?.get(1) ?: continue
                val nodes = schoolNodeRanges(rawNodes)
                val starts = icons.indices.filter { icons[it] == "calculator" }
                for ((segment, begin) in starts.withIndex()) {
                    val until = starts.getOrNull(segment + 1) ?: labels.size
                    val indices = begin until until
                    val teacher = indices.firstOrNull { icons[it] == "user" }?.let { labels[it] }.orEmpty()
                    for (index in indices.filter { icons[it] == "environment" }) {
                        val room = Regex("([^\\[]*)\\[([^]]+)]").matchEntire(labels[index]) ?: continue
                        val weeks = schoolWeekRanges(room.groupValues[2], trailingParityAppliesToAll = true)
                        for ((first, last) in nodes) for ((start, end, parity) in weeks) output += JwCourse(
                            labels[begin], room.groupValues[1].trim(), teacher, day, first, last, start, end, parity)
                    }
                }
            }
        }
        return output
    }

    private fun lines(element: Element): List<String> = element.html().split(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE))
        .map { Jsoup.parse(it).text().trim() }

    private companion object {
        val gxicPeriods = listOf("08:40" to "09:20", "09:30" to "10:10", "10:30" to "11:10", "11:20" to "12:00",
            "14:30" to "15:10", "15:20" to "16:00", "16:10" to "16:50", "17:00" to "17:40", "19:40" to "20:20", "20:30" to "21:10")
    }
}
