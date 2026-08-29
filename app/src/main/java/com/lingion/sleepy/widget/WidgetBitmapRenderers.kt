package com.lingion.sleepy.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.CourseColorUtil
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Canvas bitmap 渲染器 — 各 Receiver.loadDataSync 拉数据后由本对象渲染，
 * 输出 PNG bitmap 推给 RemoteViews（生产桌面渲染 + WidgetRenderActivity 调试预览共用）。
 *
 * 4 个 widget 复用同一份 scheme，色彩与 app 主题一致。
 */
object WidgetBitmapRenderers {

    // ── Scheme 颜色（与 WidgetContent.resolveSchemePublic 一致） ──
    // 死代码清理: cPrimary…cPractice 9 个课程色字段与 surface 字段赋值后从未被渲染消费
    // (课程底色走 CourseColorUtil, 背景实际用 bg/surfaceContainer), 已删。
    data class Scheme(
        val bg: Int,
        val primary: Int,
        val primaryContainer: Int,
        val onPrimaryContainer: Int,
        val onSurface: Int,
        val onSurfaceVariant: Int,
        val surfaceContainer: Int,
        val surfaceVariant: Int,
        val isDark: Boolean
    )

    /**
     * 主题色 — 走 resolveSchemePublic (WidgetContent.kt, 全部 widget 渲染共用)
     * 之前硬编码 Default 紫色 → 不跟随 app 主题/system 动态取色 → 移植到 RemoteViews 后仍是错的。
     * 现在接收 themeKey, 完全对齐 WeekGridWidgetProvider.renderBitmap 的取色方式。
     */
    private fun scheme(context: Context, themeKey: String, isDark: Boolean): Scheme {
        val s = resolveSchemePublic(context, themeKey, isDark)
        fun androidx.compose.ui.graphics.Color.toIntArgb(): Int =
            (0xFF shl 24) or ((this.red * 255).toInt() shl 16) or
                ((this.green * 255).toInt() shl 8) or (this.blue * 255).toInt()
        return Scheme(
            bg = s.bg.toIntArgb(),
            primary = s.primary.toIntArgb(),
            primaryContainer = s.primaryContainer.toIntArgb(),
            onPrimaryContainer = s.onPrimaryContainer.toIntArgb(),
            onSurface = s.onSurface.toIntArgb(),
            onSurfaceVariant = s.onSurfaceVariant.toIntArgb(),
            surfaceContainer = s.surfaceContainer.toIntArgb(),
            surfaceVariant = s.surfaceVariant.toIntArgb(),
            isDark = isDark
        )
    }

    // hslToColorInt / pickCourseColor 本地副本已收敛至 util/CourseColorUtil.kt (决策 D3 单一事实来源)。
    // 之前用 resolveCourseColorKey 关键词分类 → 与首页/WeekGrid 色系不一致, 已废弃。

