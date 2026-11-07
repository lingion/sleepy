package com.lingion.sleepy.data.jw

/** Union only identical arrangements; different teachers, rooms, periods or times remain distinct. */
internal fun mergeCourseWeeks(courses: List<JwCourse>): List<JwCourse> {
    val groups = linkedMapOf<JwCourse, MutableSet<Int>>()
    for (course in courses) {
        if (course.startWeek !in 1..366 || course.endWeek !in course.startWeek..366 || course.type !in 0..2) continue
        val key = course.copy(startWeek = 1, endWeek = 1, type = 0)
        val weeks = groups.getOrPut(key) { sortedSetOf() }
        weeks += (course.startWeek..course.endWeek).filter { course.type == 0 || it % 2 == course.type % 2 }
    }
    return groups.flatMap { (course, weeks) ->
        JwUcasParser.splitWeekRuns(weeks.toList()).map {
            course.copy(startWeek = it.startWeek, endWeek = it.endWeek, type = it.type)
        }
    }
}
