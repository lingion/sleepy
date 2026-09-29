package com.lingion.sleepy.ui.screen.schedule

import com.lingion.sleepy.data.entity.PeriodTableEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 甲案 作息冲突三选项 — 编辑会话状态模型契约
 * (docs/superpowers/specs/2026-09-24-period-table-conflict-resolution-design.md §2.1/§2.2)。
 *
 * 锁不变量:
 *   1. 未改作息 → hasScheduleChanged=false, 普通保存不弹窗
 *   3. 选择任一选项 → 只记录 pendingSchedulePolicy, 不改草稿
 *   5. 选择后又改作息 → 旧策略失效 (重新弹三选项)
 *   6. 取消 → 草稿恢复 originalEffectiveSchedule + 策略清空
 *
 * 纯 JVM: 直接构造 ScheduleEditPolicyState, 不触 Repository / Android 框架
 * (PeriodTableEntity 为纯数据类, TimeTableBindingSnapshotTest / Issue40ContractTest 同法)。
 */
class ScheduleEditPolicyStateTest {

    private fun pt(
        id: Long = 7,
        name: String = "2024 秋季作息",
        timeJson: String = """[{"node":1,"start":"08:00","end":"08:45"}]""",
        nodesPerDay: Int = 12
    ) = PeriodTableEntity(id = id, name = name, nodesPerDay = nodesPerDay, timeJson = timeJson)

    @Test
    fun invariant1_initial_state_has_no_policy_and_counts_as_unchanged() {
        val s = ScheduleEditPolicyState(tableId = 1L, originalEffectiveSchedule = pt())

        assertEquals(SchedulePolicy.NONE, s.pendingSchedulePolicy.value)
        assertFalse(s.hasScheduleChanged())
        assertEquals(pt(), s.draftEffectiveSchedule.value)
    }

    @Test
    fun invariant1_draft_change_detected_by_value_equality_not_identity() {
        val original = pt()
        val s = ScheduleEditPolicyState(1L, original)

        s.updateDraft(original.copy(timeJson = """[{"node":1,"start":"08:30","end":"09:15"}]"""))

        assertTrue(s.hasScheduleChanged())
    }

    @Test
    fun invariant1_draft_content_equal_to_original_counts_as_unchanged() {
        val original = pt()
        val s = ScheduleEditPolicyState(1L, original)

        // 用户改了又改回原样: 草稿值回到 original → 视为未改
        s.updateDraft(original.copy(name = "临时名"))
        s.updateDraft(original.copy())

        assertFalse(s.hasScheduleChanged())
    }

    @Test
    fun invariant3_selectPolicy_records_without_touching_draft() {
        val original = pt()
        val s = ScheduleEditPolicyState(1L, original)
        s.updateDraft(original.copy(timeJson = "edited-json"))

        s.selectPolicy(SchedulePolicy.DETACH_COPY)

        assertEquals(SchedulePolicy.DETACH_COPY, s.pendingSchedulePolicy.value)
        assertEquals("edited-json", s.draftEffectiveSchedule.value!!.timeJson)
    }

    @Test
    fun invariant4_all_three_options_are_selectable_states() {
        // 三个选项始终显示 (不变量 7): 状态机必须接受策略全集
        for (p in listOf(SchedulePolicy.DETACH_COPY, SchedulePolicy.CREATE_NEW, SchedulePolicy.SYNC)) {
            val s = ScheduleEditPolicyState(3L, pt())
            s.updateDraft(pt(timeJson = "x"))
            s.selectPolicy(p)
            assertEquals(p, s.pendingSchedulePolicy.value)
        }
    }

    @Test
    fun invariant5_re_edit_after_selection_invalidates_policy() {
        val original = pt()
        val s = ScheduleEditPolicyState(1L, original)
        s.updateDraft(original.copy(timeJson = "a"))
        s.selectPolicy(SchedulePolicy.CREATE_NEW)
        assertEquals(SchedulePolicy.CREATE_NEW, s.pendingSchedulePolicy.value)

        s.updateDraft(original.copy(timeJson = "b")) // 选择后又改了作息

        assertEquals(SchedulePolicy.NONE, s.pendingSchedulePolicy.value)
        assertTrue(s.hasScheduleChanged())
    }

    @Test
    fun invariant6_cancel_restores_original_draft_and_clears_policy() {
        val original = pt()
        val s = ScheduleEditPolicyState(1L, original)
        s.updateDraft(original.copy(name = "别的作息", nodesPerDay = 14))
        s.selectPolicy(SchedulePolicy.SYNC)

        s.cancelPolicy()

        assertEquals(original, s.draftEffectiveSchedule.value)
        assertEquals(SchedulePolicy.NONE, s.pendingSchedulePolicy.value)
        assertFalse(s.hasScheduleChanged())
    }

    @Test
    fun unbound_table_original_null_tracks_null_draft() {
        // 未绑定共享表的课表不触发弹窗, 但状态机本身对 null 快照保持自洽
        val s = ScheduleEditPolicyState(2L, null)

        assertFalse(s.hasScheduleChanged())
        s.updateDraft(pt())
        assertTrue(s.hasScheduleChanged())
        s.cancelPolicy()
        assertEquals(null, s.draftEffectiveSchedule.value)
        assertEquals(SchedulePolicy.NONE, s.pendingSchedulePolicy.value)
    }
}
