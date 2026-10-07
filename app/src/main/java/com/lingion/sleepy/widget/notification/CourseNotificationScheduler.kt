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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 课程通知调度器 — 支持每日提醒 + 每节课前提醒。
 *
 * 每日提醒：在用户指定时间发送今日课程摘要。
 * 课前提醒：预排未来 7 天(含今天)每节课前 N 分钟的精确闹钟；
 *          每天 00:05 全窗口重同步(撤销不再需要的槽位、覆盖仍需要的, 幂等)。
 *
 * 可测性: 闹钟落地/数据读取/环境值全部走注入接口
 * ([BeforeClassAlarmPort]/[BeforeClassDataSource]/[BeforeClassEnv]),
 * JVM 单测用 internal 构造器注入 fake, 不需要 Android 运行时。
 */
class CourseNotificationScheduler private constructor(
    private val rawContext: Context?,
    private val alarmPort: BeforeClassAlarmPort,
    private val env: BeforeClassEnv,
    private val dataSource: BeforeClassDataSource
) {

    /** 生产构造器 — 外部调用方(SleepyApp/各 Receiver)保持 `CourseNotificationScheduler(context)` 不变。 */
    constructor(context: Context) : this(
        context.applicationContext,
        AndroidBeforeClassAlarmPort(context.applicationContext),
        AndroidBeforeClassEnv(context.applicationContext),
        AndroidBeforeClassDataSource(context.applicationContext)
    )

    /** JVM 单测构造器 — 注入 fake 闹钟端口/环境/数据源; 只有课前闹钟链路函数可用。 */
    internal constructor(
        alarmPort: BeforeClassAlarmPort,
        env: BeforeClassEnv,
        dataSource: BeforeClassDataSource
    ) : this(null, alarmPort, env, dataSource)

    private val context: Context
        get() = requireNotNull(rawContext) {
            "CourseNotificationScheduler: 测试构造器未注入 Context, 不可调用依赖 Android 运行时的方法"
        }

    companion object {
        const val CHANNEL_DAILY = "sleepy_daily"
        const val CHANNEL_BEFORE_CLASS = "sleepy_before_class"
        const val CHANNEL_FLUID = "sleepy_fluid_v2"

        // Request codes for PendingIntent discrimination
        private const val RC_DAILY = 1
        private const val RC_BEFORE_CLASS_SCHEDULER = 2
        private const val RC_TOMORROW_DAILY = 3
        private const val RC_BEFORE_CLASS_BASE = 100 // + courseId offset

        /** 课前闹钟预排窗口: 今天起 7 个日历天(参照 shiguang WIDGET_SYNC_DAYS=7) */
        internal const val BEFORE_CLASS_WINDOW_DAYS = 7

        // Notification IDs
        const val NOTIFY_DAILY = 1001
        const val NOTIFY_TOMORROW_DAILY = 1002
        const val NOTIFY_BEFORE_CLASS_BASE = 2000 // + courseId offset

        // 进程级: 各 Receiver 会 new 自己的实例, 锁必须跨实例; Mutex 不可重入, 持锁路径只调 *Locked
        private val scheduleLock = Mutex()
    }

    fun scheduleAll() {
        createChannels()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val prefs = context.applicationContext
            val beforeClassOn = scheduleLock.withLock {
                cancelRecurringLocked()
                if (!AppPrefs.isReminderEnabled(prefs)) {
                    cancelBeforeClassLocked()
                    return@withLock false
                }

                if (AppPrefs.isDailyReminderEnabled(prefs)) {
                    if (AppPrefs.isTodayReminderEnabled(prefs)) {
                        scheduleDaily()
                    }
                    if (AppPrefs.isTomorrowReminderEnabled(prefs)) {
                        scheduleTomorrowReminder()
                    }
                }
                if (!AppPrefs.isBeforeClassEnabled(prefs)) {
                    cancelBeforeClassLocked()
                    return@withLock false
                }
                scheduleBeforeClassDaily()
                resyncBeforeClassLocked()
                true
            }
            // 状态兜底：排 alarm 的同时立即检测是否已在某节课窗口内（补起流体云）
            if (beforeClassOn) ensureActiveFluidCloud()
        }
    }

    suspend fun cancelAll() = scheduleLock.withLock { cancelAllLocked() }

    private suspend fun cancelAllLocked() {
        cancelRecurringLocked()
        cancelBeforeClassLocked()
    }

    private fun cancelRecurringLocked() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent(RC_DAILY, DailyNotifyReceiver::class.java))
        alarmManager.cancel(buildPendingIntent(RC_TOMORROW_DAILY, TomorrowNotifyReceiver::class.java))
    }

    private suspend fun cancelBeforeClassLocked() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent(RC_BEFORE_CLASS_SCHEDULER, BeforeClassScheduleReceiver::class.java))
        cancelBeforeClassCourseAlarmsLocked()
    }

    private suspend fun cancelBeforeClassCourseAlarmsLocked() {
        // 账本覆盖已删课程/已换掉的课表; 现存课程 id 覆盖账本上线前旧版本排下的闹钟
        val courseIds = runCatching { dataSource.allCourseIds() }.getOrDefault(emptyList())
        (env.loadArmedCodes() + courseIds.map { RC_BEFORE_CLASS_BASE + it.toInt() })
            .forEach { alarmPort.cancel(it) }
        env.saveArmedCodes(emptySet())
    }

    /**
     * 取消指定课程 id 的课前闹钟（PendingIntent 语义：extras 不参与匹配）。
     * 调用方：ScheduleRepository.deleteTable —— 删表靠外键 CASCADE 级联删课程，
     * 删除后这些课程 id 已查不到，cancelAll 的"现存课程"枚举覆盖不到，
     * 故删除前捕获 id 列表、删除后调这里显式清理孤儿闹钟。
     */
    fun cancelCourseAlarms(courseIds: List<Long>) {
        courseIds.forEach { alarmPort.cancel(RC_BEFORE_CLASS_BASE + it.toInt()) }
    }

    // ==================== Daily ====================

    private fun scheduleDaily() {
        scheduleDailyAlarm(
            timeStr = AppPrefs.getDailyReminderTime(context),
            fallbackHour = 7,
            fallbackMinute = 0,
            pending = buildPendingIntent(RC_DAILY, DailyNotifyReceiver::class.java)
        )
    }

    private fun scheduleTomorrowReminder() {
        scheduleDailyAlarm(
            timeStr = AppPrefs.getTomorrowReminderTime(context),
            fallbackHour = 22,
            fallbackMinute = 0,
            pending = buildPendingIntent(RC_TOMORROW_DAILY, TomorrowNotifyReceiver::class.java)
        )
    }

    private fun scheduleDailyAlarm(
        timeStr: String,
        fallbackHour: Int,
        fallbackMinute: Int,
        pending: PendingIntent
    ) {
        val parts = timeStr.split(":")
        // 钳制到合法范围，避免破损 pref（"07:60"、负数、空值）触发 DateTimeException 崩溃
        val hour = (parts.getOrNull(0)?.toIntOrNull() ?: fallbackHour).coerceIn(0, 23)
        val minute = (parts.getOrNull(1)?.toIntOrNull() ?: fallbackMinute).coerceIn(0, 59)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val target = LocalTime.of(hour, minute)
        var next = LocalDate.now().atTime(target)
        if (!LocalTime.now().isBefore(target)) next = next.plusDays(1)
        val epoch = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        setRepeatingAlarm(alarmManager, epoch, AlarmManager.INTERVAL_DAY, pending)
    }

    // ==================== Before-class scheduler ====================

    private fun scheduleBeforeClassDaily() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = buildPendingIntent(RC_BEFORE_CLASS_SCHEDULER, BeforeClassScheduleReceiver::class.java)

        // Schedule at 00:05 every day — 每日全窗口重同步(幂等)
        val target = LocalTime.of(0, 5)
        var next = LocalDate.now().atTime(target)
        if (LocalTime.now().isAfter(target)) next = next.plusDays(1)
        val epoch = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        setRepeatingAlarm(alarmManager, epoch, AlarmManager.INTERVAL_DAY, pending)
    }

    /**
     * 兼容入口 — 语义已升级为「未来 7 天全窗口重同步」(幂等)。
     * 保留供 [BeforeClassScheduleReceiver](每日 00:05)与 debug 接收器调用。
     */
    suspend fun scheduleTodayBeforeClassAlarms() = scheduleNext7DaysExactAlarms()

    /**
     * 预排未来 7 个日历天(含今天)的课前精确闹钟, 幂等重同步。
     *
     * requestCode = RC_BEFORE_CLASS_BASE + course.id, 同 code 重复 set 由 AlarmManager 原地替换,
     * 所以只需取消"上次排过、这次不再需要"的 code。上次排过的集合持久化在账本里
     * (进程被杀也不丢), 换课表/删课/改周次/关开关后旧闹钟都能被精确撤销。
     * 学期范围外的天跳过(防呆: currentWeek 钳制会误匹配第 1 周的课)。
     */
    suspend fun scheduleNext7DaysExactAlarms() = scheduleLock.withLock { resyncBeforeClassLocked() }

    private suspend fun resyncBeforeClassLocked() {
        val previous = env.loadArmedCodes()
        val table = if (env.isBeforeClassEnabled()) dataSource.resolveCurrentTable() else null
        if (table == null) {
            previous.forEach { alarmPort.cancel(it) }
            env.saveArmedCodes(emptySet())
            return
        }
        val minutes = env.beforeClassMinutes()
        val nowEpoch = env.nowEpochMs()
        val today = env.todayDate()
        val nodes = TimeTableUtils.parseNodes(table.timeJson)

        // 1) 枚举窗口内的天, 收集课程行(仅学期内的天)
        val days = (0 until BEFORE_CLASS_WINDOW_DAYS).map { offset ->
            val date = today.plusDays(offset.toLong())
            if (DateUtils.semesterStatus(table.startDate, table.maxWeek, date)
                    != DateUtils.SemesterStatus.IN_RANGE
            ) {
                return@map date to emptyList()
            }
            val week = DateUtils.currentWeek(table.startDate, date)
            val dow = dataSource.effectiveDayOfWeek(table.id, date)
            date to dataSource.coursesForDay(table.id, dow).filter { it.inWeek(week) }
        }

        // 2) 逐天收集未来槽位; 同 courseId 保留最早的未来触发
        //    (每周重复课/调休映射共享 requestCode, 天按时间顺序遍历, 先到先得 = 最早一次)
        val desired = LinkedHashMap<Int, Pair<Long, Map<String, Any?>>>()
        days.forEach { (date, courses) ->
            courses.forEach { course ->
                val startStr = if (course.ownTime && course.startTime.isNotBlank()) {
                    course.startTime
                } else {
                    nodes.find { it.node == course.startNode }
                        ?.let { String.format("%02d:%02d", it.start.hour, it.start.minute) }
                } ?: return@forEach
                val parts = startStr.split(":")
                val h = parts.getOrNull(0)?.toIntOrNull()
                val m = parts.getOrNull(1)?.toIntOrNull()
                // 钳制：ownTime/startTime 可能是破损值（h≥24/m≥60），非法则跳过本节
                if (h == null || m == null || h !in 0..23 || m !in 0..59) return@forEach

                val classStart = date.atTime(h, m)
                val notifyTime = classStart.minusMinutes(minutes.toLong())
                val epoch = notifyTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (epoch <= nowEpoch) return@forEach // 已过触发点(或恰好到点, 由当日接收器兜底)

                val rc = RC_BEFORE_CLASS_BASE + course.id.toInt()
                if (rc in desired) return@forEach

                desired[rc] = epoch to mapOf(
                    "courseName" to course.courseName,
                    "room" to course.room,
                    "teacher" to course.teacher,
                    "startTime" to String.format("%02d:%02d", h, m),
                    "endTime" to (if (course.ownTime && course.endTime.isNotBlank()) {
                        course.endTime
                    } else {
                        nodes.find { it.node == course.startNode + course.step - 1 }
                            ?.let { String.format("%02d:%02d", it.end.hour, it.end.minute) }
                    }.orEmpty()),
                    "startNode" to course.startNode,
                    "notifyEpoch" to epoch,
                    "classEpoch" to classStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                )
            }
        }

        // 3) 撤销不再需要的: 账本里的 + 窗口内出现过的(覆盖账本上线前旧版本排下的)
        (previous + days.flatMap { it.second }.map { RC_BEFORE_CLASS_BASE + it.id.toInt() })
            .filterNot { it in desired }
            .forEach { alarmPort.cancel(it) }
        desired.forEach { (rc, slot) -> alarmPort.setExact(rc, slot.first, slot.second) }
        env.saveArmedCodes(desired.keys.toSet())
    }

    /**
     * 状态驱动的流体云兜底：只要"现在"落在任一节课的 [classStart-minutes, classStart] 窗口内，
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
        val table = resolveCurrentTable() ?: return
        val dow = com.lingion.sleepy.widget.HolidayTransferHelper.effectiveDayOfWeek(app, table.id, today)
        val week = DateUtils.currentWeek(table.startDate, today)
        // 防呆: 学期范围外不触发流体云(钳制周数会误匹配第 1 周的课)
        if (DateUtils.semesterStatus(table.startDate, table.maxWeek, today) != DateUtils.SemesterStatus.IN_RANGE) return
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
        val endTimeStr = if (hit.ownTime && hit.endTime.isNotBlank()) hit.endTime
            else nodes.find { it.node == hit.startNode + hit.step - 1 }
                ?.let { String.format("%02d:%02d", it.end.hour, it.end.minute) }.orEmpty()
        val svc = Intent(app, FluidCloudService::class.java).apply {
            putExtra("courseName", hit.courseName)
            putExtra("room", hit.room.ifBlank { app.getString(R.string.default_room) })
            putExtra("teacher", hit.teacher)
            putExtra("startTime", st)
            putExtra("endTime", endTimeStr)
            putExtra("startNode", hit.startNode)
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

    // buildPendingIntInfo 死函数已删（实际全部走下方 buildPendingIntent）

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

// ==================== 可注入端口(JVM 单测接缝) ====================

/** 课前闹钟环境值 — prefs/时钟读取接缝。 */
internal interface BeforeClassEnv {
    fun isBeforeClassEnabled(): Boolean
    fun beforeClassMinutes(): Int
    fun nowEpochMs(): Long
    fun todayDate(): LocalDate
    /** 上次重同步实际排下的 requestCode 账本 */
    fun loadArmedCodes(): Set<Int>
    fun saveArmedCodes(codes: Set<Int>)
}

