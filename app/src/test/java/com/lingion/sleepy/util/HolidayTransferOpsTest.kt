package com.lingion.sleepy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * issue#44 第二轮: HolidayTransferEntry 模型与互斥编解码。
 * - entry 三字段 (sourceDate / targetDate / segmentId)
 * - encode/decode 纯函数, 坏行跳过; 同 sourceDate 重复只留最后一条
 * - 应用层互斥写 (`withTargetExclusivity`) 按 targetDate 去重, 后写覆盖前写
 */
class HolidayTransferOpsTest {

    private val d = { m: Int, day: Int -> LocalDate.of(2026, m, day) }
    private val Ops = HolidayRangeOps.HolidayTransferOps

    @Test
    fun encodeDecode_roundTrips_list() {
        val input = listOf(
            HolidayTransferEntry(d(1, 1), d(1, 4), "seg-y"),
            HolidayTransferEntry(d(2, 15), d(2, 14), "seg-c")
        )
        val out = Ops.decodeTransfers(Ops.encodeTransfers(input))
        assertEquals(input, out)
    }

    @Test
    fun decode_skips_invalid_rows_and_dedupes_sourceDate() {
        val json = """
            [
              {"sourceDate":"2026-01-01","targetDate":"2026-01-04","segmentId":"a"},
              {"sourceDate":"bad","targetDate":"2026-01-04","segmentId":"skip"},
              {"sourceDate":"2026-01-02","targetDate":"not-a-date","segmentId":"skip"},
              {"sourceDate":"2026-01-03","targetDate":"2026-01-04","segmentId":"first"},
              {"sourceDate":"2026-01-03","targetDate":"2026-01-05","segmentId":"second"}
            ]
        """.trimIndent()
        val out = Ops.decodeTransfers(json)
        // 1/3 重复, 留 last "second" → 1/5
        assertEquals(2, out.size)
        assertEquals(HolidayTransferEntry(d(1, 1), d(1, 4), "a"), out[0])
        assertEquals(HolidayTransferEntry(d(1, 3), d(1, 5), "second"), out[1])
    }

    @Test
    fun decode_empty_or_invalid_returns_empty_list() {
        assertEquals(emptyList<HolidayTransferEntry>(), Ops.decodeTransfers(""))
        assertEquals(emptyList<HolidayTransferEntry>(), Ops.decodeTransfers("not json"))
        assertEquals(emptyList<HolidayTransferEntry>(), Ops.decodeTransfers("[]"))
    }

    @Test
    fun withTargetExclusivity_removes_prior_entry_with_same_targetDate() {
        val prior = listOf(
            HolidayTransferEntry(d(1, 1), d(1, 4), "seg-y"),
            HolidayTransferEntry(d(1, 2), d(1, 5), "seg-y")
        )
        val new = HolidayTransferEntry(d(1, 3), d(1, 4), "seg-y")
        val out = Ops.withTargetExclusivity(prior, new)
        // 1/4 之前是 1/1 → 1/4 现在被 1/3 → 1/4 替换; 1/1 这条没了; 1/2 → 1/5 保留
        assertEquals(2, out.size)
        assertTrue(out.any { it.sourceDate == d(1, 3) && it.targetDate == d(1, 4) })
        assertTrue(out.any { it.sourceDate == d(1, 2) && it.targetDate == d(1, 5) })
        assertTrue(out.none { it.sourceDate == d(1, 1) })
    }

    @Test
    fun withTargetExclusivity_appends_when_target_unused() {
        val prior = listOf(HolidayTransferEntry(d(1, 1), d(1, 4), "a"))
        val new = HolidayTransferEntry(d(1, 2), d(1, 5), "a")
        val out = Ops.withTargetExclusivity(prior, new)
        assertEquals(2, out.size)
        assertTrue(out.contains(new))
        assertTrue(out.contains(prior[0]))
    }

    @Test
    fun withTargetExclusivity_empty_prior_yields_singleton() {
        val new = HolidayTransferEntry(d(1, 1), d(1, 4), "a")
        val out = Ops.withTargetExclusivity(emptyList(), new)
        assertEquals(listOf(new), out)
    }

    @Test
    fun transferFor_returns_entry_for_sourceDate_or_null() {
        val t = listOf(
            HolidayTransferEntry(d(1, 1), d(1, 4), "a"),
            HolidayTransferEntry(d(1, 2), d(1, 5), "a")
        )
        assertNotNull(Ops.transferFor(d(1, 1), t))
        assertNull(Ops.transferFor(d(1, 3), t))
    }

    @Test
    fun effectiveDayOfWeek_uses_targetDate_dayOfWeek() {
        // 1/1 是周四 → 映射到 1/4 周日 → 应取周日的星期 7
        val transfers = listOf(HolidayTransferEntry(d(1, 1), d(1, 4), "a"))
        assertEquals(7, Ops.effectiveDayOfWeek(d(1, 1), transfers))
    }

    @Test
    fun effectiveDayOfWeek_falls_back_to_natural_when_unmapped() {
        val transfers = listOf(HolidayTransferEntry(d(1, 1), d(1, 4), "a"))
        // 1/2 是周五, 未映射 → 自然星期 5
        assertEquals(5, Ops.effectiveDayOfWeek(d(1, 2), transfers))
    }

    @Test
    fun isOrphanFor_returns_true_when_sourceDate_not_in_year_holidays() {
        val e = HolidayTransferEntry(d(1, 1), d(1, 4), "a")
        val yearHolidays = setOf(d(1, 2), d(1, 3)) // 1/1 不在
        assertTrue(Ops.isOrphanFor(e, yearHolidays))
    }

    @Test
    fun isOrphanFor_returns_false_when_sourceDate_in_year_holidays() {
        val e = HolidayTransferEntry(d(1, 1), d(1, 4), "a")
        val yearHolidays = setOf(d(1, 1), d(1, 2))
        assertEquals(false, Ops.isOrphanFor(e, yearHolidays))
    }
}