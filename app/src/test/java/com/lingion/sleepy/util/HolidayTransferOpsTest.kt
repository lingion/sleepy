package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
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

    // ============================ issue#145: 调休改写应用层契约 ============================
    //
    // 场景: 10-07(周三, day=3) 调休到 10-10(周六, day=6)。
    // daySwap = {3 → 6} 写课程改写逻辑后, 10-10 那一列 (day=6) 上既不能出现 10-07
    // 改写过来的课 ∪ 10-10 原 day=6 课(融合),也不能让两者都丢; 用户语义:
    // 10-10 显示「调过来的 10-07 课」,原 day=6 课被屏蔽。
    //
    // 修法: daySwap.values 集合 = 补班日所在 weekday 集合, 在改写 c.day 前先
    // 过滤 c.day ∈ daySwap.values 且 c.day ∉ daySwap.keySet() 的课(即原本属于
    // 补班日那列、且不属于被调走日 source 的课)。
    //
    // 抽出 applyDaySwap 纯函数到 HolidayTransferOps, 渲染层直接调用,
    // 闭包不再内联 — 同 effectiveDayOfWeek 一致(纯函数可测, 真值唯一来源)。

    private fun course(id: Long, day: Int, name: String = "课") = CourseEntity(
        id = id, groupId = "g$id", tableId = 1L, courseName = name,
        day = day, startNode = 1, step = 2, startWeek = 1, endWeek = 16, type = 0,
        color = "#FF6750A4"
    )

    @Test
    fun applyDaySwap_moves_source_courses_to_target_day() {
        val courses = listOf(
            course(1, day = 3, name = "周三课"),  // 10-07 来源
            course(2, day = 6, name = "周六课")   // 10-10 原课(将被屏蔽)
        )
        val daySwap = mapOf(3 to 6)
        val out = Ops.applyDaySwap(courses, daySwap)
        // 来源课改写到 target weekday
        val moved = out.first { it.id == 1L }
        assertEquals(6, moved.day)
        assertEquals("周三课", moved.courseName)
        // 原 target weekday 课被屏蔽
        assertEquals(null, out.firstOrNull { it.id == 2L })
    }

    @Test
    fun applyDaySwap_empty_map_returns_courses_unchanged() {
        val courses = listOf(course(1, 3), course(2, 6))
        val out = Ops.applyDaySwap(courses, emptyMap())
        assertEquals(courses, out)
    }

    @Test
    fun applyDaySwap_multiple_targets_each_source_moves_into_its_own_target() {
        // 周三(d=3)→周六(d=6), 周日(d=7)→周四(d=4)
        val courses = listOf(
            course(1, 3, "周三"),
            course(2, 6, "周六原"),
            course(3, 7, "周日"),
            course(4, 4, "周四原")
        )
        val daySwap = mapOf(3 to 6, 7 to 4)
        val out = Ops.applyDaySwap(courses, daySwap)
        val byId = out.associateBy { it.id }
        assertEquals(6, byId[1]?.day)   // 周三 → 改写到 6
        assertEquals(null, byId[2])     // 原周六屏蔽
        assertEquals(4, byId[3]?.day)   // 周日 → 改写到 4
        assertEquals(null, byId[4])     // 原周四屏蔽
    }

    @Test
    fun applyDaySwap_unmapped_day_courses_untouched() {
        val courses = listOf(
            course(1, 1, "周一"),  // 未参与 daySwap
            course(2, 3, "周三调走"),
            course(3, 6, "周六被屏蔽")
        )
        val daySwap = mapOf(3 to 6)
        val out = Ops.applyDaySwap(courses, daySwap)
        assertEquals(1, out.first { it.id == 1L }.day)  // 周一不动
        assertEquals(6, out.first { it.id == 2L }.day)  // 周三→6
        assertEquals(null, out.firstOrNull { it.id == 3L })  // 周六屏蔽
    }
}