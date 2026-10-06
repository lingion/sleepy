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
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.CourseColorUtil
import com.lingion.sleepy.util.MealBreakDetector
import com.lingion.sleepy.util.GridSeparatorGeometry
import com.lingion.sleepy.util.CourseDisplayUtil
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.HolidayManager
import com.lingion.sleepy.util.PeriodHeaderFormatter
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.ui.component.PERIOD_HEADER_CARD_PAD_DP
import com.lingion.sleepy.ui.component.PeriodHeaderAdaptiveFont
import com.lingion.sleepy.ui.component.PeriodHeaderMetrics
import com.lingion.sleepy.ui.component.solvePlacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
open class WeekGridWidgetProvider : AppWidgetProvider() {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 小组件排版档位 — 基类默认 REGULAR(现有变体); 「本周课表（网格）· 小」子类覆写为 SMALL */
    open val variantHint: WidgetVariant = WidgetVariant.REGULAR

    /**
     * ANR 修复: onUpdate/onAppWidgetOptionsChanged 在主线程回调,
     * 原实现 renderWidget 内含 runBlocking(DB) + Canvas 重活 → 主线程阻塞 → ANR。
     * 改用 goAsync() 获取 PendingResult, 在后台线程做完 DB 加载 + Bitmap 渲染后 finish。
     * 系统广播 ANR 阈值(前台~10s/后台~60s)由 goAsync 续命, 实际工作在 Dispatchers.Default。
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetVendorActions.XIAOMI_UPDATE_ACTION) {
            WidgetVendorActions.dispatchXiaomiUpdate(this, context, intent)
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, awm: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        ioScope.launch {
            try {
                for (id in ids) {
                    try { renderWidget(context, awm, id) }
                    catch (e: Throwable) { Log.e(TAG, "render failed $id", e) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, awm: AppWidgetManager, id: Int, newOptions: android.os.Bundle
    ) {
        val pending = goAsync()
        ioScope.launch {
            try { renderWidget(context, awm, id) }
            catch (e: Throwable) { Log.e(TAG, "optionsChanged render failed $id", e) }
            finally { pending.finish() }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (id in appWidgetIds) { WidgetBindingStore.remove(context, id); WidgetScrollStore.remove(context, id) }
    }

    private fun renderWidget(context: Context, awm: AppWidgetManager, widgetId: Int) {
        var data = loadWeekData(context, widgetId)
        val opts = awm.getAppWidgetOptions(widgetId)
        val density = context.resources.displayMetrics.density

        // FIX(字扁+巨大+黑边): 必须用「实际当前尺寸」而不是 MAX resize 边界。
        // 之前读 OPTION_APPWIDGET_MAX_WIDTH/HEIGHT = 616×634dp (这是 widget 能拖到的最大尺寸, 不是当前尺寸!)
        //   实际 widget 在桌面只占 ~376×651dp (窄高, ratio 0.58)。
        //   用 616×634 (ratio 0.97) 画 bitmap → fitCenter 等比缩小塞进 0.58 容器 → 上下大片留白;
        //   用 fitXY 则强行拉伸 → 字扁。根因 = bitmap 宽高比 ≠ 容器宽高比。
        // 正解 (API31+): OPTION_APPWIDGET_SIZES 返回当前真实 SizeF(dp 列表), 取最大那个 = 容器真实尺寸,
        //   bitmap 宽高比 == 容器宽高比 → 无拉伸无黑边。
        // 兼容 (API<31 回退): MIN_W × MAX_H 近似默认窄高尺寸。
        // §9.3: 尺寸解析统一走 computeSizeDp (WidgetSizeCore 单一口径: API33 类型化/
        // API31-32 无类型/回退 fallbackSizeDp), 不再内联镜像一份 max-area 逻辑。
        val (wDp, hDp) = RemoteViewsWidgetHelper.computeSizeDp(opts)
        // §4.4: 位图地板 (180×250dp) 已删 — 按容器真实尺寸绘制, fitXY 1:1 无压扁无留白;
        // 极小尺寸的可读性由 renderBitmap 降级阶梯 (时间标签→色带) 兜底, 不再靠锁尺寸。
        val w = (wDp * density).toInt().coerceAtLeast(1)
        val h = (hDp * density).toInt().coerceAtLeast(1)
        Log.d(TAG, "renderWidget: opts MIN=${opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)}" +
            "x${opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)}dp " +
            "SIZES_wDp=${wDp}x${hDp}dp → bitmap=${w}x${h}px ratio=%.2f (density=$density)".format(w.toFloat()/h))

        // SMALL 变体 + 容器 <150dp → 最小档: 不再"折叠成单列的网格脸"(用户反馈: 2×2 比例奇怪),
        // 直接走今日课程·小的完整推送管线 pushTodayData(静态闸+可滚动条带同源) —
        // 同一渲染器+同一滚动条带工厂, 像素级同一张脸。
        // 数据侧 weekGridMinimumTodayData(WeekData→今日 WidgetData), 纯函数单测单一事实来源。
        // REGULAR 或容器被拖大 ≥150dp → 全量网格排版(行为与改动前逐字节一致)。
        val variant = variantHint
        if (variant == WidgetVariant.SMALL && wDp < 150f) {
            val todayData = WidgetBitmapRenderers.weekGridMinimumTodayData(data, LocalDate.now())
            Log.d(TAG, "vSmall minimum → today face: courses=${todayData.courses.size}")
            TodayWidgetReceiver.pushTodayData(context, awm, widgetId, WidgetVariant.SMALL, todayData)
            return
        }

        val bmp = renderBitmap(context, data, w, h)
        val views = RemoteViews(context.packageName, R.layout.widget_bitmap_container)
        views.setImageViewBitmap(R.id.widget_bitmap, bmp)
        val pi = PendingIntent.getActivity(context, WidgetRoutes.tapRequestCode(widgetId),
            WidgetRoutes.tapIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_bitmap, pi)
        awm.updateAppWidget(widgetId, views)
        // Bitmap 回收已删除: setImageViewBitmap 进入 RemoteViews.mBitmapCache,
        // 系统进程常以 ashmem 共享持有, 本进程 recycle 会让启动器拿到已释放的 native
        // pixel → setImageBitmap 抛异常 → AppWidgetHostView 回落到错误视图。
        // 下一轮 onUpdate 推送新 RemoteViews 时旧 bitmap 自然随 mBitmapCache 被 GC,
        // 内存峰值受 widget 数量约束 (典型 3-5 个, ~7.8MB/个), 可接受。
    }

    companion object {
        private const val TAG = "WeekGridV19"

        /**
         * §4.4 降级阶梯几何 — bodyH(px)/slotH(px) 推导 (渲染器与契约测试单一事实来源)。
         * 与旧内联算式逐字节同式: outerPad 6dp×2 + headH 56dp, bodyH 地板 20dp,
         * 节间隙 1.5dp×(n+1), slotH 地板 3dp (整除口径保持 Int / Int)。
         */
        internal fun weekGridBodyGeomPx(hPx: Int, density: Float, maxNode: Int, mealBreakCount: Int = 0): Pair<Int, Float> {
            fun dp(v: Float) = (v * density).roundToInt()
            val bodyH = (hPx - dp(6f) * 2 - dp(56f)).coerceAtLeast(dp(20f))
            val totalGapH = dp(1.5f) * (maxNode + 1) + dp(4f) * mealBreakCount
            val slotH = ((bodyH - totalGapH) / maxNode).toFloat().coerceAtLeast(dp(3f).toFloat())
            return bodyH to slotH
        }

