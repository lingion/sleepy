package com.lingion.sleepy.ui.screen.schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HolidayTransferScheduleContractTest {
    @Test
    fun makeup_sunday_takes_source_wednesday_courses() {
        val wednesday = LocalDate.of(2026, 10, 7)  // 放假日(周三)
        val sunday = LocalDate.of(2026, 10, 11)    // 补班日(周日)
        val transfers = listOf(
            com.lingion.sleepy.util.HolidayTransferEntry(wednesday, sunday, "seg")
        )
        // 补班日 → 上周三(3)的课
        assertEquals(3, com.lingion.sleepy.util.HolidayRangeOps.HolidayTransferOps.courseDayForDate(sunday, transfers))
        // 放假日 → 无课(null)
        assertEquals(null, com.lingion.sleepy.util.HolidayRangeOps.HolidayTransferOps.courseDayForDate(wednesday, transfers))
        // 未映射的下一个周日按自然星期
        assertEquals(7, com.lingion.sleepy.util.HolidayRangeOps.HolidayTransferOps.courseDayForDate(sunday.plusDays(7), transfers))
    }
}
