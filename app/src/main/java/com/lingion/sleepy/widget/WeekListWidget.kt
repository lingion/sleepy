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
 * 桌面 WeekList 小组件 — 同步 RemoteViews + Canvas (v1.0.29 起, 从 Glance 移植)。
 * 原因见 [TodayWidgetReceiver] 注释。
 *
 * v1.0.36: 内容装得下走静态 renderAndPush; 超出走 pushScrollable(壳图+条带)。
 *
 * Glance 版 WeekListWidget 类已删除(决策 D5-11); loadDataSync 自 Glance companion 迁入本类。
 */
open class WeekListWidgetReceiver : AppWidgetProvider() {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 小组件排版档位 — 基类默认 REGULAR(现有变体); 「本周课表（列表）· 小」子类覆写为 SMALL */
    open val variantHint: WidgetVariant = WidgetVariant.REGULAR

    private fun push(context: Context, awm: AppWidgetManager, id: Int) {
        val data = loadDataSync(context)
        val opts = awm.getAppWidgetOptions(id)
        val (wDp, hDp) = RemoteViewsWidgetHelper.computeSizeDp(opts)
        val contentH = WidgetBitmapRenderers.weekListContentHeightDp(context, data)
        // SMALL 变体: compact 分支内部还有 150dp 升档闸, 这里直接传 variant
        val variant = variantHint
        if (contentH <= hDp) {
            // 内容装得下 — 原静态路径, 与主分支逐字节一致(REGULAR 时 variant 默认值等价旧调用)
            RemoteViewsWidgetHelper.renderAndPush(
                context, awm, id, TAG,
                loadData = { data },
                renderBitmap = { d, w, h ->
                    WidgetBitmapRenderers.renderWeekList(context, d, w, h, variant)
                }
            )
        } else {
            // 超出 — 可滚动: 壳图 = 原渲染器按容器尺寸画(圆角背景+首屏)
            // SMALL 档内容只有 1-2 行, 永远装得下; 兜底仍走原 scrollable
            val shell = WidgetBitmapRenderers.renderWeekList(context, data, wDp.toFloat(), hDp.toFloat(), variant)
            RemoteViewsWidgetHelper.pushScrollable(
                context, awm, id, TAG,
                layoutRes = com.lingion.sleepy.R.layout.widget_scroll_weeklist,
                shellBitmap = shell,
                scopeExtra = ScrollStripService.StripFactory.SCOPE_WEEKLIST
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
        private const val TAG = "WeekListRV"

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
                        val status = DateUtils.semesterStatus(table.startDate, table.maxWeek, today)
                        // 学期前: 钳制周=1, 第 1 周课照常显示(预习); 学期后: 课程清空, renderer 画状态行
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
