package com.lingion.sleepy.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import androidx.core.graphics.toColorInt
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Canvas bitmap 渲染器 — 复用 Glance widget 的数据加载逻辑，
 * 复刻它们的视觉样式输出 PNG（WidgetRenderActivity 用）。
 *
 * 4 个 widget 复用同一份 scheme，色彩与 app 主题一致。
 */
object WidgetBitmapRenderers {

    private const val TAG = "WidgetBitmap"

    // ── Scheme 颜色（与 WidgetContent.resolveSchemePublic 一致） ──
    data class Scheme(
        val bg: Int,
        val surface: Int,
        val primary: Int,
        val primaryContainer: Int,
        val onPrimaryContainer: Int,
        val onSurface: Int,
        val onSurfaceVariant: Int,
        val surfaceContainer: Int,
        val surfaceVariant: Int,
        val isDark: Boolean,
        val cPrimary: Int,
        val cSecondary: Int,
        val cTertiary: Int,
        val cEnglish: Int,
        val cMilitary: Int,
        val cPhysics: Int,
        val cHistory: Int,
        val cPsychology: Int,
        val cPractice: Int
    )

    private fun scheme(isDark: Boolean): Scheme {
        return if (isDark) Scheme(
            bg = "#1C1B1F".toColorInt(),
            surface = "#1C1B1F".toColorInt(),
            primary = "#D0BCFF".toColorInt(),
            primaryContainer = "#4F378B".toColorInt(),
            onPrimaryContainer = "#EADDFF".toColorInt(),
            onSurface = "#E6E1E5".toColorInt(),
            onSurfaceVariant = "#CAC4D0".toColorInt(),
            surfaceContainer = "#211F26".toColorInt(),
            surfaceVariant = "#49454F".toColorInt(),
            isDark = true,
            cPrimary = 0xFF4F378B.toInt(),
            cSecondary = 0xFF4A4458.toInt(),
            cTertiary = 0xFF633B48.toInt(),
            cEnglish = 0xFF1E3A4D.toInt(),
            cMilitary = 0xFF2E3F26.toInt(),
            cPhysics = 0xFF4D3A1E.toInt(),
            cHistory = 0xFF4D2828.toInt(),
            cPsychology = 0xFF352B4D.toInt(),
            cPractice = 0xFF1E3D32.toInt()
        ) else Scheme(
            bg = "#FDFCFF".toColorInt(),
            surface = "#FDFCFF".toColorInt(),
            primary = "#6750A4".toColorInt(),
            primaryContainer = "#EADDFF".toColorInt(),
            onPrimaryContainer = "#21005D".toColorInt(),
            onSurface = "#1D1B20".toColorInt(),
            onSurfaceVariant = "#79747E".toColorInt(),
            surfaceContainer = "#F3EDF7".toColorInt(),
            surfaceVariant = "#E7E0EC".toColorInt(),
            isDark = false,
            cPrimary = 0xFFEADDFF.toInt(),
            cSecondary = 0xFFE8DEF8.toInt(),
            cTertiary = 0xFFFFD8E4.toInt(),
            cEnglish = 0xFFD8F2FF.toInt(),
            cMilitary = 0xFFE7F3DC.toInt(),
            cPhysics = 0xFFFFE7C7.toInt(),
            cHistory = 0xFFF7D9D9.toInt(),
            cPsychology = 0xFFE6DDFB.toInt(),
            cPractice = 0xFFD7F0E8.toInt()
        )
    }

    private fun courseColor(name: String, s: Scheme): Int {
        when {
            "英语" in name -> return s.cEnglish
            "军事" in name || "国防" in name -> return s.cMilitary
            "物理" in name -> return s.cPhysics
            "历史" in name || "史纲" in name || "近代史" in name -> return s.cHistory
            "心理" in name -> return s.cPsychology
            "实践" in name || "实习" in name || "实验" in name -> return s.cPractice
            "高数" in name || "数学" in name || "电路" in name -> return s.cPrimary
            "思政" in name || "马原" in name || "毛概" in name || "形势" in name -> return s.cTertiary
        }
        val palette = listOf(s.cPrimary, s.cSecondary, s.cTertiary, s.cEnglish, s.cPhysics, s.cPsychology)
        return palette[(name.hashCode() and 0x7FFFFFFF) % palette.size]
    }

