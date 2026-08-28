package com.lingion.sleepy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 覆盖层纯逻辑: 序列化/合并/删除 (不触 Context/网络) */
class HolidayOverrideTest {

    private val holiday = HolidayEntry(LocalDate.of(2025, 1, 1), "元旦", HolidayManager.TYPE_PUBLIC_HOLIDAY)
    private val workday = HolidayEntry(LocalDate.of(2025, 1, 26), "春节补班", HolidayManager.TYPE_TRANSFER_WORKDAY)
    private val networkEntries = listOf(holiday, workday)

    // ===== JSON 序列化往返 =====

    @Test
    fun overrides_roundtrip_through_json() {
        val overrides = mapOf(
            LocalDate.of(2025, 3, 8) to HolidayEntry(LocalDate.of(2025, 3, 8), "校庆", HolidayManager.TYPE_PUBLIC_HOLIDAY),
            LocalDate.of(2025, 9, 28) to HolidayEntry(LocalDate.of(2025, 9, 28), "调休上课", HolidayManager.TYPE_TRANSFER_WORKDAY)
        )
        val json = HolidayManager.encodeOverrides(overrides)
        val decoded = HolidayManager.decodeOverrides(json)
        assertEquals(overrides, decoded)
    }

    @Test
    fun decodeOverrides_survives_garbage_and_bad_rows() {
        assertTrue(HolidayManager.decodeOverrides("{not json").isEmpty())
        assertTrue(HolidayManager.decodeOverrides("{}").isEmpty())
        assertTrue(HolidayManager.decodeOverrides("""{"bad-date":{"name":"x","type":"public_holiday"}}""").isEmpty())
        assertTrue(HolidayManager.decodeOverrides("""{"2025-01-01":{"name":"x","type":"unknown"}}""").isEmpty())
    }

    @Test
    fun decodeOverrides_accepts_empty_name() {
        val decoded = HolidayManager.decodeOverrides("""{"2025-01-01":{"name":"","type":"public_holiday"}}""")
        assertEquals("", decoded[LocalDate.of(2025, 1, 1)]?.name)
    }

    // ===== 合并语义 =====

    @Test
    fun merge_appends_custom_entries() {
        val custom = HolidayEntry(LocalDate.of(2025, 3, 8), "校庆", HolidayManager.TYPE_PUBLIC_HOLIDAY)
        val merged = HolidayManager.mergeEntries(networkEntries, mapOf(custom.date to custom))
        assertTrue(custom in merged)
        assertTrue(holiday in merged)
    }

    @Test
    fun merge_edited_entry_replaces_network_row() {
        val edited = holiday.copy(name = "元旦(改)")
        val merged = HolidayManager.mergeEntries(networkEntries, mapOf(holiday.date to edited))
        assertEquals(1, merged.count { it.date == holiday.date })
        assertEquals("元旦(改)", merged.first { it.date == holiday.date }.name)
    }

    @Test
    fun merge_removed_entry_is_absent() {
        val merged = HolidayManager.mergeEntries(
            networkEntries,
            mapOf(holiday.date to HolidayEntry(holiday.date, "", HolidayManager.OVERRIDE_REMOVED))
        )
        assertNull(merged.firstOrNull { it.date == holiday.date })
        assertTrue(workday in merged) // 未覆盖的条目不受影响
    }

    @Test
    fun merge_result_sorted_by_date() {
        val custom = HolidayEntry(LocalDate.of(2024, 12, 31), "跨年", HolidayManager.TYPE_PUBLIC_HOLIDAY)
        val merged = HolidayManager.mergeEntries(networkEntries, mapOf(custom.date to custom))
        assertEquals(merged.sortedBy { it.date }, merged)
    }

    @Test
    fun merge_keeps_override_type_for_known_dates() {
        // 用户把法定假日改成补班日 → 合并后类型跟覆盖走
        val flipped = holiday.copy(name = holiday.name, type = HolidayManager.TYPE_TRANSFER_WORKDAY)
        val merged = HolidayManager.mergeEntries(networkEntries, mapOf(holiday.date to flipped))
        assertEquals(HolidayManager.TYPE_TRANSFER_WORKDAY, merged.first { it.date == holiday.date }.type)
    }

    // ===== 灰显判定含覆盖 =====

    @Test
    fun decideGrey_override_removed_day_is_not_greyed_even_on_weekend() {
        // 周六被用户标记"不灰显"(移除) → 即使周末灰显开关开着也不灰
        assertFalse(
            HolidayManager.decideGreyWithOverrides(
                date = saturday,
                overrides = mapOf(saturday to HolidayEntry(saturday, "", HolidayManager.OVERRIDE_REMOVED)),
                holidays = setOf(saturday),
                workdays = emptySet(),
                greyHoliday = true, greyWeekend = true, ignoreWorkday = true
            )
        )
    }

    @Test
    fun decideGrey_override_workday_not_greyed() {
        // 周日被用户标记为补班(上课) → 不灰, 不受 ignoreWorkday 开关影响
        assertFalse(
            HolidayManager.decideGreyWithOverrides(
                date = sundayWorkday,
                overrides = mapOf(sundayWorkday to HolidayEntry(sundayWorkday, "自习", HolidayManager.TYPE_TRANSFER_WORKDAY)),
                holidays = emptySet(),
                workdays = emptySet(),
                greyHoliday = true, greyWeekend = true, ignoreWorkday = false
            )
        )
    }

    @Test
    fun decideGrey_override_holiday_greyed_even_if_weekday() {
        // 工作日被用户标记为假日 → 灰, 不受周末开关影响
        assertTrue(
            HolidayManager.decideGreyWithOverrides(
                date = weekday,
                overrides = mapOf(weekday to HolidayEntry(weekday, "校庆", HolidayManager.TYPE_PUBLIC_HOLIDAY)),
                holidays = emptySet(),
                workdays = emptySet(),
                greyHoliday = true, greyWeekend = false, ignoreWorkday = false
            )
        )
    }

    @Test
    fun decideGrey_without_overrides_falls_back_to_base_logic() {
        assertTrue(
            HolidayManager.decideGreyWithOverrides(
                date = holiday.date, overrides = emptyMap(),
                holidays = setOf(holiday.date), workdays = emptySet(),
                greyHoliday = true, greyWeekend = false, ignoreWorkday = false
            )
        )
        assertFalse(
            HolidayManager.decideGreyWithOverrides(
                date = weekday, overrides = emptyMap(),
                holidays = emptySet(), workdays = emptySet(),
                greyHoliday = true, greyWeekend = true, ignoreWorkday = false
            )
        )
    }

    private val saturday = LocalDate.of(2025, 1, 4)
    private val sundayWorkday = LocalDate.of(2025, 1, 26)
    private val weekday = LocalDate.of(2025, 1, 6)
}
