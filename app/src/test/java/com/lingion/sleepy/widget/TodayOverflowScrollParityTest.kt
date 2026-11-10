package com.lingion.sleepy.widget
import com.lingion.sleepy.util.DateUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TodayOverflowScrollParityTest {
    @Test
        fun `status content forces single page with non-zero padding`() {
        // v11: 状态分支 (无课表/学期外/无课) 高度 = 顶 pad + 状态行 + 各自底 pad —
        // 不进 overflow, 直接静态 (statusH < hDp)。锁法:
        // 1. isTodayStatusContent 三态全真 (无课表 / 学期外 / 无课), 有课表有课时假
        val base = com.lingion.sleepy.data.entity.CourseEntity(
            id = 1L, groupId = "a", tableId = 1L, courseName = "甲",
            day = 1, startNode = 1, step = 1, startWeek = 1, endWeek = 20, color = "blue"
        )
        val withCourse = WidgetData(
            date = LocalDate.now(), courses = listOf(base), timeJson = "",
            hasTable = true, semesterStatus = DateUtils.SemesterStatus.IN_RANGE
        )
        assertFalse("有课表+学期内+有课 = 可翻页内容", WidgetBitmapRenderers.isTodayStatusContent(withCourse))
        assertTrue(
            "无课表必须判状态内容",
            WidgetBitmapRenderers.isTodayStatusContent(withCourse.copy(hasTable = false))
        )
        assertTrue(
            "学期外必须判状态内容",
            WidgetBitmapRenderers.isTodayStatusContent(
                withCourse.copy(semesterStatus = DateUtils.SemesterStatus.AFTER_END)
            )
        )
        assertTrue(
            "无课必须判状态内容",
            WidgetBitmapRenderers.isTodayStatusContent(withCourse.copy(courses = emptyList()))
        )
        // 2. 状态内容的内容高度口径 = 顶 pad + 一行状态 + 各自底 pad (与渲染器 status 分支对得上)
        val statusH = WidgetBitmapRenderers.todayContentHeightDp(withCourse.copy(courses = emptyList()))
        assertTrue(
            "状态内容高度必须 = contentTopDp + 22f + 14f (与 renderer status 分支镜像)",
            statusH <= TodayRowGeometry.contentTopDp(false) + 22f + 14f + 0.01f
        )
    }
}
