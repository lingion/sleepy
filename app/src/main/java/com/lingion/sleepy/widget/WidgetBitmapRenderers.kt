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
import com.lingion.sleepy.util.CourseDisplayUtil
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
        displayMode: String = "node",
        groupRows: List<CourseEntity> = listOf(course),
        useAlias: Boolean = false
    ) {
        // 统一取色入口 (决策 D3) — colorless 灰底传 scheme.surfaceVariant 的 Int 值
        // issue#22: 同名课程多地点 — 用 groupRows 传同 groupId 全行,支持 AUTO/CUSTOM 模式取色
        val bgColor = CourseColorUtil.pickCourseColorIntWithGroupRows(course, groupRows, scheme.isDark, scheme.surfaceVariant, colorless)
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
        val hasMeta = timeStr.isNotBlank() || course.room.isNotBlank()

        // 字号
        val nameSize = fontSizeSp * density
        val metaSize = (fontSizeSp - 2f) * density
        val lineGap = 2f * density

        // meta 行拆分(用户反馈: 宽度不够时时间+地点拼一行必溢出卡片边框)
        // metaSize 字号下行宽可容纳 → 单行(旧行为); 放不下 → 时间一行/地点一行, 每行省略号兜底
        p.textSize = metaSize
        val metaLines = if (hasMeta) {
            courseMetaLines(
                measure = { t -> p.measureText(t) },
                maxWidth = w - pad * 2,
                timeStr = timeStr,
                room = course.room
            )
        } else emptyList()

        // 用 FontMetrics 算真实行高 → 垂直居中两行文字块
        p.textSize = nameSize
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.isAntiAlias = true
        val fmName = p.fontMetrics
        val nameH = fmName.descent - fmName.ascent

        var metaH = 0f
        var fmMeta: Paint.FontMetrics? = null
        if (metaLines.isNotEmpty()) {
            p.textSize = metaSize
            fmMeta = p.fontMetrics
            metaH = fmMeta!!.descent - fmMeta.ascent
        }
        val metaLineCount = metaLines.size
        val metaBlockH = if (metaLineCount > 0) (metaLineCount - 1) * (metaH + lineGap) + metaH else 0f

        val totalH = nameH + (if (metaLineCount > 0) lineGap + metaBlockH else 0f)
        val blockTop = y + (h - totalH) / 2f

        // 课程名 — 亮度自适应文字色 (决策 D5-13)
        p.textSize = nameSize
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.color = textColor
        // issue#26: widget 场景别名 — 渲染时读全局 widget 开关(先例: colorless)
        val name = CourseDisplayUtil.displayName(course, useAlias)
        val maxWidth = w - pad * 2
        val displayName = if (p.measureText(name) > maxWidth) {
            var n = name
            while (n.isNotEmpty() && p.measureText("$n…") > maxWidth) n = n.dropLast(1)
            "$n…"
        } else name
        c.drawText(displayName, x + pad, blockTop - fmName.ascent, p)

        // 时间/地点 — 拆行后逐行绘制, 每行省略号兜底
        if (metaLines.isNotEmpty()) {
            p.textSize = metaSize
            p.typeface = Typeface.DEFAULT
            p.color = textColor
            var my = blockTop + nameH + lineGap
            for (line in metaLines) {
                c.drawText(ellipsize(p, line, maxWidth), x + pad, my - fmMeta!!.ascent, p)
                my += metaH + lineGap
            }
        }
    }

    /**
     * Today widget 渲染 — 今日课程列表
     * SMALL 变体 + 容器 <150dp → 走紧凑档(纯文本); REGULAR 或容器被拖大 ≥150dp → 全量排版
     * (默认参数 REGULAR → 全部现有调用点零改动; 大档路径 renderTodayRegular 函数体标题行
     * 在 emptyHeader=true 时整体不画(标题/右侧槽位/‹›小箭头全跳过, y 前进量保留 →
     * 内容纵坐标与带头模式逐像素一致) — 今日导航版用真实 RemoteViews 视图(TextView+按钮)
     * 覆盖顶栏, bitmap 头部必须留白, 否则双重标题; WeekGrid 最小档/旧调用方默认 false 不受影响。
     *
     * pageOffsetDp > 0 时从内容纵轴该偏移起画 (v4 翻页遗留机制, 现调用方恒传默认
     * 0; 行原子切片逻辑保留备用)。默认 0 = 现有调用点逐字节不变。
     *
     * v6: emptyHeader=true 时 24dp 头部前进量默认仍保留 (空档, 供真实视图顶栏覆盖
     * 的静态档用); headerSpace=true 把这 24dp 整段删掉 — 条带长图从第一行课程直接
     * 起 (overflow 竖排布局里顶栏是上方独立行, 位图不需要头部空档)。
     * 静态档缺省 false 逐字节不变。
     */
    fun renderToday(
        context: Context, data: WidgetData, wDp: Float, hDp: Float,
        variant: WidgetVariant = WidgetVariant.REGULAR,
        emptyHeader: Boolean = false,
        pageOffsetDp: Float = 0f,
        headerSpace: Boolean = false
    ): Bitmap {
        // v9.3: 状态内容 (无课表/学期外/无课) 强制单页 — 状态分支画固定 y,
        // 分页只会产出 N 张同图; 单页兜底闸在这里, 条带端无须逐 scope 重复。
        val effectiveOffset = if (isTodayStatusContent(data)) 0f else pageOffsetDp
        return renderTodayRegular(context, data, wDp, hDp, emptyHeader, effectiveOffset, headerSpace)
    }

    /**
     * Today 状态内容判定 (纯 JVM 可测) — 无课表 / 学期外 / 无课。
     * 与 renderTodayRegular 的三个提前 return 分支逐一对应:
     * 这些内容在固定 y 画状态行, 无可翻页的行轴 → 强制 pageOffset=0。
     */
    fun isTodayStatusContent(data: WidgetData): Boolean =
        !data.hasTable ||
            data.semesterStatus != DateUtils.SemesterStatus.IN_RANGE ||
            data.courses.isEmpty()

    /**
     * 小档纯文本行(渲染与单测共用单一事实来源)。空课表/学期外也各有对应一行。
     * resolver 抽象掉 Context 资源访问 → 核心选取逻辑可在纯 JVM 单测断言(仓库无 Robolectric)。
     */
    fun todayCompactTexts(context: Context, data: WidgetData): List<String> =
        todayCompactTexts({ resId -> context.getString(resId) }, AppPrefs.isWidgetUseAlias(context), data)

    /** 同上 — resolver 注入版(纯 JVM 单测入口) */
    fun todayCompactTexts(resolve: (Int) -> String, data: WidgetData): List<String> =
        todayCompactTexts(resolve, useAlias = false, data = data)

    /** resolver + 别名开关注入版 — useAlias 语义: true 时显示别名(空回退原名) */
    fun todayCompactTexts(resolve: (Int) -> String, useAlias: Boolean, data: WidgetData): List<String> {
        if (!data.hasTable) return listOf(resolve(R.string.widget_create_schedule))
        if (data.semesterStatus != DateUtils.SemesterStatus.IN_RANGE) {
            val statusRes = if (data.semesterStatus == DateUtils.SemesterStatus.BEFORE_START)
                R.string.semester_not_started else R.string.semester_ended
            return listOf(resolve(statusRes))
        }
        if (data.courses.isEmpty()) return listOf(resolve(R.string.today_no_course))
        return data.courses.map { CourseDisplayUtil.displayName(it, useAlias) }
    }

    /** 标题行内容三元组 — 渲染与单测共用单一事实来源 (issue #24 Feature2 日期导航)。 */
    data class TodayHeaderParts(
        val title: String,
        val rightText: String?,
        val rightIsAction: Boolean
    )

    /**
     * 标题行内容 (纯 JVM 可断言, resolver 注入抽掉 Context):
     * 今日态 → 「今天 · 周X」+ 可选右侧日期 (showDate);
     * 导航态 → 「M/D · 周X」+ 右侧「回到今天」(showDate 不影响右侧, 保可发现性)。
     */
    fun todayHeaderParts(
        data: WidgetData, dayName: String, showDate: Boolean,
        resolve: (Int) -> String
    ): TodayHeaderParts {
        val title = if (data.isToday) "${resolve(R.string.today_today)} · $dayName"
                    else "${data.dateLabel} · $dayName"
        return if (!data.isToday) {
            TodayHeaderParts(title, resolve(R.string.today_nav_back_to_today), true)
        } else if (showDate) {
            TodayHeaderParts(title, data.dateLabel, false)
        } else {
            TodayHeaderParts(title, null, false)
        }
    }

    /**
     * Today 紧凑档 — 日期小字(顶) + 状态/首课程名(居中), 纯文本无课程胶囊。
     * 布局常量: compact 档不参与 todayContentHeightDp 滚动条带估算(固定 size 变体), 无需镜像。
     */
    private fun renderTodayCompact(context: Context, data: WidgetData, wDp: Float, hDp: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (wDp * density).toInt()
        val h = (hDp * density).toInt()
        val s = scheme(context, data.themeKey, data.isDark)
        val ctx = SleepyApp.get()

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(c)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // 背景圆角
        p.color = s.bg
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()),
            20f * density, 20f * density, p)

        val pad = 10f * density
        val lines = todayCompactTexts(ctx, data)

        // 日期行(顶部小字)
        p.color = s.onSurfaceVariant
        p.textSize = 11f * density
        p.typeface = Typeface.DEFAULT
        val dateStr = "${data.date.monthValue}/${data.date.dayOfMonth}"
        canvas.drawText(dateStr, pad, pad + 11f * density, p)

        // 状态/首课程名 — 居中大字
        p.color = s.onSurface
        p.textSize = 15f * density
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        var y = h / 2f
        for (line in lines.take(2)) {
            canvas.drawText(ellipsize(p, line, w - pad * 2), pad, y, p)
            y += 20f * density
        }

        return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
    }

    /** 按可用宽度截断文本(字符级贪心, 与 [[sleepy-vert-text-overflow-fix]] 同思路) */
    private fun ellipsize(p: Paint, text: String, maxW: Float): String {
        if (p.measureText(text) <= maxW) return text
        var t = text
        while (t.isNotEmpty() && p.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    /**
     * Today 全量排版 — 原 renderToday 函数体原样改名迁入(REGULAR 档逐字节不变保证)
     */
    private fun renderTodayRegular(
        context: Context, data: WidgetData, wDp: Float, hDp: Float,
        emptyHeader: Boolean, pageOffsetDp: Float = 0f,
        headerSpace: Boolean = false
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (wDp * density).toInt()
        val h = (hDp * density).toInt()
        val s = scheme(context, data.themeKey, data.isDark)
        val colorless = AppPrefs.isWidgetColorless(context)
        // issue#26: widget 场景别名 — 渲染时读全局 widget 开关
        val useAlias = AppPrefs.isWidgetUseAlias(context)
        // 用户显示设置 (决策 D5-12, 读法对齐 WeekGridWidgetProvider.loadWeekData L660-662)
        val displayMode = AppPrefs.getDisplayMode(context)
        val showDate = AppPrefs.isShowDate(context)
        val ctx = SleepyApp.get()

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

        // 标题行 — emptyHeader=true 时整体不画: 今日导航版顶栏用真实 RemoteViews 视图
        // (TextView 标题 + 三角按钮) 覆盖, bitmap 头部留白防双重标题; y 前进量保留 →
        // 内容纵坐标与带头渲染逐像素一致。WeekGrid 最小档等旧调用方 (false) 逐字节不变。
        // v9.3: 续页 (pageOffsetDp > 0) 同样不画 — 每页是虚拟长条的完整 viewport raw
        // crop, 再画头 = 第 2 页起每页重复一份 ‹›/标题/右侧槽位 (v9.2 只看 emptyHeader
        // 的根因)。页 0 (offset=0) 与静态壳图同参同函数, 逐像素一致。
        if (!emptyHeader && pageOffsetDp <= 0f) {
            val header = todayHeaderParts(
                data, DateUtils.localizedDay(data.date.dayOfWeek.value, ctx), showDate
            ) { ctx.getString(it) }
            val titleX = pad
            val rightX = w - pad
            p.color = s.onSurfaceVariant
            p.textSize = 16f * density
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("‹", pad, y + 13f * density, p)
            val arrowW = p.measureText("›")
            canvas.drawText("›", w - pad - arrowW, y + 13f * density, p)
            // 标题 (右侧槽位存在时按需截断)
            p.color = s.primary
            p.textSize = 13f * density
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(
                ellipsize(p, header.title, (rightX - 4f * density - titleX).coerceAtLeast(40f * density)),
                titleX, y + 13f * density, p
            )
            // 右侧槽位: 导航态「回到今天」(primary 加粗 action 样式) / 今日态日期 (次要样式)
            if (header.rightText != null) {
                if (header.rightIsAction) {
                    p.color = s.primary
                    p.textSize = 11f * density
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                } else {
                    p.color = s.onSurfaceVariant
                    p.textSize = 12f * density
                    p.typeface = Typeface.DEFAULT
                }
                val rw = p.measureText(header.rightText)
                canvas.drawText(header.rightText, rightX - rw, y + 13f * density, p)
            }
        }

        // v6 headerSpace: 条带长图不要头部空档 (顶栏在布局里是上方独立行) → 24dp 前进量整段跳过。
        // 各状态行 (无课表/学期外/无课/课程列表) 都在 y+=24 之后定位 → 只需跳过这次前进。
        if (!headerSpace) y += 24f * density

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
        // v7.10.11: 冲突分栏 — 与 App 今日页/周视图同一引擎(weekLaneRows),
        // 冲突区域一行内并排(栏间浅细竖线), 同栏课纵向堆叠, 无冲突课整宽。
        // 栏内多课时该行实际高度由最高栏决定(各栏 y 游标独立推进后再取 max 对齐)。
        val rowH = 38f * density
        val rowGap = 10f * density  // 课程胶囊间距放大(用户反馈太紧凑)
        val rowW = w - pad * 2

        val laneRows = com.lingion.sleepy.util.ConflictLayoutEngine.weekLaneRows(data.courses)
        val sepColor = (s.onSurface and 0x00FFFFFF) or 0x4D000000  // 30% 黑(浅色主题下=浅灰细线)
        val stackGap = 3f * density
        // v4 翻页: 行是原子单元 (冲突分栏行不可拦腰切)。先按全展开 y 推进一遍算出
        // 每行的 [top, bottom) px 区间, 落在 [offset, offset+位图全高) 的行才画;
        // 绘制时 y = rowTop − offset — 行内相对布局与全展开渲染逐像素一致。
        // v8: 行几何单一真值 — span 起点随 headerSpace 参数化 (旧硬编码 14+24 = 去头
        // 条带顶部 24dp 死带 + 末行被可见过滤丢弃的镜像失配根因)。
        // v9.3: 窗 = 完整位图高 h (每页 = 虚拟长条的完整 viewport raw crop) —
        // v9.2 窗扣 52dp chrome 而页位图是全 viewport 高 → 页间重叠 + 页底空带根因。
        val offsetPx = pageOffsetDp * density
        data class RowSpan(val row: com.lingion.sleepy.util.ConflictLayoutEngine.WeekLaneRow,
                           val topPx: Float, val bottomPx: Float)
        val spans = TodayRowGeometry.rowSpans(data.courses, headerSpace)
            .map { RowSpan(it.row, it.topDp * density, it.bottomDp * density) }
        val visible = spans.filter { it.bottomPx > offsetPx && it.topPx < offsetPx + h }
        visible.forEach { span ->
            val row = span.row
            val y = span.topPx - offsetPx
            if (row.laneCount == 1) {
                drawCourse(canvas, p, row.courses[0], data.timeJson, pad, y, rowW, rowH, s, density,
                    fontSizeSp = 12f, colorless = colorless, displayMode = displayMode,
                    groupRows = data.courses.filter { it.groupId == row.courses[0].groupId },
                    useAlias = useAlias)
            } else {
                val laneGap = 5f * density
                val laneW = (rowW - laneGap * (row.laneCount - 1)) / row.laneCount
                val laneRowTotalH = span.bottomPx - span.topPx
                repeat(row.laneCount) { li ->
                    val laneX = pad + li * (laneW + laneGap)
                    // 栏间浅细竖线(与 App 分栏同语义)
                    if (li > 0) {
                        val sepX = laneX - laneGap / 2f
                        val keepColor = p.color
                        p.color = sepColor
                        canvas.drawRect(sepX - 0.5f * density, y, sepX + 0.5f * density,
                            y + laneRowTotalH, p)
                        p.color = keepColor
                    }
                    val laneCourses = row.courses.filter { row.laneOf[it.id] == li }
                    var ly = y
                    laneCourses.forEachIndexed { ci, laneCourse ->
                        drawCourse(canvas, p, laneCourse, data.timeJson, laneX, ly, laneW, rowH, s, density,
                            fontSizeSp = 10f, colorless = colorless, displayMode = displayMode,
                            groupRows = data.courses.filter { it.groupId == laneCourse.groupId },
                            useAlias = useAlias)
                        ly += rowH
                        if (ci < laneCourses.size - 1) ly += stackGap
                    }
                }
            }
        }

        return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
    }

    /**
     * Today 内容全展开高度(dp) — 可滚动条带渲染用。
     * 纯计算零绘制; 布局常量逐一镜像 renderToday (改那边必须同步这边)。
     * v7.10.11: 冲突分栏行高按最高栏堆叠数算(与 renderToday 分栏镜像)。
     * v6 headerSpace: true 时头部 24dp 前进量不计 (条带去头, 顶栏是布局独立行),
     * 与 renderToday(headerSpace=true) 逐常量镜像。
     * v9.3: 状态内容 (无课表/学期外/无课) 报单页高度 (顶 pad 之内, ≤ 任何 sane
     * viewport) — 状态分支画固定 y, 报为可翻页内容 = 条带产出 N 张同图根因。
     */
    fun todayContentHeightDp(data: WidgetData, headerSpace: Boolean = false): Float {
        if (isTodayStatusContent(data)) return TodayRowGeometry.contentTopDp(headerSpace)
        // v8: 行几何单一真值 — 与 renderTodayRegular 同调 TodayRowGeometry (镜像失配根除)
        return TodayRowGeometry.contentHeightDp(data.courses, headerSpace)
    }

    // ── 今日导航顶栏按钮 (issue #24: 低对比圆角矩形 + 三角形图标) ──

    /** 视图口径: 按钮视图 40×28dp(即点击热区, 大于可视矩形), 顶栏条高 36dp。 */
    const val NAV_BUTTON_W_DP = 40f
    const val NAV_BUTTON_H_DP = 28f
    const val NAV_HEADER_H_DP = 36f

    /**
     * 今日导航三角按钮 — 40×28dp 位图, 内缩 4dp×3dp 的圆角矩形(半径 7dp),
     * 低对比配色: surfaceVariant 底 + onSurfaceVariant 三角形图标 (M3 标准安静配色,
     * 修掉上一版 primary 高饱和实心圆钮的“对比度太高”)。三角形走 Canvas Path,
     * 禁字体 glyph (‹› U+25B8 依赖系统字体)。
     */
    fun renderNavTriangle(context: Context, data: WidgetData, pointLeft: Boolean): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (NAV_BUTTON_W_DP * density).toInt()
        val h = (NAV_BUTTON_H_DP * density).toInt()
        val s = scheme(context, data.themeKey, data.isDark)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // 低对比圆角矩形底 — 内缩留出的透明边 = 按钮之间的呼吸空隙, 视图本身即点击区
        p.color = s.surfaceVariant
        c.drawRoundRect(RectF(4f * density, 3f * density, w - 4f * density, h - 3f * density),
            7f * density, 7f * density, p)

        // 三角形图标 — tip 3.5dp, 半高 4dp, 视觉居中 (底边中心后移 0.5dp)
        val cx = w / 2f
        val cy = h / 2f
        val tipDx = (if (pointLeft) -3.5f else 3.5f) * density
        val path = android.graphics.Path().apply {
            moveTo(cx + tipDx, cy)
            lineTo(cx - tipDx, cy - 4f * density)
            lineTo(cx - tipDx, cy + 4f * density)
            close()
        }
        p.color = s.onSurfaceVariant
        c.drawPath(path, p)
        return bmp
    }

    /**
     * 今日导航顶栏运行时配色 — 顶栏真实视图 (标题/动作文字/背景) 与卡面 bitmap 同一 scheme
     * 取色 (单一事实来源), 防止 TextView 与 Canvas 渲染色彩漂移。
     */
    data class TodayNavHeaderColors(val title: Int, val action: Int, val bg: Int)

    fun todayNavHeaderColors(context: Context, data: WidgetData): TodayNavHeaderColors {
        val s = scheme(context, data.themeKey, data.isDark)
        return TodayNavHeaderColors(title = s.primary, action = s.primary, bg = s.bg)
    }

    /**
     * TwoDay 小档纯文本行(渲染与单测共用单一事实来源)。
     * 状态资源与 renderTwoDayRegular 各分支逐一对应:
     *   无课表→widget_create_schedule · 学期外→semester_not_started/semester_ended
     *   今日无课→no_course(regular 两栏空列同资源) · 有课→今日首课名单行
     * resolver 抽象掉 Context 资源访问 → 核心选取逻辑可在纯 JVM 单测断言(仓库无 Robolectric)。
     */
    fun twoDayCompactTexts(context: Context, data: TwoDayData): List<String> =
        twoDayCompactTexts({ resId -> context.getString(resId) }, AppPrefs.isWidgetUseAlias(context), data)

    /** 同上 — resolver 注入版(纯 JVM 单测入口) */
    fun twoDayCompactTexts(resolve: (Int) -> String, data: TwoDayData): List<String> =
        twoDayCompactTexts(resolve, useAlias = false, data = data)

    /** resolver + 别名开关注入版 — useAlias 语义: true 时显示别名(空回退原名) */
    fun twoDayCompactTexts(resolve: (Int) -> String, useAlias: Boolean, data: TwoDayData): List<String> {
        if (!data.hasTable || data.days.isEmpty()) return listOf(resolve(R.string.widget_create_schedule))
        if (data.semesterStatus != DateUtils.SemesterStatus.IN_RANGE) {
            val statusRes = if (data.semesterStatus == DateUtils.SemesterStatus.BEFORE_START)
                R.string.semester_not_started else R.string.semester_ended
            return listOf(resolve(statusRes))
        }
        val today = data.days.first()
        if (today.courses.isEmpty()) return listOf(resolve(R.string.no_course))
        return data.days.flatMap { it.courses }.map { CourseDisplayUtil.displayName(it, useAlias) }
    }

    /**
     * TwoDay 紧凑档 — 日期小字(顶) + 状态/今日首课名(居中), 纯文本无两栏课程胶囊。
     * 布局常量: compact 档不参与 twoDayContentHeightDp 滚动条带估算(固定 size 变体), 无需镜像。
     */
    private fun renderTwoDayCompact(context: Context, data: TwoDayData, wDp: Float, hDp: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (wDp * density).toInt()
        val h = (hDp * density).toInt()
        val s = scheme(context, data.themeKey, data.isDark)
        val ctx = SleepyApp.get()

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(c)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // 背景圆角
        p.color = s.bg
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()),
            20f * density, 20f * density, p)

        val pad = 10f * density
        val lines = twoDayCompactTexts(ctx, data)

        // 日期行(顶部小字) — 今日日期
        p.color = s.onSurfaceVariant
        p.textSize = 11f * density
        p.typeface = Typeface.DEFAULT
        val dateStr = data.days.firstOrNull()?.let { "${it.date.monthValue}/${it.date.dayOfMonth}" } ?: ""
        canvas.drawText(dateStr, pad, pad + 11f * density, p)

        // 状态/今日首课名 — 居中大字
        p.color = s.onSurface
        p.textSize = 15f * density
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        var y = h / 2f
        for (line in lines.take(2)) {
            canvas.drawText(ellipsize(p, line, w - pad * 2), pad, y, p)
            y += 20f * density
        }

        return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
    }

    /**
     * TwoDay 内容全展开高度(dp) — 可滚动条带渲染用。常量镜像 renderTwoDay。
     * v7.10.11: 冲突分栏行高按最高栏堆叠数算(与 renderTwoDayRegular 分栏镜像)。
     */
    fun twoDayContentHeightDp(data: TwoDayData): Float {
        var h = 12f + 22f                           // pad + 顶部标签行
        if (!data.hasTable || data.days.isEmpty()) return h + 20f
        if (data.semesterStatus != DateUtils.SemesterStatus.IN_RANGE) return h + 22f + 14f  // 状态 + 提示
        // 最高一列决定整体高度; 每列: 列头(20) + 冲突分行课程 / "无课程"一行
        val colH = data.days.maxOf { day ->
            if (day.courses.isEmpty()) return@maxOf 20f + 16f
            var cy = 20f
            val rows = com.lingion.sleepy.util.ConflictLayoutEngine.weekLaneRows(day.courses)
            rows.forEach { row ->
                if (row.laneCount == 1) {
                    cy += 44f + 8f
                } else {
                    val maxStack = row.courses.groupBy { row.laneOf[it.id] }.values
                        .maxOf { it.size }.coerceAtLeast(1)
                    cy += maxStack * 44f + (maxStack - 1) * 3f + 8f
                }
            }
            cy
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
     * WeekList 小档纯文本行(渲染与单测共用单一事实来源)。
     * 状态资源与 renderWeekListRegular 各分支逐一对应:
     *   无课表→widget_create_schedule · 学期外→semester_not_started/semester_ended
     *   今明全无课→no_course(regular 空列同资源) · 有课→今天+明天各一条"周X 课名"
     *     (每天只取首课 — loadDataSync 已按 startNode 排序; 无课天跳过; 最多 2 行)
     * resolver 抽象掉 Context 资源访问 + today 锚点注入星期计算(禁 LocalDate.now() 进逻辑)
     * → 核心选取逻辑可在纯 JVM 单测断言(仓库无 Robolectric)。
     */
    fun weekListCompactTexts(context: Context, today: LocalDate, data: WeekData): List<String> =
        weekListCompactTexts(
            { resId -> context.getString(resId) },
            { dow -> DateUtils.localizedDay(dow, context) },
            AppPrefs.isWidgetUseAlias(context),
            today, data
        )

    /** 同上 — resolver 注入版(纯 JVM 单测入口) */
    fun weekListCompactTexts(
        resolve: (Int) -> String,
        dayName: (Int) -> String,
        today: LocalDate,
        data: WeekData
    ): List<String> = weekListCompactTexts(resolve, dayName, useAlias = false, today = today, data = data)

    /** resolver + 别名开关注入版 — useAlias 语义: true 时显示别名(空回退原名) */
    fun weekListCompactTexts(
        resolve: (Int) -> String,
        dayName: (Int) -> String,
        useAlias: Boolean,
        today: LocalDate,
        data: WeekData
    ): List<String> {
        if (!data.hasTable || data.days.isEmpty()) return listOf(resolve(R.string.widget_create_schedule))
        if (data.semesterStatus != DateUtils.SemesterStatus.IN_RANGE) {
            val statusRes = if (data.semesterStatus == DateUtils.SemesterStatus.BEFORE_START)
                R.string.semester_not_started else R.string.semester_ended
            return listOf(resolve(statusRes))
        }
        val todayDow = today.dayOfWeek.value
        val targetDows = listOf(todayDow, todayDow % 7 + 1)   // 今天 + 明天(周循环)
        val lines = data.days.filter { it.dayOfWeek in targetDows && it.courses.isNotEmpty() }
            // 按今天→明天的目标顺序排, 禁按 ISO 星期排: 周日锚点(tomorrow=周一)时
            // ISO 排序会把"明天"排到"今天"前面
            .sortedBy { targetDows.indexOf(it.dayOfWeek) }
            .take(2)
            .map { "${dayName(it.dayOfWeek)} ${CourseDisplayUtil.displayName(it.courses.first(), useAlias)}" }
        return lines.ifEmpty { listOf(resolve(R.string.no_course)) }
    }

    /**
     * WeekList 紧凑档 — 无标题, 今天+明天各一行"周X 课名"(取自 weekListCompactTexts),
     * 纯文本无课程胶囊。布局常量: compact 档不参与 weekListContentHeightDp 滚动条带
     * 估算(固定 size 变体), 无需镜像。
     */
    private fun renderWeekListCompact(context: Context, data: WeekData, wDp: Float, hDp: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (wDp * density).toInt()
        val h = (hDp * density).toInt()
        val s = scheme(context, data.themeKey, data.isDark)
        val ctx = SleepyApp.get()

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(c)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // 背景圆角
        p.color = s.bg
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()),
            20f * density, 20f * density, p)

        val pad = 10f * density
        val lines = weekListCompactTexts(ctx, LocalDate.now(), data)

        // 今天+明天"周X 课名" — 居中大字
        p.color = s.onSurface
        p.textSize = 15f * density
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        var y = h / 2f
        for (line in lines.take(2)) {
            canvas.drawText(ellipsize(p, line, w - pad * 2), pad, y, p)
            y += 20f * density
        }

        return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
    }

    /**
     * WeekList widget 渲染 — 7 列日列
     * SMALL 变体 + 容器 <150dp → 走紧凑档(纯文本); REGULAR 或容器被拖大 ≥150dp → 全量排版
     * (默认参数 REGULAR → 全部现有调用点零改动; 大档路径 renderWeekListRegular 函数体逐字节不变)
     */
    fun renderWeekList(
        context: Context, data: WeekData, wDp: Float, hDp: Float,
        variant: WidgetVariant = WidgetVariant.REGULAR
    ): Bitmap {
        if (variant == WidgetVariant.SMALL && wDp < 150f) {
            return renderWeekListCompact(context, data, wDp, hDp)
        }
        // SMALL 但容器被拖大 ≥150dp → 内部升档回全量排版(设计第三节决策)
        return renderWeekListRegular(context, data, wDp, hDp)
    }

    /**
     * WeekList 全量排版 — 原 renderWeekList 函数体原样改名迁入(REGULAR 档逐字节不变保证)
     */
    private fun renderWeekListRegular(context: Context, data: WeekData, wDp: Float, hDp: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (wDp * density).toInt()
        val h = (hDp * density).toInt()
        val s = scheme(context, data.themeKey, data.isDark)
        val colorless = AppPrefs.isWidgetColorless(context)
        // issue#26: widget 场景别名 — 渲染时读全局 widget 开关
        val useAlias = AppPrefs.isWidgetUseAlias(context)
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
                    val name = CourseDisplayUtil.displayName(course, useAlias)
                    // 课程颜色背景 (对齐 WeekGrid 风格) — 统一入口 CourseColorUtil (决策 D3)
                    // issue#22: 同名课程多地点 — 用 day.courses 同 groupId 全行,支持 AUTO/CUSTOM 模式取色
                    val bgColor = CourseColorUtil.pickCourseColorIntWithGroupRows(
                        course, day.courses.filter { it.groupId == course.groupId },
                        s.isDark, s.surfaceVariant, colorless
                    )
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
     * WeekView 小档列选取(渲染与单测共用单一事实来源):
     * 有课的日子优先成池; 今天必保(无课也追加进池, 锚点语义);
     * 按"与今天的距离"取最近 maxColumns 列, 最终按星期升序输出(从左到右绘制顺序)。
     * 纯函数零 LocalDate.now() — todayDow 由调用方注入。
     */
    fun weekViewCompactColumns(data: WeekData, todayDow: Int, maxColumns: Int = 3): List<Int> {
        val pool = data.days.filter { it.courses.isNotEmpty() }.map { it.dayOfWeek }
            .ifEmpty { data.days.map { it.dayOfWeek } }
            .toMutableList()
        if (todayDow !in pool) pool.add(todayDow)
        return pool.sortedBy { kotlin.math.abs(it - todayDow) }.take(maxColumns).sorted()
    }

    /**
     * WeekGrid 最小档数据映射 — WeekData → 今日 WidgetData。
     * 最小档(1×1 列)不再"折叠成单列的网格脸", 直接复用今日课程·小的渲染器:
     * 宿主只换数据, 渲染走 renderToday(SMALL) → 与今日课程·小像素同源同一张脸。
     * 纯函数零 LocalDate.now() — today 由调用方注入。
     */
    fun weekGridMinimumTodayData(data: WeekData, today: LocalDate): WidgetData {
        val timeJson = data.days.firstOrNull()?.timeJson ?: ""
        val todayDay = data.days.firstOrNull { it.dayOfWeek == today.dayOfWeek.value }
        return WidgetData(
            date = today,
            courses = todayDay?.courses ?: emptyList(),
            timeJson = timeJson,
            hasTable = data.hasTable,
            isDark = data.isDark,
            themeKey = data.themeKey,
            semesterStatus = data.semesterStatus
        )
    }

    /**
     * drawCourse meta 行拆分 — 拼行("时间 · 地点")放不下时拆两行(时间一行/地点一行)。
     * 文本宽度可加(拼行宽恒 ≥ 两行之和), 拆行永不更差 → 无需收益判定;
     * 拆开后单行仍超宽的极端场景由渲染端逐行省略号兜底。纯函数 — measure 由调用方注入。
     */
    fun courseMetaLines(
        measure: (String) -> Float,
        maxWidth: Float,
        timeStr: String,
        room: String
    ): List<String> {
        if (room.isBlank()) return listOf(timeStr)
        val combined = "$timeStr · $room"
        return if (measure(combined) <= maxWidth) listOf(combined) else listOf(timeStr, room)
    }

    /**
     * WeekView widget 渲染 — 7 列日列, 复刻 DaySummaryCell (CourseTableView.kt L559-L642)
     * SMALL 变体 + 容器 <150dp → 走紧凑档(复用 Regular 渲染器, shownDays 换成 compact 列);
     * REGULAR 或容器被拖大 ≥150dp → 全量排版
     * (默认参数 REGULAR → 全部现有调用点零改动; 大档路径 renderWeekViewRegular 函数体逐字节不变)
     */
    fun renderWeekView(
        context: Context, data: WeekData, wDp: Float, hDp: Float,
        variant: WidgetVariant = WidgetVariant.REGULAR
    ): Bitmap {
        if (variant == WidgetVariant.SMALL && wDp < 150f) {
            return renderWeekViewCompact(context, data, wDp, hDp)
        }
        // SMALL 但容器被拖大 ≥150dp → 内部升档回全量排版(设计第三节决策)
        return renderWeekViewRegular(context, data, wDp, hDp)
    }

    /**
     * WeekView 紧凑档 — 复用 Regular 渲染器的列绘制(字号不变, 列少了每列自然变宽)。
     * Regular 函数体零改动, compact 走数据侧换列: 先按用户"显示星期"设置收窄可选池
     * (避免 Regular 内 shownDays 交集为空落到"去创建课表"兜底文案), 再选 compact 列。
     */
    private fun renderWeekViewCompact(context: Context, data: WeekData, wDp: Float, hDp: Float): Bitmap {
        val todayDow = LocalDate.now().dayOfWeek.value
        // visibleDays 同 Regular 档读法(决策 D5-12): 用户设置决定可选列池, 空集回退全周防御
        val visibleDays = AppPrefs.getVisibleDays(context)
        val poolDays = if (visibleDays.isEmpty()) data.days
            else data.days.filter { it.dayOfWeek in visibleDays }
        val compactDows = weekViewCompactColumns(data.copy(days = poolDays), todayDow)
        val compactData = data.copy(
            days = data.days.filter { it.dayOfWeek in compactDows }.sortedBy { it.dayOfWeek }
        )
        return renderWeekViewRegular(context, compactData, wDp, hDp)
    }

    /**
     * WeekView 全量排版 — 原 renderWeekView 函数体原样改名迁入(REGULAR 档逐字节不变保证)
     */
    private fun renderWeekViewRegular(context: Context, data: WeekData, wDp: Float, hDp: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (wDp * density).toInt()
        val h = (hDp * density).toInt()
        val s = scheme(context, data.themeKey, data.isDark)
        val showSeparator = AppPrefs.isWidgetSeparator(context)
        // issue#26: widget 场景别名 — 渲染时读全局 widget 开关
        val useAlias = AppPrefs.isWidgetUseAlias(context)
        // visibleDays (决策 D5-12, 对齐 WeekGridWidgetProvider.renderBitmap L162-163):
        // 用户"显示星期"设置决定渲染列; 设置页 UI 保证至少留 1 天, 空集下回退全周防御
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
                    val name = CourseDisplayUtil.displayName(course, useAlias)
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
     * SMALL 变体 + 容器 <150dp → 走紧凑档(纯文本); REGULAR 或容器被拖大 ≥150dp → 全量排版
     * (默认参数 REGULAR → 全部现有调用点零改动; 大档路径 renderTwoDayRegular 函数体逐字节不变)
     */
    fun renderTwoDay(
        context: Context, data: TwoDayData, wDp: Float, hDp: Float,
        variant: WidgetVariant = WidgetVariant.REGULAR
    ): Bitmap {
        return renderTwoDayRegular(context, data, wDp, hDp)
    }

    /**
     * TwoDay 全量排版 — 原 renderTwoDay 函数体原样改名迁入(REGULAR 档逐字节不变保证)
     */
    private fun renderTwoDayRegular(context: Context, data: TwoDayData, wDp: Float, hDp: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (wDp * density).toInt()
        val h = (hDp * density).toInt()
        val s = scheme(context, data.themeKey, data.isDark)
        val colorless = AppPrefs.isWidgetColorless(context)
        // issue#26: widget 场景别名 — 渲染时读全局 widget 开关
        val useAlias = AppPrefs.isWidgetUseAlias(context)
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
                // v7.10.11: 冲突分栏 — 同引擎, 冲突课并排半栏(栏间浅细竖线), 同栏堆叠
                val rowGap = 8f * density
                val maxRowH = 44f * density
                val stackGap = 3f * density
                val laneGap = 5f * density
                val sepColor = (s.onSurface and 0x00FFFFFF) or 0x4D000000
                val laneRows = com.lingion.sleepy.util.ConflictLayoutEngine.weekLaneRows(day.courses)
                laneRows.forEach { row ->
                    if (row.laneCount == 1) {
                        drawCourse(canvas, p, row.courses[0], day.timeJson, colX, cy, colW, maxRowH, s, density,
                            fontSizeSp = 10f, colorless = colorless, displayMode = displayMode,
                            groupRows = day.courses.filter { it.groupId == row.courses[0].groupId },
                            useAlias = useAlias)
                        cy += maxRowH + rowGap
                    } else {
                        val laneW = (colW - laneGap * (row.laneCount - 1)) / row.laneCount
                        val maxStack = row.courses.groupBy { row.laneOf[it.id] }.values
                            .maxOf { it.size }.coerceAtLeast(1)
                        val laneRowTotalH = maxStack * maxRowH + (maxStack - 1) * stackGap
                        repeat(row.laneCount) { li ->
                            val laneX = colX + li * (laneW + laneGap)
                            if (li > 0) {
                                val sepX = laneX - laneGap / 2f
                                val keepColor = p.color
                                p.color = sepColor
                                canvas.drawRect(sepX - 0.5f * density, cy, sepX + 0.5f * density,
                                    cy + laneRowTotalH, p)
                                p.color = keepColor
                            }
                            val laneCourses = row.courses.filter { row.laneOf[it.id] == li }
                            var ly = cy
                            laneCourses.forEach { laneCourse ->
                                drawCourse(canvas, p, laneCourse, day.timeJson, laneX, ly, laneW, maxRowH, s, density,
                                    fontSizeSp = 9f, colorless = colorless, displayMode = displayMode,
                                    groupRows = day.courses.filter { it.groupId == laneCourse.groupId },
                                    useAlias = useAlias)
                                ly += maxRowH
                                if (ly < cy + laneRowTotalH) ly += stackGap
                            }
                        }
                        cy += laneRowTotalH + rowGap
                    }
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