    private val dayLabels = arrayOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private fun drawCourse(
        c: Canvas, p: Paint, course: CourseEntity, timeJson: String, x: Float, y: Float, w: Float, h: Float,
        scheme: Scheme, density: Float, fontSizeSp: Float = 11f, colorless: Boolean = false,
        displayMode: String = "node"
    ) {
        // 统一取色入口 (决策 D3) — colorless 灰底传 scheme.surfaceVariant 的 Int 值
        val bgColor = CourseColorUtil.pickCourseColorInt(course, scheme.isDark, scheme.surfaceVariant, colorless)
        // 文字色亮度自适应 (决策 D5-13) — 深色自定义课色上切白字, 浅色底仍 onSurface
        val textColor = CourseColorUtil.textColorOn(bgColor, scheme.isDark, scheme.onSurface)
        val pad = (3f * density).coerceAtLeast(1f)
        p.color = bgColor
        c.drawRoundRect(RectF(x, y, x + w, y + h), 8f * density, 8f * density, p)

        // 时间 + 地点 — 先算 meta 文本 (需要知道是否有第二行才能居中)
        // displayMode (决策 D5-12, 对齐 CourseTableView.LessonRow):
        //   "time" → 具体时间段 "08:00-09:35"; "node"(默认) → 节次 "3-4节"
        val timeStr = if (displayMode == "time" && timeJson.isNotBlank()) {
            TimeTableUtils.courseTimeString(
                courseStartNode = course.startNode,
                courseStep = course.step,
                timeJson = timeJson,
                ownTime = course.ownTime,
                startTime = course.startTime,
                endTime = course.endTime
            ) ?: course.shortNodeString(SleepyApp.get())
        } else {
            course.shortNodeString(SleepyApp.get())
        }
        val meta = if (course.room.isNotBlank()) "$timeStr · ${course.room}" else timeStr
        val hasMeta = meta.isNotBlank()

        // 字号
        val nameSize = fontSizeSp * density
        val metaSize = (fontSizeSp - 2f) * density
        val lineGap = 2f * density

        // 用 FontMetrics 算真实行高 → 垂直居中两行文字块
        p.textSize = nameSize
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.isAntiAlias = true
        val fmName = p.fontMetrics
        val nameH = fmName.descent - fmName.ascent

        var metaH = 0f
        var fmMeta: Paint.FontMetrics? = null
        if (hasMeta) {
            p.textSize = metaSize
            fmMeta = p.fontMetrics
            metaH = fmMeta!!.descent - fmMeta.ascent
        }

        val totalH = nameH + (if (hasMeta) lineGap + metaH else 0f)
        val blockTop = y + (h - totalH) / 2f

        // 课程名 — 亮度自适应文字色 (决策 D5-13)
        p.textSize = nameSize
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.color = textColor
        val name = course.courseName
        val maxWidth = w - pad * 2
        val displayName = if (p.measureText(name) > maxWidth) {
            var n = name
            while (n.isNotEmpty() && p.measureText("$n…") > maxWidth) n = n.dropLast(1)
            "$n…"
        } else name
        c.drawText(displayName, x + pad, blockTop - fmName.ascent, p)

        // 时间 + 地点 — 亮度自适应文字色 (决策 D5-13), 非 onSurfaceVariant(灰)
        if (hasMeta) {
            p.textSize = metaSize
            p.typeface = Typeface.DEFAULT
            p.color = textColor
            c.drawText(meta, x + pad, blockTop + nameH + lineGap - fmMeta!!.ascent, p)
        }
    }

