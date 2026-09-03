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
}
