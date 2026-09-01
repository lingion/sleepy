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
 * 桌面 Today 小组件 — 同步 RemoteViews + Canvas (v1.0.29 起, 从 Glance 移植)。
 *
 * 之前是 GlanceAppWidgetReceiver → provideGlance 异步 SessionWorker → OPPO OplusHansManager
 * 冻结进程 → RemoteViews 从不生成 → 卡在 widget_loading 紫色布局 → 不跟随主题。
 * 现在克隆 WeekGridWidgetProvider 模式: goAsync → 加载 → 画 bitmap → awm.updateAppWidget,
 * 全程在冻结窗口前完成 → 秒刷 + 主题正确。
 *
 * v1.0.36: 内容装得下走静态 renderAndPush(与主分支一致); 装不下走 pushScrollable
 * (壳图+条带 ListView, 条带与静态渲染同源 → 顶部像素一致, 可滚动)。
 *
 * Glance 版 TodayWidget 类已删除(决策 D5-11); loadDataSync 自 Glance companion 迁入本类。
 */
open class TodayWidgetReceiver : AppWidgetProvider() {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 小组件排版档位 — 基类默认 REGULAR(现有变体); 「今日课程 · 小」子类覆写为 SMALL */
    open val variantHint: WidgetVariant = WidgetVariant.REGULAR

    private fun push(context: Context, awm: AppWidgetManager, id: Int) {
        pushTodayData(context, awm, id, variantHint, loadDataSync(context))
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
        private const val TAG = "TodayWidgetRV"

        /**
         * 今日课程推送管线(静态/可滚动闸门) — 网格小最小档与今日课程·小共用,
         * 保证"变成今日课程那个小组件的样子"像素级同源(同一渲染器+同一滚动条带工厂)。
         * 注意 Today 小变体在这里等效直通(REGULAR 也走这条闸), 与改动前行为一致。
         */
        fun pushTodayData(
            context: Context, awm: AppWidgetManager, id: Int,
            variant: WidgetVariant, data: WidgetData
        ) {
            val opts = awm.getAppWidgetOptions(id)
            val (wDp, hDp) = RemoteViewsWidgetHelper.computeSizeDp(opts)
            val contentH = WidgetBitmapRenderers.todayContentHeightDp(data)
            if (contentH <= hDp) {
                RemoteViewsWidgetHelper.renderAndPush(
                    context, awm, id, TAG,
                    loadData = { data },
                    renderBitmap = { d, w, h ->
                        WidgetBitmapRenderers.renderToday(context, d, w, h, variant)
                    }
                )
            } else {
                val shell = WidgetBitmapRenderers.renderToday(context, data, wDp.toFloat(), hDp.toFloat(), variant)
                RemoteViewsWidgetHelper.pushScrollable(
                    context, awm, id, TAG,
                    layoutRes = com.lingion.sleepy.R.layout.widget_scroll_today,
                    shellBitmap = shell,
                    scopeExtra = ScrollStripService.StripFactory.SCOPE_TODAY
                )
            }
        }

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
                        val status = DateUtils.semesterStatus(table.startDate, table.maxWeek, today)
                        val all = repo.getCoursesByDayOnce(table.id, dayOfWeek)
                        // 学期外(前/后)不展示课程 — App 今日页同语义, 避免学期前显示"第1周"的课
                        val visible = if (status != DateUtils.SemesterStatus.IN_RANGE) emptyList() else
                            all.filter { it.inWeek(week) }.sortedBy { it.startNode }
                        WidgetData(date = today, courses = visible, timeJson = table.timeJson, hasTable = true, isDark = isDark, themeKey = themeKey, semesterStatus = status)
                    }
                }
            } catch (_: Throwable) {
                WidgetData(date = today, courses = emptyList(), timeJson = TimeTableUtils.DEFAULT_TIME_JSON, hasTable = false, isDark = isDark, themeKey = themeKey)
            }
        }
    }
}
