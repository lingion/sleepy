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
        val table1 = doc.getElementById("table1") ?: return result
        val trs = table1.getElementsByTag("tr")

        var node = 0
        for (tr in trs) {
            var countFlag = false
            var countDay = 1
            val tds = tr.getElementsByTag("td")
            for (td in tds) {
                val courseValue = td.text().trim()
                if (courseValue in OTHER_HEADER) continue
                if (courseValue.isEmpty()) { if (countFlag) countDay++; continue }
                if (NODE_PATTERN.matches(courseValue)) { node = courseValue.toInt(); countFlag = true; continue }

                val tdHtml = td.html()
                if (!tdHtml.contains("</span>")) { countDay++; continue }   // B2 修复
                val infos = tdHtml.substringAfter("</span>")
                    .substringBeforeLast("<br>")
                    .split("<br>")
                    .map { it.trim() }
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
                    if (step == null) { i += 2; continue }   // continue 作用于 while：跳过此 section 继续扫
                    val room = roomStr.substringBeforeLast('(').trim()

                    for (wp in weekStr.split(',')) {
                        val item = wp.trim()
                        if (item.isEmpty()) continue
                        val type = when {
                            item.contains('单') -> 1
                            item.contains('双') -> 2
                            else -> 0
                        }
                        val startWeek: Int
                        val endWeek: Int
                        if (item.contains('-')) {
                            startWeek = item.substringBefore('-').filter { it.isDigit() }.toIntOrNull() ?: continue
                            endWeek = item.substringAfter('-').filter { it.isDigit() }.toIntOrNull() ?: startWeek
                        } else {
                            startWeek = item.filter { it.isDigit() }.toIntOrNull() ?: continue
                            endWeek = startWeek
                        }
                        result += JwCourse(
                            name = courseName, room = room, teacher = teacher,
                            day = countDay.coerceIn(1, 7),
                            startNode = node, endNode = node + step - 1,
                            startWeek = startWeek.coerceAtLeast(1),
                            endWeek = endWeek.coerceAtLeast(startWeek),
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

    companion object {
        private val NODE_PATTERN = Regex("""\d+""")
        private val OTHER_HEADER = setOf(
            "时间", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日",
            "早晨", "上午", "下午", "晚上"
        )
    }
}
