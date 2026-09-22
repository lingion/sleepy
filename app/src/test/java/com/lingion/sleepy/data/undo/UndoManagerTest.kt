package com.lingion.sleepy.data.undo

import com.lingion.sleepy.data.entity.PeriodTableEntity
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

    @Test
    fun `snapshot carries periodTables for issue40 undo`() {
        // issue#40: 撤回快照必须含独立时间节次表, 否则撤回建时间表类动作后
        // period_tables 残留/绑定丢失
        UndoManager.clear()
        val p1 = PeriodTableEntity(id = 5, name = "春季作息")
        UndoManager.capture(emptyList(), emptyList(), null, listOf(p1))
        val snap = UndoManager.poll()
        check(snap?.periodTables?.single()?.id == 5L)
        check(snap.periodTables.single().name == "春季作息")
    }

    @Test
    fun `legacy three-arg capture still compiles with empty periodTables`() {
        // 兼容: 旧三参数位置调用不得因新参数破坏
        UndoManager.clear()
        UndoManager.capture(listOf(TimeTableEntity(id = 1, name = "T", startDate = "")), emptyList(), 1L)
        val snap = UndoManager.poll()
        check(snap?.periodTables?.isEmpty() == true)
        check(snap.defaultTableId == 1L)
    }

    // ────────────────────────────────────────────────────────────────────
    // 2026-09-21 redo 套件 — 单级 redo 槽, 与 undo 互不抢占, 新写清 redo
    // ────────────────────────────────────────────────────────────────────

    @Test
    fun `recordRedo and pollRedo are mutually exclusive with undo`() {
        // redo 与 undo 是两条独立槽;recordRedo 不动 undo 槽, pollRedo 不动 undo 槽
        UndoManager.clear()
        val t = TimeTableEntity(id = 1, name = "T", startDate = "2026-09-01")
        UndoManager.capture(listOf(t), emptyList(), 1L)
        UndoManager.recordRedo(
            UndoSnapshot(tables = listOf(t.copy(name = "modified")), courses = emptyList(), defaultTableId = 1L)
        )
        check(UndoManager.hasSnapshot)        // undo 槽仍存在
        check(UndoManager.hasRedoSnapshot)    // redo 槽也存在
        val undo = UndoManager.poll()
        check(undo?.tables?.single()?.name == "T")
        check(!UndoManager.hasSnapshot)        // undo 排空
        check(UndoManager.hasRedoSnapshot)    // redo 仍在(互不抢)
        val redo = UndoManager.pollRedo()
        check(redo?.tables?.single()?.name == "modified")
        check(!UndoManager.hasRedoSnapshot)
    }

    @Test
    fun `capture clears redo slot (fork prevention)`() {
        // 标准编辑器语义: undo → 改 → redo 路径废。新用户动作 = 历史分叉, redo 立即作废
        UndoManager.clear()
        UndoManager.recordRedo(
            UndoSnapshot(tables = emptyList(), courses = emptyList(), defaultTableId = null)
        )
        check(UndoManager.hasRedoSnapshot)
        UndoManager.capture(emptyList(), emptyList(), null)
        check(!UndoManager.hasRedoSnapshot)   // capture 必清 redo
    }

    @Test
    fun `reinsertForRedoSymmetry restores undo slot without recording capture`() {
        // redo 应用快照后必须把 redo 前态成 undo(对称 — 用户再次点撤回回退 redo)
        // 实现把 redo 前态直接灌进 undo 槽,不走 capture(因为 restoring=true 抑制)
        UndoManager.clear()
        val before = UndoSnapshot(tables = emptyList(), courses = emptyList(), defaultTableId = null)
        UndoManager.reinsertForRedoSymmetry(before)
        check(UndoManager.hasSnapshot)
        check(UndoManager.poll() === before)
    }

    @Test
    fun `clear empties both undo and redo slots`() {
        UndoManager.clear()
        UndoManager.capture(emptyList(), emptyList(), null)
        UndoManager.recordRedo(UndoSnapshot(tables = emptyList(), courses = emptyList(), defaultTableId = null))
        UndoManager.clear()
        check(!UndoManager.hasSnapshot)
        check(!UndoManager.hasRedoSnapshot)
    }

    @Test
    fun `full undo-then-redo-then-new-write cycle`() {
        // 用户原动作: A→B→C 三态演进。
        // 1. capture(A)=S, 应用 B → redo=S(B) 已不可能(按设计 redo 只在 undo 触发时落地)
        //    这里用仓库层语义模拟: undo() 把当前(C)存 redo, 恢复 S(=A)
        //    redo() 把当前(A)存 undo, 恢复 S(B)
        //    new-write(C') 必须清 redo, 否则能从 B 直跳 C' 而绕过 A(历史分叉)
        UndoManager.clear()
        val a = UndoSnapshot(tables = emptyList(), courses = emptyList(), defaultTableId = null)  // initial
        val b = UndoSnapshot(tables = listOf(TimeTableEntity(id = 1, name = "B", startDate = "")), courses = emptyList(), defaultTableId = 1L)
        // 用户在 B 状态点击"撤回" → 仓库把当前态(C,此处=b)录 redo,恢复 a
        UndoManager.recordRedo(b)
        UndoManager.reinsertForRedoSymmetry(a)   // 假装"应用 B 之前态 a"
        check(!UndoManager.hasRedoSnapshot == false || !UndoManager.hasSnapshot == false)
        // 用户点"取消撤回" → 仓库把当前态(a)录 undo, 恢复 b
        UndoManager.reinsertForRedoSymmetry(b)
        UndoManager.recordRedo(a)
        // 关键: 用户做了新动作(capture C') → redo 必清
        UndoManager.capture(emptyList(), emptyList(), 2L)
        check(!UndoManager.hasRedoSnapshot)    // 关键断言:新写清 redo
    }
}
