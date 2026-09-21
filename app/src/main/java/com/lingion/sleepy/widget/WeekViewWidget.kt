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
 * 桌面 WeekView 小组件 — 本周课表(周视图), 同步 RemoteViews + Canvas。
 * 与 WeekListWidget 布局完全一致(7 列竖排胶囊), 但课程胶囊无彩色填充
 * (surfaceVariant 背景 + onSurfaceVariant 文字), 纯主题色方案。
 */
open class WeekViewWidgetReceiver : AppWidgetProvider() {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 小组件排版档位 — 基类默认 REGULAR(现有变体); 「本周课表（周视图）· 小」子类覆写为 SMALL */
    open val variantHint: WidgetVariant = WidgetVariant.REGULAR

    private fun push(context: Context, awm: AppWidgetManager, id: Int) {
        // 世代闸 (设计 §9.4, 与 Today 同构): resize 连发时旧渲染结果不得覆盖新内容
        val gen = WidgetResizeCore.bump(id)
        // SMALL 变体: compact 分支内部还有 150dp 升档闸, 这里直接传 variant
        val variant = variantHint
        val data = loadDataSync(context, id)
        val opts = awm.getAppWidgetOptions(id)
        val (wDp, hDp) = RemoteViewsWidgetHelper.computeSizeDp(opts)
        // issue#31 荣耀 4×5: 内容超出容器 → pushScrollable (WeekList v1.0.36 同构),
        // 条带全展开长图不裁 5 门; 旧实现无闸 = 永远静态裁切 (显示不完全 + 不能滚)。
        // FIXED 窗口 (设计 §4.3, 出厂默认): 逐列预算截断 + 列底「+N」; 行高与
        // weekViewContentHeightDp 逐字节同源 (wrapMax2Lines 行数 × fontMetrics 行高 + 3dp)。
        // compact 档 (SMALL<150dp) 自有列选取, 不叠窗口。
        val forceScroll = WidgetScrollStore.isScrollEnabled(context, id)
        val compactFace = variant == WidgetVariant.SMALL && wDp < 150
        val visibleDays = com.lingion.sleepy.util.AppPrefs.getVisibleDays(context)
        // 闸门口径 (§9.1/§9.5): forceScroll 比条带全量; 静态脸比实际渲染 —
        // compact 档按今天邻域 ≤3 列, regular 档按 take(5) 封顶 (与渲染器同口径)
        val contentH = if (forceScroll) {
            WidgetBitmapRenderers.weekViewContentHeightDp(context, data, wDp.toFloat())
        } else if (compactFace) {
            WidgetBitmapRenderers.weekViewContentHeightDp(
                context,
                data.copy(days = WidgetBitmapRenderers.compactShownDays(
                    data, visibleDays, LocalDate.now().dayOfWeek.value
                )),
                wDp.toFloat(), maxCoursesPerDay = 5
            )
        } else {
            WidgetBitmapRenderers.weekViewContentHeightDp(
                context, data, wDp.toFloat(), maxCoursesPerDay = 5
            )
        }
        val shownDays = if (visibleDays.isEmpty()) data.days
            else data.days.filter { it.dayOfWeek in visibleDays }.sortedBy { it.dayOfWeek }
        val statusH = if (data.semesterStatus != DateUtils.SemesterStatus.IN_RANGE ||
            data.weekDisplayStatus != com.lingion.sleepy.util.WeekDisplayStatus.NORMAL) 16f else 0f
        val wins = if (!forceScroll && !compactFace && data.hasTable && shownDays.isNotEmpty() &&
            data.days.any { it.courses.isNotEmpty() }
        ) {
            val density = context.resources.displayMetrics.density
            val paint = android.graphics.Paint().apply {
                textSize = 9f * density
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL
                )
            }
            val lineH = (paint.fontMetrics.descent - paint.fontMetrics.ascent) / density
            val colW = (wDp - 12f - 4f * (shownDays.size - 1)) / shownDays.size
            val maxTextWidth = (colW - 8f) * density
            val useAlias = com.lingion.sleepy.util.AppPrefs.isWidgetUseAlias(context)
            computeWeekViewWindows(shownDays, hDp.toFloat(), statusH, lineH) { c ->
                WidgetBitmapRenderers.wrapMax2Lines(
                    com.lingion.sleepy.util.CourseDisplayUtil.displayName(c, useAlias),
                    paint, maxTextWidth
                ).size * lineH + 3f
            }
        } else null
        val visibleByCol = wins?.map { w -> w.visible.flatMap { it.row.courses } }
        val footerByCol = wins?.map { w ->
            if (w.footer) context.getString(R.string.widget_footer_more_short, w.hiddenAheadCourses)
            else null
        }
        if (contentH <= hDp || wins != null) {
            RemoteViewsWidgetHelper.renderAndPush(
                context, awm, id, TAG,
                loadData = { data },
                renderBitmap = { d, w, h ->
                    WidgetBitmapRenderers.renderWeekView(
                        context, d, w, h, variant,
                        visibleByCol = visibleByCol, footerByCol = footerByCol
                    )
                },
                pushGen = gen
            )
        } else {
            // 超出 — 可滚动: 壳图 = 原渲染器按容器尺寸画 (首屏), 条带 = 全展开长图
            val shell = WidgetBitmapRenderers.renderWeekView(context, data, wDp.toFloat(), hDp.toFloat(), variant)
            RemoteViewsWidgetHelper.pushScrollable(
                context, awm, id, TAG,
                layoutRes = com.lingion.sleepy.R.layout.widget_scroll_weeklist,
                shellBitmap = shell,
                scopeExtra = ScrollStripService.StripFactory.SCOPE_WEEKVIEW,
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
        private const val TAG = "WeekViewRV"

        /**
         * WeekView 逐列 FIXED 窗口 (设计 §4.3): HEAD 模式;
         * availH = hDp − 56 − statusH (外pad6×2 + 标题12+14 + chip14+4 + 卡底6);
         * heightOf 由调用方按渲染同源测量 (行数×lineH+3dp 尾 gap), 页脚 = lineH+3。
         */
        internal fun computeWeekViewWindows(
            shownDays: List<DayData>,
            hDp: Float,
            statusH: Float,
            lineH: Float,
            heightOf: (com.lingion.sleepy.data.entity.CourseEntity) -> Float
        ): List<FixedWindowCore.WindowResult> = shownDays.map { day ->
            val availH = hDp - 56f - statusH
            val entries = day.courses.map { c ->
                FixedWindowCore.WindowEntry(
                    com.lingion.sleepy.util.ConflictLayoutEngine.WeekLaneRow(
                        listOf(c), mapOf(c.id to 0), 1
                    ),
                    heightOf(c), null, null
                )
            }
            FixedWindowCore.window(
                entries, availH, FixedWindowCore.Mode.HEAD, null,
                gapDp = 0f, footerH = lineH + 3f
            )
        }

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
                    val source = WidgetWeekDataLoader.resolve(appWidgetId)
                    if (source == null) {
                        WeekData(days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey)
                    } else {
                        val table = source.table
                        val week = source.display.targetWeek
                        val status = source.display.semesterStatus
                        // 学期前: 第 1 周课照常显示(预习); 学期后: 课程清空, renderer 画状态行
                        val days = (1..7).map { dayOfWeek ->
                            val date = source.dateFor(dayOfWeek)
                            val visible = if (status == DateUtils.SemesterStatus.AFTER_END) emptyList() else
                                source.coursesFor(dayOfWeek, week)
                            DayData(date = date, dayOfWeek = dayOfWeek, courses = visible, timeJson = table.timeJson)
                        }
                        // 最小档三天窗口 (2026-09-15 用户令): 真实日期, 上下周打通
                        val compactWindow = WidgetCompactWindow.build(
                            repo, table.id, table.timeJson, table.startDate, table.maxWeek,
                            today, WidgetCompactWindowStore.isTodayFirst(context, appWidgetId),
                            displayWeek = source.display.targetWeek.takeIf {
                                source.display.status == com.lingion.sleepy.util.WeekDisplayStatus.NEAREST_BUSY_DAY
                            }
                        )
                        WeekData(days = days, hasTable = true, isDark = isDark, themeKey = themeKey, semesterStatus = status, compactWindow = compactWindow, weekDisplayStatus = source.display.status)
                    }
                }
            } catch (_: Throwable) {
                WeekData(days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey)
            }
        }
    }
}
