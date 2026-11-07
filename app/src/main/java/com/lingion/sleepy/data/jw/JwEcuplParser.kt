// ECUPL (华东政法大学) main parser — wakeup-parity-ecupl-super-2026-10-06 SOP v1.11
package com.lingion.sleepy.data.jw

/**
 * ECUPL annual TaskActivity timetable. marshalTable supplies the academic week
 * origin; the detail table supplies course names, teachers, credits and notes.
 * A time-script page is handled by the separate HHmm/decimal-bitmap branch.
 * Source semantics were verified against the user's local WakeUp 6.6.2 APK.
 */
class JwEcuplParser(source: String) : JwParser(source) {

    companion object {
        // ---- 协议指纹 10 件套 ----

        /** R1: 主表 class="listTable" 容器 */
        val RE_LIST_TABLE: Regex = Regex("""<table[^>]*class="listTable"[^>]*>""")

        /** R3: 课程名模糊匹配 — 课程单元格含学科关键词 */
        val RE_COURSE_NAME: Regex = Regex("""<td[^>]*>([^<]*(?:法|律|学|课)[^<]*)</td>""")

        /** R4: 教师单元格 */
        val RE_TEACHER: Regex = Regex("""<td[^>]*>([^<]*(?:教授|老师|讲师|[一-龥]{2,4})[^<]*)</td>""")

        /** R9: 周次段解析 — "1-16" / "1-8单" / "1-8双" / "1-16每周" */
        val RE_WEEK_RANGE: Regex = Regex("""(\d+)\s*[-~]\s*(\d+)\s*(?:(单|双)|每周)?""")

        /** R10: 节次单元格 — "1-2节" */
        val RE_SECTION: Regex = Regex("""(\d+)\s*[-~]\s*(\d+)\s*节?""")

        /** ECUPL 默认总周数 (WakeUp 默认) */
        const val DEFAULT_WEEK_COUNT: Int = 20
    }

    /**
     * 只有同时存在元数据表和 TaskActivity/index 时间表时才生成课程。
     *
     * 返回课程列表。
     */
    override fun generateCourseList(): List<JwCourse> {
        // Coordinates still come from TaskActivity/index; metadata never creates a slot.
        if (!source.contains("new TaskActivity(") ||
            !source.contains("table0.marshalTable") ||
            !RE_LIST_TABLE.containsMatchIn(source)) return JwEcuplScriptParser(source).generateCourseList()
        val marshal = Regex("""table0\.marshalTable\s*\(\s*(\d+)\s*,\s*\d+\s*,\s*(\d+)\s*\)""").find(source)
            ?: return emptyList()
        val origin = marshal.groupValues[1].toIntOrNull()?.takeIf { it in 2..54 } ?: return emptyList()
        val weekCount = marshal.groupValues[2].toIntOrNull()?.takeIf { it in 1..64 } ?: return emptyList()
        val year = Regex("""new CourseTable\(\s*(\d{4})\s*,\s*\d+\s*\)""").find(source)
            ?.groupValues?.get(1)?.toIntOrNull() ?: return emptyList()
        // 6.6.2 ECUPLParser.OooOOo0 has a distinct year-wrap branch. The helper
        // returns ISO weekday numbers, so the extra week condition is Saturday (6).
        val yearOffset = if (java.time.LocalDate.of(year, 12, 31).dayOfWeek == java.time.DayOfWeek.SATURDAY) 55 else 54
        val courses = JwClassicEamsParser(source) { bitmap ->
            when {
                bitmap.any { it != '0' && it != '1' } || bitmap.length < origin - 1 -> ""
                '1' !in bitmap.take(origin - 1) -> bitmap.drop(origin - 2).take(weekCount + 1)
                else -> ("0".repeat(yearOffset - origin) + bitmap).take(weekCount + 1)
            }
        }.generateCourseList()
        val details = ecuplDetails(source)
        return courses.map { course ->
            val code = course.name.substringAfterLast('(', "").removeSuffix(")")
            val detail = details[code]
            if (detail == null) course else course.copy(
                name=detail.name, teacher=detail.teacher, credit=detail.credit, note=detail.note)
        }
    }

    override fun confidence(): Int {
        if (RE_LIST_TABLE.containsMatchIn(source) && source.contains("table0.marshalTable") &&
            source.contains("new TaskActivity(") &&
            Regex("""new CourseTable\(\s*\d{4}\s*,\s*\d+\s*\)""").containsMatchIn(source)) return 100
        var hits = 0
        // R1: 主表锚点
        if (RE_LIST_TABLE.containsMatchIn(source)) hits += 3
        // R3: 课程名锚点
        if (RE_COURSE_NAME.containsMatchIn(source)) hits += 2
        // R4: 教师锚点
        if (RE_TEACHER.containsMatchIn(source)) hits += 1
        // R9: 周次段锚点
        if (RE_WEEK_RANGE.containsMatchIn(source)) hits += 2
        return when {
            hits >= 7 -> 95
            hits >= 5 -> 80
            hits >= 3 -> 60
            hits >= 1 -> 30
            else -> 0
        }
    }

    override fun matchedFeatures(): List<String> = buildList {
        if (source.contains("table0.marshalTable") && source.contains("new TaskActivity(")) add("ecupl:annual-week-origin")
        if (RE_LIST_TABLE.containsMatchIn(source)) add("ecupl:r1-listTable")
        if (RE_COURSE_NAME.containsMatchIn(source)) add("ecupl:r3-courseName")
        if (RE_TEACHER.containsMatchIn(source)) add("ecupl:r4-teacher")
        if (RE_WEEK_RANGE.containsMatchIn(source)) add("ecupl:r9-weekRange")
        if (RE_SECTION.containsMatchIn(source)) add("ecupl:r10-section")
        if (isEmpty()) add("guard:no-ecupl-markers")
    }
}
