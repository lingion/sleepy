package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

class JwNyistParser(source: String) : JwParser(source) {
    private val document by lazy { Jsoup.parse(source) }

    override fun generateCourseList(): List<JwCourse> = mergeCourseWeeks(buildList {
        for (group in document.select(".course-content")) {
            val name = group.selectFirst(".name")?.text()?.trim().orEmpty()
            if (name.isEmpty()) continue
            for (item in group.select(".course-item-list")) {
                val time = item.selectFirst("div.time > p.content")?.text()?.trim().orEmpty()
                val match = timePattern.matchEntire(time) ?: continue
                val day = weekdays.indexOf(match.groupValues[2]) + 1
                if (day !in 1..7) continue
                val nodes = schoolNodeRanges(match.groupValues[3])
                val startTime = match.groupValues[4]
                val endTime = match.groupValues[5]
                if (!JwCourse.isValidTimeRange(startTime, endTime)) continue
                val weeks = match.groupValues[1].trim().split(Regex("\\s+")).map { token ->
                    val week = weekPattern.matchEntire(token)
                    if (week == null) emptyList() else schoolWeekRanges(
                        week.groupValues[1] + week.groupValues[2])
                }
                if (weeks.any { it.isEmpty() }) continue
                val teacher = item.selectFirst("div.teacher > p.content")?.text()?.trim().orEmpty()
                val room = item.selectFirst("div.address > p.content")?.text()?.trim().orEmpty()
                for ((firstNode, lastNode) in nodes) for ((firstWeek, lastWeek, parity) in weeks.flatten()) {
                    add(JwCourse(name, room, teacher, day, firstNode, lastNode, firstWeek, lastWeek, parity,
                        startTime = startTime, endTime = endTime))
                }
            }
        }
    })

    override fun confidence(): Int = if (document.selectFirst(".course-content .course-item-list") != null) 100 else 0
    override fun matchedFeatures(): List<String> = if (confidence() > 0) listOf("NYIST/course-content") else emptyList()

    private companion object {
        val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val weekPattern = Regex("""第\[([0-9]+(?:-[0-9]+)?)\]周(\([单双]\))?""")
        val timePattern = Regex("""(.+?)\s+(周[一二三四五六日])\s+([0-9,，\-]+)节\s+([0-9]{2}:[0-9]{2})\s*[~～-]\s*([0-9]{2}:[0-9]{2})""")
    }
}
