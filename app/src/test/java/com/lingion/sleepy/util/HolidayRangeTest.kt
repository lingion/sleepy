package com.lingion.sleepy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 范围化覆盖纯逻辑: 聚合/合并/集合展开/序列化 (不触 Context/网络) */
class HolidayRangeTest {

    private fun d(m: Int, day: Int) = LocalDate.of(2025, m, day)
    private fun entry(m: Int, day: Int, name: String, type: String) =
        HolidayEntry(d(m, day), name, type)

    // ===== 网络段聚合 =====

    @Test
    fun aggregate_merges_consecutive_same_name_same_type() {
        val segments = HolidayRangeOps.aggregateSegments(
            listOf(
                entry(2, 10, "春节", HolidayManager.TYPE_PUBLIC_HOLIDAY),
                entry(2, 11, "春节", HolidayManager.TYPE_PUBLIC_HOLIDAY),
                entry(2, 12, "春节", HolidayManager.TYPE_PUBLIC_HOLIDAY),
            )
        )
        assertEquals(1, segments.size)
        assertEquals(d(2, 10), segments[0].startDate)
        assertEquals(d(2, 12), segments[0].endDate)
    }

    @Test
    fun aggregate_splits_on_gap_or_name_or_type_change() {
        val segments = HolidayRangeOps.aggregateSegments(
            listOf(
                entry(5, 1, "劳动节", HolidayManager.TYPE_PUBLIC_HOLIDAY),
                entry(5, 2, "劳动节", HolidayManager.TYPE_PUBLIC_HOLIDAY),
                entry(5, 4, "劳动节", HolidayManager.TYPE_PUBLIC_HOLIDAY), // 5/3 断档
                entry(5, 5, "劳动节(青年节)", HolidayManager.TYPE_PUBLIC_HOLIDAY), // 名称变
                entry(4, 27, "班", HolidayManager.TYPE_TRANSFER_WORKDAY), // 类型变+乱序
            )
        )
        assertEquals(4, segments.size)
    }

    @Test
    fun aggregate_empty_and_singleton() {
        assertTrue(HolidayRangeOps.aggregateSegments(emptyList()).isEmpty())
        val one = HolidayRangeOps.aggregateSegments(listOf(entry(1, 1, "元旦", HolidayManager.TYPE_PUBLIC_HOLIDAY)))
        assertEquals(1, one.size)
        assertEquals(d(1, 1), one[0].startDate)
    }

    // ===== 合并 =====

    @Test
    fun merge_adds_new_range() {
        val ov = HolidayRange(id = "id1", name = "校庆", startDate = d(3, 8), endDate = d(3, 9),
            type = HolidayManager.TYPE_PUBLIC_HOLIDAY, sourceKey = null)
        val result = HolidayRangeOps.mergeSegments(
            listOf(entry(1, 1, "元旦", HolidayManager.TYPE_PUBLIC_HOLIDAY)), listOf(ov))
        assertEquals(2, result.active.size)
        assertTrue(result.removed.isEmpty())
    }

    @Test
    fun merge_replaces_network_segment_via_sourceKey() {
        // 网络: 春节 2/10-2/14; 用户改成 2/10-2/12
        val ov = HolidayRange("id1", "春节", d(2, 10), d(2, 12),
            HolidayManager.TYPE_PUBLIC_HOLIDAY, sourceKey = "holiday:${d(2, 10)}")
        val result = HolidayRangeOps.mergeSegments(
            (10..14).map { entry(2, it, "春节", HolidayManager.TYPE_PUBLIC_HOLIDAY) }, listOf(ov))
        assertEquals(1, result.active.size)
        assertEquals(d(2, 12), result.active[0].endDate)
    }

    @Test
    fun merge_removed_network_segment_goes_to_removed_list() {
        val ov = HolidayRange("id1", "元旦", d(1, 1), d(1, 1),
            HolidayRangeOps.REMOVED, sourceKey = "holiday:${d(1, 1)}")
        val result = HolidayRangeOps.mergeSegments(
            listOf(entry(1, 1, "元旦", HolidayManager.TYPE_PUBLIC_HOLIDAY)), listOf(ov))
        assertTrue(result.active.isEmpty())
        assertEquals(1, result.removed.size)
    }

