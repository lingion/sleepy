package com.lingion.sleepy.widget

import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.TimeTableEntity

/**
 * Widget 共用：找当前要展示的课表。
 *
 * 策略：
 * 1. 若存在非默认表（用户导入/手动创建的）→ 取最新一个（id 倒序）
 * 2. 否则用 default 表
 * 3. 否则 null（widget 显示"请先创建课表"）
 *
 * 解决"导入后 widget 仍显示旧默认表/mock 数据"的问题。
 */
object WidgetTableResolver {
    suspend fun resolveCurrentTable(): TimeTableEntity? {
        val repo = SleepyApp.get().repository
        val all = repo.getAllTables()
        // 优先：用户非默认表且有课的（排除刚创建的空表）
        val userTableWithCourses = all.filter { !it.isDefault }
            .maxByOrNull { runCatching { repo.getCourses(it.id).size }.getOrDefault(0) }
            ?.takeIf { runCatching { repo.getCourses(it.id).isNotEmpty() }.getOrDefault(false) }
        if (userTableWithCourses != null) return userTableWithCourses
        // 次选：默认表
        val def = repo.getDefaultTable()
        if (def != null && runCatching { repo.getCourses(def.id).isNotEmpty() }.getOrDefault(false)) {
            return def
        }
        // 最后：任意有课的表
        return all.maxByOrNull { runCatching { repo.getCourses(it.id).size }.getOrDefault(0) }
            ?.takeIf { runCatching { repo.getCourses(it.id).isNotEmpty() }.getOrDefault(false) }
    }
}