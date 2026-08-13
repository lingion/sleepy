package com.lingion.sleepy.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Widget 主动更新调度器：
 * — 数据变更时调用 [notifyDataChanged]
 * — 对全部 4 个已注册 receiver 广播 APPWIDGET_UPDATE(系统级刷新)
 *   全部 4 个都是同步 RemoteViews AppWidgetProvider → 秒刷,不受 OPPO 冻结影响
 * — WorkManager 每 15 分钟兜底刷新
 */
object WidgetUpdater {

    private const val TAG = "WidgetUpdater"
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

    /**
     * 立即刷新所有已放置的小组件。
     *
     * - 全部 4 个 widget (RemoteViews): 同步广播 APPWIDGET_UPDATE → AppWidgetProvider.onUpdate
     *   同步调 awm.updateAppWidget(id, views) → 在 OPPO 冻结窗口前推送 → 秒刷,不受冻结影响。
     * - WorkManager 每 15 分钟兜底刷新 (schedule)。
     *
     * suspend: 调用方(MineScreen 按钮/主题切换/SleepyApp 等)在协程里 await。
     */
    suspend fun notifyDataChanged(context: Context) {
        Log.e(TAG, ">>> notifyDataChanged ENTERED")
        withContext(Dispatchers.IO) {
            val awm = AppWidgetManager.getInstance(context)

            // ── 全部 4 个小组件 (RemoteViews): 同步广播,秒刷 ──
            // v1.0.29: Today/WeekList/TwoDay 已从 Glance 移植为同步 RemoteViews AppWidgetProvider,
            // 与 WeekGrid 同路径 — 普通 AppWidgetProvider.onUpdate 同步调 awm.updateAppWidget(id, views),
            // 在 OPPO 冻结窗口(5s)前就完成推送 → 永远可靠,不再卡 widget_loading。
            val remoteViewsReceivers = listOf(
                WeekGridWidgetProvider::class.java,
                TodayWidgetReceiver::class.java,
                WeekListWidgetReceiver::class.java,
                WeekViewWidgetReceiver::class.java,
                TwoDayWidgetReceiver::class.java
            )
            for (receiver in remoteViewsReceivers) {
                try {
                    val component = ComponentName(context, receiver)
                    val ids = awm.getAppWidgetIds(component)
                    if (ids.isNotEmpty()) {
                        Log.d(TAG, "${receiver.simpleName} ids=${ids.toList()}, broadcasting UPDATE")
                        val intent = Intent("android.appwidget.action.APPWIDGET_UPDATE").apply {
                            this.component = component
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                        }
                        context.sendBroadcast(intent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "${receiver.simpleName} broadcast failed", e)
                }
            }
        }
    }
}
