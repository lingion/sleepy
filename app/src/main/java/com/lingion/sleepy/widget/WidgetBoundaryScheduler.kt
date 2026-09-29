package com.lingion.sleepy.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.util.Log
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.ConflictLayoutEngine
import com.lingion.sleepy.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 课程边界刷新调度器 (设计文稿 widget-redesign-2026-09 §5)。
 *
 * FIXED 窗口的锚点/页脚在「一行结束」的瞬间变化(进行中→下一节 / 全结束→ALL_DONE),
 * 15-min 周期兜底最长晚 15+ 分钟。本调度器枚举今日在屏的窗口族实例(Today 系/
 * TwoDay 系/网格小档 — 后两者无导航, loadDataSync 目标日恒=今天), 收集全部行
 * end 分钟, 在**全局最小边界(end+1 分钟, 让「已结束」判定生效)排一颗单次闹钟**;
 * 到点 → [WidgetBoundaryReceiver] → notifyDataChanged 全量重推 → 链内自续重排。
 *
 * 权限降级 (评审 #12): S+ 无精确闹钟权限 → setAndAllowWhileIdle 静默不精确,
 * 正确性由 15-min 周期兜底保证, 不打扰用户。Boot/替换包无条件重排。
 */
object WidgetBoundaryScheduler {

    private const val TAG = "WidgetBoundaryScheduler"
    private const val RC = 7601

    /** 窗口锚点随课程边界变化的族 (WeekList/WeekView 为 HEAD 列式, 无边界语义)。 */
    private val BOUNDARY_FAMILIES: List<Class<*>> = listOf(
        TodayWidgetReceiver::class.java,
        TodaySmallWidgetReceiver::class.java,
        TwoDayWidgetReceiver::class.java,
        TwoDaySmallWidgetReceiver::class.java,
        WeekGridSmallWidgetProvider::class.java
    )

    /** 纯函数: 下一边界分钟 = min(end+1) 且 > nowMin; 无未来边界 → null。 */
    internal fun nextBoundaryMin(endMins: List<Int>, nowMin: Int): Int? =
        endMins.asSequence().map { it + 1 }.filter { it > nowMin }.minOrNull()

    /** 行 end 分钟 — 与 FIXED 锚点同一契约 (effectiveCourseTime 真实分钟, 行取 max)。 */
    internal fun rowEndMins(courses: List<CourseEntity>, timeJson: String?): List<Int> =
        FixedWindowCore
            .entriesOf(ConflictLayoutEngine.weekLaneRows(courses, timeJson), timeJson) { 38f }
            .mapNotNull { it.endMin }

    /**
     * 重排下一边界闹钟 (幂等: 同 RC cancel+重排)。阻塞读库 (loadDataSync 内
     * runBlocking + Once 缓存) — 只允许在 IO 线程调用。
     */
    fun armNext(context: Context) {
        try {
            val awm = AppWidgetManager.getInstance(context)
            val now = LocalDateTime.now()
            val nowMin = now.hour * 60 + now.minute
            val endMins = ArrayList<Int>()
            for (cls in BOUNDARY_FAMILIES) {
                val ids = awm.getAppWidgetIds(ComponentName(context, cls))
                for (id in ids) {
                    val data = TodayWidgetReceiver.loadDataSync(context, id)
                    if (data.isToday && data.hasTable &&
                        data.semesterStatus == DateUtils.SemesterStatus.IN_RANGE
                    ) {
                        endMins += rowEndMins(data.courses, data.timeJson)
                    }
                }
            }
            val nextMin = nextBoundaryMin(endMins, nowMin)
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pending = buildPendingIntent(context)
            am.cancel(pending)
            if (nextMin == null) {
                Log.d(TAG, "no future boundary today (endMins=${endMins.size}) — alarm cleared")
                return
            }
            val triggerAt = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault())
                .plusMinutes(nextMin.toLong()).toInstant().toEpochMilli()
            if (triggerAt <= System.currentTimeMillis()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                // 无精确权限 → 不精确 + 15-min 周期兜底, 静默降级 (评审 #12)
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                Log.d(TAG, "inexact arm at +${nextMin - nowMin}min (no exact-alarm permission)")
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                Log.d(TAG, "exact arm at +${nextMin - nowMin}min (endMins=${endMins.size})")
            } else {
                // API 21/22 (Android 5): 无 Doze, setExact 即可; setExactAndAllowWhileIdle 是 API 23+
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                Log.d(TAG, "exact arm at +${nextMin - nowMin}min (endMins=${endMins.size})")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "armNext failed", t)
        }
    }

    private fun buildPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, RC,
            Intent(context, WidgetBoundaryReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

/** 边界到点 → 全量重推 (notifyDataChanged 尾部自重排下一边界, 链自续)。 */
class WidgetBoundaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            WidgetUpdater.notifyDataChanged(context)
        }
    }
}
