package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/**
 * 强智 qz_old 变体解析器（"需要 IE 的那种"老强智教务，如湖南工学院）。
 *
 * 与新版 QzParser 完全不同：单元格内无 title 属性，靠 `<br>` 分隔 + 时间串
 * 含 `[` + `]` + `周` + `节` 四要素来定位。type 恒为 0，不识别单/双周。
 *
 * 样本学校（上游 SchoolListActivity.kt 实证）：湖南工学院等 3 所。
 *
 * 上游源码：dIT8Zv/WakeupSchedule_BUPT OldQzParser.kt (Apache-2.0)
 *   https://github.com/dIT8Zv/WakeupSchedule_BUPT/blob/master/app/src/main/java/com/suda/yzune/wakeupschedule/schedule_import/parser/OldQzParser.kt
 */
class JwOldQzParser(source: String) : JwParser(source) {

    override fun generateCourseList(): List<JwCourse> {
        val courseList = arrayListOf<JwCourse>()
        val doc = Jsoup.parse(source)
        val kbtable = doc.getElementById("kbtable") ?: return courseList
        val trs = kbtable.getElementsByTag("tr")

        for (tr in trs) {
            val tds = tr.getElementsByTag("td")
            if (tds.isEmpty()) continue

            // day 初始 -1: td[0]=节次标签 day=0, td[1..7]=周一..周日 day=1..7
            var day = -1

            for (td in tds) {
                day++
                val divs = td.getElementsByTag("div")
                for (div in divs) {
                    // 过滤 display:none 和空白格
                    // 上游原文是 == "display: none;"（带空格），真实页面存在
                    // "display:none;"（无空格）变体（fixture timetable_kbtable_hidden.html
                    // 有两种写法各一个 div，expected 要求两者都被过滤），
                    // 因此去掉空格后比较 — 有意偏离 upstream 的 bug fix。
                    if (div.attr("style").replace(" ", "") == "display:none;") continue
                    if (div.text().isBlank()) continue

                    val split = div.html().split("<br>")
                    var preIndex = -1

                    fun toCourse() {
                        if (preIndex == -1) return
                        // 课名 = split[0] (Jsoup 解析剥残留 HTML 标签)
                        val courseName = Jsoup.parse(split[0]).text().trim()
                        // room = split[preIndex+1], teacher = split[preIndex-1]
                        val room = Jsoup.parse(split[preIndex + 1]).text().trim()
                        val teacher = Jsoup.parse(split[preIndex - 1]).text().trim()
                        // 时间串形如 "1-16周[1-2节]" 或 "10周[1-2节]"
                        val timeInfo = Jsoup.parse(split[preIndex]).text().trim().split("周[")
                        val startWeek = if (timeInfo[0].contains('-')) {
                            timeInfo[0].split('-')[0].toInt()
                        } else timeInfo[0].toInt()
                        val endWeek = if (timeInfo[0].contains('-')) {
                            timeInfo[0].split('-')[1].toInt()
                        } else timeInfo[0].toInt()
                        val startNode = timeInfo[1].split('-')[0].toInt()
                        // "[1-2节]" split('-') 得 ["1","2节]"]，endNode 去 '节]' 后再 toInt
                        val endNode = timeInfo[1].split('-')[1].substringBefore('节').toInt()

                        courseList.add(
                            JwCourse(
                                name = courseName,
                                room = room,
                                teacher = teacher,
                                day = day,
                                startNode = startNode,
                                endNode = endNode,
                                startWeek = startWeek,
                                endWeek = endWeek,
                                type = 0
                            )
                        )
                    }

                    for (i in split.indices) {
                        // 时间串特征：[ + ] + 周 + 节 四要素
                        if (split[i].contains('[') && split[i].contains(']') &&
                            split[i].contains('节') && split[i].contains('周')
                        ) {
                            if (preIndex != -1) toCourse()
                            preIndex = i
                        }
                        if (i == split.size - 1) toCourse()
                    }
                }
            }
        }
        return courseList
    }
}
