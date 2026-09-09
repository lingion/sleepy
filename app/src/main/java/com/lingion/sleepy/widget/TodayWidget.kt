package com.lingion.sleepy.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
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
        pushTodayData(context, awm, id, variantHint, loadDataSync(context, id), this::class.java)
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
        for (id in appWidgetIds) {
            WidgetBindingStore.remove(context, id)
            TodayDateNavStore.remove(context, id)
        }
    }

    /**
     * 导航点击派发 (issue #24 Feature2) — 三个 nav action 走 [handleNav],
     * 其余 (APPWIDGET_UPDATE / DELETED / OPTIONS_CHANGED …) 原样转发 super,
     * 否则 onUpdate 等默认分发会被截断。
     */
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PREV_DAY, ACTION_NEXT_DAY, ACTION_RESET_DAY -> handleNav(context, intent)
            else -> super.onReceive(context, intent)
        }
    }

    /** shift / remove 持久化后重推该实例 (R2 带参 / R3 回今天 / R4 按 id 隔离)。 */
    private fun handleNav(context: Context, intent: Intent) {
        val action = intent.action
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val pending = goAsync()
        ioScope.launch {
            try {
                when (navDelta(action)) {
                    -1L -> TodayDateNavStore.shift(context, widgetId, LocalDate.now(), -1L)
                    1L -> TodayDateNavStore.shift(context, widgetId, LocalDate.now(), 1L)
                    else -> if (action == ACTION_RESET_DAY) TodayDateNavStore.remove(context, widgetId)
                }
                push(context, AppWidgetManager.getInstance(context), widgetId)
            } catch (e: Throwable) {
                Log.e(TAG, "nav failed $widgetId", e)
            } finally { pending.finish() }
        }
    }

    companion object {
        private const val TAG = "TodayWidgetRV"

        // ── issue #24 Feature2 日期导航 ──
        /** 前一天 / 后一天 / 回今天 的广播 action (nav PendingIntent 用)。 */
        const val ACTION_PREV_DAY = "com.lingion.sleepy.widget.TODAY_NAV_PREV"
        const val ACTION_NEXT_DAY = "com.lingion.sleepy.widget.TODAY_NAV_NEXT"
        const val ACTION_RESET_DAY = "com.lingion.sleepy.widget.TODAY_NAV_RESET"

        /** action → 导航增量; RESET / 未知 / null → null (RESET 由 handleNav 单独分支)。 */
        fun navDelta(action: String?): Long? = when (action) {
            ACTION_PREV_DAY -> -1L
            ACTION_NEXT_DAY -> 1L
            else -> null
        }

        /** (widgetId, zone 序号) → 唯一 requestCode — 同 id 多 PI 不同 extras 必须不同 code。 */
        fun navRequestCode(widgetId: Int, ordinal: Int): Int = widgetId * 3 + ordinal

        /**
         * 配置今日导航顶栏 — 只给 Today 系 receiver 的实例用
         * (WeekGrid 最小档复用 pushTodayData 管线但传 receiverClass=null → 不会进来)。
         * 顶栏 = 真实 RemoteViews 视图顶栏: 标题 TextView + 左右低对比三角按钮 + 「回到今天」
         * TextView (仅导航态显示, 居两钮正中)。视图即点击区 (40×28dp 大热区, 修
         * 「点击区域太小」); bitmap 头部留白由渲染器 emptyHeader=true 保证, 不双重标题。
         */
        fun configureTodayNav(
            context: Context, views: android.widget.RemoteViews,
            widgetId: Int, receiverClass: Class<*>, data: WidgetData
        ) {
            // 顶栏背景 — overflow 路径挡住条带上滑内容; 颜色与卡面 bitmap 同一 scheme
            val colors = WidgetBitmapRenderers.todayNavHeaderColors(context, data)
            views.setInt(com.lingion.sleepy.R.id.widget_today_header, "setBackgroundColor", colors.bg)
            // 标题 — 「M/D · 周X」恒显日期 (用户定稿: 两种状态格式统一)
            views.setTextViewText(
                com.lingion.sleepy.R.id.widget_today_nav_title,
                navTitle(data, DateUtils.localizedDay(data.date.dayOfWeek.value, context))
            )
            views.setTextColor(com.lingion.sleepy.R.id.widget_today_nav_title, colors.title)
            views.setTextViewTextSize(
                com.lingion.sleepy.R.id.widget_today_nav_title,
                TypedValue.COMPLEX_UNIT_DIP, 13f
            )
            // 「回到今天」 — 仅导航态显示, 纯文本居两钮正中 (用户规格)
            views.setTextViewText(
                com.lingion.sleepy.R.id.widget_today_nav_today,
                context.getString(com.lingion.sleepy.R.string.today_nav_back_to_today)
            )
            views.setTextColor(com.lingion.sleepy.R.id.widget_today_nav_today, colors.action)
            views.setTextViewTextSize(
                com.lingion.sleepy.R.id.widget_today_nav_today,
                TypedValue.COMPLEX_UNIT_DIP, 11f
            )
            views.setViewVisibility(
                com.lingion.sleepy.R.id.widget_today_nav_today,
                if (data.isToday) android.view.View.GONE else android.view.View.VISIBLE
            )
            // 三角按钮位图 — 低对比圆角矩形 (surfaceVariant 底 + onSurfaceVariant 图标)
            views.setImageViewBitmap(
                com.lingion.sleepy.R.id.widget_today_nav_prev,
                WidgetBitmapRenderers.renderNavTriangle(context, data, pointLeft = true)
            )
            views.setImageViewBitmap(
                com.lingion.sleepy.R.id.widget_today_nav_next,
            WidgetBitmapRenderers.renderNavTriangle(context, data, pointLeft = false)
            )
            val zones = listOf(
                Triple(com.lingion.sleepy.R.id.widget_today_nav_prev, ACTION_PREV_DAY, 0),
                Triple(com.lingion.sleepy.R.id.widget_today_nav_next, ACTION_NEXT_DAY, 1),
                Triple(com.lingion.sleepy.R.id.widget_today_nav_today, ACTION_RESET_DAY, 2)
            )
            for ((viewId, action, ordinal) in zones) {
                val pi = PendingIntent.getBroadcast(
                    context, navRequestCode(widgetId, ordinal),
                    Intent(context, receiverClass).apply {
                        this.action = action
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(viewId, pi)
            }
        }

        /** 顶栏标题 — 「M/D · 周X」恒显日期 (用户定稿), 今日/导航两态格式统一。纯函数可 JVM 断言。 */
        fun navTitle(data: WidgetData, dayName: String): String =
            "${data.dateLabel} · $dayName"

        /**
         * 今日课程推送管线(静态/可滚动闸门) — 网格小最小档与今日课程·小共用,
         * 保证"变成今日课程那个小组件的样子"像素级同源(同一渲染器+同一滚动条带工厂)。
         * 注意 Today 小变体在这里等效直通(REGULAR 也走这条闸), 与改动前行为一致。
         *
         * [receiverClass] = 拥有该 widget 的 AppWidgetProvider 类 (issue #24 Feature2):
         * Today 系 receiver (含子类) → 传自身类, 挂日期导航 (widget_today_nav_static /
         * widget_scroll_today_nav + configureTodayNav);
         * WeekGrid 最小档 → 不传 (默认 null) → 布局与行为与改动前逐字节一致, 不沾导航区。
         */
        fun pushTodayData(
            context: Context, awm: AppWidgetManager, id: Int,
            variant: WidgetVariant, data: WidgetData,
            receiverClass: Class<*>? = null
        ) {
            val navEnabled = receiverClass != null &&
                TodayWidgetReceiver::class.java.isAssignableFrom(receiverClass)
            val opts = awm.getAppWidgetOptions(id)
            val (wDp, hDp) = RemoteViewsWidgetHelper.computeSizeDp(opts)
            val contentH = WidgetBitmapRenderers.todayContentHeightDp(data)
            val navZones: ((android.widget.RemoteViews) -> Unit)? = if (navEnabled) {
                val rc = receiverClass!!
                { views -> configureTodayNav(context, views, id, rc, data) }
            } else null
            if (!navEnabled) {
                // WeekGrid 最小档 — 改动前行为逐字节一致 (无导航, 无按钮条)
                if (contentH <= hDp) {
                    RemoteViewsWidgetHelper.renderAndPush(
                        context, awm, id, TAG,
                        loadData = { data },
                        renderBitmap = { d, w, h ->
                            WidgetBitmapRenderers.renderToday(context, d, w, h, variant)
                        },
                        layoutRes = com.lingion.sleepy.R.layout.widget_bitmap_container
                    )
                } else {
                    val shell = WidgetBitmapRenderers.renderToday(
                        context, data, wDp.toFloat(), hDp.toFloat(), variant
                    )
                    RemoteViewsWidgetHelper.pushScrollable(
                        context, awm, id, TAG,
                        layoutRes = com.lingion.sleepy.R.layout.widget_scroll_today,
                        shellBitmap = shell,
                        scopeExtra = ScrollStripService.StripFactory.SCOPE_TODAY
                    )
                }
            } else if (contentH <= hDp) {
                // Today 系静态分支 — bitmap(emptyHeader 留白顶栏) + 真实视图顶栏 (issue #24)
                val shell = WidgetBitmapRenderers.renderToday(
                    context, data, wDp.toFloat(), hDp.toFloat(), variant, emptyHeader = true
                )
                val views = android.widget.RemoteViews(
                    context.packageName, com.lingion.sleepy.R.layout.widget_today_nav_static
                )
                views.setImageViewBitmap(com.lingion.sleepy.R.id.widget_bitmap, shell)
                val tap = PendingIntent.getActivity(
                    context, WidgetRoutes.tapRequestCode(id),
                    WidgetRoutes.tapIntent(context),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(com.lingion.sleepy.R.id.widget_bitmap, tap)
                configureTodayNav(context, views, id, receiverClass!!, data)
                awm.updateAppWidget(id, views)
                Log.d(TAG, "pushTodayData static-nav id=$id ${wDp}x${hDp}dp content=$contentH")
            } else {
                // Today 系 overflow — 壳+条带(emptyHeader 同源) + 真实视图顶栏
                // (不透明, 挡住条带上滑内容; 条带滚动位 0 与静态渲染坐标一致)
                val shell = WidgetBitmapRenderers.renderToday(
                    context, data, wDp.toFloat(), hDp.toFloat(), variant, emptyHeader = true
                )
                RemoteViewsWidgetHelper.pushScrollable(
                    context, awm, id, TAG,
                    layoutRes = com.lingion.sleepy.R.layout.widget_scroll_today_nav,
                    shellBitmap = shell,
                    scopeExtra = ScrollStripService.StripFactory.SCOPE_TODAY,
                    configureViews = navZones,
                    stripHeaderless = true
                )
            }
        }

        /**
         * 同步版数据加载 (runBlocking DB 读) — 供 RemoteViews Receiver 使用。
         *
         * [appWidgetId] is plumbed through so a per-widget binding can override
         * the app-wide default table; receivers fall back to
         * [WidgetTableResolver.resolveCurrentTable] when no binding exists.
         */
        fun loadDataSync(context: Context, appWidgetId: Int): WidgetData =
            loadDataForDate(context, appWidgetId, TodayDateNavStore.target(context, appWidgetId, LocalDate.now()))

        /**
         * 指定日期版数据加载 (issue #24 StackView 翻页卡工厂用) — [target] 由调用方给出
         * (loadDataSync 传导航锚定日, StackView 卡工厂传卡面日期)。
         */
        fun loadDataForDate(context: Context, appWidgetId: Int, target: LocalDate): WidgetData {
            val today = LocalDate.now()
            val dayOfWeek = DateUtils.todayDayOfWeek(target)
            val isSystemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val isDark = com.lingion.sleepy.util.AppPrefs.isDarkMode(context, isSystemDark)
            val themeKey = com.lingion.sleepy.util.AppPrefs.getThemeKey(context)
            val themeMode = com.lingion.sleepy.util.AppPrefs.getThemeMode(context)
            Log.d("TodayWidget", "DIAG: isDark=$isDark isSystemDark=$isSystemDark themeMode=$themeMode themeKey=$themeKey")
            return try {
                runBlocking {
                    val app = SleepyApp.get()
                    val repo = app.repository
                    val table = WidgetTableResolver.resolveBoundTable(appWidgetId)
                        ?: WidgetTableResolver.resolveCurrentTable()
                    if (table == null) {
                        WidgetData(date = target, courses = emptyList(), timeJson = TimeTableUtils.DEFAULT_TIME_JSON, hasTable = false, isDark = isDark, themeKey = themeKey, isToday = target == today)
                    } else {
                        val week = DateUtils.currentWeek(table.startDate, target)
                        val status = DateUtils.semesterStatus(table.startDate, table.maxWeek, target)
                        val all = repo.getCoursesByDayOnce(table.id, dayOfWeek)
                        // 学期外(前/后)不展示课程 — App 今日页同语义, 避免学期前显示"第1周"的课
                        val visible = if (status != DateUtils.SemesterStatus.IN_RANGE) emptyList() else
                            all.filter { it.inWeek(week) }.sortedBy { it.startNode }
                        WidgetData(date = target, courses = visible, timeJson = table.timeJson, hasTable = true, isDark = isDark, themeKey = themeKey, semesterStatus = status, isToday = target == today)
                    }
                }
            } catch (_: Throwable) {
                WidgetData(date = target, courses = emptyList(), timeJson = TimeTableUtils.DEFAULT_TIME_JSON, hasTable = false, isDark = isDark, themeKey = themeKey, isToday = target == today)
            }
        }
    }
}
