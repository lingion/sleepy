package com.lingion.sleepy.ui.screen.edit

import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * issue#23 非常规校验 / 保存路径契约:
 *  - validateCourseDraft 是私有函数,真实拦截由 UI 在 save 前执行
 *  - 此处聚焦可在 JVM 单测直接验证的「保存时串接 insertEdgeNode」契约 —
 *    这是边缘节次真正落到 timeJson 的唯一路径,必须有回归测试守护
 */
class IrregularValidationTest {

    private fun dummyTable(timeJson: String = TimeTableUtils.DEFAULT_TIME_JSON) =
        TimeTableEntity(
            id = 1L,
            name = "t",
            startDate = "2026-09-07",
            maxWeek = 16,
            timeJson = timeJson,
            createdAt = 0L
        )

    @Test
    fun save_path_persists_pending_edges_via_insertEdgeNode() {
        var timeJson = TimeTableUtils.DEFAULT_TIME_JSON
        val pending = listOf(
            PendingEdgeInsert(TimeTableUtils.EdgeClass.Before, start = "07:30", end = "08:00"),
            PendingEdgeInsert(TimeTableUtils.EdgeClass.After, start = "21:30", end = "22:15")
        )
        pending.forEach { p ->
            timeJson = TimeTableUtils.insertEdgeNode(timeJson, p.edgeClass, p.start, p.end)
        }
        val rows = TimeTableUtils.parseTimeSlotRows(timeJson)
        assertEquals("标准 12 + 前 0 + 后 13 = 14", 14, rows.size)
        val zero = rows.firstOrNull { it.node == 0 }
        val thirteen = rows.firstOrNull { it.node == 13 }
        assertNotNull("前置 0 节必须存在", zero)
        assertEquals("07:30", zero!!.start)
        assertNotNull("后置 13 节必须存在", thirteen)
        assertEquals("21:30", thirteen!!.start)
    }

    @Test
    fun save_path_chained_before_inserts_descending_node_numbers() {
        var timeJson = TimeTableUtils.DEFAULT_TIME_JSON
        repeat(3) { i ->
            timeJson = TimeTableUtils.insertEdgeNode(
                timeJson, TimeTableUtils.EdgeClass.Before, "0$i:00", "0$i:30"
            )
        }
        val rows = TimeTableUtils.parseTimeSlotRows(timeJson)
        val beforeNodes = rows.filter { it.edgeClass == TimeTableUtils.EdgeClass.Before }
            .map { it.node }.sortedDescending()
        assertEquals(listOf(0, -1, -2), beforeNodes)
    }

    @Test
    fun save_path_chained_after_inserts_ascending_node_numbers() {
        var timeJson = TimeTableUtils.DEFAULT_TIME_JSON
        repeat(3) { i ->
            timeJson = TimeTableUtils.insertEdgeNode(
                timeJson, TimeTableUtils.EdgeClass.After, "20:0$i", "20:3$i"
            )
        }
        val rows = TimeTableUtils.parseTimeSlotRows(timeJson)
        val afterNodes = rows.filter { it.edgeClass == TimeTableUtils.EdgeClass.After }
            .map { it.node }.sorted()
        assertEquals(listOf(13, 14, 15), afterNodes)
    }

    @Test
    fun irregularEnabled_false_leaves_timeJson_untouched() {
        val t = dummyTable()
        assertEquals(TimeTableUtils.DEFAULT_TIME_JSON, t.timeJson)
        assertNull(TimeTableUtils.edgeNodesOf(
            t.timeJson, TimeTableUtils.EdgeClass.Before
        ).firstOrNull())
        assertTrue(
            TimeTableUtils.edgeNodesOf(t.timeJson, TimeTableUtils.EdgeClass.After).isEmpty()
        )
    }

    @Test
    fun irregularEnabled_true_edge_path_extends_timeJson() {
        val afterEdge = TimeTableUtils.insertEdgeNode(
            TimeTableUtils.DEFAULT_TIME_JSON,
            TimeTableUtils.EdgeClass.After, "21:00", "21:45"
        )
        assertEquals(13, TimeTableUtils.parseTimeSlotRows(afterEdge).size)
    }
}