        /** §4.4 降级阶梯末档: 单节 slotH < 9dp → 文字行排不下, 切色带模式 (无文字非空白) */
        internal fun weekGridColorBand(slotHPx: Float, density: Float): Boolean =
            slotHPx < (9f * density).roundToInt()

        /**
         * 教室角标几何钳制 — 标签字号不得超底部预留带, 基线不得越过带外。
         * 极矮卡上旧代码 roomSize 下限 5dp 可大于预留带, 标签压进竖排课名区 = 挤占。
         * 返回 (clampedSize, baseline): size ≤ 预留带高, 基线锚定卡底--pad-size×0.3。
         * 纯函数 — 渲染与单测单一事实来源。
         */
        internal fun weekGridRoomLabelLayout(
            cardBottom: Float,
            unifiedPad: Float,
            roomReserveH: Float,
            requestedSize: Float
        ): Pair<Float, Float> {
            val size = requestedSize.coerceAtMost((roomReserveH / 1.1f).coerceAtLeast(0f))
            val baseline = cardBottom - unifiedPad - size * 0.3f
            return size to baseline
        }

        fun renderBitmap(context: Context, data: WeekData, wPx: Int, hPx: Int): Bitmap {
            val density = context.resources.displayMetrics.density
            val isDark = data.isDark

            // ── 颜色 (跟随主题: resolveSchemePublic 支持 system=动态取色) ──
            // 之前硬编码紫色十六进制 → 小组件永远紫色, 不跟随 app / 系统壁纸取色
            val scheme = resolveSchemePublic(context, data.themeKey, isDark)
            fun androidx.compose.ui.graphics.Color.toIntArgb(): Int =
                (0xFF shl 24) or ((this.red * 255).toInt() shl 16) or
                    ((this.green * 255).toInt() shl 8) or (this.blue * 255).toInt()
            val bgSurface       = scheme.surface.toIntArgb()
            val bgContainer     = scheme.surfaceContainer.toIntArgb()
            val bgToday         = scheme.primaryContainer.toIntArgb()
            val fgPrimary       = scheme.primary.toIntArgb()
            val fgOnSurface     = scheme.onSurface.toIntArgb()
            val fgOnSurfaceVar  = scheme.onSurfaceVariant.toIntArgb()
            val gridLine        = scheme.surfaceVariant.toIntArgb()
            // 统一底色开关的中性底 = 所选主题的 secondaryContainer (2026-09-28 用户令:
            // 与「我的」页「刷新所有小组件」FilledTonalButton 同色, 即主题色淡调)
            val unifiedCourseBg = scheme.secondaryContainer.toIntArgb()
            val showSeparators  = AppPrefs.isGridShowSeparators(context)
            val longBreakSpacing = AppPrefs.isGridLongBreakSpacing(context)
            val colorless       = AppPrefs.isWidgetColorless(context)

            // v23: 课程颜色完全对齐 CourseTableView — 黄金角 HSL 分配
            // hue = groupId.hashCode() * 137.508° → 相邻课色差最大化, 同门课永远同色
            // 亮色 S=0.55 L=0.82 (粉彩), 暗色 S=0.40 L=0.28 (沉稳)
            // 用户自定义 color 优先 (#FF6750A4 视为未设置)
            // (本地 hslToColorInt/pickCourseColor 副本已收敛至 util/CourseColorUtil.kt, 决策 D3)

            // ── 数据 ──
            val timeJson = data.days.firstOrNull()?.timeJson ?: ""
            val allSlots = TimeTableUtils.timeSlotsFor(timeJson)
            val maxNode = (data.days.flatMap { it.courses }
                .maxOfOrNull { it.startNode + it.step - 1 } ?: allSlots.size)
                .coerceAtLeast(1)
            // issue#22: 同名课程多地点 — 跨天汇总 course 全集,传给 pickCourseColorIntWithGroupRows
            val allCourses = data.days.flatMap { it.courses }
            val slots = allSlots.take(maxNode)
            val mealBreakAfterRows = if (longBreakSpacing) {
                MealBreakDetector.detectDisplayRowIndexes(
                    timeJson,
                    allCourses,
                    slots.map { it.nodeEnd }
                )
            } else emptySet()
            val sortedDays = data.visibleDays.sorted()
            val dayCount = sortedDays.size.coerceIn(1, 7)

            // ── 布局 (dp → px, 跟 CourseTableView 同参数) ──
            val dp = { v: Float -> (v * density).roundToInt() }
            // v19c 字号参数 (用户原话: "你这个字号明显是不合格的")
            // 之前 headH*0.30 cap dp(15f) 太大, day header 文字溢出 cell 边界全挤在一起
            // 改成: cap 降到 dp(13f), min 升到 dp(10f), 文字宽度永远 < dayW - padding
            val outerPad = dp(6f)
            val headH = dp(56f)
            val timeW = dp(40f)
            val gapH = dp(1.5f)
            val gapW = dp(2.5f)

            val bodyW = wPx - outerPad * 2
            // §4.4 降级阶梯几何单一事实来源 (与 weekGridBodyGeomPx 契约测试同源)
            val (bodyH, slotH) = weekGridBodyGeomPx(
                hPx,
                density,
                maxNode,
                mealBreakAfterRows.count { it < maxNode - 1 }
            )
            val totalGapW = gapW * (dayCount + 1)
            val dayW = ((bodyW - timeW - totalGapW) / dayCount)
                .toFloat().coerceAtLeast(dp(20f).toFloat())  // 下限: 防 launcher 返极小宽度致负数

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

            // 空状态: 无课表时显示占位提示, 不渲染空白网格
            // 学期后课程被清空 → 落到这分支; 学期状态文案优先于"去创建课表"
            if (!data.hasTable || data.days.isEmpty() || data.days.all { it.courses.isEmpty() }) {
                val ctx = SleepyApp.get()
                p.textAlign = Paint.Align.CENTER
                p.color = fgOnSurface
                p.textSize = dp(15f).toFloat()
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                if (data.semesterStatus != DateUtils.SemesterStatus.IN_RANGE) {
                    val statusRes = if (data.semesterStatus == DateUtils.SemesterStatus.BEFORE_START)
                        R.string.semester_not_started else R.string.semester_ended
                    c.drawText(ctx.getString(statusRes), wPx / 2f, hPx / 2f - dp(8f), p)
                    p.textSize = dp(11f).toFloat()
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    p.color = fgOnSurfaceVar
                    c.drawText(ctx.getString(R.string.today_semester_out_hint),
                        wPx / 2f, hPx / 2f + dp(12f), p)
                } else {
                    c.drawText(ctx.getString(R.string.widget_create_schedule),
                        wPx / 2f, hPx / 2f - dp(8f), p)
                    p.textSize = dp(11f).toFloat()
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    p.color = fgOnSurfaceVar
                    c.drawText(ctx.getString(R.string.widget_open_sleepy),
                        wPx / 2f, hPx / 2f + dp(12f), p)
                }
                return bmp
            }

            // ── Header (Day labels) ──
            var x = outerPad.toFloat()
            var y = outerPad.toFloat()

            // v19b: 字号完全根据 widget 宽高自适应 (用户原话: "能不能自动根据这个宽度, 高度调整")
            // 1dp 永远 = 1dp, 但用 widget 尺寸作为 scale 单位
            // dayW = (bodyW-timeW) / 7, cardH = slotH * step
            // 单节 course 卡片: cardH = slotH (小卡), 多节: cardH = slotH*N (大卡)

            // time column 角落
            p.color = bgSurface
            c.drawRoundRect(RectF(x, y, x + timeW, y + headH),
                dp(14f).toFloat(), dp(14f).toFloat(), p)

            // 学期前(课照常显示供预习): 角落画学期状态, 用户知道现在学期没开始
            if (data.semesterStatus == DateUtils.SemesterStatus.BEFORE_START) {
                val ctx2 = SleepyApp.get()
                p.color = fgOnSurfaceVar
                p.textSize = (headH * 0.16f).coerceAtMost(dp(9f).toFloat()).coerceAtLeast(dp(6f).toFloat())
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                p.textAlign = Paint.Align.CENTER
                c.drawText(ctx2.getString(R.string.semester_not_started), x + timeW / 2f, y + headH * 0.6f, p)
            }

            // day headers
            for ((idx, dow) in sortedDays.withIndex()) {
                val cellX = x + timeW + gapW + idx * (dayW + gapW)
                val dayData = data.days.firstOrNull { it.dayOfWeek == dow }
                val isToday = dayData != null && DateUtils.isDateToday(dayData.date)
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
            val mealGapExtraPx = dp(4f).toFloat()
            fun rowTop(rowIndex: Int): Float = bodyTop + gapH + rowIndex * (slotH + gapH) +
                mealBreakAfterRows.count { it + 1 <= rowIndex } * mealGapExtraPx
            val mealBreakBands = mealBreakAfterRows.mapNotNull { row ->
                if (row !in 0 until maxNode - 1) return@mapNotNull null
                GridSeparatorGeometry.Span(rowTop(row) + slotH, rowTop(row + 1))
            }
            val bodyBottom = bodyTop + bodyH - gapH

            // Today backgrounds, grid borders, and course cards share the same row geometry.
            for ((idx, dow) in sortedDays.withIndex()) {
                val colX = x + timeW + gapW + idx * (dayW + gapW)
                val dayData = data.days.firstOrNull { it.dayOfWeek == dow }
                if (dayData != null && DateUtils.isDateToday(dayData.date)) {
                    p.color = bgToday
                    p.alpha = 40
                    c.drawRect(RectF(colX, bodyTop, colX + dayW, bodyTop + bodyH), p)
                    p.alpha = 255
                }
            }
            if (showSeparators) {
                p.color = gridLine
                p.alpha = 90
                p.strokeWidth = dp(0.7f).toFloat()
                for ((idx, _) in sortedDays.withIndex()) {
                    val colX = x + timeW + gapW + idx * (dayW + gapW)
                    for (segment in GridSeparatorGeometry.verticalSegments(bodyTop, bodyBottom, mealBreakBands)) {
                        c.drawLine(colX, segment.start, colX, segment.end, p)
                        c.drawLine(colX + dayW, segment.start, colX + dayW, segment.end, p)
                    }
                }
                for (row in 0 until maxNode) {
                    val rowY = rowTop(row)
                    c.drawLine(x + timeW, rowY, x + timeW + gapW + dayCount * (dayW + gapW) - gapW, rowY, p)
                }
                for (band in mealBreakBands) {
                    c.drawLine(x + timeW, band.start,
                        x + timeW + gapW + dayCount * (dayW + gapW) - gapW, band.start, p)
                }
                c.drawLine(x + timeW, bodyTop + bodyH - gapH,
                    x + timeW + gapW + dayCount * (dayW + gapW) - gapW, bodyTop + bodyH - gapH, p)
                p.alpha = 255
            }

            // §4.4 降级阶梯末档: slotH 小到文字行排不下 (单节卡高 <9dp) → 色带模式:
            // 日头保留, 主体只画课程色条 (冲突课按 lane 分宽), 无任何文字。任意高度非空白。
            if (weekGridColorBand(slotH, density)) {
                for ((idx, dow) in sortedDays.withIndex()) {
                    val colX = x + timeW + gapW + idx * (dayW + gapW)
                    val dayData = data.days.firstOrNull { it.dayOfWeek == dow } ?: continue
                    for (laneRect in com.lingion.sleepy.util.ConflictLayoutEngine
                            .gridDayLanes(dayData.courses, dayData.timeJson)) {
                        val course = laneRect.course
                        val startIdx = (course.startNode - 1).coerceAtLeast(0)
                        val step = course.step.coerceAtLeast(1).coerceAtMost(maxNode - startIdx)
                        val top = rowTop(startIdx)
                        val barH = (slotH * step + gapH * (step - 1)).coerceAtLeast(1f)
                        val laneX = colX + dayW * laneRect.laneStartFraction
                        val laneW = dayW * laneRect.laneWidthFraction
                        p.color = CourseColorUtil.pickCourseColorIntWithGroupRows(
                            course, allCourses.filter { it.groupId == course.groupId },
                            isDark, gridLine, colorless
                        )
                        p.alpha = if (dayData.isGrey) 120 else 200
                        val r = minOf(dp(4f).toFloat(), barH / 2f)
                        c.drawRoundRect(RectF(laneX, top, laneX + laneW, top + barH), r, r, p)
                        p.alpha = 255
                    }
                }
                return bmp
            }

            // time column labels
            p.textAlign = Paint.Align.CENTER
            val widgetHeaderLayout = AppPrefs.getPeriodHeaderLayout(context)
            val widgetHeaderStyle = AppPrefs.getPeriodHeaderStyle(context)
            val widgetHeaderHanging = AppPrefs.getPeriodHeaderHanging(context).coerceIn(-1f, 1f)
            val widgetHeaderShowX = AppPrefs.isPeriodHeaderShowX(context)
            // §4.4 颜色池 — 显式补 surfaceContainerLow,旧链漏导 → 卡片底色硬用 surfaceContainer,时间列卡片与预览色不一致。
            val bgSurfaceLow = scheme.surfaceContainerLow.toIntArgb()
            // 三行卡片几何 — 与 PeriodHeaderCellContent / SingleTimeHeadCell 共享同一事实来源。
            // PERIOD_HEADER_CARD_PAD_DP (3dp) 与预览/周视图逐层相等 (用户 2026-09-28 令)。
            val cardPadPx = dp(PERIOD_HEADER_CARD_PAD_DP).toFloat()
            val cardRadiusPx = dp(8f).toFloat()
            // 整列统一字号 — 列级 forColumn 先按全列最紧约束算一次 (用户 2026-09-29 令),
            // 与 Compose 侧 CourseTableView.columnFont 同一算法同一输入域 (sp)。
            val spToPxForColumn = { v: Float ->
                android.util.TypedValue.applyDimension(
                    android.util.TypedValue.COMPLEX_UNIT_SP, v,
                    context.resources.displayMetrics
                )
            }
            val columnSharedFont = if (widgetHeaderLayout == "three_line") {
                val rows = slots.mapNotNull { slot ->
                    val isSingleNode = slot.nodeStart == slot.nodeEnd
                    val label = if (widgetHeaderShowX && isSingleNode) {
                        PeriodHeaderFormatter.fullLabel(slot.nodeStart, widgetHeaderStyle)
                    } else {
                        PeriodHeaderFormatter.range(slot.nodeStart, slot.nodeEnd, widgetHeaderStyle)
                    }
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    p.textSize = spToPxForColumn(11f)
                    val startW = p.measureText(slot.displayStart)
                    val endW = p.measureText(slot.displayEnd)
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    p.textSize = spToPxForColumn(12f)
                    val labelW = p.measureText(label)
                    val timeMaxW = maxOf(startW, endW)
                    PeriodHeaderAdaptiveFont.RowConstraint(
                        inkWidthSp = (timeMaxW + labelW) / density,
                        timeMaxWidthSp = timeMaxW / density,
                        labelWidthSp = labelW / density,
                    )
                }
                PeriodHeaderAdaptiveFont.forColumn(
                    cardWidthSp = (timeW - 2f * cardPadPx).coerceAtLeast(1f) / density,
                    cardHeightSp = (slotH - 2f * cardPadPx).coerceAtLeast(1f) / density,
                    rows = rows,
                )
            } else null
            for (i in 1..maxNode) {
                val rowY = rowTop(i - 1)
                val slot = slots.getOrNull(i - 1)

                val centerX = x + timeW / 2f
                if (widgetHeaderLayout == "three_line" && slot != null) {
                    val start = slot.displayStart
                    val end = slot.displayEnd
                    val isSingleNode = slot.nodeStart == slot.nodeEnd
                    val label = if (widgetHeaderShowX && isSingleNode) {
                        PeriodHeaderFormatter.fullLabel(slot.nodeStart, widgetHeaderStyle)
                    } else {
                        PeriodHeaderFormatter.range(slot.nodeStart, slot.nodeEnd, widgetHeaderStyle)
                    }
                    // 同 Compose: 测量与绘制必须用同一组 paint 值,先以基础字号测宽,再走自适应,
                    // 最后按自适应字号二次实测并端点异锚(用户 2026-09-27 三行表头收口)。
                    // 单位契约: PeriodHeaderAdaptiveFont 输出是 sp 语义(Compose 侧 .sp 渲染),
                    // Canvas Paint.textSize 是 px 语义 → 必须 sp→px 换算,否则文字缩小 density 倍。
                    val spToPx = { v: Float ->
                        android.util.TypedValue.applyDimension(
                            android.util.TypedValue.COMPLEX_UNIT_SP, v,
                            context.resources.displayMetrics
                        )
                    }
                    val baseTimeSize = spToPx(11f)
                    val baseLabelSize = spToPx(12f)
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    p.textSize = baseTimeSize
                    val baseStartW = p.measureText(start)
                    val baseEndW = p.measureText(end)
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    p.textSize = baseLabelSize
                    val baseLabelW = p.measureText(label)
                    val baseMetrics = PeriodHeaderMetrics(
                        baseStartW, baseEndW, baseLabelW,
                        showX = widgetHeaderShowX && isSingleNode
                    )
                    val cardInnerW = (timeW - 2f * cardPadPx).coerceAtLeast(1f)
                    val cardInnerH = (slotH - 2f * cardPadPx).coerceAtLeast(1f)
                    // 单位契约: PeriodHeaderAdaptiveFont 输入输出是 sp 语义(Compose 侧 .sp 渲染),
                    // Canvas Paint.textSize 是 px 语义 → 必须 px/density 入参、sp→px 出参。
                    // dp 字面量数值≈sp 禁再除 density (2026-09-30 修单位 bug)。
                    val adaptive = columnSharedFont ?: PeriodHeaderAdaptiveFont.compute(
                        cardWidthSp = cardInnerW / density,
                        cardHeightSp = cardInnerH / density,
                        inkWidthSp = baseMetrics.inkWidth(widgetHeaderHanging) / density,
                        timeMaxWidthSp = baseMetrics.timeMax / density,
                        labelWidthSp = baseLabelW / density,
                    )
                    val timeSizePx = spToPx(adaptive.timeSize)
                    val labelSizePx = spToPx(adaptive.labelSize)
                    // 按自适应字号二次实测,得到绘制阶段真正使用的 width / 行高。
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    p.textSize = timeSizePx
                    val startW = p.measureText(start)
                    val endW = p.measureText(end)
                    val startFontMetrics = p.fontMetrics
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    p.textSize = labelSizePx
                    val labelW = p.measureText(label)
                    val labelFontMetrics = p.fontMetrics
                    val metrics = PeriodHeaderMetrics(
                        startW, endW, labelW,
                        showX = widgetHeaderShowX && isSingleNode
                    )
                    val placement = metrics.solvePlacement(widgetHeaderHanging)
                    // 卡片宽 = 元素矩形(contentWidth) + 内边距 ×2,轨道内居中(用户 2026-09-27 令:
                    // 卡片自适应元素矩形,不要用轨道宽度撑出空白)。
                    val cardW = (placement.contentWidth + 2f * cardPadPx).coerceAtLeast(1f)
                    // 外框覆盖整行，3dp 只属于卡片内部 padding（与预览/SingleTimeHeadCell 相同）。
                    val cardH = slotH.coerceAtLeast(1f)
                    val cardLeft = (centerX - cardW / 2f)
                    val cardTop = rowY
                    // 卡片底色(surfaceContainerLow 圆角 8dp,与 SingleTimeHeadCell 同款)
                    p.color = bgSurfaceLow
                    p.style = Paint.Style.FILL
                    c.drawRoundRect(RectF(cardLeft, cardTop, cardLeft + cardW, cardTop + cardH),
                        cardRadiusPx, cardRadiusPx, p)
                    // 三行基线按 Compose Text 的实际字号/字体度量计算，避免固定比例造成纵向漂移。
                    val timeRowH = startFontMetrics.descent - startFontMetrics.ascent
                    val labelRowH = labelFontMetrics.descent - labelFontMetrics.ascent
                    val contentH = timeRowH + labelRowH + timeRowH
                    val contentTop = cardTop + (cardH - contentH).coerceAtLeast(0f) / 2f
                    val baseX = cardLeft + cardPadPx
                    p.style = Paint.Style.FILL
                    p.textAlign = Paint.Align.LEFT
                    // 第一行:开始时间
                    p.color = fgOnSurfaceVar
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    p.textSize = timeSizePx
                    val startBaseline = contentTop - startFontMetrics.ascent
                    c.drawText(start, baseX + placement.timeBaseLeft, startBaseline, p)
                    // 第二行:标签(端点异锚)
                    p.color = fgOnSurface
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    p.textSize = labelSizePx
                    val labelBaseline = contentTop + timeRowH - labelFontMetrics.ascent
                    c.drawText(label, baseX + placement.labelLeft, labelBaseline, p)
                    // 第三行:结束时间
                    p.color = fgOnSurfaceVar
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    p.textSize = timeSizePx
                    val endBaseline = contentTop + timeRowH + labelRowH - startFontMetrics.ascent
                    c.drawText(end, baseX + placement.timeBaseLeft, endBaseline, p)
                } else {
                    // Legacy remains the compact two-line grid header.
                    p.color = fgOnSurface
                    p.textSize = (slotH * 0.40f).coerceAtMost(dp(13f).toFloat()).coerceAtLeast(dp(8f).toFloat())
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    val cy = rowY + slotH / 2f + p.textSize * 0.35f
                    val periodText = if (widgetHeaderLayout == "legacy") i.toString()
                    else PeriodHeaderFormatter.label(i, widgetHeaderStyle)
                    c.drawText(periodText, centerX, cy, p)
                    if (slot != null && slotH > dp(18f)) {
                        p.color = fgOnSurfaceVar
                        p.textSize = (slotH * 0.20f).coerceAtMost(dp(7f).toFloat()).coerceAtLeast(dp(4f).toFloat())
                        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        c.drawText(slot.timeString, centerX, cy + p.textSize * 1.6f, p)
                    }
                }
            }

            // v21: 竖排(直书) — token 化 + 拉丁组旋转 + 标点优化
            val useVertForms = AppPrefs.isVertPunctReplace(context)  // 方案B开关(默认false=方案A'旋转)
            // issue#26: widget 场景别名 — 字号预算与绘制必须用同一个名字, 否则截断不一致
            val useAlias = AppPrefs.isWidgetUseAlias(context)

            // v20b: 字号统一到「全表最小理想值」— 自适应算法 + 统一字号
            // 每卡按 cardH/unitHeight 算理想字号(v21: 用 token 单位高度替代旧字数)
            // → 全表取最小 → 所有卡用同一个字号(整齐)
            // 下限 11dp 保可读; 上限对齐表头"周一/周二"字号(用户原话: 课名字号最大不能超过周一周二)
            //   表头 day-name 字号 = headH * 0.24 capped dp(13) → 这里 nameMaxDp 用同一 cap
            val unifiedPad = dp(4f).toFloat()
            val nameMinDp = dp(11f).toFloat()   // 可读下限
            val nameMaxDp = (headH * 0.24f).coerceAtMost(dp(13f).toFloat())  // 不超过表头"周一"字号
            val dayAvailW = (dayW - unifiedPad * 2).coerceAtLeast(dp(8f).toFloat())
            val nameMaxPxByW = dayAvailW * 0.92f
            val nameCeil = minOf(nameMaxDp, nameMaxPxByW)
            val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG)

            // 遍历所有课程算每卡理想字号, 取全表最小 → unifiedCharSize
            var minIdeal = nameCeil  // 初始=上限, 任何卡都会更小
            for (dow in sortedDays) {
                val dd = data.days.firstOrNull { it.dayOfWeek == dow } ?: continue
                for (course in dd.courses) {
                    val step = course.step.coerceIn(1, maxNode)
                    val cardH = slotH * step + gapH * (step - 1)
                    val hasRoom = course.room.isNotBlank()
                    // v22: 真实可用高度(不夹下限 → 矮卡算真实空间) + 自适应 room 预留
                    val availCardHPre = (cardH - unifiedPad * 2).coerceAtLeast(0f)
                    val roomReservePre = if (hasRoom) (nameMinDp * 0.7f).coerceAtMost(availCardHPre * 0.35f) else 0f
                    val nameAvailH = (availCardHPre - roomReservePre).coerceAtLeast(0f)
                    // v21: token 单位高度(拉丁组旋转省空间 → unit<字数 → 统一号可能更大)
                    val tokens = tokenizeName(CourseDisplayUtil.displayName(course, useAlias), useVertForms)
                    val unitH = measureUnitHeight(tokens, measurePaint).coerceAtLeast(1f)
                    val ideal = (nameAvailH / unitH).coerceIn(nameMinDp, nameCeil)
                    if (ideal < minIdeal) minIdeal = ideal
                }
            }
            val unifiedCharSize = minIdeal
            Log.d(TAG, "v21 unifiedCharSize=${unifiedCharSize.toInt()}px vertForms=$useVertForms (全表最小理想字号, token化) nameMin=${nameMinDp.toInt()}px nameMax=${nameCeil.toInt()}px slotH=${slotH}px dayW=${dayW}px")

            // day columns
            for ((idx, dow) in sortedDays.withIndex()) {
                val colX = x + timeW + gapW + idx * (dayW + gapW)
                val dayData = data.days.firstOrNull { it.dayOfWeek == dow } ?: continue
                // 课程卡片
                // v7.10.8: 冲突课分栏 — 与 App 周视图同一引擎(ConflictLayoutEngine.gridDayLanes),
                // 冲突区域内的课并排各占 1/N 列宽, 无冲突课整列宽。旧实现所有课画满整列宽,
                // 同节次课互相覆盖(后画盖先画), 小组件上冲突课信息丢失。
                val laneRects = com.lingion.sleepy.util.ConflictLayoutEngine.gridDayLanes(dayData.courses, dayData.timeJson)
                for (laneRect in laneRects) {
                    val course = laneRect.course
                    val startIdx = (course.startNode - 1).coerceAtLeast(0)
                    val step = course.step.coerceAtLeast(1)
                        .coerceAtMost(maxNode - startIdx)
                    val cardTop = rowTop(startIdx)
                    val cardH = slotH * step + gapH * (step - 1)
                    // 分栏: 横向按引擎给的起点/宽度比例收缩列宽
                    val laneX = colX + dayW * laneRect.laneStartFraction
                    val laneW = dayW * laneRect.laneWidthFraction
                    val cardRect = RectF(laneX, cardTop, laneX + laneW, cardTop + cardH)

                    // 卡片背景色 (v19e: 对齐 CourseTableView palette) — 统一入口 CourseColorUtil (决策 D3)
                    // colorless 灰底传 gridLine(即 surfaceVariant 的 Int), 与原实现一致
                    val baseColor = CourseColorUtil.pickCourseColorIntWithGroupRows(
                        course, allCourses.filter { it.groupId == course.groupId },
                        isDark, gridLine, colorless
                    )
                    p.color = baseColor
                    p.alpha = if (dayData.isGrey) 120 else 200
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

                    // v19k: 课名居中独占主体, 教室做底部小字角标
                    // 卡片窄(~40dp), 双列并排挤死 → 改成: 课名竖排居中 + 教室缩到 0.6× 字号横排在底部
                    val textColor = if (isDarkOn(baseColor)) Color.WHITE else 0xFF1D1B20.toInt()
                    p.color = textColor
                    p.alpha = if (dayData.isGrey) 153 else 255
                    if (dayData.isGrey && AppPrefs.getHolidayStyle(context) == "strikethrough") {
                        p.flags = p.flags or Paint.STRIKE_THRU_TEXT_FLAG
                    }
                    p.textAlign = Paint.Align.CENTER

                    // nameChars 死变量已删 (v21 起 token 化走 tokenizeName, 不再用字符列表)
                    val roomChars = course.room.takeIf { it.isNotBlank() }
                        ?.filter { it != '\n' && it != ' ' }?.toList() ?: emptyList()

                    // v20b: 用全表统一字号(unifiedCharSize), 截断逻辑保留
                    val availCardH = cardRect.height() - unifiedPad * 2
                    val hasRoom = roomChars.isNotEmpty()
                    val roomReserveH = if (hasRoom) (nameMinDp * 0.7f).coerceAtMost(availCardH * 0.35f) else 0f
                    val nameAvailH = (availCardH - roomReserveH).coerceAtLeast(0f)
                    val charSize = unifiedCharSize

                    // v21: token 化课名 → 截断 → 按类型绘制
                    val nameCenterX = cardRect.centerX()
                    p.textSize = charSize
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    p.alpha = 255
                    p.textAlign = Paint.Align.CENTER

                    val tokens = tokenizeName(CourseDisplayUtil.displayName(course, useAlias), useVertForms)

                    // v22: 字符级贪心截断 — 任何 token 都可拆到字符级, 彻底杜绝溢出
                    //   CJK/PUNCT: 逐字累加, 放不下就截断
                    //   LATIN(旋转组≥2): 不可拆 → 整组放不下则截断
                    p.textSize = charSize
                    data class DrawnToken(val type: TT, val text: String, val h: Float, val size: Float)
                    val drawn = ArrayList<DrawnToken>()
                    var cumH = 0f
                    var truncated = false
                    val ellipsisChar = if (useVertForms) '︙' else '…'
                    val ellipsisH = charSize  // 省略号占~1字高
                    for (tok in tokens) {
                        if (truncated) break
                        when (tok.type) {
                            TT.CJK -> {
                                for (ch in tok.text) {
                                    if (cumH + charSize > nameAvailH) { truncated = true; break }
                                    drawn.add(DrawnToken(TT.CJK, ch.toString(), charSize, charSize))
                                    cumH += charSize
                                }
                            }
                            TT.LATIN -> {
                                // 旋转组不可拆: 整组放不下即截断
                                val tokH = p.measureText(tok.text)
                                if (cumH + tokH > nameAvailH) { truncated = true; break }
                                drawn.add(DrawnToken(TT.LATIN, tok.text, tokH, charSize))
                                cumH += tokH
                            }
                            TT.PUNCT -> {
                                for (ch in tok.text) {
                                    val chW = p.measureText(ch.toString())
                                    if (cumH + chW > nameAvailH) { truncated = true; break }
                                    drawn.add(DrawnToken(TT.PUNCT, ch.toString(), chW, charSize))
                                    cumH += chW
                                }
                            }
                        }
                    }
                    // 截断后腾省略号高度: 从尾部逐字移除直到 … 放得下
                    var showEllipsis = truncated
                    if (truncated) {
                        while (drawn.isNotEmpty() && cumH + ellipsisH > nameAvailH) {
                            cumH -= drawn.removeAt(drawn.size - 1).h
                        }
                        if (drawn.isEmpty()) showEllipsis = false  // 一个字都放不下 → 不画…
                    }
                    // v22: 极端矮卡(nameAvailH < charSize, 连一个最小字号字都放不下)
                    //   缩放首字字号至刚好填满 nameAvailH → 彻底零溢出, 且仍保留内容
                    if (drawn.isEmpty() && tokens.isNotEmpty()) {
                        val tinySize = nameAvailH.coerceIn(1f, charSize)
                        p.textSize = tinySize
                        val tinyH = p.measureText(tokens[0].text.first().toString())  // CJK ≈ tinySize
                        drawn.add(DrawnToken(TT.CJK, tokens[0].text.first().toString(), tinyH, tinySize))
                        cumH = tinyH
                        showEllipsis = false
                    }
                    val nameBlockH = cumH + (if (showEllipsis) ellipsisH else 0f)
                    val nameBlockTop = cardRect.top + unifiedPad + (availCardH - roomReserveH - nameBlockH) / 2f

                    // 逐 token 绘制 (每个 token 自带 size, 支持极端矮卡缩放)
                    var cy = nameBlockTop
                    for (tok in drawn) {
                        p.textSize = tok.size
                        when (tok.type) {
                            TT.CJK -> {
                                c.drawText(tok.text, nameCenterX, cy + tok.size * 0.82f, p)
                                cy += tok.h
                            }
                            TT.LATIN -> {
                                // 整组顺时针旋转90°
                                val centerX = nameCenterX
                                val centerY = cy + tok.h / 2f
                                c.save()
                                c.translate(centerX, centerY)
                                c.rotate(90f)
                                c.drawText(tok.text, 0f, tok.size * 0.35f, p)
                                c.restore()
                                cy += tok.h
                            }
                            TT.PUNCT -> {
                                // 单字旋转90°
                                val centerX = nameCenterX
                                val centerY = cy + tok.h / 2f
                                c.save()
                                c.translate(centerX, centerY)
                                c.rotate(90f)
                                c.drawText(tok.text, 0f, tok.size * 0.35f, p)
                                c.restore()
                                cy += tok.h
                            }
                        }
                    }
                    if (showEllipsis) {
                        p.textSize = charSize
                        c.drawText(ellipsisChar.toString(), nameCenterX, cy + charSize * 0.82f, p)
                    }

                    // 教室: 底部横排小字角标, 0.62× 字号, 半透明, 按卡片宽截断省略
                    // (挤占防御: 标签字号/基线钳在 roomReserveH 预留带内, 极矮卡不再压进课名区)
                    if (roomChars.isNotEmpty()) {
                        val roomStr = course.room.filter { it != '\n' && it != ' ' }
                        val roomRequested = (charSize * 0.62f).coerceAtMost(dp(8f).toFloat()).coerceAtLeast(dp(5f).toFloat())
                        val (roomSize, roomCy) = weekGridRoomLabelLayout(
                            cardBottom = cardRect.bottom,
                            unifiedPad = unifiedPad,
                            roomReserveH = roomReserveH,
                            requestedSize = roomRequested
                        )
                        p.textSize = roomSize
                        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        p.alpha = 160
                        // 按卡片可用宽算能放几个字符, 超了截断 + …
                        val availRoomW = (cardRect.width() - unifiedPad * 2)
                        val maxRoomChars = ((availRoomW / (roomSize * 0.55f)).toInt()).coerceAtLeast(2)  // 中文≈0.55em宽
                        val roomVisible = if (roomStr.length > maxRoomChars) {
                            roomStr.take(maxRoomChars - 1) + "…"
                        } else roomStr
                        c.drawText(roomVisible, nameCenterX, roomCy, p)
                        p.alpha = 255
                    }
                    p.flags = p.flags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    p.alpha = 255
                    // 循环内 Log.d 渲染调试日志已删（每张课程卡都求值字符串模板, Release 也无法被 R8 消除）
                }
            }

            return bmp
        }

