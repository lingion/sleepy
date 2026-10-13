// SCAU (华南农业大学 jwxt.scau.edu.cn) parser — wakeup-parity-scau-2026-10-06 SOP v1.11
package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/** SCAU timetable HTML parser. Three independently gated shapes: pinned greyovo Njw2017
 * Element UI DOM, WakeUp border-grid DOM, and WakeUp old bordercolor row pairs.
 * Evidence and limitations: docs/scau-cross-verify-2026-10-13/. */
class JwScauParser(source: String) : JwParser(source) {

    companion object {
        /** INV-1: 节次 regex (匹配如 `5节)`, `12节)`)。 */
        val RE_SECTION = Regex("(\\d+)节\\)")

        /** INV-2: 周次片段过滤 (纯数字/逗号/横线)。 */
        val RE_WEEK_SEGMENT = Regex("[\\d,\\-]+")

        /** INV-9/10: 半角/全角单/双周标注 (用于 confidence/features 统计)。 */
        val RE_PARITY_HALF = Regex("[(（](单|双)[)）]")

        /** INV-3: 主路径容器选择器 (无 bordercolor 限定)。 */
        const val SELECTOR_PRIMARY_TABLE = "table[border=1]"

        /** INV-4: 降级路径容器选择器 (含 bordercolor=#000000)。 */
        const val SELECTOR_FALLBACK_TABLE = "table[border=1][bordercolor=#000000]"

        /** INV-5: 单元选择器。 */
        const val SELECTOR_TD_VALIGN_TOP = "td[valign=top]"

    }

    override fun generateCourseList(): List<JwCourse> {
        val elementUi = parseElementUiStrategy()
        if (elementUi.isNotEmpty()) return elementUi
        val primary = parsePrimaryStrategy()
        return if (primary.isNotEmpty()) primary else parseFallbackStrategy()
    }

    /** Pinned greyovo/ScheduleXParser_SCAU sample: Element UI's seven weekday cells follow a section cell. */
    private fun parseElementUiStrategy(): List<JwCourse> {
        val out = mutableListOf<JwCourse>()
        val doc = Jsoup.parse(source)
        for (table in doc.select(".el-table__body-wrapper table.el-table__body")) {
            for ((rowIndex, tr) in table.select("tbody > tr").withIndex()) {
                val cells = tr.children().filter { it.normalName() == "td" }
                if (cells.size < 8) continue
                val rowLabel = Regex("^([0-9]{1,2})(?: *[-－~至] *([0-9]{1,2}))?节$").matchEntire(cells[0].text().trim())
                val defaults = arrayOf(1 to 2, 3 to 5, 6 to 7, 8 to 9, 10 to 12, 13 to 15)
                val rowNodes = if (rowLabel != null) {
                    val start = rowLabel.groupValues[1].toInt()
                    start to (rowLabel.groupValues[2].toIntOrNull() ?: start)
                } else if (cells[0].text().isBlank()) defaults.getOrNull(rowIndex) else null
                for (day in 1..7) {
                    val cell = cells[day]
                    for (block in cell.select("div[style]").filter {
                        Regex("(?:^|;) *position *: *relative *(?:;|$)", RegexOption.IGNORE_CASE)
                            .containsMatchIn(it.attr("style"))
                    }) {
                        val fields = block.children().filter { it.normalName() == "div" }
                        if (fields.size < 8) continue
                        val name = fields[3].text().trim().removePrefix("*").trim()
                        val teacher = fields[4].text().trim()
                        val timing = fields[6].text().trim()
                        val weekText = Regex("[（(]([^）)]+)[）)]").find(timing)?.groupValues?.get(1).orEmpty()
                        val room = fields[7].text().trim()
                        if (name.isBlank() || weekText.isBlank()) continue
                        val parity = when {
                            weekText.contains("单") -> 1
                            weekText.contains("双") -> 2
                            else -> 0
                        }
                        val section = Regex("^([0-9]{1,2})(?: *[-－~至] *([0-9]{1,2}))?节$")
                            .matchEntire(timing.substringBefore('(').substringBefore('（').trim())
                        val nodes = if (section != null) {
                            val start = section.groupValues[1].toInt()
                            start to (section.groupValues[2].toIntOrNull() ?: start)
                        } else if (timing.contains('节')) continue else rowNodes ?: continue
                        if (nodes.first !in 1..30 || nodes.second !in nodes.first..30) continue
                        for ((start, end, type) in parseWeekRuns(weekText, parity)) {
                            out += JwCourse(name, room, teacher, day, nodes.first, nodes.second, start, end, type)
                        }
                    }
                }
            }
        }
        return out
    }

