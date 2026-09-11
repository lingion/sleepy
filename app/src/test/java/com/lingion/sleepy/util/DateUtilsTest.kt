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

// ---- 非周一 startDate 归一回归 (issue #5: 2026-09-01 是周二, 显示成周一) ----

class DateUtilsMondayNormalizeTest {
    // 2026-08-31 是周一, 2026-09-01 是周二

    @Test fun normalizeTuesdayToItsMonday() {
        assertEquals(LocalDate.parse("2026-08-31"), DateUtils.mondayOf(LocalDate.parse("2026-09-01")))
    }

    @Test fun normalizeIsIdentityForMonday() {
        assertEquals(LocalDate.parse("2026-08-31"), DateUtils.mondayOf(LocalDate.parse("2026-08-31")))
    }

    @Test fun normalizeSundayToSameWeekMonday() {
        assertEquals(LocalDate.parse("2026-08-31"), DateUtils.mondayOf(LocalDate.parse("2026-09-06")))
    }

    @Test fun dateOfWeekNormalizesNonMondayStart() {
        // 用户填 09-01 当第一周起点 → 第1周周一应是 08-31 而非 09-01 本身
        assertEquals(LocalDate.parse("2026-08-31"), DateUtils.dateOfWeek("2026-09-01", 1, 1))
    }

    @Test fun dateOfWeekTuesdayFromNonMondayStart() {
        // 第1周周二 = 09-01 真实周二
        assertEquals(LocalDate.parse("2026-09-01"), DateUtils.dateOfWeek("2026-09-01", 1, 2))
    }

    @Test fun dateOfWeekSundayFromNonMondayStart() {
        assertEquals(LocalDate.parse("2026-09-06"), DateUtils.dateOfWeek("2026-09-01", 1, 7))
    }

    @Test fun dateOfWeekMondayStartUnchanged() {
        // 合法周一基准行为不变
        assertEquals(LocalDate.parse("2026-09-07"), DateUtils.dateOfWeek("2026-09-07", 1, 1))
        assertEquals(LocalDate.parse("2026-09-08"), DateUtils.dateOfWeek("2026-09-07", 1, 2))
        assertEquals(LocalDate.parse("2026-09-14"), DateUtils.dateOfWeek("2026-09-07", 2, 1))
    }

    @Test fun currentWeekTreatsNonMondayStartAsItsMonday() {
        // 存的 09-01, 今天 09-01 → 第1周周二
        assertEquals(1, DateUtils.currentWeek("2026-09-01", LocalDate.parse("2026-09-01")))
        // 今天 08-31 (周一) 也在第1周
        assertEquals(1, DateUtils.currentWeek("2026-09-01", LocalDate.parse("2026-08-31")))
        // 09-07 周一 → 第2周 (08-31 起算已过7天)
        assertEquals(2, DateUtils.currentWeek("2026-09-01", LocalDate.parse("2026-09-07")))
    }

    @Test fun normalizeStartDateString() {
        assertEquals("2026-08-31", DateUtils.normalizeStartDate("2026-09-01"))
        assertEquals("2026-08-31", DateUtils.normalizeStartDate("2026-09-06"))
        assertEquals("2026-08-31", DateUtils.normalizeStartDate("2026-08-31"))
    }

    @Test fun normalizeStartDateInvalidPassthrough() {
        assertEquals("garbage", DateUtils.normalizeStartDate("garbage"))
        assertEquals("", DateUtils.normalizeStartDate(""))
    }

    // ---- semesterStatus 同一归一口径 (D1/issue#5 类: raw startDate vs mondayOf 劈叉) ----

    @Test fun semesterStatusTreatsNonMondayStartAsItsMonday() {
        // 存的 09-01(周二), 今天 08-31(周一): currentWeek 说第1周, semesterStatus
        // 不得再说「未开始」(raw 比较 08-31 isBefore 09-01 = true = 劈叉根因)
        assertEquals(
            DateUtils.SemesterStatus.IN_RANGE,
            DateUtils.semesterStatus("2026-09-01", 20, LocalDate.parse("2026-08-31"))
        )
    }

    @Test fun semesterStatusWeekOneSundayInRangeForNonMondayStart() {
        assertEquals(
            DateUtils.SemesterStatus.IN_RANGE,
            DateUtils.semesterStatus("2026-09-01", 20, LocalDate.parse("2026-09-06"))
        )
    }

    @Test fun semesterStatusAfterEndBoundaryUsesNormalizedStart() {
        // maxWeek=1: 归一后第 2 周起点 = 08-31+7 = 09-07 → AFTER_END
        // (raw 口径 between(09-01, 09-07)=6 → 第1周, 会误报 IN_RANGE)
        assertEquals(
            DateUtils.SemesterStatus.AFTER_END,
            DateUtils.semesterStatus("2026-09-01", 1, LocalDate.parse("2026-09-07"))
        )
    }

    @Test fun semesterStatusMondayStartBehaviorUnchanged() {
        // 合法周一基准: 归一是恒等变换, 三态边界逐位不变 (回归守卫)
        assertEquals(
            DateUtils.SemesterStatus.BEFORE_START,
            DateUtils.semesterStatus("2026-09-07", 20, LocalDate.parse("2026-09-06"))
        )
        assertEquals(
            DateUtils.SemesterStatus.IN_RANGE,
            DateUtils.semesterStatus("2026-09-07", 20, LocalDate.parse("2026-09-07"))
        )
        assertEquals(
            DateUtils.SemesterStatus.AFTER_END,
            DateUtils.semesterStatus("2026-09-07", 1, LocalDate.parse("2026-09-14"))
        )
    }

    @Test fun semesterStatusAgreesWithCurrentWeekOnNormalization() {
        // 契约: 两函数必须同一归一口径 — currentWeek=1 (≤maxWeek) 时 semesterStatus 恒 IN_RANGE
        for (day in listOf("2026-08-31", "2026-09-01", "2026-09-06")) {
            val today = LocalDate.parse(day)
            assertEquals(1, DateUtils.currentWeek("2026-09-01", today))
            assertEquals(
                "currentWeek(2026-09-01, $day)=1 但 semesterStatus 不在学期内 (D1 劈叉)",
                DateUtils.SemesterStatus.IN_RANGE,
                DateUtils.semesterStatus("2026-09-01", 20, today)
            )
        }
    }
}
