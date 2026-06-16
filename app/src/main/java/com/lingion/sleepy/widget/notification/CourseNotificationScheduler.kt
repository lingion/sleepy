package com.lingion.sleepy.widget.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lingion.sleepy.MainActivity
import com.lingion.sleepy.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 课程通知调度器 — 每天早上 7:00 推送今日课程提醒。
 * 完全自包含，不依赖任何外部 SDK。
 */
class CourseNotificationScheduler(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "wakeup_pure_daily"
        const val CHANNEL_NAME = "每日课程提醒"
        const val NOTIFY_ID = 1001
        const val ALARM_HOUR = 7
        const val ALARM_MINUTE = 0
    }

    fun scheduleDailyReminder() {
        createNotificationChannel()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailyNotifyReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 计算明天 7:00
        val now = LocalTime.now()
        val target = LocalTime.of(ALARM_HOUR, ALARM_MINUTE)
        var next = LocalDate.now().atTime(target)
        if (now.isAfter(target)) next = next.plusDays(1)
        val nextEpoch = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            nextEpoch,
            AlarmManager.INTERVAL_DAY,
            pending
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "每天早上推送今日课程安排"
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}

class DailyNotifyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 读取今日课程并发送通知（简化版）
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return

        val i = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val today = LocalDate.now()
        val dayName = com.lingion.sleepy.util.DateUtils.chineseDay(today.dayOfWeek.value)
        val notification = NotificationCompat.Builder(context, CourseNotificationScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle("今日课程 — ${today.monthValue}月${today.dayOfMonth}日 $dayName")
            .setContentText("点击查看今日课程安排")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(CourseNotificationScheduler.NOTIFY_ID, notification)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED
            || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            com.lingion.sleepy.SleepyApp.get().notificationScheduler.scheduleDailyReminder()
        }
    }
}