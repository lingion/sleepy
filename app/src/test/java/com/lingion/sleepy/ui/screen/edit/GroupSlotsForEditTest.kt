package com.lingion.sleepy.ui.screen.edit

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupSlotsForEditTest {

    private fun course(
        startNode: Int, step: Int,
        startWeek: Int, endWeek: Int, type: Int,
        day: Int = 1, ownTime: Boolean = false,
        startTime: String = "", endTime: String = ""
    ) = CourseEntity(
        groupId = "g", tableId = 1L, courseName = "课", day = day,
        startNode = startNode, step = step,
        startWeek = startWeek, endWeek = endWeek, type = type,
        color = "#FF6750A4", ownTime = ownTime, startTime = startTime, endTime = endTime
    )

    // 用户报告场景：1-5周1-3节 + 6-10周5-7节 → 2 组，周次保留
    @Test
    fun userReportedCase_twoGroups() {
        val courses = listOf(
            course(1, 3, 1, 5, 0),
            course(5, 3, 6, 10, 0)
        )
        val groups = groupSlotsForEdit(courses)
        assertEquals(2, groups.size)
    }

    // 同节次同周次的两天 → 1 组
    @Test
    fun sameSlotDifferentDays_oneGroup() {
        val courses = listOf(
            course(1, 2, 1, 16, 0, day = 1),
            course(1, 2, 1, 16, 0, day = 3)
        )
        assertEquals(1, groupSlotsForEdit(courses).size)
    }

    // 同节次但周次不同 → 2 组（旧逻辑会错误合并）
    @Test
    fun sameNodeDifferentWeeks_twoGroups() {
        val courses = listOf(
            course(1, 2, 1, 8, 0),
            course(1, 2, 9, 16, 0)
        )
        assertEquals(2, groupSlotsForEdit(courses).size)
    }

    // 同节次同周次但单双周不同 → 2 组
    @Test
    fun sameRangeDifferentType_twoGroups() {
        val courses = listOf(
            course(1, 2, 1, 16, 1),
            course(1, 2, 1, 16, 2)
        )
        assertEquals(2, groupSlotsForEdit(courses).size)
    }

    // ownTime 课按时间区分
    @Test
    fun ownTimeDifferentTimes_twoGroups() {
        val courses = listOf(
            course(1, 2, 1, 16, 0, ownTime = true, startTime = "08:00", endTime = "09:40"),
            course(1, 2, 1, 16, 0, ownTime = true, startTime = "10:00", endTime = "11:40")
        )
        assertEquals(2, groupSlotsForEdit(courses).size)
    }

    // 组内 days 去重聚合的原料：同组两条 day=1/day=3
    @Test
    fun groupMembersPreserved() {
        val courses = listOf(
            course(1, 2, 1, 16, 0, day = 1),
            course(1, 2, 1, 16, 0, day = 3),
            course(5, 2, 1, 16, 0, day = 1)
        )
        val groups = groupSlotsForEdit(courses)
        assertEquals(2, groups.size)
        assertEquals(2, groups.first { it[0].startNode == 1 }.size)
    }
}
