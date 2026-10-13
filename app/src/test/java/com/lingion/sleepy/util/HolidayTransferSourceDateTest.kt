package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HolidayTransferSourceDateTest {
    private val ops = HolidayRangeOps.HolidayTransferOps
    private fun d(value: String) = LocalDate.parse(value)
    private fun transfer(a: String, b: String) = HolidayTransferEntry(d(a), d(b), "test")
    private val odd = CourseEntity(id=1, groupId="odd", tableId=1, courseName="Source odd", day=3,
        startNode=1, step=1, startWeek=5, endWeek=5, type=1, color="#123456")
    private val even = odd.copy(id=2, groupId="even", courseName="Target even", startWeek=6, endWeek=6, type=2)
    private val sunday = odd.copy(id=3, day=7, startWeek=1, endWeek=20, type=0)
    private fun select(date: String, rules: List<HolidayTransferEntry>, max: Int=20) =
        ops.coursesForDate(d(date), "2026-09-07", max, listOf(odd, even, sunday), rules).map { it.id }

    @Test fun currentMakeupAfterSemesterKeepsActualTargetWeek() {
        val now = d("2026-10-18").atTime(7, 0)
        val rules = listOf(transfer("2026-10-07", "2026-10-18"))
        val context = WeekDisplayResolver.resolve("2026-09-07", 5, now, listOf(odd),
            TimeTableUtils.DEFAULT_TIME_JSON, false, rules)
        assertEquals(6, context.targetWeek)
        val ordinary = WeekDisplayResolver.resolve("2026-09-07", 5, now, listOf(odd),
            TimeTableUtils.DEFAULT_TIME_JSON, false)
        assertEquals(5, ordinary.targetWeek)
    }

    @Test fun crossWeekUsesSourceParityAndReplacesDestination() {
        val rules=listOf(transfer("2026-10-07", "2026-10-18"))
        assertEquals(emptyList<Long>(), select("2026-10-07", rules))
        assertEquals(listOf(3L), select("2026-10-11", rules))
        assertEquals(listOf(1L), select("2026-10-18", rules))
    }
    @Test fun sameWeekdayAcrossWeeksDoesNotLoseSourceDate() {
        assertEquals(listOf(1L), select("2026-10-14", listOf(transfer("2026-10-07", "2026-10-14"))))
        assertEquals(listOf(2L), select("2026-10-14", emptyList()))
    }
    @Test fun sourceSemesterBoundsApplyInsteadOfTargetBounds() {
        assertEquals(listOf(1L), select("2026-10-18", listOf(transfer("2026-10-07", "2026-10-18")), max=5))
        assertEquals(emptyList<Long>(), select("2026-10-18", listOf(transfer("2026-09-02", "2026-10-18"))))
    }
    @Test fun mappingsAreSimultaneousNotRecursiveAndLastTargetWins() {
        val a=d("2026-10-07"); val b=d("2026-10-14"); val c=d("2026-10-18")
        val chain=listOf(HolidayTransferEntry(a,b,"a"),HolidayTransferEntry(b,c,"b"))
        assertEquals(a,ops.courseDateForDate(b,chain)); assertEquals(b,ops.courseDateForDate(c,chain))
        val swap=listOf(HolidayTransferEntry(a,b,"a"),HolidayTransferEntry(b,a,"b"))
        assertEquals(b,ops.courseDateForDate(a,swap)); assertEquals(a,ops.courseDateForDate(b,swap))
        assertEquals(a,ops.courseDateForDate(a,listOf(HolidayTransferEntry(a,a,"self"))))
        assertEquals(b,ops.courseDateForDate(c,listOf(HolidayTransferEntry(a,c,"first"),HolidayTransferEntry(b,c,"last"))))
    }
}
