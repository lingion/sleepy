package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/**
 * 强智教务系统解析器（基础版）。
 *
 * 基于 dIT8Zv/WakeupSchedule_BUPT (Apache-2.0) QzParser.kt
 * 简化而来（去掉了 CourseBaseBean/CourseDetailBean 转换层）。
 *
 * 源仓库：https://github.com/dIT8Zv/WakeupSchedule_BUPT/blob/master/app/src/main/java/com/suda/yzune/wakeupschedule/schedule_import/parser/qz/QzParser.kt
 *
 * 抓取对象：教务系统课表页的 `id="kbtable"` 节点，
 * 遍历 tr/td/div[class="tableName"]，用 `title="老师"` / `title="教室"` /
 * `title="周次(节次)"` 三个属性提数据。同格内多门课用 "-----" 分隔。
 *
 * 子类（如 [JwQzCrazyParser]）可重写 [tableName] 适配不同变体。
 */
open class JwQzParser(source: String) : JwParser(source) {

    /** 课表单元格内 class 名（多数强智学校为 "kbcontent"，部分 crazy 变体为 "kbcontent1"） */
    open val tableName: String = "kbcontent"
    protected open val usesSchoolRowMapping: Boolean = false

    open fun parseCourseName(infoStr: String): String {
        val prefix = infoStr.split(Regex("<(?:font|span)\\b[^>]*\\btitle\\s*=", RegexOption.IGNORE_CASE), limit = 2).first()
        val lines = prefix.split(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE))
            .map { Jsoup.parseBodyFragment(it).text().trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return ""
        if (lines.first().startsWith("节次:")) {
            return lines.firstOrNull { it.contains("学时]") }
                ?.substringBefore('[')?.substringBefore("课序号")?.trim()
                ?: lines.lastOrNull { !it.startsWith("节次:") && !Regex("分组\\d+").matches(it) }.orEmpty()
        }
        return lines.joinToString(" ").substringBefore("课序号").trim()
    }

    /** A school-specific row map applies only when the page supplies no section field. */
    open fun rowNodes(row: Int): Pair<Int, Int> = row * 2 - 1 to row * 2

