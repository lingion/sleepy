package com.lingion.sleepy

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.TimeTableUtils

/** Demonstration data only; both previews use the production timetable renderers. */
object MealBreakPreviewFixture {
    val timeJson = TimeTableUtils.DEFAULT_TIME_JSON
    val courses = listOf(
        course(1, "高等数学", 1, 1, "A201"),
        course(2, "大学英语", 1, 5, "B302"),
        course(3, "程序设计", 2, 3, "实验楼 105"),
        course(4, "大学物理", 2, 7, "A203"),
        course(5, "高等数学", 3, 1, "A201"),
        course(6, "体育", 3, 5, "体育馆"),
        course(7, "工程制图", 4, 3, "C101"),
        course(8, "创新实践", 4, 9, "实验楼 202"),
        course(9, "大学英语", 5, 5, "B302"),
        course(10, "自主研习", 5, 11, "图书馆")
    )

    private fun course(id: Long, name: String, day: Int, node: Int, room: String) = CourseEntity(
        id = id, groupId = name, tableId = 1L, courseName = name,
        day = day, startNode = node, step = 2, startWeek = 1, endWeek = 16,
        room = room, color = "#FF6750A4"
    )
}
