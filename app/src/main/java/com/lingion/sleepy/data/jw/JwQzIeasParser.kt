package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/**
 * 强智 iEAS 网络版 (`/ieas2.1/...`) 解析器。
 *
 * 学校样本: 北京航空航天大学 (jwxt.buaa.edu.cn:7001/ieas2.1),
 *           同形态可能包含其他 iEAS 部署高校 (BUAA SOP cross-validated 2026-09-06)。
 *
 * 数据来源: `GET /ieas2.1/kbcx/queryGrkb` 返回 **HTML 表格** (不是 JSON),
 *           5 仓 cross-verified (APassbyDreg/BUAA_JW_Utils + SE2020-TopUnderstanding/
 *           BUAA-Campus-Tools-Backend + fondoger/buaa-teacher-evaluation +
 *           Cauchy1412/BUAAGetCourse + KKRainbow/JWOneShotEval)。
 *
 * 解析约定：优先读取 iEAS 课表行的 data-* 字段；无字段时回退到五列文本
 * (课程名、教师、教室、周次、时间)。公开上游确认了 queryGrkb HTML endpoint，
 * 但没有提交可再分发的真实课表页面，因此测试 fixture 使用同一字段契约的合成页面。
 *
 * 上游参考 (代码自写, 不复用):
 *   - SE2020-TopUnderstanding/BUAA-Campus-Tools-Backend web.py (Python, BeautifulSoup)
 *   - APassbyDreg/BUAA_JW_Utils (Python, requests + cookie)
 */
class JwQzIeasParser(source: String) : JwParser(source) {

    override fun generateCourseList(): List<JwCourse> {
        val doc = Jsoup.parse(source)
        val table = doc.selectFirst("table#queryGrkb") ?: doc.selectFirst("table") ?: return emptyList()
        return table.select("tr").flatMap { row ->
            val cells = row.select("th,td").map { it.text().trim() }
            val name = row.attr("data-course-name").ifBlank { cells.getOrNull(0).orEmpty() }.trim()
            if (name.isBlank()) return@flatMap emptyList()
            val teacher = row.attr("data-teacher").ifBlank { cells.getOrNull(1).orEmpty() }.trim().cleanNull()
            val room = row.attr("data-room").ifBlank { cells.getOrNull(2).orEmpty() }.trim().cleanNull()
            val weeks = row.attr("data-weeks").ifBlank { cells.getOrNull(3).orEmpty() }
            val day = row.attr("data-day").toIntOrNull() ?: parseDay(cells.getOrNull(4).orEmpty())
            val parsedNodes = parseNodes(cells.getOrNull(4).orEmpty())
            val start = row.attr("data-start-node").toIntOrNull() ?: parsedNodes.first
            val end = row.attr("data-end-node").toIntOrNull() ?: parsedNodes.second
            if (day !in 1..7 || start < 1 || end < start) return@flatMap emptyList()
            parseWeeks(weeks).map { (sw, ew, type) -> JwCourse(name, room, teacher, day, start, end, sw, ew, type) }
        }
    }

    override fun confidence(): Int = if (Jsoup.parse(source).select("table#queryGrkb").isNotEmpty()) 90 else 0

    override fun matchedFeatures(): List<String> = buildList {
        if (Jsoup.parse(source).select("table#queryGrkb").isNotEmpty()) add("table#queryGrkb")
        if (source.contains("/ieas2.1/")) add("path:/ieas2.1/")
    }

    private fun parseDay(text: String): Int = Regex("周([一二三四五六日天1-7])").find(text)?.groupValues?.get(1)?.let {
        when (it) { "一", "1" -> 1; "二", "2" -> 2; "三", "3" -> 3; "四", "4" -> 4; "五", "5" -> 5; "六", "6" -> 6; else -> 7 }
    } ?: 0

    private fun parseNodes(text: String): Pair<Int, Int> {
        val values = Regex("第\\s*(\\d+)(?:\\s*[-~,，、]\\s*(\\d+))?").find(text)?.groupValues ?: return 0 to 0
        val start = values.getOrNull(1)?.toIntOrNull() ?: 0
        return start to (values.getOrNull(2)?.toIntOrNull() ?: start)
    }

    private fun parseWeeks(raw: String): List<Triple<Int, Int, Int>> {
        val normalized = raw.replace("周", "").replace('（', '(').replace('）', ')')
        return normalized.split(',', '，').mapNotNull { segment0 ->
            val segment = segment0.trim()
            if (segment.isBlank()) return@mapNotNull null
            val type = when { segment.contains("单") -> 1; segment.contains("双") -> 2; else -> 0 }
            val numbers = Regex("\\d+").findAll(segment).map { it.value.toInt() }.toList()
            if (numbers.isEmpty()) return@mapNotNull null
            if (segment.contains('-') && numbers.size >= 2) {
                val start = when (type) { 1 -> if (numbers[0] % 2 == 0) numbers[0] + 1 else numbers[0]; 2 -> if (numbers[0] % 2 == 1) numbers[0] + 1 else numbers[0]; else -> numbers[0] }
                Triple(start, maxOf(start, numbers[1]), type)
            } else Triple(numbers[0], numbers[0], type)
        }
    }

    private fun String.cleanNull(): String = takeUnless { it.equals("null", true) }.orEmpty()
}
