package com.lingion.sleepy.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * issue#44 第二轮 T3: 灰显与调休映射联动。
 * 命中映射的放假日 = 那天要上课(上的是目标日的课), 永不灰显;
 * 未映射日期走 decideGrey 旧行为, transfers 为空时行为逐位不变。
 */
class HolidayGreyTransferTest {

    private val nationalDay = LocalDate.of(2026, 10, 1)  // 法定假日, 周四
    private val workday = LocalDate.of(2026, 10, 10)     // 补班周六

    private val holidays = setOf(nationalDay, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3))
    private val workdays = setOf(workday)

    @Test
    fun mapped_source_date_never_greys_even_if_holiday() {
        // 10/1 已映射到某目标日 → 那天上课, 不灰
        val transfers = listOf(HolidayTransferEntry(nationalDay, LocalDate.of(2026, 10, 8), "seg"))
        val ops = HolidayRangeOps.HolidayTransferOps
        val isMapped = { d: LocalDate -> ops.transferFor(d, transfers) != null }
        assertFalse(
            HolidayManager.decideGrey(
                date = nationalDay,
                holidays = holidays,
                workdays = workdays,
                greyHoliday = true,
                greyWeekend = true,
                ignoreWorkday = true,
                dateHasTransfer = isMapped(nationalDay)
            )
        )
    }

    @Test
    fun unmapped_holiday_still_greys() {
        val transfers = listOf(HolidayTransferEntry(nationalDay, LocalDate.of(2026, 10, 8), "seg"))
        val ops = HolidayRangeOps.HolidayTransferOps
        val isMapped = { d: LocalDate -> ops.transferFor(d, transfers) != null }
        assertTrue(
            HolidayManager.decideGrey(
                date = LocalDate.of(2026, 10, 2), // 同段未映射的假日
                holidays = holidays,
                workdays = workdays,
                greyHoliday = true,
                greyWeekend = true,
                ignoreWorkday = true,
                dateHasTransfer = isMapped(LocalDate.of(2026, 10, 2))
            )
        )
    }

    @Test
    fun empty_transfers_keeps_legacy_grey_behavior() {
        // 兼容锁: dateHasTransfer=false 时逐位等同旧签名结果
        val legacy = HolidayManager.decideGrey(
            date = nationalDay, holidays = holidays, workdays = workdays,
            greyHoliday = true, greyWeekend = true, ignoreWorkday = true
        )
        val withFlag = HolidayManager.decideGrey(
            date = nationalDay, holidays = holidays, workdays = workdays,
            greyHoliday = true, greyWeekend = true, ignoreWorkday = true,
            dateHasTransfer = false
        )
        assertTrue(legacy)
        assertTrue(legacy == withFlag)
    }
}