    open fun convert(day: Int, nodeCount: Int, infoStr: String, courseList: MutableList<JwCourse>) {
        val courseHtml = Jsoup.parseBodyFragment(infoStr)
        val courseName = parseCourseName(infoStr)
        if (courseName.isBlank() || day !in 1..7) return
        fun field(title: String) = courseHtml.getElementsByAttributeValue("title", title).text().trim()
        val teacher = field("老师").ifEmpty { field("教师") }
        val room = (field("教室") + field("分组")).ifEmpty { field("上课地点") }
        val combined = field("周次(节次)")
        val enrollment = courseHtml.getElementsByAttributeValue("title", "选课人数").html()
            .split(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)).getOrNull(1)
            ?.let { Jsoup.parseBodyFragment(it).text() }.orEmpty()
        val time = combined.ifEmpty { field("周次") }.ifEmpty { enrollment }
            .replace('（', '(').replace('）', ')')
        if (time.isBlank()) return
        val bracket = Regex("\\[([^]]*)]").find(time)
        val tail = if (bracket == null) Regex("(?:\\s+|(?<=\\)))(第?[0-9][0-9,，、\\s-]*节)\\s*$").find(time) else null
        val weekText = when {
            bracket != null -> time.substring(0, bracket.range.first)
            tail != null -> time.substring(0, tail.range.first)
            else -> time
        }
        val independentNodes = field("节次").replace('（', '(').replace('）', ')')
        val explicitNodes = bracket?.groupValues?.get(1) ?: tail?.groupValues?.get(1)
            ?: independentNodes.takeIf { it.isNotBlank() }?.substringAfterLast(')')
        // An invalid supplied field is not a missing field. Never replace it with the grid row.
        val nodes = if (explicitNodes != null) parseNodes(explicitNodes) else listOf(rowNodes(nodeCount))
        if (nodes.isEmpty()) return
        for (segment in weekText.split(Regex("[,，、\\\\]"))) {
            val type = when { segment.contains('单') -> 1; segment.contains('双') -> 2; else -> 0 }
            val normalized = segment.replace(Regex("[第周单双()\\s]"), "")
            val range = Regex("(\\d+)(?:-(\\d+))?").matchEntire(normalized) ?: continue
            val start = range.groupValues[1].toIntOrNull() ?: continue
            val end = range.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: start
            if (start <= 0 || end < start) continue
            for ((first, last) in nodes) {
                courseList += JwCourse(courseName, room, teacher, day, first, last, start, end, type)
            }
        }
    }

    private fun parseNodes(text: String): List<Pair<Int, Int>> {
        val cleaned = text.trim().removeSurrounding("[", "]").removePrefix("第").removeSuffix("节").trim()
        val values = sortedSetOf<Int>()
        for (part in cleaned.split(Regex("[,，、]"))) {
            val token = part.replace(Regex("\\s+"), "")
            val range = Regex("(\\d+)(?:-(\\d+))?").matchEntire(token) ?: return emptyList()
            val packed = range.groupValues[2].isEmpty() && token.length == 4
            val start = (if (packed) token.take(2) else range.groupValues[1]).toIntOrNull() ?: return emptyList()
            val end = if (packed) token.takeLast(2).toIntOrNull() ?: return emptyList()
                else range.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: start
            if (start !in 1..99 || end !in start..99) return emptyList()
            values.addAll(start..end)
        }
        val runs = mutableListOf<Pair<Int, Int>>()
        for (node in values) {
            val previous = runs.lastOrNull()
            if (previous != null && previous.second + 1 == node) runs[runs.lastIndex] = previous.first to node
            else runs += node to node
        }
        return runs
    }

    private fun courseBlocks(html: String): List<String> = buildList {
        // Do not consume a name attached directly to the dashes (-----下一门课<br>).
        for (part in html.split(Regex("-{5,}"))) {
            var buffer = StringBuilder()
            var hasFields = false
            for (line in part.split(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE))) {
                val fragment = Jsoup.parseBodyFragment(line)
                val text = fragment.text().trim()
                if (buffer.isEmpty() && text.isEmpty()) continue
                val isField = fragment.select("[title]").isNotEmpty() || line.contains("xsks") ||
                    Regex("^\\d.*\\[.+].*").matches(text)
                if (hasFields && !isField && text.isNotEmpty()) {
                    add(buffer.toString())
                    buffer = StringBuilder()
                    hasFields = false
                }
                buffer.append(line).append("<br>")
                hasFields = hasFields || isField
            }
            if (hasFields) add(buffer.toString())
        }
    }

    private fun weekday(text: String): Int? = when (text.trim().removePrefix("星期").removePrefix("周")) {
        "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6; "日", "天" -> 7
        else -> null
    }

    override fun generateCourseList(): List<JwCourse> {
        val courseList = arrayListOf<JwCourse>()
        val doc = Jsoup.parse(source)
        if (!usesSchoolRowMapping && tableName == "kbcontent") {
            JwQzModernHtml.parse(doc)?.let { return it }
        }
        // New QZ layouts (NNNU) use explicit sections and a detailed #timetable view.
        if (!usesSchoolRowMapping && tableName == "kbcontent" && doc.getElementById("kbtable") == null &&
            doc.selectFirst("table#timetable .kbcontent") != null
        ) return JwQzTimetableParser(source).generateCourseList()
        // T8: 找不到 #kbtable 抛 JwParseException(NO_TABLE_CONTAINER_MARKER)
        //   而不是静默 return courseList. 让 T9 区分"协议选错"和"课表页为空".
        val kbTable = doc.getElementById("kbtable") ?: doc.getElementById("timetable")
            ?: throw JwParseException(
                "页面缺少 #kbtable，可能未到达课表页/选错教务类型",
                attempts = listOf(
                    JwParserRegistry.ParserAttempt(
                        parserName = "JwQzParser",
                        type = JwProtocol.TYPE_QZ,
                        courseCount = 0,
                        confidence = confidence(),
                        matchedFeatures = matchedFeatures(),
                        exception = "NO_TABLE_CONTAINER_MARKER",
                    )
                ),
            )
        var nodeCount = 0
        var days = (1..7).toList()
        for (tr in kbTable.select("tr").filter { it.closest("table") == kbTable }) {
            val cells = tr.children().filter { it.tagName() == "td" || it.tagName() == "th" }
            val headerDays = cells.mapNotNull { weekday(it.text()) }
            if (headerDays.size == 7 && headerDays.toSet().size == 7) {
                days = headerDays
                continue
            }
            val tds = cells.filter { it.tagName() == "td" }
            if (tds.isEmpty()) continue
            val dayCells = if (tds.size >= 7) tds.takeLast(7) else tds.dropWhile {
                it.getElementsByClass(tableName).isEmpty() &&
                    Regex("^(?:第?[一二三四五六七八九十0-9-]+节|上午|下午|晚上)$").matches(it.text().trim())
            }
            if (dayCells.isEmpty() || dayCells.any { (it.attr("colspan").toIntOrNull() ?: 1) > 1 }) continue
            nodeCount++
            dayCells.forEachIndexed { index, td ->
                for (content in td.getElementsByClass(tableName)) {
                    if (content.parents().any { it != td && it.hasClass(tableName) }) continue
                    if (content.text().isBlank()) continue
                    for (block in courseBlocks(content.html())) convert(days[index], nodeCount, block, courseList)
                }
            }
        }
        return courseList
    }

    /**
     * T8 新增：confidence 评分。
     * 命中 #kbtable 与 font[title=老师/教室/周次(节次)] 三件套 = 100；
     * 仅命中 kbtable+kbcontent = 70；仅命中 kbtable = 30。
     */
    override fun confidence(): Int {
        return try {
            val doc = Jsoup.parse(source)
            if (!usesSchoolRowMapping && tableName == "kbcontent" && JwQzModernHtml.features(doc).isNotEmpty()) return 100
            if (doc.getElementById("kbtable") == null &&
                doc.selectFirst("table#timetable .kbcontent [title='周次(节次)']") != null
            ) return 100
            val kbtable = doc.getElementById("kbtable") ?: doc.getElementById("timetable") ?: return 0
            if (kbtable.getElementsByClass(tableName).any { content ->
                    content.select("[title='周次(节次)'], [title='周次'], [title='选课人数']").isNotEmpty()
                }) return 100
            val fontCount = doc.getElementsByAttributeValue("title", "老师").size +
                doc.getElementsByAttributeValue("title", "教师").size +
                doc.getElementsByAttributeValue("title", "教室").size +
                doc.getElementsByAttributeValue("title", "周次(节次)").size
            when {
                fontCount >= 3 -> 100
                kbtable.getElementsByClass(tableName).isNotEmpty() -> 70
                else -> 30
            }
        } catch (e: Exception) { 0 }
    }

    override fun matchedFeatures(): List<String> = try {
        val doc = Jsoup.parse(source)
        buildList {
            if (!usesSchoolRowMapping && tableName == "kbcontent") addAll(JwQzModernHtml.features(doc))
            if (doc.getElementById("kbtable") != null) add("id=kbtable")
            if (doc.selectFirst("table#timetable .$tableName") != null) add("id=timetable")
            if (doc.getElementsByClass(tableName).isNotEmpty()) add("class=$tableName")
            if (doc.getElementsByAttributeValue("title", "老师").isNotEmpty()) add("title=老师")
            if (doc.getElementsByAttributeValue("title", "教师").isNotEmpty()) add("title=教师")
            if (doc.getElementsByAttributeValue("title", "教室").isNotEmpty()) add("title=教室")
            if (doc.getElementsByAttributeValue("title", "上课地点").isNotEmpty()) add("title=上课地点")
            if (doc.getElementsByAttributeValue("title", "周次").isNotEmpty()) add("title=周次")
            if (doc.getElementsByAttributeValue("title", "节次").isNotEmpty()) add("title=节次")
            if (doc.getElementsByAttributeValue("title", "选课人数").isNotEmpty()) add("title=选课人数")
            if (doc.getElementsByAttributeValue("title", "周次(节次)").isNotEmpty()) add("title=周次(节次)")
        }
    } catch (e: Exception) { emptyList() }
}
