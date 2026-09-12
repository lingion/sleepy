package com.lingion.sleepy

import android.app.Application
import android.content.res.Configuration
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.repository.ScheduleRepository
import com.lingion.sleepy.util.HolidayManager
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
        // 预热 SharedPreferences: 首次 getSharedPreferences 后台异步加载整文件,
        // 避免冷启动后首个 Compose 屏在主线程同步做磁盘反序列化 (AppPrefs 全部
        // getter 都在调用方线程直读, 严格模式 diskRead / 低端机卡顿来源)。
        // 拿到实例即触发异步 loadFromDisk, 不阻塞本线程。
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                getSharedPreferences("sleepy_prefs", android.content.Context.MODE_PRIVATE)
            }
        }
        // app 回前台时检测：若当前在某节课的课前窗口内，补起流体云（状态兜底）
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
        // 15-min periodic 兜底 (KEEP 幂等): 午夜自续链是单次任务, 强杀进程会清掉,
        // periodic 是唯一能复活它的自主驱动 — schedule() 此前零调用方 (7ecb554 起断链),
        // 恢复挂载使 WidgetUpdater 头注释的兜底描述重新为真。
        WidgetUpdater.schedule(this@SleepyApp)
        // 后台预取节假日数据
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { HolidayManager.preload(this@SleepyApp) } catch (_: Throwable) {}
        }
    }

    /**
     * 系统【运行时】切换深/浅色模式或系统字体缩放 (fontScale) 时联动刷新小组件。
     *
     * Android 原生行为:configuration change 会让系统重发 APPWIDGET_UPDATE 给所有 widget。
     * 历史上 OPPO ColorOS 上 Glance 版 widget(Today/WeekList/TwoDay)因
     * GlanceAppWidgetManager.getGlanceIdBy 返回 null 被静默跳过(v1.0.29 已全移植为
     * 同步 RemoteViews, Glance 层已删除, 决策 D5-11)。
     *
     * 这里主动调 notifyDataChanged() 广播 APPWIDGET_UPDATE,强制全部 5 个
     * RemoteViews widget 重渲染,确保跟随系统主题。
     *
     * fontScale (issue#31 P3「字体遮盖箭头」残余): 顶栏档位 (navHeaderTier) 与
     * bitmap 都在推送时定格 — 用户事后调大系统字体, launcher 会用新字号重 inflate
     * 顶栏 TextView (sp 随宿主缩放), 但我们的档位判定/图不会自动重算, 最长要等
     * 15-min periodic 兜底, 期间大字文本可能挤压箭头。fontScale 一变立即全量重推
     * = 判定与实测同字体口径。
     */
    private var lastNightMode: Int = -1
    private var lastFontScale: Float = -1f

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 仅夜间模式或字体缩放变化才触发刷新,避免屏幕旋转等无谓刷新
        val curNight = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val curFontScale = newConfig.fontScale
        if (curNight != lastNightMode || curFontScale != lastFontScale) {
            lastNightMode = curNight
            lastFontScale = curFontScale
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    WidgetUpdater.notifyDataChanged(this@SleepyApp)
                } catch (_: Throwable) {}
            }
        }
    }

    companion object {
        @Volatile
        private var instance: SleepyApp? = null

        fun get(): SleepyApp = instance
            ?: throw IllegalStateException("SleepyApp.onCreate() not called yet")
    }
}