        // ===== v21 竖排(直书) token 化 =====
        // 把课名切成有序 token: CJK run(直立) / Latin run≥2(整组旋转90°) / Latin=1(直立) / 标点
        // 标点处理由 useVertForms 决定: true→替换为 Vertical Forms 直立; false→逐个旋转90°

        /** token 类型 */
        private enum class TT { CJK, LATIN, PUNCT }

        /** 一个 token: 类型 + 文本(已按方案处理过标点替换) */
        private data class NameToken(val type: TT, val text: String)

        /** 标点字符集 — 横排符号, 需特殊处理(旋转或替换) */
        private val PUNCT_CHARS = setOf(
            '(', ')', '（', '）', '〔', '〕', '【', '】', '《', '》', '〈', '〉',
            '「', '」', '『', '』', '[', ']', '{', '}', '〈', '〉',
            '—', '–', '～', '~', '…', '·', '・', '、', '，', '。', '：', '；',
            '！', '？', '”', '“', '’', '‘', '"', '\'', '/', '／', '｜', '|'
        )

        /** 方案B: 横排符号 → Unicode Vertical Forms (U+FE19–FE44) */
        private val VERT_FORM_MAP = mapOf(
            '(' to '︵', '（' to '︵',   // U+FE35
            ')' to '︶', '）' to '︶',   // U+FE36
            '〔' to '︹',                  // U+FE39
            '〕' to '︺',                  // U+FE3A
            '【' to '︻',                  // U+FE3B
            '】' to '︼',                  // U+FE3C
            '《' to '︽',                  // U+FE3D
            '》' to '︾',                  // U+FE3E
            '〈' to '︿',                  // U+FE3F
            '〉' to '﹀',                  // U+FE40
            '「' to '﹁',                  // U+FE41
            '」' to '﹂',                  // U+FE42
            '『' to '﹃',                  // U+FE43
            '』' to '﹄',                  // U+FE44
            '[' to '︻',                  // 复用
            ']' to '︼',                  // 复用
            '{' to '︷',                  // U+FE37
            '}' to '︸',                  // U+FE38
            '—' to '︱',                  // U+FE31
            '…' to '︙'                   // U+FE19
        )