/** 课前闹钟数据源 — 课表/课程查询接缝。 */
internal interface BeforeClassDataSource {
    suspend fun resolveCurrentTable(): TimeTableEntity?
    suspend fun coursesForDay(tableId: Long, dayOfWeek: Int): List<CourseEntity>
    suspend fun allCourseIds(): List<Long>
    fun effectiveDayOfWeek(tableId: Long?, date: LocalDate): Int
}

/** 课前闹钟落地端口 — AlarmManager/PendingIntent 接缝; extras 语义与旧 Intent extras 一致。 */
internal interface BeforeClassAlarmPort {
    fun setExact(requestCode: Int, epochMs: Long, extras: Map<String, Any?>)
    fun cancel(requestCode: Int)
}

internal class AndroidBeforeClassEnv(private val ctx: Context) : BeforeClassEnv {
    override fun isBeforeClassEnabled(): Boolean = AppPrefs.isBeforeClassEnabled(ctx)
    override fun beforeClassMinutes(): Int = AppPrefs.getBeforeClassMinutes(ctx)
    override fun nowEpochMs(): Long = System.currentTimeMillis()
    override fun todayDate(): LocalDate = LocalDate.now()

    // 独立文件: 闹钟是本机状态, 不能跟 sleepy_prefs.xml 一起被云备份/换机迁移带走
    private val ledger get() = ctx.getSharedPreferences(LEDGER_FILE, Context.MODE_PRIVATE)

