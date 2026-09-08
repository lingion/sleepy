package com.lingion.sleepy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** issue#23 §2.2 候选集合规则 + updateEdgeNodeTimes 槽位时间编辑 */
class EdgeCandidatesTest {

    private fun json(rows: List<TimeTableUtils.TimeSlotRow>): String =
        TimeTableUtils.buildTimeJsonFromRows(rows)

    private fun std(n: Int) = TimeTableUtils.TimeSlotRow(n, "08:00", "08:45")

    private fun edge(n: Int, s: String, e: String, cls: TimeTableUtils.EdgeClass) =
        TimeTableUtils.TimeSlotRow(n, s, e, cls)

    @Test
    fun 纯标准节次_候选为新建0和新13() {
        val candidates = TimeTableUtils.edgeCandidates(json((1..12).map { std(it) }))
        assertEquals(2, candidates.size)
        assertEquals(0, candidates[0].node)
        assertFalse(candidates[0].exists)
        assertEquals("", candidates[0].start)
        assertEquals(13, candidates[1].node)
        assertFalse(candidates[1].exists)
    }

    @Test
    fun 已有0号槽位_候选为负1复用0和新13() {
        val candidates = TimeTableUtils.edgeCandidates(
            json((1..12).map { std(it) } + edge(0, "08:00", "08:30", TimeTableUtils.EdgeClass.Before))
        )
        assertEquals(3, candidates.size)
        assertEquals(-1, candidates[0].node)
        assertFalse(candidates[0].exists)
        assertEquals(0, candidates[1].node)
        assertTrue(candidates[1].exists)
        assertEquals("08:00", candidates[1].start)
        assertEquals("08:30", candidates[1].end)
        assertEquals(13, candidates[2].node)
        assertFalse(candidates[2].exists)
    }

    @Test
    fun 已有负1到14_候选按升序含全部槽位加两个新建() {
        val candidates = TimeTableUtils.edgeCandidates(
            json(
                (1..12).map { std(it) } +
                    edge(-1, "07:30", "07:55", TimeTableUtils.EdgeClass.Before) +
                    edge(0, "08:00", "08:30", TimeTableUtils.EdgeClass.Before) +
                    edge(13, "18:00", "18:40", TimeTableUtils.EdgeClass.After) +
                    edge(14, "19:00", "19:40", TimeTableUtils.EdgeClass.After)
            )
        )
        // §2.2: Before 组升序(-2 新建, -1, 0) + After 组升序(13, 14, 15 新建)
        assertEquals(listOf(-2, -1, 0, 13, 14, 15), candidates.map { it.node })
        assertEquals(
            listOf(false, true, true, true, true, false),
            candidates.map { it.exists }
        )
        assertEquals("07:30", candidates[1].start)
        assertEquals("18:00", candidates[3].start)
    }

    @Test
    fun updateEdgeNodeTimes_改边缘行生效() {
        val base = json((1..12).map { std(it) } + edge(0, "08:00", "08:30", TimeTableUtils.EdgeClass.Before))
        val updated = TimeTableUtils.updateEdgeNodeTimes(base, 0, "07:50", "08:20")
        val row = TimeTableUtils.parseTimeSlotRows(updated).first { it.node == 0 }
        assertEquals("07:50", row.start)
        assertEquals("08:20", row.end)
        // 标准行不受影响
        val stdRow = TimeTableUtils.parseTimeSlotRows(updated).first { it.node == 5 }
        assertEquals("08:00", stdRow.start)
    }

    @Test
    fun updateEdgeNodeTimes_标准行原样返回() {
        val base = json((1..5).map { std(it) })
        assertEquals(base, TimeTableUtils.updateEdgeNodeTimes(base, 5, "00:00", "01:00"))
    }

    @Test
    fun 损坏timeJson返回原样() {
        val updated = TimeTableUtils.updateEdgeNodeTimes("not json", 0, "07:50", "08:20")
        assertEquals("not json", updated)
    }
}
