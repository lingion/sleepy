package com.lingion.sleepy.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
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
 * 桌面 TwoDay 小组件 — 同步 RemoteViews + Canvas (v1.0.29 起, 从 Glance 移植)。
 * 原因见 [TodayWidgetReceiver] 注释。
 *
 * v1.0.36: 内容装得下走静态 renderAndPush; 超出走 pushScrollable(壳图+条带)。
 *
 * Glance 版 TwoDayWidget 类已删除(决策 D5-11); loadDataSync 自 Glance companion 迁入本类。
 */
open class TwoDayWidgetReceiver : AppWidgetProvider() {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 小组件排版档位 — 基类默认 REGULAR(现有变体); 「最近两天 · 小」子类覆写为 SMALL */
    open val variantHint: WidgetVariant = WidgetVariant.REGULAR

    private fun push(context: Context, awm: AppWidgetManager, id: Int) {
        // 世代闸 (设计 §9.4, 与 Today 同构): resize 连发时旧渲染结果不得覆盖新内容
        val gen = WidgetResizeCore.bump(id)
        val data = loadDataSync(context, id)
        val opts = awm.getAppWidgetOptions(id)
        val (wDp, hDp) = RemoteViewsWidgetHelper.computeSizeDp(opts)
        val contentH = WidgetBitmapRenderers.twoDayContentHeightDp(data)
        // SMALL 变体: compact 分支内部还有 150dp 升档闸, 这里直接传 variant
        val variant = variantHint
        // FIXED 窗口 (设计 §4.2, 出厂默认): 每列独立窗口 — 今天列 TIME_WINDOW,
        // 明天列 HEAD; 页脚合并为一条 (hiddenAhead 求和)。forceScroll=实验开关 (步骤 4)。
        val forceScroll = WidgetScrollStore.isScrollEnabled(context, id)
        val wins = if (!forceScroll && data.hasTable &&
            data.semesterStatus == DateUtils.SemesterStatus.IN_RANGE &&
            data.days.any { it.courses.isNotEmpty() }
        ) {
            computeTwoDayWindows(data, hDp.toFloat(), TodayWidgetReceiver.currentNowMin())
        } else null
        val visibleByCol = wins?.map { w -> w.visible.flatMap { it.row.courses } }
        // 每列独立「+N」(2026-09-14 用户定稿 + 同日 0 值定稿): 今天/明天各说各的,
        // 恒画 — 上完/没课列显示「+0」; 合并求和禁回流。「已结束」长状态行退场,
        // statusByCol 仅作 ALL_DONE 列抑制「无课程」文案的标记 (不画字)。
        val footerTexts = wins?.map { w ->
            context.getString(R.string.widget_footer_more_short, w.hiddenAheadCourses)
        }
        val statusByCol = wins?.map { w ->
            if (w.status == FixedWindowCore.Status.ALL_DONE) "" else null
        }
        if (contentH <= hDp || wins != null) {
            RemoteViewsWidgetHelper.renderAndPush(
                context, awm, id, TAG,
                loadData = { data },
                renderBitmap = { d, w, h ->
                    WidgetBitmapRenderers.renderTwoDay(
                        context, d, w, h, variant,
                        visibleByCol = visibleByCol,
                        footerTexts = footerTexts, statusByCol = statusByCol
                    )
                },
                layoutRes = if (footerTexts != null)
                    com.lingion.sleepy.R.layout.widget_bitmap_footer
                else com.lingion.sleepy.R.layout.widget_bitmap_container,
                configureViews = footerConfigureViews(context, id, footerTexts),
                pushGen = gen
            )
        } else {
            val shell = WidgetBitmapRenderers.renderTwoDay(context, data, wDp.toFloat(), hDp.toFloat(), variant)
            RemoteViewsWidgetHelper.pushScrollable(
                context, awm, id, TAG,
                layoutRes = com.lingion.sleepy.R.layout.widget_scroll_twoday,
                shellBitmap = shell,
                scopeExtra = ScrollStripService.StripFactory.SCOPE_TWODAY,
                pushGen = gen
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetVendorActions.XIAOMI_UPDATE_ACTION) {
            WidgetVendorActions.dispatchXiaomiUpdate(this, context, intent)
        } else {
            super.onReceive(context, intent)
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
        for (id in appWidgetIds) { WidgetBindingStore.remove(context, id); WidgetScrollStore.remove(context, id) }
    }

    companion object {
        private const val TAG = "TwoDayRV"

        /** 页脚条点击 PI 挂接 (任一列有「+N」才挂); 与 Today 系同一自救通道 */
        internal fun footerConfigureViews(
            context: Context, widgetId: Int, footerTexts: List<String?>?
        ): ((android.widget.RemoteViews) -> Unit)? {
            if (footerTexts == null) return null
            val pi = TodayWidgetReceiver.footerConfigurePi(context, widgetId)
            return { v: android.widget.RemoteViews ->
                v.setOnClickPendingIntent(com.lingion.sleepy.R.id.widget_footer_bar, pi)
            }
        }

        /**
         * TwoDay 每列 FIXED 窗口 (设计 §4.2): availH = hDp − 44 (pad12+列头20+pad12,
         * 2026-09-14c 顶部标签行删除); 行高/聚类与 twoDayContentHeightDp·renderTwoDayRegular
         * 逐字节同源 (timeJson 聚类, 单行 36, 堆叠 maxStack×36+(maxStack−1)×3, 行距 6)。
         * 今天列 TIME_WINDOW(nowMin), 明天列 HEAD。
         */
        internal fun computeTwoDayWindows(
            data: TwoDayData,
            hDp: Float,
            nowMin: Int?
        ): List<FixedWindowCore.WindowResult> = data.days.map { day ->
            val availH = hDp - 44f
            val rows = com.lingion.sleepy.util.ConflictLayoutEngine.weekLaneRows(day.courses, day.timeJson)
            val entries = FixedWindowCore.entriesOf(rows, day.timeJson) { row ->
                if (row.laneCount == 1) 36f
                else {
                    val maxStack = row.courses.groupBy { row.laneOf[it.id] }.values
                        .maxOf { it.size }.coerceAtLeast(1)
                    maxStack * 36f + (maxStack - 1) * 3f
                }
            }
            FixedWindowCore.window(
                entries = entries,
                availH = availH,
                mode = if (day.isToday && nowMin != null) FixedWindowCore.Mode.TIME_WINDOW
                else FixedWindowCore.Mode.HEAD,
                nowMin = if (day.isToday) nowMin else null,
                gapDp = 6f
            )
        }

        /**
         * 同步版数据加载 — 今天 + 明天课程。
         *
         * [appWidgetId] is plumbed through so a per-widget binding can override
         * the app-wide default table; receivers fall back to
         * [WidgetTableResolver.resolveCurrentTable] when no binding exists.
         */
        fun loadDataSync(context: Context, appWidgetId: Int): TwoDayData {
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)
            val isSystemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val isDark = com.lingion.sleepy.util.AppPrefs.isDarkMode(context, isSystemDark)
            val themeKey = com.lingion.sleepy.util.AppPrefs.getThemeKey(context)
            return try {
                runBlocking {
                    val source = WidgetWeekDataLoader.resolve(appWidgetId)
                    if (source == null) {
                        TwoDayData(days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey)
                    } else {
                        val table = source.table
                        val dates = if (source.display.status == com.lingion.sleepy.util.WeekDisplayStatus.NEAREST_BUSY_DAY) {
                            listOf(source.display.targetDate, source.display.targetDate.plusDays(1))
                        } else listOf(today, tomorrow)
                        val status = DateUtils.semesterStatus(table.startDate, table.maxWeek, dates.first())
                        val days = dates.map { date ->
                            val week = DateUtils.currentWeek(table.startDate, date)
                            val dow = HolidayTransferHelper.effectiveDayOfWeek(context, table.id, date)
                            val courses = if (status != DateUtils.SemesterStatus.IN_RANGE) emptyList()
                                else source.coursesFor(dow, week)
                            DayData(date = date, dayOfWeek = dow, courses = courses, timeJson = table.timeJson)
                        }
                        TwoDayData(
                            days = days,
                            hasTable = true,
                            isDark = isDark,
                            themeKey = themeKey,
                            semesterStatus = status,
                            weekDisplayStatus = source.display.status
                        )
                    }
                }
            } catch (_: Throwable) {
                TwoDayData(days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey)
            }
        }
    }
}
