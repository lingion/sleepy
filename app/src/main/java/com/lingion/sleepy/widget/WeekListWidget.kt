package com.lingion.sleepy.widget

import android.content.Context
import android.content.Intent
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
 * 周视图小组件（胶囊列表样式）— 每天一行，课程名竖排
 */
class WeekListWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { loadWeekData(context) }
        android.util.Log.d("WeekListWidget", "provideGlance: hasTable=${data.hasTable}, days=${data.days.size}, " +
            "courses=${data.days.sumOf { it.courses.size }}, perDay=${data.days.map { "${it.dayOfWeek}:${it.courses.size}" }}")
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        provideContent {
            WeekListContent(
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
            val table = WidgetTableResolver.resolveCurrentTable()
            if (table == null) {
                WeekData(days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey)
            } else {
                val week = DateUtils.currentWeek(table.startDate, today)
                val days = (1..7).map { dayOfWeek ->
                    val date = DateUtils.dateOfWeekDay(today, dayOfWeek)
                    val all = repo.getCoursesByDayOnce(table.id, dayOfWeek)
                    val visible = all.filter { it.inWeek(week) }.sortedBy { it.startNode }
                    DayData(date = date, dayOfWeek = dayOfWeek, courses = visible, timeJson = table.timeJson)
                }
                WeekData(days = days, hasTable = true, isDark = isDark, themeKey = themeKey)
            }
        } catch (_: Throwable) {
            WeekData(days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey)
        }
    }
}

class WeekListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekListWidget()
}
