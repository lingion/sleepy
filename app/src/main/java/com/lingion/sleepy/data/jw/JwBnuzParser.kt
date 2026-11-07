package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/**
 * 北京师范大学珠海分校（BNUZ）解析器 — T3。
 *
 * 协议：JwProtocol.TYPE_BNUZ = "bnuz"
 * 上游：dIT8Zv/WakeupSchedule_BUPT (Apache-2.0) BNUZParser.kt
 *
 * 页面结构：table[id=table1]，列=星期(countDay 1..7)，行=节次。
 * 表头行 td="时间"/"星期一".. 被 OTHER_HEADER 跳过；纯数字 td 是节次行头。
 * 课程 td html 形如：
 *   <span>&nbsp;</span>课名<br>教师{1-16周}<br>教室(2节)<br>[教师{周次}<br>教室(2节)<br>]...
 *   substringAfter("</span>").substringBeforeLast("<br>").split("<br>")
 *   → infos[0]=课名, (infos[i], infos[i+1]) i=1,3,5.. = (教师+周次, 教室+节数) 对
 * 周次 item：含 '-' → 范围；单值 → start=end；含'单'→type=1 含'双'→type=2。
 *
 * 对上游偏离：table1 null 返回空；无 </span> 的 td 跳过；step 解析 toIntOrNull 防崩。
 */
class JwBnuzParser(source: String) : JwParser(source) {

    override fun generateCourseList(): List<JwCourse> {
        val result = arrayListOf<JwCourse>()
        val doc = Jsoup.parse(source)
        doc.selectFirst("#schedule tbody")?.let { return parseModern(it) }
        val table1 = doc.getElementById("table1") ?: return result
        val trs = table1.getElementsByTag("tr")

        for (tr in trs) {
            var node = 0
            var countFlag = false
            var countDay = 1
            val tds = tr.getElementsByTag("td")
            for (td in tds) {
                val courseValue = td.text().trim()
                if (courseValue in OTHER_HEADER) continue
                if (courseValue.isEmpty()) { if (countFlag) countDay++; continue }
                if (NODE_PATTERN.matches(courseValue)) { node = courseValue.toIntOrNull() ?: 0; countFlag = true; continue }

                val tdHtml = td.html()
                if (!tdHtml.contains("</span>")) { countDay++; continue }   // B2 修复
                val infos = tdHtml.substringAfter("</span>")
                    .substringBeforeLast("<br>")
                    .split("<br>")
                    .map { Jsoup.parseBodyFragment(it).text().trim() }
                    .filter { it.isNotBlank() }                              // B4 规范化
                if (infos.isEmpty()) { countDay++; continue }
                val courseName = infos[0]

                var i = 1
                while (i < infos.size) {
                    if (i + 1 >= infos.size) break
                    val teacherAndWeek = infos[i]
                    val roomStr = infos[i + 1]
                    if (!teacherAndWeek.contains('{') || !teacherAndWeek.contains('}')) { i += 2; continue }

                    val teacher = teacherAndWeek.substringBefore('{').trim()
                    val weekStr = teacherAndWeek.substringAfter('{').substringBefore('}').trim()

                    // B3 修复：无 "(N节)" 后缀时丢弃该 section（上游抛 NumberFormatException 整表崩）
                    val step = roomStr.substringAfterLast('(').substringBeforeLast('节')
                        .trim().toIntOrNull()
                    if (step == null || step <= 0 || node <= 0 || countDay !in 1..7 || node.toLong() + step - 1 > Int.MAX_VALUE) { i += 2; continue }
                    val room = roomStr.substringBeforeLast('(').trim()

                    for ((startWeek, endWeek, type) in weekRuns(weekStr)) {
                        result += JwCourse(
                            name = courseName, room = room, teacher = teacher,
                            day = countDay,
                            startNode = node, endNode = node + step - 1,
                            startWeek = startWeek,
                            endWeek = endWeek,
                            type = type
                        )
                    }
                    i += 2
                }
                countDay++
            }
        }
        return result
    }

    private fun parseModern(body: org.jsoup.nodes.Element): List<JwCourse> = buildList {
        val rows = body.children().filter { it.tagName() == "tr" }
        for ((index, row) in rows.withIndex()) {
            val day = index + 1
            if (day !in 1..7) continue
            for (course in row.select("td div[ng-repeat=\"c in s.info\"]")) {
                val name = course.children().firstOrNull()?.text()?.trim().orEmpty()
                if (name.isEmpty()) continue
                val paragraphs = course.selectFirst("li")?.select("p[ng-repeat]").orEmpty()
                for (paragraph in paragraphs) {
                    val text = paragraph.text().trim()
                    val schedule = Regex("""^第?(.+?)周第\s*([0-9]+)(?:\s*-\s*([0-9]+))?\s*节""")
                        .find(text) ?: continue
                    val first = schedule.groupValues[2].toIntOrNull() ?: continue
                    val last = schedule.groupValues[3].ifEmpty { schedule.groupValues[2] }.toIntOrNull() ?: continue
                    if (first <= 0 || last < first) continue
                    val room = Regex("""\[([^\[\]]*)]""").find(text)?.groupValues?.get(1).orEmpty()
                    val teacher = paragraph.select("span").lastOrNull()?.text()?.trim().orEmpty()
                    for ((from, to, type) in weekRuns(schedule.groupValues[1])) {
                        add(JwCourse(name, room, teacher, day, first, last, from, to, type))
                    }
                }
            }
        }
    }.distinct()

    private fun weekRuns(text: String): List<Triple<Int, Int, Int>> {
        val result = mutableListOf<Triple<Int, Int, Int>>()
        for (token in text.split(',', '，')) {
            val match = Regex("""第?\s*([0-9]+)(?:\s*-\s*([0-9]+))?\s*(单|双)?""")
                .matchEntire(token.replace("周", "").trim()) ?: return emptyList()
            val from = match.groupValues[1].toIntOrNull() ?: return emptyList()
            val to = match.groupValues[2].ifEmpty { match.groupValues[1] }.toIntOrNull() ?: return emptyList()
            if (from <= 0 || to < from) return emptyList()
            val type = when (match.groupValues[3]) { "单" -> 1; "双" -> 2; else -> 0 }
            result.add(Triple(from, to, type))
        }
        return result
    }

    companion object {
        private val NODE_PATTERN = Regex("""\d+""")
        private val OTHER_HEADER = setOf(
            "时间", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日",
            "早晨", "上午", "下午", "晚上"
        )
    }

    /** T8: #table1 + span 结构 = 100; es.bnuz = 90 */
    override fun confidence(): Int = try {
        val doc = org.jsoup.Jsoup.parse(source)
        val table1 = doc.getElementById("table1")
        when {
            doc.selectFirst("#schedule div[ng-repeat=\"c in s.info\"]") != null -> 100
            table1 != null && source.contains("</span>") -> 100
            source.contains("es.bnuz") -> 90
            table1 != null -> 70
            else -> 0
        }
    } catch (e: Exception) { 0 }

    override fun matchedFeatures(): List<String> = buildList {
        if (Jsoup.parse(source).selectFirst("#schedule div[ng-repeat=\"c in s.info\"]") != null) add("schedule-course-paragraphs")
        if (source.contains("table1")) add("id=table1")
        if (source.contains("</span>")) add("<span>课程名</span>")
        if (source.contains("es.bnuz")) add("URL=es.bnuz")
        if (source.contains("{") && source.contains("周")) add("{N-M周}")
    }
}
