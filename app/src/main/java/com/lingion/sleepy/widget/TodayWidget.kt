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
/** 顶栏降级档位 — 2×2 刷新按钮定稿: 回到今天恒为真实按钮 (40dp), 恓牲序 = 标题先退。 */
internal enum class NavTier {
    /** 装不下三钮固定件 (10+4+40×3+10=144dp) — 整条导航 GONE, bitmap 全高 (issue#31 2×2 定案) */
    HIDE_NAV,
    /** 满标题「M/D · 周X」+ 刷新按钮 */
    FULL,
    /** 标题去星期「M/D」+ 刷新按钮 */
    SHORT_TITLE,
    /** 标题 GONE — 三钮 (prev/refresh/next) 保留, 2×2 窄档主形态 */
    HIDE_TITLE
}

open class TodayWidgetReceiver : AppWidgetProvider() {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 小组件排版档位 — 基类默认 REGULAR(现有变体); 「今日课程 · 小」子类覆写为 SMALL */
    open val variantHint: WidgetVariant = WidgetVariant.REGULAR

    private fun push(context: Context, awm: AppWidgetManager, id: Int) {
        // 世代号: resize/更新连发时, 后台渲染任务乱序完成会让旧尺寸结果覆盖新内容
        // (覆盖后无后续更新纠正 = 永久 stale)。开渲染前 bump, commit 前校验。
        val gen = WidgetResizeCore.bump(id)
        try {
            pushTodayData(context, awm, id, variantHint, loadDataSync(context, id), this::class.java, gen)
        } catch (e: Throwable) {
            // 渲染失败保留上一份有效 RemoteViews (launcher 端继续显示旧内容), 只记日志
            Log.e(TAG, "push render failed id=$id", e)
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
        for (id in appWidgetIds) {
            WidgetBindingStore.remove(context, id)
            TodayDateNavStore.remove(context, id)
            WidgetResizeCore.remove(id)
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
                    else -> {
                        if (action == ACTION_RESET_DAY) TodayDateNavStore.remove(context, widgetId)
                    }
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
            widgetId: Int, receiverClass: Class<*>, data: WidgetData,
            wDp: Int = 0
        ) {
            // 顶栏背景 — overflow 路径挡住条带上滑内容; 颜色与卡面 bitmap 同一 scheme
            val colors = WidgetBitmapRenderers.todayNavHeaderColors(context, data)
            views.setInt(com.lingion.sleepy.R.id.widget_today_header, "setBackgroundColor", colors.bg)
            // 标题 — 「M/D · 周X」满配, 装不下逐级降级 (2×2 刷新按钮定稿: 回到今天恒为
            // 真实按钮, 恓牲序 = 标题先退 FULL → SHORT_TITLE → HIDE_TITLE → HIDE_NAV)
            val fullTitle = navTitle(data, DateUtils.localizedDay(data.date.dayOfWeek.value, context))
            val dateOnlyTitle = data.dateLabel
            // tier 判定必须知道 refresh 按钮会不会显示 (isToday 时 GONE) —
            // 不为一颗看不见的按钮预算宽度, 窄档上不再无谓牺牲标题 (2026-09-10)
            val tier = navHeaderTier(
                context, fullTitle, dateOnlyTitle, wDp,
                refreshVisible = !data.isToday
            )
            // issue#31 荣耀 2×2 定案: 整条导航装不下 → header GONE (bitmap 全高,
            // 无导航键 — 留着只会被标题压住/挤出界, 点哪都是开 App = 歧义)。
            if (tier == NavTier.HIDE_NAV) {
                views.setViewVisibility(
                    com.lingion.sleepy.R.id.widget_today_header, android.view.View.GONE
                )
                views.setContentDescription(
                    com.lingion.sleepy.R.id.widget_today_header,
                    "header GONE tier=HIDE_NAV w=$wDp"
                )
                // 真实根因 (#31 P1 v1.0.55 真机复测): MagicOS launcher 端把 prev/next
                // 当独立可点击元素保留, 即便父容器 GONE 也接 PendingIntent — 用户看到
                // 两个 < > 形状以为是翻页按钮, 点了无反应 = 困惑 = 看似「箭头还在」。
                // HIDE_NAV 档下把 prev/next 重绑成 tapIntent (开 App), 让 launcher 端
                // 无论是否尊重 GONE, 行为都收敛到「点哪都是开 App」。
                val tap = PendingIntent.getActivity(
                    context, WidgetRoutes.tapRequestCode(widgetId),
                    WidgetRoutes.tapIntent(context),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(
                    com.lingion.sleepy.R.id.widget_today_nav_prev, tap
                )
                views.setOnClickPendingIntent(
                    com.lingion.sleepy.R.id.widget_today_nav_next, tap
                )
                return
            }
            views.setViewVisibility(
                com.lingion.sleepy.R.id.widget_today_header, android.view.View.VISIBLE
            )
            val titleText = if (tier >= NavTier.SHORT_TITLE) dateOnlyTitle else fullTitle
            // HIDE_TITLE 档: 标题 GONE, 三钮 (prev/refresh/next) 保留 — 2×2 主形态,
            // 回到今天恒可点 (2×2 刷新按钮定稿)
            views.setViewVisibility(
                com.lingion.sleepy.R.id.widget_today_nav_title,
                if (tier >= NavTier.HIDE_TITLE) android.view.View.GONE else android.view.View.VISIBLE
            )
            views.setTextViewText(
                com.lingion.sleepy.R.id.widget_today_nav_title,
                titleText
            )
            // 真机取证标签: title 文本 + 档位 + 宽度 — uiautomator content-desc 可读
            views.setContentDescription(
                com.lingion.sleepy.R.id.widget_today_nav_title,
                "title=$titleText tier=$tier w=$wDp"
            )
            views.setTextColor(com.lingion.sleepy.R.id.widget_today_nav_title, colors.title)
            // sp 口径: 与测量端 sp*density*fontScale 同随 fontScale — 旧 DIP 推送
            // 不随系统大字, 测量端却乘 fontScale → 大字档位过度降级 (2026-09-10 一致性)
            views.setTextViewTextSize(
                com.lingion.sleepy.R.id.widget_today_nav_title,
                TypedValue.COMPLEX_UNIT_SP, 13f
            )
            // 刷新按钮 (回到今天) — 2×2 定稿: 「回到今天/今天」文字被窄档裁掉点不了,
            // 换成与 prev/next 同规格的真实按钮 (40×28dp, 视图即点击区), 点击回到今天。
            // isToday (未翻周) 时无回到今天的语义 → GONE (与现状一致)。
            views.setImageViewBitmap(
                com.lingion.sleepy.R.id.widget_today_nav_today,
                WidgetBitmapRenderers.renderNavRefresh(context, data)
            )
            views.setContentDescription(
                com.lingion.sleepy.R.id.widget_today_nav_today,
                "refresh 40x28dp back-to-today"
            )
            views.setViewVisibility(
                com.lingion.sleepy.R.id.widget_today_nav_today,
                if (data.isToday) android.view.View.GONE else android.view.View.VISIBLE
            )
            // 真机取证标签: refresh 可见性原因 — isToday
            views.setContentDescription(
                com.lingion.sleepy.R.id.widget_today_nav_today,
                "refresh gone=${data.isToday} tier=$tier"
            )
            // 三角按钮位图 — 低对比圆角矩形 (surfaceVariant 底 + onSurfaceVariant 图标)
            views.setImageViewBitmap(
                com.lingion.sleepy.R.id.widget_today_nav_prev,
                WidgetBitmapRenderers.renderNavTriangle(context, data, pointLeft = true)
            )
            views.setContentDescription(
                com.lingion.sleepy.R.id.widget_today_nav_prev,
                "prev 40x28dp w=$wDp"
            )
            views.setImageViewBitmap(
                com.lingion.sleepy.R.id.widget_today_nav_next,
            WidgetBitmapRenderers.renderNavTriangle(context, data, pointLeft = false)
            )
            views.setContentDescription(
                com.lingion.sleepy.R.id.widget_today_nav_next,
                "next 40x28dp w=$wDp"
            )
            // 头部容器取证标签: 档位/宽度/日期可见性一站式
            views.setContentDescription(
                com.lingion.sleepy.R.id.widget_today_header,
                "header tier=$tier w=$wDp id=$widgetId"
            )
            // spacer 取证标签 (无图标无绘制也打标 — 用户要求全元素可探测)
            views.setContentDescription(
                com.lingion.sleepy.R.id.widget_spacer_l, "spacerL tier=$tier w=$wDp"
            )
            views.setContentDescription(
                com.lingion.sleepy.R.id.widget_spacer_r, "spacerR tier=$tier w=$wDp"
            )
            // 按钮语义: ‹› 翻天, nav_today = 回到今天 (v5 定案, 翻页语义删除)。
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
         * 顶栏降级判定 — 2×2 刷新按钮定稿 (用户 2026-09-13): 「回到今天」文字在窄档被裁
         * 点不了, 换成真实刷新按钮 (40dp, 恒在导航态) — 恓牲序改为标题先退:
         *   FULL → SHORT_TITLE(去星期) → HIDE_TITLE(标题 GONE, 三钮保留) → HIDE_NAV
         * 2×2 (148dp): req(dateOnly)=173 > 148 → HIDE_TITLE, 三钮固定件 144dp ≤ 148 ✓
         * 4×3 (~250dp): req(full)=215 ≤ 250 → FULL ✓
         *
         * 底座 = prev40 + refresh40 + next40 + padding 20 + margins 14 (spacer weight=1
         * 可压到 0)。标题按实际串测量 (fontScale 感知)。
         */
        internal fun navHeaderTier(
            density: Float, wDp: Int, fullTitle: String, dateOnlyTitle: String,
            titleMeasure: (String) -> Float,
            refreshVisible: Boolean = true
        ): NavTier {
            if (wDp <= 0) return NavTier.FULL  // 未知宽 → 走满配安全路径
            // padStart10 + title + margin4 + prev40 + (6+refresh40+6) + next40 + padEnd10
            fun required(title: String): Float =
                10f + titleMeasure(title) / density + 4f + 40f + (6f + 40f + 6f) + 40f + 10f
            // refresh GONE (isToday) 时预算只含 标题+两钮: GONE 视图连 6+6 margin 一起消失
            fun requiredNoRefresh(title: String): Float =
                10f + titleMeasure(title) / density + 4f + 40f + 40f + 10f
            // 三钮固定件 (标题 GONE): 10 + 4 + 40×3 + 10 = 144dp
            val threeButtonDp = 10f + 4f + 40f + 40f + 40f + 10f
            if (!refreshVisible) {
                if (requiredNoRefresh(fullTitle) <= wDp) return NavTier.FULL
                if (requiredNoRefresh(dateOnlyTitle) <= wDp) return NavTier.SHORT_TITLE
                return NavTier.HIDE_NAV
            }
            if (required(fullTitle) <= wDp) return NavTier.FULL
            if (required(dateOnlyTitle) <= wDp) return NavTier.SHORT_TITLE
            // date-only + 刷新按钮也装不下 → 标题 GONE, 三钮保留 (2×2 主形态:
            // 刷新按钮恒可点, 恓牲的是标题); 三钮也放不下才整条 GONE (issue#31)
            if (threeButtonDp <= wDp) return NavTier.HIDE_TITLE
            return NavTier.HIDE_NAV
        }

        /** Android Context 入口 — 真实 Paint (含 fontScale) + 资源串测量标题。 */
        private fun navHeaderTier(
            context: Context, fullTitle: String, dateOnlyTitle: String, wDp: Int,
            refreshVisible: Boolean
        ): NavTier {
            val density = context.resources.displayMetrics.density
            val fontScale = context.resources.configuration.fontScale
            fun scaledPaint(sp: Float) = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = sp * density * fontScale
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD
                )
            }
            val titlePaint = scaledPaint(13f)
            return navHeaderTier(
                density, wDp, fullTitle, dateOnlyTitle,
                titleMeasure = titlePaint::measureText,
                refreshVisible = refreshVisible
            )
        }

        /**
         * 今日课程推送管线(静态/可滚动闸门) — 网格小最小档与今日课程·小共用,
         * 保证"变成今日课程那个小组件的样子"像素级同源(同一渲染器+同一滚动条带工厂)。
         * 注意 Today 小变体在这里等效直通(REGULAR 也走这条闸), 与改动前行为一致。
         *
         * [receiverClass] = 拥有该 widget 的 AppWidgetProvider 类 (issue #24 Feature2):
         * Today 系 receiver (含子类) → 传自身类, 挂日期导航 (widget_today_nav_static +
         * configureTodayNav, v4 手动翻页无滚动服务);
         * WeekGrid 最小档 → 不传 (默认 null) → 布局与行为与改动前逐字节一致, 不沾导航区。
         */
        fun pushTodayData(
            context: Context, awm: AppWidgetManager, id: Int,
            variant: WidgetVariant, data: WidgetData,
            receiverClass: Class<*>? = null,
            pushGen: Long = 0L
        ) {
            val navEnabled = receiverClass != null &&
                TodayWidgetReceiver::class.java.isAssignableFrom(receiverClass)
            val opts = awm.getAppWidgetOptions(id)
            val (wDp, hDp) = RemoteViewsWidgetHelper.computeSizeDp(opts)
            // 闸门口径: 所有路径统一按带头口径 (headerSpace=false, 与渲染/条带同参) —
            // v9 起 overflow 与静态同一把尺, 不再有 bar 行口径分叉
            val contentH = WidgetBitmapRenderers.todayContentHeightDp(data)
            if (!navEnabled) {
                // WeekGrid 最小档 — 改动前行为逐字节一致 (无导航, 无按钮条)
                if (contentH <= hDp) {
                    RemoteViewsWidgetHelper.renderAndPush(
                        context, awm, id, TAG,
                        loadData = { data },
                        renderBitmap = { d, w, h ->
                            WidgetBitmapRenderers.renderToday(context, d, w, h, variant)
                        },
                        layoutRes = com.lingion.sleepy.R.layout.widget_bitmap_container,
                        pushGen = pushGen
                    )
                } else {
                    // v11: 壳图按全展开 contentH 渲染 (与条带同参, v9.1 契约) —
                    // v9.2 传 hDp 后壳图只画首屏是页面旋转产物, 单 child 长图恢复后
                    // 壳图必须也全展开 (壳图在底层, 只有滚动位 0 可见, 高一点无副作用)
                    val shell = WidgetBitmapRenderers.renderToday(
                        context, data, wDp.toFloat(), contentH, variant
                    )
                    RemoteViewsWidgetHelper.pushScrollable(
                        context, awm, id, TAG,
                        layoutRes = com.lingion.sleepy.R.layout.widget_scroll_today,
                        shellBitmap = shell,
                        scopeExtra = ScrollStripService.StripFactory.SCOPE_TODAY,
                        pushGen = pushGen
                    )
                }
            } else if (contentH <= hDp) {
                // Today 系静态分支 — bitmap(emptyHeader 留白顶栏) + 真实视图顶栏 (issue #24)
                // v8.1 口径: 静态档 bitmap 画 24dp 头部空档 (headerSpace=false), 闸门用
                // contentH(同口径) ≤ hDp — 与渲染逐字节一致
                // issue#31 荣耀 2×2: 顶栏整体装不下 (HIDE_NAV) → bitmap 带头部画满
                // (emptyHeader=false), 无真实视图顶栏, 无导航键 — 点按开 App。
                val navReceiver = receiverClass
                val tierForGate = if (navReceiver != null &&
                    TodayWidgetReceiver::class.java.isAssignableFrom(navReceiver)
                ) navHeaderTier(
                    context, navTitle(data, DateUtils.localizedDay(data.date.dayOfWeek.value, context)),
                    data.dateLabel, wDp,
                    refreshVisible = !data.isToday
                ) else NavTier.HIDE_NAV
                if (tierForGate == NavTier.HIDE_NAV) {
                    RemoteViewsWidgetHelper.renderAndPush(
                        context, awm, id, TAG,
                        loadData = { data },
                        renderBitmap = { d, w, h ->
                            // HIDE_NAV fullface: 顶栏整体 GONE, 无任何按钮 — 「回到今天」
                            // 有交互语义, 点不了就不该出现 (用户 2026-09-13: 要么能点
                            // 要么不存在)。日期标题无交互语义保留。
                            WidgetBitmapRenderers.renderToday(
                                context, d, w, h, variant, showBackToToday = false
                            )
                        },
                        layoutRes = com.lingion.sleepy.R.layout.widget_bitmap_container,
                        pushGen = pushGen
                    )
                    Log.d(TAG, "pushTodayData static-fullface id=$id ${wDp}x${hDp}dp content=$contentH (HIDE_NAV — 顶栏 GONE, #31 定案)")
                    return
                }
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
                // 真机取证标签: 静态 bitmap 尺寸
                views.setContentDescription(
                    com.lingion.sleepy.R.id.widget_bitmap,
                    "static ${wDp}x${hDp}dp content=$contentH id=$id"
                )
                configureTodayNav(context, views, id, receiverClass!!, data, wDp)
                if (WidgetResizeCore.isStale(id, pushGen)) {
                    Log.d(TAG, "skip stale static push id=$id gen=$pushGen")
                    return
                }
                awm.updateAppWidget(id, views)
                Log.d(TAG, "pushTodayData static-nav id=$id ${wDp}x${hDp}dp content=$contentH tier=${navHeaderTier(context, navTitle(data, DateUtils.localizedDay(data.date.dayOfWeek.value, context)), data.dateLabel, wDp, refreshVisible = !data.isToday)}")
            } else {
                // Today 系 overflow v9 (2026-09-10 用户定稿放弃左右切换: 「今日的就不搞
                // 左右切换了…样子就是跟最近两天一样, 就是这个头部和下面一起滚动」):
                //   1:1 抄 TwoDay overflow — 壳图+条带 ListView 双层, bitmap 头部 (日期
                //   标题) 画进长图随内容一起滚, 无独立 bar 行, 无导航键。
                // v6 无壳翻车 (条带异步加载期间整卡透明); v7/v8 的 36dp bar 行 + 去头条带
                // 在真机仍翻车 (巨卡), 一并退场 — 回到同台 OPPO 一直正常的 TwoDay 形态。
                // 条带不带去头标记 (缺省带头) → 长图从头部标题起 = 壳图同参, 滚动位 0
                // 首屏与静态渲染逐像素一致 (与 !navEnabled overflow 分支逐字节同构)。
                // v11: 壳图也按 contentH 渲染 (与 ScrollStripService 条带同参) —
                // 滚动位 0 时壳图与条带首屏逐像素一致; 滚动后壳图被 ListView 覆盖
                // (ListView match_parent 容器), 壳图"多余"的高成为滚动可见区。
                val shell = WidgetBitmapRenderers.renderToday(
                    context, data, wDp.toFloat(), contentH, variant,
                    showBackToToday = false
                )
                RemoteViewsWidgetHelper.pushScrollable(
                    context, awm, id, TAG,
                    layoutRes = com.lingion.sleepy.R.layout.widget_scroll_today,
                    shellBitmap = shell,
                    scopeExtra = ScrollStripService.StripFactory.SCOPE_TODAY,
                    pushGen = pushGen
                )
                Log.d(TAG, "pushTodayData scroll id=$id ${wDp}x${hDp}dp content=$contentH shell=${contentH}dp (v9.1+v11 形态, 单 child 整长图)")
            }
        }

        /**
         * 同步版数据加载 (runBlocking DB 读) — 供 RemoteViews Receiver 使用。
         *
         * [appWidgetId] is plumbed through so a per-widget binding can override
         * the app-wide default table; receivers fall back to
         * [WidgetTableResolver.resolveCurrentTable] when no binding exists.
         *
         * now 只取一次贯穿全程 (nav target 解析 + isToday 同口径): 两次独立 now()
         * 在跨午夜渲染时会把 nav target 算在昨天、isToday 判在今天 = 单次渲染口径分裂。
         */
        fun loadDataSync(context: Context, appWidgetId: Int): WidgetData {
            val now = LocalDate.now()
            return loadDataForDate(
                context, appWidgetId,
                TodayDateNavStore.target(context, appWidgetId, now), now
            )
        }

        /**
         * 指定日期版数据加载 — [target] 由调用方给出 (loadDataSync 传导航锚定日,
         * 翻页卡工厂传卡面日期); [today] 必须同源自调用方 (禁内部再取第二次 now)。
         */
        fun loadDataForDate(
            context: Context, appWidgetId: Int, target: LocalDate, today: LocalDate
        ): WidgetData {
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
