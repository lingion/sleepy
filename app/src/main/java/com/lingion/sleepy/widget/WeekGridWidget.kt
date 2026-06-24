package com.lingion.sleepy.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.lingion.sleepy.MainActivity
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.DateUtils
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/**
 * v17-rebuild: WeekGrid widget — RemoteViews + Bitmap + Canvas
 *
 * 用户原话："竖着的和横着的网格做出来，比如说你横着的每一格它是10像素，
 *          竖着的每一个是10像素，那你就定位出来10×10的表格了呀"
 *
 * 抛弃 Glance 全部 Compose layout 限制：
 * - 用 Canvas 在 Bitmap 上画整个 widget
 * - 每个 cell 10×10px logical, 然后 scale 到 widget 实际像素尺寸
 * - 跨节课程 = drawRoundRect(startY, endY), 文字垂直居中
 * - Period 数量无限制 (Glance RemoteViews bug = 1.1.0 LinearLayout 丢最后几个 child)
 */
class WeekGridWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate: ids=${appWidgetIds.toList()}")
        for (widgetId in appWidgetIds) {
            try {
                renderWidget(context, appWidgetManager, widgetId)
            } catch (e: Throwable) {
                Log.e(TAG, "render failed for $widgetId", e)
            }
        }
    }

    private fun renderWidget(
        context: Context,
        awm: AppWidgetManager,
        widgetId: Int
    ) {
        // 1. 加载周数据
        val data = loadWeekData(context)

        // 2. 读 widget 实际尺寸（像素）
        val info = awm.getAppWidgetInfo(widgetId)
        val density = context.resources.displayMetrics.density
        val maxWidthPx = awm.getAppWidgetOptions(widgetId)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            .takeIf { it > 0 }
            ?: (info?.minWidth ?: 250 * density).toInt()
        val maxHeightPx = awm.getAppWidgetOptions(widgetId)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            .takeIf { it > 0 }
            ?: (info?.minHeight ?: 640 * density).toInt()

        Log.d(TAG, "widgetId=$widgetId size=${maxWidthPx}x${maxHeightPx}px (density=$density)")

        // 3. 画 Bitmap
        val bitmap = renderBitmap(
            context = context,
            data = data,
            widthPx = maxWidthPx,
            heightPx = maxHeightPx
        )

        // 4. 打包 RemoteViews
        val views = RemoteViews(context.packageName, R.layout.widget_bitmap_container)
        views.setImageViewBitmap(R.id.widget_bitmap, bitmap)
        views.setViewVisibility(R.id.widget_bitmap, View.VISIBLE)

        // 5. 点击 widget 打开 MainActivity
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, widgetId, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_bitmap, pi)

        awm.updateAppWidget(widgetId, views)
    }

    companion object {
        private const val TAG = "WeekGridProvider"

        // ★ 10×10 logical grid, scale 到 widget 实际尺寸
        private const val GRID_UNIT_DP = 10f

        fun renderBitmap(
            context: Context,
            data: WeekData,
            widthPx: Int,
            heightPx: Int
        ): Bitmap {
            val density = context.resources.displayMetrics.density

            // ★ v17-rebuild: maxNode 完全由用户课表决定 (1 ~ 1000+ 都不截断)
            // 用户原话: "用户课表填1个时间段小组件就1个 填9999个小组件就9999个"
            // 算法: 真实 maxNode = 数据最大结束节次 (1px/row 都不截断, 让用户看到全量数据)
            val dataMaxNode = (data.days.flatMap { d -> d.courses }
                .maxOfOrNull { it.startNode + it.step - 1 } ?: 0)
            val maxNode = dataMaxNode.coerceAtLeast(1)  // 用户课表完全决定, 永不截断
            val visibleDays = data.visibleDays.sorted()
            val dayCount = visibleDays.size.coerceIn(1, 7)

            // ★ 像素级别布局 — 不依赖 unitPx 整数
            val headerH_px = 36f
            val timeColW_px = (widthPx * 0.12f).toInt().coerceAtLeast(40)
            val dayColW_px = (widthPx - timeColW_px) / dayCount
            val bodyH_px = (heightPx - headerH_px).toInt().coerceAtLeast(40)
            val rowH_px = bodyH_px.toFloat() / maxNode
            val unitPx = rowH_px / 1.0f  // 给课程卡片用

            Log.d(TAG, "render: widgetPx=${widthPx}x$heightPx, timeCol=${timeColW_px}px, " +
                "dayCol=${dayColW_px}px, headerH=${headerH_px}px, bodyH=${bodyH_px}px, rowH=${rowH_px}px, " +
                "maxNode=$maxNode (data=$dataMaxNode), dayCount=$dayCount")

            // 创建 Bitmap
            val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            // 画背景
            val bgColor = if (data.isDark) Color.parseColor("#1C1B1F") else Color.WHITE
            canvas.drawColor(bgColor)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.isAntiAlias = true

            // ── 1. 表头行 (Day labels) ──
            paint.color = if (data.isDark) Color.parseColor("#2B2930") else Color.parseColor("#F3F0F4")
            canvas.drawRect(0f, 0f, widthPx.toFloat(), headerH_px, paint)

            paint.color = if (data.isDark) Color.parseColor("#E6E1E5") else Color.parseColor("#1C1B1F")
            paint.textSize = (headerH_px * 0.45f).coerceAtLeast(8f)
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val todayDow = LocalDate.now().dayOfWeek.value

            // day column headers
            for ((idx, dow) in visibleDays.withIndex()) {
                val colX = timeColW_px + idx * dayColW_px
                val colCenterX = colX + dayColW_px / 2f
                val textY = headerH_px * 0.65f
                val dayName = DateUtils.localizedDay(dow, SleepyApp.get())
                val label = if (dow == todayDow) "•$dayName•" else dayName
                canvas.drawText(label, colCenterX, textY, paint)
            }

            // ── 2. Time column (左侧) ──
            paint.color = if (data.isDark) Color.parseColor("#2B2930") else Color.parseColor("#F3F0F4")
            canvas.drawRect(0f, headerH_px, timeColW_px.toFloat(), heightPx.toFloat(), paint)

            paint.color = if (data.isDark) Color.parseColor("#CAC4D0") else Color.parseColor("#49454F")
            paint.textSize = rowH_px.coerceAtMost(36f).coerceAtLeast(7f)
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

            val bodyTop = headerH_px
            for (i in 1..maxNode) {
                val rowTop = bodyTop + (i - 1) * rowH_px
                val cellCenterY = rowTop + rowH_px / 2f + rowH_px * 0.35f
                canvas.drawText("$i", timeColW_px / 2f, cellCenterY, paint)
            }

            // ── 3. Body 区域 ──
            val todayBgColor = if (data.isDark) Color.parseColor("#3D2A5C") else Color.parseColor("#F3E5F5")
            val todayIdx = visibleDays.indexOf(todayDow)
            if (todayIdx >= 0) {
                paint.color = todayBgColor
                val todayX = timeColW_px + todayIdx * dayColW_px
                canvas.drawRect(todayX.toFloat(), bodyTop, (todayX + dayColW_px).toFloat(), heightPx.toFloat(), paint)
            }

            // ── 4. 网格线 (horizontal) ──
            paint.color = if (data.isDark) Color.parseColor("#36343B") else Color.parseColor("#E7E0EC")
            paint.strokeWidth = 1f
            for (i in 0..maxNode) {
                val y = bodyTop + i * rowH_px
                canvas.drawLine(timeColW_px.toFloat(), y, widthPx.toFloat(), y, paint)
            }
            canvas.drawLine(timeColW_px.toFloat(), 0f, timeColW_px.toFloat(), heightPx.toFloat(), paint)
            for (i in 0..dayCount) {
                val x = timeColW_px + i * dayColW_px
                canvas.drawLine(x.toFloat(), bodyTop, x.toFloat(), heightPx.toFloat(), paint)
            }

            // ── 5. 课程卡片 ──
            val cornerRadius = (rowH_px * 0.3f).coerceAtLeast(2f)
            for ((idx, dow) in visibleDays.withIndex()) {
                val dayData = data.days.firstOrNull { it.dayOfWeek == dow } ?: continue
                val colX = timeColW_px + idx * dayColW_px
                val colW = dayColW_px
                val sortedCourses = dayData.courses.sortedBy { it.startNode }
                for (course in sortedCourses) {
                    drawCourse(canvas, paint, course, colX.toFloat(), colW.toFloat(), bodyTop, rowH_px, cornerRadius, data.isDark)
                }
            }

            return bmp
        }

        private fun drawCourse(
            canvas: Canvas,
            paint: Paint,
            course: CourseEntity,
            colX: Float,
            colW: Float,
            bodyTop: Float,
            rowH_px: Float,
            cornerRadius: Float,
            isDark: Boolean
        ) {
            val startRow = course.startNode - 1
            val endRow = course.startNode + course.step - 2

            val rectTop = bodyTop + startRow * rowH_px + 1f
            val rectBottom = bodyTop + (endRow + 1) * rowH_px - 1f
            val rectLeft = colX + 2f
            val rectRight = colX + colW - 2f

            // 解析颜色
            val baseColor = runCatching { Color.parseColor(course.color) }
                .getOrDefault(Color.parseColor("#6750A4"))

            // 卡片背景 (圆角)
            paint.color = baseColor
            val rect = RectF(rectLeft, rectTop, rectRight, rectBottom)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

            // 文字颜色 (对比)
            val textColor = if (isDarkOn(baseColor)) Color.WHITE else Color.parseColor("#1C1B1F")
            paint.color = textColor
            paint.textAlign = Paint.Align.CENTER

            // 课程名 — 居中, 字号 = cell 高度的 25% (再小就丢字)
            val cellH = rectBottom - rectTop
            val nameSize = (cellH * 0.25f).coerceAtMost(40f).coerceAtLeast(7f)
            paint.textSize = nameSize
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            val nameCx = (rectLeft + rectRight) / 2f
            val nameBaselineY = rectTop + cellH * 0.45f
            val nameMaxWidth = (rectRight - rectLeft) - 4f
            val nameText = ellipsize(course.courseName, paint, nameMaxWidth)
            canvas.drawText(nameText, nameCx, nameBaselineY, paint)

            // 老师/教室 — 第二行, 字号小一点
            if (cellH > rowH_px * 2.5f) {
                val subSize = (cellH * 0.13f).coerceAtMost(28f).coerceAtLeast(6f)
                paint.textSize = subSize
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                val sub = listOfNotNull(
                    course.teacher.takeIf { it.isNotBlank() },
                    course.room.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                    val subText = ellipsize(sub, paint, nameMaxWidth)
                    val subBaselineY = nameBaselineY + subSize * 1.2f
                    canvas.drawText(subText, nameCx, subBaselineY, paint)
                }
            }
        }

        /** 简单亮度判断 — 深色背景用白字 */
        private fun isDarkOn(color: Int): Boolean {
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            return luminance < 0.6
        }

        /** 文字超出宽度则省略 */
        private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
            if (paint.measureText(text) <= maxWidth) return text
            var s = text
            while (s.isNotEmpty() && paint.measureText("$s…") > maxWidth) {
                s = s.dropLast(1)
            }
            return "$s…"
        }

        /**
         * 读周数据（IO thread, 但此处通常在 widget thread 调用，足够快）
         */
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
                        (1..7).map { dow -> dow to repo.getCoursesByDayOnce(t.id, dow).filter { it.inWeek(week) }.sortedBy { it.startNode } }
                    } else emptyList()
                    Pair(t, map)
                }
                if (table == null) {
                    Log.w(TAG, "loadWeekData: no default table")
                    WeekData(
                        days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey,
                        displayMode = displayMode, showDate = showDate, visibleDays = visibleDays
                    )
                } else {
                    val days = daysPerCourse.map { (dayOfWeek, courses) ->
                        val date = DateUtils.dateOfWeekDay(today, dayOfWeek)
                        DayData(date = date, dayOfWeek = dayOfWeek, courses = courses, timeJson = table.timeJson)
                    }
                    Log.d(TAG, "loadWeekData: table=${table.id}, totalCourses=${days.sumOf { it.courses.size }}")
                    WeekData(
                        days = days, hasTable = true, isDark = isDark, themeKey = themeKey,
                        displayMode = displayMode, showDate = showDate, visibleDays = visibleDays
                    )
                }
            } catch (e: Throwable) {
                Log.e(TAG, "loadWeekData failed", e)
                WeekData(
                    days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey,
                    displayMode = displayMode, showDate = showDate, visibleDays = visibleDays
                )
            }
        }
    }
}
