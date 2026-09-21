package com.lingion.sleepy.data

import com.lingion.sleepy.data.entity.TimeTableEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** C2 issue#40: 绑定不覆盖用户作息, 解绑恢复首次绑定前快照。 */
class TimeTableBindingSnapshotTest {

    private fun table(
        periodTableId: Long? = null,
        timeJson: String = """[{"node":1,"start":"08:20","end":"09:05"}]""",
        nodesPerDay: Int = 1,
        smartConfigJson: String = "manual",
        preBindSnapshotJson: String = ""
    ) = TimeTableEntity(
        id = 7,
        name = "正式课表",
        startDate = "2026-02-23",
        maxWeek = 16,
        nodesPerDay = nodesPerDay,
        timeJson = timeJson,
        smartConfigJson = smartConfigJson,
        periodTableId = periodTableId,
        preBindSnapshotJson = preBindSnapshotJson
    )

    @Test
    fun first_bind_keeps_compat_columns_and_records_snapshot() {
        val original = table()
        val bound = TimeTableEntity.snapshotForBind(original, targetId = 42)

        assertEquals(42L, bound.periodTableId)
        assertEquals(original.timeJson, bound.timeJson)
        assertEquals(original.nodesPerDay, bound.nodesPerDay)
        assertEquals(original.smartConfigJson, bound.smartConfigJson)
        assertTrue("snapshot must carry original timeJson", bound.preBindSnapshotJson.contains("08:20"))
        assertTrue("snapshot must carry original nodesPerDay", bound.preBindSnapshotJson.contains("\"n\":1"))
    }

    @Test
    fun rebind_does_not_refresh_first_snapshot() {
        val first = TimeTableEntity.snapshotForBind(table(), targetId = 42)
        val rebound = first.copy(periodTableId = 99)

        assertEquals(first.preBindSnapshotJson, rebound.preBindSnapshotJson)
        assertEquals(first.timeJson, rebound.timeJson)
    }

    @Test
    fun unbind_restores_manual_columns_and_clears_snapshot() {
        val original = table()
        val bound = TimeTableEntity.snapshotForBind(original, targetId = 42).copy(
            timeJson = """[{"node":1,"start":"08:10","end":"08:50"}]""",
            smartConfigJson = "tampered"
        )
        val unbound = TimeTableEntity.restoredForUnbind(bound)

        assertEquals(null, unbound.periodTableId)
        assertEquals(original.timeJson, unbound.timeJson)
        assertEquals(original.nodesPerDay, unbound.nodesPerDay)
        assertEquals(original.smartConfigJson, unbound.smartConfigJson)
        assertEquals("", unbound.preBindSnapshotJson)
    }

    @Test
    fun unbind_without_snapshot_falls_back_to_current_compat_columns() {
        val corrupted = table(periodTableId = 99, timeJson = "[]", preBindSnapshotJson = "")
        val unbound = TimeTableEntity.restoredForUnbind(corrupted)

        assertEquals(null, unbound.periodTableId)
        assertEquals("[]", unbound.timeJson)
    }
}
