package com.lingion.sleepy.widget.notification

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
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
import kotlinx.coroutines.withContext
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
        const val CHANNEL_FLUID = "sleepy_fluid_v2"

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
        // ★ 整段放入 IO 协程：cancelAll 现为 suspend，需在协程内先取消再重排，
        //   保证「先取消后重排」的顺序不被打散（避免取消与重排的竞态），
        //   同时把查库挪出主线程，消除 runBlocking 导致的 ANR 风险。
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            cancelAll()

            val prefs = context.applicationContext
            if (!AppPrefs.isReminderEnabled(prefs)) return@launch

            if (AppPrefs.isDailyReminderEnabled(prefs)) {
                scheduleDaily()
            }
            if (AppPrefs.isBeforeClassEnabled(prefs)) {
                scheduleBeforeClassDaily()
                // ★ 状态兜底：排 alarm 的同时立即检测是否已在某节课窗口内（补起流体云）
                ensureActiveFluidCloud()
            }
        }
    }

    suspend fun cancelAll() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Cancel daily
        alarmManager.cancel(buildPendingIntent(RC_DAILY, DailyNotifyReceiver::class.java))

        // Cancel before-class scheduler
        alarmManager.cancel(buildPendingIntent(RC_BEFORE_CLASS_SCHEDULER, BeforeClassScheduleReceiver::class.java))

        // 课前提醒的 request code 用 RC_BEFORE_CLASS_BASE + course.id（稳定唯一）。
        // 取消时遍历数据库里所有课程 id，逐个 cancel，不再依赖写死的 50 上限。
        // ★ 改为 suspend + withContext(IO) 查库，不再在主线程 runBlocking 阻塞导致 ANR。
        val courseIds = withContext(Dispatchers.IO) {
            runCatching {
                SleepyApp.get().repository.let { repo ->
                    repo.getAllTables().flatMap { repo.getCourses(it.id) }
                }.map { it.id.toInt() }
            }.getOrDefault(emptyList())
        }
        for (cid in courseIds) {
            try {
                alarmManager.cancel(buildPendingIntent(RC_BEFORE_CLASS_BASE + cid, BeforeClassNotifyReceiver::class.java))
            } catch (_: Exception) {}
        }
    }

    // ==================== Daily ====================

    private fun scheduleDaily() {
        val timeStr = AppPrefs.getDailyReminderTime(context)
        val parts = timeStr.split(":")
        // 钳制到合法范围，避免破损 pref（"07:60"、负数、空值）触发 DateTimeException 崩溃
        val hour = (parts.getOrNull(0)?.toIntOrNull() ?: 7).coerceIn(0, 23)
        val minute = (parts.getOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, 59)

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
        android.util.Log.d("CourseScheduler", "scheduleToday start enabled=${AppPrefs.isBeforeClassEnabled(app)} minutes=${AppPrefs.getBeforeClassMinutes(app)}")
        if (!AppPrefs.isBeforeClassEnabled(app)) return
        val minutes = AppPrefs.getBeforeClassMinutes(app)
        val today = LocalDate.now()
        val dow = DateUtils.todayDayOfWeek(today)

        val table = resolveCurrentTable()
        android.util.Log.d("CourseScheduler", "table=${table?.id}:${table?.name} start=${table?.startDate} today=$today dow=$dow")
        if (table == null) return
        val week = DateUtils.currentWeek(table.startDate, today)
        val allCourses = SleepyApp.get().repository.getCoursesByDayOnce(table.id, dow)
        val courses = allCourses.filter { it.inWeek(week) }
        android.util.Log.d("CourseScheduler", "week=$week coursesAll=${allCourses.size} coursesInWeek=${courses.size}")

        // Parse time nodes
        val nodes = TimeTableUtils.parseNodes(table.timeJson)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()

        courses.forEachIndexed { index, course ->
            // Get course start time
            android.util.Log.d("CourseScheduler", "course index=$index id=${course.id} name=${course.courseName} ownTime=${course.ownTime} start=${course.startTime} node=${course.startNode}")
            val startTimeStr = if (course.ownTime && course.startTime.isNotBlank()) {
                course.startTime
            } else {
                nodes.find { it.node == course.startNode }?.let { String.format("%02d:%02d", it.start.hour, it.start.minute) }
            } ?: run {
                android.util.Log.w("CourseScheduler", "skip no start time course=${course.id}")
                return@forEachIndexed
            }
            val parts = startTimeStr.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull()
            val m = parts.getOrNull(1)?.toIntOrNull()
            // 钳制：ownTime/startTime 可能是破损值（h≥24/m≥60），非法则跳过本节
            if (h == null || m == null || h !in 0..23 || m !in 0..59) {
                android.util.Log.w("CourseScheduler", "skip invalid time course=${course.id} time=$startTimeStr")
                return@forEachIndexed
            }

            val classStart = today.atTime(h, m)
            val notifyTime = classStart.minusMinutes(minutes.toLong())
            val epoch = notifyTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            android.util.Log.d("CourseScheduler", "course=${course.id} start=$classStart notify=$notifyTime epoch=$epoch now=$now")
            if (epoch <= now) {
                android.util.Log.d("CourseScheduler", "skip past alarm course=${course.id}")
                return@forEachIndexed
            }

            val intent = Intent(context, BeforeClassNotifyReceiver::class.java).apply {
                putExtra("courseName", course.courseName)
                putExtra("room", course.room)
                putExtra("teacher", course.teacher)
                putExtra("startTime", String.format("%02d:%02d", h, m))
                putExtra("notifyEpoch", epoch)
                putExtra("classEpoch", classStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            }
            val pending = PendingIntent.getBroadcast(
                context, RC_BEFORE_CLASS_BASE + course.id.toInt(),
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

    /**
     * ★ 状态驱动的流体云兜底：只要"现在"落在任一节课的 [classStart-minutes, classStart] 窗口内，
     * 就确保 FluidCloudService 在跑、流体云在显示。不依赖"正好提前N分钟那一秒"的 alarm。
     *
     * 调用时机：app 回前台、app 启动、课程数据变更、WorkManager 周期兜底。
     * 解决"alarm 错过那一秒 / 用户在窗口内才打开 app → 流体云永远不起"的问题。
     */
    suspend fun ensureActiveFluidCloud() {
        val app = context.applicationContext
        if (!AppPrefs.isReminderEnabled(app) || !AppPrefs.isBeforeClassEnabled(app)) return
        if (!AppPrefs.isBeforeClassFluidEnabled(app)) return
        val minutes = AppPrefs.getBeforeClassMinutes(app)
        val today = LocalDate.now()
        val dow = DateUtils.todayDayOfWeek(today)
        val table = resolveCurrentTable() ?: return
        val week = DateUtils.currentWeek(table.startDate, today)
        val nodes = TimeTableUtils.parseNodes(table.timeJson)
        val now = System.currentTimeMillis()

        // 找出现在处于课前窗口内的第一节课
        val hit = SleepyApp.get().repository.getCoursesByDayOnce(table.id, dow)
            .filter { it.inWeek(week) }
            .firstOrNull { c ->
                val st = if (c.ownTime && c.startTime.isNotBlank()) c.startTime
                    else nodes.find { it.node == c.startNode }?.let { String.format("%02d:%02d", it.start.hour, it.start.minute) }
                val p = st?.split(":")
                val h = p?.getOrNull(0)?.toIntOrNull(); val m = p?.getOrNull(1)?.toIntOrNull()
                if (h == null || m == null || h !in 0..23 || m !in 0..59) return@firstOrNull false
                val classStart = today.atTime(h, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val notifyEpoch = classStart - minutes * 60_000L
                now in notifyEpoch..classStart  // 现在在窗口内
            } ?: return

        // 计算这节课的精确窗口，启动 FluidCloudService
        val st = if (hit.ownTime && hit.startTime.isNotBlank()) hit.startTime
            else nodes.find { it.node == hit.startNode }!!.let { String.format("%02d:%02d", it.start.hour, it.start.minute) }
        val p = st.split(":")
        val classStart = today.atTime(p[0].toInt(), p[1].toInt()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val notifyEpoch = classStart - minutes * 60_000L
        val svc = Intent(app, FluidCloudService::class.java).apply {
            putExtra("courseName", hit.courseName)
            putExtra("room", hit.room.ifBlank { app.getString(R.string.default_room) })
            putExtra("teacher", hit.teacher)
            putExtra("startTime", st)
            putExtra("notifyEpoch", notifyEpoch)
            putExtra("classEpoch", classStart)
        }
        try {
            androidx.core.content.ContextCompat.startForegroundService(app, svc)
            android.util.Log.d("CourseScheduler", "ensureActiveFluidCloud: started for ${hit.courseName} notify=$notifyEpoch class=$classStart now=$now")
        } catch (t: Throwable) {
            android.util.Log.w("CourseScheduler", "ensureActiveFluidCloud start failed", t)
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
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_FLUID,
            context.getString(R.string.notif_channel_fluid),
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = context.getString(R.string.notif_channel_fluid_desc) })
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
            .setSmallIcon(R.drawable.ic_notification_time)
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
        android.util.Log.d("BeforeClassNotify", "entered extras=${intent.extras?.keySet()}")
        if (!hasNotifPermission(context)) {
            android.util.Log.w("BeforeClassNotify", "POST_NOTIFICATIONS denied")
            return
        }
        if (!AppPrefs.isReminderEnabled(context) || !AppPrefs.isBeforeClassEnabled(context)) {
            android.util.Log.w("BeforeClassNotify", "reminder toggles disabled")
            return
        }

        val courseName = intent.getStringExtra("courseName") ?: return
        val room = intent.getStringExtra("room") ?: ""
        val startTime = intent.getStringExtra("startTime") ?: ""
        val roomStr = room.ifBlank { context.getString(R.string.notif_room_unknown) }
        val teacher = intent.getStringExtra("teacher") ?: ""
        val fluid = intent.getBooleanExtra("debug_force_fluid", false) || AppPrefs.isBeforeClassFluidEnabled(context)
        val banner = AppPrefs.isBeforeClassBannerEnabled(context)
        if (!banner && !fluid) return
        val fields = AppPrefs.getBeforeClassFluidFields(context)
        val fluidText = buildList {
            if ("name" in fields) add(courseName)
            if ("time" in fields) add(startTime)
            if ("room" in fields && room.isNotBlank()) add(roomStr)
            if ("teacher" in fields && teacher.isNotBlank()) add(teacher)
        }.ifEmpty { listOf(courseName) }.joinToString("  ·  ")
        val primary = AppPrefs.getBeforeClassFluidPrimary(context)
        val primaryText = when (primary) {
            "name" -> courseName
            "time" -> startTime
            else -> roomStr
        }
        val roomTeacherText = buildList {
            if (room.isNotBlank()) add(roomStr)
            if (teacher.isNotBlank()) add(teacher)
        }.ifEmpty { listOf(roomStr) }.joinToString("  ·  ")
        val timeTeacherText = buildList {
            if (startTime.isNotBlank()) add(startTime)
            if (teacher.isNotBlank()) add(teacher)
        }.ifEmpty { listOf(startTime.ifBlank { context.getString(R.string.notif_before_class_title) }) }.joinToString("  ·  ")

        val text = if (teacher.isBlank()) {
            context.getString(R.string.notif_before_class_text, courseName, startTime, roomStr)
        } else {
            context.getString(R.string.notif_before_class_text_with_teacher, courseName, startTime, roomStr, teacher)
        }

        // == 流体云 / Live Update ==
        // ★ 所有 SDK>=26 统一走 FluidCloudService：service 的 Handler 每 15s 循环
        //   re-post ProgressStyle 通知推进 progress，进度条才会持续动。
        //   旧代码在 SDK>=36 单独静态 post 一次就 return，导致进度条停在 0 不更新。

        // FluidCloudService 接管流体云：前台服务每 15s 循环 re-post ProgressStyle 通知推进进度条。
        // SDK>=26（含 Android 16）统一走此路径。
        if (fluid && Build.VERSION.SDK_INT >= 26) {
            val serviceIntent = Intent(context, FluidCloudService::class.java).apply {
                putExtra("courseName", courseName)
                putExtra("room", roomStr)
                putExtra("teacher", teacher)
                putExtra("startTime", startTime)
                putExtra("notifyEpoch", intent.getLongExtra("notifyEpoch", System.currentTimeMillis()))
                putExtra("classEpoch", intent.getLongExtra("classEpoch", System.currentTimeMillis()))
            }
            ContextCompat.startForegroundService(context, serviceIntent)
            return
        }

        // == Fallback: standard notification ==
        val notif = NotificationCompat.Builder(context, if (fluid) CourseNotificationScheduler.CHANNEL_FLUID else CourseNotificationScheduler.CHANNEL_BEFORE_CLASS)
            .setSmallIcon(R.drawable.ic_notification_time)
            .setContentTitle(if (fluid) courseName else context.getString(R.string.notif_before_class_title))
            .setContentText(if (fluid) fluidText else text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (fluid) fluidText else text))
            .setTicker(if (fluid) fluidText else text)
            .setSubText(if (fluid) fluidText else null)
            .setOngoing(fluid)
            .setOnlyAlertOnce(false)
            .setPriority(if (fluid) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
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