    @Test
    fun merge_workday_sourceKey_only_kills_workday_segment() {
        val ov = HolidayRange("id1", "班", d(1, 26), d(1, 26),
            HolidayRangeOps.REMOVED, sourceKey = "workday:${d(1, 26)}")
        val result = HolidayRangeOps.mergeSegments(
            listOf(entry(1, 26, "班", HolidayManager.TYPE_TRANSFER_WORKDAY)), listOf(ov))
        assertTrue(result.active.isEmpty())
    }

    @Test
    fun merge_same_sourceKey_twice_second_wins() {
        val a = HolidayRange("id1", "春节", d(2, 10), d(2, 12),
            HolidayManager.TYPE_PUBLIC_HOLIDAY, sourceKey = "holiday:${d(2, 10)}")
        val b = HolidayRange("id2", "寒假", d(2, 10), d(2, 14),
            HolidayManager.TYPE_PUBLIC_HOLIDAY, sourceKey = "holiday:${d(2, 10)}")
        val result = HolidayRangeOps.mergeSegments(
            (10..14).map { entry(2, it, "春节", HolidayManager.TYPE_PUBLIC_HOLIDAY) }, listOf(a, b))
        // 后应用的覆盖前者: active 里只剩 id2
        assertEquals(1, result.active.size)
        assertEquals("id2", result.active[0].id)
    }

    // ===== 集合展开 =====

    @Test
    fun toSets_expands_ranges_and_splits_types() {
        val active = listOf(
            HolidayRange("id1", "春节", d(2, 10), d(2, 11), HolidayManager.TYPE_PUBLIC_HOLIDAY, null),
            HolidayRange("id2", "班", d(1, 26), d(1, 26), HolidayManager.TYPE_TRANSFER_WORKDAY, null),
        )
        val (holidays, workdays) = HolidayRangeOps.toSets(active)
        assertEquals(setOf(d(2, 10), d(2, 11)), holidays)
        assertEquals(setOf(d(1, 26)), workdays)
    }

    // ===== 序列化 =====

    @Test
    fun overrides_roundtrip_through_json() {
        val overrides = listOf(
            HolidayRange("id1", "校庆", d(3, 8), d(3, 9), HolidayManager.TYPE_PUBLIC_HOLIDAY, null),
            HolidayRange("id2", "调休", d(9, 28), d(9, 28), HolidayManager.TYPE_TRANSFER_WORKDAY, "workday:${d(9, 28)}"),
            HolidayRange("id3", "元旦", d(1, 1), d(1, 1), HolidayRangeOps.REMOVED, "holiday:${d(1, 1)}"),
        )
        val decoded = HolidayRangeOps.decodeOverrides(HolidayRangeOps.encodeOverrides(overrides))
        assertEquals(overrides, decoded)
    }

    @Test
    fun decodeOverrides_survives_garbage_and_bad_rows() {
        assertTrue(HolidayRangeOps.decodeOverrides("{not json").isEmpty())
        assertTrue(HolidayRangeOps.decodeOverrides("[]").isEmpty())
        // start > end 的段跳过
        assertTrue(HolidayRangeOps.decodeOverrides(
            """[{"id":"x","name":"n","start":"2025-03-09","end":"2025-03-08","type":"public_holiday"}]"""
        ).isEmpty())
        // 类型不认的跳过
        assertTrue(HolidayRangeOps.decodeOverrides(
            """[{"id":"x","name":"n","start":"2025-03-08","end":"2025-03-08","type":"weird"}]"""
        ).isEmpty())
    }

    @Test
    fun newId_is_8_hex_chars_and_unique() {
        val ids = (1..100).map { HolidayRangeOps.newId() }.toSet()
        assertEquals(100, ids.size)
        ids.forEach { assertTrue(it.length == 8); assertTrue(it.all { c -> c.isDigit() || c in 'a'..'f' }) }
    }
}
