package com.lingion.sleepy.widget.notification

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.lingion.sleepy.MainActivity
import com.lingion.sleepy.R
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.widget.resolveSchemePublic

/**
 * Keeps the promoted course notification's progress synchronized with the
 * user's before-class reminder window. The capsule text remains static.
 */
class FluidCloudService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var courseName = ""
    private var room = ""
    private var teacher = ""
    private var startTime = ""
    private var notifyEpoch = 0L
    private var classEpoch = 0L

    private val updater = object : Runnable {
        override fun run() {
            postProgressNotification()
            if (System.currentTimeMillis() < classEpoch) {
                handler.postDelayed(this, UPDATE_INTERVAL_MS)
            } else {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        courseName = intent?.getStringExtra("courseName") ?: getString(R.string.default_course_name)
        room = intent?.getStringExtra("room").orEmpty().ifBlank { getString(R.string.default_room) }
        teacher = intent?.getStringExtra("teacher").orEmpty()
        startTime = intent?.getStringExtra("startTime").orEmpty()
        notifyEpoch = intent?.getLongExtra("notifyEpoch", 0L) ?: 0L
        classEpoch = intent?.getLongExtra("classEpoch", 0L) ?: 0L

        if (notifyEpoch <= 0L || classEpoch <= notifyEpoch) {
            val now = System.currentTimeMillis()
            notifyEpoch = now
            classEpoch = now + 1L
        }

        if (classEpoch <= System.currentTimeMillis()) {
            // 修复 P0: startForegroundService 启动后，即使决定立即停止也必须先
            // startForeground()，否则 Android 12+ 抛 ForegroundServiceDidNotStartInTimeException。
            // 用最小占位通知履行契约，随后移除。
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                try {
                    val placeholder = NotificationCompat.Builder(this, CourseNotificationScheduler.CHANNEL_FLUID)
                        .setSmallIcon(R.drawable.ic_notification_time)
                        .setContentTitle(courseName)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .build()
                    startForeground(CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE, placeholder)
                } catch (_: Throwable) {}
            }
            androidx.core.app.NotificationManagerCompat.from(this)
                .cancel(CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        handler.removeCallbacks(updater)
        postProgressNotification()
        if (System.currentTimeMillis() < classEpoch) {
            handler.postDelayed(updater, UPDATE_INTERVAL_MS)
        }
        return START_NOT_STICKY
    }

    private fun postProgressNotification() {
        val now = System.currentTimeMillis()
        val totalWindow = (classEpoch - notifyEpoch).coerceAtLeast(1L)
        val elapsed = (now - notifyEpoch).coerceIn(0L, totalWindow)
        val progress = ((elapsed * 100L) / totalWindow).toInt().coerceIn(0, 100)
        val primary = AppPrefs.getBeforeClassFluidPrimary(this)
        val primaryText = when (primary) {
            "name" -> courseName
            "time" -> startTime
            else -> room
        }
        val coursePreview = buildList {
            if (startTime.isNotBlank()) add(startTime)
            add(room)
            if (teacher.isNotBlank()) add(teacher)
        }.joinToString("  ·  ")

        val style = NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(progress)
        // 不分 segments：课前提醒是一个连续倒计时进度，旧代码的 70/30 分段没有实际语义，
        // 反而在 70% 处把进度条断开造成视觉割裂。

        // 通知色跟随主题（之前硬编码默认紫 0xFF6750A4，用户选春绿/海蓝等主题后通知色与 app 内不一致）。
        // 复用 widget 渲染侧的 resolveSchemePublic 派生 primary（支持 system=MaterialYou 动态取色）。
        val isSystemDark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val themePrimaryArgb = resolveSchemePublic(
            this,
            AppPrefs.getThemeKey(this),
            AppPrefs.isDarkMode(this, isSystemDark)
        ).primary.toArgb()

        val notification = NotificationCompat.Builder(this, CourseNotificationScheduler.CHANNEL_FLUID)
            .setSmallIcon(R.drawable.ic_notification_time)
            .setColor(themePrimaryArgb)
            .setContentTitle(courseName)
            .setContentText(coursePreview)
            .setStyle(style)
            .setSubText(room)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setRequestPromotedOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setShortCriticalText(primaryText.take(7))
            .build()

        if (android.os.Build.VERSION.SDK_INT >= 26) {
            // 前台服务路径: startForeground 本身不需要 POST_NOTIFICATIONS 运行时权限
            startForeground(CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE, notification)
        } else {
            // Lint MissingPermission: 前台服务由 startForegroundService 启动链路触发,
            //   但 API<26 notify 分支仍需权限校验兜底(权限被拒时静默跳过, 不抛 SecurityException)
            if (ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                androidx.core.app.NotificationManagerCompat.from(this)
                    .notify(CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE, notification)
            }
        }
        android.util.Log.d(
            "FluidCloudService",
            "updated course progress=$progress notify=$notifyEpoch class=$classEpoch"
        )
    }

    override fun onDestroy() {
        handler.removeCallbacks(updater)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val UPDATE_INTERVAL_MS = 15_000L
        // MODE_A / MODE_B 死常量已删（从未被读取——服务固定走 ProgressStyle 进度条模式）
    }
}