    /**
     * Today widget 渲染 — 今日课程列表
     */
    fun renderToday(context: Context, data: WidgetData, wDp: Float, hDp: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (wDp * density).toInt()
        val h = (hDp * density).toInt()
        val s = scheme(context, data.themeKey, data.isDark)
        val colorless = AppPrefs.isWidgetColorless(context)
        // 用户显示设置 (决策 D5-12, 读法对齐 WeekGridWidgetProvider.loadWeekData L660-662)
        val displayMode = AppPrefs.getDisplayMode(context)
        val showDate = AppPrefs.isShowDate(context)

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

        // 标题行：今天 · 周X  +  日期 (showDate=false 时隐藏右侧日期, 对齐课表页设置)
        val ctx = SleepyApp.get()
        p.color = s.primary
        p.textSize = 13f * density
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val titleStr = "${ctx.getString(R.string.today_today)} · ${DateUtils.localizedDay(data.date.dayOfWeek.value, ctx)}"
        canvas.drawText(titleStr, pad, y + 13f * density, p)

        if (showDate) {
            p.color = s.onSurfaceVariant
            p.textSize = 12f * density
            p.typeface = Typeface.DEFAULT
            val dateStr = "${data.date.monthValue}/${data.date.dayOfMonth}"
            val dateWidth = p.measureText(dateStr)
            canvas.drawText(dateStr, w - pad - dateWidth, y + 13f * density, p)
        }

        y += 24f * density

        if (!data.hasTable) {
            p.color = s.onSurface
            p.textSize = 15f * density
            canvas.drawText(ctx.getString(R.string.widget_create_schedule), pad, y + 15f * density, p)
            return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
        }

        // 学期外: 状态标题 + 提示行, 不画课程 (loadDataSync 已清空 courses, 此处为标题语义)
        if (data.semesterStatus != DateUtils.SemesterStatus.IN_RANGE) {
            val statusRes = if (data.semesterStatus == DateUtils.SemesterStatus.BEFORE_START)
                R.string.semester_not_started else R.string.semester_ended
            p.color = s.onSurface
            p.textSize = 15f * density
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(ctx.getString(statusRes), pad, y + 15f * density, p)
            y += 22f * density
            p.color = s.onSurfaceVariant
            p.textSize = 11f * density
            p.typeface = Typeface.DEFAULT
            canvas.drawText(ctx.getString(R.string.today_semester_out_hint), pad, y + 11f * density, p)
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

        // 课程列表（全部渲染，不再截断）
        val rowH = 38f * density
        val rowGap = 10f * density  // 课程胶囊间距放大(用户反馈太紧凑)
        val rowW = w - pad * 2

        data.courses.forEachIndexed { idx, course ->
            drawCourse(canvas, p, course, data.timeJson, pad, y, rowW, rowH, s, density,
                fontSizeSp = 12f, colorless = colorless, displayMode = displayMode)
            y += rowH
            if (idx < data.courses.size - 1) y += rowGap
        }

        return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
    }

    /**
     * Today 内容全展开高度(dp) — 可滚动条带渲染用。
     * 纯计算零绘制; 布局常量逐一镜像 renderToday (改那边必须同步这边)。
     */
    fun todayContentHeightDp(data: WidgetData): Float {
        // 标题区: pad(14) + 标题行(24) — 与 renderToday: y=pad; y+=24
        var h = 14f + 24f
        if (!data.hasTable) return h + 20f          // "去创建课表" 一行
        if (data.semesterStatus != DateUtils.SemesterStatus.IN_RANGE) return h + 22f + 14f  // 学期状态 + 提示行
        if (data.courses.isEmpty()) return h + 22f + 14f  // 无课标题 + 休息副行
        val rowH = 38f
        val rowGap = 10f
        h += data.courses.size * rowH + (data.courses.size - 1) * rowGap
        h += 14f                                    // 底部 pad
        return h
    }

    /**
     * TwoDay 内容全展开高度(dp) — 可滚动条带渲染用。常量镜像 renderTwoDay。
     */
    fun twoDayContentHeightDp(data: TwoDayData): Float {
        var h = 12f + 22f                           // pad + 顶部标签行
        if (!data.hasTable || data.days.isEmpty()) return h + 20f
        if (data.semesterStatus != DateUtils.SemesterStatus.IN_RANGE) return h + 22f + 14f  // 状态 + 提示
        // 最高一列决定整体高度; 每列: 列头(20) + 课程(44+8)*n / "无课程"一行
        val colH = data.days.maxOf { day ->
            if (day.courses.isEmpty()) 20f + 16f
            else 20f + day.courses.size * 44f + (day.courses.size - 1) * 8f
        }
        h += colH + 12f                             // 底部 pad
        return h
    }

    /**
     * WeekList 内容全展开高度(dp) — 可滚动条带渲染用。常量镜像 renderWeekList。
     */
    fun weekListContentHeightDp(context: Context, data: WeekData): Float {
        val outerPad = 6f
        if (!data.hasTable) return outerPad * 2 + 20f
        val visibleDays = AppPrefs.getVisibleDays(context)
        val shownDays = if (visibleDays.isEmpty()) data.days
            else data.days.filter { it.dayOfWeek in visibleDays }.sortedBy { it.dayOfWeek }
        if (shownDays.isEmpty()) return outerPad * 2 + 20f
        // 学期外状态行: 顶部全宽 +16dp (renderWeekList 学期外段)
        val statusH = if (data.semesterStatus != DateUtils.SemesterStatus.IN_RANGE) 16f else 0f
        // 最高一列: [状态行] + 标题(12+14) + chip 行(14+6) + 课程行 (16+3)*n
        val colH = shownDays.maxOf { day ->
            var cy = statusH + 12f + 14f
            if (day.courses.isNotEmpty()) {
                cy += 14f + 6f
                cy += day.courses.size * 16f + (day.courses.size - 1) * 3f
            }
            cy
        }
        return outerPad * 2 + colH
    }

    /**
     * WeekList widget 渲染 — 7 列日列
     */
    fun renderWeekList(context: Context, data: WeekData, wDp: Float, hDp: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (wDp * density).toInt()
        val h = (hDp * density).toInt()
        val s = scheme(context, data.themeKey, data.isDark)
        val colorless = AppPrefs.isWidgetColorless(context)
        // visibleDays (决策 D5-12, 对齐 WeekGridWidgetProvider.renderBitmap L162-163):
        // 用户"显示星期"设置决定渲染列; 设置页 UI 保证至少留 1 天, 空集时回退全周防御
        val visibleDays = AppPrefs.getVisibleDays(context)
        val shownDays = if (visibleDays.isEmpty()) data.days
            else data.days.filter { it.dayOfWeek in visibleDays }.sortedBy { it.dayOfWeek }

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

        if (!data.hasTable || shownDays.isEmpty()) {
            p.color = s.onSurface
            p.textSize = 15f * density
            canvas.drawText(SleepyApp.get().getString(R.string.widget_create_schedule),
                outerPad, outerPad + 15f * density, p)
            return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
        }

        val todayDow = LocalDate.now().dayOfWeek.value
        val colGap = 4f * density
        val dayCount = shownDays.size
        val colW = (innerW - colGap * (dayCount - 1)) / dayCount

        // 学期外: 顶部全宽状态行(只画一次; 学期前=第1周课照常预习 / 学期后=课程已清空)
        var colTop = outerPad
        if (data.semesterStatus != DateUtils.SemesterStatus.IN_RANGE) {
            val statusRes = if (data.semesterStatus == DateUtils.SemesterStatus.BEFORE_START)
                R.string.semester_not_started else R.string.semester_ended
            p.color = s.onSurfaceVariant
            p.textSize = 10f * density
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val statusText = SleepyApp.get().getString(statusRes)
            val stw = p.measureText(statusText)
            canvas.drawText(statusText, (w - stw) / 2f, outerPad + 10f * density, p)
            colTop = outerPad + 16f * density
        }

        // 列数随 visibleDays 变化 (原硬编码 7 列)
        for (i in shownDays.indices) {
            val day = shownDays[i]
            val x = outerPad + i * (colW + colGap)
            val isToday = day.dayOfWeek == todayDow
            val cardBg = if (isToday) s.primaryContainer else s.surfaceContainer

            // 列背景
            p.color = cardBg
            canvas.drawRoundRect(RectF(x, colTop, x + colW, outerPad + innerH),
                14f * density, 14f * density, p)

            var cy = colTop + 12f * density

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
                val chipFm = p.fontMetrics
                val chipBaseline = cy + (chipH - (chipFm.descent - chipFm.ascent)) / 2f - chipFm.ascent
                canvas.drawText(chipText, x + (colW - ctw) / 2, chipBaseline, p)
                cy += chipH + 6f * density

                // 课程列表 — 每门课带颜色胶囊背景
                p.textSize = 9f * density
                p.typeface = Typeface.DEFAULT
                val coursePad = 3f * density
                val courseRowH = 16f * density
                val courseGap = 3f * density
                day.courses.forEachIndexed { idx, course ->
                    val name = course.courseName
                    // 课程颜色背景 (对齐 WeekGrid 风格) — 统一入口 CourseColorUtil (决策 D3)
                    val bgColor = CourseColorUtil.pickCourseColorInt(course, s.isDark, s.surfaceVariant, colorless)
                    p.color = bgColor
                    canvas.drawRoundRect(
                        RectF(x + coursePad, cy, x + colW - coursePad, cy + courseRowH),
                        4f * density, 4f * density, p)
                    // 课程名 — FontMetrics 垂直居中 + 亮度自适应文字色 (决策 D5-13, 对齐 drawCourse 同入口)
                    p.color = CourseColorUtil.textColorOn(bgColor, s.isDark, s.onSurface)
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    val maxTextWidth = colW - coursePad * 2 - 4f * density
                    val displayName = if (p.measureText(name) > maxTextWidth) {
                        var n = name
                        while (n.isNotEmpty() && p.measureText("$n…") > maxTextWidth) n = n.dropLast(1)
                        "$n…"
                    } else name
                    val fm = p.fontMetrics
                    val textBaseline = cy + (courseRowH - (fm.descent - fm.ascent)) / 2f - fm.ascent
                    canvas.drawText(displayName, x + coursePad + 2f * density, textBaseline, p)
                    p.typeface = Typeface.DEFAULT
                    cy += courseRowH + courseGap
                }
            }
        }

        return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
    }

