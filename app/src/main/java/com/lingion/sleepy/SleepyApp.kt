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
        WidgetUpdater.schedule(this)
        // 每次 app 启动都强制刷新所有 widget — 解决 Glance 缓存不刷新的问题
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