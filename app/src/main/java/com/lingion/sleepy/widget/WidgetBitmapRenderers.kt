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
     * v11 (今天真机翻车后撤回 v10): 回归 v9.1 形态 = 单 child 整张长图 + launcher
     * 原生 ListView 滚动 (TwoDay/WeekList 同构一直正常)。v10 试过的"逐行子项 =
     * 每行一张透明位图"在 OPPO 上复发了 v1 extent 冻结 (无拖动) + 双层错位叠影 +
     * 第一行压住壳图标题导致 TopBar 消失 — 三症状同根,都是多 child 整错的。
     * 滚动模型自此回 v9.1: 整张不透明长图, 一张位图 = 一条 ListView 子项。
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
        headerSpace: Boolean = false,
        showBackToToday: Boolean = true,
        visibleCourses: List<com.lingion.sleepy.data.entity.CourseEntity>? = null
    ): Bitmap = renderTodayRegular(
        context, data, wDp, hDp, emptyHeader, headerSpace, showBackToToday,
        visibleCourses
    )

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
        resolve: (Int) -> String,
        showBackToToday: Boolean = true
    ): TodayHeaderParts {
        val title = if (data.isToday) "${resolve(R.string.today_today)} · $dayName"
                    else "${data.dateLabel} · $dayName"
        return if (!data.isToday && showBackToToday) {
            TodayHeaderParts(title, resolve(R.string.today_nav_back_to_today), true)
        } else if (showDate && data.isToday) {
            TodayHeaderParts(title, data.dateLabel, false)
        } else {
            // 导航态的日期已在 title 中；showBackToToday=false 时也不得再画第二份日期。
            TodayHeaderParts(title, null, false)
        }
    }

    /** 按可用宽度截断文本(字符级贪心, 与 [[sleepy-vert-text-overflow-fix]] 同思路) */
    private fun ellipsize(p: Paint, text: String, maxW: Float): String {
        if (p.measureText(text) <= maxW) return text
        var t = text
        while (t.isNotEmpty() && p.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    /** ellipsize 的纯函数版 — measure 回调注入, JVM 单测可断言 (courseMetaLines 用) */
    private fun ellipsizeBy(measure: (String) -> Float, text: String, maxW: Float): String {
        if (measure(text) <= maxW) return text
        var t = text
        while (t.isNotEmpty() && measure("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    /**
     * Today 全量排版 — 原 renderToday 函数体原样改名迁入(REGULAR 档逐字节不变保证)
     */
    private fun renderTodayRegular(
        context: Context, data: WidgetData, wDp: Float, hDp: Float,
        emptyHeader: Boolean,
        headerSpace: Boolean,
        showBackToToday: Boolean = true,
        visibleCourses: List<com.lingion.sleepy.data.entity.CourseEntity>? = null
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

        val pad = 10f * density
        var y = pad

        // 标题行 — emptyHeader=true 时整体不画: 今日导航版顶栏用真实 RemoteViews 视图
        // (TextView 标题 + 三角按钮) 覆盖, bitmap 头部留白防双重标题; y 前进量保留 →
        // 内容纵坐标与带头渲染逐像素一致。WeekGrid 最小档等旧调用方 (false) 逐字节不变。
        // v10: pageOffset 守卫删除 (无分页无续页, 守卫回归 emptyHeader 单条件)。
        if (!emptyHeader) {
            val header = todayHeaderParts(
                data, DateUtils.localizedDay(data.date.dayOfWeek.value, ctx), showDate,
                resolve = { ctx.getString(it) },
                showBackToToday = showBackToToday
            )
            val titleX = pad
            val rightX = w - pad
            // issue#31 荣耀 2×2: 裸 ‹› glyph 已删 (v5 翻页时代遗留, 无色块无点击区,
            // 用户误当按钮去点)。真实按钮 = nav_static 布局的 prev/next ImageView
            // (renderNavTriangle 三角 + PendingIntent, issue#24 两天功能)。
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
        if (!headerSpace) y += 20f * density

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

        // FIXED 窗口 ALL_DONE (2026-09-14b 用户定稿): 不画「今日课程已结束」长状态行 —
        // 唯一结束标记 = 底部条左下角「+0」胶囊 (十几个字符的状态文案禁回流)。空正文即可。

        // 课程列表（全部渲染，不再截断）
        // v7.10.11: 冲突分栏 — 与 App 今日页/周视图同一引擎(weekLaneRows),
        // 冲突区域一行内并排(栏间浅细竖线), 同栏课纵向堆叠, 无冲突课整宽。
        // 栏内多课时该行实际高度由最高栏决定(各栏 y 游标独立推进后再取 max 对齐)。
        // v11 撤回 v10 逐行子项 (OPPO extent 冻结/叠影/TopBar 覆盖三症状同根):
        // 渲染器回归 v9.1 — 一次画完整展开长图, 调用方保证 h=全展开高。
        // v8: 行几何单一真值 — span 起点随 headerSpace 参数化。
        val rowH = 30f * density
        val rowGap = 4f * density  // 2026-09-14d 密度二调 — 与 TodayRowGeometry 同源
        val rowW = w - pad * 2

        val laneRows = com.lingion.sleepy.util.ConflictLayoutEngine.weekLaneRows(data.courses, data.timeJson)
        val sepColor = (s.onSurface and 0x00FFFFFF) or 0x4D000000  // 30% 黑(浅色主题下=浅灰细线)
        val stackGap = 3f * density
        // FIXED 窗口: 只画窗内课程 (聚类按重叠链分簇, 连续簇子集重聚类 = 原簇序列,
        // 与推送侧窗口同一 weekLaneRows 口径 → 行几何逐像素一致)
        val spans = TodayRowGeometry.rowSpans(visibleCourses ?: data.courses, headerSpace, data.timeJson)
        spans.forEach { span ->
            val row = span.row
            val y = span.topDp * density
            if (row.laneCount == 1) {
                drawCourse(canvas, p, row.courses[0], data.timeJson, pad, y, rowW, rowH, s, density,
                    fontSizeSp = 11f, colorless = colorless, displayMode = displayMode,
                    groupRows = data.courses.filter { it.groupId == row.courses[0].groupId },
                    useAlias = useAlias)
            } else {
                val laneGap = 5f * density
                val laneW = (rowW - laneGap * (row.laneCount - 1)) / row.laneCount
                val laneRowTotalH = (span.bottomDp - span.topDp) * density
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

        // 隐藏课提示不再画进位图 (2026-09-15 底部导航条定稿): 「+N」胶囊是底部条真实
        // 视图 (renderNavCapsule + PendingIntent), 位图底部区域留给导航条, 无文字页脚。

        return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
    }

    /**
     * Today 内容全展开高度(dp) — 可滚动条带渲染用。
     * 纯计算零绘制; 布局常量逐一镜像 renderToday (改那边必须同步这边)。
     * v7.10.11: 冲突分栏行高按最高栏堆叠数算(与 renderToday 分栏镜像)。
     * v6 headerSpace: true 时头部 24dp 前进量不计 (条带去头, 顶栏是布局独立行),
     * 与 renderToday(headerSpace=true) 逐常量镜像。
     * v11: 状态内容 (无课表/学期外/无课) 走单行估算, 与渲染器 status 分支对得上。
     */
    fun todayContentHeightDp(data: WidgetData, headerSpace: Boolean = false): Float {
        // 空态分支沿用旧口径 (单行状态文本 + 各自 pad)
        if (!data.hasTable) return TodayRowGeometry.contentTopDp(headerSpace) + 20f
        if (data.semesterStatus != DateUtils.SemesterStatus.IN_RANGE)
            return TodayRowGeometry.contentTopDp(headerSpace) + 22f + 14f
        if (data.courses.isEmpty()) return TodayRowGeometry.contentTopDp(headerSpace) + 22f + 14f
        // v8: 行几何单一真值 — 与 renderTodayRegular 同调 TodayRowGeometry (镜像失配根除)
        return TodayRowGeometry.contentHeightDp(data.courses, headerSpace, data.timeJson)
    }

    // ── 今日导航顶栏按钮 (issue #24: 低对比圆角矩形 + 三角形图标) ──

    /** 视图口径: 按钮视图 40×24dp(即点击热区, 大于可视矩形), 底部条高 28dp。 */
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
     * 刷新图标按钮 (回到今天) — 与 [renderNavTriangle] 同风格: surfaceVariant 圆角底
     * + onSurfaceVariant 弧形箭头 (270° 圆弧 + 顺时针箭头尖, 12 点方向入口)。
     * 尺寸同 NAV_BUTTON_W_DP/NAV_BUTTON_H_DP, 视图本身即点击区。
     */
    fun renderNavRefresh(context: Context, data: WidgetData): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (NAV_BUTTON_W_DP * density).toInt()
        val h = (NAV_BUTTON_H_DP * density).toInt()
        val s = scheme(context, data.themeKey, data.isDark)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // 低对比圆角矩形底 — 与 prev/next 同规格同呼吸空隙
        p.color = s.surfaceVariant
        c.drawRoundRect(RectF(4f * density, 3f * density, w - 4f * density, h - 3f * density),
            7f * density, 7f * density, p)

        // 刷新弧 — 270° 圆弧开口朝 12 点方向, 线帽圆头
        val cx = w / 2f
        val cy = h / 2f
        val r = 5.5f * density
        val arcRect = RectF(cx - r, cy - r, cx + r, cy + r)
        p.color = s.onSurfaceVariant
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * density
        p.strokeCap = Paint.Cap.ROUND
        c.drawArc(arcRect, -90f, 270f, false, p)

        // 箭头尖 — 弧终点 (12 点方向) 顺时针箭头
        val tipX = cx
        val tipY = cy - r
        val path = android.graphics.Path().apply {
            moveTo(tipX + 3.5f * density, tipY - 1f * density)
            lineTo(tipX - 2.5f * density, tipY - 1f * density)
            lineTo(tipX, tipY + 3f * density)
            close()
        }
        p.style = Paint.Style.FILL
        c.drawPath(path, p)
        return bmp
    }

    /** 底部条「+N」胶囊视图高 (宽随文本自适应, 最小 30dp)。 */
    const val NAV_CAPSULE_H_DP = 20f
    const val NAV_CAPSULE_MIN_W_DP = 30f

    /**
     * 底部条「+N」胶囊 — 隐藏课提示 + 配置页自救入口 (2026-09-15 底部导航条定稿,
     * 替代旧「还有 N 节未上」文字页脚)。与三角按钮同配色 (surfaceVariant 底 +
     * onSurfaceVariant 字), 全圆角胶囊形; 宽 = 文本测量 + 左右 8dp, 下限 30dp。
     */
    fun renderNavCapsule(context: Context, data: WidgetData, text: String): Bitmap {
        val density = context.resources.displayMetrics.density
        val s = scheme(context, data.themeKey, data.isDark)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val w = (NAV_CAPSULE_MIN_W_DP.coerceAtLeast(p.measureText(text) / density + 16f) * density).toInt()
        val h = (NAV_CAPSULE_H_DP * density).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        p.color = s.surfaceVariant
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), h / 2f, h / 2f, p)
        p.color = s.onSurfaceVariant
        val fm = p.fontMetrics
        c.drawText(text, (w - p.measureText(text)) / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, p)
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
     * TwoDay 内容全展开高度(dp) — 可滚动条带渲染用。常量镜像 renderTwoDay。
     * v7.10.11: 冲突分栏行高按最高栏堆叠数算(与 renderTwoDayRegular 分栏镜像)。
     */
    fun twoDayContentHeightDp(data: TwoDayData): Float {
        var h = 12f                                 // pad (2026-09-14c: 顶部标签行已删)
        if (!data.hasTable || data.days.isEmpty()) return h + 20f
        if (data.semesterStatus != DateUtils.SemesterStatus.IN_RANGE) return h + 22f + 14f  // 状态 + 提示
        // 最高一列决定整体高度; 每列: 列头(20) + 冲突分行课程 / "无课程"一行
        val colH = data.days.maxOf { day ->
            if (day.courses.isEmpty()) return@maxOf 20f + 16f
            var cy = 20f
            val rows = com.lingion.sleepy.util.ConflictLayoutEngine.weekLaneRows(day.courses, day.timeJson)
            rows.forEach { row ->
                if (row.laneCount == 1) {
                    cy += 36f + 6f
                } else {
                    val maxStack = row.courses.groupBy { row.laneOf[it.id] }.values
                        .maxOf { it.size }.coerceAtLeast(1)
                    cy += maxStack * 36f + (maxStack - 1) * 3f + 6f
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
     * issue#31: 周视图全展开内容高度 (dp) — 推送闸 (contentH ≤ hDp?) 与
     * SCOPE_WEEKVIEW 条带长图共用同一函数 (口径分裂 = 壳图/条带错位)。
     *
     * 与 renderWeekViewRegular 排版逐项对应 (visibleDays 收窄由调用方先行:
     * 推送闸读设置过滤; 条带工厂同参):
     *   [状态行 16] + 列内: 标题 12+14 + chip(有课时) 14+4 + 课程行 (行高 + 3dp 间隔)
     * 课程行高 = fontMetrics(9sp) — 与渲染同源, 无常量漂移。外层 pad 6×2。
     */
    fun weekViewContentHeightDp(
        context: Context, data: WeekData, wDp: Float,
        maxCoursesPerDay: Int = Int.MAX_VALUE
    ): Float {
        val outerPad = 6f
        if (!data.hasTable) return outerPad * 2 + 20f
        val visibleDays = AppPrefs.getVisibleDays(context)
        val shownDays = if (visibleDays.isEmpty()) data.days
            else data.days.filter { it.dayOfWeek in visibleDays }.sortedBy { it.dayOfWeek }
        if (shownDays.isEmpty()) return outerPad * 2 + 20f
        val statusH = if (data.semesterStatus != DateUtils.SemesterStatus.IN_RANGE) 16f else 0f
        val density = context.resources.displayMetrics.density
        val p = android.graphics.Paint().apply {
            textSize = 9f * density
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        }
        val fm = p.fontMetrics
        val lineH = (fm.descent - fm.ascent) / density
        // 列宽与渲染器同式 (wDp - pad*2 - gap*(n-1)) / n — 换行数依赖列宽,
        // 宽度不同高度不同, 必须同参 (壳图/条带/闸门三方一致)
        val colGap = 4f
        val colW = (wDp - outerPad * 2 - colGap * (shownDays.size - 1)) / shownDays.size
        val textPad = 4f
        val maxTextWidth = colW - textPad * 2
        val colH = shownDays.maxOf { day ->
            var cy = statusH + 12f + 14f
            if (day.courses.isNotEmpty()) {
                cy += 14f + 4f
                // 课程块: 行数与渲染器 wrapMax2Lines 同式 (别名口径也同源), 
                // 行 = 1..2 行; 课程间 3dp 间隔 (含尾课收尾 3dp, 与渲染 idx==last 分支一致)
                val useAlias = AppPrefs.isWidgetUseAlias(context)
                // §9.5 口径统一: 静态脸渲染 take(maxCoursesPerDay), 闸门按同口径量;
                // 条带(全展开)保持默认 Int.MAX_VALUE 全量口径
                day.courses.take(maxCoursesPerDay).forEachIndexed { idx, course ->
                    val name = CourseDisplayUtil.displayName(course, useAlias)
                    val lines = wrapMax2Lines(name, p, maxTextWidth * density)
                    cy += lines.size * lineH
                    cy += 3f
                    if (idx < day.courses.size - 1) {
                        // 分隔线开关只影响线本身 1dp 的位置, 两侧半 gap 之和恒等 courseGap
                        // (渲染: sep ? gap/2+line+gap/2 : gap — 垂直总高相同)
                    }
                }
            }
            cy
        }
        return outerPad * 2 + colH
    }

    /**
     * WeekList 紧凑档 (2026-09-14 用户定稿改版) — 与周视图·小同一张脸:
     * 今天邻域 ≤3 列 + Regular 渲染器 (彩色胶囊保留)。旧「今天+明天各一行
     * 周X 课名」纯文本脸被否 — 丑且无信息量, 禁回流。compact 走数据侧换列,
     * Regular 函数体零改动 (先按用户"显示星期"设置收窄可选池, 避免 shownDays
     * 交集为空落到"去创建课表"兜底文案)。
     */
    private fun renderWeekListCompact(
        context: Context, data: WeekData, wDp: Float, hDp: Float,
        visibleByCol: List<List<com.lingion.sleepy.data.entity.CourseEntity>?>?,
        footerByCol: List<String?>?
    ): Bitmap {
        val todayDow = LocalDate.now().dayOfWeek.value
        val visibleDays = AppPrefs.getVisibleDays(context)
        // compact 脸日列 = compactShownDays 单一口径 (compactWindow 优先, 可跨上下周)
        val compactData = data.copy(days = compactShownDays(data, visibleDays, todayDow))
        return renderWeekListRegular(context, compactData, wDp, hDp, visibleByCol, footerByCol)
    }

    /**
     * WeekList widget 渲染 — 7 列日列
     * SMALL 变体 + 容器 <150dp → 走紧凑档(今天邻域 ≤3 列, 同周视图·小);
     * REGULAR 或容器被拖大 ≥150dp → 全量排版
     * (默认参数 REGULAR → 全部现有调用点零改动; 大档路径 renderWeekListRegular 函数体逐字节不变)
     */
    fun renderWeekList(
        context: Context, data: WeekData, wDp: Float, hDp: Float,
        variant: WidgetVariant = WidgetVariant.REGULAR,
        visibleByCol: List<List<com.lingion.sleepy.data.entity.CourseEntity>?>? = null,
        footerByCol: List<String?>? = null
    ): Bitmap {
        if (variant == WidgetVariant.SMALL && wDp < 150f) {
            return renderWeekListCompact(context, data, wDp, hDp, visibleByCol, footerByCol)
        }
        // SMALL 但容器被拖大 ≥150dp → 内部升档回全量排版(设计第三节决策)
        return renderWeekListRegular(context, data, wDp, hDp, visibleByCol, footerByCol)
    }

    /**
     * WeekList 全量排版 — 原 renderWeekList 函数体原样改名迁入(REGULAR 档逐字节不变保证)
     */
    private fun renderWeekListRegular(
        context: Context, data: WeekData, wDp: Float, hDp: Float,
        visibleByCol: List<List<com.lingion.sleepy.data.entity.CourseEntity>?>?,
        footerByCol: List<String?>?
    ): Bitmap {
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
            val isToday = DateUtils.isDateToday(day.date)
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
                // FIXED 窗口 (设计 §4.3): 逐列预算截断, 列底「+N」短页脚 (无独立页脚行)
                p.textSize = 9f * density
                p.typeface = Typeface.DEFAULT
                val coursePad = 3f * density
                val courseRowH = 16f * density
                val courseGap = 3f * density
                (visibleByCol?.getOrNull(i) ?: day.courses).forEachIndexed { idx, course ->
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
                // 列底「+N」短页脚 (FIXED 窗口隐藏了后续课)
                footerByCol?.getOrNull(i)?.let { more ->
                    p.color = s.onSurfaceVariant
                    p.textSize = 9f * density
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    val mw = p.measureText(more)
                    canvas.drawText(more, x + (colW - mw) / 2f, cy + 11f * density, p)
                    p.typeface = Typeface.DEFAULT
                }
            }
        }

        return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
    }

    /**
     * 最小档 compact 脸实际显示日列 — 渲染器与 push() 高度闸门共用单一口径 (§9.1):
     * compactWindow 非空 → 三天真实日期窗口 (2026-09-15 用户令, 可跨上下周, 按日期升序);
     * 空 → 回退旧 weekViewCompactColumns 今天邻域 ≤3 列口径(防御: 数据源未提供窗口时)。
     * visibleDays 过滤后为空集则回退未过滤窗口(防御, 对齐 D5-12 空集回退惯例)。
     */
    fun compactShownDays(data: WeekData, visibleDays: Set<Int>, todayDow: Int): List<DayData> {
        if (data.compactWindow.isNotEmpty()) {
            val win = if (visibleDays.isEmpty()) data.compactWindow
                else data.compactWindow.filter { it.dayOfWeek in visibleDays }.ifEmpty { data.compactWindow }
            return win.sortedBy { it.date }
        }
        val pool = if (visibleDays.isEmpty()) data.days
            else data.days.filter { it.dayOfWeek in visibleDays }
        val dows = weekViewCompactColumns(data.copy(days = pool), todayDow)
        return data.days.filter { it.dayOfWeek in dows }.sortedBy { it.dayOfWeek }
    }

    /**
     * 最小档三天窗口的真实日期 (2026-09-15 用户令, 渲染与单测共用单一事实来源):
     * todayFirst=true → 今天/明天/后天(今日居于第一位); false → 昨天/今天/明天(今日居于第二位)。
     * 固定三天, 不跳空天; 日期可越出本周(上下周打通由数据层按所在周周次过滤课程)。
     * 纯函数零 LocalDate.now() — today 由调用方注入。
     */
    fun compactWindowDates(today: LocalDate, todayFirst: Boolean): List<LocalDate> =
        if (todayFirst) listOf(today, today.plusDays(1), today.plusDays(2))
        else listOf(today.minusDays(1), today, today.plusDays(1))

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
     * drawCourse meta 行: 恒单行 — 时间与地点各占半宽, 放不下各自省略号截断。
     *
     * 旧行为(拼行放不下→拆时间/地点两行)已废: 两行使 meta 块总高超出行高,
     * drawCourse 垂直居中后块体溢出胶囊, 竖向盖住相邻行/行间隙 —— 正是用户
     * 「不允许元素过长挡住其他的, 不能挤占其他的」禁的场景。恒单行 → 块高恒定,
     * 结构上不可能挤占; 长文本在自己半宽槽内截断, 不抢他人空间。
     * 纯函数 — measure 由调用方注入。
     */
    fun courseMetaLines(
        measure: (String) -> Float,
        maxWidth: Float,
        timeStr: String,
        room: String
    ): List<String> {
        val time = timeStr.trim()
        val place = room.trim()
        if (time.isBlank() && place.isBlank()) return emptyList()
        if (place.isBlank()) return listOf(time)
        if (time.isBlank()) return listOf(place)
        val combined = "$time · $place"
        if (measure(combined) <= maxWidth) return listOf(combined)
        // 溢出 → 同一行内各占一半(分隔符宽度对半摊), 各自截断, 行数不变
        val sepW = measure(" · ")
        val halfW = ((maxWidth - sepW) / 2f).coerceAtLeast(0f)
        return listOf(
            ellipsizeBy(measure, time, halfW) + " · " + ellipsizeBy(measure, place, halfW)
        )
    }

    /**
     * WeekView widget 渲染 — 7 列日列, 复刻 DaySummaryCell (CourseTableView.kt L559-L642)
     * SMALL 变体 + 容器 <150dp → 走紧凑档(复用 Regular 渲染器, shownDays 换成 compact 列);
     * REGULAR 或容器被拖大 ≥150dp → 全量排版
     * (默认参数 REGULAR → 全部现有调用点零改动; 大档路径 renderWeekViewRegular 函数体逐字节不变)
     */
    fun renderWeekView(
        context: Context, data: WeekData, wDp: Float, hDp: Float,
        variant: WidgetVariant = WidgetVariant.REGULAR,
        /** 每列课程上限 — 静态 face 保持 5 门裁切; 条带全展开长图传 Int.MAX_VALUE (#31) */
        maxCoursesPerDay: Int = 5,
        visibleByCol: List<List<com.lingion.sleepy.data.entity.CourseEntity>?>? = null,
        footerByCol: List<String?>? = null
    ): Bitmap {
        if (variant == WidgetVariant.SMALL && wDp < 150f) {
            return renderWeekViewCompact(context, data, wDp, hDp, maxCoursesPerDay)
        }
        // SMALL 但容器被拖大 ≥150dp → 内部升档回全量排版(设计第三节决策)
        return renderWeekViewRegular(context, data, wDp, hDp, maxCoursesPerDay, visibleByCol, footerByCol)
    }

    /**
     * WeekView 紧凑档 — 复用 Regular 渲染器的列绘制(字号不变, 列少了每列自然变宽)。
     * Regular 函数体零改动, compact 走数据侧换列: 先按用户"显示星期"设置收窄可选池
     * (避免 Regular 内 shownDays 交集为空落到"去创建课表"兜底文案), 再选 compact 列。
     */
    private fun renderWeekViewCompact(
        context: Context, data: WeekData, wDp: Float, hDp: Float,
        maxCoursesPerDay: Int = 5
    ): Bitmap {
        val todayDow = LocalDate.now().dayOfWeek.value
        // visibleDays 同 Regular 档读法(决策 D5-12): 空集回退全周防御; 日列走 compactShownDays 单一口径
        val visibleDays = AppPrefs.getVisibleDays(context)
        val compactData = data.copy(days = compactShownDays(data, visibleDays, todayDow))
        return renderWeekViewRegular(context, compactData, wDp, hDp, maxCoursesPerDay)
    }

    /**
     * WeekView 全量排版 — 原 renderWeekView 函数体原样改名迁入(REGULAR 档逐字节不变保证)
     */
    private fun renderWeekViewRegular(
        context: Context, data: WeekData, wDp: Float, hDp: Float,
        maxCoursesPerDay: Int = 5,
        visibleByCol: List<List<com.lingion.sleepy.data.entity.CourseEntity>?>? = null,
        footerByCol: List<String?>? = null
    ): Bitmap {
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
            val isToday = DateUtils.isDateToday(day.date)
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
                // FIXED 窗口 (设计 §4.3): 逐列预算截断优先于 take(5); fits 分支保留 5 门口径
                val courses = visibleByCol?.getOrNull(i) ?: day.courses.take(maxCoursesPerDay)
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
                // 列底「+N」短页脚 (FIXED 窗口隐藏了后续课)
                footerByCol?.getOrNull(i)?.let { more ->
                    p.color = s.onSurfaceVariant
                    p.textSize = 9f * density
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    val mw = p.measureText(more)
                    canvas.drawText(more, x + (colW - mw) / 2f, cy - fm.ascent, p)
                    p.typeface = Typeface.DEFAULT
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
        variant: WidgetVariant = WidgetVariant.REGULAR,
        visibleByCol: List<List<com.lingion.sleepy.data.entity.CourseEntity>?>? = null,
        footerTexts: List<String?>? = null,
        statusByCol: List<String?>? = null
    ): Bitmap {
        return renderTwoDayRegular(context, data, wDp, hDp, visibleByCol, footerTexts, statusByCol)
    }

    /**
     * TwoDay 全量排版 — 原 renderTwoDay 函数体原样改名迁入(REGULAR 档逐字节不变保证)
     */
    private fun renderTwoDayRegular(
        context: Context, data: TwoDayData, wDp: Float, hDp: Float,
        visibleByCol: List<List<com.lingion.sleepy.data.entity.CourseEntity>?>?,
        footerTexts: List<String?>?,
        statusByCol: List<String?>?
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

        // 2026-09-14c: 顶部「最近两天」标签行删除 (用户: 是个人都知道) — 省 22dp 给课程行

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
            // FIXED 窗口 (设计 §4.2): 今天列 TIME_WINDOW / 明天列 HEAD, 推送侧算好传入
            val colCourses = visibleByCol?.getOrNull(colIdx) ?: day.courses
            val colStatus = statusByCol?.getOrNull(colIdx)

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

            if (colStatus != null) {
                // ALL_DONE 列 (2026-09-14b): 什么都不画 — 「+0」胶囊是唯一结束标记;
                // 不得落进「无课程」分支 (有课只是上完了, 说无课程是撒谎)
            } else if (colCourses.isEmpty()) {
                p.color = s.onSurfaceVariant
                p.textSize = 11f * density
                canvas.drawText(ctx.getString(R.string.no_course), colX, cy + 11f * density, p)
            } else {
                // 胶囊固定最大高度 44dp, 不再撑满整个列
                // v7.10.11: 冲突分栏 — 同引擎, 冲突课并排半栏(栏间浅细竖线), 同栏堆叠
                val rowGap = 6f * density
                val maxRowH = 36f * density
                val stackGap = 3f * density
                val laneGap = 5f * density
                val sepColor = (s.onSurface and 0x00FFFFFF) or 0x4D000000
                val laneRows = com.lingion.sleepy.util.ConflictLayoutEngine.weekLaneRows(colCourses, day.timeJson)
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

        // FIXED 窗口每列「+N」胶囊 (2026-09-14 用户定稿): 各列独立计数 —
        // 合并「+N 节待上」禁回流 (今天/明天各欠几节必须分得清)。
        footerTexts?.forEachIndexed { colIdx, text ->
            if (text == null) return@forEachIndexed
            p.textSize = 11f * density
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val tw = p.measureText(text)
            val pillW = (tw + 16f * density).coerceAtMost(colW)
            val pillH = 18f * density
            val cx = pad + colIdx * (colW + colGap) + colW / 2f
            val left = (cx - pillW / 2f).coerceIn(pad, w - pad - pillW)
            val top = h - pad - pillH
            p.color = s.surfaceVariant
            canvas.drawRoundRect(RectF(left, top, left + pillW, top + pillH), pillH / 2f, pillH / 2f, p)
            p.color = s.onSurfaceVariant
            val fm = p.fontMetrics
            canvas.drawText(text, cx - tw / 2f, top + pillH / 2f - (fm.ascent + fm.descent) / 2f, p)
        }

        return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
    }

    /**
     * Canvas 手动换行: 最多2行, 超出截断 "…".
     * CJK 按字符断行; Latin 在空格处断行.
     */
    internal fun wrapMax2Lines(
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
