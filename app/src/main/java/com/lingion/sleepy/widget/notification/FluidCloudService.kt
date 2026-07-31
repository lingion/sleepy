package com.lingion.sleepy.widget.notification

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.lingion.sleepy.MainActivity
import com.lingion.sleepy.R
import com.lingion.sleepy.util.AppPrefs

/**
 * Keeps the promoted course notification's progress synchronized with the
 * user's before-class reminder window. The capsule text remains static.
 */
class FluidCloudService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var courseName = "课程"
    private var room = "上课地点"
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
        courseName = intent?.getStringExtra("courseName") ?: "课程"
        room = intent?.getStringExtra("room").orEmpty().ifBlank { "上课地点" }
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
            .setProgressSegments(
                listOf(
                    NotificationCompat.ProgressStyle.Segment(70),
                    NotificationCompat.ProgressStyle.Segment(30)
                )
            )

        val notification = NotificationCompat.Builder(this, CourseNotificationScheduler.CHANNEL_FLUID)
            .setSmallIcon(R.drawable.ic_notification_time)
            .setColor(0xFF6750A4.toInt())
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
            startForeground(CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE, notification)
        } else {
            androidx.core.app.NotificationManagerCompat.from(this)
                .notify(CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE, notification)
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
        const val MODE_A = "progress_style"
        const val MODE_B = "marquee"
    }
}
