package com.lingion.sleepy.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Widget 主动更新调度器：
 * — 数据变更时调用 [notifyDataChanged]
 * — WorkManager 每 15 分钟兜底刷新所有已放置的小组件
 * — 跨天时强制刷新（凌晨 00:05 触发）
 *
 * 使用方式：App 启动 / 课程变更后调 [notifyDataChanged]。
 */
object WidgetUpdater {

    private const val WORK_NAME = "sleepy_widget_update"
    private const val REPEAT_MINUTES = 15L

    /** 注册定期刷新（幂等） */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            REPEAT_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(Constraints.Builder().build())
            .setInitialDelay(3, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** 立即刷新所有已放置的小组件 */
    suspend fun notifyDataChanged(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val manager = GlanceAppWidgetManager(context)
                // TodayWidget
                manager.getGlanceIds(TodayWidget::class.java).forEach { id ->
                    TodayWidget().update(context, id)
                }
                // WeekWidget
                manager.getGlanceIds(WeekWidget::class.java).forEach { id ->
                    WeekWidget().update(context, id)
                }
                // TwoDayWidget
                manager.getGlanceIds(TwoDayWidget::class.java).forEach { id ->
                    TwoDayWidget().update(context, id)
                }
            } catch (_: Exception) {
                // 小组件未放置时可能抛异常，忽略
            }
        }
    }
}