    private val dayLabels = arrayOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private fun drawCourse(
        c: Canvas, p: Paint, course: CourseEntity, timeJson: String, x: Float, y: Float, w: Float, h: Float,
        scheme: Scheme, density: Float, fontSizeSp: Float = 11f
    ) {
        val bgColor = courseColor(course.courseName, scheme)
        val pad = (3f * density).coerceAtLeast(1f)
        p.color = bgColor
        c.drawRoundRect(RectF(x, y, x + w, y + h), 8f * density, 8f * density, p)

        // 课程名
        p.color = scheme.onSurface
        p.textSize = fontSizeSp * density * 1f
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.isAntiAlias = true
        val name = course.courseName
        val maxWidth = w - pad * 2
        // 简单截断
        val displayName = if (p.measureText(name) > maxWidth) {
            var n = name
            while (n.isNotEmpty() && p.measureText("$n…") > maxWidth) n = n.dropLast(1)
            "$n…"
        } else name
        c.drawText(displayName, x + pad, y + pad + fontSizeSp * density, p)

        // 时间 + 地点
        p.textSize = (fontSizeSp - 2f) * density
        p.typeface = Typeface.DEFAULT
        p.color = scheme.onSurfaceVariant
        val timeStr = TimeTableUtils.courseTimeString(
            courseStartNode = course.startNode,
            courseStep = course.step,
            timeJson = timeJson,
            ownTime = course.ownTime,
            startTime = course.startTime,
            endTime = course.endTime
        ) ?: ""
        val meta = if (course.room.isNotBlank()) "$timeStr · ${course.room}" else timeStr
        c.drawText(meta, x + pad, y + pad + fontSizeSp * density * 2.2f, p)
    }

