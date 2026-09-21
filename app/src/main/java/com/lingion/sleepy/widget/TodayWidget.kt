package com.lingion.sleepy.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.util.ConflictLayoutEngine
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.util.WeekDisplayResolver
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
            WidgetScrollStore.remove(context, id)
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
         * 今日系底部导航条配置 (2026-09-15 用户定稿, 替代旧顶栏 configureTodayNav) —
         * 位图带头渲染日期标题, 底部 28dp 条为真实视图:
         * 最左「+N」胶囊 (有隐藏课且宽度装得下才显示, 点 → 配置页自救通道) +
         * ◀ 前一天 / 回今天 (仅导航态) / ▶ 后一天。条高恒计入内容预算
         * (computeTodayWindow 扣 NAV_BAR_H_DP) → 条内元素永远放得下,
         * 旧 NavTier 顶栏降级档位整体退场。
         * [receiverClass]=null (WeekGrid 最小档复用管线) → 三钮全 GONE, 只可能显示胶囊。
         */
        fun configureTodayBar(
            context: Context, views: android.widget.RemoteViews,
            widgetId: Int, receiverClass: Class<*>?, data: WidgetData,
            hidden: Int, wDp: Int
        ) {
            val navEnabled = receiverClass != null &&
                TodayWidgetReceiver::class.java.isAssignableFrom(receiverClass)
            // 条背景 = 卡面同色 (与位图 bg 同一 scheme, 无缝)
            val colors = WidgetBitmapRenderers.todayNavHeaderColors(context, data)
            views.setInt(R.id.widget_today_bar, "setBackgroundColor", colors.bg)
            // 「+N」胶囊 (2026-09-14 用户定稿 + 同日 0 值定稿): 恒画 — 有隐藏课 +N,
            // 全部上完/无隐藏课「+0」。长状态行 (今日课程已结束) 整体退场, 0 就是结束
            // 标记; 恒推位图+VISIBLE 也根除 launcher 视图复用残留旧值问题。
            views.setImageViewBitmap(
                R.id.widget_nav_more,
                WidgetBitmapRenderers.renderNavCapsule(
                    context, data, if (hidden > 0) "+$hidden" else "+0"
                )
            )
            views.setViewVisibility(R.id.widget_nav_more, android.view.View.VISIBLE)
            views.setContentDescription(
                R.id.widget_nav_more,
                "capsule hidden=$hidden w=$wDp"
            )
            views.setContentDescription(R.id.widget_spacer_l, "spacerL w=$wDp")
            if (!navEnabled) {
                // WeekGrid 最小档 — 无导航语义, 三钮连 PI 都不挂 (点哪都是开 App)
                for (vid in listOf(
                    R.id.widget_today_nav_prev, R.id.widget_today_nav_today,
                    R.id.widget_today_nav_next
                )) {
                    views.setViewVisibility(vid, android.view.View.GONE)
                }
                return
            }
            // 三角按钮位图 — 低对比圆角矩形 (surfaceVariant 底 + onSurfaceVariant 图标)
            views.setImageViewBitmap(
                R.id.widget_today_nav_prev,
                WidgetBitmapRenderers.renderNavTriangle(context, data, pointLeft = true)
            )
            views.setContentDescription(R.id.widget_today_nav_prev, "prev 40x28dp w=$wDp")
            views.setImageViewBitmap(
                R.id.widget_today_nav_next,
                WidgetBitmapRenderers.renderNavTriangle(context, data, pointLeft = false)
            )
            views.setContentDescription(R.id.widget_today_nav_next, "next 40x28dp w=$wDp")
            // 回今天 (刷新钮) — 「要么能点要么不存在」: isToday 无回今天语义 → GONE
            views.setImageViewBitmap(
                R.id.widget_today_nav_today,
                WidgetBitmapRenderers.renderNavRefresh(context, data)
            )
            views.setViewVisibility(
                R.id.widget_today_nav_today,
                if (data.isToday) android.view.View.GONE else android.view.View.VISIBLE
            )
            views.setContentDescription(
                R.id.widget_today_nav_today, "refresh gone=${data.isToday} w=$wDp"
            )
            // 按钮语义: ◀▶ 翻天, nav_today = 回到今天。同 id 多 PI 必须不同 requestCode。
            val zones = listOf(
                Triple(R.id.widget_today_nav_prev, ACTION_PREV_DAY, 0),
                Triple(R.id.widget_today_nav_next, ACTION_NEXT_DAY, 1),
                Triple(R.id.widget_today_nav_today, ACTION_RESET_DAY, 2)
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

        /**
         * 底部条宽档布局放得下判定 (纯函数, JVM 锁死; 2026-09-14 胶囊恒画改版):
         * pad(10+10) + 胶囊(36 保守估计) + 间隙(4) + 按钮 ≤ wDp。
         * 按钮 = 无导航 0 / 今日态 ◀▶ 80 / 导航态 ◀▶+回今天+边距 132。
         * 放不下 → 换紧凑档 (32×26 钮、零间距), 胶囊两档都恒画 — 只降按钮规格,
         * 不再隐藏胶囊 (用户定稿: 按钮不得盖过「+N」)。wDp≤0 → 保守用宽档。
         */
        internal fun bottomBarWideFits(wDp: Int, isToday: Boolean, navEnabled: Boolean): Boolean {
            if (wDp <= 0) return true
            val buttons = if (!navEnabled) 0f else if (isToday) 80f else 40f + 6f + 40f + 6f + 40f
            return 20f + 36f + 4f + buttons <= wDp.toFloat()
        }

        /** 底部条布局选档: 宽档放得下胶囊+三钮用宽档, 否则紧凑档 (胶囊两档都恒画)。 */
        private fun todayBarLayout(wDp: Int, isToday: Boolean, navEnabled: Boolean): Int =
            if (bottomBarWideFits(wDp, isToday, navEnabled))
                com.lingion.sleepy.R.layout.widget_today_nav_static
            else com.lingion.sleepy.R.layout.widget_today_nav_static_compact

        /**
         * 今日课程推送管线(静态/可滚动闸门) — 网格小最小档与今日课程·小共用,
         * 保证"变成今日课程那个小组件的样子"像素级同源(同一渲染器+同一滚动条带工厂)。
         * 注意 Today 小变体在这里等效直通(REGULAR 也走这条闸), 与改动前行为一致。
         *
         * [receiverClass] = 拥有该 widget 的 AppWidgetProvider 类 (issue #24 Feature2):
         * Today 系 receiver (含子类) → 传自身类, 挂日期导航 (widget_today_nav_static 底部条 +
         * configureTodayBar, 手动翻页无滚动服务);
         * WeekGrid 最小档 → 不传 (默认 null) → 布局与行为与改动前逐字节一致, 不沾导航区。
         */
        fun pushTodayData(
            context: Context, awm: AppWidgetManager, id: Int,
            variant: WidgetVariant, data: WidgetData,
            receiverClass: Class<*>? = null,
            pushGen: Long = 0L,
            forceScroll: Boolean = WidgetScrollStore.isScrollEnabled(context, id)
        ) {
            val navEnabled = receiverClass != null &&
                TodayWidgetReceiver::class.java.isAssignableFrom(receiverClass)
            val opts = awm.getAppWidgetOptions(id)
            val (wDp, hDp) = RemoteViewsWidgetHelper.computeSizeDp(opts)
            // 闸门口径: 所有路径统一按带头口径 (headerSpace=false, 与渲染/条带同参) —
            // v9 起 overflow 与静态同一把尺, 不再有 bar 行口径分叉
            val contentH = WidgetBitmapRenderers.todayContentHeightDp(data)
            // FIXED 窗口 (设计 §4.1, 出厂默认): 溢出不再走滚动, 画「当前时刻起」固定窗;
            // ALL_DONE 公共闸与是否溢出无关 (评审 #4: fits 少课也要显示已结束状态)。
            // forceScroll=实验开关 (设计 §6, 步骤 4 接 AppPrefs), 默认 false。
            val win = if (!forceScroll && data.hasTable &&
                data.semesterStatus == DateUtils.SemesterStatus.IN_RANGE &&
                data.courses.isNotEmpty()
            ) {
                computeTodayWindow(data, hDp.toFloat(), if (data.isToday) currentNowMin() else null)
            } else null
            val winCourses = win?.visible?.flatMap { it.row.courses }
            // 「+N」胶囊计数 (2026-09-15 底部导航条定稿): 隐藏课不再画文字页脚,
            // 窗口填满内容预算 (footerH=0), 底部条恒在且恒放得下。
            val hidden = win?.hiddenAheadCourses ?: 0
            if (!navEnabled) {
                // WeekGrid 最小档 — 无导航按钮, 底部条只可能亮「+N」胶囊 (自救通道)
                if (contentH <= hDp || win != null) {
                    RemoteViewsWidgetHelper.renderAndPush(
                        context, awm, id, TAG,
                        loadData = { data },
                        renderBitmap = { d, w, h ->
                            WidgetBitmapRenderers.renderToday(
                                context, d, w, h, variant,
                                visibleCourses = winCourses
                            )
                        },
                        layoutRes = todayBarLayout(wDp, data.isToday, navEnabled = false),
                        configureViews = { v: android.widget.RemoteViews ->
                            configureTodayBar(context, v, id, null, data, hidden, wDp)
                        },
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
            } else if (contentH <= hDp || win != null) {
                // Today 系静态分支 (2026-09-15 底部导航条定稿) — 位图带头渲染
                // (日期+星期标题画进位图顶部, 不再 emptyHeader 留白), 底部 28dp 真实
                // 视图条: 「+N」胶囊 + ◀/回今天/▶。内容预算恒扣条高 → 装得下几行画几行
                // (旧「预留页脚 → 明明放得下两节只画一节」翻车根除)。
                val shell = WidgetBitmapRenderers.renderToday(
                    context, data, wDp.toFloat(), hDp.toFloat(), variant,
                    showBackToToday = false,
                    visibleCourses = winCourses
                )
                val views = android.widget.RemoteViews(
                    context.packageName,
                    todayBarLayout(wDp, data.isToday, navEnabled = true)
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
                configureTodayBar(context, views, id, receiverClass, data, hidden, wDp)
                if (WidgetResizeCore.isStale(id, pushGen)) {
                    Log.d(TAG, "skip stale static push id=$id gen=$pushGen")
                    return
                }
                awm.updateAppWidget(id, views)
                Log.d(TAG, "pushTodayData static-nav id=$id ${wDp}x${hDp}dp content=$contentH hidden=$hidden")
            } else {
                // Today 系 overflow (滚动实验模式 / 极小尺寸) — 壳图+条带 ListView,
                // 无导航控件。2026-09-15 用户定稿: 翻页键只存在于静态档 (拖大),
                // 缩到无控件的最小尺寸 → 导航态清零强制回今天; 再拖大也停在今天
                // (状态已清, 天然满足「拖回去只显示当天」)。
                var scrollData = data
                if (!data.isToday) {
                    TodayDateNavStore.remove(context, id)
                    scrollData = loadDataSync(context, id)
                }
                val scrollContentH =
                    if (scrollData === data) contentH
                    else WidgetBitmapRenderers.todayContentHeightDp(scrollData)
                val shell = WidgetBitmapRenderers.renderToday(
                    context, scrollData, wDp.toFloat(), scrollContentH, variant,
                    showBackToToday = false
                )
                RemoteViewsWidgetHelper.pushScrollable(
                    context, awm, id, TAG,
                    layoutRes = com.lingion.sleepy.R.layout.widget_scroll_today,
                    shellBitmap = shell,
                    scopeExtra = ScrollStripService.StripFactory.SCOPE_TODAY,
                    pushGen = pushGen
                )
                Log.d(TAG, "pushTodayData scroll id=$id ${wDp}x${hDp}dp content=$scrollContentH forcedToday=${scrollData !== data} (v9.1+v11 形态, 单 child 整长图)")
            }
        }

        /** 当前时刻 (当日分钟);时钟读取独立成函数便于契约测试注入 */
        internal fun currentNowMin(): Int =
            java.time.LocalTime.now().let { it.hour * 60 + it.minute }

        /**
         * Today 系 FIXED 窗口计算 (设计 §4.1):
         * availH = hDp − 内容顶 (pad10+头部预留20=30) − 底部导航条28 = hDp−58;行几何与渲染逐字节同源
         * (TodayRowGeometry.rowSpans = 节点聚类, 与 todayContentHeightDp 同口径)。
         * nowMin=null (非今天/预览) → HEAD 模式;今天 → TIME_WINDOW。
         */
        internal fun computeTodayWindow(
            data: WidgetData,
            hDp: Float,
            nowMin: Int?
        ): FixedWindowCore.WindowResult {
            // 底部导航条恒扣 (2026-09-15 定稿): 条内元素恒放得下, 内容区 = 顶栏下沿 ~ 条上沿
            val availH = hDp - TodayRowGeometry.contentTopDp(false) - TodayRowGeometry.NAV_BAR_H_DP
            val rows = ConflictLayoutEngine.weekLaneRows(data.courses, data.timeJson)
            val entries = FixedWindowCore.entriesOf(rows, data.timeJson) {
                TodayRowGeometry.rowHeightDp(it)
            }
            return FixedWindowCore.window(
                entries = entries,
                availH = availH,
                mode = if (nowMin != null) FixedWindowCore.Mode.TIME_WINDOW
                else FixedWindowCore.Mode.HEAD,
                nowMin = nowMin,
                gapDp = TodayRowGeometry.ROW_GAP_DP,
                // 填满档: 底部条高已在 availH 外恒扣, 窗口不再二次预留 → 隐藏课只点亮胶囊
                footerH = 0f
            )
        }

        /**
         * 页脚条点击 → 配置页 (设计 §6.5 自救通道): 带 id 打开已有绑定 = WidgetEditScreen,
         * 用户在那里开「强制滚动(实验)」。requestCode 独立偏移, 不与点击 PI (RC=widgetId) 撞。
         */
        internal fun footerConfigurePi(context: Context, widgetId: Int): PendingIntent =
            PendingIntent.getActivity(
                context, widgetId + FOOTER_PI_RC_OFFSET,
                Intent(context, WidgetConfigureActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        /** 页脚 PI requestCode 偏移 (点击 PI 用 widgetId, 页脚用 widgetId+此值) */
        private const val FOOTER_PI_RC_OFFSET = 90000

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
            val manualNavigation = TodayDateNavStore.hasNavigation(context, appWidgetId)
            return loadDataForDate(
                context, appWidgetId,
                TodayDateNavStore.target(context, appWidgetId, now), now,
                autoNearestBusyDay = !manualNavigation
            )
        }

        /**
         * 指定日期版数据加载 — [target] 由调用方给出 (loadDataSync 传导航锚定日,
         * 翻页卡工厂传卡面日期); [today] 必须同源自调用方 (禁内部再取第二次 now)。
         */
        fun loadDataForDate(
            context: Context,
            appWidgetId: Int,
            target: LocalDate,
            today: LocalDate,
            autoNearestBusyDay: Boolean = false
        ): WidgetData {
            val isSystemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val isDark = com.lingion.sleepy.util.AppPrefs.isDarkMode(context, isSystemDark)
            val themeKey = com.lingion.sleepy.util.AppPrefs.getThemeKey(context)
            val themeMode = com.lingion.sleepy.util.AppPrefs.getThemeMode(context)
            Log.d("TodayWidget", "DIAG: isDark=$isDark isSystemDark=$isSystemDark themeMode=$themeMode themeKey=$themeKey")
            return try {
                runBlocking {
                    val source = WidgetWeekDataLoader.resolve(appWidgetId)
                    if (source == null) {
                        WidgetData(date = target, courses = emptyList(), timeJson = TimeTableUtils.DEFAULT_TIME_JSON, hasTable = false, isDark = isDark, themeKey = themeKey, isToday = target == today)
                    } else {
                        val table = source.table
                        val effectiveTarget = if (autoNearestBusyDay &&
                            source.display.status == com.lingion.sleepy.util.WeekDisplayStatus.NEAREST_BUSY_DAY
                        ) source.display.targetDate else target
                        val effectiveDayOfWeek = DateUtils.todayDayOfWeek(effectiveTarget)
                        val week = DateUtils.currentWeek(table.startDate, effectiveTarget)
                        val status = DateUtils.semesterStatus(table.startDate, table.maxWeek, effectiveTarget)
                        // 学期外(前/后)不展示课程 — App 今日页同语义, 避免学期前显示"第1周"的课
                        val visible = if (status != DateUtils.SemesterStatus.IN_RANGE) emptyList() else
                            source.coursesFor(effectiveDayOfWeek, week)
                        WidgetData(
                            date = effectiveTarget,
                            courses = visible,
                            timeJson = table.timeJson,
                            hasTable = true,
                            isDark = isDark,
                            themeKey = themeKey,
                            semesterStatus = status,
                            isToday = effectiveTarget == today,
                            weekDisplayStatus = WeekDisplayResolver.statusForSelectedWeek(
                                source.display, week
                            )
                        )
                    }
                }
            } catch (_: Throwable) {
                WidgetData(date = target, courses = emptyList(), timeJson = TimeTableUtils.DEFAULT_TIME_JSON, hasTable = false, isDark = isDark, themeKey = themeKey, isToday = target == today)
            }
        }
    }
}