    /**
     * 主路径 (WakeUp o00OOO00 形态, 强智新教务 lyuap HTML grid):
     * - 选 `table[border=1]` (INV-3, 无 bordercolor)
     * - td[valign=top] 文本按 `<br>` 拆多行字段 (INV-5 + INV-7)
     * - 字段顺序: line0=课程名, line1=教师, line2=周次, line3=教室 (WakeUp o00OOO00:197-219)
     * - 数据行按原位置计数，保留空课行对节次的影响
     * - 单/双周 (line0+line1+line2 拼串后查 "(单)"/"(双)"/"（单）"/"（双）" 半角/全角) → type
     * - 周次: 第 2 行按 "," 拆, 每段用 `[\d,\-]+` 过滤 (INV-2), 含 `-` 视为区间
     */
    private fun parsePrimaryStrategy(): List<JwCourse> {
        val doc = Jsoup.parse(source)
        val out = mutableListOf<JwCourse>()
        for (table in doc.select(SELECTOR_PRIMARY_TABLE).filter { !it.hasAttr("bordercolor") }) {
          var courseRow = 0
          for (tr in table.select("tr")) {
            val cells = tr.children().filter { it.normalName() == "td" }
            if (cells.isEmpty() || tr.children().any { it.normalName() == "th" }) continue
            for ((tdIndex, td) in cells.withIndex()) {
                if (!td.attr("valign").equals("top", ignoreCase = true)) continue
                val hasSectionColumn = cells.size > 7 || (cells.size != 7 && cells.first().text().isBlank() && !cells.first().hasAttr("valign"))
                val day = tdIndex + if (hasSectionColumn) 0 else 1
                if (day !in 1..7) continue
                val lines = td.html().split("<br>")
                val parsed = lines.map { Jsoup.parse(it).text() }

                // INV-1: 节次 regex 扫描 (sectionFound 仅做 valid-course 检测标志)
                var sectionFound = false
                for (line in parsed) {
                    val match = RE_SECTION.find(line)
                    if (match != null) {
                        sectionFound = true
                        break
                    }
                }
                if (!sectionFound) continue

                // INV-9/10: 单/双周检测 (joinedText 拼 cell 所有 line)
                val joinedText = parsed.getOrNull(2).orEmpty()
                val type = when {
                    joinedText.contains("(单)") || joinedText.contains("（单）") -> 1
                    joinedText.contains("(双)") || joinedText.contains("（双）") -> 2
                    else -> 0
                }

                // 主路径字段: name/teacher/weeks/room — line0=name, line1=teacher, line2=weeks, line3=room
                val name = parsed.getOrNull(0)?.trim() ?: ""
                val teacher = parsed.getOrNull(1)?.trim() ?: ""
                val runs = parseWeekRuns(parsed.getOrNull(2).orEmpty(), type)
                if (name.isBlank() || runs.isEmpty()) continue
                val room = parsed.getOrNull(3)?.trim() ?: ""
                for ((sw, ew, parity) in runs) out += JwCourse(
                    name = name,
                    teacher = teacher,
                    room = room,
                    day = day,
                    startNode = courseRow * 2 + 1,
                    endNode = courseRow * 2 + 2,
                    startWeek = sw,
                    endWeek = ew,
                    type = parity,
                )
            }
            courseRow++
          }
        }
        return out
    }

