package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/**
 * 中国科学院大学选课系统个人课表（`/course/personSchedule`）解析器。
 *
 * #18 的采集包确认页面是服务端 HTML：表头为星期一至星期日，tbody 每行的 th 为节次，
 * td 中 a[href*="/course/coursetime/"] 的文本为课程名。采集包中详情域 xkcts 的跨域请求
 * 被 OPTIONS 403 阻断，故没有可验证的单课周次/地点；课程先按当前学期 1–16 周导入。
 */
class JwUcasParser(source: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> {
        val table = Jsoup.parse(source).select("table").firstOrNull { table ->
            table.select("thead th").any { it.text().contains("节次/星期") } &&
                table.select("a[href*=/course/coursetime/]").isNotEmpty()
        } ?: return emptyList()

        val result = mutableListOf<JwCourse>()
        table.select("tbody tr").forEach { row ->
            val node = row.selectFirst("th")?.text()?.trim()?.toIntOrNull() ?: return@forEach
            row.select("> td").forEachIndexed { index, cell ->
                val day = index + 1
                cell.select("a[href*=/course/coursetime/]").map { it.text().trim() }
                    .filter { it.isNotBlank() }.distinct().forEach { name ->
                        result += JwCourse(
                            name = name, day = day, startNode = node, endNode = node,
                            startWeek = 1, endWeek = 16
                        )
                    }
            }
        }
        return mergeAdjacentRows(result)
    }

    override fun confidence(): Int = if (source.contains("个人课表") && source.contains("/course/coursetime/")) 95 else 0

    override fun matchedFeatures(): List<String> = buildList {
        if (source.contains("个人课表")) add("title:个人课表")
        if (source.contains("/course/coursetime/")) add("href:/course/coursetime/")
    }

    private fun mergeAdjacentRows(courses: List<JwCourse>): List<JwCourse> = courses
        .groupBy { listOf(it.name, it.day, it.startWeek, it.endWeek, it.type) }
        .flatMap { (_, group) ->
            group.sortedBy { it.startNode }.fold(mutableListOf<JwCourse>()) { merged, course ->
                val previous = merged.lastOrNull()
                if (previous != null && previous.endNode + 1 == course.startNode) {
                    merged[merged.lastIndex] = previous.copy(endNode = course.endNode)
                } else merged += course
                merged
            }
        }
}
