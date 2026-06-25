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
        // 用户数据表优先（用户导入/手动创建的都是 isDefault=false）
        val userTable = all.filter { !it.isDefault }.maxByOrNull { it.id }
        return userTable ?: repo.getDefaultTable()
    }
}