package com.lingion.sleepy.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import com.lingion.sleepy.MainActivity
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 周视图小组件（网格样式）— 7 列网格，课程按节次定位
 *
 * v2 关键改动：
 * — 通过 AppWidgetManager.getAppWidgetOptions 读 widget 实际尺寸
 * — 按 (widget实际高度 - 表头 - padding) / maxNode 动态算每节高度 perNodeHeight
 * — 跨节课程用 perNodeHeight * step 真实跨节合并，不再被 defaultWeight 压扁
 */
class WeekGridWidget : GlanceAppWidget() {

    /** ★ 外部直接注入尺寸（WidgetRenderActivity 用，绕过 AppWidgetManager 默认 options） */
    companion object {
        var overrideSizeDp: Pair<Int, Int>? = null  // (width, height)
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { loadWeekData(context) }

        // 读 widget 实际尺寸
        var widgetWidthDp: Int
        var widgetHeightDp: Int

        if (overrideSizeDp != null) {
            widgetWidthDp = overrideSizeDp!!.first
            widgetHeightDp = overrideSizeDp!!.second
            Log.d("WeekGridWidget", "using overrideSize ${widgetWidthDp}x${widgetHeightDp}")
        } else {
            val awm = AppWidgetManager.getInstance(context)
            val glanceMgr = GlanceAppWidgetManager(context)
            val widgetId = glanceMgr.getAppWidgetId(id)
            val options = awm.getAppWidgetOptions(widgetId)
            val density = context.resources.displayMetrics.density
            val maxHeightPx = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                .takeIf { it > 0 }
                ?: options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            val maxWidthPx = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
                .takeIf { it > 0 }
                ?: options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            widgetHeightDp = if (maxHeightPx > 0) (maxHeightPx / density).toInt() else 280
            widgetWidthDp = if (maxWidthPx > 0) (maxWidthPx / density).toInt() else 320
        }

        // ★ FIX: maxNode 替代 maxSumStep — 20 节课只显示 9 节的真 bug
        val maxNode = data.days.maxOfOrNull { d ->
            d.courses.maxOfOrNull { it.startNode + it.step - 1 } ?: 0
        }?.coerceIn(4, 10) ?: 4

        Log.d("WeekGridWidget", "widgetSize=${widgetWidthDp}x${widgetHeightDp}dp, maxNode=$maxNode, hasTable=${data.hasTable}")

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        provideContent {
            WeekGridContent(
                data = data,
                openAppAction = actionStartActivity(openAppIntent),
                widgetWidthDp = widgetWidthDp,
                widgetHeightDp = widgetHeightDp
            )
        }
    }

    internal suspend fun loadWeekData(context: Context): WeekData {
        val today = LocalDate.now()
        val isDark = AppPrefs.isDarkMode(context)
        val themeKey = AppPrefs.getThemeKey(context)
        val displayMode = AppPrefs.getDisplayMode(context)
        val showDate = AppPrefs.isShowDate(context)
        val visibleDays = AppPrefs.getVisibleDays(context)
        return try {
            val app = SleepyApp.get()
            val repo = app.repository
            val table = repo.getDefaultTable()
            if (table == null) {
                Log.w("WeekGridWidget", "loadWeekData: no default table")
                WeekData(days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey,
                    displayMode = displayMode, showDate = showDate, visibleDays = visibleDays)
            } else {
                val week = DateUtils.currentWeek(table.startDate, today)
                val days = visibleDays.map { dayOfWeek ->
                    val date = DateUtils.dateOfWeekDay(today, dayOfWeek)
                    val all = repo.getCoursesByDayOnce(table.id, dayOfWeek)
                    val visible = all.filter { it.inWeek(week) }.sortedBy { it.startNode }
                    DayData(date = date, dayOfWeek = dayOfWeek, courses = visible, timeJson = table.timeJson)
                }
                Log.d("WeekGridWidget", "loadWeekData: table=${table.id}, week=$week, totalCourses=${days.sumOf { it.courses.size }}, visibleDays=$visibleDays")
                WeekData(days = days, hasTable = true, isDark = isDark, themeKey = themeKey,
                    displayMode = displayMode, showDate = showDate, visibleDays = visibleDays)
            }
        } catch (e: Throwable) {
            Log.e("WeekGridWidget", "loadWeekData failed", e)
            WeekData(days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey,
                displayMode = displayMode, showDate = showDate, visibleDays = visibleDays)
        }
    }
}

/**
 * 按 widget 实际高度算每节高度。
 * 减表头 30dp / padding 12dp / maxNode。
 * 含义：maxNode 个 step 总高 = widget 可用高，每 step 高度 = perNodeHeight。
 * ★ 自动缩放：真分数除法，不 snap — 让 grid 真撑满 widget
 */
internal fun computePerNodeHeight(widgetHeightDp: Int, maxNode: Int): Dp {
    val headerH = 30
    val padding = 12
    val stepCount = maxNode.coerceAtLeast(1)
    val available = (widgetHeightDp - headerH - padding).coerceAtLeast(40)
    val perNode = available.toFloat() / stepCount
    return perNode.dp
}

class WeekGridWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekGridWidget()
}