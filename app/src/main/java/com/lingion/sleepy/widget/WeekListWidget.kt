package com.lingion.sleepy.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import com.lingion.sleepy.MainActivity
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Glance 版 WeekListWidget — 仅供 WidgetRenderActivity (调试预览) 使用。
 * 桌面实际渲染走 [WeekListWidgetReceiver] (同步 RemoteViews)。
 */
class WeekListWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { loadDataSync(context) }
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

    companion object {
        /**
         * 同步版数据加载 — 7 列日列课程。与 WeekGridWidgetProvider.loadWeekData 结构一致。
         */
        fun loadDataSync(context: Context): WeekData {
            val today = LocalDate.now()
            val isSystemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val isDark = com.lingion.sleepy.util.AppPrefs.isDarkMode(context, isSystemDark)
            val themeKey = com.lingion.sleepy.util.AppPrefs.getThemeKey(context)
            return try {
                runBlocking {
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
                }
            } catch (_: Throwable) {
                WeekData(days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey)
            }
        }
    }
}

/**
 * ★ 桌面 WeekList 小组件 — 同步 RemoteViews + Canvas (v1.0.29 起, 从 Glance 移植)。
 * 原因见 [TodayWidgetReceiver] 注释。失去 LazyColumn 滚动 → 静态 bitmap。
 */
class WeekListWidgetReceiver : AppWidgetProvider() {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onUpdate(context: Context, awm: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        ioScope.launch {
            try {
                for (id in ids) {
                    try {
                        RemoteViewsWidgetHelper.renderAndPush(
                            context, awm, id, TAG,
                            loadData = { WeekListWidget.loadDataSync(context) },
                            renderBitmap = { data, wDp, hDp ->
                                WidgetBitmapRenderers.renderWeekList(context, data, wDp, hDp)
                            }
                        )
                    } catch (e: Throwable) { Log.e(TAG, "render failed $id", e) }
                }
            } finally { pending.finish() }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, awm: AppWidgetManager, id: Int, newOptions: Bundle
    ) {
        val pending = goAsync()
        ioScope.launch {
            try {
                RemoteViewsWidgetHelper.renderAndPush(
                    context, awm, id, TAG,
                    loadData = { WeekListWidget.loadDataSync(context) },
                    renderBitmap = { data, wDp, hDp ->
                        WidgetBitmapRenderers.renderWeekList(context, data, wDp, hDp)
                    }
                )
            } catch (e: Throwable) { Log.e(TAG, "optionsChanged render failed $id", e) }
            finally { pending.finish() }
        }
    }

    companion object { private const val TAG = "WeekListRV" }
}
