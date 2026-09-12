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
 * 桌面 WeekView 小组件 — 本周课表(周视图), 同步 RemoteViews + Canvas。
 * 与 WeekListWidget 布局完全一致(7 列竖排胶囊), 但课程胶囊无彩色填充
 * (surfaceVariant 背景 + onSurfaceVariant 文字), 纯主题色方案。
 */
open class WeekViewWidgetReceiver : AppWidgetProvider() {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 小组件排版档位 — 基类默认 REGULAR(现有变体); 「本周课表（周视图）· 小」子类覆写为 SMALL */
    open val variantHint: WidgetVariant = WidgetVariant.REGULAR

    private fun push(context: Context, awm: AppWidgetManager, id: Int) {
        // SMALL 变体: compact 分支内部还有 150dp 升档闸, 这里直接传 variant
        val variant = variantHint
        val data = loadDataSync(context, id)
        val opts = awm.getAppWidgetOptions(id)
        val (wDp, hDp) = RemoteViewsWidgetHelper.computeSizeDp(opts)
        // issue#31 荣耀 4×5: 内容超出容器 → pushScrollable (WeekList v1.0.36 同构),
        // 条带全展开长图不裁 5 门; 旧实现无闸 = 永远静态裁切 (显示不完全 + 不能滚)。
        val contentH = WidgetBitmapRenderers.weekViewContentHeightDp(context, data, wDp.toFloat())
        if (contentH <= hDp) {
            RemoteViewsWidgetHelper.renderAndPush(
                context, awm, id, TAG,
                loadData = { data },
                renderBitmap = { d, w, h ->
                    WidgetBitmapRenderers.renderWeekView(context, d, w, h, variant)
                }
            )
        } else {
            // 超出 — 可滚动: 壳图 = 原渲染器按容器尺寸画 (首屏), 条带 = 全展开长图
            val shell = WidgetBitmapRenderers.renderWeekView(context, data, wDp.toFloat(), hDp.toFloat(), variant)
            RemoteViewsWidgetHelper.pushScrollable(
                context, awm, id, TAG,
                layoutRes = com.lingion.sleepy.R.layout.widget_scroll_weeklist,
                shellBitmap = shell,
                scopeExtra = ScrollStripService.StripFactory.SCOPE_WEEKVIEW
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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (id in appWidgetIds) WidgetBindingStore.remove(context, id)
    }

    companion object {
        private const val TAG = "WeekViewRV"

        /**
         * 同步版数据加载 — 与 WeekListWidget.loadDataSync 完全一致。
         * 7 列日列课程, 每列含当天可见节次。
         *
         * [appWidgetId] is plumbed through so a per-widget binding can override
         * the app-wide default table; receivers fall back to
         * [WidgetTableResolver.resolveCurrentTable] when no binding exists.
         */
        fun loadDataSync(context: Context, appWidgetId: Int): WeekData {
            val today = LocalDate.now()
            val isSystemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val isDark = com.lingion.sleepy.util.AppPrefs.isDarkMode(context, isSystemDark)
            val themeKey = com.lingion.sleepy.util.AppPrefs.getThemeKey(context)
            return try {
                kotlinx.coroutines.runBlocking {
                    val app = com.lingion.sleepy.SleepyApp.get()
                    val repo = app.repository
                    val table = WidgetTableResolver.resolveBoundTable(appWidgetId)
                        ?: WidgetTableResolver.resolveCurrentTable()
                    if (table == null) {
                        WeekData(days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey)
                    } else {
                        val week = DateUtils.currentWeek(table.startDate, today)
                        val status = DateUtils.semesterStatus(table.startDate, table.maxWeek, today)
                        // 学期前: 第 1 周课照常显示(预习); 学期后: 课程清空, renderer 画状态行
                        val days = (1..7).map { dayOfWeek ->
                            val date = DateUtils.dateOfWeekDay(today, dayOfWeek)
                            val all = repo.getCoursesByDayOnce(table.id, dayOfWeek)
                            val visible = if (status == DateUtils.SemesterStatus.AFTER_END) emptyList() else
                                all.filter { it.inWeek(week) }.sortedBy { it.startNode }
                            DayData(date = date, dayOfWeek = dayOfWeek, courses = visible, timeJson = table.timeJson)
                        }
                        WeekData(days = days, hasTable = true, isDark = isDark, themeKey = themeKey, semesterStatus = status)
                    }
                }
            } catch (_: Throwable) {
                WeekData(days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey)
            }
        }
    }
}
