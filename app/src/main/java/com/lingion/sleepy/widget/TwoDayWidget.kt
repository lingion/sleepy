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
import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 两天视图小组件 — 今天 + 明天
 */
class TwoDayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { loadTwoDayData(context) }
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        provideContent {
            TwoDayContent(
                data = data,
                openAppAction = actionStartActivity(openAppIntent)
            )
        }
    }

    private suspend fun loadTwoDayData(context: Context): TwoDayData {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val isDark = com.lingion.sleepy.util.AppPrefs.isDarkMode(context)
        val themeKey = com.lingion.sleepy.util.AppPrefs.getThemeKey(context)
        return try {
            val app = SleepyApp.get()
            val repo = app.repository
            val table = WidgetTableResolver.resolveCurrentTable()
            if (table == null) {
                TwoDayData(days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey)
            } else {
                val week = DateUtils.currentWeek(table.startDate, today)
                val todayDow = today.dayOfWeek.value
                val tomorrowDow = tomorrow.dayOfWeek.value
                val todayCourses = repo.getCoursesByDayOnce(table.id, todayDow)
                    .filter { it.inWeek(week) }.sortedBy { it.startNode }
                val tomorrowCourses = repo.getCoursesByDayOnce(table.id, tomorrowDow)
                    .filter { it.inWeek(week) }.sortedBy { it.startNode }
                TwoDayData(
                    days = listOf(
                        DayData(date = today, dayOfWeek = todayDow, courses = todayCourses, timeJson = table.timeJson),
                        DayData(date = tomorrow, dayOfWeek = tomorrowDow, courses = tomorrowCourses, timeJson = table.timeJson)
                    ),
                    hasTable = true,
                    isDark = isDark,
                    themeKey = themeKey
                )
            }
        } catch (_: Throwable) {
            TwoDayData(days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey)
        }
    }
}

class TwoDayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TwoDayWidget()
}
