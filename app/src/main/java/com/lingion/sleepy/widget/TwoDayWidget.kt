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
 * ★ 桌面 TwoDay 小组件 — 同步 RemoteViews + Canvas (v1.0.29 起, 从 Glance 移植)。
 * 原因见 [TodayWidgetReceiver] 注释。
 *
 * v1.0.36: 内容装得下走静态 renderAndPush; 超出走 pushScrollable(壳图+条带)。
 *
 * Glance 版 TwoDayWidget 类已删除(决策 D5-11); loadDataSync 自 Glance companion 迁入本类。
 */
class TwoDayWidgetReceiver : AppWidgetProvider() {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun push(context: Context, awm: AppWidgetManager, id: Int) {
        val data = loadDataSync(context)
        val opts = awm.getAppWidgetOptions(id)
        val (wDp, hDp) = RemoteViewsWidgetHelper.computeSizeDp(opts)
        val contentH = WidgetBitmapRenderers.twoDayContentHeightDp(data)
        if (contentH <= hDp) {
            RemoteViewsWidgetHelper.renderAndPush(
                context, awm, id, TAG,
                loadData = { data },
                renderBitmap = { d, w, h -> WidgetBitmapRenderers.renderTwoDay(context, d, w, h) }
            )
        } else {
            val shell = WidgetBitmapRenderers.renderTwoDay(context, data, wDp.toFloat(), hDp.toFloat())
            RemoteViewsWidgetHelper.pushScrollable(
                context, awm, id, TAG,
                layoutRes = com.lingion.sleepy.R.layout.widget_scroll_twoday,
                shellBitmap = shell,
                scopeExtra = ScrollStripService.StripFactory.SCOPE_TWODAY
            )
        }
    }

    override fun onUpdate(context: Context, awm: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        ioScope.launch {
            try {
                for (id in ids) {
                    try { push(context, awm, id) }
                    catch (e: Throwable) { Log.e(TAG, "render failed $id", e) }
                }
            } finally { pending.finish() }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, awm: AppWidgetManager, id: Int, newOptions: Bundle
    ) {
        val pending = goAsync()
        ioScope.launch {
            try { push(context, awm, id) }
            catch (e: Throwable) { Log.e(TAG, "optionsChanged render failed $id", e) }
            finally { pending.finish() }
        }
    }

    companion object {
        private const val TAG = "TwoDayRV"

        /**
         * 同步版数据加载 — 今天 + 明天课程。
         */
        fun loadDataSync(context: Context): TwoDayData {
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)
            val isSystemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val isDark = com.lingion.sleepy.util.AppPrefs.isDarkMode(context, isSystemDark)
            val themeKey = com.lingion.sleepy.util.AppPrefs.getThemeKey(context)
            return try {
                runBlocking {
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
                }
            } catch (_: Throwable) {
                TwoDayData(days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey)
            }
        }
    }
}
