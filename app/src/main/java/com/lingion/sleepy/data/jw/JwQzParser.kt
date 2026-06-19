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

    open fun parseCourseName(infoStr: String): String {
        // 兜底：Jsoup 解析在某些裸 HTML 上可能失败，直接取子串
        return try {
            Jsoup.parse(infoStr.substringBefore("<font").trim()).text()
        } catch (e: Exception) {
            infoStr.substringBefore("<font").trim()
        }
    }

    open fun convert(day: Int, nodeCount: Int, infoStr: String, courseList: MutableList<JwCourse>) {
        val node = nodeCount * 2 - 1
        val courseHtml = Jsoup.parse(infoStr)
        val courseName = parseCourseName(infoStr)
        val teacher = courseHtml.getElementsByAttributeValue("title", "老师").text().trim()
        val room = courseHtml.getElementsByAttributeValue("title", "教室").text().trim() +
            courseHtml.getElementsByAttributeValue("title", "分组").text().trim()
        val weekStr = courseHtml.getElementsByAttributeValue("title", "周次(节次)")
            .text().substringBefore("(周)")
        val weekList = weekStr.split(',')

        var startWeek = 0
        var endWeek = 0
        var type = 0

        weekList.forEach { weekItem ->
            if (weekItem.contains('-')) {
                val weeks = weekItem.split('-')
                if (weeks.isNotEmpty()) startWeek = weeks[0].toIntOrNull() ?: 1
                if (weeks.size > 1) {
                    type = when {
                        weeks[1].contains('单') -> 1
                        weeks[1].contains('双') -> 2
                        else -> 0
                    }
                    // 兼容 "1-16周"、"1-16周(单)"、"1-16(单)"、"1-16" 等格式
                    endWeek = weeks[1]
                        .replace("周", "")
                        .replace("(", "")
                        .replace(")", "")
                        .trim()
                        .toIntOrNull() ?: startWeek
                }
            } else {
                val v = weekItem.replace("周", "").substringBefore('(').toIntOrNull() ?: 1
                startWeek = v
                endWeek = v
            }
            courseList.add(
                JwCourse(
                    name = courseName,
                    room = room,
                    teacher = teacher,
                    day = day,
                    startNode = node,
                    endNode = node + 1,
                    startWeek = startWeek,
                    endWeek = endWeek,
                    type = type
                )
            )
        }
    }

    override fun generateCourseList(): List<JwCourse> {
        val courseList = arrayListOf<JwCourse>()
        val doc = Jsoup.parse(source)
        val kbTable = doc.getElementById("kbtable") ?: return courseList
        val trs = kbTable.getElementsByTag("tr")

        var nodeCount = 0
        for (tr in trs) {
            val tds = tr.getElementsByTag("td")
            if (tds.isEmpty()) {
                continue
            }
            nodeCount++

            var day = 0
            for (td in tds) {
                day++
                val divs = td.getElementsByTag("div")
                for (div in divs) {
                    val courseElements = div.getElementsByClass(tableName)
                    if (courseElements.text().isBlank()) continue
                    val courseHtml = courseElements.html()
                    var startIndex = 0
                    var splitIndex = courseHtml.indexOf("-----")
                    while (splitIndex != -1) {
                        convert(day, nodeCount, courseHtml.substring(startIndex, splitIndex), courseList)
                        startIndex = courseHtml.indexOf("<br>", splitIndex) + 4
                        splitIndex = courseHtml.indexOf("-----", startIndex)
                    }
                    convert(
                        day,
                        nodeCount,
                        courseHtml.substring(startIndex, courseHtml.length),
                        courseList
                    )
                }
            }
        }
        return courseList
    }
}