    /**
     * 降级路径 (WakeUp SCAUParser 形态, 老视图残留):
     * - 直接选择 old-view 容器，不依赖 head 标记
     * - INV-4: 选 `table[border=1][bordercolor=#000000]`
     * - 配对含节次的标题行与紧随的数据行，跳过其他表头
     * - td[valign=top] 文本按 `<br>` 拆字段 (INV-5 + INV-7)
     * - INV-8/13: line0=`课程名:教师` (按全角冒号 split) — 否则 teacher=""
     * - line1=周次, line2=教室
     * - 奇偶仅取周次文本，课程名与教师不参与判定
     * - 节次: 从 trSection 第 tdIdx 行的 `(\d+)节)` 提取 → sectionNum
     * - INV-node: startNode=sectionNum*2-1, endNode=sectionNum*2 (与主路径 trIndex×2 等价)
     */
    private fun parseFallbackStrategy(): List<JwCourse> {
        val out = mutableListOf<JwCourse>()
        for (table in Jsoup.parse(source).select(SELECTOR_FALLBACK_TABLE)) {
            val trList = table.select("tr")
            for (rowIndex in 0 until trList.size - 1) {
                val trSection = trList[rowIndex]
                if (trSection.children().any { it.normalName() == "th" }) continue
                if (trSection.select("td").none { RE_SECTION.containsMatchIn(it.text()) }) continue
                val trData = trList[rowIndex + 1]
                val tdSectionList = trSection.children().filter { it.normalName() == "td" }
                val tdDataList = trData.children().filter { it.normalName() == "td" }
                for ((tdIdx, tdData) in tdDataList.withIndex()) {
                    if (!tdData.attr("valign").equals("top", ignoreCase = true)) continue
                    val day = tdIdx + if (tdDataList.size > 7) 0 else 1
                    if (day !in 1..7) continue
                    val lines = tdData.html().split("<br>")
                    val parsed = lines.map { Jsoup.parse(it).text() }

                    // INV-11: 单/双周 substring ("单"/"双") → type 1/2
                    val checkText = parsed.getOrNull(1).orEmpty()
                    val type = when {
                        "单" in checkText -> 1
                        "双" in checkText -> 2
                        else -> 0
                    }

                    // 降级路径字段: line0=name:teacher (全角冒号 split), line1=weeks, line2=room
                    val firstLine = parsed.getOrNull(0) ?: ""
                    val (name, teacher) = if ("：" in firstLine) {
                        val parts = firstLine.split("：", limit = 2)
                        (parts.getOrNull(0)?.trim() ?: "") to (parts.getOrNull(1)?.trim() ?: "")
                    } else {
                        firstLine.trim() to ""
                    }

                    val runs = parseWeekRuns(parsed.getOrNull(1).orEmpty(), type)
                    if (name.isBlank() || runs.isEmpty()) continue
                    val room = parsed.getOrNull(2)?.trim() ?: ""

                    // 节次: trSection 第 tdIdx 行的 (\d+)节) 提取
                    val tdSection = tdSectionList.getOrNull(tdIdx) ?: continue
                    val sectionMatch = RE_SECTION.find(tdSection.text())
                    val sectionNum = sectionMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    if (sectionNum !in 1..30) continue

                    for ((sw, ew, parity) in runs) out += JwCourse(
                        name = name,
                        teacher = teacher,
                        room = room,
                        day = day,
                        startNode = sectionNum * 2 - 1,
                        endNode = sectionNum * 2,
                        startWeek = sw,
                        endWeek = ew,
                        type = parity,
                    )
                }
            }
        }
        return out
    }

    /**
     * 解析周次区间 (主路径 line2 / 降级 line1):
     * - 按 "," 拆, 每段先按 INV-2 过滤 (纯数字/逗号/横线)
     * - 含 `-` 视为区间, 否则视为单周
     * - 取首段起点 + 末段终点 (WakeUp o00OOO00:251-272 / SCAUParser:392-413)
     */
    internal fun parseWeekRange(parsed: List<String>, weeksLineIndex: Int): Pair<Int, Int> {
        val runs = parseWeekRuns(parsed.getOrNull(weeksLineIndex).orEmpty())
        return if (runs.isEmpty()) 0 to 0 else runs.first().first to runs.last().second
    }

