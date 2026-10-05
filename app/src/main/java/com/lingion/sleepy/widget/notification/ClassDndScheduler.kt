package com.lingion.sleepy.widget.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
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
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 上课自动勿扰调度器 (参照 shiguang DndSchedulerWorker)。
 *
 * 契约:
 *  - 枚举未来 [WINDOW_DAYS] 天的课程区间, 只排"下一次上课开始"与
 *    "下一次下课结束"两颗配对闹钟 (-- 各自 cancel+重排 → 幂等)。
 *  - 每次闹钟触发 ([ClassDndReceiver]) 先校准当下进入/离开状态,
 *    再自续重排 — 即使某颗闹钟丢失, 下一颗触发也会恢复正确状态,
 *    不会永久滞留勿扰。
 *  - 勿扰切换需要系统"通知策略访问"权限; 未授权时只跳过切换、
 *    闹钟链不断 (用户随时可在系统页授权)。
 *
 * 边界计算是纯函数 (入参 intervals/nowMs), JVM 可测, 不依赖 Android。
 */
class ClassDndScheduler(private val context: Context) {

    /** 下一次课程区间边界; null = 窗口内无未来边界。 */
    data class Boundaries(val nextStart: LocalDateTime?, val nextEnd: LocalDateTime?)

    companion object {
        const val RC_START = 7701
        const val RC_END = 7702
        internal const val WINDOW_DAYS = 7L

        /** 当前时刻是否处于某节课区间 [start, end) 内。 */
        internal fun isCurrentlyInClass(
            intervals: List<Pair<LocalDateTime, LocalDateTime>>,
            now: LocalDateTime,
        ): Boolean = intervals.any { (s, e) -> !now.isBefore(s) && now.isBefore(e) }

        /** 未来边界: 只取 still-future 的最早开始/最早结束。 */
        internal fun nextBoundaries(
            intervals: List<Pair<LocalDateTime, LocalDateTime>>,
            now: LocalDateTime,
        ): Boundaries = Boundaries(
            nextStart = intervals.map { it.first }.filter { it.isAfter(now) }.minOrNull(),
            nextEnd = intervals.map { it.second }.filter { it.isAfter(now) }.minOrNull(),
        )

        /** 单门课 → (start, end) 区间; 时间不可解析/非法 → null 跳过。 */
        internal fun courseInterval(
            date: LocalDate,
            course: CourseEntity,
            nodes: List<TimeTableUtils.NodeTime>,
        ): Pair<LocalDateTime, LocalDateTime>? {
            val startStr = if (course.ownTime && course.startTime.isNotBlank()) course.startTime
                else nodes.find { it.node == course.startNode }
                    ?.let { String.format("%02d:%02d", it.start.hour, it.start.minute) }
                ?: return null
            val endNode = course.startNode + course.step - 1
            val endStr = if (course.ownTime && course.endTime.isNotBlank()) course.endTime
                else nodes.find { it.node == endNode }
                    ?.let { String.format("%02d:%02d", it.end.hour, it.end.minute) }
                ?: return null
            val sh = startStr.split(":").getOrNull(0)?.toIntOrNull() ?: return null
            val sm = startStr.split(":").getOrNull(1)?.toIntOrNull() ?: return null
            val eh = endStr.split(":").getOrNull(0)?.toIntOrNull() ?: return null
            val em = endStr.split(":").getOrNull(1)?.toIntOrNull() ?: return null
            if (sh !in 0..23 || sm !in 0..59 || eh !in 0..23 || em !in 0..59) return null
            val start = date.atTime(sh, sm)
            val end = date.atTime(eh, em)
            if (!end.isAfter(start)) return null // 跨天课罕见, 保守跳过
            return start to end
        }
    }

    /** 开关关 / 无课表 → 直接返回; 否则校准当下状态后排下一对边界闹钟。 */
    suspend fun rebuildAlarms() {
        val table = com.lingion.sleepy.widget.WidgetTableResolver.resolveCurrentTable()
            ?: return
        val intervals = buildIntervals(table)
        val now = LocalDateTime.now()
        // 状态校准: 已在课中但闹钟链断了(如刚开开关) → 立即进入勿扰
        if (isCurrentlyInClass(intervals, now)) applyDnd(enter = true)
        val b = nextBoundaries(intervals, now)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        b.nextStart?.let { setExact(am, RC_START, it) } ?: cancelSlot(am, RC_START)
        b.nextEnd?.let { setExact(am, RC_END, it) } ?: cancelSlot(am, RC_END)
    }

    /** 开关入口 (ReminderScreen/BootReceiver 调): 开 → 重排; 关 → 取消并恢复。 */
    fun syncFromPrefs() {
        if (AppPrefs.isClassDndEnabled(context)) {
            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
            ).launch { rebuildAlarms() }
        } else {
            disableAndRestore()
        }
    }

    /** 关闭开关: 取消闹钟并恢复勿扰现场。 */
    fun disableAndRestore() {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelSlot(am, RC_START)
        cancelSlot(am, RC_END)
        applyDnd(enter = false)
    }

    private suspend fun buildIntervals(table: TimeTableEntity): List<Pair<LocalDateTime, LocalDateTime>> {
        val today = LocalDate.now()
        val nodes = TimeTableUtils.parseNodes(table.timeJson)
        return (0 until WINDOW_DAYS).flatMap { offsetDays ->
            val date = today.plusDays(offsetDays)
            if (DateUtils.semesterStatus(table.startDate, table.maxWeek, date)
                != DateUtils.SemesterStatus.IN_RANGE
            ) return@flatMap emptyList()
            val week = DateUtils.currentWeek(table.startDate, date)
            val dow = com.lingion.sleepy.widget.HolidayTransferHelper
                .effectiveDayOfWeek(context, table.id, date)
            com.lingion.sleepy.SleepyApp.get().repository
                .getCoursesByDayOnce(table.id, dow)
                .filter { it.inWeek(week) }
                .mapNotNull { courseInterval(date, it, nodes) }
        }
    }

    private fun setExact(am: AlarmManager, rc: Int, at: LocalDateTime) {
        val pi = PendingIntent.getBroadcast(
            context, rc, Intent(context, ClassDndReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val epoch = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epoch, pi)
        } catch (_: SecurityException) {
            // 无精确闹钟权限 → 非精确兜底, 勿扰晚到但不丢
            am.set(AlarmManager.RTC_WAKEUP, epoch, pi)
        }
    }

    private fun cancelSlot(am: AlarmManager, rc: Int) {
        val pi = PendingIntent.getBroadcast(
            context, rc, Intent(context, ClassDndReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pi?.let { am.cancel(it) }
    }

    /**
     * 勿扰切换; 需"通知策略访问"权限, 未授权静默跳过。
     * 用户手动改过勿扰 (filter 非预期值) 时进入/恢复都跳过, 不跟人抢方向盘。
     */
    fun applyDnd(enter: Boolean) {
        val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        if (!nm.isNotificationPolicyAccessGranted) return
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (enter) {
            if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return // 用户已手动静音/振动
            nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_NONE)
        } else {
            nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }
}

/**
 * 勿扰边界闹钟接收器 — 先校准当下状态, 再自续重排。
 * requestCode 区分语义仅用于日志; 实际动作由"现在还在校区内吗"决定。
 */
class ClassDndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!AppPrefs.isClassDndEnabled(context)) return
        // 校准+重排查库 — 挪出主线程防 ANR, 同 BeforeClassScheduleReceiver 模式
        val app = context.applicationContext
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
        ).launch {
            ClassDndScheduler(app).rebuildAlarms()
        }
    }
}
