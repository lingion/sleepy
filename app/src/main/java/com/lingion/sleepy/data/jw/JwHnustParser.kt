package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/**
 * 湖南科技大学 / 湖南科技大学潇湘学院 / 东北石油大学（HNUST）解析器 — T3。
 *
 * 协议：JwProtocol.TYPE_HNUST = "hnust"
 * 上游：dIT8Zv/WakeupSchedule_BUPT (Apache-2.0) HNUSTParser.kt
 *
 * 页面结构：table[id=kbtable]，行=节次，列=星期；课程数据在 td 内的 div 里：
 *   <div id="1-1" style="display: none;">课名<br>教师<br>1-16周<br>教室</div>
 *   div id 前段 N = 大节序号 → startNode = N*2-1, endNode = N*2（第 1 大节 = 1-2 节）
 *
 * @param oldQzType 0=湖南科技大学/潇湘学院：style="display: none;" 的 div 才是课
 *                  1=东北石油大学：style != "display: none;" 的 div 才是课
 *
 * 对上游偏离：kbtable null 返回空；style 比较去空格+小写（兼容 "display:none;" 与 "display: none;"）；
 * split 越界保护；周次/id 数字解析 toIntOrNull 防崩。
 */
class JwHnustParser(source: String, private val oldQzType: Int = 0) : JwParser(source) {

    override fun generateCourseList(): List<JwCourse> {
        val courseList = arrayListOf<JwCourse>()
        val doc = Jsoup.parse(source)
        val kbtable = doc.getElementById("kbtable") ?: return courseList   // H1
        val trs = kbtable.getElementsByTag("tr")

        for (tr in trs) {
            val tds = tr.getElementsByTag("td")
            if (tds.isEmpty()) continue

            var day = -1
            for (td in tds) {
                day++
                val divs = td.getElementsByTag("div")
                for (div in divs) {
                    val style = div.attr("style").replace(" ", "").lowercase()   // H2
                    if (div.text().isBlank()) continue
                    if (oldQzType == 0) {
                        if (style != "display:none;") continue
                    } else {
                        if (style == "display:none;") continue
                    }

                    val split = div.html().split("<br>").map { it.trim() }
                    var preIndex = -1

                    for (i in split.indices) {
                        if (WEEK_PATTERN2.containsMatchIn(split[i])) {
                            if (preIndex != -1) toCourse(split, preIndex, div, day, courseList)
                            preIndex = i
                        }
                        if (i == split.size - 1) toCourse(split, preIndex, div, day, courseList)
                    }
                }
            }
        }
        return courseList
    }

    private fun toCourse(
        split: List<String>, preIndex: Int, div: org.jsoup.nodes.Element,
        day: Int, out: MutableList<JwCourse>
    ) {
        if (preIndex == -1) return
        if (preIndex - 1 < 0 || preIndex + 1 >= split.size) return   // H3/H5
        val courseName = Jsoup.parse(split[0]).text().trim()
        val room = Jsoup.parse(split[preIndex + 1]).text().trim()
        val teacher = Jsoup.parse(split[preIndex - 1]).text().trim()

        val timeInfo = Jsoup.parse(split[preIndex]).text().trim().split(",")
        for (t in timeInfo) {
            val s = t.trim()
            if (s.isBlank()) continue
            val weekStr = s.substringBefore('周').trim()
            if (weekStr.isBlank()) continue
            val startWeek: Int
            val endWeek: Int
            if (weekStr.contains('-')) {
                startWeek = weekStr.substringBefore('-').filter { it.isDigit() }.toIntOrNull() ?: continue  // H4
                endWeek = weekStr.substringAfter('-').filter { it.isDigit() }.toIntOrNull() ?: startWeek
            } else {
                startWeek = weekStr.filter { it.isDigit() }.toIntOrNull() ?: continue
                endWeek = startWeek
            }
            val nodeIdx = div.attr("id").split('-').firstOrNull()
                ?.filter { it.isDigit() }?.toIntOrNull() ?: continue   // H4
            val startNode = nodeIdx * 2 - 1
            out += JwCourse(
                name = courseName, teacher = teacher, room = room,
                day = day.coerceIn(1, 7),
                startNode = startNode.coerceAtLeast(1),
                endNode = (startNode + 1).coerceAtLeast(startNode),
                startWeek = startWeek.coerceAtLeast(1),
                endWeek = endWeek.coerceAtLeast(startWeek),
                type = 0
            )
        }
    }

    companion object {
        /** 上游 Common.weekPattern2 原文，不可改字符 */
        private val WEEK_PATTERN2 = Regex("""\d{1,2}周""")
    }
}
