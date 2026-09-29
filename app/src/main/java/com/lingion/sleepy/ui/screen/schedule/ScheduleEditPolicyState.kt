package com.lingion.sleepy.ui.screen.schedule

import com.lingion.sleepy.data.entity.PeriodTableEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 甲案 (设计文档 §2.1) — 编辑课表时绑定了共享作息表情况下, 用户对作息改动的处置选择。
 *
 * - [NONE]        未发生 / 已清空 — 默认状态, 也用于"取消"和"选择后再次改作息"。
 * - [DETACH_COPY] 改本表作息: 用 draftEffectiveSchedule 写本表内置作息 + 解绑共享表。
 * - [CREATE_NEW]  以 draftEffectiveSchedule 新建同名独立作息表 + 改绑到新表。
 * - [SYNC]        把 draftEffectiveSchedule 写回共享作息表 (影响所有绑定它的课表)。
 *
 * 不变量 7: 三个选项始终显示 (不隐藏), 影响数量仅用于提示文案。
 */
enum class SchedulePolicy {
    NONE,
    DETACH_COPY,
    CREATE_NEW,
    SYNC
}

/**
 * 设计文档 §2.1 — 单张课表编辑会话的状态容器:
 *
 * - [originalEffectiveSchedule]: 进入编辑页时实际生效的作息 (含绑定水合结果)。
 * - [draftEffectiveSchedule]:    用户当前正在编辑的作息草稿; 改作息时由 UI 写入。
 * - [pendingSchedulePolicy]:     弹窗选择结果, 数据库写入必须等待再次保存 + 此策略仍生效。
 *
 * 7 条不变量 (§2.2) 全部在此容器内闭环:
 *   1. 未改作息 → hasScheduleChanged=false
 *   3. selectPolicy 只登记策略, 不动 draft
 *   5. updateDraft 后若 draft ≠ original → pendingSchedulePolicy 自动归零 (旧策略失效)
 *   6. cancelPolicy → draft 恢复 original, 策略归零
 *
 * 设计为独立 holder, 不依赖 [ScheduleViewModel] 字段初始化器
 * (后者依赖 SleepyApp.get().repository, 在纯 JVM 测试中会抛), 便于
 * ScheduleEditPolicyStateTest 直接 `ScheduleEditPolicyState(tableId, original)` 构造。
 */
class ScheduleEditPolicyState(
    val tableId: Long,
    private val originalEffectiveSchedule: PeriodTableEntity?
) {
    private val _draftEffectiveSchedule =
        MutableStateFlow<PeriodTableEntity?>(originalEffectiveSchedule)
    val draftEffectiveSchedule: StateFlow<PeriodTableEntity?> =
        _draftEffectiveSchedule.asStateFlow()

    private val _pendingSchedulePolicy = MutableStateFlow(SchedulePolicy.NONE)
    val pendingSchedulePolicy: StateFlow<SchedulePolicy> = _pendingSchedulePolicy.asStateFlow()

    /**
     * 设计文档 §2.2 不变量 1 — 草稿与原值不相等即视为改了作息。
     * 用 data class equals: 任一字段变化 (timeJson/nodesPerDay/smartConfigJson/name …) 都触发。
     */
    fun hasScheduleChanged(): Boolean =
        _draftEffectiveSchedule.value != originalEffectiveSchedule

    /**
     * UI 节次编辑区提交草稿。设计文档 §2.2 不变量 5: 改了就让旧策略失效, 下一次保存重新弹三选项。
     */
    fun updateDraft(draft: PeriodTableEntity?) {
        _draftEffectiveSchedule.value = draft
        if (draft != originalEffectiveSchedule && _pendingSchedulePolicy.value != SchedulePolicy.NONE) {
            _pendingSchedulePolicy.value = SchedulePolicy.NONE
        }
    }

    /** 设计文档 §2.2 不变量 3 — 只登记, 不动草稿; 真正的写入仍要等再次保存。 */
    fun selectPolicy(policy: SchedulePolicy) {
        _pendingSchedulePolicy.value = policy
    }

    /**
     * 设计文档 §2.2 不变量 6 — 弹窗取消: 草稿恢复 original + 策略清零。
     * 取消仅影响作息相关改动, 不影响其他字段 (课程行/名称/日期等) — 那些由 UI 自留草稿。
     */
    fun cancelPolicy() {
        _draftEffectiveSchedule.value = originalEffectiveSchedule
        _pendingSchedulePolicy.value = SchedulePolicy.NONE
    }
}