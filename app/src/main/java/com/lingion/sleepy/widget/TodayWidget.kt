package com.lingion.sleepy.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/**
 * ★ 桌面 Today 小组件 — 同步 RemoteViews + Canvas (v1.0.29 起, 从 Glance 移植)。
 *
 * 之前是 GlanceAppWidgetReceiver → provideGlance 异步 SessionWorker → OPPO OplusHansManager
 * 冻结进程 → RemoteViews 从不生成 → 卡在 widget_loading 紫色布局 → 不跟随主题。
 * 现在克隆 WeekGridWidgetProvider 模式: goAsync → 加载 → 画 bitmap → awm.updateAppWidget,
 * 全程在冻结窗口前完成 → 秒刷 + 主题正确。
 *
 * 失去 Glance 交互(单课点击跳转/滚动) → 整个 widget 单击打开 app (与 WeekGrid 同取舍)。
 *
 * Glance 版 TodayWidget 类已删除(决策 D5-11): 5 个生产入口全走 RemoteViews,
 * Glance 层生产不可达; loadDataSync 自 Glance companion 迁入本类。
 */
class TodayWidgetReceiver : AppWidgetProvider() {
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
                                WidgetBitmapRenderers.renderToday(context, data, wDp, hDp)
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
                        WidgetBitmapRenderers.renderToday(context, data, wDp, hDp)
                    }
                )
            } catch (e: Throwable) { Log.e(TAG, "optionsChanged render failed $id", e) }
            finally { pending.finish() }
        }
    }

    companion object {
        private const val TAG = "TodayWidgetRV"

        /**
         * 同步版数据加载 (runBlocking DB 读) — 供 RemoteViews Receiver 使用。
         */
        fun loadDataSync(context: Context): WidgetData {
            val today = LocalDate.now()
            val dayOfWeek = DateUtils.todayDayOfWeek(today)
            val isSystemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val isDark = com.lingion.sleepy.util.AppPrefs.isDarkMode(context, isSystemDark)
            val themeKey = com.lingion.sleepy.util.AppPrefs.getThemeKey(context)
            val themeMode = com.lingion.sleepy.util.AppPrefs.getThemeMode(context)
            Log.d("TodayWidget", "DIAG: isDark=$isDark isSystemDark=$isSystemDark themeMode=$themeMode themeKey=$themeKey")
            return try {
                runBlocking {
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
                }
            } catch (_: Throwable) {
                WidgetData(date = today, courses = emptyList(), timeJson = TimeTableUtils.DEFAULT_TIME_JSON, hasTable = false, isDark = isDark, themeKey = themeKey)
            }
        }
    }
}
