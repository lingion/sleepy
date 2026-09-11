package com.lingion.sleepy.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Widget 主动更新调度器：
 * — 数据变更时调用 [notifyDataChanged]
 * — 对全部 10 个已注册 receiver 广播 APPWIDGET_UPDATE(系统级刷新)
 *   普通和小尺寸变体都是同步 RemoteViews AppWidgetProvider → 秒刷,不受 OPPO 冻结影响
 * — WorkManager 每 15 分钟兜底刷新
 *
 * 跨厂商兼容事实底座: docs/widget-vendor-specs/INDEX.md
 * （华为/荣耀/小米/OPPO/vivo/魅族/三星启动器的约束清单 + 已落地/明确不做对照表）。
 * 本类是"同步广播秒刷"规则的实现点 — 该规则同时解决 OPPO Glance 冻结与
 * HyperOS 去定时刷新后的兜底（WorkManager 周期任务）。
 *
 * 跨天兜底: 15-min periodic 在 00:00 后最长可晚 15+ 分钟才刷 (doze 更久) →
 *   [notifyDataChanged] 每次都把一个单次任务排到下一个本地午夜 ([nextMidnightDelayMillis]),
 *   唯一名 REPLACE 幂等重排; 午夜 worker 走同一条 notifyDataChanged → 链自续,
 *   跨 00:00 后立刻显示新的一天 (时区变化会打断挂钟对齐, 由下次任意刷新重新对齐)。
 */
object WidgetUpdater {

    private const val TAG = "WidgetUpdater"
    private const val WORK_NAME = "sleepy_widget_update"
    private const val MIDNIGHT_WORK_NAME = "sleepy_widget_midnight_update"
    private const val REPEAT_MINUTES = 15L

    /** All widget providers receiving the synchronous refresh broadcast. */
    internal val remoteViewsReceiverClasses: List<Class<out AppWidgetProvider>> =
        ALL_WIDGET_VARIANTS.map { it.receiverClass }

    /** 距下一个本地午夜的毫秒数 — 恒 >0 (午夜整点 = 整 24h), 纯函数可 JVM 单测。 */
    internal fun nextMidnightDelayMillis(now: LocalDateTime): Long {
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
        return Duration.between(now, nextMidnight).toMillis()
    }

    /**
     * 排一个到下个午夜的刷新单次任务 (幂等): 唯一名 + REPLACE → 任意刷新路径重复
     * 调用只保留最新对齐结果, 链不重复不分叉。
     */
    private fun armMidnightRefresh(context: Context) {
        val delay = nextMidnightDelayMillis(LocalDateTime.now())
        val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .setConstraints(Constraints.Builder().build())
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            MIDNIGHT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

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
     * - 全部 10 个 widget (RemoteViews): 同步广播 APPWIDGET_UPDATE → AppWidgetProvider.onUpdate
     *   同步调 awm.updateAppWidget(id, views) → 在 OPPO 冻结窗口前推送 → 秒刷,不受冻结影响。
     * - WorkManager 每 15 分钟兜底刷新 (schedule)。
     *
     * suspend: 调用方(MineScreen 按钮/主题切换/SleepyApp 等)在协程里 await。
     */
    suspend fun notifyDataChanged(context: Context) {
        withContext(Dispatchers.IO) {
            val awm = AppWidgetManager.getInstance(context)

            // ── 全部 10 个小组件 (RemoteViews): 同步广播,秒刷 ──

            // v1.0.29: Today/WeekList/TwoDay 已从 Glance 移植为同步 RemoteViews AppWidgetProvider,
            // 与 WeekGrid 同路径 — 普通 AppWidgetProvider.onUpdate 同步调 awm.updateAppWidget(id, views),
            // 在 OPPO 冻结窗口(5s)前就完成推送 → 永远可靠,不再卡 widget_loading。
            val remoteViewsReceivers = remoteViewsReceiverClasses
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
        // 跨天链: 广播全部落地后重新对齐下一晚 (放 withContext 外 — worker 取消信号
        // 不会打断已提交的 WorkManager 排程; 午夜 worker 走本函数 → 链自续)。
        runCatching { armMidnightRefresh(context) }
    }
}
