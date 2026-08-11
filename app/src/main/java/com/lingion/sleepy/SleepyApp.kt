package com.lingion.sleepy

import android.app.Application
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.repository.ScheduleRepository
import com.lingion.sleepy.widget.WidgetUpdater
import com.lingion.sleepy.widget.notification.CourseNotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application 类 — 初始化全局依赖。
 *
 * 没有任何 SDK / 广告 / 拍照搜题，只有：
 * - Room 数据库
 * - 课表仓库
 * - 每日课程通知调度
 * - 小组件定期刷新
 */
class SleepyApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val repository: ScheduleRepository by lazy { ScheduleRepository(database) }
    val notificationScheduler: CourseNotificationScheduler by lazy {
        CourseNotificationScheduler(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        androidx.core.app.NotificationManagerCompat.from(this)
            .cancel(CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE)
        // ★ app 回前台时检测：若当前在某节课的课前窗口内，补起流体云（状态兜底）
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : androidx.lifecycle.DefaultLifecycleObserver {
                override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        try { notificationScheduler.ensureActiveFluidCloud() } catch (_: Throwable) {}
                    }
                }
            }
        )
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            WidgetUpdater.notifyDataChanged(this@SleepyApp)
        }
    }

    companion object {
        @Volatile
        private var instance: SleepyApp? = null

        fun get(): SleepyApp = instance
            ?: throw IllegalStateException("SleepyApp.onCreate() not called yet")
    }
}