    /**
     * WeekView widget 渲染 — 7 列日列, 复刻 DaySummaryCell (CourseTableView.kt L559-L642)
     * 列卡片: primaryContainer(今天) / surfaceContainer(其他), 14dp 圆角
     * 课程列表: 纯文本无胶囊背景, take(5), onSurfaceVariant 色, 2dp 间距
     */
    fun renderWeekView(context: Context, data: WeekData, wDp: Float, hDp: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (wDp * density).toInt()
        val h = (hDp * density).toInt()
        val s = scheme(context, data.themeKey, data.isDark)
        val showSeparator = AppPrefs.isWidgetSeparator(context)
        // visibleDays (决策 D5-12, 对齐 WeekGridWidgetProvider.renderBitmap L162-163):
        // 用户"显示星期"设置决定渲染列; 设置页 UI 保证至少留 1 天, 空集时回退全周防御
        val visibleDays = AppPrefs.getVisibleDays(context)
        val shownDays = if (visibleDays.isEmpty()) data.days
            else data.days.filter { it.dayOfWeek in visibleDays }.sortedBy { it.dayOfWeek }

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

        if (!data.hasTable || shownDays.isEmpty()) {
            p.color = s.onSurface
            p.textSize = 15f * density
            canvas.drawText(SleepyApp.get().getString(R.string.widget_create_schedule),
                outerPad, outerPad + 15f * density, p)
            return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
        }

        val todayDow = LocalDate.now().dayOfWeek.value
        val colGap = 4f * density
        val dayCount = shownDays.size
        val colW = (innerW - colGap * (dayCount - 1)) / dayCount

        // 学期外: 顶部全宽状态行(只画一次, 同 renderWeekList)
        var colTop = outerPad
        if (data.semesterStatus != DateUtils.SemesterStatus.IN_RANGE) {
            val statusRes = if (data.semesterStatus == DateUtils.SemesterStatus.BEFORE_START)
                R.string.semester_not_started else R.string.semester_ended
            p.color = s.onSurfaceVariant
            p.textSize = 10f * density
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val statusText = SleepyApp.get().getString(statusRes)
            val stw = p.measureText(statusText)
            canvas.drawText(statusText, (w - stw) / 2f, outerPad + 10f * density, p)
            colTop = outerPad + 16f * density
        }

        // 列数随 visibleDays 变化 (原硬编码 7 列)
        for (i in shownDays.indices) {
            val day = shownDays[i]
            val x = outerPad + i * (colW + colGap)
            val isToday = day.dayOfWeek == todayDow
            val cardBg = if (isToday) s.primaryContainer else s.surfaceContainer

            // 列背景
            p.color = cardBg
            canvas.drawRoundRect(RectF(x, colTop, x + colW, outerPad + innerH),
                14f * density, 14f * density, p)

            var cy = colTop + 12f * density

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
                val chipFm = p.fontMetrics
                val chipBaseline = cy + (chipH - (chipFm.descent - chipFm.ascent)) / 2f - chipFm.ascent
                canvas.drawText(chipText, x + (colW - ctw) / 2, chipBaseline, p)
                cy += chipH + 4f * density  // 4dp gap (DaySummaryCell L624)

                // 课程 mini-list — 最多2行换行 + 课程间分隔线(可选)
                p.textSize = 9f * density
                p.typeface = Typeface.DEFAULT
                p.style = Paint.Style.FILL
                val textPad = 4f * density
                val maxTextWidth = colW - textPad * 2
                val courseGap = 3f * density  // 3dp (原2dp太紧, workflow验证阶段推荐3dp对齐胶囊版)
                val fm = p.fontMetrics
                val lineH = fm.descent - fm.ascent
                val courses = day.courses.take(5)
                courses.forEachIndexed { idx, course ->
                    val name = course.courseName
                    // today → onPrimaryContainer@0.82alpha, 其他 → onSurfaceVariant
                    p.color = if (isToday)
                        (0xD1 shl 24) or (s.onPrimaryContainer and 0x00FFFFFF)
                    else
                        s.onSurfaceVariant

                    val lines = wrapMax2Lines(name, p, maxTextWidth)
                    lines.forEach { line ->
                        canvas.drawText(line, x + textPad, cy - fm.ascent, p)
                        cy += lineH
                    }

                    // 课程间分隔: 开关ON→可见1dp@40%线; OFF→纯3dp留白
                    if (idx < courses.size - 1) {
                        if (showSeparator) {
                            cy += courseGap / 2f
                            p.color = (s.onSurfaceVariant and 0x00FFFFFF) or 0x66000000
                            p.style = Paint.Style.STROKE
                            p.strokeWidth = 1f * density
                            canvas.drawLine(x + textPad, cy, x + colW - textPad, cy, p)
                            p.style = Paint.Style.FILL
                            p.strokeWidth = 0f
                            cy += courseGap / 2f
                        } else {
                            cy += courseGap
                        }
                    } else {
                        cy += courseGap
                    }
                }
            }
        }

