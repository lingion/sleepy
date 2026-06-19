package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/**
 * 老版 URP 教务系统解析器（HTML 表格版本）
 *
 * 数据来源：URP 综合教务（部分校）课表页用 HTML 表格展示，class 为
 *   "displayTag" 或 "table table-striped table-bordered"。
 * 表头：课程名/教师/星期/节次/周次/教学楼/教室/节数
 *
 * 基于 dIT8Zv/WakeupSchedule_BUPT (Apache-2.0) UrpParser.kt 简化而来
 * https://github.com/dIT8Zv/WakeupSchedule_BUPT/blob/master/app/src/main/java/com/suda/yzune/wakeupschedule/schedule_import/parser/UrpParser.kt
 */
class JwUrpParser(source: String) : JwParser(source) {

    override fun generateCourseList(): List<JwCourse> {
        val result = arrayListOf<JwCourse>()
        val doc = Jsoup.parse(source)
        var tables = doc.getElementsByAttributeValue("class", "displayTag")
        if (tables.isEmpty()) {
            tables = doc.getElementsByAttributeValue("class", "table table-striped table-bordered")
        }
        if (tables.isEmpty()) return result

        for (table in tables) {
            // 跳过非课表（第一行含"星期一"的是顶部信息行）
            if (table.text().contains("星期一")) continue

            val thead = table.getElementsByTag("thead").firstOrNull() ?: continue
            val ths = thead.getElementsByTag("th")
            val headSize = ths.size

            var nameIdx = -1
            var teacherIdx = -1
            var weekIdx = -1
            var dayIdx = -1
            var nodeIdx = -1
            var stepIdx = -1
            var buildingIdx = -1
            var roomIdx = -1

            ths.eachText().forEachIndexed { i, s ->
                when (s.trim()) {
                    "课程名" -> nameIdx = i
                    "教师" -> teacherIdx = i
                    "周次" -> weekIdx = i
                    "星期" -> dayIdx = i
                    "节次" -> nodeIdx = i
                    "节数" -> stepIdx = i
                    "教学楼" -> buildingIdx = i
                    "教室" -> roomIdx = i
                }
            }
            if (dayIdx == -1) continue

            val tbody = table.getElementsByTag("tbody").firstOrNull() ?: continue
            var courseName = ""
            var teacher = ""

            for (tr in tbody.getElementsByTag("tr")) {
                val tds = tr.getElementsByTag("td")
                val wholeFlag = tds.size > headSize - weekIdx
                val acDayIdx = if (wholeFlag) dayIdx else dayIdx - weekIdx
                if (tds[acDayIdx].text().trim().isBlank()) continue

                if (wholeFlag) {
                    courseName = tds[nameIdx].text()
                    teacher = tds[teacherIdx].text().trim()
                }

                val room = try {
                    val bIdx = if (wholeFlag) buildingIdx else buildingIdx - weekIdx
                    val rIdx = if (wholeFlag) roomIdx else roomIdx - weekIdx
                    tds[bIdx].text().trim() + tds[rIdx].text().trim()
                } catch (e: Exception) { "" }

                val nodeE = tds[if (wholeFlag) nodeIdx else nodeIdx - weekIdx]
                val startNode = getStartNode(nodeE.text())
                val step = if (stepIdx != -1) {
                    val sIdx = if (wholeFlag) stepIdx else stepIdx - weekIdx
                    getStep(tds[sIdx].text().trim())
                } else {
                    val end = nodeE.text().trim().substringAfter('-').substringBefore('节').trim().toIntOrNull() ?: startNode
                    end - startNode + 1
                }
                val day = getDay(tds[acDayIdx].text())
                val acWeekIdx = if (wholeFlag) weekIdx else 0
                val weekStr = tds[acWeekIdx].text().trim()

                val ranges = weekStrToRanges(weekStr)
                for (r in ranges) {
                    result += JwCourse(
                        name = courseName, room = room, teacher = teacher, day = day,
                        startNode = startNode, endNode = startNode + step - 1,
                        startWeek = r.first, endWeek = r.second, type = r.third
                    )
                }
            }
        }
        return result
    }

    private fun getDay(str: String): Int = try {
        str.trim().toInt()
    } catch (e: Exception) {
        when (str.trim()) {
            "星期一" -> 1; "星期二" -> 2; "星期三" -> 3; "星期四" -> 4
            "星期五" -> 5; "星期六" -> 6; "星期日", "星期天" -> 7
            else -> 1
        }
    }

    private fun getStartNode(s: String): Int {
        val t = s.trim()
        return if (t.contains('-')) {
            t.substringBefore('-').toIntOrNull() ?: 1
        } else {
            t.substringAfter('第').substringBefore('大').substringBefore('小').toIntOrNull() ?: 1
        }
    }

    private fun getStep(s: String): Int = s.toIntOrNull() ?: 1

    private fun weekStrToRanges(weekStr: String): List<Triple<Int, Int, Int>> {
        val result = mutableListOf<Triple<Int, Int, Int>>()
        if (weekStr.isBlank()) {
            result += Triple(1, 20, 0)
            return result
        }
        // 支持 "1-16周", "1,3,5周", "1-16周(单)"
        weekStr.split(',').forEach { week ->
            val cleaned = week.replace("周", "").replace("(", "").replace(")", "")
            val type = when {
                week.contains('单') -> 1
                week.contains('双') -> 2
                else -> 0
            }
            if (cleaned.contains('-')) {
                val parts = cleaned.split('-')
                val s = parts[0].toIntOrNull() ?: 1
                val e = parts.getOrNull(1)?.toIntOrNull() ?: s
                result += Triple(s, e, type)
            } else {
                val v = cleaned.toIntOrNull() ?: 1
                result += Triple(v, v, type)
            }
        }
        return result
    }
}
