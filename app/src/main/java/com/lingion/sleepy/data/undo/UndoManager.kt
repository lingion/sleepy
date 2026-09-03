package com.lingion.sleepy.data.undo

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity

/** 改动前的全库快照 — tables+courses+默认表 id。 */
data class UndoSnapshot(
    val tables: List<TimeTableEntity>,
    val courses: List<CourseEntity>,
    val defaultTableId: Long?
)

/**
 * 单级撤回的快照槽(进程内单例)。
 *
 * repo 写方法执行前 [capture], 用户点撤回时 [poll] 取走并清空 —
 * 单级语义: 撤回不可再撤回; App 进程被杀快照即失效(不落盘)。
 *
 * [beginBatch] 支持复合动作(如导入=建表+插课+设默认):
 * 批内多次 capture 只保留第一次, 保证整个动作回退到同一时点;
 * 不在批内时每次 capture 都覆盖槽(单写动作语义)。
 *
 * [restoring] 抑制恢复动作自身的捕获, 防止 undo 生成新的 undo。
 */
object UndoManager {
    @Volatile private var slot: UndoSnapshot? = null
    @Volatile private var batchDepth: Int = 0
    @Volatile var restoring: Boolean = false

    val hasSnapshot: Boolean get() = slot != null

    fun beginBatch() { batchDepth++ }

    fun endBatch() { batchDepth = (batchDepth - 1).coerceAtLeast(0) }

    fun capture(tables: List<TimeTableEntity>, courses: List<CourseEntity>, defaultTableId: Long?) {
        if (restoring) return
        if (batchDepth > 0 && slot != null) return   // 批内已有快照 — 保动作链起点
        slot = UndoSnapshot(tables, courses, defaultTableId)
    }

    fun poll(): UndoSnapshot? {
        val s = slot
        slot = null
        return s
    }

    fun clear() { slot = null }
}