    override fun loadArmedCodes(): Set<Int> =
        ledger.getStringSet(KEY_ARMED, null).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    override fun saveArmedCodes(codes: Set<Int>) {
        ledger.edit().putStringSet(KEY_ARMED, codes.map { it.toString() }.toSet()).apply()
    }

    private companion object {
        const val LEDGER_FILE = "sleepy_alarm_ledger"
        const val KEY_ARMED = "before_class_armed_codes"
    }
}

internal class AndroidBeforeClassDataSource(private val ctx: Context) : BeforeClassDataSource {
    override suspend fun resolveCurrentTable(): TimeTableEntity? =
        com.lingion.sleepy.widget.WidgetTableResolver.resolveCurrentTable()

    override suspend fun coursesForDay(tableId: Long, dayOfWeek: Int): List<CourseEntity> =
        SleepyApp.get().repository.getCoursesByDayOnce(tableId, dayOfWeek)

    override suspend fun allCourseIds(): List<Long> {
        val repo = SleepyApp.get().repository
        return repo.getAllTables().flatMap { repo.getCourses(it.id) }.map { it.id }
    }

    override fun effectiveDayOfWeek(tableId: Long?, date: LocalDate): Int =
        com.lingion.sleepy.widget.HolidayTransferHelper.effectiveDayOfWeek(ctx, tableId, date)
}

