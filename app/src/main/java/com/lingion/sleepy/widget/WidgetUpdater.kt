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
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Widget 主动更新调度器：
 * — 数据变更时调用 [notifyDataChanged]
 * — 对全部 4 个已注册 receiver 广播 APPWIDGET_UPDATE(系统级刷新, 最可靠)
 * — Glance .update() 兜底(部分启动器延迟渲染时补充)
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
     * ★ 根因修复(用户反馈"刷新功能是假的"):
     * 之前只用 GlanceAppWidgetManager.getGlanceIds() + .update() 刷新 Glance 小组件。
     * 问题: ① getGlanceIds 在部分设备/启动器(尤其 OPPO ColorOS)上返回空列表 → 永远刷不到;
     *       ② WeekGridWidget(Glance) 根本没注册, 只有 WeekGridWidgetProvider(RemoteViews) 注册了;
     *       ③ Glance .update() 是异步的, launcher 进程可能因 cache 不重渲染 → 视觉无变化。
     *
     * 正解: 对全部 4 个已注册的 receiver 广播 APPWIDGET_UPDATE + APPWIDGET_IDS。
     *       这是 Android 系统级刷新机制, GlanceAppWidgetReceiver 和 AppWidgetProvider 都接,
     *       比绕过系统直接调 .update() 可靠得多。
     */
    suspend fun notifyDataChanged(context: Context) {
        withContext(Dispatchers.IO) {
            // ── 对全部 4 个已注册 receiver 广播 APPWIDGET_UPDATE ──
            val awm = AppWidgetManager.getInstance(context)
            val receivers = listOf(
                TodayWidgetReceiver::class.java,
                WeekListWidgetReceiver::class.java,
                WeekGridWidgetProvider::class.java,
                TwoDayWidgetReceiver::class.java
            )
            for (receiver in receivers) {
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
                    Log.e(TAG, "${receiver.simpleName} refresh failed", e)
                }
            }

            // ── Glance .update() 兜底(部分启动器对 APPWIDGET_UPDATE 广播延迟渲染) ──
            try {
                val manager = GlanceAppWidgetManager(context)
                val glanceWidgets = listOf(
                    TodayWidget::class.java to TodayWidget(),
                    TwoDayWidget::class.java to TwoDayWidget(),
                    WeekListWidget::class.java to WeekListWidget()
                )
                var totalUpdated = 0
                for ((clazz, widget) in glanceWidgets) {
                    val glanceIds = manager.getGlanceIds(clazz)
                    if (glanceIds.isNotEmpty()) {
                        Log.d(TAG, "Glance ${clazz.simpleName} ids=${glanceIds.toList()}, calling update()")
                        glanceIds.forEach { id -> widget.update(context, id); totalUpdated++ }
                    }
                }
                if (totalUpdated > 0) Log.d(TAG, "Glance .update() fallback updated $totalUpdated widgets")
            } catch (e: Exception) {
                Log.w(TAG, "Glance fallback update failed: ${e.message}")
            }
        }
    }
}
