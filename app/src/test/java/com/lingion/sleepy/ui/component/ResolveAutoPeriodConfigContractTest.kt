package com.lingion.sleepy.ui.component

import com.lingion.sleepy.data.entity.DurationOption
import com.lingion.sleepy.data.entity.SmartPeriodConfig
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.util.TimeTableUtils.EdgeClass
import com.lingion.sleepy.util.TimeTableUtils.TimeSlotRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * issue#23 Task 4 — 进自动模式配置同步契约:
 *   1) 手动 45/30 行 → 进自动模式后派生配置 primary=45, 30 出现在第 3 节点(顺序正确),
 *      仍能反推回原行的起止;
 *   2) stored 配置能 derive 出当前标准行 → 原样保留(含 durations/breaks 标签);
 *   3) stored 配置对不上当前行 → 从当前行重推断(以 stored 作为 previous 承接标签);
 *   4) 任何有效标准起止对 → resolve → stored 再 resolve → derive 回到原行(node + start + end);
 *   5) edge 行永不参与推断/比对;
 *   6) 行不可推断(空/畸形) → 返回 null(由调用方决定兜底, 不强制默认值)。
 */
class ResolveAutoPeriodConfigContractTest {

    private fun row(node: Int, start: String, end: String, edge: EdgeClass? = null) =
        TimeSlotRow(node, start, end, edge)

    @Test
    fun `45-30 manual rows become primary 45 with 30 group at node 3`() {
        val rows = listOf(
            row(1, "08:00", "08:45"),
            row(2, "08:55", "09:40"),
            row(3, "10:10", "10:40")
        )

        val resolved = resolveAutoPeriodConfig(rows, null)

        assertNotNull(resolved)
        assertEquals(45, resolved!!.periodMinutes)
        assertEquals(3, resolved.totalPeriods)
        assertEquals(listOf(DurationOption(30, isLong = false)), resolved.durations)
        assertEquals(listOf<Int?>(null, null, 0), resolved.periodAssignments)
        assertEquals(listOf("08:00" to "08:45", "08:55" to "09:40", "10:10" to "10:40"),
            resolved.derive().map { it.start to it.end })
    }

    @Test
    fun `stored config preserved when it still derives the current rows`() {
        val rows = listOf(
            row(1, "08:00", "08:45"),
            row(2, "08:55", "09:40"),
            row(3, "10:10", "10:40")
        )
        val stored = resolveAutoPeriodConfig(rows, null)!!
        val labelled = stored.copy(
            durations = listOf(DurationOption(30, isLong = true, label = "短课"))
        )

        val resolved = resolveAutoPeriodConfig(rows, labelled)

        assertNotNull(resolved)
        assertSame(labelled, resolved)
        assertEquals("短课", resolved!!.durations.single().label)
    }

    @Test
    fun `stored config is re-inferred when it no longer matches current rows`() {
        val originalRows = listOf(
            row(1, "08:00", "08:45"),
            row(2, "08:55", "09:40"),
            row(3, "10:10", "10:40")
        )
        val stored = resolveAutoPeriodConfig(originalRows, null)!!
        // user reorders period 3 start — stored's derive no longer matches new rows
        val mutated = listOf(
            row(1, "08:00", "08:45"),
            row(2, "08:55", "09:40"),
            row(3, "10:00", "10:30")
        )

        val resolved = resolveAutoPeriodConfig(mutated, stored)

        assertNotNull(resolved)
        assertEquals(45, resolved!!.periodMinutes)
        assertEquals(3, resolved.totalPeriods)
        assertEquals(listOf("08:00" to "08:45", "08:55" to "09:40", "10:00" to "10:30"),
            resolved.derive().map { it.start to it.end })
    }

    @Test
    fun `round trip preserves every valid standard start end pair`() {
        val rows = listOf(
            row(1, "08:00", "08:45"),
            row(2, "08:55", "09:25"),
            row(3, "10:25", "11:10"),
            row(4, "11:20", "12:05"),
            row(5, "13:30", "14:15"),
            row(6, "14:25", "14:55")
        )

        val firstPass = resolveAutoPeriodConfig(rows, null)
        assertNotNull(firstPass)

        // Stored config used as input on second pass must still derive the exact rows,
        // proving resolve→stored→resolve→derive is a fixed point for valid inputs.
        val secondPass = resolveAutoPeriodConfig(rows, firstPass)
        assertSame(firstPass, secondPass)
        assertEquals(rows.map { it.node }, secondPass!!.derive().map { it.node })
        assertEquals(rows.map { it.start to it.end }, secondPass.derive().map { it.start to it.end })
    }