        return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
    }

    /**
     * TwoDay widget 渲染 — 今天 + 明天 (左右两栏竖排)
     * 用户反馈: 不要把第二天堆在底下 → 改成左列今天 / 右列明天 并排
     */
    fun renderTwoDay(context: Context, data: TwoDayData, wDp: Float, hDp: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (wDp * density).toInt()
        val h = (hDp * density).toInt()
        val s = scheme(context, data.themeKey, data.isDark)
        val colorless = AppPrefs.isWidgetColorless(context)
        // 用户显示设置 (决策 D5-12, 读法对齐 WeekGridWidgetProvider.loadWeekData L660-662)
        val displayMode = AppPrefs.getDisplayMode(context)
        val showDate = AppPrefs.isShowDate(context)

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

        // 学期外: 状态标题 + 提示行, 不画两栏课程 (loadDataSync 已清空, 此处给标题语义)
        if (data.semesterStatus != DateUtils.SemesterStatus.IN_RANGE) {
            val statusRes = if (data.semesterStatus == DateUtils.SemesterStatus.BEFORE_START)
                R.string.semester_not_started else R.string.semester_ended
            p.color = s.onSurface
            p.textSize = 15f * density
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(ctx.getString(statusRes), pad, y + 15f * density, p)
            y += 22f * density
            p.color = s.onSurfaceVariant
            p.textSize = 11f * density
            p.typeface = Typeface.DEFAULT
            canvas.drawText(ctx.getString(R.string.today_semester_out_hint), pad, y + 11f * density, p)
            return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
        }

        // 左右两栏: 每天一列, 中间竖直分隔
        val colGap = 10f * density
        val colW = (w - pad * 2 - colGap * (data.days.size - 1)) / data.days.size
        val listTop = y
        val listBottom = h - pad
        val listH = (listBottom - listTop).coerceAtLeast(40f * density)

        data.days.forEachIndexed { colIdx, day ->
            val colX = pad + colIdx * (colW + colGap)

            // 列标题
            p.color = s.primary
            p.textSize = 12f * density
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val title = when {
                day.isToday -> ctx.getString(R.string.today_today)
                day.isTomorrow -> ctx.getString(R.string.tomorrow)
                else -> day.dayName
            }
            canvas.drawText(title, colX, listTop + 12f * density, p)
            val titleW = p.measureText(title)

            // showDate=false 时隐藏列标题旁的日期 (对齐课表页设置)
            if (showDate) {
                p.color = s.onSurfaceVariant
                p.textSize = 10f * density
                p.typeface = Typeface.DEFAULT
                canvas.drawText(day.dayLabel, colX + titleW + 6f * density, listTop + 12f * density, p)
            }

            var cy = listTop + 20f * density

            if (day.courses.isEmpty()) {
                p.color = s.onSurfaceVariant
                p.textSize = 11f * density
                canvas.drawText(ctx.getString(R.string.no_course), colX, cy + 11f * density, p)
            } else {
                // 胶囊固定最大高度 44dp, 不再撑满整个列
                val rowGap = 8f * density
                val maxRowH = 44f * density
                day.courses.forEach { course ->
                    drawCourse(canvas, p, course, day.timeJson, colX, cy, colW, maxRowH, s, density,
                        fontSizeSp = 10f, colorless = colorless, displayMode = displayMode)
                    cy += maxRowH + rowGap
                }
            }

            // 列间竖直分隔线
            if (colIdx < data.days.size - 1) {
                val sepX = colX + colW + colGap / 2f
                p.color = (s.onSurfaceVariant and 0x00FFFFFF) or 0x20000000
                canvas.drawRect(sepX - 0.5f * density, listTop, sepX + 0.5f * density, listBottom, p)
            }
        }

        return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
    }

    /**
     * Canvas 手动换行: 最多2行, 超出截断 "…".
     * CJK 按字符断行; Latin 在空格处断行.
     */
    private fun wrapMax2Lines(
        text: String,
        paint: Paint,
        maxWidth: Float
    ): List<String> {
        val charsFit = paint.breakText(text, true, maxWidth, null)
        if (charsFit <= 0) return listOf("…")  // 列极窄: 连一个字都放不下
        if (charsFit >= text.length) return listOf(text)

        // 找行1断点: 优先空格, 否则字符边界
        val lastSpace = text.lastIndexOf(' ', charsFit)
        val line1End: Int
        val remainderStart: Int
        if (lastSpace > 0 && lastSpace > charsFit * 4 / 5) {
            line1End = lastSpace
            remainderStart = lastSpace + 1
        } else {
            line1End = charsFit
            remainderStart = charsFit
        }

        val line1 = text.substring(0, line1End)
        val remainder = text.substring(remainderStart)
        if (remainder.isEmpty()) return listOf(line1)

        val charsFit2 = paint.breakText(remainder, true, maxWidth, null)
        if (charsFit2 >= remainder.length) return listOf(line1, remainder)

        // 行2超宽 → 截断 "…"
        var lo = 0
        var hi = remainder.length
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (paint.measureText(remainder.substring(0, mid) + "…") <= maxWidth) lo = mid
            else hi = mid - 1
        }
        val line2 = if (lo == 0) "…" else remainder.substring(0, lo) + "…"
        return listOf(line1, line2)
    }
}