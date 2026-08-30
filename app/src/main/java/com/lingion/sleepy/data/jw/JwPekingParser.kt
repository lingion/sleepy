package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/**
 * 北京大学（PKU）解析器 — T3。
 *
 * 协议：JwProtocol.TYPE_PKU = "pku"
 * 上游：dIT8Zv/WakeupSchedule_BUPT (Apache-2.0) PekingParser.kt
 *
 * 页面结构：table[class=datagrid] > tbody > tr > 11 列 td
 *   tds[0]=课名  tds[4]=教师(整行共用)  tds[7]=时段(<br> 分隔多段)
 *   tds[8]=选课状态(含"未"跳过)
 * 时段块按空格切 token：
 *   timeInfo[0]="1~16周"  timeInfo[1]="周一1~2节"或"周一1~2节(单)"
 *   timeInfo[2]=教室（缺失时从 timeInfo[1] 括号内取）
 *
 * 对上游偏离：kbtable/tbody null 时返回空而非 NPE（pku_login.html 登录页）。
 */
class JwPekingParser(source: String) : JwParser(source) {

    override fun generateCourseList(): List<JwCourse> {
        val result = arrayListOf<JwCourse>()
        val doc = Jsoup.parse(source)
        val table = doc.selectFirst("table[class=datagrid]") ?: return result
        val tbody = table.selectFirst("tbody") ?: return result

        var teacher = ""
        for (tr in tbody.getElementsByTag("tr")) {
            val tds = tr.getElementsByTag("td")
            if (tds.size < 11) continue
            if (tds[8].text().contains('未')) continue

            val courseName = tds[0].text().trim()
            teacher = tds[4].text().trim()

            var startWeek = 1; var endWeek = 16
            var startNode = 1; var endNode = 2
            var type = 0; var day = 7

            val timeBlocks = tds[7].html().split("<br>")
            for (block in timeBlocks) {
                val timeInfo = Jsoup.parse(block).text().trim().split(' ').filter { it.isNotBlank() }
                if (timeInfo.size < 2) continue

                val token0 = timeInfo[0]
                if (token0.contains('~')) {
                    token0.substringBefore('~').filter { it.isDigit() }.toIntOrNull()?.let { startWeek = it }
                    token0.substringAfter('~').substringBefore('周').filter { it.isDigit() }
                        .toIntOrNull()?.let { endWeek = it }
                }
                type = when {
                    timeInfo[1].contains('单') -> 1
                    timeInfo[1].contains('双') -> 2
                    else -> 0
                }
                for ((index, s) in CHINESE_WEEK_LIST.withIndex()) {
                    if (index != 0 && timeInfo[1].contains(s)) { day = index; break }
                }
                NODE_PATTERN1.find(timeInfo[1])?.let { m ->
                    val v = m.value
                    v.substringBefore('~').filter { ch -> ch.isDigit() }.toIntOrNull()?.let { startNode = it }
                    v.substringAfter('~').substringBefore('节').filter { ch -> ch.isDigit() }
                        .toIntOrNull()?.let { endNode = it }
                }
                val room = if (timeInfo.size >= 3) timeInfo[2]
                           else timeInfo[1].substringAfter('(').substringBefore(')')

                result += JwCourse(
                    name = courseName, day = day,
                    startNode = startNode.coerceAtLeast(1),
                    endNode = endNode.coerceAtLeast(startNode),
                    startWeek = startWeek.coerceAtLeast(1),
                    endWeek = endWeek.coerceAtLeast(startWeek),
                    type = type, teacher = teacher, room = room
                )
            }
        }
        return result
    }

    companion object {
        /** 上游 Common.nodePattern1 原文，不可改字符 */
        private val NODE_PATTERN1 = Regex("""\d{1,2}[~]*\d*节""")
        private val CHINESE_WEEK_LIST = arrayOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
    }
}
