package com.lingion.sleepy.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.lingion.sleepy.R
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
        // 世代闸 (设计 §9.4, 与 Today 同构): resize 连发时旧渲染结果不得覆盖新内容
        val gen = WidgetResizeCore.bump(id)
        val data = loadDataSync(context, id)
        val opts = awm.getAppWidgetOptions(id)
        val (wDp, hDp) = RemoteViewsWidgetHelper.computeSizeDp(opts)
        // SMALL 变体: compact 分支内部还有 150dp 升档闸, 这里直接传 variant
        val variant = variantHint
        // FIXED 窗口 (设计 §4.3, 出厂默认): 逐列预算截断 + 列底「+N」; compact 档
        // (SMALL<150dp) 自有列选取 (今天邻域 ≤3 列), 不叠窗口 (与周视图·小同构)。
        val forceScroll = WidgetScrollStore.isScrollEnabled(context, id)
        val compactFace = variant == WidgetVariant.SMALL && wDp < 150
        val visibleDays = com.lingion.sleepy.util.AppPrefs.getVisibleDays(context)
        // 闸门口径 (§9.1): compact 脸 = 今天邻域 ≤3 列实际列集 (2026-09-14 改版,
        // 与渲染器 renderWeekListCompact 同口径 — 旧两行纯文本脸已废);
        // forceScroll 比条带全量 (regular 口径 = 条带口径, WeekList 无 5 门封顶)
        val contentH = if (!forceScroll && compactFace) {
            WidgetBitmapRenderers.weekListContentHeightDp(
                context,
                data.copy(days = WidgetBitmapRenderers.compactShownDays(
                    data, visibleDays, LocalDate.now().dayOfWeek.value
                ))
            )
        } else WidgetBitmapRenderers.weekListContentHeightDp(context, data)
        val shownDays = if (visibleDays.isEmpty()) data.days
            else data.days.filter { it.dayOfWeek in visibleDays }.sortedBy { it.dayOfWeek }
        val statusH = if (data.semesterStatus != DateUtils.SemesterStatus.IN_RANGE) 16f else 0f
        val wins = if (!forceScroll && !compactFace && data.hasTable && shownDays.isNotEmpty() &&
            data.days.any { it.courses.isNotEmpty() }
        ) computeWeekListWindows(shownDays, hDp.toFloat(), statusH) else null
        val visibleByCol = wins?.map { w -> w.visible.flatMap { it.row.courses } }
        val footerByCol = wins?.map { w ->
            if (w.footer) context.getString(R.string.widget_footer_more_short, w.hiddenAheadCourses)
            else null
        }
        if (contentH <= hDp || wins != null) {
            // 内容装得下 — 原静态路径, 与主分支逐字节一致(REGULAR 时 variant 默认值等价旧调用)
            RemoteViewsWidgetHelper.renderAndPush(
                context, awm, id, TAG,
                loadData = { data },
                renderBitmap = { d, w, h ->
                    WidgetBitmapRenderers.renderWeekList(
                        context, d, w, h, variant,
                        visibleByCol = visibleByCol, footerByCol = footerByCol
                    )
                },
                pushGen = gen
            )
        } else {
            // 超出 — 可滚动: 壳图 = 原渲染器按容器尺寸画(圆角背景+首屏)
            // compact 脸 3 列课多时也会超 → 同 regular 走 scrollable 条带
            val shell = WidgetBitmapRenderers.renderWeekList(context, data, wDp.toFloat(), hDp.toFloat(), variant)
            RemoteViewsWidgetHelper.pushScrollable(
                context, awm, id, TAG,
                layoutRes = com.lingion.sleepy.R.layout.widget_scroll_weeklist,
                shellBitmap = shell,
                scopeExtra = ScrollStripService.StripFactory.SCOPE_WEEKLIST,
                pushGen = gen
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
        for (id in appWidgetIds) { WidgetBindingStore.remove(context, id); WidgetScrollStore.remove(context, id); WidgetCompactWindowStore.remove(context, id) }
    }

    companion object {
        private const val TAG = "WeekListRV"

        /**
         * WeekList 逐列 FIXED 窗口 (设计 §4.3): HEAD 模式 (整周列表无时间锚点);
         * availH = hDp − 58 − statusH (外pad6×2 + 标题12+14 + chip14+6 + 卡底6);
         * 行高 16+3 含尾 gap (渲染器每课后加 3dp, 与列底对齐), 页脚同 19dp。
         */
        internal fun computeWeekListWindows(
            shownDays: List<DayData>,
            hDp: Float,
            statusH: Float
        ): List<FixedWindowCore.WindowResult> = shownDays.map { day ->
            val availH = hDp - 58f - statusH
            val entries = day.courses.map { c ->
                FixedWindowCore.WindowEntry(
                    com.lingion.sleepy.util.ConflictLayoutEngine.WeekLaneRow(
                        listOf(c), mapOf(c.id to 0), 1
                    ),
                    19f, null, null
                )
            }
            FixedWindowCore.window(
                entries, availH, FixedWindowCore.Mode.HEAD, null,
                gapDp = 0f, footerH = 19f
            )
        }

        /**
         * 同步版数据加载 — 7 列日列课程。与 WeekGridWidgetProvider.loadWeekData 结构一致。
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
                runBlocking {
                    val app = SleepyApp.get()
                    val repo = app.repository
                    val table = WidgetTableResolver.resolveBoundTable(appWidgetId)
                        ?: WidgetTableResolver.resolveCurrentTable()
                    if (table == null) {
                        WeekData(days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey)
                    } else {
                        val week = DateUtils.currentWeek(table.startDate, today)
                        val status = DateUtils.semesterStatus(table.startDate, table.maxWeek, today)
                        // 学期前: 钳制周=1, 第 1 周课照常显示(预习); 学期后: 课程清空, renderer 画状态行
                        val days = (1..7).map { dayOfWeek ->
                            val date = DateUtils.dateOfWeekDay(today, dayOfWeek)
                            // issue#44: 取课按调休映射
                            val courseDow = MakeupCourseDayHelper.effectiveDayOfWeek(context, table.id, date)
                            val all = repo.getCoursesByDayOnce(table.id, courseDow)
                            val visible = if (status == DateUtils.SemesterStatus.AFTER_END) emptyList() else
                                all.filter { it.inWeek(week) }.sortedBy { it.startNode }
                            DayData(date = date, dayOfWeek = dayOfWeek, courses = visible, timeJson = table.timeJson)
                        }
                        // 最小档三天窗口 (2026-09-15 用户令): 真实日期, 上下周打通
                        val compactWindow = WidgetCompactWindow.build(
                            context, repo, table.id, table.timeJson, table.startDate, table.maxWeek,
                            today, WidgetCompactWindowStore.isTodayFirst(context, appWidgetId)
                        )
                        WeekData(days = days, hasTable = true, isDark = isDark, themeKey = themeKey, semesterStatus = status, compactWindow = compactWindow)
                    }
                }
            } catch (_: Throwable) {
                WeekData(days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey)
            }
        }
    }
}
