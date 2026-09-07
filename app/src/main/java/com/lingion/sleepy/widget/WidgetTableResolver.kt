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
        // 优先：默认表且有课
        val def = all.firstOrNull { it.isDefault }
            ?.takeIf { runCatching { repo.getCourses(it.id).isNotEmpty() }.getOrDefault(false) }
        if (def != null) return def
        // 次选：任意有课的表（课程数最多）
        return all.maxByOrNull { runCatching { repo.getCourses(it.id).size }.getOrDefault(0) }
            ?.takeIf { runCatching { repo.getCourses(it.id).isNotEmpty() }.getOrDefault(false) }
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