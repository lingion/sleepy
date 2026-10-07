// SCAU (华南农业大学 jwxt.scau.edu.cn) parser — wakeup-parity-scau-2026-10-06 SOP v1.11
package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/**
 * 华南农业大学 (SCAU) 教务解析器 — jwxt.scau.edu.cn
 *
 * 协议指纹 (跨仓印证 2 仓 POSITIVE 共识 + 8 仓 INDIRECT):
 *  1. 主路径选择器: `table[border=1]` (WakeUp o00OOO00.smali:20, 无 bordercolor 限定)
 *  2. 降级路径选择器: `table[border=1][bordercolor=#000000]` (WakeUp SCAUParser.smali:142)
 *  3. 单元选择器: `td[valign=top]` (WakeUp o00OOO00:61 / SCAUParser:200)
 *  4. 字段分隔: `<br>` split (WakeUp o00OOO00:95 / SCAUParser:234)
 *  5. 节次↔节点 (主路径): startNode=trIndex*2+1, endNode=trIndex*2+2 (WakeUp o00OOO00:181-185)
 *  6. 节次↔节点 (降级): startNode=sectionNum*2-1, endNode=sectionNum*2 (WakeUp SCAUParser:629-633)
 *  7. 主路径字段: line0=name / line1=teacher / line2=weeks / line3=room
 *  8. 降级路径字段: line0="name:teacher" (全角冒号 split) / line1=weeks / line2=room
 *  9. 单/双周: () 半角 / () 全角 / "单" / "双" substring → type 1/2 (Sleepy 通用)
 *
 * 上游协议形态 (POSITIVE cross-verify):
 *  - WakeUp SCAUParser.smali (1489 行) + o00OOO00.smali (1737 行) — 主备双路径
 *  - greyovo/AIScheduleSCAU (MIT) — 13 大节映射 + cheerio + el-table__body-wrapper (INV-12)
 *  - greyovo/ScheduleXParser_SCAU (MIT) — 同作者 Java SCAU 适配, 协议同源
 *
 * 设计决策 (SOP §7 Phase 2 决议):
 *  - **独立类, 不复用 JwQzParser**: Sleepy JwQzParser 用 `#kbtable` 容器 + `kbcontent` 单元格,
 *    SCAU WakeUp 用 `table[border=1][bordercolor=#000000]` 容器 + `td[valign=top]` 单元格,
 *    选择器策略完全不同. findings.json landing_decision 选定 Option A 独立类.
 *  - **主备双路径**: 实现 o00OOO00 主路径 + SCAUParser 降级路径, 后者通过 `<head>/</head>` 切片
 *    隔离老视图 HTML, 与 findings.json decompiled_kotlin 一致.
 */
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

        /** 默认学期最大周数 (WakeUp 默认 16, SCAU 强智新教务常见 18, 走保守 16)。 */
        const val DEFAULT_MAX_WEEK = 16
    }

    override fun generateCourseList(): List<JwCourse> {
        val primary = parsePrimaryStrategy()
        return if (primary.isNotEmpty()) primary else parseFallbackStrategy()
    }

    /**
     * 主路径 (WakeUp o00OOO00 形态, 强智新教务 lyuap HTML grid):
     * - 选 `table[border=1]` (INV-3, 无 bordercolor)
     * - td[valign=top] 文本按 `<br>` 拆多行字段 (INV-5 + INV-7)
     * - 字段顺序: line0=课程名, line1=教师, line2=周次, line3=教室 (WakeUp o00OOO00:197-219)
     * - 节次 (sectionFound 标志) 仅做 "是否为有效课程行" 检测, 节点索引用 trIndex * 2 + 1
     *   (WakeUp o00OOO00:181-185, INV-1 检测 + trIndex 索引)
     * - 单/双周 (line0+line1+line2 拼串后查 "(单)"/"(双)"/"（单）"/"（双）" 半角/全角) → type
     * - 周次: 第 2 行按 "," 拆, 每段用 `[\d,\-]+` 过滤 (INV-2), 含 `-` 视为区间
     */
    private fun parsePrimaryStrategy(): List<JwCourse> {
        val doc = Jsoup.parse(source)
        val table = doc.selectFirst(SELECTOR_PRIMARY_TABLE) ?: return emptyList()
        val trList = table.select("tr")
        val out = mutableListOf<JwCourse>()

        for ((trIndex, tr) in trList.withIndex()) {
            val tdList = tr.select(SELECTOR_TD_VALIGN_TOP)
            for ((tdIndex, td) in tdList.withIndex()) {
                val day = tdIndex + 1
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
                val joinedText = parsed.joinToString("")
                val type = when {
                    joinedText.contains("(单)") || joinedText.contains("（单）") -> 1
                    joinedText.contains("(双)") || joinedText.contains("（双）") -> 2
                    else -> 0
                }

                // 主路径字段: name/teacher/weeks/room — line0=name, line1=teacher, line2=weeks, line3=room
                val name = parsed.getOrNull(0)?.trim() ?: ""
                val teacher = parsed.getOrNull(1)?.trim() ?: ""
                val (sw, ew) = parseWeekRange(parsed, weeksLineIndex = 2)
                val (sw2, ew2) = JwParity.adjustedRange(sw, ew, type)
                val room = parsed.getOrNull(3)?.trim() ?: ""

                out += JwCourse(
                    name = name,
                    teacher = teacher,
                    room = room,
                    day = day,
                    startNode = trIndex * 2 + 1,
                    endNode = trIndex * 2 + 2,
                    startWeek = sw2,
                    endWeek = ew2,
                    type = type,
                )
            }
        }
        return out
    }

    /**
     * 降级路径 (WakeUp SCAUParser 形态, 老视图残留):
     * - INV-6: 切片 `<head>` / `</head>` 拆分
     * - INV-4: 选 `table[border=1][bordercolor=#000000]`
     * - tr 列表 `chunked(2)`: 第 1 行=节次标题, 第 2 行=数据
     * - td[valign=top] 文本按 `<br>` 拆字段 (INV-5 + INV-7)
     * - INV-8/13: line0=`课程名:教师` (按全角冒号 split) — 否则 teacher=""
     * - line1=周次, line2=教室
     * - INV-11: 单/双周 substring ("单"/"双") → type 1/2
     * - 节次: 从 trSection 第 tdIdx 行的 `(\d+)节)` 提取 → sectionNum
     * - INV-node: startNode=sectionNum*2-1, endNode=sectionNum*2 (与主路径 trIndex×2 等价)
     */
    private fun parseFallbackStrategy(): List<JwCourse> {
        val out = mutableListOf<JwCourse>()
        // INV-6: <head>/</head> 切片
        val segments = source.split("<head>", "</head>").filter { it.isNotBlank() }
        for (segment in segments) {
            val doc = Jsoup.parse(segment)
            val table = doc.selectFirst(SELECTOR_FALLBACK_TABLE) ?: continue
            val trList = table.select("tr")
            val trPairs = trList.chunked(2)
            for (pair in trPairs) {
                if (pair.size < 2) continue
                val trSection = pair[0]
                val trData = pair[1]
                val tdSectionList = trSection.select(SELECTOR_TD_VALIGN_TOP)
                val tdDataList = trData.select(SELECTOR_TD_VALIGN_TOP)
                for ((tdIdx, tdData) in tdDataList.withIndex()) {
                    val day = tdIdx + 1
                    val lines = tdData.html().split("<br>")
                    val parsed = lines.map { Jsoup.parse(it).text() }

                    // INV-11: 单/双周 substring ("单"/"双") → type 1/2
                    val checkText = parsed.joinToString("")
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

                    val (sw, ew) = parseWeekRange(parsed, weeksLineIndex = 1)
                    val (sw2, ew2) = JwParity.adjustedRange(sw, ew, type)
                    val room = parsed.getOrNull(2)?.trim() ?: ""

                    // 节次: trSection 第 tdIdx 行的 (\d+)节) 提取
                    val tdSection = tdSectionList.getOrNull(tdIdx) ?: continue
                    val sectionMatch = RE_SECTION.find(tdSection.text())
                    val sectionNum = sectionMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    if (sectionNum == 0) continue

                    out += JwCourse(
                        name = name,
                        teacher = teacher,
                        room = room,
                        day = day,
                        startNode = sectionNum * 2 - 1,
                        endNode = sectionNum * 2,
                        startWeek = sw2,
                        endWeek = ew2,
                        type = type,
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
        val weekText = parsed.getOrNull(weeksLineIndex) ?: ""
        val segments = weekText.split(",").map { it.trim() }
        val ranges = mutableListOf<IntRange>()
        for (seg in segments) {
            // INV-2: 周次片段过滤 (子串匹配, 容忍 "1-8周" 后缀)
            if (!RE_WEEK_SEGMENT.containsMatchIn(seg)) continue
            // 去掉 "周" 后缀
            val cleanSeg = seg.replace("周", "").trim()
            if ("-" in cleanSeg) {
                val parts = cleanSeg.split("-").mapNotNull { it.trim().toIntOrNull() }
                if (parts.size == 2 && parts[1] >= parts[0] && parts[0] > 0) {
                    ranges.add(parts[0]..parts[1])
                }
            } else {
                val w = cleanSeg.toIntOrNull() ?: continue
                if (w > 0) ranges.add(w..w)
            }
        }
        if (ranges.isEmpty()) return 1 to DEFAULT_MAX_WEEK
        return ranges.first().first to ranges.last().last
    }

    override fun confidence(): Int {
        val doc = Jsoup.parse(source)
        var hits = 0
        if (doc.selectFirst(SELECTOR_FALLBACK_TABLE) != null) hits += 3
        if (doc.selectFirst(SELECTOR_PRIMARY_TABLE) != null) hits += 2
        if (doc.select(SELECTOR_TD_VALIGN_TOP).isNotEmpty()) hits += 2
        if (RE_SECTION.containsMatchIn(source)) hits += 2
        if (source.contains("<head>") && source.contains("</head>")) hits++
        if (source.contains("：")) hits++
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
            if (doc.selectFirst(SELECTOR_PRIMARY_TABLE) != null) add("scau:border-primary")
            if (doc.select(SELECTOR_TD_VALIGN_TOP).isNotEmpty()) add("scau:valign-top-td")
            if (RE_SECTION.containsMatchIn(source)) add("scau:section-regex")
            if (source.contains("<head>") && source.contains("</head>")) add("scau:head-slice")
            if (source.contains("：")) add("scau:fullwidth-colon")
            if (RE_PARITY_HALF.containsMatchIn(source)) add("scau:parity-marker")
            if (RE_WEEK_SEGMENT.containsMatchIn(source)) add("scau:week-segment")
        }
    }
}
