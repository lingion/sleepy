package com.lingion.sleepy.ui.screen.mine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 甲案 (设计文档 §2.2, §3.1-§3.3, §4) 集成契约:
 * EditTableScreen 在绑定共享作息表时改作息 → 三选项 BottomSheet +
 * 待执行提醒 Banner + 三策略执行路径 + 取消草稿恢复。
 *
 * 仓库无 Robolectric / 无 coroutines-test, 按既有契约测试风格锁源码触发。
 */
class ScheduleConflictFlowContractTest {

    private fun findUpward(rel: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, rel)
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("$rel not found")
    }

    private val editTableScreen by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/mine/EditTableScreen.kt").readText()
    }
    private val conflictSheet by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/mine/ScheduleConflictBottomSheet.kt").readText()
    }
    private val pendingBanner by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/mine/PendingPolicyBanner.kt").readText()
    }
    private val scheduleViewModel by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/schedule/ScheduleViewModel.kt").readText()
    }

    @Test
    fun conflict_sheet_is_a_modal_bottom_sheet_with_three_options() {
        assertTrue(
            "ScheduleConflictBottomSheet 必须用 ModalBottomSheet",
            conflictSheet.contains("ModalBottomSheet(")
        )
        for (opt in listOf("DETACH_COPY", "CREATE_NEW", "SYNC")) {
            assertTrue(
                "三选项必须都接 SchedulePolicy.$opt",
                conflictSheet.contains("SchedulePolicy.$opt")
            )
        }
    }

    @Test
    fun pending_banner_only_renders_when_policy_not_none() {
        assertTrue(
            "PendingPolicyBanner 在 policy=NONE 时必须 return, 不渲染",
            pendingBanner.contains("if (policy == SchedulePolicy.NONE) return")
        )
        for (opt in listOf("DETACH_COPY", "CREATE_NEW", "SYNC")) {
            assertTrue(
                "Banner 必须为每个非 NONE 策略显示文字 ($opt)",
                pendingBanner.contains("SchedulePolicy.$opt ->")
            )
        }
    }

    @Test
    fun save_button_branches_through_three_invariants() {
        // invariant ②: 改了作息 + 无策略 → 弹窗
        assertTrue(
            "save-button 必须先判 hasScheduleChanged() && pendingPolicy==NONE 才弹窗",
            editTableScreen.contains("editState.hasScheduleChanged() && pendingPolicy == SchedulePolicy.NONE")
        )
        // invariant ④: 有策略 → 执行策略
        assertTrue(
            "save-button 必须把 pendingPolicy!=NONE 路径分给 executePolicyAndSave",
            editTableScreen.contains("pendingPolicy != SchedulePolicy.NONE") &&
                editTableScreen.contains("viewModel.executePolicyAndSave(")
        )
        // invariant ①: 普通保存只在非甲案路径(未绑定/未改作息/换绑)走
        assertTrue(
            "普通保存路径须保留 — 未绑定 / 未改作息时仍可用",
            editTableScreen.contains("viewModel.updateTableRemappingCourses(updated)")
        )
    }

    @Test
    fun execute_policy_uses_main_branch_primitives_only() {
        assertTrue(
            "executePolicyAndSave 须走 repo.updateTableRemappingCourses(DETACH_COPY 写入本表)",
            scheduleViewModel.contains("repo.updateTableRemappingCourses(")
        )
        assertTrue(
            "executePolicyAndSave CREATE_NEW 须走全局唯一名插入(原语 insertPeriodTableWithUniqueName)",
            scheduleViewModel.contains("insertPeriodTableWithUniqueName(")
        )
        assertTrue(
            "executePolicyAndSave SYNC 须走 repo.updatePeriodTable 写共享表",
            scheduleViewModel.contains("repo.updatePeriodTable(")
        )
        // §3.5 原子性 = UndoManager 批内
        assertTrue(
            "executePolicyAndSave 必须包在 UndoManager.beginBatch/endBatch 内",
            scheduleViewModel.contains("UndoManager.beginBatch()") &&
                editTableScreen.contains("executePolicyAndSave(")
        )
    }

    @Test
    fun cancel_restores_original_draft_and_clears_policy() {
        // invariant ⑥: 取消恢复 original + 清策略 + 不写库
        assertTrue(
            "ScheduleEditPolicyState.cancelPolicy 必须恢复 original + 清策略",
            findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/schedule/ScheduleEditPolicyState.kt")
                .readText()
                .let { it.contains("cancelPolicy()") && it.contains("originalEffectiveSchedule") }
        )
        assertTrue(
            "EditTableScreen sheet 的 onCancel 必须调 editState.cancelPolicy",
            editTableScreen.contains("editState.cancelPolicy()")
        )
    }

    @Test
    fun re_edit_invalidates_pending_policy_invariant_five() {
        // invariant ⑤: 选择后再改作息 → 旧策略失效
        assertTrue(
            "updateDraft 必须在 draft≠original 时把非 NONE 策略作废回 NONE",
            findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/schedule/ScheduleEditPolicyState.kt")
                .readText()
                .contains("_pendingSchedulePolicy.value = SchedulePolicy.NONE")
        )
    }

    @Test
    fun repo_get_tables_bound_to_continues_to_back_dialog_count() {
        assertTrue(
            "EditTableScreen 弹窗前必须取绑定课表数(用于影响提示)",
            editTableScreen.contains("viewModel.repoTablesBoundToCount(")
        )
    }

    @Test
    fun schedule_undo_batch_atomics_all_three_policies() {
        // §7 撤回要求: 三选项全部包在 UndoManager 批内, 一次撤回整步回退
        // 检查 executePolicyAndSave 体内有 beginBatch, endBatch 配对
        val vm = scheduleViewModel
        val execFun = vm.substringAfter("fun executePolicyAndSave(").substringBefore("fun ")
        val begin = execFun.split("UndoManager.beginBatch()").size - 1
        val end = execFun.split("UndoManager.endBatch()").size - 1
        assertEquals("executePolicyAndSave 须 beginBatch=endBatch=1", 1, begin)
        assertEquals("executePolicyAndSave 须 beginBatch=endBatch=1", 1, end)
    }
}