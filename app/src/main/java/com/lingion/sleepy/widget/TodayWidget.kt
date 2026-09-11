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
/** 顶栏降级档位 — 真机窄档 (SIZES=148dp) 实测两字也装不下, 必须再降两级。 */
internal enum class NavTier {
    /** 满标题「M/D · 周X」+「回到今天」 */
    FULL,
    /** 满标题 +「今天」两字 */
    TWO_CHAR,
    /** 标题去星期「M/D」+「今天」两字 */
    SHORT_TITLE,
    /** 标题去星期 + 隐藏 nav_today (prev/next 保留, 核心翻页不丢) */
    HIDE_TODAY
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
            // 标题 — 「M/D · 周X」满配, 装不下逐级降级 (真机 148dp 档实测: 满标题+两字
            // 也溢出 → 去星期; 再不够 → 隐藏回到今天, prev/next 保留)
            val fullTitle = navTitle(data, DateUtils.localizedDay(data.date.dayOfWeek.value, context))
            val dateOnlyTitle = data.dateLabel
            val navTodayFull = context.getString(com.lingion.sleepy.R.string.today_nav_back_to_today)
            val navTodayShort = context.getString(com.lingion.sleepy.R.string.today_nav_today_short)
            // tier 判定必须知道 nav_today 会不会显示 (isToday 时 GONE) —
            // 不为一颗看不见的按钮预算宽度, 窄档上不再无谓牺牲标题 (2026-09-10)
            val tier = navHeaderTier(
                context, fullTitle, dateOnlyTitle, wDp,
                navTodayFull = navTodayFull, navTodayShort = navTodayShort,
                navTodayVisible = !data.isToday
            )
            val titleText = if (tier >= NavTier.SHORT_TITLE) dateOnlyTitle else fullTitle
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
            // 「回到今天」vs「今天」vs 隐藏 — 三级降级逐档真实度量 (无固定 dp 阈值):
            //   真机窄档 (SIZES=148dp) 连两字+满标题都装不下 → 去星期 → 仍不够隐藏,
            //   nav_next 不再被 LinearLayout 横向溢出推出可视区 (2026-09-09 真机根因)。
            val navTodayText = if (tier <= NavTier.TWO_CHAR) {
                context.getString(com.lingion.sleepy.R.string.today_nav_back_to_today)
            } else {
                context.getString(com.lingion.sleepy.R.string.today_nav_today_short)
            }
            views.setTextViewText(
                com.lingion.sleepy.R.id.widget_today_nav_today,
                navTodayText
            )
            views.setTextColor(com.lingion.sleepy.R.id.widget_today_nav_today, colors.action)
            views.setTextViewTextSize(
                com.lingion.sleepy.R.id.widget_today_nav_today,
                TypedValue.COMPLEX_UNIT_SP, 11f
            )
            views.setViewVisibility(
                com.lingion.sleepy.R.id.widget_today_nav_today,
                if (data.isToday || tier >= NavTier.HIDE_TODAY)
                    android.view.View.GONE else android.view.View.VISIBLE
            )
            // 真机取证标签: nav_today 可见性原因 — isToday or tier
            views.setContentDescription(
                com.lingion.sleepy.R.id.widget_today_nav_today,
                "navtoday gone=${data.isToday || tier >= NavTier.HIDE_TODAY} isToday=${data.isToday} tier=$tier"
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
         * 当前 widget 宽度下, 顶栏装得下"回到今天"四字吗 — 装不下用"今天"两字,
         * 避免 nav_next 被 LinearLayout 挤到下面单独成行(launcher 列宽 ≤ 2 时复现)。
         *
         * 顶栏 LinearLayout 横向累加(单位 dp):
         *   padStart 10 + titleText(实际) + marginStart 4 + nav_prev 40 + spacer 0(weight=1,可压到0)
         *   + nav_today text + marginStart 6 + marginEnd 6 + spacer 0(weight=1) + nav_next 40 + padEnd 10
         * spacer 是 weight=1 弹性项, 剩余空间不够时压缩到 0dp — 因此只要"标题 + 两钮 + 今日 + margin
         * + padding" 总和 ≤ widget 宽, LinearLayout 就能正常排(两个 spacer 平分剩余空间)。
         * 反之总和 > widget 宽, nav_next 会被外推成第二行的巨 view。
         *
         * 阈值不硬编码: 标题与 nav_today 文案均按实际串测量 — 测量闭包与文案由调用方
         * 注入, Android 入口传 context.getString 资源串 + 真实 Paint; sp 文本跟随系统
         * 字体缩放 (fontScale), Paint 构造 textSize 必须乘 fontScale, 否则大字体设备
         * 实测比测量宽 → 漏判 → nav_next 再被挤。
         * 纯函数 + 测量闭包注入: 不依赖 Android framework, 单测可 JVM 断言边界。
         */
        internal fun fitsNavTodayFourChar(
            density: Float, wDp: Int, titleText: String,
            navTodayFull: String, navTodayShort: String = navTodayFull,
            titleMeasure: (String) -> Float, navTodayMeasure: (String) -> Float
        ): Boolean {
            if (wDp <= 0) return true  // 未知宽 → 走四字安全路径
            // 文本宽转 dp: measure 返回 px, 除以 density
            val titleW = titleMeasure(titleText) / density
            // 四字档文案 = 资源注入串 (locale 对齐渲染文本)
            val backToTodayW = navTodayMeasure(navTodayFull) / density
            // 累加: padStart + title + marginStart + nav_prev + nav_today(margin+text) + nav_next + padEnd
            // spacer(weight=1) 可压到 0 → 不计入"最小必要宽度"
            val requiredDp = 10f + titleW + 4f + 40f + (6f + backToTodayW + 6f) + 40f + 10f
            return requiredDp <= wDp.toFloat()
        }

        /** Android Context 入口 — 构造真实 Paint (含 fontScale) 注入纯函数。 */
        private fun fitsNavTodayFourChar(
            context: Context, titleText: String, wDp: Int
        ): Boolean {
            val density = context.resources.displayMetrics.density
            val fontScale = context.resources.configuration.fontScale
            fun scaledPaint(sp: Float) = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = sp * density * fontScale  // sp = dp × fontScale
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD
                )
            }
            val titlePaint = scaledPaint(13f)   // nav_title 是 13sp
            val navTodayPaint = scaledPaint(11f)  // nav_today 是 11sp
            return fitsNavTodayFourChar(
                density, wDp, titleText,
                navTodayFull = context.getString(com.lingion.sleepy.R.string.today_nav_back_to_today),
                navTodayShort = context.getString(com.lingion.sleepy.R.string.today_nav_today_short),
                titleMeasure = titlePaint::measureText,
                navTodayMeasure = navTodayPaint::measureText
            )
        }

        /**
         * 顶栏三级降级判定 — 逐档真实度量, 全程无固定 dp 阈值。
         *
         * 真机取证 (OPPO PKX110, 2026-09-09): 148dp 档上「9/8 · 周二」+两钮+「今天」
         * 固定件 ≈187dp > 148dp → LinearLayout 横向溢出, nav_next 被推出可视区
         * (uiautomator 层级中消失)。旧判定只到"两字"档, 装不下时无路可退。
         *
         * 降级序 (恓牲度递增, 与用户定稿一致: 先去星期, 不到万不得已不隐藏功能):
         *   FULL → TWO_CHAR → SHORT_TITLE(去星期) → HIDE_TODAY(隐藏回到今天)
         * 每档按当档文案真实测量; prev/next 按钮 40dp×2 + padding 20dp + margins
         * 10dp 是不可压缩底座 (spacer weight=1 可压到 0)。
         */
        internal fun navHeaderTier(
            density: Float, wDp: Int, fullTitle: String, dateOnlyTitle: String,
            navTodayFull: String, navTodayShort: String,
            titleMeasure: (String) -> Float, navTodayMeasure: (String) -> Float,
            navTodayVisible: Boolean = true
        ): NavTier {
            if (wDp <= 0) return NavTier.FULL  // 未知宽 → 走满配安全路径
            fun required(title: String, navTodayText: String): Float {
                val titleW = titleMeasure(title) / density
                val navW = navTodayMeasure(navTodayText) / density
                // padStart10 + title + margin4 + prev40 + (6+navToday+6) + next40 + padEnd10
                return 10f + titleW + 4f + 40f + (6f + navW + 6f) + 40f + 10f
            }
            // nav_today 不显示 (isToday) 时预算只含标题+两钮: padStart10 + title +
            // margin4 + prev40 + next40 + padEnd10 (GONE 视图连 6+6 margin 一起消失)
            // — 不为一颗看不见的按钮预算宽度 (2026-09-10)
            fun requiredNoToday(title: String): Float =
                10f + titleMeasure(title) / density + 4f + 40f + 40f + 10f
            if (!navTodayVisible) {
                if (requiredNoToday(fullTitle) <= wDp) return NavTier.FULL
                if (requiredNoToday(dateOnlyTitle) <= wDp) return NavTier.SHORT_TITLE
                return NavTier.HIDE_TODAY
            }
            if (required(fullTitle, navTodayFull) <= wDp) return NavTier.FULL
            if (required(fullTitle, navTodayShort) <= wDp) return NavTier.TWO_CHAR
            if (required(dateOnlyTitle, navTodayShort) <= wDp) return NavTier.SHORT_TITLE
            return NavTier.HIDE_TODAY
        }

        /** Android Context 入口 — 真实 Paint (含 fontScale) + 资源串注入纯函数判定档位。 */
        private fun navHeaderTier(
            context: Context, fullTitle: String, dateOnlyTitle: String, wDp: Int,
            navTodayFull: String, navTodayShort: String, navTodayVisible: Boolean
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
            val navTodayPaint = scaledPaint(11f)
            return navHeaderTier(
                density, wDp, fullTitle, dateOnlyTitle,
                navTodayFull = navTodayFull, navTodayShort = navTodayShort,
                navTodayVisible = navTodayVisible,
                titleMeasure = titlePaint::measureText,
                navTodayMeasure = navTodayPaint::measureText
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
                    val shell = WidgetBitmapRenderers.renderToday(
                        context, data, wDp.toFloat(), hDp.toFloat(), variant
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
                Log.d(TAG, "pushTodayData static-nav id=$id ${wDp}x${hDp}dp content=$contentH tier=${navHeaderTier(context, navTitle(data, DateUtils.localizedDay(data.date.dayOfWeek.value, context)), data.dateLabel, wDp, navTodayFull = context.getString(com.lingion.sleepy.R.string.today_nav_back_to_today), navTodayShort = context.getString(com.lingion.sleepy.R.string.today_nav_today_short), navTodayVisible = !data.isToday)}")
            } else {
                // Today 系 overflow v9 (2026-09-10 用户定稿放弃左右切换: 「今日的就不搞
                // 左右切换了…样子就是跟最近两天一样, 就是这个头部和下面一起滚动」):
                //   1:1 抄 TwoDay overflow — 壳图+条带 ListView 双层, bitmap 头部 (日期
                //   标题) 画进长图随内容一起滚, 无独立 bar 行, 无导航键。
                // v6 无壳翻车 (条带异步加载期间整卡透明); v7/v8 的 36dp bar 行 + 去头条带
                // 在真机仍翻车 (巨卡), 一并退场 — 回到同台 OPPO 一直正常的 TwoDay 形态。
                // 条带不带去头标记 (缺省带头) → 长图从头部标题起 = 壳图同参, 滚动位 0
                // 首屏与静态渲染逐像素一致 (与 !navEnabled overflow 分支逐字节同构)。
                val shell = WidgetBitmapRenderers.renderToday(
                    context, data, wDp.toFloat(), hDp.toFloat(), variant
                )
                RemoteViewsWidgetHelper.pushScrollable(
                    context, awm, id, TAG,
                    layoutRes = com.lingion.sleepy.R.layout.widget_scroll_today,
                    shellBitmap = shell,
                    scopeExtra = ScrollStripService.StripFactory.SCOPE_TODAY,
                    pushGen = pushGen
                )
                Log.d(TAG, "pushTodayData scroll id=$id ${wDp}x${hDp}dp content=$contentH shell=${contentH}dp (v9.1 TwoDay同构, 壳图按全展开)")
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
