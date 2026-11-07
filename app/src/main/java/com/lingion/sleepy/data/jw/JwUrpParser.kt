package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/**
 * 老版 URP 教务系统解析器（HTML 表格版本）
 *
 * 数据来源：URP 综合教务（部分校）课表页用 HTML 表格展示，class 为
 *   "displayTag" 或 "table table-striped table-bordered"。
 * 表头：课程名/教师/星期/节次/周次/教学楼/教室/节数
 *
 * 基于 dIT8Zv/WakeupSchedule_BUPT (Apache-2.0) UrpParser.kt 简化而来
 * https://github.com/dIT8Zv/WakeupSchedule_BUPT/blob/master/app/src/main/java/com/suda/yzune/wakeupschedule/schedule_import/parser/UrpParser.kt
 */
class JwUrpParser(source: String) : JwParser(source) {

    override fun generateCourseList(): List<JwCourse> {
        val document = Jsoup.parse(source)
        val grid = parseUrpGrid(document)
        if (grid.isNotEmpty()) return grid
        return document.select("table.displayTag, table.table.table-striped.table-bordered, table#tb")
            .flatMap(::parseTable)
    }

    private fun parseTable(table: org.jsoup.nodes.Element): List<JwCourse> {
        val headers = table.select("thead th").map { it.text().trim() }
        fun column(label: String): Int = headers.indexOfFirst { it.contains(label) }
        val nameIndex = column("课程名")
        val teacherIndex = column("教师")
        val weekIndex = column("周次")
        val dayIndex = column("星期")
        val nodeIndex = column("节次")
        val stepIndex = column("节数")
        val buildingIndex = column("教学楼")
        val roomIndex = column("教室")
        if (listOf(nameIndex, weekIndex, dayIndex, nodeIndex).any { it < 0 }) return emptyList()
        val rows = table.select("tbody > tr").filter { it.closest("table") == table }
        val carried = mutableMapOf<Int, Pair<org.jsoup.nodes.Element, Int>>()
        val courses = mutableListOf<JwCourse>()
        for ((rowIndex, row) in rows.withIndex()) {
            val cells = arrayOfNulls<org.jsoup.nodes.Element>(headers.size)
            for ((index, entry) in carried) if (entry.second >= rowIndex) cells[index] = entry.first
            var cursor = 0
            for (cell in row.children()) {
                if (cell.tagName() != "td") return emptyList()
                val span = if (cell.hasAttr("rowspan")) cell.attr("rowspan").toIntOrNull() else 1
                val width = if (cell.hasAttr("colspan")) cell.attr("colspan").toIntOrNull() else 1
                if (span == null || span !in 1..rows.size - rowIndex || width != 1) return emptyList()
                while (cursor < cells.size && cells[cursor] != null) cursor++
                if (cursor >= cells.size) return emptyList()
                cells[cursor] = cell
                if (span > 1) carried[cursor] = cell to rowIndex + span - 1
                cursor++
            }
            if (cells.any { it == null }) continue
            fun text(index: Int): String = cells.getOrNull(index)?.text()?.trim().orEmpty()
            val name = text(nameIndex)
            if (name.isEmpty()) continue
            val day = dayNumber(text(dayIndex)) ?: continue
            val ranges = nodeRanges(text(nodeIndex))
            if (ranges.isEmpty()) continue
            val step = if (stepIndex < 0) null else smallNumber(text(stepIndex)) ?: continue
            if (step != null && step !in 1..30) continue
            val weekText = text(weekIndex)
            val parsedWeeks = schoolWeekRanges(weekText, trailingParityAppliesToAll = true)
            val weeks = if (weekText.contains(',') || weekText.contains('，')) {
                JwChengFangParser.weekIntList2WeekBeanList(parsedWeeks.flatMap { (first, last, parity) ->
                    (first..last).filter { parity == 0 || it % 2 == parity % 2 }
                }.distinct().sorted())
            } else parsedWeeks
            for ((start, end) in ranges) {
                val last = if (step == null) end else start + step - 1
                if (last !in start..30) continue
                for ((firstWeek, lastWeek, parity) in weeks) {
                    courses += JwCourse(name, text(buildingIndex) + text(roomIndex), text(teacherIndex), day,
                        start, last, firstWeek, lastWeek, parity)
                }
            }
        }
        return courses
    }

    private fun dayNumber(raw: String): Int? = raw.toIntOrNull()?.takeIf { it in 1..7 }
        ?: when (raw.removePrefix("星期").removePrefix("周")) {
            "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6; "日", "天" -> 7
            else -> null
        }

