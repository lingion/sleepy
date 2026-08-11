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
 * ★ 修复：旧逻辑"优先选非默认表中课程数最多的"，导致只要存在任何非默认表
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
}