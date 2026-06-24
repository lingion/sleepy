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
        // ★ v19d: Bitmap 用固定 dp 尺寸 (用户原话: "字体依然巨大, 九节课")
        // 根因: 不同 launcher 返回的 OPTION_APPWIDGET_MIN_WIDTH/HEIGHT 单位不一致
        //   第三方 launcher (Android 16) 返回 dp (e.g. 320, 400) 而非 pixels
        //   → Bitmap 实际只有 320×400px → headH=168px 后 bodyH=116px → 只装 9 节
        //   → fontSize 35px 占 Bitmap 12% → 真机显示"巨大"
        // 修法: Bitmap 固定 360×600 dp (= 真实 4×5 widget 容器大小)
        //   layout scaleType=fitCenter 自动缩放匹配任何 widget 容器
        val w = (360 * density).toInt()
        val h = (600 * density).toInt()
        Log.d(TAG, "renderWidget: FIXED dp bitmap=${w}x${h}px (density=$density, opts MIN=${opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)}x${opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)})")

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

            // ★ v19e: 课程颜色对齐 CourseTableView.pickCourseColor
            // 优先按课程名关键词匹配 (英语/物理/心理/高数等), 否则 hash 到 palette
            // 用户原话: "你这个课程的颜色也没有跟软件内的对齐"
            // 颜色定义来自 LightCoursePalette / DarkCoursePalette
            val palette = if (isDark) mapOf(
                "primary" to 0xFF4F378B.toInt(),     // 高数/数学/主课
                "secondary" to 0xFF4A4458.toInt(),   // 通用
                "tertiary" to 0xFF633B48.toInt(),    // 思政/史纲
                "english" to 0xFF1E3A4D.toInt(),     // 英语
                "military" to 0xFF2E3F26.toInt(),    // 军事/国防
                "physics" to 0xFF4D3A1E.toInt(),     // 物理
                "history" to 0xFF4D2828.toInt(),      // 历史
                "psychology" to 0xFF352B4D.toInt(),   // 心理
                "practice" to 0xFF1E3D32.toInt()     // 实践/实验
            ) else mapOf(
                "primary" to 0xFFEADDFF.toInt(),
                "secondary" to 0xFFE8DEF8.toInt(),
                "tertiary" to 0xFFFFD8E4.toInt(),
                "english" to 0xFFD8F2FF.toInt(),
                "military" to 0xFFE7F3DC.toInt(),
                "physics" to 0xFFFFE7C7.toInt(),
                "history" to 0xFFF7D9D9.toInt(),
                "psychology" to 0xFFE6DDFB.toInt(),
                "practice" to 0xFFD7F0E8.toInt()
            )
            val hashPaletteKeys = listOf("primary", "secondary", "tertiary", "english", "physics", "psychology")
            fun pickCourseColor(name: String): Int {
                // 1. 关键词规则 (跟 CourseTableView courseColorRules 一致)
                val rules = listOf(
                    "英语" to "english",
                    "军事" to "military", "国防" to "military",
                    "物理" to "physics",
                    "历史" to "history", "史纲" to "history", "近代史" to "history",
                    "心理" to "psychology",
                    "实践" to "practice", "实习" to "practice", "实验" to "practice",
                    "高数" to "primary", "数学" to "primary", "电路" to "primary",
                    "思政" to "tertiary", "马原" to "tertiary", "毛概" to "tertiary", "形势" to "tertiary"
                )
                rules.firstOrNull { (kw, _) -> name.contains(kw) }?.let { (_, key) ->
                    return palette[key]!!
                }
                // 2. hash fallback
                val h = (name.hashCode() and 0x7FFFFFFF)
                val key = hashPaletteKeys[h % hashPaletteKeys.size]
                return palette[key]!!
            }

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
            // ★ v19c 字号参数 (用户原话: "你这个字号明显是不合格的")
            // 之前 headH*0.30 cap dp(15f) 太大, day header 文字溢出 cell 边界全挤在一起
            // 改成: cap 降到 dp(13f), min 升到 dp(10f), 文字宽度永远 < dayW - padding
            val outerPad = dp(6f)
            val headH = dp(56f)
            val timeW = dp(40f)
            val gapH = dp(1.5f)
            val gapW = dp(2.5f)

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

            // ★ v19b: 字号完全根据 widget 宽高自适应 (用户原话: "能不能自动根据这个宽度, 高度调整")
            // 1dp 永远 = 1dp, 但用 widget 尺寸作为 scale 单位
            // dayW = (bodyW-timeW) / 7, cardH = slotH * step
            // 单节 course 卡片: cardH = slotH (小卡), 多节: cardH = slotH*N (大卡)

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

                // day name 字号 = headH * 0.24 (降比例, 防溢出 cell)
                val dayName = DateUtils.localizedDay(dow, SleepyApp.get())
                p.color = if (isToday) fgPrimary else fgOnSurface
                p.textSize = (headH * 0.24f).coerceAtMost(dp(13f).toFloat()).coerceAtLeast(dp(9f).toFloat())
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                p.textAlign = Paint.Align.CENTER
                val cx = cellX + dayW / 2f
                c.drawText(dayName, cx, y + headH * 0.4f, p)

                // date or count 字号 = headH * 0.18 (降比例)
                p.textSize = (headH * 0.18f).coerceAtMost(dp(10f).toFloat()).coerceAtLeast(dp(7f).toFloat())
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

                // period number 字号 = slotH * 0.40 (降比例)
                p.color = fgOnSurface
                p.textSize = (slotH * 0.40f)
                    .coerceAtMost(dp(13f).toFloat())
                    .coerceAtLeast(dp(8f).toFloat())
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val cy = rowY + slotH / 2f + p.textSize * 0.35f
                c.drawText("$i", x + timeW / 2f, cy, p)

                // time label 字号 = slotH * 0.20 (降比例)
                if (slot != null && slotH > dp(18f)) {
                    p.color = fgOnSurfaceVar
                    p.textSize = (slotH * 0.20f)
                        .coerceAtMost(dp(7f).toFloat())
                        .coerceAtLeast(dp(4f).toFloat())
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    c.drawText(slot, x + timeW / 2f, cy + p.textSize * 1.6f, p)
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

                    // 卡片背景色 (v19e: 对齐 CourseTableView palette)
                    val baseColor = pickCourseColor(course.courseName)
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

                    // ★ v19f: 台湾从右往左竖排 (用户原话: "全部改成台湾从右往左竖排文字")
                    // 字直立不旋转, 单字一格, 第一列在最右, 字数超出 cardH 自动开第二列 (往左)
                    val textColor = if (isDarkOn(baseColor)) Color.WHITE else 0xFF1D1B20.toInt()
                    p.color = textColor
                    p.textAlign = Paint.Align.CENTER

                    // 字块准备: 课程名字符 + (教室) + (教室字符)
                    val nameChars = course.courseName.filter { it != '\n' && it != ' ' }.toList()
                    val roomChars = course.room.takeIf { it.isNotBlank() }
                        ?.filter { it != '\n' && it != ' ' }?.toList() ?: emptyList()
                    val totalChars = nameChars.size +
                        (if (roomChars.isNotEmpty()) 1 + roomChars.size else 0) // 1 = name/room 间距

                    // 可用区域 (留 padding)
                    val pad = dp(3f).toFloat()
                    val availW = (cardRect.width() - pad * 2).coerceAtLeast(dp(8f).toFloat())
                    val availH = (cardRect.height() - pad * 2).coerceAtLeast(dp(8f).toFloat())

                    // 每字正方形 (汉字方块), charSize = 单字边长 (px)
                    // 字号下限 7dp, 上限 = 卡片短边 (跨节卡也不会过大)
                    val maxCharSize = (availW * 0.7f).coerceAtMost(availH * 0.5f) // 单列时字不能太宽
                    var charSize = (availH / totalChars).coerceAtMost(maxCharSize)
                        .coerceAtLeast(dp(7f).toFloat())
                    // 算需要几列
                    var charsPerCol = (availH / charSize).toInt().coerceAtLeast(1)
                    var cols = ((totalChars + charsPerCol - 1) / charsPerCol).coerceAtLeast(1)
                    // 字宽 = 单列宽 (留 column gap)
                    val colGap = dp(1f).toFloat()
                    var colW = (availW - colGap * (cols - 1)) / cols
                    // 重新算 charSize 让字填满列宽 (方块字)
                    charSize = charSize.coerceAtMost(colW).coerceAtLeast(dp(7f).toFloat())
                    charsPerCol = (availH / charSize).toInt().coerceAtLeast(1)
                    cols = ((totalChars + charsPerCol - 1) / charsPerCol).coerceAtLeast(1)
                    colW = (availW - colGap * (cols - 1)) / cols
                    charSize = charSize.coerceAtMost(colW).coerceAtLeast(dp(7f).toFloat())

                    val totalH = charSize * charsPerCol
                    val blockTop = cardRect.top + (cardRect.height() - totalH) / 2f
                    // 右对齐起点 (第一列 = 最右)
                    val blockRight = cardRect.right - pad

                    p.textSize = charSize
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

                    // 把所有字符连成单序列: nameChars + (分隔空槽) + roomChars
                    val allChars = if (roomChars.isNotEmpty())
                        nameChars + listOf('\u0000') + roomChars // 0 = 空槽
                    else nameChars

                    for ((idx, ch) in allChars.withIndex()) {
                        val col = idx / charsPerCol // 0 = 最右
                        val row = idx % charsPerCol
                        val cx = blockRight - col * (colW + colGap) - colW / 2f
                        val cy = blockTop + charSize * (row + 0.82f) // 0.82 = baseline 偏下
                        if (ch == '\u0000') continue // 空槽
                        c.drawText(ch.toString(), cx, cy, p)
                    }

                    // 副标签用 NORMAL + 略小 + 半透明
                    if (roomChars.isNotEmpty() && charSize > dp(8f).toFloat()) {
                        // roomChars 已经画过, 此处无额外操作 (上面循环已包含 room 部分)
                        // 但用 NORMAL 字重 + 半透明 — 需要第二次 pass 覆盖 room 部分
                        // 简化: 在 cardH > slotH * 2 时降字号重画 room
                        if (cardH > slotH * 2f) {
                            val subSize = (charSize * 0.85f).coerceAtLeast(dp(6f).toFloat())
                            p.textSize = subSize
                            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                            p.alpha = 200
                            // roomChars 起始 idx = nameChars.size + 1
                            val roomStart = nameChars.size + 1
                            for ((offset, ch) in roomChars.withIndex()) {
                                val idx = roomStart + offset
                                val col = idx / charsPerCol
                                val row = idx % charsPerCol
                                val cx = blockRight - col * (colW + colGap) - colW / 2f
                                // 用 subSize 重算 baseline (原 charSize, 现在 subSize)
                                val cy = blockTop + subSize * (row + 0.82f)
                                if (ch == '\u0000') continue
                                c.drawText(ch.toString(), cx, cy, p)
                            }
                            p.alpha = 255
                        }
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
