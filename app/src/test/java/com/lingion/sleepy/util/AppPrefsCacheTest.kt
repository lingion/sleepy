package com.lingion.sleepy.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPrefsCacheTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppPrefs.clearMemoryCachesForTesting()
    }

    @Test
    fun visibleDays_cached_in_memory_and_invalidated_on_write() {
        AppPrefs.setVisibleDays(context, setOf(1, 2, 3, 4, 5))
        val first = AppPrefs.getVisibleDays(context)
        val second = AppPrefs.getVisibleDays(context)
        assertEquals(setOf(1, 2, 3, 4, 5), first)
        assertSame("连续读取应直接返回内存缓存实例", first, second)

        AppPrefs.setVisibleDays(context, setOf(1, 2, 3, 4, 5, 6, 7))
        val third = AppPrefs.getVisibleDays(context)
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 7), third)
    }

    @Test
    fun holidayRanges_cached_in_memory_and_invalidated_on_write() {
        val ranges = listOf(
            HolidayRange(
                id = "test-1",
                name = "国庆节",
                startDate = LocalDate.of(2025, 10, 1),
                endDate = LocalDate.of(2025, 10, 7),
                type = "holiday",
                sourceKey = null,
            )
        )
        AppPrefs.setHolidayRanges(context, ranges)
        val first = AppPrefs.getHolidayRanges(context)
        val second = AppPrefs.getHolidayRanges(context)
        assertEquals(1, first.size)
        assertEquals("test-1", first[0].id)
        assertSame("连续读取应直接命中同一内存列表引用", first, second)

        AppPrefs.setHolidayRanges(context, emptyList())
        val third = AppPrefs.getHolidayRanges(context)
        assertTrue(third.isEmpty())
    }

    @Test
    fun holidayTransfers_cached_per_tableId_and_cleared_properly() {
        val table1 = 101L
        val table2 = 102L
        val transfer1 = listOf(
            HolidayTransferEntry(
                sourceDate = LocalDate.of(2025, 10, 4),
                targetDate = LocalDate.of(2025, 10, 11),
                segmentId = "seg-1"
            )
        )
        val transfer2 = listOf(
            HolidayTransferEntry(
                sourceDate = LocalDate.of(2025, 5, 2),
                targetDate = LocalDate.of(2025, 5, 10),
                segmentId = "seg-2"
            )
        )

        AppPrefs.setHolidayTransfers(context, table1, transfer1)
        AppPrefs.setHolidayTransfers(context, table2, transfer2)

        val read1A = AppPrefs.getHolidayTransfers(context, table1)
        val read1B = AppPrefs.getHolidayTransfers(context, table1)
        val read2A = AppPrefs.getHolidayTransfers(context, table2)

        assertEquals(1, read1A.size)
        assertEquals(1, read2A.size)
        assertSame("同一 tableId 连续读取命中内存缓存", read1A, read1B)
        assertEquals("seg-1", read1A[0].segmentId)
        assertEquals("seg-2", read2A[0].segmentId)

        AppPrefs.clearHolidayTransfers(context, table1)
        assertTrue("清除后读表1应为空", AppPrefs.getHolidayTransfers(context, table1).isEmpty())
        assertEquals("清除表1不应影响表2", 1, AppPrefs.getHolidayTransfers(context, table2).size)
    }

    @Test
    fun recentJwSchools_cached_in_memory() {
        AppPrefs.recordRecentJwSchool(context, "test_school_a")
        val first = AppPrefs.getRecentJwSchoolKeys(context)
        val second = AppPrefs.getRecentJwSchoolKeys(context)
        assertTrue(first.contains("test_school_a"))
        assertSame("最近导入学校应走内存缓存", first, second)
    }
}