    /** Keep sparse advertised weeks as separate runs. */
    internal fun parseWeekRuns(text: String, parity: Int = 0): List<Triple<Int, Int, Int>> {
        val runs = mutableListOf<Triple<Int, Int, Int>>()
        for (token in text.split(',', '，', '、', ';', '；')) {
            val explicit = when {
                token.contains("单") -> 1
                token.contains("双") -> 2
                else -> 0
            }
            val tokenParity = if (explicit != 0) explicit else if (text.split(',', '，', '、', ';', '；').size == 1) parity else 0
            val clean = token.replace("（单）", "").replace("（双）", "")
                .replace("(单)", "").replace("(双)", "")
                .replace("单周", "").replace("双周", "").replace("周", "")
            val match = Regex("""^ *([0-9]{1,2})(?: *[-－—~至] *([0-9]{1,2}))? *$""").matchEntire(clean) ?: continue
            val start = match.groupValues[1].toInt()
            val end = match.groupValues[2].toIntOrNull() ?: start
            if (start !in 1..30 || end !in start..30) continue
            val weeks = (start..end).filter { tokenParity == 0 || it % 2 == tokenParity % 2 }
            if (weeks.isEmpty()) continue
            var first = weeks.first()
            var last = first
            for (week in weeks.drop(1)) {
                val gap = week - last
                if (gap != 1 && !(tokenParity != 0 && gap == 2)) {
                    runs += Triple(first, last, tokenParity)
                    first = week
                }
                last = week
            }
            runs += Triple(first, last, tokenParity)
        }
        return runs.sortedWith(compareBy({ it.first }, { it.second })).fold(mutableListOf<Triple<Int, Int, Int>>()) { merged, run ->
            val last = merged.lastOrNull()
            val step = if (run.third == 0) 1 else 2
            if (last != null && last.third == run.third && run.first <= last.second + step)
                merged[merged.lastIndex] = Triple(last.first, maxOf(last.second, run.second), run.third)
            else merged += run
            merged
        }
    }

    override fun confidence(): Int {
        val doc = Jsoup.parse(source)
        var hits = 0
        val hasPrimary = doc.select(SELECTOR_PRIMARY_TABLE).any { !it.hasAttr("bordercolor") }
        val hasFallback = doc.selectFirst(SELECTOR_FALLBACK_TABLE) != null
        if (hasFallback) hits += 3
        if (hasPrimary) hits += 2
        if (doc.select("table[border=1] td[valign=top]").isNotEmpty()) hits += 2
        if (doc.select("table[border=1] td").any { RE_SECTION.containsMatchIn(it.text()) }) hits += 2
        if (source.contains("<head>") && source.contains("</head>")) hits++
        if (doc.select(SELECTOR_FALLBACK_TABLE).any { it.text().contains("：") }) hits++
        return when {
            hits >= 8 -> 95
            hits >= 6 -> 80
            hits >= 4 -> 60
            hits >= 2 -> 30
            else -> 0
        }
    }

    override fun matchedFeatures(): List<String> {
        val doc = Jsoup.parse(source)
        return buildList {
            if (doc.selectFirst(SELECTOR_FALLBACK_TABLE) != null) add("scau:bordercolor-fallback")
            if (doc.select(SELECTOR_PRIMARY_TABLE).any { !it.hasAttr("bordercolor") }) add("scau:border-primary")
            if (doc.select(SELECTOR_TD_VALIGN_TOP).isNotEmpty()) add("scau:valign-top-td")
            if (RE_SECTION.containsMatchIn(source)) add("scau:section-regex")
            if (source.contains("<head>") && source.contains("</head>")) add("scau:head-slice")
            if (source.contains("：")) add("scau:fullwidth-colon")
            if (RE_PARITY_HALF.containsMatchIn(source)) add("scau:parity-marker")
            if (RE_WEEK_SEGMENT.containsMatchIn(source)) add("scau:week-segment")
        }
    }
}
