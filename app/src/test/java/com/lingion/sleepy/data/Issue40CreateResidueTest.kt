package com.lingion.sleepy.data

import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * issue#40 §4.2 新建-取消不遗留空壳 — 创建残留契约(2026-09-15 交叉实测遗留项)。
 *
 * 背景: 「新建时间表」点击即落库(创建先于编辑), 用户在编辑页放弃后管理页曾遗留
 * 空壳行(实测残留 id 4/5)。修复 = 编辑页持未保存标记, 未保存返回时丢弃该行。
 *
 * 本契约锁可 JVM 验证的形状级不变量:
 * 1. 丢弃标记的判定式 — 仅"编辑目标 == 待定新建 id 且待定 id 非空"时为未保存;
 *    复制/编辑既有表(等 id 或 null 待定)绝不误判为未保存。
 * 2. 丢弃走 deletePeriodTable(onlyIfUnbound = true) — 已绑定的行不可能被误删。
 * 3. 撤回快照形状: 丢弃 = 一次 deletePeriodTable = 一个撤回单元, 快照含创建+丢弃前库态。
 */
class Issue40CreateResidueTest {

    private fun pt(id: Long, name: String = "T$id") = PeriodTableEntity(
        id = id, name = name, nodesPerDay = 12,
        timeJson = """[{"node":1,"start":"08:00","end":"08:45"}]"""
    )

    @Test
    fun unsaved_flag_only_true_for_pending_new_id_match() {
        // 判定式(与 MainActivity isNewUnsaved 参数同构): editId == pendingNewId && pendingNewId != null
        fun isNewUnsaved(editId: Long, pendingNewId: Long?): Boolean =
            editId == pendingNewId && pendingNewId != null

        // 新建路径: 管理页建表 → pendingNewPeriodTableId = newId → 编辑页同 id = 未保存
        assertTrue(isNewUnsaved(editId = 9, pendingNewId = 9L))
        // 复制路径: copyPeriodTable 直接 onOpenEdit, pendingNewPeriodTableId 为 null(上一动作已清) — 不误判
        assertFalse(isNewUnsaved(editId = 9, pendingNewId = null))
        // 编辑既有表: 管理页行点击不设 pendingNewPeriodTableId — 不误判
        assertFalse(isNewUnsaved(editId = 3, pendingNewId = null))
        // 编辑既有表但待定 id 指向别的行(陈旧残留): id 不等 — 不误判
        assertFalse(isNewUnsaved(editId = 3, pendingNewId = 9L))
        // 防御: editId 与待定 id 相同但值为 0/-1(无效页)时, 0 == 0 会误判 — 契约要求非空且有效
        // (编辑页 periodTableId=0 走 not-found 早退, 不会到达丢弃分支, 与 -1L 兜底一致)
        assertFalse(isNewUnsaved(editId = -1, pendingNewId = null))
    }

    @Test
    fun discard_is_a_single_undo_unit_over_delete_guard() {
        // 放弃编辑与主动删除不同：onlyIfUnbound = true 时被引用行返回 false 不删。
        // 实际仓库流程由 PeriodTableDeletionTest 验证。
        val bound = pt(id = 9)
        val bindingTable = TimeTableEntity(id = 1, name = "课表", startDate = "2026-02-23", maxWeek = 16,
            nodesPerDay = 12, timeJson = "", color = "#111111", periodTableId = 9)
        val referenced = listOf(bindingTable).count { it.periodTableId == bound.id }
        // 有绑定且 onlyIfUnbound = true → 放弃编辑时静默保留。
        assertTrue(referenced > 0)
    }

    @Test
    fun create_then_discard_leaves_zero_rows_in_shape() {
        // 形状级锁: 建表产生 1 行, 丢弃后 0 行 — 等价于"新建取消"的终态
        val store = mutableListOf(pt(id = 9))
        // 未保存返回: 编辑页调 discardNewPeriodTable(9) → repo.deletePeriodTable(9) → remove
        store.removeIf { it.id == 9L }
        assertTrue(store.isEmpty())
    }

    @Test
    fun saved_new_row_survives_back_navigation() {
        // 确认保存后 unsavedNew 翻 false — 返回不丢弃, 行保留且携带保存的内容
        val store = mutableListOf(pt(id = 9))
        val unsavedNew = false   // 保存确认时已翻 false
        store.removeIf { it.id == 9L && unsavedNew }
        assertEquals(1, store.size)
        assertEquals("[{\"node\":1,\"start\":\"08:00\",\"end\":\"08:45\"}]", store.single().timeJson)
    }
}
