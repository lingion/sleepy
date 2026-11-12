package com.lingion.sleepy.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.util.AppClock
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.HolidayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Shared rendering for the two fixed 4x2 components; this base is not registered. */
abstract class WeekGridWideWidgetProvider : AppWidgetProvider() {
    abstract val layoutMode: WeekGridWideMode
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WidgetVendorActions.XIAOMI_UPDATE_ACTION -> WidgetVendorActions.dispatchXiaomiUpdate(this, context, intent)
            AppWidgetManager.ACTION_APPWIDGET_DELETED -> {
                val pending = goAsync()
                try {
                    super.onReceive(context, intent)
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        try { WidgetBoundaryScheduler.armNext(context) } finally { pending?.finish() }
                    }
                } catch (t: Throwable) {
                    pending?.finish()
                    throw t
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = update(context, manager, ids)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) =
        update(context, manager, intArrayOf(id))

    private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                for (id in ids) try {
                    val settings = WeekGridWideSettingsStore.read(context, id).copy(mode = layoutMode)
                    val now = LocalDateTime.now(AppClock)
                    val data = loadData(context, id, settings, now)
                    val (w, h) = RemoteViewsWidgetHelper.computeSizeDp(manager.getAppWidgetOptions(id))
                    val density = context.resources.displayMetrics.density
                    val bitmap = WeekGridWideRenderer.renderBitmap(context, data, settings, now,
                        (w * density).toInt().coerceAtLeast(1), (h * density).toInt().coerceAtLeast(1))
                    val views = RemoteViews(context.packageName, R.layout.widget_bitmap_container)
                    views.setImageViewBitmap(R.id.widget_bitmap, bitmap)
                    views.setOnClickPendingIntent(R.id.widget_bitmap, PendingIntent.getActivity(context,
                        WidgetRoutes.tapRequestCode(id), WidgetRoutes.tapIntent(context),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                    manager.updateAppWidget(id, views)
                    // RemoteViews owns the bitmap payload; do not recycle it here.
                } catch (e: Exception) { Log.e("WeekGridWide", "render failed: $id", e) }
                WidgetBoundaryScheduler.armNext(context)
                WidgetUpdater.schedule(context)
                WidgetUpdater.armMidnightRefresh(context)
            } finally { pending?.finish() }
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        for (id in ids) {
            WidgetBindingStore.remove(context, id)
            WeekGridWideSettingsStore.remove(context, id)
        }
        super.onDeleted(context, ids)
    }

    companion object {
        fun modeForReceiverName(name: String?): WeekGridWideMode? = when (name?.substringAfterLast('.')) {
            "WeekGridVerticalWidgetProvider" -> WeekGridWideMode.VERTICAL
            "WeekGridHorizontalWidgetProvider" -> WeekGridWideMode.HORIZONTAL
            else -> null
        }

        internal fun coursesForDate(source: WidgetWeekSource, date: java.time.LocalDate): List<com.lingion.sleepy.data.entity.CourseEntity> {
            val actual = com.lingion.sleepy.util.HolidayRangeOps.HolidayTransferOps.coursesForDate(
                date, source.table.startDate, source.table.maxWeek, source.courses, source.transfers)
            val preview = if (source.transfers.none { it.sourceDate == date || it.targetDate == date } &&
                com.lingion.sleepy.util.DateUtils.semesterStatus(source.table.startDate, source.table.maxWeek, date) ==
                com.lingion.sleepy.util.DateUtils.SemesterStatus.BEFORE_START) {
                source.courses.filter { it.fixedDate == null && it.day == date.dayOfWeek.value && it.inWeek(1) }
            } else emptyList()
            return (actual + preview).sortedBy { it.startNode }
        }

        internal suspend fun loadData(context: Context, id: Int, settings: WeekGridWideSettings,
            now: LocalDateTime = LocalDateTime.now(AppClock)): WeekData {
            val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            val dark = AppPrefs.isDarkMode(context, systemDark)
            val theme = AppPrefs.getThemeKey(context)
            val source = WidgetWeekDataLoader.resolve(id, now)
                ?: return WeekData(emptyList(), false, isDark = dark, themeKey = theme)
            return dataForSource(context, source, settings.mode, now, dark, theme)
        }

        /** Shared date/data policy for the receivers and their instance previews. */
        internal suspend fun dataForSource(context: Context, source: WidgetWeekSource?, mode: WeekGridWideMode,
            now: LocalDateTime, dark: Boolean, theme: String): WeekData {
            if (source == null) return WeekData(emptyList(), false, isDark = dark, themeKey = theme)
            val dates = WeekGridWideWindowCore.visibleDates(now.toLocalDate(), mode)
            val days = dates.map { date -> DayData(date, date.dayOfWeek.value,
                coursesForDate(source, date), source.table.timeJson,
                HolidayManager.shouldGrey(context, date, source.table.id)) }
            return WeekData(days, true, isDark = dark, themeKey = theme,
                showDate = AppPrefs.isShowDate(context), visibleDays = dates.map { it.dayOfWeek.value }.toSet(),
                semesterStatus = if (days.any { it.courses.isNotEmpty() } &&
                    source.display.semesterStatus == com.lingion.sleepy.util.DateUtils.SemesterStatus.AFTER_END)
                    com.lingion.sleepy.util.DateUtils.SemesterStatus.IN_RANGE else source.display.semesterStatus)
        }
    }
}
