package com.lingion.sleepy.data.undo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity

/** 改动前的全库快照 — periodTables+tables+courses+默认表 id (issue#40 起含独立时间节次表)。 */
data class UndoSnapshot(
    val periodTables: List<PeriodTableEntity> = emptyList(),
    val tables: List<TimeTableEntity>,
    val courses: List<CourseEntity>,
    val defaultTableId: Long?
)

/**
 * 多步撤回的快照栈(进程内单例)。
 *
 * repo 写方法执行前 [capture], 用户点撤回时 [poll] 从栈顶取走一步 —
 * 多步语义: 连点撤回逐步回退; App 进程被杀两栈即失效(不落盘)。
 *
 * [beginBatch] 支持复合动作(如导入=建表+插课+设默认):
 * 批内多次 capture 只保留第一次, 保证整个动作回退到同一时点;
 * 不在批内时每次 capture 都压入新一步(单写动作语义)。
 *
 * v7.10.16w 撤回锚定修复(用户 2026-09-10 报"复制课表后追加导入, 撤回把整个
 * 副本撤没了"): 批内首拍原本只在"槽为空"时生效 — 上一动作(如复制课表)留下的
 * 旧快照会被本批误当成动作起点, 撤回跳过本动作直接回到更早, 副本被连根拔。
 * 现在 beginBatch 时把旧动作锚点过期: 本动作首拍必落到"本动作开始前"的库态。
 * 每个用户动作的撤回点 = 该动作自己开始前 — 动作链上不跳步。
 *
 * [restoring] 抑制恢复动作自身的捕获, 防止 undo 生成新的 undo。
 *
 * 2026-09-21 用户令: redo — 撤回后可"取消撤回"; 任何新写动作清空 redo,
 * 历史不分支。undo/redo 两栈独立有值: 撤回把栈顶搬到 redo 栈, 取消撤回反向搬运。
 *
 * 2026-10-27 多步化: slot/redoSlot 改 undoStack/redoStack(末元素=栈顶),
 * 深度 maxDepth 控制裁剪; 其余规则(批首拍锚定、restoring 抑制、新写清空 redo、
 * 恢复不生成新步)原样保留。
 */
object UndoManager {
    /** 出厂默认撤回深度: 保留最近 5 步; 供测试与启动兜底引用。 */
    const val DEFAULT_MAX_DEPTH = 5

    // mutableStateOf: Compose 读取 hasSnapshot 自动订阅, 撤回/重做按钮随栈有无条目显隐(用户 2026-09-03)
    private var undoStack by mutableStateOf<List<UndoSnapshot>>(emptyList())
    private var redoStack by mutableStateOf<List<UndoSnapshot>>(emptyList())
    @Volatile private var batchDepth: Int = 0
    // 本批首拍是否已落: 批内多次 capture 只保第一次 — 但锚定的是本批开始前(非旧快照)
    @Volatile private var batchCaptured: Boolean = false
    @Volatile var restoring: Boolean = false

    /** 撤回深度上限: -1 无限、0 禁用捕获、正数 N 保留最近 N 步。公开可读可写, 走 [applyMaxDepth] 立即生效。 */
    @Volatile var maxDepth: Int = DEFAULT_MAX_DEPTH

    val hasSnapshot: Boolean get() = undoStack.isNotEmpty()
    val hasRedoSnapshot: Boolean get() = redoStack.isNotEmpty()

    fun beginBatch() {
        batchDepth++
        // 批边界 = 新用户动作开始 — 旧动作的快照对新动作是过期锚点, 立即作废。
        // 若真要嵌套批(当前无此用法), 内层 begin 不清 batchCaptured(只在 0→1 清)。
        if (batchDepth == 1) batchCaptured = false
    }

    fun endBatch() { batchDepth = (batchDepth - 1).coerceAtLeast(0) }

    fun capture(
        tables: List<TimeTableEntity>,
        courses: List<CourseEntity>,
        defaultTableId: Long?,
        periodTables: List<PeriodTableEntity> = emptyList()
    ) {
        if (restoring || maxDepth == 0) return
        // 新用户动作 = 历史从 redo 分叉 — redo 立即作废(标准 undo/redo 语义)。
        // restore 期间捕获本就被抑制, 这里只管非 restore 的正常写路径。
        redoStack = emptyList()
        if (batchDepth > 0) {
            if (batchCaptured) return   // 批内已有本动作快照 — 保动作链起点
            batchCaptured = true
        }
        val snap = UndoSnapshot(periodTables, tables, courses, defaultTableId)
        undoStack = (undoStack + snap).trimmed()
    }

    /** 取走栈顶(末元素=最近一步), 取走即弹; 空栈返回 null。 */
    fun poll(): UndoSnapshot? {
        val s = undoStack.lastOrNull() ?: return null
        undoStack = undoStack.dropLast(1)
        return s
    }

    /** 撤回执行时: 当前库态(= 被撤回动作的结果)整体压入 redo 栈。 */
    fun recordRedo(snap: UndoSnapshot) { redoStack = (redoStack + snap).trimmed() }

    /** 取消撤回时取走 redo 栈顶(取走即弹, 与 poll 同语义)。 */
    fun pollRedo(): UndoSnapshot? {
        val s = redoStack.lastOrNull() ?: return null
        redoStack = redoStack.dropLast(1)
        return s
    }

    /**
     * redo 成功后重填 undo 栈顶。capture 在 restore 抑制下不会自己压栈 —
     * 但 redo 也是一次"回到过去"的恢复, 用户随后点撤回应能撤掉它。
     * [snap] = redo 前的库态, 直接压回 undo 栈顶(绕过批/抑制逻辑, 栈顶即此值)。
     */
    fun reinsertForRedoSymmetry(snap: UndoSnapshot) { undoStack = (undoStack + snap).trimmed() }

    /**
     * 设定深度并立即施加: v 为 0 清空两条栈, v 为正把两条栈裁剪到最近 v 条,
     * v 为 -1 或负数视为无限, 不清不裁。
     */
    fun applyMaxDepth(v: Int) {
        maxDepth = v
        if (v == 0) {
            undoStack = emptyList()
            redoStack = emptyList()
        } else if (v > 0) {
            undoStack = undoStack.trimmed()
            redoStack = redoStack.trimmed()
        }
    }

    fun clear() {
        undoStack = emptyList()
        redoStack = emptyList()
    }

    /** 按 maxDepth 裁剪: 保留最近 maxDepth 条(末元素=栈顶), 未超限原样返回。 */
    private fun List<UndoSnapshot>.trimmed(): List<UndoSnapshot> =
        if (maxDepth > 0 && size > maxDepth) takeLast(maxDepth) else this
}
