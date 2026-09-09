package com.lingion.sleepy.util

import com.lingion.sleepy.util.TimeTableUtils.TimeSlotRow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * issue #28 P3: 手动改作息表后课程节次自适应。
 *
 * 报告者场景: 错误 16 节表上课程锚在 13-16 节(晚间 18:30-22:00 真实时间), 手动把作息
 * 改成正确的 12 节表后课程仍显示 13-16 节 — 节次编号从未按绝对时间重排。
 * 修复 = TimeTableUtils.remapCourseNodes: 课程旧表绝对时间窗 → 新表重叠区间。
 */
class Ics28CourseRemapTest {

    /** 正确的 NEU 12 节表(P1 修复后导入产物): 五块 [08:00-09:40|10:00-11:40|14:00-15:40|16:00-17:40|18:30-22:00] */
    private val neu12 = TimeTableUtils.buildTimeJsonFromRows(
        listOf(
            TimeSlotRow(1, "08:00", "08:50"), TimeSlotRow(2, "08:50", "09:40"),
            TimeSlotRow(3, "10:00", "10:50"), TimeSlotRow(4, "10:50", "11:40"),
            TimeSlotRow(5, "14:00", "14:50"), TimeSlotRow(6, "14:50", "15:40"),
            TimeSlotRow(7, "16:00", "16:50"), TimeSlotRow(8, "16:50", "17:40"),
            TimeSlotRow(9, "18:30", "19:22"), TimeSlotRow(10, "19:22", "20:15"),
            TimeSlotRow(11, "20:15", "21:07"), TimeSlotRow(12, "21:07", "22:00")
        )
    )

    /** 报告者的错误 16 节表: 晚间块被锚在 13-16 节(时间仍对, 节次编号错) */
    private val wrong16 = TimeTableUtils.buildTimeJsonFromRows(
        listOf(
            TimeSlotRow(1, "08:00", "08:45"), TimeSlotRow(2, "08:45", "09:30"),
            TimeSlotRow(3, "09:30", "10:15"), TimeSlotRow(4, "10:15", "11:00"),
            TimeSlotRow(5, "11:00", "11:45"), TimeSlotRow(6, "12:30", "13:15"),
            TimeSlotRow(7, "13:15", "14:00"), TimeSlotRow(8, "14:00", "14:45"),
            TimeSlotRow(9, "14:45", "15:30"), TimeSlotRow(10, "15:30", "16:15"),
            TimeSlotRow(11, "16:15", "17:00"), TimeSlotRow(12, "17:00", "17:45"),
            TimeSlotRow(13, "18:30", "19:22"), TimeSlotRow(14, "19:22", "20:15"),
            TimeSlotRow(15, "20:15", "21:07"), TimeSlotRow(16, "21:07", "22:00")
        )
    )

    @Test
    fun eveningCourse_16to12_movesToCorrectNodes() {
        // 13-16 节 (18:30-22:00) → 新表 9-12 节
        assertEquals(9 to 4, TimeTableUtils.remapCourseNodes(13, 4, wrong16, neu12))
    }

    @Test
    fun morningCourse_staysPut() {
        // 1-2 节 (08:00-09:30) → 新表仍是 1-2 (首 end>08:00 = 节1, 末 start<09:30 = 节2)
        assertEquals(1 to 2, TimeTableUtils.remapCourseNodes(1, 2, wrong16, neu12))
    }

    @Test
    fun middayCourse_shiftsToOverlapWindow() {
        // 旧表 3-4 (09:30-11:00): 首个 end>09:30 的节 = 2 (end 09:40), 末个 start<11:00 = 4
        assertEquals(2 to 3, TimeTableUtils.remapCourseNodes(3, 2, wrong16, neu12))
    }

    @Test
    fun sameTable_identicalJson_returnsSameSpan() {
        assertEquals(9 to 4, TimeTableUtils.remapCourseNodes(9, 4, neu12, neu12))
    }

    @Test
    fun oldRowMissing_keepsCourseUnchanged() {
        // 课程锚的旧节次在旧表里不存在(旧表只有 12 行) → 不动(不猜)
        assertEquals(13 to 4, TimeTableUtils.remapCourseNodes(13, 4, neu12, neu12))
    }

    @Test
    fun courseBeyondNewTable_clampsToOverlappingTail() {
        // 旧表 15-16 (20:15-22:00) → 新表只有 12 节: 首个 end>20:15 = 11, 末个 start<22:00 = 12
        assertEquals(11 to 2, TimeTableUtils.remapCourseNodes(15, 2, wrong16, neu12))
    }

    @Test
    fun blankOldJson_fallsBackToTwelveSmartRows_unmappableCourseKept() {
        // 旧表空白 = 12 节 smart 默认铺底; 课程锚在 16 节(不存在) → 保持原样
        assertEquals(16 to 2, TimeTableUtils.remapCourseNodes(16, 2, "", neu12))
    }
}
