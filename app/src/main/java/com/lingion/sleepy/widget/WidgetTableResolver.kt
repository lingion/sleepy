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
        val repo = SleepyApp.get().repository
        val all = repo.getAllTables()
        // 单遍解析: 每张表的课程列表只物化一次 (课程数与「是否有课」同源),
        // 不再默认表检查 + maxByOrNull + takeIf 各读一遍 = 每表 2-3x 列表物化。
        // 选择语义与旧三步逐位一致:
        //   1. 第一个「默认表且有课」直接用 (defaultHit, firstOrNull 口径);
        //   2. 否则课程数最多的表 (并列取先出现者, maxByOrNull 口径), 全空 → null。
        var best: TimeTableEntity? = null
        var bestCount = -1
        var defaultHit: TimeTableEntity? = null
        for (table in all) {
            val count = runCatching { repo.getCourses(table.id).size }.getOrDefault(0)
            if (table.isDefault && count > 0 && defaultHit == null) defaultHit = table
            if (count > bestCount) {
                best = table
                bestCount = count
            }
        }
        return defaultHit ?: best.takeIf { bestCount > 0 }
    }

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
        return runCatching { app.repository.getTable(boundId) }.getOrNull()
    }
}