internal class AndroidBeforeClassAlarmPort(private val ctx: Context) : BeforeClassAlarmPort {
    private val alarmManager: AlarmManager
        get() = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun setExact(requestCode: Int, epochMs: Long, extras: Map<String, Any?>) {
        val intent = Intent(ctx, BeforeClassNotifyReceiver::class.java).apply {
            extras.forEach { (key, value) ->
                when (value) {
                    is String -> putExtra(key, value)
                    is Long -> putExtra(key, value)
                }
            }
        }
        val pending = PendingIntent.getBroadcast(
            ctx, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // 精确闹钟权限被收回: 降级非精确但仍穿透 Doze, 否则提前几天排的闹钟会被拖到亮屏才响
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMs, pending)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMs, pending)
        }
    }

    override fun cancel(requestCode: Int) {
        try {
            val pending = PendingIntent.getBroadcast(
                ctx, requestCode, Intent(ctx, BeforeClassNotifyReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pending?.let { alarmManager.cancel(it) }
        } catch (_: Exception) {}
    }
}

// ==================== Receivers ====================

/** Same-day schedule summary — fires at the user-chosen morning/daytime time. */
class DailyNotifyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!hasNotifPermission(context)) return
        if (!AppPrefs.isReminderEnabled(context) ||
            !AppPrefs.isDailyReminderEnabled(context) ||
            !AppPrefs.isTodayReminderEnabled(context)
        ) return

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            sendScheduleSummary(
                context = context,
                targetDate = LocalDate.now(),
                isTomorrowPreview = false
            )
        }
    }
}

