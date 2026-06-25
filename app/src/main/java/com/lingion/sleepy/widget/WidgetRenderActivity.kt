package com.lingion.sleepy.widget

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 调试用 Activity：渲染 4 个桌面 Widget 样式到屏幕，并保存为 PNG 用于 README 截图。
 *
 * 通过 Intent extra 指定要渲染哪个 widget：
 *  - `widget=today` (180x110 dp)
 *  - `widget=twoday` (240x140 dp)
 *  - `widget=weeklist` (280x160 dp)
 *  - `widget=weekgrid` (250x640 dp)
 *  - 缺省 = weekgrid
 *
 * 真实数据来源：当前课表（表 1 = 2026 春学期，HEU 13 节真实课表）。
 */
class WidgetRenderActivity : Activity() {

    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val which = intent.getStringExtra("widget") ?: "weekgrid"
        val (wDp, hDp) = when (which) {
            "today" -> 180f to 110f
            "twoday" -> 240f to 140f
            "weeklist" -> 280f to 160f
            else -> 250f to 640f
        }
        Log.d(TAG, "rendering widget=$which, size=${wDp}x${hDp}dp")

        // FrameLayout: 居中 ImageView 展示 widget bitmap
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF1A1A2E.toInt())
        }
        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Widget Preview"
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply {
            setMargins(0, 0, 0, 0)
        }
        root.addView(img, params)
        setContentView(root)

        scope.launch {
            try {
                val bmp = renderWidgetBitmap(which, wDp, hDp)
                img.setImageBitmap(bmp)
            } catch (e: Throwable) {
                Log.e(TAG, "render failed", e)
                img.setBackgroundColor(Color.RED)
            }
        }
    }

    private suspend fun renderWidgetBitmap(which: String, wDp: Float, hDp: Float) =
        when (which) {
            "today" -> {
                val today = java.time.LocalDate.now()
                val dayOfWeek = com.lingion.sleepy.util.DateUtils.todayDayOfWeek(today)
                val table = WidgetTableResolver.resolveCurrentTable()
                val courses = if (table != null) {
                    val week = com.lingion.sleepy.util.DateUtils.currentWeek(table.startDate, today)
                    val all = SleepyApp.get().repository.getCoursesByDayOnce(table.id, dayOfWeek)
                    all.filter { it.inWeek(week) }.sortedBy { it.startNode }
                } else emptyList()
                WidgetBitmapRenderers.renderToday(
                    this,
                    WidgetData(
                        date = today,
                        courses = courses,
                        timeJson = table?.timeJson ?: TimeTableUtils.DEFAULT_TIME_JSON,
                        hasTable = table != null,
                        isDark = com.lingion.sleepy.util.AppPrefs.isDarkMode(this),
                        themeKey = com.lingion.sleepy.util.AppPrefs.getThemeKey(this)
                    ),
                    wDp, hDp
                )
            }
            "twoday" -> {
                val today = java.time.LocalDate.now()
                val tomorrow = today.plusDays(1)
                val table = WidgetTableResolver.resolveCurrentTable()
                val days = if (table != null) {
                    val week = com.lingion.sleepy.util.DateUtils.currentWeek(table.startDate, today)
                    listOf(today, tomorrow).map { date ->
                        val dow = date.dayOfWeek.value
                        val all = SleepyApp.get().repository.getCoursesByDayOnce(table.id, dow)
                        val visible = all.filter { it.inWeek(week) }.sortedBy { it.startNode }
                        DayData(date = date, dayOfWeek = dow, courses = visible, timeJson = table.timeJson)
                    }
                } else emptyList()
                WidgetBitmapRenderers.renderTwoDay(
                    this,
                    TwoDayData(
                        days = days,
                        hasTable = table != null,
                        isDark = com.lingion.sleepy.util.AppPrefs.isDarkMode(this),
                        themeKey = com.lingion.sleepy.util.AppPrefs.getThemeKey(this)
                    ),
                    wDp, hDp
                )
            }
            "weeklist" -> {
                val today = java.time.LocalDate.now()
                val table = WidgetTableResolver.resolveCurrentTable()
                val days = if (table != null) {
                    val week = com.lingion.sleepy.util.DateUtils.currentWeek(table.startDate, today)
                    (1..7).map { dow ->
                        val date = com.lingion.sleepy.util.DateUtils.dateOfWeekDay(today, dow)
                        val all = SleepyApp.get().repository.getCoursesByDayOnce(table.id, dow)
                        val visible = all.filter { it.inWeek(week) }.sortedBy { it.startNode }
                        DayData(date = date, dayOfWeek = dow, courses = visible, timeJson = table.timeJson)
                    }
                } else emptyList()
                WidgetBitmapRenderers.renderWeekList(
                    this,
                    WeekData(
                        days = days,
                        hasTable = table != null,
                        isDark = com.lingion.sleepy.util.AppPrefs.isDarkMode(this),
                        themeKey = com.lingion.sleepy.util.AppPrefs.getThemeKey(this)
                    ),
                    wDp, hDp
                )
            }
            else -> {
                val data = WeekGridWidgetProvider.loadWeekData(this)
                val density = resources.displayMetrics.density
                val w = (wDp * density).toInt()
                val h = (hDp * density).toInt()
                WeekGridWidgetProvider.renderBitmap(this, data, w, h)
            }
        }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WidgetRender"

        /** 启动入口：渲染指定 widget */
        fun start(activity: android.app.Activity, which: String) {
            activity.startActivity(
                android.content.Intent(activity, WidgetRenderActivity::class.java)
                    .putExtra("widget", which)
            )
        }
    }
}