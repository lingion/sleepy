package com.lingion.sleepy.util

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DateUtilsTest {
    @Test fun beforeSemesterNeverProducesNegativeWeek() {
        assertEquals(1, DateUtils.currentWeek("2026-09-07", LocalDate.parse("2026-08-24")))
        assertEquals(DateUtils.SemesterStatus.BEFORE_START, DateUtils.semesterStatus("2026-09-07", 20, LocalDate.parse("2026-08-24")))
    }

    @Test fun detectsAfterSemester() {
        assertEquals(DateUtils.SemesterStatus.AFTER_END, DateUtils.semesterStatus("2026-09-07", 20, LocalDate.parse("2027-02-01")))
    }

    // ---- semesterStatus 边界 ----

    @Test fun semesterStartDayIsInRange() {
        assertEquals(DateUtils.SemesterStatus.IN_RANGE, DateUtils.semesterStatus("2026-09-07", 20, LocalDate.parse("2026-09-07")))
    }

    @Test fun lastDayOfMaxWeekIsStillInRange() {
        // 第20周最后一天: start + 20*7 - 1
        assertEquals(DateUtils.SemesterStatus.IN_RANGE, DateUtils.semesterStatus("2026-09-07", 20, LocalDate.parse("2027-01-24")))
    }

    @Test fun dayAfterMaxWeekIsAfterEnd() {
        assertEquals(DateUtils.SemesterStatus.AFTER_END, DateUtils.semesterStatus("2026-09-07", 20, LocalDate.parse("2027-01-25")))
    }

    @Test fun invalidStartDateFallsBackToInRange() {
        assertEquals(DateUtils.SemesterStatus.IN_RANGE, DateUtils.semesterStatus("garbage", 20, LocalDate.parse("2026-08-24")))
    }

    // ---- currentWeek 钳制 ----

    @Test fun afterEndWeekIsClampedForBrowsing() {
        // 2027-02-01 真实周数=21, 浏览仍钳在 20
        assertEquals(20, DateUtils.currentWeek("2026-09-07", LocalDate.parse("2027-02-01")).coerceAtMost(20))
    }

    @Test fun firstDayIsWeekOne() {
        assertEquals(1, DateUtils.currentWeek("2026-09-07", LocalDate.parse("2026-09-07")))
    }

    @Test fun invalidStartFallbackIsWeekOne() {
        assertEquals(1, DateUtils.currentWeek("bad-date", LocalDate.parse("2026-08-24")))
    }
}