    private fun smallNumber(raw: String): Int? = raw.toIntOrNull() ?: when (raw) {
        "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6; "七" -> 7; "八" -> 8; "九" -> 9
        "十" -> 10; "十一" -> 11; "十二" -> 12; "十三" -> 13; "十四" -> 14; "十五" -> 15
        else -> null
    }

    private fun nodeRanges(raw: String): List<Pair<Int, Int>> {
        val cleaned = raw.removePrefix("第").removeSuffix("节")
        if (cleaned.endsWith("大")) {
            val number = smallNumber(cleaned.removeSuffix("大")) ?: return emptyList()
            val start = number * 2 - 1
            return if (start in 1..30) listOf(start to start) else emptyList()
        }
        val number = smallNumber(cleaned.removeSuffix("小"))
        return if (number != null && number in 1..30) listOf(number to number) else schoolNodeRanges(cleaned)
    }

    /**
     * T10: URP 网格变体解析 — td[id='<day>_<node>'] 定位, div.class_div 含 >=5 个 <p>:
     * p[0]=课名, p[1]=星号占位, p[2]=教师, p[3]=周次串, p[4]=节次串, p[5]=教室(可选)
     */
    private fun parseUrpGrid(doc: org.jsoup.nodes.Document): List<JwCourse> {
        val result = mutableListOf<JwCourse>()
        val gridTds = doc.select("td[id]")
        for (td in gridTds) {
            val id = td.attr("id")
            val parts = id.split("_")
            if (parts.size != 2) continue
            val day = parts[0].toIntOrNull() ?: continue
            if (day !in 1..7) continue
            val node = parts[1].toIntOrNull() ?: continue
            val divs = td.getElementsByClass("class_div")
            for (div in divs) {
                val ps = div.getElementsByTag("p")
                if (ps.size < 5) continue
                val name = ps[0].text().trim()
                if (name.isEmpty()) continue
                val teacher = ps[2].text().trim()
                val weekStr = ps[3].text().trim()
                val nodeStr = ps[4].text().trim()
                val ranges = gridWeekRanges(weekStr)
                if (ranges.isEmpty()) continue
                // 节次串 "N-M节" → start/end
                val nParts = nodeStr.replace("节", "").split("-")
                val startNode = nParts.getOrNull(0)?.trim()?.toIntOrNull() ?: node
                val endNode = nParts.getOrNull(1)?.trim()?.toIntOrNull() ?: startNode
                val room = if (ps.size >= 6) ps[5].text().trim() else ""
                for (r in ranges) {
                    result += JwCourse(
                        name = name, teacher = teacher, room = room, day = day,
                        startNode = startNode, endNode = endNode,
                        startWeek = r.first, endWeek = r.second, type = r.third
                    )
                }
            }
        }
        return result
    }

    /** 网格变体周次串: "1-16周" / "2-14周单周" / "1-8,10-17周" / "3-12周双周" → 归并段 */
    private fun gridWeekRanges(weekStr: String): List<Triple<Int, Int, Int>> {
        if (weekStr.isBlank()) return emptyList()
        val result = mutableListOf<Triple<Int, Int, Int>>()
        val cleaned = weekStr.replace("周", "")
        cleaned.split(',', '，', ';', '；').forEach { seg0 ->
            val seg = seg0.trim()
            if (seg.isEmpty()) return@forEach
            val type = when {
                seg.contains('单') -> 1
                seg.contains('双') -> 2
                else -> 0
            }
            val digits = seg.filter { it.isDigit() || it == '-' }
            if (digits.contains('-')) {
                val parts = digits.split('-')
                val s = parts.getOrNull(0)?.toIntOrNull() ?: return@forEach
                val e = parts.getOrNull(1)?.toIntOrNull() ?: s
                if (type == 0) {
                    result += Triple(s, e, 0)
                } else {
                    // 单/双周: 展开成奇/偶周序列再归并 ("2-14周单周" → (3,13,1))
                    val weeks = (s..e).filter { if (type == 1) it % 2 == 1 else it % 2 == 0 }
                    result += JwChengFangParser.weekIntList2WeekBeanList(weeks)
                }
            } else {
                val v = digits.toIntOrNull() ?: return@forEach
                result += Triple(v, v, type)
            }
        }
        return result
    }

    /** T8: displayTag = 100; table-striped = 90; urp grid td[id] = 100 */
    override fun confidence(): Int = when {
        Regex("""td[^>]+id="\d+_\d+"""").containsMatchIn(source) && source.contains("class_div") -> 100
        source.contains("displayTag") -> 100
        source.contains("table-striped") -> 90
        else -> 0
    }

    override fun matchedFeatures(): List<String> = buildList {
        if (source.contains("displayTag")) add("class=displayTag")
        if (source.contains("table-striped")) add("class=table table-striped table-bordered")
    }
}
