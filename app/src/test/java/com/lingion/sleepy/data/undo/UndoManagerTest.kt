package com.lingion.sleepy.data.undo

import com.lingion.sleepy.data.entity.TimeTableEntity
import org.junit.Test

class UndoManagerTest {

    @Test
    fun `capture stores single slot and poll drains it`() {
        UndoManager.clear()
        val t = TimeTableEntity(id = 1, name = "T", startDate = "2026-09-01")
        UndoManager.capture(listOf(t), emptyList(), 1L)
        check(UndoManager.hasSnapshot)
        val snap = UndoManager.poll()
        check(snap?.defaultTableId == 1L)
        check(!UndoManager.hasSnapshot)          // poll 即清空(单级)
        check(UndoManager.poll() == null)        // 二次 poll 为空
    }

    @Test
    fun `capture overwrites previous snapshot`() {
        UndoManager.clear()
        UndoManager.capture(emptyList(), emptyList(), null)
        UndoManager.capture(
            listOf(TimeTableEntity(id = 9, name = "N", startDate = "")),
            emptyList(), 9L
        )
        check(UndoManager.poll()?.tables?.single()?.id == 9L)
    }

    @Test
    fun `restoring flag suppresses capture`() {
        UndoManager.clear()
        UndoManager.restoring = true
        try {
            UndoManager.capture(emptyList(), emptyList(), null)
            check(!UndoManager.hasSnapshot)      // 恢复期间不生成快照
        } finally {
            UndoManager.restoring = false
        }
    }

    @Test
    fun `beginBatch re-captures only once per batch`() {
        UndoManager.clear()
        UndoManager.beginBatch()
        try {
            UndoManager.capture(listOf(TimeTableEntity(id = 1, name = "A", startDate = "")), emptyList(), 1L)
            UndoManager.capture(listOf(TimeTableEntity(id = 2, name = "B", startDate = "")), emptyList(), 2L)
            // 批内多次 capture 只保留第一次 — 复合动作(导入=建表+插课+设默认)整批回退到动作前
            check(UndoManager.poll()?.tables?.single()?.id == 1L)
            check(!UndoManager.hasSnapshot)
        } finally {
            UndoManager.endBatch()   // 单例 batchDepth 必须复原, 否则泄漏影响后续测试
        }
    }

    @Test
    fun `stale snapshot from previous action is not reused by new batch`() {
        // 用户 2026-09-10 报: 复制课表(动作A留旧快照S) → 追加导入(动作B开批) →
        // 撤回 → 整个副本被撤没。根因 = 批内"槽里已有快照就跳过"把上一动作的
        // 旧快照当成本批起点 — 撤回跳过了动作B, 直接回到动作A之前。
        // 语义: 每个新动作(批或单写)的快照必须锚定本动作开始前, 旧快照不得复用。
        UndoManager.clear()
        // 动作A: 复制课表(非批, 单写组合) — 留下指向"复制前"的快照
        UndoManager.capture(listOf(TimeTableEntity(id = 1, name = "orig", startDate = "")), emptyList(), 1L)
        // 动作B: 追加导入 — beginBatch 开批, 首个 capture 必须落到"动作B开始前"的库态
        UndoManager.beginBatch()
        try {
            UndoManager.capture(listOf(TimeTableEntity(id = 1, name = "orig", startDate = ""), TimeTableEntity(id = 2, name = "copy", startDate = "")), emptyList(), 1L)
            val snap = UndoManager.poll()
            // 旧实现: 槽里已有快照 → 跳过, poll 出来的是动作A的快照(无 id=2 副本) — 错
            // 正确: 动作B首拍生效, 快照含副本表 — 撤回只回退动作B, 副本保留
            check(snap?.tables?.any { it.id == 2L } == true)
        } finally {
            UndoManager.endBatch()
        }
    }

    @Test
    fun `single write after another action re-captures fresh`() {
        // 同族: 动作A留快照 → 动作B非批单写 → B 的 capture 也必须生效(不能被
        // "已有快照"挡掉)。单写没有 batchDepth 保护, capture 语义 = 总是覆盖。
        UndoManager.clear()
        UndoManager.capture(listOf(TimeTableEntity(id = 1, name = "A", startDate = "")), emptyList(), 1L)
        UndoManager.capture(listOf(TimeTableEntity(id = 1, name = "A", startDate = ""), TimeTableEntity(id = 3, name = "B", startDate = "")), emptyList(), 1L)
        check(UndoManager.poll()?.tables?.any { it.id == 3L } == true)
    }
}
