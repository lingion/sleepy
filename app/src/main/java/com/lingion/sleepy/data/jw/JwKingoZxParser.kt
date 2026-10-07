// Kingosoft ZX (金智智慧树 zx 系列) parser — wakeup-parity-kingo-zx-2026-10-06 SOP v1.11
package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/**
 * Kingosoft ZX (金智智慧树 zx 系列) 解析器
 *
 * 协议指纹 (跨仓印证 2 仓 POSITIVE 共识 + 3 仓 INDIRECT):
 *  1. pageRpt 第一层: `#pageRpt > table` 容器选择器
 *  2. pageRpt 第二层: `#pageRpt > table > tbody > tr` 行选择器
 *  3. reportArea: `#reportArea` 降级容器选择器
 *  4. mytable: `table#mytable` 降级容器选择器
 *  5. xkinfo: `#xkinfo` 降级容器选择器
 *  6. 字段提取: 单元格文本按 <br> 拆分课程/教师/周次/教室
 *  7. 周次解析: 数字+ "-"+ 数字 区间, 逗号分隔多段
 *  8. 节次解析: 匹配 `\d+节` 或 `第\d+节`
 *  9. 教室提取: 周次行后的下一个单元格
 *
 * 4-helper fallback chain:
 *  ① pageRpt 第一层 (优先级最高)
 *  ② pageRpt 第二层 (嵌套表格场景)
 *  ③ reportArea (旧版页面)
 *  ④ mytable + xkinfo (极旧版兼容)
 *
 * 上游协议形态 (POSITIVE cross-verify):
 *  - WakeUp_KingoSuperParser.smali (3 链降级)
 *  - aischedule-lit-kingosoft (icepie, MIT)
 *  - oh-my-lit (Go 跨语言移植)
 *  - kingosoft_api (lizhengqiang)
 *
 * 设计决策:
 *  - 独立类, 复用 TYPE_KINGO_NEW (已在 JwProtocol 定义 TYPE_KINGO_ZX)
 *  - 4-helper fallback chain 降级策略, 每层失败自动尝试下一层
 */
class JwKingoZxParser(source: String) : JwParser(source) {

    companion object {
        /** pageRpt 第一层容器选择器 */
        const val SELECTOR_PAGE_RPT = "#pageRpt > table"
        
        /** pageRpt 第二层容器选择器 (嵌套表格) */
        const val SELECTOR_PAGE_RPT_NESTED = "#pageRpt table"
        
        /** reportArea 降级选择器 */
        const val SELECTOR_REPORT_AREA = "#reportArea"
        
        /** mytable 降级选择器 */
        const val SELECTOR_MYTABLE = "table#mytable"
        
        /** xkinfo 降级选择器 */
        const val SELECTOR_XKINFO = "#xkinfo"
        
        /** 节次匹配: 1节 / 第1节 */
        val RE_SECTION = Regex("(?:第)?(\\d+)节")
        
        /** 周次匹配: 1-16, 1,2,3 */
        val RE_WEEK = Regex("(\\d+)(?:-(\\d+))?(?:,(\\d+))*(?:-(\\d+))?")
        
        /** 默认最大周数 */
        const val DEFAULT_MAX_WEEK = 16
    }

    override fun generateCourseList(): List<JwCourse> {
        // 4-helper fallback chain
        val chain1 = parsePageRpt()
        if (chain1.isNotEmpty()) return chain1
        
        val chain2 = parsePageRptNested()
        if (chain2.isNotEmpty()) return chain2
        
        val chain3 = parseReportArea()
        if (chain3.isNotEmpty()) return chain3
        
        return parseMytableXkinfo()
    }

    override fun confidence(): Int {
        val features = matchedFeatures()
        return when {
            features.size >= 6 -> 90
            features.size >= 4 -> 70
            features.size >= 2 -> 40
            else -> 10
        }
    }

    override fun matchedFeatures(): List<String> {
        val doc = Jsoup.parse(source)
        val features = mutableListOf<String>()
        
        if (doc.selectFirst(SELECTOR_PAGE_RPT) != null) features.add("pageRpt-primary")
        if (doc.selectFirst(SELECTOR_PAGE_RPT_NESTED) != null) features.add("pageRpt-nested")
        if (doc.selectFirst(SELECTOR_REPORT_AREA) != null) features.add("reportArea")
        if (doc.selectFirst(SELECTOR_MYTABLE) != null) features.add("mytable")
        if (doc.selectFirst(SELECTOR_XKINFO) != null) features.add("xkinfo")
        if (source.contains("pageRpt")) features.add("pageRpt-text")
        
        return features
    }

