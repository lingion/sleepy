package com.lingion.sleepy.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.lingion.sleepy.MainActivity
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.DateUtils
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * v19: WeekGrid widget — RemoteViews + Bitmap + Canvas
 *
 * 为什么不用 Glance: Glance 1.1.0 转 RemoteViews 时 LinearLayout 丢 Period 11+ child
 * Canvas 在 Bitmap 上画, 不受 LinearLayout child 数量限制, Period 1~9999 全显示
 *
 * 视觉复刻 CourseTableView: 圆角卡片 + gap + today 高亮 + 课程名居中
 */
class WeekGridWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, awm: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            try { renderWidget(context, awm, id) }
            catch (e: Throwable) { Log.e(TAG, "render failed $id", e) }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, awm: AppWidgetManager, id: Int, newOptions: android.os.Bundle
    ) {
        renderWidget(context, awm, id)
    }

    private fun renderWidget(context: Context, awm: AppWidgetManager, widgetId: Int) {
        val data = loadWeekData(context)
        val opts = awm.getAppWidgetOptions(widgetId)
        val density = context.resources.displayMetrics.density
        val w = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            .takeIf { it > 0 } ?: (320 * density).toInt()
        val h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            .takeIf { it > 0 } ?: (640 * density).toInt()

        val bmp = renderBitmap(context, data, w, h)
        val views = RemoteViews(context.packageName, R.layout.widget_bitmap_container)
        views.setImageViewBitmap(R.id.widget_bitmap, bmp)
        val pi = PendingIntent.getActivity(context, widgetId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_bitmap, pi)
        awm.updateAppWidget(widgetId, views)
    }

    companion object {
        private const val TAG = "WeekGridV19"

        fun renderBitmap(context: Context, data: WeekData, wPx: Int, hPx: Int): Bitmap {
            val density = context.resources.displayMetrics.density
            val isDark = data.isDark

            // ── 颜色 (复刻 SleepyTheme) ──
            val bgSurface       = if (isDark) 0xFF2B2930.toInt() else 0xFFF7F2FA.toInt()
            val bgContainer     = if (isDark) 0xFF1C1B1F.toInt() else 0xFFF3F0F4.toInt()
            val bgCell          = if (isDark) 0xFF36343B.toInt() else 0xFFFFFFFF.toInt()
            val bgToday         = if (isDark) 0xFF4A3B6B.toInt() else 0xFFE8DEF8.toInt()
            val fgPrimary       = if (isDark) 0xFFD0BCFF.toInt() else 0xFF6750A4.toInt()
            val fgOnSurface     = if (isDark) 0xFFE6E1E5.toInt() else 0xFF1D1B20.toInt()
            val fgOnSurfaceVar  = if (isDark) 0xFFCAC4D0.toInt() else 0xFF49454F.toInt()
            val gridLine        = if (isDark) 0xFF49454F.toInt() else 0xFFE7E0EC.toInt()

            // ── 数据 ──
            val timeJson = data.days.firstOrNull()?.timeJson ?: ""
            val allSlots = parseTimeSlots(timeJson)
            val maxNode = (data.days.flatMap { it.courses }
                .maxOfOrNull { it.startNode + it.step - 1 } ?: allSlots.size)
                .coerceAtLeast(1)
            val slots = allSlots.take(maxNode)
            val sortedDays = data.visibleDays.sorted()
            val dayCount = sortedDays.size.coerceIn(1, 7)
            val todayDow = LocalDate.now().dayOfWeek.value

            // ── 布局 (dp → px, 跟 CourseTableView 同参数) ──
            val dp = { v: Float -> (v * density).roundToInt() }
            val outerPad = dp(8f)
            val headH = dp(48f)
            val timeW = dp(42f)
            val gapH = dp(1.5f)
            val gapW = dp(2f)

            val bodyW = wPx - outerPad * 2
            val bodyH = hPx - outerPad * 2 - headH
            val totalGapW = gapW * (dayCount + 1)
            val dayW = ((bodyW - timeW - totalGapW) / dayCount).toFloat()
            val totalGapH = gapH * (maxNode + 1)
            val slotH = ((bodyH - totalGapH) / maxNode).toFloat().coerceAtLeast(dp(3f).toFloat())

            Log.d(TAG, "w=${wPx}x${hPx} maxNode=$maxNode dayCount=$dayCount " +
                "slotH=${slotH}px dayW=${dayW}px headH=${headH}px")

            // ── Canvas ──
            val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)

            // 背景: 圆角容器
            p.color = bgContainer
            val containerRect = RectF(0f, 0f, wPx.toFloat(), hPx.toFloat())
            c.drawRoundRect(containerRect, dp(18f).toFloat(), dp(18f).toFloat(), p)

            // ── Header (Day labels) ──
            var x = outerPad.toFloat()
            var y = outerPad.toFloat()

            // time column 角落
            p.color = bgSurface
            c.drawRoundRect(RectF(x, y, x + timeW, y + headH),
                dp(14f).toFloat(), dp(14f).toFloat(), p)

            // day headers
            for ((idx, dow) in sortedDays.withIndex()) {
                val cellX = x + timeW + gapW + idx * (dayW + gapW)
                val isToday = dow == todayDow
                val dayData = data.days.firstOrNull { it.dayOfWeek == dow }
                val count = dayData?.courses?.size ?: 0
                val dateStr = if (data.showDate && dayData != null) DateUtils.shortDate(dayData.date) else null

                p.color = if (isToday) bgToday else bgSurface
                c.drawRoundRect(RectF(cellX, y, cellX + dayW, y + headH),
                    dp(14f).toFloat(), dp(14f).toFloat(), p)

                // day name
                val dayName = DateUtils.localizedDay(dow, SleepyApp.get())
                p.color = if (isToday) fgPrimary else fgOnSurface
                p.textSize = dp(11f).toFloat()
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                p.textAlign = Paint.Align.CENTER
                val cx = cellX + dayW / 2f
                c.drawText(dayName, cx, y + headH * 0.4f, p)

                // date or count
                p.textSize = dp(8f).toFloat()
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                p.color = fgOnSurfaceVar
                val sub = dateStr ?: if (count > 0) "$count" else "—"
                c.drawText(sub, cx, y + headH * 0.72f, p)
            }

            // ── Body ──
            y = (outerPad + headH).toFloat()
            val bodyTop = y

            // time column labels
            p.textAlign = Paint.Align.CENTER
            for (i in 1..maxNode) {
                val rowY = bodyTop + gapH + (i - 1) * (slotH + gapH)
                val slot = slots.getOrNull(i - 1)

                // period number
                p.color = fgOnSurface
                p.textSize = (slotH * 0.4f).coerceAtMost(dp(11f).toFloat()).coerceAtLeast(dp(6f).toFloat())
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val cy = rowY + slotH / 2f + p.textSize * 0.35f
                c.drawText("$i", x + timeW / 2f, cy, p)

                // time label (如果 slot 高度够)
                if (slot != null && slotH > dp(16f)) {
                    p.color = fgOnSurfaceVar
                    p.textSize = dp(6f).toFloat()
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    c.drawText(slot, x + timeW / 2f, cy + p.textSize * 1.5f, p)
                }
            }

            // day columns
            for ((idx, dow) in sortedDays.withIndex()) {
                val colX = x + timeW + gapW + idx * (dayW + gapW)
                val dayData = data.days.firstOrNull { it.dayOfWeek == dow } ?: continue
                val isToday = dow == todayDow

                // today 背景列
                if (isToday) {
                    p.color = bgToday
                    p.alpha = 40
                    c.drawRect(RectF(colX, bodyTop, colX + dayW, bodyTop + bodyH), p)
                    p.alpha = 255
                }

                // 课程卡片
                val sortedCourses = dayData.courses.sortedBy { it.startNode }
                for (course in sortedCourses) {
                    val startIdx = (course.startNode - 1).coerceAtLeast(0)
                    val step = course.step.coerceAtLeast(1)
                        .coerceAtMost(maxNode - startIdx)
                    val cardTop = bodyTop + gapH + startIdx * (slotH + gapH)
                    val cardH = slotH * step + gapH * (step - 1)
                    val cardRect = RectF(colX, cardTop, colX + dayW, cardTop + cardH)

                    // 卡片背景色
                    val baseColor = runCatching { Color.parseColor(course.color) }
                        .getOrDefault(if (isDark) 0xFF6750A4.toInt() else 0xFF6750A4.toInt())
                    p.color = baseColor
                    p.alpha = 200
                    c.drawRoundRect(cardRect, dp(10f).toFloat(), dp(10f).toFloat(), p)
                    p.alpha = 255

                    // border
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = dp(0.5f).toFloat()
                    p.color = baseColor
                    p.alpha = 80
                    c.drawRoundRect(cardRect, dp(10f).toFloat(), dp(10f).toFloat(), p)
                    p.style = Paint.Style.FILL
                    p.alpha = 255

                    // 课程名
                    val textColor = if (isDarkOn(baseColor)) Color.WHITE else 0xFF1D1B20.toInt()
                    p.color = textColor
                    p.textAlign = Paint.Align.CENTER
                    val cx2 = colX + dayW / 2f

                    // 字号: 卡片高度 * 0.16, 但限制在 7-12dp 之间
                    val nameSize = (cardH * 0.16f)
                        .coerceAtLeast(dp(7f).toFloat())
                        .coerceAtMost(dp(12f).toFloat())
                    p.textSize = nameSize
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    val nameMaxW = dayW - dp(4f)
                    val nameText = ellipsize(course.courseName, p, nameMaxW)
                    // 垂直居中: (cardTop + cardH/2) + textSize*0.35 (baseline correction)
                    val nameY = cardTop + cardH / 2f + nameSize * 0.35f
                    c.drawText(nameText, cx2, nameY, p)

                    // 时间/地点 (如果卡片够高)
                    if (cardH > slotH * 2f) {
                        val subSize = (nameSize * 0.75f).coerceAtLeast(dp(5f).toFloat())
                        p.textSize = subSize
                        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        p.alpha = 180
                        val sub = course.room.takeIf { it.isNotBlank() } ?: ""
                        if (sub.isNotBlank()) {
                            val subText = ellipsize(sub, p, nameMaxW)
                            c.drawText(subText, cx2, nameY + nameSize * 0.9f, p)
                        }
                        p.alpha = 255
                    }
                }
            }

            return bmp
        }

        private fun parseTimeSlots(timeJson: String): List<String> {
            return try {
                val arr = org.json.JSONArray(timeJson)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    o.getString("start")
                }
            } catch (e: Exception) {
                // 默认 12 节
                listOf("08:00","08:55","10:00","10:55","14:00","14:55",
                    "16:00","16:55","19:00","19:55","20:50","21:45")
            }
        }

        private fun isDarkOn(color: Int): Boolean {
            val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
            return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0 < 0.55
        }

        private fun ellipsize(text: String, paint: Paint, maxW: Float): String {
            if (paint.measureText(text) <= maxW) return text
            var s = text
            while (s.isNotEmpty() && paint.measureText("$s…") > maxW) s = s.dropLast(1)
            return if (s.isEmpty()) "" else "$s…"
        }

        fun loadWeekData(context: Context): WeekData {
            val today = LocalDate.now()
            val isDark = AppPrefs.isDarkMode(context)
            val themeKey = AppPrefs.getThemeKey(context)
            val displayMode = AppPrefs.getDisplayMode(context)
            val showDate = AppPrefs.isShowDate(context)
            val visibleDays = AppPrefs.getVisibleDays(context)
            return try {
                val (table, daysPerCourse) = kotlinx.coroutines.runBlocking {
                    val app = SleepyApp.get()
                    val repo = app.repository
                    val t = repo.getDefaultTable()
                    val map = if (t != null) {
                        val week = DateUtils.currentWeek(t.startDate, today)
                        (1..7).map { dow ->
                            dow to repo.getCoursesByDayOnce(t.id, dow)
                                .filter { it.inWeek(week) }.sortedBy { it.startNode }
                        }
                    } else emptyList()
                    Pair(t, map)
                }
                if (table == null) {
                    WeekData(days = emptyList(), hasTable = false, isDark = isDark,
                        themeKey = themeKey, displayMode = displayMode,
                        showDate = showDate, visibleDays = visibleDays)
                } else {
                    val days = daysPerCourse.map { (dow, courses) ->
                        val date = DateUtils.dateOfWeekDay(today, dow)
                        DayData(date = date, dayOfWeek = dow, courses = courses, timeJson = table.timeJson)
                    }
                    WeekData(days = days, hasTable = true, isDark = isDark,
                        themeKey = themeKey, displayMode = displayMode,
                        showDate = showDate, visibleDays = visibleDays)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "loadWeekData failed", e)
                WeekData(days = emptyList(), hasTable = false, isDark = isDark,
                    themeKey = themeKey, displayMode = displayMode,
                    showDate = showDate, visibleDays = visibleDays)
            }
        }
    }
}
