package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.*
import org.junit.Test

class LongBreakCourseGeometryTest {
    private val times = """[{"node":1,"start":"10:00","end":"10:45"},{"node":2,"start":"11:00","end":"11:45"},{"node":3,"start":"13:15","end":"14:00"}]"""
    private fun course() = CourseEntity(groupId="g", tableId=1, courseName="Crossing", day=1,
        startNode=2, step=2, startWeek=1, endWeek=20, color="#123456")
    private fun weights(courses: List<CourseEntity>): List<Float> {
        val gaps = MealBreakDetector.detect(times, courses).associate { it.afterRowIndex to it.minutes.toInt() }
        return TimetableViewportPolicy.expandWeightsForLongBreaks(
            listOf(1f,1f,1f), gaps.keys, gaps, periodMinutes=45, enabled=true)
    }
    @Test fun emptyLongBreakAddsExactlyItsMinuteWeight() {
        assertEquals(listOf(1f,3f,1f), weights(emptyList()))
    }
    @Test fun standardCourseCrossingGapKeepsContinuousCardGeometry() {
        assertEquals(listOf(1f,1f,1f), weights(listOf(course())))
    }
    @Test fun explicitTimeOverlappingGapSuppressesFoldEvenWithinOneNode() {
        val c=course().copy(step=1, ownTime=true, isIrregularTime=true, startTime="11:30",endTime="12:30")
        assertEquals(listOf(1f,1f,1f), weights(listOf(c)))
    }
    @Test fun classEndingAtGapBoundaryDoesNotSuppressEmptyBreak() {
        val c=course().copy(step=1, ownTime=true, isIrregularTime=true, startTime="11:00",endTime="11:45")
        assertEquals(listOf(1f,3f,1f), weights(listOf(c)))
    }

    // === #183 薄带卡高刚性 回归契约 (2026-10-17 定位: 带在 cardH 被双扣) ===
    // 症状: 长课间上方一节课的胶囊回缩 ~24dp, 不满行高; 其余行正常。
    // 根因: cardY 走 yOfRows (坐标层已加带), cardH 又减 gapAfterRow=gapH+带 (高度层二次扣带)。
    // 不变量: 薄带只进行坐标偏移, 绝不进卡片高度 — 卡高与带厚无关。
    @Test fun anchoredCardHeightIsIndependentOfBandWidth() {
        val rowH = 52f
        val gapH = TimetableViewportPolicy.ROW_GAP_DP
        for (band in listOf(0f, 6f, 24f, 48f)) {
            // 行尾带厚变化不得进入卡高: 锚行卡 = 普通行卡 = rowH − gapH 满高
            assertEquals(rowH - gapH,
                TimetableViewportPolicy.longBreakCardHeightDp(naturalSpanDp = rowH, gapDp = gapH), 1e-5f)
        }
    }

    @Test fun multiStepCardEndingAtBandRowKeepsContinuousGeometry() {
        // 两节连排卡尾落在带行: 高 = 2×rowH − gapH (旧 bug 会再多减一个带厚)
        val rowH = 52f
        val gapH = TimetableViewportPolicy.ROW_GAP_DP
        assertEquals(rowH * 2f - gapH,
            TimetableViewportPolicy.longBreakCardHeightDp(naturalSpanDp = rowH * 2f, gapDp = gapH), 1e-5f)
    }

    @Test fun fractionalCardBottomingOnBandRowSubtractsOnlyNormalGap() {
        // ownTime 课尾恰落在带行 (整数尾): 旧公式在此减 gapAfterRow=gapH+带; 新口径只减 gapH
        val rowH = 52f
        val gapH = TimetableViewportPolicy.ROW_GAP_DP
        val fracSpan = rowH * 1.5f // 跨 1.5 行
        assertEquals(fracSpan - gapH,
            TimetableViewportPolicy.longBreakCardHeightDp(naturalSpanDp = fracSpan, gapDp = gapH), 1e-5f)
    }
}
