package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal object JwZfVariantHtml {
    fun parse(source: String): List<JwCourse>? {
        val doc = Jsoup.parse(source)
        doc.outputSettings().prettyPrint(false)
        doc.getElementById("table_tb")?.let { parseNested(it).takeIf { courses -> courses.isNotEmpty() }?.let { return it } }
        val newTable = doc.getElementById("table1")
        if (newTable?.selectFirst("td[id] .title") != null) {
            for (cell in newTable.select("td[id]")) {
                for (record in cell.getElementsByTag("div")) {
                    if (record.getElementsByClass("title").isEmpty()) continue
                    val paragraphs = record.getElementsByTag("p")
                    if (paragraphs.isEmpty() || paragraphs.any { it.getElementsByAttribute("title").isNotEmpty() }) continue
                    paragraphs.getOrNull(0)?.attr("title", "节/周")
                    paragraphs.getOrNull(1)?.attr("title", "上课地点")
                    paragraphs.getOrNull(2)?.attr("title", "教师")
                }
            }
            JwNewZfParser(doc.outerHtml()).generateCourseList().takeIf { it.isNotEmpty() }?.let { return it }
        }
        val classTable = doc.getElementById("Table6") ?: doc.getElementById("table6") ?: return null
        return parseClassTable(doc, classTable)
    }

    fun parseClass(source: String): List<JwCourse> {
        val document = Jsoup.parse(source)
        document.outputSettings().prettyPrint(false)
        val table = document.getElementById("Table6") ?: document.getElementById("table6") ?: return emptyList()
        return parseClassTable(document, table)
    }

    private fun parseClassTable(doc: org.jsoup.nodes.Document, classTable: Element): List<JwCourse> {
        classTable.attr("id", "Table1")
        for (cell in classTable.select("td")) {
            val festival = cell.getElementsByClass("festival").firstOrNull()?.text()?.trim()?.toIntOrNull()
            if (festival != null && cell.text().trim() == festival.toString()) {
                cell.text("第${festival}节")
                continue
            }
            val text = cell.html()
            val pieces = text.split(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE))
            if (pieces.size < 3) continue
            val rewritten = pieces.map(::normalizeClassTime).joinToString("<br>")
            cell.html(rewritten)
        }
        return JwOldZfParser(doc.outerHtml()).generateCourseList()
    }

    private fun normalizeClassTime(value: String): String {
        if (value.contains('{')) return value
        val normalized = value.replace('（', '(').replace('）', ')')
        val slash = Regex("[0-9]+节/([单双]?)周\\(([^)]*)\\)").find(normalized)
        if (slash != null) {
            val count = Regex("[0-9]+").find(slash.value)?.value ?: return value
            return "{第" + slash.groupValues[2] + "周}" + when (slash.groupValues[1]) {
                "单" -> "单周"; "双" -> "双周"; else -> ""
            } + "${count}节/"
        }
        val beforeSections = normalized.substringBefore('(').trim()
        val weeks = Regex("([0-9]+)(?:-([0-9]+))?([单双]?)").matchEntire(beforeSections) ?: return value
        val end = weeks.groupValues[2].ifEmpty { weeks.groupValues[1] }
        return "{第" + weeks.groupValues[1] + "-" + end + "周}" + when (weeks.groupValues[3]) {
            "单" -> "单周"; "双" -> "双周"; else -> ""
        } + normalized.substringAfter(beforeSections)
    }

    private fun parseNested(table: Element): List<JwCourse> = buildList {
        for (cell in table.select("td[id^=td_][rowspan]")) {
            val coordinates = Regex("td_([1-7])-([0-9]+)").matchEntire(cell.id()) ?: continue
            val day = coordinates.groupValues[1].toInt()
            val start = coordinates.groupValues[2].toInt()
            val span = cell.attr("rowspan").toIntOrNull() ?: continue
            if (start <= 0 || span <= 0) continue
            for (record in cell.select("table")) {
                val paragraphs = record.select("p")
                if (paragraphs.size < 5) continue
                val name = paragraphs[0].text().trim()
                val room = paragraphs[2].text().trim()
                val teacher = paragraphs[3].text().trim()
                val weeks = paragraphs[4].text().trim()
                val field = Element("div").text(name)
                field.appendElement("br")
                field.appendElement("font").attr("title", "教师").text(teacher)
                field.appendElement("br")
                field.appendElement("font").attr("title", "教室").text(room)
                field.appendElement("br")
                field.appendElement("font").attr("title", "周次(节次)")
                    .text("$weeks[$start-${start + span - 1}节]")
                val courses = mutableListOf<JwCourse>()
                JwQzParser("").convert(day, 1, field.html(), courses)
                addAll(courses)
            }
        }
    }
}
