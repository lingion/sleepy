package com.lingion.sleepy.data.undo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
 * v7.10.16w 撤回锚定修复(用户 2026-09-10 报"复制课表后追加导入, 撤回把整个
 * 副本撤没了"): 批内首拍原本只在"槽为空"时生效 — 上一动作(如复制课表)留下的
 * 旧快照会被本批误当成动作起点, 撤回跳过本动作直接回到更早, 副本被连根拔。
 * 现在 beginBatch 时把旧快照过期: 本动作首拍必落到"本动作开始前"的库态。
 * 每个用户动作的撤回点 = 该动作自己开始前 — 动作链上不跳步。
 *
 * [restoring] 抑制恢复动作自身的捕获, 防止 undo 生成新的 undo。
 */
object UndoManager {
    // mutableStateOf: Compose 读取 hasSnapshot 自动订阅, 撤回按钮随有无快照显隐(用户 2026-09-03)
    private var slot by androidx.compose.runtime.mutableStateOf<UndoSnapshot?>(null)
    @Volatile private var batchDepth: Int = 0
    // 本批首拍是否已落: 批内多次 capture 只保第一次 — 但锚定的是本批开始前(非旧快照)
    @Volatile private var batchCaptured: Boolean = false
    @Volatile var restoring: Boolean = false

    val hasSnapshot: Boolean get() = slot != null

    fun beginBatch() {
        batchDepth++
        // 批边界 = 新用户动作开始 — 旧动作的快照对新动作是过期锚点, 立即作废。
        // 若真要嵌套批(当前无此用法), 内层 begin 不清 batchCaptured(只在 0→1 清)。
        if (batchDepth == 1) batchCaptured = false
    }

    fun endBatch() { batchDepth = (batchDepth - 1).coerceAtLeast(0) }

    fun capture(tables: List<TimeTableEntity>, courses: List<CourseEntity>, defaultTableId: Long?) {
        if (restoring) return
        if (batchDepth > 0) {
            if (batchCaptured) return   // 批内已有本动作快照 — 保动作链起点
            batchCaptured = true
        }
        slot = UndoSnapshot(tables, courses, defaultTableId)
    }

    fun poll(): UndoSnapshot? {
        val s = slot
        slot = null
        return s
    }

    fun clear() { slot = null }
}
