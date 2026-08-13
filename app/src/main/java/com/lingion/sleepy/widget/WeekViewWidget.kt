package com.lingion.sleepy.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/**
 * ★ 桌面 WeekView 小组件 — 本周课表(周视图), 同步 RemoteViews + Canvas。
 * 与 WeekListWidget 布局完全一致(7 列竖排胶囊), 但课程胶囊无彩色填充
 * (surfaceVariant 背景 + onSurfaceVariant 文字), 纯主题色方案。
 */
class WeekViewWidgetReceiver : AppWidgetProvider() {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onUpdate(context: Context, awm: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        ioScope.launch {
            try {
                for (id in ids) {
                    try {
                        RemoteViewsWidgetHelper.renderAndPush(
                            context, awm, id, TAG,
                            loadData = { loadDataSync(context) },
                            renderBitmap = { data, wDp, hDp ->
                                WidgetBitmapRenderers.renderWeekView(context, data, wDp, hDp)
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
                    loadData = { loadDataSync(context) },
                    renderBitmap = { data, wDp, hDp ->
                        WidgetBitmapRenderers.renderWeekView(context, data, wDp, hDp)
                    }
                )
            } catch (e: Throwable) { Log.e(TAG, "optionsChanged render failed $id", e) }
            finally { pending.finish() }
        }
    }

    companion object {
        private const val TAG = "WeekViewRV"

        /**
         * 同步版数据加载 — 与 WeekListWidget.loadDataSync 完全一致。
         * 7 列日列课程, 每列含当天可见节次。
         */
        fun loadDataSync(context: Context): WeekData {
            val today = LocalDate.now()
            val isSystemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val isDark = com.lingion.sleepy.util.AppPrefs.isDarkMode(context, isSystemDark)
            val themeKey = com.lingion.sleepy.util.AppPrefs.getThemeKey(context)
            return try {
                kotlinx.coroutines.runBlocking {
                    val app = com.lingion.sleepy.SleepyApp.get()
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
