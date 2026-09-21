package com.lingion.sleepy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 编辑课程时段摘要的纯 JVM 接线契约。
 *
 * 仓库目前没有 Compose UI 测试环境，因此这里锁定最容易回退的产品语义：
 * 卡头必须读取草稿实时状态，并覆盖周次、节次/时间、教师和地点。
 */
class EditCourseSlotSummaryContractTest {

    private val source: String by lazy {
        sequenceOf(
            File("app/src/main/java/com/lingion/sleepy/ui/screen/edit/AddCourseScreen.kt"),
            File("src/main/java/com/lingion/sleepy/ui/screen/edit/AddCourseScreen.kt")
        ).first { it.isFile }.readText()
    }

    private val summarySource: String by lazy {
        source.substringAfter("private fun MeetingBlockSummary(")
            .substringBefore("/** 非常规选项折叠栏")
    }

    @Test
    fun `slot header uses the live two-line summary`() {
        assertTrue(source.contains("MeetingBlockSummary(block = block, timeJson = timeJson)"))
        assertTrue(summarySource.contains("text = calendarSummary"))
        assertTrue(summarySource.contains("text = detailSummary"))
        assertFalse("旧卡头不能只显示已选星期", source.contains("R.string.selected_days"))
    }

    @Test
    fun `summary covers calendar range and every timing mode`() {
        listOf(
            "block.days",
            "block.startWeek",
            "block.endWeek",
            "block.weekType",
            "block.startNode",
            "block.step",
            "block.isIrregularNode",
            "block.isIrregularTime",
            "block.effectiveRange(timeJson)"
        ).forEach { state ->
            assertTrue("summary must react to $state", summarySource.contains(state))
        }

        listOf(
            "slot_summary_week_single",
            "slot_summary_week_range",
            "slot_summary_period_single",
            "slot_summary_period_range",
            "slot_summary_time_range"
        ).forEach { key ->
            assertTrue("summary must render $key", summarySource.contains("R.string.$key"))
        }
    }

    @Test
    fun `teacher and room appear only when filled`() {
        assertTrue(summarySource.contains("block.teacherState.isBlank()"))
        assertTrue(summarySource.contains("block.roomState.isBlank()"))
        assertTrue(summarySource.contains("R.string.slot_summary_teacher"))
        assertTrue(summarySource.contains("R.string.slot_summary_room"))
        assertTrue(summarySource.contains("listOfNotNull(timingSummary, teacherSummary, roomSummary)"))
    }
}
