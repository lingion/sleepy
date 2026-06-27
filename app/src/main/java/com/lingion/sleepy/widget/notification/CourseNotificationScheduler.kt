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
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 课程通知调度器 — 支持每日提醒 + 每节课前提醒。
 *
 * 每日提醒：在用户指定时间发送今日课程摘要。
 * 课前提醒：每天凌晨调度当天每节课前 N 分钟的通知。
 */
class CourseNotificationScheduler(private val context: Context) {

    companion object {
        const val CHANNEL_DAILY = "sleepy_daily"
        const val CHANNEL_BEFORE_CLASS = "sleepy_before_class"

        // Request codes for PendingIntent discrimination
        private const val RC_DAILY = 1
        private const val RC_BEFORE_CLASS_SCHEDULER = 2
        private const val RC_BEFORE_CLASS_BASE = 100 // + courseId offset

        // Notification IDs
        const val NOTIFY_DAILY = 1001
        const val NOTIFY_BEFORE_CLASS_BASE = 2000 // + courseId offset
    }

    fun scheduleAll() {
        createChannels()
        cancelAll()

        val prefs = context.applicationContext
        if (!AppPrefs.isReminderEnabled(prefs)) return

        if (AppPrefs.isDailyReminderEnabled(prefs)) {
            scheduleDaily()
        }
        if (AppPrefs.isBeforeClassEnabled(prefs)) {
            scheduleBeforeClassDaily()
        }
    }

    fun cancelAll() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Cancel daily
        alarmManager.cancel(buildPendingIntent(RC_DAILY, DailyNotifyReceiver::class.java))

        // Cancel before-class scheduler
        alarmManager.cancel(buildPendingIntent(RC_BEFORE_CLASS_SCHEDULER, BeforeClassScheduleReceiver::class.java))

        // Cancel all individual before-class alarms — we don't know exact courseIds,
        // but cancelAll of a range is not supported. Instead, BeforeClassScheduleReceiver
        // reschedules fresh each day, and cancelAll cancels the daily scheduler.
        // For safety, cancel a reasonable range of before-class pending intents.
        for (i in 0 until 50) {
            try {
                alarmManager.cancel(buildPendingIntent(RC_BEFORE_CLASS_BASE + i, BeforeClassNotifyReceiver::class.java))
            } catch (_: Exception) {}
        }
    }

    // ==================== Daily ====================

    private fun scheduleDaily() {
        val timeStr = AppPrefs.getDailyReminderTime(context)
        val parts = timeStr.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 7
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = buildPendingIntent(RC_DAILY, DailyNotifyReceiver::class.java)

        val target = LocalTime.of(hour, minute)
        var next = LocalDate.now().atTime(target)
        if (LocalTime.now().isAfter(target)) next = next.plusDays(1)
        val epoch = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        setRepeatingAlarm(alarmManager, epoch, AlarmManager.INTERVAL_DAY, pending)
    }

    // ==================== Before-class scheduler ====================

    private fun scheduleBeforeClassDaily() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = buildPendingIntent(RC_BEFORE_CLASS_SCHEDULER, BeforeClassScheduleReceiver::class.java)

        // Schedule at 00:05 every day
        val target = LocalTime.of(0, 5)
        var next = LocalDate.now().atTime(target)
        if (LocalTime.now().isAfter(target)) next = next.plusDays(1)
        val epoch = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        setRepeatingAlarm(alarmManager, epoch, AlarmManager.INTERVAL_DAY, pending)

        // Also immediately schedule for today (in case app was opened after midnight)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            scheduleTodayBeforeClassAlarms()
        }
    }

    /**
     * Queries today's courses and schedules individual before-class alarms.
     * Called by [BeforeClassScheduleReceiver] at midnight and by [scheduleBeforeClassDaily].
     */
    suspend fun scheduleTodayBeforeClassAlarms() {
        val app = context.applicationContext
        if (!AppPrefs.isBeforeClassEnabled(app)) return

        val minutes = AppPrefs.getBeforeClassMinutes(app)
        val today = LocalDate.now()
        val dow = DateUtils.todayDayOfWeek(today)

        val table = resolveCurrentTable() ?: return
        val week = DateUtils.currentWeek(table.startDate, today)
        val courses = SleepyApp.get().repository.getCoursesByDayOnce(table.id, dow)
            .filter { it.inWeek(week) }

        // Parse time nodes
        val nodes = TimeTableUtils.parseNodes(table.timeJson)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()

        courses.forEachIndexed { index, course ->
            // Get course start time
            val startTimeStr = if (course.ownTime && course.startTime.isNotBlank()) {
                course.startTime
            } else {
                nodes.find { it.node == course.startNode }?.let { String.format("%02d:%02d", it.start.hour, it.start.minute) }
            } ?: return@forEachIndexed

            val parts = startTimeStr.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: return@forEachIndexed
            val m = parts.getOrNull(1)?.toIntOrNull() ?: return@forEachIndexed

            val classStart = today.atTime(h, m)
            val notifyTime = classStart.minusMinutes(minutes.toLong())
            val epoch = notifyTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            // Only schedule future alarms
            if (epoch <= now) return@forEachIndexed

            val intent = Intent(context, BeforeClassNotifyReceiver::class.java).apply {
                putExtra("courseName", course.courseName)
                putExtra("room", course.room)
                putExtra("startTime", String.format("%02d:%02d", h, m))
            }
            val pending = PendingIntent.getBroadcast(
                context, RC_BEFORE_CLASS_BASE + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Use exact alarm for precision, fall back to inexact on Android 12+ without grant
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, epoch, pending)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epoch, pending)
            }
        }
    }

    // ==================== Helpers ====================

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_DAILY,
            context.getString(R.string.notif_channel_daily),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.notif_channel_daily_desc) })
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_BEFORE_CLASS,
            context.getString(R.string.notif_channel_before_class),
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = context.getString(R.string.notif_channel_before_class_desc) })
    }

    private fun buildPendingIntInfo(rc: Int, cls: Class<*>): PendingIntent =
        PendingIntent.getBroadcast(
            context, rc, Intent(context, cls),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    @Suppress("UNCHECKED_CAST")
    private fun buildPendingIntent(rc: Int, cls: Class<out BroadcastReceiver>): PendingIntent =
        PendingIntent.getBroadcast(
            context, rc, Intent(context, cls),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun setRepeatingAlarm(am: AlarmManager, epoch: Long, interval: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, epoch, interval, pi)
        } else {
            am.setRepeating(AlarmManager.RTC_WAKEUP, epoch, interval, pi)
        }
    }

    private suspend fun resolveCurrentTable(): TimeTableEntity? {
        return com.lingion.sleepy.widget.WidgetTableResolver.resolveCurrentTable()
    }
}

