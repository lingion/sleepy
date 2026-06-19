package com.lingion.sleepy.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import com.lingion.sleepy.MainActivity
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 周视图小组件（网格样式）— 7 列网格，课程按节次定位
 */
class WeekGridWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { loadWeekData(context) }
        Log.d("WeekGridWidget", "provideGlance: hasTable=${data.hasTable}, days=${data.days.size}, " +
            "courses=${data.days.sumOf { it.courses.size }}, maxNode=" +
            (if (data.days.isEmpty()) 0 else data.days.maxOf { d -> d.courses.maxOfOrNull { it.startNode + it.step - 1 } ?: 0 }))
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        provideContent {
            WeekGridContent(
                data = data,
                openAppAction = actionStartActivity(openAppIntent)
            )
        }
    }

    internal suspend fun loadWeekData(context: Context): WeekData {
        val today = LocalDate.now()
        val isDark = com.lingion.sleepy.util.AppPrefs.isDarkMode(context)
        val themeKey = com.lingion.sleepy.util.AppPrefs.getThemeKey(context)
        return try {
            val app = SleepyApp.get()
            val repo = app.repository
            val table = repo.getDefaultTable()
            if (table == null) {
                Log.w("WeekGridWidget", "loadWeekData: no default table")
                WeekData(days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey)
            } else {
                val week = DateUtils.currentWeek(table.startDate, today)
                val days = (1..7).map { dayOfWeek ->
                    val date = DateUtils.dateOfWeekDay(today, dayOfWeek)
                    val all = repo.getCoursesByDayOnce(table.id, dayOfWeek)
                    val visible = all.filter { it.inWeek(week) }.sortedBy { it.startNode }
                    DayData(date = date, dayOfWeek = dayOfWeek, courses = visible, timeJson = table.timeJson)
                }
                Log.d("WeekGridWidget", "loadWeekData: table=${table.id}, week=$week, totalCourses=${days.sumOf { it.courses.size }}")
                WeekData(days = days, hasTable = true, isDark = isDark, themeKey = themeKey)
            }
        } catch (e: Throwable) {
            Log.e("WeekGridWidget", "loadWeekData failed", e)
            WeekData(days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey)
        }
    }
}

class WeekGridWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekGridWidget()
}