/** Previous-evening schedule preview — follows the same no-course behavior as the same-day summary. */
class TomorrowNotifyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!hasNotifPermission(context)) return
        if (!AppPrefs.isReminderEnabled(context) ||
            !AppPrefs.isDailyReminderEnabled(context) ||
            !AppPrefs.isTomorrowReminderEnabled(context)
        ) return

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            sendScheduleSummary(
                context = context,
                targetDate = LocalDate.now().plusDays(1),
                isTomorrowPreview = true
            )
        }
    }
}

private suspend fun sendScheduleSummary(
    context: Context,
    targetDate: LocalDate,
    isTomorrowPreview: Boolean
) {
    val table = com.lingion.sleepy.widget.WidgetTableResolver.resolveCurrentTable()
    val dow = com.lingion.sleepy.widget.HolidayTransferHelper.effectiveDayOfWeek(
        context.applicationContext, table?.id, targetDate
    )
    val dayOfMonth = targetDate.dayOfMonth

    val courses = if (table == null) {
        emptyList()
    } else {
        val week = DateUtils.currentWeek(table.startDate, targetDate)
        val inSemester = DateUtils.semesterStatus(table.startDate, table.maxWeek, targetDate) ==
            DateUtils.SemesterStatus.IN_RANGE
        if (!inSemester) {
            emptyList()
        } else {
            SleepyApp.get().repository
                .getCoursesByDayOnce(table.id, dow)
                .filter { it.inWeek(week) }
                .sortedBy { it.startNode }
        }
    }

    val title: String
    val text: String
    if (courses.isEmpty()) {
        title = context.getString(
            if (isTomorrowPreview) R.string.notif_tomorrow_title_no_course else R.string.notif_daily_title_no_course,
            dayOfMonth
        )
        text = context.getString(
            if (isTomorrowPreview) R.string.notif_tomorrow_text_no_course
            else R.string.notif_daily_text_no_course
        )
    } else {
        title = context.getString(
            if (isTomorrowPreview) R.string.notif_tomorrow_title else R.string.notif_daily_title,
            dayOfMonth,
            courses.size
        )
        val first = courses.first()
        val firstTime = getCourseStartTime(first, requireNotNull(table))
        val firstRoom = first.room.ifBlank { context.getString(R.string.notif_room_unknown) }
        text = context.getString(R.string.notif_daily_text_first, first.courseName, firstTime, firstRoom)
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

    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        == PackageManager.PERMISSION_GRANTED
    ) {
        NotificationManagerCompat.from(context).notify(
            if (isTomorrowPreview) {
                CourseNotificationScheduler.NOTIFY_TOMORROW_DAILY
            } else {
                CourseNotificationScheduler.NOTIFY_DAILY
            },
            notif
        )
    }
}

