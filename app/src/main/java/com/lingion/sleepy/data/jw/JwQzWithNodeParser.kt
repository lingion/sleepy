package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/**
 * 强智 qz_with_node 变体解析器。
 *
 * 差异：title="周次(节次)" 的文本携带节次信息（不再让节次=所在大格位置），
 * 支持三种文本形态：
 *   1) 含空格 → "1-16(周) 1-2节" — 按空格拆 [周次段, 节次段]
 *   2) 无空格且无 [ ] → 独立 title="周次" + title="节次"
 *   3) 其它 → "周N-X节[Y-Z节]" / "1-16(周)[1-2节]" 格式按 '周[' ']' 拆
 *
 * 样本学校：北邮 jwgl.bupt.edu.cn/jsxsd、广外 jxgl.gdufs.edu.cn/jsxsd、
 *          北邮 WebVPN、北理工、北理工珠海、海南大学、江苏师大等 14 所。
 *
 * 上游源码：dIT8Zv/WakeupSchedule_BUPT QzWithNodeParser.kt (Apache-2.0)
 *   https://github.com/dIT8Zv/WakeupSchedule_BUPT/blob/master/app/src/main/java/com/suda/yzune/wakeupschedule/schedule_import/parser/qz/QzWithNodeParser.kt
 */
class JwQzWithNodeParser(source: String) : JwQzParser(source) {

    override fun convert(day: Int, nodeCount: Int, infoStr: String, courseList: MutableList<JwCourse>) {
        val courseHtml = Jsoup.parse(infoStr)
        // 课名提取：上游 substringBefore("<font").substringBefore("<span>")
        // 第二段 needle 是 "<span>"（带尖括号），对 "<span title=..>" 永不命中，
        // 因此 with_node 页面必须用 <font title=..> 结构（实测 BUPT/JSNU 适配器一致）
        val courseName = Jsoup.parse(
            infoStr.substringBefore("<font").substringBefore("<span>").trim()
        ).text()
        val teacher = courseHtml.getElementsByAttributeValue("title", "老师").text().trim()
        val room = courseHtml.getElementsByAttributeValue("title", "教室").text().trim() +
            courseHtml.getElementsByAttributeValue("title", "分组").text().trim()
        val tempStr = courseHtml.getElementsByAttributeValue("title", "周次(节次)").text()

        // 三分支周次/节次提取
        val weekStr = when {
            tempStr.contains(' ') -> courseHtml.getElementsByAttributeValue("title", "周次(节次)").text().split(' ')[0]
            tempStr.isBlank() -> courseHtml.getElementsByAttributeValue("title", "周次").text()
            else -> courseHtml.getElementsByAttributeValue("title", "周次(节次)").text().substringBefore(')')
        }
        val nodeList = when {
            tempStr.contains(' ') -> courseHtml.getElementsByAttributeValue("title", "周次(节次)").text()
                .split(' ')[1].removeSurrounding("[", "]").split('-')
            tempStr.isBlank() -> courseHtml.getElementsByAttributeValue("title", "节次").text()
                .substringAfter(')').removeSurrounding("[", "]").split('-')
            else -> courseHtml.getElementsByAttributeValue("title", "周次(节次)").text()
                .substringAfter(')').removeSurrounding("[", "]").split('-')
        }

        val weekList = weekStr.split(',')
        var startWeek = 0
        var endWeek = 0
        var type = 0

        weekList.forEach { item ->
            if (item.contains('-')) {
                val weeks = item.split('-')
                if (weeks.isNotEmpty()) startWeek = weeks[0].toInt()
                if (weeks.size > 1) {
                    type = when {
                        weeks[1].contains('单') -> 1
                        weeks[1].contains('双') -> 2
                        else -> 0
                    }
                    endWeek = weeks[1].substringBefore('(').toInt()
                }
            } else {
                startWeek = item.substringBefore('(').toInt()
                endWeek = item.substringBefore('(').toInt()
            }
            courseList.add(
                JwCourse(
                    name = courseName,
                    teacher = teacher,
                    room = room,
                    day = day,
                    startNode = nodeList.first().substringBefore('节').toInt(),
                    endNode = nodeList.last().substringBefore('节').toInt(),
                    startWeek = startWeek,
                    endWeek = endWeek,
                    type = type
                )
            )
        }
    }
}
