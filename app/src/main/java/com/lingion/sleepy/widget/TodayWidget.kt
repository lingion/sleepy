package com.lingion.sleepy.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import com.lingion.sleepy.MainActivity
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // DB 读必须在 IO 线程
        val data = withContext(Dispatchers.IO) { loadWidgetData(context) }
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        provideContent {
            WidgetContent(
                data = data,
                openAppAction = actionStartActivity(openAppIntent),
                openCourseAction = { courseId ->
                    actionStartActivity(MainActivity.intentForCourse(context, courseId))
                }
            )
        }
    }

    /**
     * 拉数据组装 [WidgetData]：
     * 1. 找默认课表
     * 2. 算当前周次
     * 3. 拉今天 day-of-week 的所有课程
     * 4. 过滤掉不在当前周次的
     * 5. 按节次排序
     * 6. 读 app 的深色模式 → 喂给小组件配色
     *
     * 任何一步失败 / 无数据都返回安全的空状态。
     */
    private suspend fun loadWidgetData(context: Context): WidgetData {
        val today = LocalDate.now()
        val dayOfWeek = DateUtils.todayDayOfWeek(today)
        val isDark = com.lingion.sleepy.util.AppPrefs.isDarkMode(context)
        val themeKey = com.lingion.sleepy.util.AppPrefs.getThemeKey(context)
        return try {
            val app = SleepyApp.get()
            val repo = app.repository

            val table = WidgetTableResolver.resolveCurrentTable()
            if (table == null) {
                WidgetData(date = today, courses = emptyList(), timeJson = TimeTableUtils.DEFAULT_TIME_JSON, hasTable = false, isDark = isDark, themeKey = themeKey)
            } else {
                val week = DateUtils.currentWeek(table.startDate, today)
                val all = repo.getCoursesByDayOnce(table.id, dayOfWeek)
                val visible = all.filter { it.inWeek(week) }.sortedBy { it.startNode }
                WidgetData(date = today, courses = visible, timeJson = table.timeJson, hasTable = true, isDark = isDark, themeKey = themeKey)
            }
        } catch (_: Throwable) {
            WidgetData(date = today, courses = emptyList(), timeJson = TimeTableUtils.DEFAULT_TIME_JSON, hasTable = false, isDark = isDark, themeKey = themeKey)
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