// ==================== Receivers ====================

/**
 * Daily summary notification — fires at user-chosen time.
 * Content: "今日{X}号 您有{N}节课 第一节课{courseName}于{HH}时{MM}分在{room}上课"
 */
class DailyNotifyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!hasNotifPermission(context)) return
        if (!AppPrefs.isReminderEnabled(context) || !AppPrefs.isDailyReminderEnabled(context)) return

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            sendDailyNotification(context)
        }
    }

    private suspend fun sendDailyNotification(context: Context) {
        val today = LocalDate.now()
        val dow = DateUtils.todayDayOfWeek(today)

        val table = com.lingion.sleepy.widget.WidgetTableResolver.resolveCurrentTable()
        val dayOfMonth = today.dayOfMonth

        val title: String
        val text: String

        if (table == null) {
            title = context.getString(R.string.notif_daily_title_no_course, dayOfMonth)
            text = context.getString(R.string.notif_daily_text_no_course)
        } else {
            val week = DateUtils.currentWeek(table.startDate, today)
            val courses = SleepyApp.get().repository
                .getCoursesByDayOnce(table.id, dow)
                .filter { it.inWeek(week) }
                .sortedBy { it.startNode }

            if (courses.isEmpty()) {
                title = context.getString(R.string.notif_daily_title_no_course, dayOfMonth)
                text = context.getString(R.string.notif_daily_text_no_course)
            } else {
                title = context.getString(R.string.notif_daily_title, dayOfMonth, courses.size)

                // Build first course info
                val first = courses.first()
                val firstTime = getCourseStartTime(first, table)
                val firstRoom = first.room.ifBlank { context.getString(R.string.notif_room_unknown) }
                text = context.getString(R.string.notif_daily_text_first,
                    first.courseName, firstTime, firstRoom)
            }
        }

        val notif = NotificationCompat.Builder(context, CourseNotificationScheduler.CHANNEL_DAILY)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(CourseNotificationScheduler.NOTIFY_DAILY, notif)
    }
}

/**
 * Midnight scheduler — sets up individual before-class alarms for the day.
 */
class BeforeClassScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!AppPrefs.isReminderEnabled(context) || !AppPrefs.isBeforeClassEnabled(context)) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            CourseNotificationScheduler(context.applicationContext).scheduleTodayBeforeClassAlarms()
        }
    }
}

/**
 * Individual before-class notification — fires N minutes before a class.
 * Content: "下节课{courseName}于{HH}:{MM}在{room}上课"
 */
class BeforeClassNotifyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!hasNotifPermission(context)) return
        if (!AppPrefs.isReminderEnabled(context) || !AppPrefs.isBeforeClassEnabled(context)) return

        val courseName = intent.getStringExtra("courseName") ?: return
        val room = intent.getStringExtra("room") ?: ""
        val startTime = intent.getStringExtra("startTime") ?: ""
        val roomStr = room.ifBlank { context.getString(R.string.notif_room_unknown) }

        val text = context.getString(R.string.notif_before_class_text, courseName, startTime, roomStr)

        val notif = NotificationCompat.Builder(context, CourseNotificationScheduler.CHANNEL_BEFORE_CLASS)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle(context.getString(R.string.notif_before_class_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE, notif)
    }
}

/**
 * Boot receiver — reschedules everything after reboot or app update.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED
            || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (AppPrefs.isReminderEnabled(context)) {
                SleepyApp.get().notificationScheduler.scheduleAll()
            }
        }
    }
}

// ==================== Shared helpers ====================

private fun hasNotifPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun openAppIntent(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

private fun getCourseStartTime(course: CourseEntity, table: TimeTableEntity): String {
    if (course.ownTime && course.startTime.isNotBlank()) return course.startTime
    val nodes = TimeTableUtils.parseNodes(table.timeJson)
    val node = nodes.find { it.node == course.startNode } ?: return ""
    return String.format("%02d:%02d", node.start.hour, node.start.minute)
}
