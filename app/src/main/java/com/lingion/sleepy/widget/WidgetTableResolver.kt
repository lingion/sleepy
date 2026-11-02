package com.lingion.sleepy.widget

import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.TimeTableEntity

/**
 * Widget 共用：找当前要展示的课表。
 *
 * 策略（与 App 内默认课表一致）：
 * 1. 默认表（isDefault=true）且有课 → 用它
 * 2. 否则任意有课的表（按课程数最多）
 * 3. 否则 null（widget 显示"请先创建课表"）
 *
 * 修复：旧逻辑"优先选非默认表中课程数最多的"，导致只要存在任何非默认表
 *   （如测试/导入副表），widget 就脱离用户在 App 里设的默认表，App 与 widget 不同步。
 */
object WidgetTableResolver {
    suspend fun resolveCurrentTable(): TimeTableEntity? {
        val app = SleepyApp.get()
        val repo = app.repository
        val all = repo.getAllTables()
        val currentSelectedId = com.lingion.sleepy.util.AppPrefs.getCurrentTableId(app)

        var selectedHit: TimeTableEntity? = null
        var defaultHit: TimeTableEntity? = null
        var best: TimeTableEntity? = null
        var bestCount = -1

        for (table in all) {
            val count = runCatching { repo.getCourses(table.id).size }.getOrDefault(0)
            if (currentSelectedId != null && table.id == currentSelectedId) {
                selectedHit = table
            }
            if (table.isDefault && count > 0 && defaultHit == null) {
                defaultHit = table
            }
            if (count > bestCount) {
                best = table
                bestCount = count
            }
        }
        // issue#40: 选出的表若绑定了独立时间节次表, 出口水合 —
        // 节次时间/智慧节次/节次数按绑定表解释, 兼容列兜底。悬空引用静默回退旧列。
        val chosen = selectedHit ?: defaultHit ?: best.takeIf { bestCount > 0 } ?: return null
        return chosen.hydratedWith(repo.getPeriodTableOrNull(chosen.periodTableId))
    }

    /** issue#40: null 安全取时间节次表 — id 为 null 或查无此行都返回 null */
    private suspend fun com.lingion.sleepy.data.repository.ScheduleRepository.getPeriodTableOrNull(
        periodTableId: Long?
    ) = periodTableId?.let { runCatching { getPeriodTable(it) }.getOrNull() }

    /**
     * Resolve the table a specific widget instance is bound to.
     *
     * Returns the bound table if the user has explicitly bound one and it
     * still exists in the database. Returns null when the widget has no
     * binding or the bound table was deleted — callers should fall back to
     * [resolveCurrentTable] in that case.
     *
     * Lazy invalidation: a deleted table does NOT rewrite the binding entry;
     * [WidgetBindingStore.remove] is invoked only from the receiver's
     * `onDeleted` callback when the widget instance itself is removed.
     */
    suspend fun resolveBoundTable(widgetId: Int): TimeTableEntity? {
        val app = SleepyApp.get()
        val boundId = WidgetBindingStore.get(app, widgetId) ?: return null
        val table = runCatching { app.repository.getTable(boundId) }.getOrNull() ?: return null
        // issue#40: 绑定实例同样按其绑定的独立时间节次表水合
        val periodTable = table.periodTableId?.let {
            runCatching { app.repository.getPeriodTable(it) }.getOrNull()
        }
        return table.hydratedWith(periodTable)
    }
}