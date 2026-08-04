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
}