/**
 * Midnight scheduler — sets up individual before-class alarms for the day.
 */
class BeforeClassScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!AppPrefs.isReminderEnabled(context) || !AppPrefs.isBeforeClassEnabled(context)) return
        // goAsync: onReceive 返回后缓存进程可能被立即冻结, 重同步协程会停在半路
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                CourseNotificationScheduler(context.applicationContext).scheduleTodayBeforeClassAlarms()
            } finally {
                pending.finish()
            }
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
        // primaryText / roomTeacherText / timeTeacherText 三个死变量已删
        // (计算后从未被使用——SDK>=26 路径直接交给 FluidCloudService, fallback 用上面的 fluidText/text)

        val text = if (teacher.isBlank()) {
            context.getString(R.string.notif_before_class_text, courseName, startTime, roomStr)
        } else {
            context.getString(R.string.notif_before_class_text_with_teacher, courseName, startTime, roomStr, teacher)
        }

        // == 流体云 / Live Update ==
        // 所有 SDK>=26 统一走 FluidCloudService：service 的 Handler 每 15s 循环
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
                putExtra("endTime", intent.getStringExtra("endTime").orEmpty())
                putExtra("startNode", intent.getIntExtra("startNode", 0))
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

        // Lint MissingPermission + 运行时兜底: 同 DailyNotifyReceiver,
        //   onReceive 校验后到此处之间权限可能被撤销 → 内联 checkSelfPermission 再查一次
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context)
                .notify(CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE, notif)
        }
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
            // 课程边界闹钟无条件重排 (设计 §5): 与通知开关无关, armNext 阻塞读库 → IO
            val appContext = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                com.lingion.sleepy.widget.WidgetBoundaryScheduler.armNext(appContext)
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
