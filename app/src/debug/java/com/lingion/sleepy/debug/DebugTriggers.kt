package com.lingion.sleepy.debug

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.AppClock
import com.lingion.sleepy.widget.notification.FluidCloudService
import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * DebugImportReceiver 六能力系统化（Task 5, SPEC §/v1/trigger）—
 * receiver 保留 adb 广播入口形态, 逻辑全部转调本 object;
 * console TriggerHandler 与 receiver 共享同一实现, 不再两套漂移。
 *
 * 全部 suspend-free（内部 runBlocking）— 调用方自带线程或 ContentProvider
 * 主线程皆可; 每个能力返回人类可读结果摘要（data.triggered 语义）。
 *
 * 虚拟时钟口径（Task 4 依赖）：时间锚一律 AppClock —— "现在+N分钟上课"的课
 * 按虚拟时间落库, 课前判定同样按虚拟时间, 调试台改 clock 即可端到端验证。
 */
object DebugTriggers {

    private const val TAG = "DebugTriggers"

    /** 能力 1: 非测试表设默认 + 删名字含"测试"/"流体"的表。 */
    suspend fun restoreAndCleanup(context: Context): String {
        val repo = SleepyApp.get().repository
        val all = repo.getAllTables()
        val keep = all.firstOrNull { !it.name.contains("测试") && !it.name.contains("fluid") }
        if (keep != null) repo.setDefault(keep.id)
        val deleted = all.filter { it.name.contains("测试") || it.name.contains("流体") }
            .onEach { repo.deleteTable(it.id) }
            .map { it.name }
        return "deleted=${deleted.size} names=$deleted default=${keep?.name}"
    }

    /** 能力 2: 删除所有非默认表。 */
    suspend fun deleteNonDefaultTables(context: Context): String {
        val repo = SleepyApp.get().repository
        val before = repo.getAllTables().size
        repo.getAllTables().filter { !it.isDefault }.forEach { repo.deleteTable(it.id) }
        val after = repo.getAllTables().size
        return "tables $before -> $after"
    }

    /** 能力 3: 过期窗口触发 FluidCloudService 自停 = 清流体云通知。 */
    fun clearFluidNotification(context: Context): String {
        val now = AppClock.millis()
        val expired = Intent(context, FluidCloudService::class.java).apply {
            putExtra("courseName", "")
            putExtra("room", "")
            putExtra("startTime", "")
            putExtra("notifyEpoch", now - 2_000L)
            putExtra("classEpoch", now - 1_000L)
        }
        ContextCompat.startForegroundService(context, expired)
        return "fluid service expired-window stop dispatched"
    }

    /** 能力 4: 直接起流体云演示（2 分钟窗口; receiver 旧形态保留 60s 驻留, console 侧不阻塞）。 */
    fun testBeforeClass(
        context: Context,
        courseName: String = "高等数学",
        startTime: String = "14:00",
        room: String = "A101",
        teacher: String = "张老师",
    ): String {
        val now = AppClock.millis()
        val svc = Intent(context, FluidCloudService::class.java).apply {
            putExtra("courseName", courseName)
            putExtra("startTime", startTime)
            putExtra("room", room)
            putExtra("teacher", teacher)
            putExtra("notifyEpoch", now)
            putExtra("classEpoch", now + 120_000L)
        }
        ContextCompat.startForegroundService(context, svc)
        return "fluid service started window=120s course=$courseName@$startTime"
    }

    /** 能力 5: 立即重排今日课前提醒。 */
    suspend fun scheduleBeforeClassNow(context: Context): String {
        SleepyApp.get().notificationScheduler.scheduleTodayBeforeClassAlarms()
        return "scheduleTodayBeforeClassAlarms executed"
    }

    /** 能力 6: 新建默认测试课表 + 一节"虚拟现在+N分钟"上课的 ownTime 课。 */
    suspend fun addSoonestCourse(
        context: Context,
        delayMin: Int = 5,
        courseName: String = "流体云测试课",
        room: String = "A101",
    ): String {
        val repo = SleepyApp.get().repository
        val cls = LocalTime.now(AppClock).plusMinutes(delayMin.toLong())
        val end = cls.plusMinutes(45)
        val today = LocalDate.now(AppClock)
        val monday = today.with(DayOfWeek.MONDAY)
        val tid = run {
            val id = repo.insertTable(
                TimeTableEntity(
                    name = "流体云触发测试",
                    startDate = monday.toString(),
                    isDefault = true
                )
            )
            repo.setDefault(id)
            id
        }
        val c = CourseEntity(
            groupId = UUID.randomUUID().toString(),
            tableId = tid,
            courseName = courseName,
            teacher = "测试",
            room = room,
            day = today.dayOfWeek.value,
            startNode = 1, step = 1,
            startWeek = 1, endWeek = 20, type = 0,
            color = "#FF6750A4",
            ownTime = true,
            startTime = String.format("%02d:%02d", cls.hour, cls.minute),
            endTime = String.format("%02d:%02d", end.hour, end.minute)
        )
        repo.insertCourse(c)
        Log.d(TAG, "created table $tid, course at ${c.startTime} ownTime")
        return "tableId=$tid startTime=${c.startTime} endTime=${c.endTime} day=${c.day}"
    }
}
