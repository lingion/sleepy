package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Declared school protocols audited against the local WakeUp 6.6.2 classes6.dex. */
internal class JwUndergradBHtmlParser(source: String, private val protocol: String) : JwParser(source) {
    private val doc by lazy { Jsoup.parse(source).also { it.outputSettings().prettyPrint(false) } }

    override fun generateCourseList(): List<JwCourse> = (when (protocol) {
        "huat" -> huat()
        "seu_2017" -> seu2017()
        "shu2024" -> shu2024()
        "tju" -> tju()
        "hqu" -> hqu()
        "changzhou" -> changzhou()
        else -> emptyList()
    }).filter { valid(it) }

    private fun text(html: String) = Jsoup.parseBodyFragment(html).text().trim()
    private fun lines(cell: Element) = cell.html().split(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)).map(::text)

    private fun huat(): List<JwCourse> {
        val table = doc.getElementById("ctl00_ContentPlaceHolder1_CourseTable") ?: return emptyList()
        val output = mutableListOf<JwCourse>()
        for ((rowIndex, row) in table.select("[valign=middle]").withIndex()) {
            var day = 0
            for (cell in row.children()) {
                if (cell.hasAttr("align")) continue
                day++
                for (body in cell.select("tbody")) {
                    val raw = body.text()
                    val value = raw.replace(Regex("([0-9]+)-([0-9]+周[（）全半]*)$"), "$1 - $2")
                    val fields = value.trim().split(' ')
                    if (fields.size < 5) continue
                    val startWeek = fields[fields.size - 3].toIntOrNull() ?: continue
                    val endWeek = fields.last().replace(Regex("[周（）全半]"), "").toIntOrNull() ?: continue
                    var teacher = "未知教师"
                    var room = "未知教室"
                    when (fields.size) {
                        6 -> if ('H' in fields[fields.size - 4]) teacher = fields[1] else room = fields[2]
                        7 -> { teacher = fields[1]; room = fields[3] }
                        else -> if (fields.size > 7) {
                            val candidate = fields[fields.size - 4]
                            val hasRoom = Regex("[0-9]{4}").matches(candidate)
                            if (hasRoom) room = candidate
                            teacher = fields.subList(1, fields.size - if (hasRoom) 5 else 4).joinToString(" ")
                        }
                    }
                    val start = (rowIndex.toLong() * 2 + 1).takeIf { it <= Int.MAX_VALUE }?.toInt() ?: continue
                    val end = endNode(start, if (rowIndex == 4) 3 else 2) ?: continue
                    output += JwCourse(fields[0], room, teacher, day, start, end, startWeek, endWeek, credit = 1f)
                }
            }
        }
        return output
    }

    private fun seu2017(): List<JwCourse> {
        val tables = doc.select(".tableline")
        if (tables.size < 2) return emptyList()
        val teachers = mutableMapOf<String, Pair<String, Float>>()
        for (row in tables.first()!!.select("tr").drop(1)) {
            val cells = row.select("td")
            if (cells.size >= 4) teachers[cells[1].text().trim()] =
                cells[2].text().trim() to (cells[3].text().trim().toFloatOrNull() ?: 0f)
        }
        val output = mutableListOf<JwCourse>()
        val headers = setOf("时间", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日", "早晨", "上午", "下午", "晚上")
        for (row in tables.last()!!.select("tr").drop(1)) {
            var day = 0
            for (cell in row.select("td")) {
                if (cell.text().trim() in headers) continue
                val fields = lines(cell)
                for (entry in fields.chunked(3)) {
                    if (entry.size != 3) continue
                    val timing = Regex("\\[([^]]+)周]([^节]+)节").matchEntire(entry[1]) ?: continue
                    val nodes = range(timing.groupValues[2]) ?: continue
                    val name = entry[0]
                    val (teacher, credit) = teachers[name] ?: ("" to 0f)
                    val parity = when { "(单)" in entry[2] -> 1; "(双)" in entry[2] -> 2; else -> 0 }
                    for ((first, last, _) in weeks(timing.groupValues[1])) output += JwCourse(
                        name, entry[2].replace("(单)", "").replace("(双)", ""), teacher,
                        day, nodes.first, nodes.second, first, last, parity, credit = credit)
                }
                day++
            }
        }
        return output
    }

    private fun shu2024(): List<JwCourse> {
        val output = mutableListOf<JwCourse>()
        for (card in doc.select("div.arranged-content div.el-card.arranged-course-card")) {
            val fields = card.select("div.card-item.cv-clearfix").associate { item ->
                item.select("div.label.cv-pull-left").text().trim().removeSuffix(":") to
                    item.select("div.value.cv-pull-left").text().trim()
            }
            output += shuCourses(fields["课程名"].orEmpty(), fields["上课教师"].orEmpty(), fields["上课地点"].orEmpty(),
                fields["上课时间"].orEmpty(), fields["学分"]?.toFloatOrNull() ?: 0f)
        }
        if (output.isNotEmpty()) return output
        for (row in doc.select("div.arranged-content table.el-table__body tbody tr.el-table__row")) {
            val cells = row.select("td")
            if (cells.size < 11) continue
            output += shuCourses(cells[1].text().trim(), cells[5].text().trim(), cells[7].text().trim(),
                cells[6].text().trim(), cells[3].text().trim().toFloatOrNull() ?: 0f)
        }
        return output
    }

    private fun shuCourses(name: String, teacher: String, room: String, timing: String, credit: Float): List<JwCourse> {
        val output = mutableListOf<JwCourse>()
        for (part in timing.split(Regex("\\s+"))) {
            if (part.isBlank()) continue
            val match = Regex("([一二三四五六日])([0-9]+)-([0-9]+)([单双]?)(?:\\(([^()]*)\\))?([单双]?)").matchEntire(part) ?: continue
            if (match.groupValues[4].isNotEmpty() && match.groupValues[6].isNotEmpty()) continue
            val parity = when (match.groupValues[4] + match.groupValues[6]) { "单" -> 1; "双" -> 2; else -> 0 }
            val rawWeeks = match.groupValues[5]
            val parsed = if (rawWeeks.isBlank()) listOf(1 to 10) else {
                val values = rawWeeks.split(',').map { segment ->
                    val token = Regex("(?:第)?([0-9]+(?:[-~][0-9]+)?)(?:周)?").matchEntire(segment) ?: return@map null
                    range(token.groupValues[1])
                }
                if (values.any { it == null }) continue
                values.filterNotNull()
            }
            val day = "一二三四五六日".indexOf(match.groupValues[1]) + 1
            val nodes = range(match.groupValues[2] + "-" + match.groupValues[3]) ?: continue
            // Merge intervals without expanding individual weeks; sparse holes remain absent.
            for ((first, last) in mergeRanges(parsed)) output += JwCourse(name, room, teacher, day,
                nodes.first, nodes.second, first, last, parity, credit = credit)
        }
        return output
    }

    internal fun tableName(): String? {
        if (protocol != "shu2024") return null
        val title = doc.select("a.gologin-btn span.el-link--inner").firstOrNull()?.ownText().orEmpty()
        val m = Regex("(\\d{4})-(\\d{4})学年(\\S+)季学期").find(title) ?: return null
        return m.groupValues[1].takeLast(2) + "-" + m.groupValues[2].takeLast(2) + m.groupValues[3]
    }

    private fun tju(): List<JwCourse> {
        val headers = doc.selectFirst(".el-table__header")?.select("th")?.map { it.text().trim() } ?: return emptyList()
        fun column(label: String) = headers.indexOfFirst { label in it }
        val nameColumn = column("课程名称")
        val teacherColumn = column("教师")
        if (nameColumn < 0 || teacherColumn < 0) return emptyList()
        val output = mutableListOf<JwCourse>()
        for (row in doc.selectFirst(".el-table__body")?.select("tr").orEmpty()) {
            val cells = row.select("td")
            fun value(index: Int) = cells.getOrNull(index)?.text()?.trim().orEmpty()
            val timing = value(column("上课时间")).ifBlank { value(column("教学安排")) }
            for (part in timing.split(",星期")) {
                val m = Regex("(?:星期)?([一二三四五六日天])\\s+(\\d+(?:-\\d+)?)节\\[([^]]+)](.*)").matchEntire(part.trim()) ?: continue
                val day = "一二三四五六日".indexOf(m.groupValues[1].replace('天', '日')) + 1
                val nodes = range(m.groupValues[2]) ?: continue
                val teacher = value(teacherColumn).split(Regex("\\(.*?\\)")).filter { it.isNotBlank() }.joinToString(",")
                val room = if (column("上课地点") < 0) m.groupValues[4] else value(column("上课地点"))
                for ((first, last, parity) in weeks(m.groupValues[3])) output += JwCourse(value(nameColumn), room, teacher,
                    day, nodes.first, nodes.second, first, last, parity, credit = value(column("学分")).toFloatOrNull() ?: 0f,
                    note = value(column("备注")))
            }
        }
        return output
    }

    private fun hqu(): List<JwCourse> {
        data class Plan(val name: String, val teacher: String, val credit: Float)
        val plans = doc.getElementById("CoursePlanTable")?.select("tr").orEmpty().mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size < 3 || cells[0].text().isBlank()) null else Plan(cells[0].text().trim(), cells[1].text().trim(), cells[2].text().trim().toFloatOrNull() ?: 0f)
        }
        val output = mutableListOf<JwCourse>()
        for ((day, row) in doc.getElementById("ArrangeTable")?.select("tr").orEmpty().withIndex()) {
            if (day == 0) continue
            for ((column, cell) in row.select("td").withIndex()) {
                if (column == 0) continue
                val parts = cell.select("div").map { it.text().trim() }
                if (parts.isEmpty()) continue
                val plan = plans.firstOrNull { parts[0].endsWith(it.name) }
                val name = plan?.name ?: parts[0]
                val weekIndex = parts.indexOfLast { Regex("\\d+周").containsMatchIn(it) }
                if (weekIndex < 1) continue
                var room = parts[weekIndex - 1]
                val start = ((column.toLong() - 1) * 2 + 1).takeIf { it in 1..Int.MAX_VALUE }?.toInt() ?: continue
                val length = Regex("\\((\\d+)节\\)").find(room)
                val count = if (length != null) length.groupValues[1].toIntOrNull() ?: continue else if (start < 11) 2 else 3
                val end = endNode(start, count) ?: continue
                if (length != null) room = room.replace(length.value, "")
                val parity = when { "单周" in cell.html() -> 1; "双周" in cell.html() -> 2; else -> 0 }
                for ((first, last, _) in weeks(parts[weekIndex].substringBefore("周"))) output += JwCourse(name, room,
                    plan?.teacher.orEmpty(), day, start, end, first, last, parity, credit = plan?.credit ?: 0f)
            }
        }
        return output
    }

    private fun changzhou(): List<JwCourse> {
        val teachers = doc.getElementById("GVxkall")?.select(".dg1-item").orEmpty().mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size < 6) null else cells[1].text() to cells[5].text()
        }.toMap()
        val output = mutableListOf<JwCourse>()
        for ((node, row) in doc.getElementById("GVxkkb")?.select(".dg1-item").orEmpty().withIndex()) {
            for ((day, cell) in row.select("td").withIndex()) {
                if (day == 0) continue
                var prefix = ""
                for (part in cell.text().split('/')) {
                    if (part.isBlank()) continue
                    val fields = part.trim().split(' ')
                    if (fields.size != 4) {
                        prefix += part.trim() + "/"
                        continue
                    }
                    val ranges = weeks(fields[3])
                    if (ranges.isEmpty()) { prefix += part.trim() + "/"; continue }
                    val parity = when (fields[2]) { "单" -> 1; "双" -> 2; else -> 0 }
                    for ((first, last, _) in ranges) output += JwCourse(prefix + fields[0], fields[1], teachers[fields[0]].orEmpty(),
                        day, node + 1, node + 1, first, last, parity)
                    prefix = ""
                }
            }
        }
        return output
    }

    companion object {
        internal fun range(raw: String): Pair<Int, Int>? {
            val m = Regex("([0-9]+)(?:\\s*[-~]\\s*([0-9]+))?").matchEntire(raw.trim()) ?: return null
            val first = m.groupValues[1].toIntOrNull() ?: return null
            val last = m.groupValues[2].ifBlank { m.groupValues[1] }.toIntOrNull() ?: return null
            return (first to last).takeIf { first > 0 && last >= first }
        }

        internal fun weeks(raw: String): List<Triple<Int, Int, Int>> = raw.split(Regex("[,，、\\\\/ ]+")).mapNotNull { piece ->
            val m = Regex("(?:第)?([0-9]+(?:[-~][0-9]+)?)(?:周)?(?:[（(]?([单双])[)）]?(?:周)?)?").matchEntire(piece.trim()) ?: return@mapNotNull null
            val r = range(m.groupValues[1]) ?: return@mapNotNull null
            val parity = when (m.groupValues[2]) { "单" -> 1; "双" -> 2; else -> 0 }
            Triple(r.first, r.second, parity)
        }

        internal fun endNode(start: Int, length: Int): Int? =
            (start.toLong() + length.toLong() - 1).takeIf { start > 0 && length > 0 && it <= Int.MAX_VALUE }?.toInt()

        internal fun mergeRanges(values: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
            val output = mutableListOf<Pair<Int, Int>>()
            for (value in values.sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })) {
                val previous = output.lastOrNull()
                if (previous != null && value.first.toLong() <= previous.second.toLong() + 1) {
                    output[output.lastIndex] = previous.first to maxOf(previous.second, value.second)
                } else output += value
            }
            return output
        }

        internal fun valid(c: JwCourse) = c.name.isNotBlank() && c.day in 1..7 && c.startNode > 0 &&
            c.endNode >= c.startNode && c.startWeek > 0 && c.endWeek >= c.startWeek && c.type in 0..2 &&
            (c.type == 0 || c.startWeek % 2 == c.type % 2 || c.endWeek > c.startWeek)

        val shuPeriods = listOf("08:00-08:45", "08:55-09:40", "10:00-10:45", "10:55-11:40", "13:00-13:45", "13:55-14:40",
            "15:00-15:45", "15:55-16:40", "18:00-18:45", "18:55-19:40", "20:00-20:45", "20:55-21:40")
            .mapIndexed { index, time -> Triple(index + 1, time.substringBefore('-'), time.substringAfter('-')) }
    }
}
