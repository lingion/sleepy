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
}