    /**
     * Today widget 渲染 — 今日课程列表
     */
    fun renderToday(context: Context, data: WidgetData, wDp: Float, hDp: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (wDp * density).toInt()
        val h = (hDp * density).toInt()
        val s = scheme(data.isDark)

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(c)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // 背景圆角
        p.color = s.bg
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()),
            20f * density, 20f * density, p)

        val pad = 14f * density
        var y = pad

        // 标题行：今天 · 周X  +  日期
        val ctx = SleepyApp.get()
        p.color = s.primary
        p.textSize = 13f * density
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val titleStr = "${ctx.getString(R.string.today_today)} · ${DateUtils.localizedDay(data.date.dayOfWeek.value, ctx)}"
        canvas.drawText(titleStr, pad, y + 13f * density, p)

        p.color = s.onSurfaceVariant
        p.textSize = 12f * density
        p.typeface = Typeface.DEFAULT
        val dateStr = "${data.date.monthValue}/${data.date.dayOfMonth}"
        val dateWidth = p.measureText(dateStr)
        canvas.drawText(dateStr, w - pad - dateWidth, y + 13f * density, p)

        y += 24f * density

        if (!data.hasTable) {
            p.color = s.onSurface
            p.textSize = 15f * density
            canvas.drawText(ctx.getString(R.string.widget_create_schedule), pad, y + 15f * density, p)
            return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
        }

        if (data.courses.isEmpty()) {
            p.color = s.onSurface
            p.textSize = 16f * density
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(ctx.getString(R.string.today_no_course), pad, y + 16f * density, p)
            y += 22f * density
            p.color = s.onSurfaceVariant
            p.textSize = 12f * density
            p.typeface = Typeface.DEFAULT
            canvas.drawText(ctx.getString(R.string.today_rest), pad, y + 12f * density, p)
            return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
        }

        // 课程列表
        val visible = data.courses.take(WidgetData.MAX_COURSES)
        val hidden = data.courses.size - visible.size
        val rowH = 38f * density
        val rowGap = 6f * density
        val rowW = w - pad * 2

        visible.forEachIndexed { idx, course ->
            drawCourse(canvas, p, course, data.timeJson, pad, y, rowW, rowH, s, density, fontSizeSp = 12f)
            y += rowH
            if (idx < visible.size - 1) y += rowGap
        }

        if (hidden > 0) {
            y += 4f * density
            p.color = s.onSurfaceVariant
            p.textSize = 11f * density
            canvas.drawText(ctx.getString(R.string.more_sections, hidden), pad, y + 11f * density, p)
        }

        return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
    }

    /**
     * WeekList widget 渲染 — 7 列日列
     */
    fun renderWeekList(context: Context, data: WeekData, wDp: Float, hDp: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (wDp * density).toInt()
        val h = (hDp * density).toInt()
        val s = scheme(data.isDark)

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(c)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // 背景
        p.color = s.bg
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()),
            20f * density, 20f * density, p)

        val outerPad = 6f * density
        val innerW = w - outerPad * 2
        val innerH = h - outerPad * 2

        if (!data.hasTable || data.days.isEmpty()) {
            p.color = s.onSurface
            p.textSize = 15f * density
            canvas.drawText(SleepyApp.get().getString(R.string.widget_create_schedule),
                outerPad, outerPad + 15f * density, p)
            return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
        }

        val todayDow = LocalDate.now().dayOfWeek.value
        val colGap = 4f * density
        val colW = (innerW - colGap * 6) / 7

        // 7 列
        for (i in data.days.indices) {
            val day = data.days[i]
            val x = outerPad + i * (colW + colGap)
            val isToday = day.dayOfWeek == todayDow
            val cardBg = if (isToday) s.primaryContainer else s.surfaceContainer

            // 列背景
            p.color = cardBg
            canvas.drawRoundRect(RectF(x, outerPad, x + colW, outerPad + innerH),
                14f * density, 14f * density, p)

            var cy = outerPad + 12f * density

            // 星期标题
            p.color = if (isToday) s.onPrimaryContainer else s.onSurface
            p.textSize = 12f * density
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val title = dayLabels[day.dayOfWeek]
            val tw = p.measureText(title)
            canvas.drawText(title, x + (colW - tw) / 2, cy, p)
            cy += 14f * density

            // 课程数量 chip
            if (day.courses.isNotEmpty()) {
                val chipText = "${day.courses.size} 门"
                p.color = s.surfaceVariant
                val chipW = (chipText.length * 6f + 12f) * density
                val chipH = 14f * density
                canvas.drawRoundRect(RectF(x + (colW - chipW) / 2, cy, x + (colW - chipW) / 2 + chipW, cy + chipH),
                    50f, 50f, p)
                p.color = s.onSurfaceVariant
                p.textSize = 9f * density
                val ctw = p.measureText(chipText)
                canvas.drawText(chipText, x + (colW - ctw) / 2, cy + chipH - 4f * density, p)
                cy += chipH + 6f * density

                // 课程列表
                p.color = if (isToday) s.onPrimaryContainer else s.onSurfaceVariant
                p.textSize = 9f * density
                p.typeface = Typeface.DEFAULT
                day.courses.forEachIndexed { idx, course ->
                    val name = course.courseName
                    val maxTextWidth = colW - 8f * density
                    val displayName = if (p.measureText(name) > maxTextWidth) {
                        var n = name
                        while (n.isNotEmpty() && p.measureText("$n…") > maxTextWidth) n = n.dropLast(1)
                        "$n…"
                    } else name
                    canvas.drawText(displayName, x + 4f * density, cy + 9f * density, p)
                    cy += 14f * density
                    if (idx < day.courses.size - 1) {
                        // 分隔线
                        p.color = (s.onSurfaceVariant and 0x00FFFFFF) or 0x40000000
                        canvas.drawRect(x + 4f * density, cy - 2f * density, x + colW - 4f * density, cy - 1f * density, p)
                        cy += 1f * density
                    }
                }
            }
        }

        return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
    }

    /**
     * TwoDay widget 渲染 — 今天 + 明天
     */
    fun renderTwoDay(context: Context, data: TwoDayData, wDp: Float, hDp: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (wDp * density).toInt()
        val h = (hDp * density).toInt()
        val s = scheme(data.isDark)

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(c)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // 背景
        p.color = s.bg
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()),
            20f * density, 20f * density, p)

        val ctx = SleepyApp.get()
        val pad = 12f * density
        var y = pad

        // 顶部标签
        p.color = s.primary
        p.textSize = 13f * density
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(ctx.getString(R.string.widget_twoday_label), pad, y + 13f * density, p)
        y += 22f * density

        if (!data.hasTable || data.days.isEmpty()) {
            p.color = s.onSurface
            p.textSize = 15f * density
            canvas.drawText(ctx.getString(R.string.widget_create_schedule), pad, y + 15f * density, p)
            return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
        }

        data.days.forEach { day ->
            // 天标题
            p.color = s.primary
            p.textSize = 12f * density
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val title = when {
                day.isToday -> ctx.getString(R.string.today_today)
                day.isTomorrow -> ctx.getString(R.string.tomorrow)
                else -> day.dayName
            }
            canvas.drawText(title, pad, y + 12f * density, p)
            val titleW = p.measureText(title)

            p.color = s.onSurfaceVariant
            p.textSize = 10f * density
            p.typeface = Typeface.DEFAULT
            canvas.drawText(day.dayLabel, pad + titleW + 6f * density, y + 12f * density, p)
            y += 16f * density

            if (day.courses.isEmpty()) {
                p.color = s.onSurfaceVariant
                p.textSize = 11f * density
                canvas.drawText(ctx.getString(R.string.no_course), pad, y + 11f * density, p)
                y += 16f * density
            } else {
                val rowH = 32f * density
                val rowW = w - pad * 2
                day.courses.take(3).forEachIndexed { idx, course ->
                    drawCourse(canvas, p, course, day.timeJson, pad, y, rowW, rowH, s, density, fontSizeSp = 11f)
                    y += rowH + 3f * density
                }
                if (day.courses.size > 3) {
                    p.color = s.onSurfaceVariant
                    p.textSize = 10f * density
                    canvas.drawText(ctx.getString(R.string.more_sections, day.courses.size - 3),
                        pad, y + 10f * density, p)
                    y += 14f * density
                }
            }

            if (day != data.days.last()) y += 6f * density
        }

        return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
    }
}