    /** Chain ①: pageRpt 第一层 */
    private fun parsePageRpt(): List<JwCourse> {
        val doc = Jsoup.parse(source)
        val table = doc.selectFirst(SELECTOR_PAGE_RPT) ?: return emptyList()
        return parseTableRows(table, 1)
    }

    /** Chain ②: pageRpt 第二层 (嵌套表格) */
    private fun parsePageRptNested(): List<JwCourse> {
        val doc = Jsoup.parse(source)
        val tables = doc.select(SELECTOR_PAGE_RPT_NESTED)
        for (table in tables) {
            val result = parseTableRows(table, 1)
            if (result.isNotEmpty()) return result
        }
        return emptyList()
    }

    /** Chain ③: reportArea 降级 */
    private fun parseReportArea(): List<JwCourse> {
        val doc = Jsoup.parse(source)
        val container = doc.selectFirst(SELECTOR_REPORT_AREA) ?: return emptyList()
        val table = container.selectFirst("table") ?: return emptyList()
        return parseTableRows(table, 1)
    }

    /** Chain ④: mytable + xkinfo 极旧版 */
    private fun parseMytableXkinfo(): List<JwCourse> {
        val doc = Jsoup.parse(source)
        val table = doc.selectFirst(SELECTOR_MYTABLE) ?: doc.selectFirst(SELECTOR_XKINFO) ?: return emptyList()
        return parseTableRows(table, 1)
    }

    private fun parseTableRows(table: org.jsoup.nodes.Element, dayOffset: Int): List<JwCourse> {
        val out = mutableListOf<JwCourse>()
        val rows = table.select("tbody tr, tr")
        
        for ((rowIdx, row) in rows.withIndex()) {
            val cells = row.select("td, th")
            if (cells.size < 4) continue
            
            for ((colIdx, cell) in cells.withIndex()) {
                if (colIdx == 0) continue
                val html = cell.html()
                val text = cell.text().trim()
                if (text.isBlank() || text.contains("节") == false) continue
                
                // 提取课程信息 - 按 html <br> 拆分
                val lines = html.split(Regex("(?i)<br\\s*/?>"))
                    .map { Jsoup.parse(it).text().trim() }
                    .filter { it.isNotBlank() }
                if (lines.isEmpty()) continue
                
                val name = lines[0]
                if (name.isBlank()) continue
                
                val teacher = lines.getOrNull(1)?.trim() ?: ""
                val room = lines.getOrNull(2)?.trim() ?: ""
                
                val weekLine = lines.find { it.contains("周") } ?: text
                val weekInfo = parseWeeks(weekLine)
                if (weekInfo.first < 1) continue
                
                // 解析节次
                val sectionMatch = RE_SECTION.find(weekLine.ifEmpty { text }) ?: RE_SECTION.find(text)
                val section = sectionMatch?.groupValues?.get(1)?.toIntOrNull() ?: continue
                
                val day = ((colIdx % 7) + 1).coerceIn(1, 7)
                val startNode = section
                val endNode = section
                
                out.add(JwCourse(
                    name = name,
                    room = room,
                    teacher = teacher,
                    day = day,
                    startNode = startNode,
                    endNode = endNode,
                    startWeek = weekInfo.first,
                    endWeek = weekInfo.second,
                    type = weekInfo.third
                ))
            }
        }
        return out
    }

    private fun parseWeeks(text: String): Triple<Int, Int, Int> {
        val weekMatches = RE_WEEK.findAll(text)
        var startWeek = DEFAULT_MAX_WEEK
        var endWeek = 1
        var type = 0
        
        for (match in weekMatches) {
            val w1 = match.groupValues[1].toIntOrNull() ?: continue
            val w2 = match.groupValues[2].toIntOrNull() ?: w1
            startWeek = minOf(startWeek, w1)
            endWeek = maxOf(endWeek, w2)
        }
        
        if (text.contains("单")) type = 1
        else if (text.contains("双")) type = 2
        
        if (startWeek > endWeek) return Triple(0, 0, 0)
        return Triple(startWeek, endWeek, type)
    }
}
