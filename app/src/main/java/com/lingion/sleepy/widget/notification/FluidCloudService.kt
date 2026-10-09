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
    private var endTime = ""
    private var startNode = 0
    private var notifyEpoch = 0L
    private var classEpoch = 0L
    private var updateSequence = 0

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
        CourseNotificationScheduler.ensureNotificationChannels(this)
        if (intent?.action == ACTION_STOP) {
            stopCloudNotification()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_TEST) {
            // 流体云测试入口: 用示例课程强制唤起一次, 窗口 2 分钟(进度条真实推进)。
            val now = System.currentTimeMillis()
            courseName = getString(R.string.reminder_fluid_test_course)
            room = getString(R.string.reminder_fluid_test_room)
            teacher = ""
            startTime = android.text.format.DateFormat.format("HH:mm", now + TEST_WINDOW_MS).toString()
            endTime = android.text.format.DateFormat.format("HH:mm", now + TEST_WINDOW_MS + 45 * 60_000L).toString()
            startNode = 1
            notifyEpoch = now
            classEpoch = now + TEST_WINDOW_MS
        } else {
            courseName = intent?.getStringExtra("courseName") ?: getString(R.string.default_course_name)
            room = intent?.getStringExtra("room").orEmpty().ifBlank { getString(R.string.default_room) }
            teacher = intent?.getStringExtra("teacher").orEmpty()
            startTime = intent?.getStringExtra("startTime").orEmpty()
            endTime = intent?.getStringExtra("endTime").orEmpty()
            startNode = intent?.getIntExtra("startNode", 0) ?: 0
            notifyEpoch = intent?.getLongExtra("notifyEpoch", 0L) ?: 0L
            classEpoch = intent?.getLongExtra("classEpoch", 0L) ?: 0L
        }

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
        updateSequence = if (updateSequence == Int.MAX_VALUE) 1 else updateSequence + 1
        val state = CourseLiveCardState(
            courseName = courseName,
            room = room,
            teacher = teacher,
            startTime = startTime,
            notifyEpoch = notifyEpoch,
            classEpoch = classEpoch,
            nowEpoch = now,
            updateSequence = updateSequence,
            endTime = endTime,
            startNode = startNode
        )
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = VendorLiveCardRenderer.build(
            context = this,
            state = state,
            contentIntent = contentIntent,
            channelId = CourseNotificationScheduler.CHANNEL_FLUID
        )

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
            "updated course progress=${state.progress} notify=$notifyEpoch class=$classEpoch"
        )
    }

    private fun stopCloudNotification() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            try {
                val placeholder = NotificationCompat.Builder(this, CourseNotificationScheduler.CHANNEL_FLUID)
                    .setSmallIcon(R.drawable.ic_notification_time)
                    .setContentTitle(getString(R.string.reminder_fluid_title))
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
                startForeground(CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE, placeholder)
            } catch (_: Throwable) {}
        }
        handler.removeCallbacks(updater)
        androidx.core.app.NotificationManagerCompat.from(this)
            .cancel(CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(updater)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val UPDATE_INTERVAL_MS = 15_000L
        private const val TEST_WINDOW_MS = 2 * 60_000L
        const val ACTION_TEST = "com.lingion.sleepy.action.FLUID_TEST"
        const val ACTION_STOP = "com.lingion.sleepy.action.FLUID_STOP"

        fun requestStop(context: android.content.Context) {
            val intent = Intent(context, FluidCloudService::class.java).setAction(ACTION_STOP)
            try {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } catch (_: Throwable) {
                androidx.core.app.NotificationManagerCompat.from(context)
                    .cancel(CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE)
            }
        }
    }
}
