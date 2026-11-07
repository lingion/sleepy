package com.lingion.sleepy.data.jw

/** Declared ShanghaiTech graduate 2024 layout with its thirteen-period timetable. */
class JwShtu2024Parser(source: String) : JwParser(source) {
    private val periods = listOf(
        "08:15" to "09:00", "09:10" to "09:55", "10:15" to "11:00", "11:10" to "11:55",
        "13:00" to "13:45", "13:55" to "14:40", "15:00" to "15:45", "15:55" to "16:40",
        "16:50" to "17:35", "18:00" to "18:45", "18:55" to "19:40", "19:50" to "20:35", "20:45" to "21:30",
    )

    override fun generateCourseList(): List<JwCourse> = WakeUpCompat.parseJzPost(source).mapNotNull { course ->
        val first = periods.getOrNull(course.startNode - 1) ?: return@mapNotNull null
        val last = periods.getOrNull(course.endNode - 1) ?: return@mapNotNull null
        course.copy(startTime = first.first, endTime = last.second)
    }

    override fun confidence(): Int = if (org.jsoup.Jsoup.parse(source)
        .selectFirst("[id^=jsTbl_] .arrage, table.MsoNormalTable.mtt_table .arrage") != null) 100 else 0
    override fun matchedFeatures(): List<String> = if (confidence() > 0) listOf("declared-shtu-post-2024", "arrage") else emptyList()
}
