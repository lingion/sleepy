package com.lingion.sleepy.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Widget 主动更新调度器：
 * — 数据变更时调用 [notifyDataChanged]
 * — 同时刷新 Glance widget + RemoteViews widget (WeekGridWidgetProvider)
 * — 带重试机制：Glance .update() 异步可能延迟，首次失败后重试 2 次
 * — WorkManager 每 15 分钟兜底刷新
 */
object WidgetUpdater {

    private const val TAG = "WidgetUpdater"
    private const val WORK_NAME = "sleepy_widget_update"
    private const val REPEAT_MINUTES = 15L
    private const val MAX_RETRIES = 3

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

    /**
     * 立即刷新所有已放置的小组件（Glance + RemoteViews）。
     * 带重试：Glance 异步 update 可能因为进程调度延迟，重试确保最终生效。
     */
    suspend fun notifyDataChanged(context: Context) {
        withContext(Dispatchers.IO) {
            // ── 1. RemoteViews widget (WeekGridWidgetProvider — Bitmap/Canvas) ──
            // 这是之前漏掉的：通过广播 APPWIDGET_UPDATE 强制刷新
            try {
                val awm = AppWidgetManager.getInstance(context)
                val provider = ComponentName(context, WeekGridWidgetProvider::class.java)
                val ids = awm.getAppWidgetIds(provider)
                if (ids.isNotEmpty()) {
                    Log.d(TAG, "RemoteViews widget ids=${ids.toList()}, broadcasting UPDATE")
                    val intent = Intent("android.appwidget.action.APPWIDGET_UPDATE").apply {
                        component = provider
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                    context.sendBroadcast(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "RemoteViews widget refresh failed", e)
            }

            // ── 2. Glance widgets — 带重试 ──
            var lastError: Exception? = null
            for (attempt in 1..MAX_RETRIES) {
                try {
                    val manager = GlanceAppWidgetManager(context)
                    var totalUpdated = 0

                    manager.getGlanceIds(TodayWidget::class.java).forEach { id ->
                        TodayWidget().update(context, id); totalUpdated++
                    }
                    manager.getGlanceIds(WeekListWidget::class.java).forEach { id ->
                        WeekListWidget().update(context, id); totalUpdated++
                    }
                    manager.getGlanceIds(WeekGridWidget::class.java).forEach { id ->
                        WeekGridWidget().update(context, id); totalUpdated++
                    }
                    manager.getGlanceIds(TwoDayWidget::class.java).forEach { id ->
                        TwoDayWidget().update(context, id); totalUpdated++
                    }

                    Log.d(TAG, "Glance widgets updated: $totalUpdated (attempt $attempt)")
                    if (totalUpdated > 0 || attempt == MAX_RETRIES) break
                } catch (e: Exception) {
                    lastError = e
                    Log.w(TAG, "Glance update attempt $attempt failed: ${e.message}")
                }
                // 重试前等 500ms
                delay(500)
            }
            lastError?.let { Log.e(TAG, "Glance widget refresh failed after $MAX_RETRIES attempts", it) }
        }
    }
}
