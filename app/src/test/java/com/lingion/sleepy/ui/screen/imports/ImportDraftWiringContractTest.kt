package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.data.entity.DurationOption
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

import org.junit.Test

/** New imports bypass drafts; old persisted drafts remain readable. */
class ImportDraftWiringContractTest {

    // --- issue#23 Task 5: 导入边界共享推断契约 ---

    @Test
    fun `imported rows with 45-min majority and one 30-min row yield correct smart config`() {
        // 真实复刻从教务抓回的混合时长节次: 4 节, 第 3 节 30 分钟.
        val rows = listOf(
            TimeTableUtils.TimeSlotRow(1, "08:00", "08:45"),
            TimeTableUtils.TimeSlotRow(2, "08:55", "09:40"),
            TimeTableUtils.TimeSlotRow(3, "09:50", "10:20"),
            TimeTableUtils.TimeSlotRow(4, "10:30", "11:15"),
        )

        val inferred = TimeTableUtils.inferSmartPeriodConfig(rows)

        assertEquals(45, inferred!!.periodMinutes)
        assertEquals(4, inferred.totalPeriods)
        assertEquals(listOf(DurationOption(30, isLong = false)), inferred.durations)
        assertEquals(
            listOf<Int?>(null, null, 0, null),
            inferred.periodAssignments,
        )
        assertEquals(
            listOf("08:00" to "08:45", "08:55" to "09:40", "09:50" to "10:20", "10:30" to "11:15"),
            inferred.derive().map { it.start to it.end },
        )
    }

    @Test
    fun `incomplete imported rows keep manual mode and do not write guessed defaults`() {
        // 第 2 节 start 空白 — inferSmartPeriodConfig 必须返回 null, 调用方回退最简默认,
        // TimeSlotEditor 保持手动模式 + 既有校验 (RegExp 兜底), 不写猜测值.
        val rows = listOf(
            TimeTableUtils.TimeSlotRow(1, "08:00", "08:45"),
            TimeTableUtils.TimeSlotRow(2, "", "09:30"),
        )

        assertNull(TimeTableUtils.inferSmartPeriodConfig(rows))
    }

    @Test
    fun `edge rows are excluded from imported inference`() {
        // 真实导入边界: 早读/晚自习节点 (edgeClass) 不应进入主时长推断,
        // 否则衍生时会扭曲节次数.
        val rows = listOf(
            TimeTableUtils.TimeSlotRow(0, "07:00", "07:59", TimeTableUtils.EdgeClass.Before),
            TimeTableUtils.TimeSlotRow(1, "08:00", "08:45"),
            TimeTableUtils.TimeSlotRow(2, "08:55", "09:40"),
            TimeTableUtils.TimeSlotRow(3, "09:50", "10:35"),
            TimeTableUtils.TimeSlotRow(4, "22:00", "23:00", TimeTableUtils.EdgeClass.After),
        )

        val inferred = TimeTableUtils.inferSmartPeriodConfig(rows)

        assertEquals(3, inferred!!.totalPeriods)
        assertEquals(
            listOf("08:00" to "08:45", "08:55" to "09:40", "09:50" to "10:35"),
            inferred.derive().map { it.start to it.end },
        )
    }

    @Test
    fun `import inference round trip preserves every valid standard pair`() {
        // 任一可推断的导入行 -> derive 回到原行 (node/start/end 三元组逐行相等).
        val rows = listOf(
            TimeTableUtils.TimeSlotRow(3, "10:00", "10:30"),
            TimeTableUtils.TimeSlotRow(1, "08:00", "08:45"),
            TimeTableUtils.TimeSlotRow(2, "08:55", "09:40"),
            TimeTableUtils.TimeSlotRow(4, "10:40", "11:25"),
        )

        val inferred = TimeTableUtils.inferSmartPeriodConfig(rows)
        val derived = inferred!!.derive()

        assertEquals(listOf(1, 2, 3, 4), derived.map { it.node })
        assertEquals(
            listOf("08:00" to "08:45", "08:55" to "09:40", "10:00" to "10:30", "10:40" to "11:25"),
            derived.map { it.start to it.end },
        )
    }
}