        private fun isLatin(ch: Char): Boolean =
            (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9')

        private fun isCJK(ch: Char): Boolean =
            (ch in '一'..'鿿' || ch in '㐀'..'䶿' || ch in '豈'..'﫿')

        /**
         * 课名 → token 列表。先去空白, 再扫描连续 run。
         * useVertForms=true(方案B): 标点替换为 Vertical Forms(变 CJK 直立)
         * useVertForms=false(方案A'): 标点保持原样(绘制时逐个旋转)
         */
        private fun tokenizeName(name: String, useVertForms: Boolean): List<NameToken> {
            val s = name.filter { it != '\n' && it != ' ' }
            if (s.isEmpty()) return emptyList()
            val tokens = ArrayList<NameToken>()
            val sb = StringBuilder()
            var runType: TT? = null

            fun flush() {
                if (sb.isNotEmpty() && runType != null) {
                    tokens.add(NameToken(runType!!, sb.toString()))
                    sb.clear()
                }
                runType = null
            }

            for (ch in s) {
                // 方案B: 标点先替换为 Vertical Forms → 归为 CJK 直立
                val c = if (useVertForms && ch in VERT_FORM_MAP) VERT_FORM_MAP[ch]!! else ch
                val t = when {
                    isCJK(c) -> TT.CJK
                    c in PUNCT_CHARS -> TT.PUNCT
                    isLatin(c) -> TT.LATIN
                    else -> TT.CJK  // 其他字符(含替换后的竖排符号)按 CJK 直立
                }
                if (t != runType) { flush(); runType = t }
                sb.append(c)
            }
            flush()

            // 后处理: LATIN run 长度=1 → 按 spec 保持直立(改判为 CJK 处理即直立)
            return tokens.map { tok ->
                if (tok.type == TT.LATIN && tok.text.length == 1) NameToken(TT.CJK, tok.text) else tok
            }
        }

        /**
         * token 单位高度(与 charSize 无关的比值):
         *   CJK/单字Latin: 每字 1.0
         *   LATIN run≥2(旋转): measureText/textSize (旋转后占高=组宽)
         *   PUNCT(旋转 方案A'): measureText(每字)/textSize
         *   PUNCT 已替换为 VertForms → 走 CJK 路径(每字≈1.0)
         * 用临时 paint 在任意 textSize(如1.0)下测, 比值与绝对字号无关。
         */
        private fun measureUnitHeight(tokens: List<NameToken>, paint: Paint): Float {
            var h = 0f
            for (tok in tokens) {
                when (tok.type) {
                    TT.CJK -> h += tok.text.length * 1f
                    TT.LATIN -> {
                        paint.textSize = 1f
                        h += paint.measureText(tok.text)  // 旋转组占高=组宽
                    }
                    TT.PUNCT -> {
                        paint.textSize = 1f
                        for (ch in tok.text) h += paint.measureText(ch.toString())
                    }
                }
            }
            return h
        }

        private fun isDarkOn(color: Int): Boolean {
            val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
            return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0 < 0.55
        }

        fun loadWeekData(context: Context, appWidgetId: Int): WeekData {
            val today = LocalDate.now()
            val isSystemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val isDark = AppPrefs.isDarkMode(context, isSystemDark)
            val themeKey = AppPrefs.getThemeKey(context)
            val showDate = AppPrefs.isShowDate(context)
            val visibleDays = AppPrefs.getVisibleDays(context)
            return try {
                // Triple<Table?, Status, List<Pair<dow, courses>>>
                val loaded = kotlinx.coroutines.runBlocking {
                    val app = SleepyApp.get()
                    val repo = app.repository
                    // 选表逻辑：先按 widgetId 取绑定表，未绑定则走 WidgetTableResolver（默认表优先），避免与 App 选中表不同步
                    val t = WidgetTableResolver.resolveBoundTable(appWidgetId)
                        ?: WidgetTableResolver.resolveCurrentTable()
                    val status = if (t != null)
                        DateUtils.semesterStatus(t.startDate, t.maxWeek, today)
                    else DateUtils.SemesterStatus.IN_RANGE
                    val map = if (t != null) {
                        val week = DateUtils.currentWeek(t.startDate, today)
                        (1..7).map { dow ->
                            val date = DateUtils.dateOfWeekDay(today, dow)
                            // issue#44: 取课按调休映射
                            val courseDow = HolidayTransferHelper.effectiveDayOfWeek(context, t.id, date)
                            // 学期前: 第 1 周课照常显示(预习); 学期后: 课程清空, renderer 画状态行
                            val courses = if (status == DateUtils.SemesterStatus.AFTER_END) emptyList() else
                                repo.getCoursesByDayOnce(t.id, courseDow)
                                    .filter { it.inWeek(week) }.sortedBy { it.startNode }
                            dow to courses
                        }
                    } else emptyList()
                    Triple(t, status, map)
                }
                val (t, status, daysPerCourse) = loaded
                if (t == null) {
                    WeekData(days = emptyList(), hasTable = false, isDark = isDark,
                        themeKey = themeKey,
                        showDate = showDate, visibleDays = visibleDays)
                } else {
                    val days = daysPerCourse.map { (dow, courses) ->
                        val date = DateUtils.dateOfWeekDay(today, dow)
                        DayData(
                            date = date,
                            dayOfWeek = dow,
                            courses = courses,
                            timeJson = t.timeJson,
                            isGrey = runBlocking { HolidayManager.shouldGrey(context, date, t.id) }
                        )
                    }
                    WeekData(days = days, hasTable = true, isDark = isDark,
                        themeKey = themeKey,
                        showDate = showDate, visibleDays = visibleDays,
                        semesterStatus = status)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "loadWeekData failed", e)
                WeekData(days = emptyList(), hasTable = false, isDark = isDark,
                    themeKey = themeKey,
                    showDate = showDate, visibleDays = visibleDays)
            }
        }
    }
}
