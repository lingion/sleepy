package com.lingion.sleepy.widget

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * ★ OPPO ColorOS 冻结根治 worker。
 *
 * 根因(2026-08-12 日志实证):Glance 的 [GlanceAppWidget.update] 只把渲染任务入队给
 * WorkManager 的 SessionWorker,真正的 Compose→RemoteViews 转换在后台异步完成。
 * OPPO OplusHansManager 在 update 触发后约 5 秒冻结 app 进程
 * (日志: "freeze uid: 10985 com.lingion.sleepy scene: LcdOn"),
 * SessionWorker 被 cancel → "Session is not available for appWidget-xxx" → RemoteViews
 * 从未生成 → 3 个 Glance 小组件(Today/WeekList/TwoDay)不刷新。
 * 用户点刷新后立刻回桌面看效果 → app 进后台 → 被冻 → 渲染中断。
 * WeekGrid(RemoteViews)同步渲染→awm.updateAppWidget 立即推送→永远秒刷,不受影响。
 *
 * 解法:用 expedited job 包住刷新 + guard 窗口。expedited job 在系统配额内享前台执行
 * 优先级,进程不被 OplusHansManager 冻结 → Glance SessionWorker 能跑完渲染。
 *
 * (不用 setForeground:WorkManager 的 SystemForegroundService manifest 未声明 matching 的
 * foregroundServiceType → startForeground 抛 IllegalArgumentException → 崩溃。已实测。)
 */
class WidgetRefreshNowWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            android.util.Log.e(TAG, ">>> WidgetRefreshNowWorker START (expedited)")

            // Glance 三件套直更(渲染在 SessionWorker 里异步跑)
            WidgetUpdater.updateGlanceWidgetsNow(applicationContext)

            // ★ 留足时间让 Glance SessionWorker 完成渲染。
            //   expedited job 享前台执行优先级(系统配额内),期间进程不被冻 →
            //   SessionWorker 能跑完渲染。实测 OPPO 在 update 后 ~5s 冻结,这里 hold 4s。
            delay(GLANCE_RENDER_GUARD_MS)
            android.util.Log.e(TAG, ">>> WidgetRefreshNowWorker guard window elapsed, releasing")

            Result.success()
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "WidgetRefreshNowWorker failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "WidgetRefreshNow"
        private const val WORK_NAME = "sleepy_widget_refresh_now"
        // Glance provideGlance + RemoteViews 渲染通常 < 2s,留 4s 余量覆盖 OPPO 5s 冻结窗口
        private const val GLANCE_RENDER_GUARD_MS = 4000L

        /** 立即触发一次前台优先级保护的 Glance 小组件刷新。幂等(REPLACE)。 */
        fun enqueue(context: Context) {
            val req = OneTimeWorkRequestBuilder<WidgetRefreshNowWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                .setInputData(workDataOf("ts" to System.currentTimeMillis()))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                req
            )
        }
    }
}
