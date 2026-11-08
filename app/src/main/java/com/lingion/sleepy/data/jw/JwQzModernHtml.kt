package com.lingion.sleepy.data.jw

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal object JwQzModernHtml {
    fun features(document: Document): List<String> = buildList {
        if (document.selectFirst("td[name=kbDataTd] li.qz-toolitiplists .qz-tooltipContent-title") != null) {
            add("qz-2024-tooltip")
        }
        val header = document.selectFirst(".el-table__header")?.text().orEmpty()
        if (Regex("星期[一二三四五六日天]").findAll(header).map { it.value.replace('天', '日') }.toSet().size == 7 &&
            document.select(".el-table__body .cell[style]").any {
                it.attr("style").replace(" ", "").contains("text-align:center")
            }) add("qz-2017-element-table")
    }

    fun parse(document: Document): List<JwCourse>? {
        val features = features(document)
        if ("qz-2024-tooltip" in features) return parse2024(document)
        if ("qz-2017-element-table" in features) return parse2017(document)
        return null
    }

    private fun parse2024(document: Document): List<JwCourse> = buildList {
        for (cell in document.getElementsByAttributeValue("name", "kbDataTd")) {
            val day = cell.elementSiblingIndex()
            if (day !in 1..7) continue
            for (record in cell.select("li.qz-toolitiplists")) {
                val name = record.selectFirst(".qz-tooltipContent-title")?.text()?.trim().orEmpty()
                val fields = record.select(".qz-tooltipContent-detailitem").associate { field ->
                    val text = field.text().trim()
                    text.substringBefore('：').trim() to text.substringAfter('：', "").trim()
                }
                val time = fields["时间"].orEmpty()
                if (!Regex("\\[[^]]+]").containsMatchIn(time)) continue
                addAll(convert(name, fields["老师"].orEmpty(), fields["地点"].orEmpty(), time, day))
            }
        }
    }

    private fun parse2017(document: Document): List<JwCourse> = buildList {
        val headers = document.selectFirst(".el-table__header")?.select("div").orEmpty()
        val sunday = headers.indexOfFirst { it.text().contains("星期日") }
        val monday = headers.indexOfFirst { it.text().contains("星期一") }
        val sundayFirst = sunday >= 0 && monday >= 0 && sunday < monday
        val rows = document.selectFirst(".el-table__body tbody")?.children()?.filter { it.tagName() == "tr" }.orEmpty()
        for ((rowIndex, row) in rows.withIndex()) {
            val start = rowIndex * 2 + 1
            val end = start + if (rowIndex == rows.lastIndex) 2 else 1
            val cells = row.select(".cell[style]").filter {
                it.attr("style").replace(" ", "").contains("text-align:center")
            }
            for ((column, cell) in cells.withIndex()) {
                if (column !in 0..6) continue
                val day = if (sundayFirst) listOf(7, 1, 2, 3, 4, 5, 6)[column] else column + 1
                for (record in cell.children()) {
                    val fields = record.children()
                    val index = fields.indexOfLast { Regex("\\d{1,2}周").containsMatchIn(it.text()) }
                    if (index < 3) continue
                    val name = fields[index - 3].text().trim()
                    val teacher = fields[index - 2].text().trim()
                    val time = fields[index].text().trim()
                    val slash = time.split('/')
                    val room: String
                    val schedule: String
                    if (slash.size == 3 && slash[1].contains('节')) {
                        room = slash[0].trim()
                        schedule = slash[2] + "[" + slash[1] + "]"
                    } else {
                        room = fields.getOrNull(index + 1)?.text()?.trim().orEmpty()
                        val sections = Regex("^([0-9,，、 -]+)节").find(time)
                        schedule = if (sections != null) time.substring(sections.range.last + 1) +
                            "[" + sections.groupValues[1] + "节]" else "$time[$start-$end 节]"
                    }
                    addAll(convert(name, teacher, room, schedule.replace("单双", ""), day))
                }
            }
        }
    }

    private fun convert(name: String, teacher: String, room: String, time: String, day: Int): List<JwCourse> {
        if (name.isBlank() || time.isBlank()) return emptyList()
        val fragment = Element("div").text(name)
        fragment.appendElement("br")
        fragment.appendElement("font").attr("title", "教师").text(teacher)
        fragment.appendElement("br")
        fragment.appendElement("font").attr("title", "上课地点").text(room)
        fragment.appendElement("br")
        fragment.appendElement("font").attr("title", "周次(节次)").text(time)
        val courses = mutableListOf<JwCourse>()
        JwQzParser("").convert(day, 1, fragment.html(), courses)
        return courses
    }
}
