package com.lingion.sleepy.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.Bundle
import androidx.glance.appwidget.GlanceAppWidget
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
 * — 对全部 4 个已注册 receiver 广播 APPWIDGET_UPDATE(系统级刷新)
 * — Glance 直接 .update() 兜底(用 AppWidgetManager appWidgetId 构造 GlanceId,
 *   绕过 GlanceAppWidgetManager · 其 getGlanceIds/getGlanceIdBy 在 OPPO ColorOS 上返回空)
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
     * ★ Glance receiver 的 onUpdate 救援 — 绕过父类 onUpdate 的 OPPO bug。
     *
     * 父类 [GlanceAppWidgetReceiver.onUpdate] 内部依赖 GlanceAppWidgetManager 查 glanceId,
     * 在 OPPO ColorOS 上该查找返回空 → 静默跳过 → Glance 小组件不重新渲染。
     * 系统 APPWIDGET_UPDATE (用户刷新/系统主题切换/周期刷新) 全走父类 onUpdate → 全部失效。
     *
     * 这里直接用 appWidgetId 构造 GlanceId(int) → widget.update() 强制重渲染。
     * 在 IO 协程跑, 不阻塞主线程。
     *
     * 返回 true = 已处理(父类可跳过); false = 无 widget / 失败(让父类兜底试)。
     */
    suspend fun updateGlanceWidgetDirect(
        context: Context,
        appWidgetIds: IntArray,
        widget: GlanceAppWidget
    ): Boolean {
        Log.e(TAG, ">>> updateGlanceWidgetDirect ENTERED: ${widget.javaClass.simpleName} ids=${appWidgetIds.toList()}")
        if (appWidgetIds.isEmpty()) return false
        return withContext(Dispatchers.IO) {
            val glanceMgr = GlanceAppWidgetManager(context)
            var anyUpdated = false
            for (appWidgetId in appWidgetIds) {
                try {
                    val gid = glanceMgr.getGlanceIdBy(appWidgetId)
                    widget.update(context, gid)
                    anyUpdated = true
                    Log.d(TAG, "onUpdate rescue: ${widget.javaClass.simpleName} id=$appWidgetId")
                } catch (e: Exception) {
                    Log.e(TAG, "onUpdate rescue failed: ${widget.javaClass.simpleName} id=$appWidgetId", e)
                }
            }
            anyUpdated
        }
    }

    /**
     * 立即刷新所有已放置的小组件。
     *
     * - WeekGrid(RemoteViews): 同步广播 APPWIDGET_UPDATE → onReceive 同步 push → 秒刷,
     *   不受 OPPO 冻结影响。
     * - 3 个 Glance widget: 走 [WidgetRefreshNowWorker] (expedited + setForeground),
     *   渲染期间进程前台优先级 → 挡住 OPPO OplusHansManager 冻结 → SessionWorker
     *   能跑完 → RemoteViews 真正落地。(根因见 WidgetRefreshNowWorker 注释)
     *
     * suspend: 调用方(MineScreen 按钮/主题切换/SleepyApp 等)在协程里 await。
     * Glance 侧异步走 worker,此函数 await WeekGrid 同步部分后即返回(不等 Glance 渲染完,
     * 渲染在 worker 内自行 guard)。
     */
    suspend fun notifyDataChanged(context: Context) {
        Log.e(TAG, ">>> notifyDataChanged ENTERED")
        withContext(Dispatchers.IO) {
            val awm = AppWidgetManager.getInstance(context)

            // ── WeekGrid (RemoteViews): 同步广播,秒刷 ──
            // 普通 AppWidgetProvider.onUpdate 同步调 awm.updateAppWidget(id, views),
            // 在 OPPO 冻结窗口(5s)前就完成推送 → 永远可靠。
            val remoteViewsReceivers = listOf(WeekGridWidgetProvider::class.java)
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

        // ── Glance 三件套: 走前台优先级 worker,挡冻结 ──
        // 不在此直接 widget.update():update() 只入队 SessionWorker 异步渲染,返回后
        // 若 app 退后台(app 内点完刷新立刻回桌面看效果),OPPO 5s 内冻进程 → 渲染中断。
        // worker 的 setForeground 保证渲染窗口内进程不被冻。
        WidgetRefreshNowWorker.enqueue(context)
    }

    /**
     * ★ 仅 Glance 三件套直更 — 由 [WidgetRefreshNowWorker] 在前台优先级下调用。
     *
     * 用 AppWidgetManager.getAppWidgetIds(component) 拿 widget id(OPPO 上正常),
     * GlanceAppWidgetManager.getGlanceIdBy(int) int→GlanceId 包装,调 .update() 强制重渲染。
     * (绕过 GlanceAppWidgetReceiver.onUpdate 父类的 getGlanceIdBy DataStore 查询 — 该查询
     *  在 OPPO 上返回空,导致系统直发的 APPWIDGET_UPDATE 静默跳过。)
     */
    suspend fun updateGlanceWidgetsNow(context: Context) {
        Log.e(TAG, ">>> updateGlanceWidgetsNow ENTERED")
        withContext(Dispatchers.IO) {
            val awm = AppWidgetManager.getInstance(context)
            val glanceMgr = GlanceAppWidgetManager(context)
            val glanceWidgets = listOf(
                TodayWidget() to "TodayWidget",
                TwoDayWidget() to "TwoDayWidget",
                WeekListWidget() to "WeekListWidget"
            )
            for ((widget, name) in glanceWidgets) {
                try {
                    val receiverCls = when (widget) {
                        is TodayWidget -> TodayWidgetReceiver::class.java
                        is TwoDayWidget -> TwoDayWidgetReceiver::class.java
                        is WeekListWidget -> WeekListWidgetReceiver::class.java
                        else -> null
                    }
                    val ids = if (receiverCls != null) {
                        awm.getAppWidgetIds(ComponentName(context, receiverCls))
                    } else intArrayOf()
                    Log.e(TAG, "DIAG $name: ids=${ids.toList()}")
                    for (appWidgetId in ids) {
                        val gid = glanceMgr.getGlanceIdBy(appWidgetId)
                        Log.e(TAG, "DIAG $name: BEFORE widget.update id=$appWidgetId")
                        widget.update(context, gid)
                        Log.e(TAG, "DIAG $name: AFTER widget.update id=$appWidgetId")
                        // updateAppWidgetOptions 回写:触发 launcher 重消费 RemoteViews
                        try {
                            val opts = awm.getAppWidgetOptions(appWidgetId)
                            awm.updateAppWidgetOptions(appWidgetId, opts)
                            Log.e(TAG, "DIAG $name: updateAppWidgetOptions done id=$appWidgetId")
                        } catch (e: Exception) {
                            Log.e(TAG, "DIAG $name: updateAppWidgetOptions FAILED id=$appWidgetId", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Glance direct update failed: $name", e)
                }
            }
        }
    }
}