    @Test
    fun `edge rows are excluded from inference and from derive match`() {
        val standard = listOf(
            row(1, "08:00", "08:45"),
            row(2, "08:55", "09:40"),
            row(3, "09:50", "10:35")
        )
        val rowsWithEdges = listOf(
            row(0, "07:00", "07:59", EdgeClass.Before),
            standard[0],
            standard[1],
            standard[2],
            row(4, "22:00", "23:00", EdgeClass.After)
        )

        val resolved = resolveAutoPeriodConfig(rowsWithEdges, null)

        assertNotNull(resolved)
        assertEquals(3, resolved!!.totalPeriods)
        assertEquals(standard.map { it.start to it.end },
            resolved.derive().map { it.start to it.end })
        // Same rows-with-edges fed back: stored's derive (3 standard rows) matches the
        // 3 standard rows we passed in, so the stored handle is preserved verbatim.
        assertSame(resolved, resolveAutoPeriodConfig(rowsWithEdges, resolved))
    }

    @Test
    fun `existing auto config changes still sync derived rows`() {
        val rows = listOf(
            row(1, "08:00", "08:45"),
            row(2, "08:55", "09:40")
        )
        val resolved = resolveAutoPeriodConfig(rows, null)!!

        // Simulate the user editing duration group minutes inside the Auto tab: a copy
        // should still derive exactly the rows they intended (no surprise loss of sync).
        val edited = resolved.copy(periodMinutes = 50)
        assertEquals(listOf("08:00" to "08:50", "09:00" to "09:50"),
            edited.derive().map { it.start to it.end })
        // And the resolver still treats the edited config as the authoritative stored
        // handle for matching rows: feeding the derived rows back with the edited
        // config stored must return the same instance (no silent re-inference).
        val rowsForEdited = edited.derive().map { TimeSlotRow(it.node, it.start, it.end) }
        assertSame(edited, resolveAutoPeriodConfig(rowsForEdited, edited))
    }

    @Test
    fun `uninferable rows return null so caller can decide fallback`() {
        assertNull(resolveAutoPeriodConfig(emptyList(), null))
        assertNull(resolveAutoPeriodConfig(listOf(row(1, "bad", "08:45")), null))
        assertNull(resolveAutoPeriodConfig(listOf(row(1, "09:00", "08:45")), null))
        assertNull(resolveAutoPeriodConfig(
            listOf(row(1, "08:00", "08:45"), row(3, "08:55", "09:40")), null))
    }

    @Test
    fun `stored config preserved when it still derives rows with edge rows ignored`() {
        // previously saved config derives the bare standard rows; caller passes the
        // current row list which includes edge rows. Stored must still be preserved.
        val standard = listOf(
            row(1, "08:00", "08:45"),
            row(2, "08:55", "09:40"),
            row(3, "10:10", "10:40")
        )
        val stored = resolveAutoPeriodConfig(standard, null)!!
        val labelled = stored.copy(
            durations = listOf(DurationOption(30, isLong = false, label = "短课"))
        )

        val rowsWithEdges = listOf(
            row(0, "07:00", "07:59", EdgeClass.Before),
            standard[0], standard[1], standard[2],
            row(4, "22:00", "23:00", EdgeClass.After)
        )

        val resolved = resolveAutoPeriodConfig(rowsWithEdges, labelled)

        assertNotNull(resolved)
        assertSame(labelled, resolved)
        assertTrue(resolved!!.durations.single().label == "短课")
    }

    @Test
    fun `resolve util delegates to inferSmartPeriodConfig for unstored path`() {
        // smoke test: ensures resolveAutoPeriodConfig(null stored) yields a non-null
        // result by delegating to TimeTableUtils.inferSmartPeriodConfig.
        val rows = listOf(
            row(1, "08:00", "08:45"),
            row(2, "08:55", "09:40"),
            row(3, "09:50", "10:35")
        )
        val resolved = resolveAutoPeriodConfig(rows, null)
        val expected = TimeTableUtils.inferSmartPeriodConfig(rows)
        assertNotNull(resolved)
        assertNotNull(expected)
        assertEquals(expected!!.periodMinutes, resolved!!.periodMinutes)
        assertEquals(expected.totalPeriods, resolved.totalPeriods)
        assertEquals(expected.durations, resolved.durations)
        assertEquals(expected.breaks, resolved.breaks)
        assertEquals(expected.periodAssignments, resolved.periodAssignments)
        assertEquals(expected.transitionAssignments, resolved.transitionAssignments)
    }
}