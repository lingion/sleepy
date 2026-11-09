package com.lingion.sleepy.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.ui.screen.schedule.SchedulePresentationCache
import com.lingion.sleepy.ui.screen.schedule.ScheduleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HolidayFastComputeGreyDaysTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun fastComputeGreyDays_returns_empty_when_toggles_disabled() {
        AppPrefs.setHolidayGreyHoliday(context, false)
        AppPrefs.setHolidayGreyWeekend(context, false)
        val dates = (1..7).associateWith { day -> LocalDate.of(2025, 1, day) }
        val result = HolidayManager.fastComputeGreyDays(context, dates)
        assertTrue(result.isEmpty())
    }

    @Test
    fun fastComputeGreyDays_greys_weekends_and_skips_transfers() {
        AppPrefs.setHolidayGreyHoliday(context, true)
        AppPrefs.setHolidayGreyWeekend(context, true)
        AppPrefs.setHolidayIgnoreWorkday(context, true)

        val wednesday = LocalDate.of(2025, 1, 1) // 元旦
        val saturday = LocalDate.of(2025, 1, 4)  // 周六
        val sunday = LocalDate.of(2025, 1, 5)    // 周日
        val dates = mapOf(3 to wednesday, 6 to saturday, 7 to sunday)
        val transfers = listOf(HolidayTransferEntry(wednesday, LocalDate.of(2025, 1, 2), "seg"))

        val result = HolidayManager.fastComputeGreyDays(context, dates, tableId = 1L, transfers = transfers)
        assertTrue(result.contains(6))
        assertTrue(result.contains(7))
        assertFalse(result.contains(3))
    }

    @Test
    fun presentationCache_caches_weekGreyDays_synchronously() {
        AppPrefs.setHolidayGreyHoliday(context, true)
        AppPrefs.setHolidayGreyWeekend(context, true)
        AppPrefs.setHolidayIgnoreWorkday(context, true)

        val cache = SchedulePresentationCache()
        val table = TimeTableEntity(
            id = 100L,
            name = "Test Table",
            startDate = "2025-01-06", // 2025-01-06 周一
            maxWeek = 20,
            nodesPerDay = 12
        )
        val state = ScheduleState(
            tables = listOf(table),
            selectedTableId = table.id,
            transfers = emptyList()
        )

        // 第 1 周: 2025-01-06 ~ 2025-01-12 (6 是周六, 7 是周日)
        val greyDaysWeek1 = cache.weekGreyDays(context, state, 1)
        assertEquals(setOf(6, 7), greyDaysWeek1)

        // 第二次调用直接命中缓存引用
        val cached = cache.weekGreyDays(context, state, 1)
        assertTrue(greyDaysWeek1 === cached)
    }
}
