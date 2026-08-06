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
import com.lingion.sleepy.MainActivity
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.DateUtils
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
class WeekGridWidgetProvider : AppWidgetProvider() {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * ★ ANR 修复: onUpdate/onAppWidgetOptionsChanged 在主线程回调,
     * 原实现 renderWidget 内含 runBlocking(DB) + Canvas 重活 → 主线程阻塞 → ANR。
     * 改用 goAsync() 获取 PendingResult, 在后台线程做完 DB 加载 + Bitmap 渲染后 finish。
     * 系统广播 ANR 阈值(前台~10s/后台~60s)由 goAsync 续命, 实际工作在 Dispatchers.Default。
     */
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

    private fun renderWidget(context: Context, awm: AppWidgetManager, widgetId: Int) {
        val data = loadWeekData(context)
        val opts = awm.getAppWidgetOptions(widgetId)
        val density = context.resources.displayMetrics.density

        // ★★ FIX(字扁+巨大+黑边): 必须用「实际当前尺寸」而不是 MAX resize 边界。
        // 之前读 OPTION_APPWIDGET_MAX_WIDTH/HEIGHT = 616×634dp (这是 widget 能拖到的最大尺寸, 不是当前尺寸!)
        //   实际 widget 在桌面只占 ~376×651dp (窄高, ratio 0.58)。
        //   用 616×634 (ratio 0.97) 画 bitmap → fitCenter 等比缩小塞进 0.58 容器 → 上下大片留白;
        //   用 fitXY 则强行拉伸 → 字扁。根因 = bitmap 宽高比 ≠ 容器宽高比。
        // 正解 (API31+): OPTION_APPWIDGET_SIZES 返回当前真实 SizeF(dp 列表), 取最大那个 = 容器真实尺寸,
        //   bitmap 宽高比 == 容器宽高比 → 无拉伸无黑边。
        // 兼容 (API<31 回退): MIN_W × MAX_H 近似默认窄高尺寸。
        val optMaxW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val optMaxH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        val optMinW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val optMinH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        var wDp = 0
        var hDp = 0
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val sizes = opts.getParcelableArrayList(
                AppWidgetManager.OPTION_APPWIDGET_SIZES, android.util.SizeF::class.java)
            sizes?.maxByOrNull { it.width * it.height }?.let { s -> wDp = s.width.toInt(); hDp = s.height.toInt() }
        }
        if (wDp <= 0 || hDp <= 0) {
            // 回退: MIN_W (最窄) × MAX_H (最高) ≈ 默认放置后的窄高容器
            wDp = optMinW.takeIf { it > 0 } ?: 360
            hDp = optMaxH.takeIf { it > 0 } ?: 600
        }
        val w = (wDp * density).toInt().coerceAtLeast((180 * density).toInt())
        val h = (hDp * density).toInt().coerceAtLeast((250 * density).toInt())
        Log.d(TAG, "renderWidget: opts MAX=${optMaxW}x${optMaxH}dp MIN=${optMinW}x${optMinH}dp " +
            "SIZES_wDp=${wDp}x${hDp}dp → bitmap=${w}x${h}px ratio=%.2f (density=$density)".format(w.toFloat()/h))

        val bmp = renderBitmap(context, data, w, h)
        val views = RemoteViews(context.packageName, R.layout.widget_bitmap_container)
        views.setImageViewBitmap(R.id.widget_bitmap, bmp)
        val pi = PendingIntent.getActivity(context, widgetId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_bitmap, pi)
        awm.updateAppWidget(widgetId, views)
        // ★ Bitmap 回收: RemoteViews.setImageViewBitmap 会拷贝 bitmap 到 binder 事务,
        // 本进程持有的原 bitmap 不再需要, 立即回收避免 ~7.8MB 大图累积占内存。
        bmp.recycle()
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
                // 复用 CourseColorRules 的统一关键词/ hash 逻辑 (单一事实来源)
                val key = when (resolveCourseColorKey(name)) {
                    CourseColorKey.ENGLISH -> "english"
                    CourseColorKey.MILITARY -> "military"
                    CourseColorKey.PHYSICS -> "physics"
                    CourseColorKey.HISTORY -> "history"
                    CourseColorKey.PSYCHOLOGY -> "psychology"
                    CourseColorKey.PRACTICE -> "practice"
                    CourseColorKey.PRIMARY -> "primary"
                    CourseColorKey.TERTIARY -> "tertiary"
                    CourseColorKey.SECONDARY -> "secondary"
                }
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
            val bodyH = (hPx - outerPad * 2 - headH).coerceAtLeast(dp(20f))
            val totalGapW = gapW * (dayCount + 1)
            val dayW = ((bodyW - timeW - totalGapW) / dayCount)
                .toFloat().coerceAtLeast(dp(20f).toFloat())  // 下限: 防 launcher 返极小宽度致负数
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

            // ★ 空状态: 无课表时显示占位提示, 不渲染空白网格(与 Glance 版 EmptyTableState 一致)
            if (!data.hasTable || data.days.isEmpty() || data.days.all { it.courses.isEmpty() }) {
                val ctx = SleepyApp.get()
                p.color = fgOnSurface
                p.textSize = dp(15f).toFloat()
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                p.textAlign = Paint.Align.CENTER
                c.drawText(ctx.getString(R.string.widget_create_schedule),
                    wPx / 2f, hPx / 2f - dp(8f), p)
                p.textSize = dp(11f).toFloat()
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                p.color = fgOnSurfaceVar
                c.drawText(ctx.getString(R.string.widget_open_sleepy),
                    wPx / 2f, hPx / 2f + dp(12f), p)
                return bmp
            }

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

            // ★ v19i: 统一字号按最小跨节卡算 (用户原话: "不是按单结算, 是按现有结束跨节的最小的那个算")
            // 之前 v19h 按 slotH (单节) 算, 用户不要 — 要按所有现存课程中 step 最小的跨节卡算
            // 例子: 用户课程有 P1-3节(step=3), P7-5节(step=5), P12-12节(step=12)
            //   → 最小 step=3 → 按 P1-3节 卡的 cardH = slotH*3 + gapH*2 算字号
            // ★ v19k: 卡片窄(~40dp) → 单列居中更透气, 教室做小字角标
            val unifiedPad = dp(4f).toFloat()
            val unifiedColGap = dp(2f).toFloat()  // 仅在极少数极宽 widget 时双列才用
            val unifiedDayAvailW = (dayW - unifiedPad * 2).coerceAtLeast(dp(8f).toFloat())
            val unifiedColW2 = (unifiedDayAvailW - unifiedColGap) / 2 // 双列 (name + room)

            // 找所有课程的最小 step (≥1), 但如果存在跨节课程 (step>=2), 至少按 step=2 算 (排除单节)
            // 例: 用户课程有 P1-1节(step=1), P2-2节(step=2), P7-5节(step=5) → minStep=2
            // 例: 全单节 → minStep=1 (回退到 slotH)
            val rawMinStep = data.days.flatMap { it.courses }
                .minOfOrNull { it.step.coerceAtLeast(1) } ?: 1
            val hasMultiStep = data.days.flatMap { it.courses }
                .any { it.step >= 2 }
            val minStepAll = if (hasMultiStep) maxOf(2, rawMinStep) else 1
            // 按这个最小跨节卡的 cardH 算字号
            val minCardH = slotH * minStepAll + gapH * (minStepAll - 1)
            val unifiedSlotAvailH = (minCardH - unifiedPad * 2).coerceAtLeast(dp(8f).toFloat())

            val maxNameCharsAll = data.days.flatMap { it.courses }
                .maxOfOrNull { c -> c.courseName.filter { it != '\n' && it != ' ' }.length } ?: 6
            val maxRoomCharsAll = data.days.flatMap { it.courses }
                .maxOfOrNull { c -> c.room.takeIf { it.isNotBlank() }
                    ?.filter { it != '\n' && it != ' ' }?.length ?: 0 } ?: 0
            val unifiedMaxRows = maxOf(maxNameCharsAll, maxRoomCharsAll).coerceAtLeast(1)
            val unifiedCharSize = (unifiedSlotAvailH / unifiedMaxRows)
                .coerceAtMost(unifiedColW2 * 0.95f)
                .coerceAtLeast(dp(7f).toFloat())
            Log.d(TAG, "v19i unifiedCharSize=${unifiedCharSize}px minStep=$minStepAll minCardH=${minCardH}px maxRows=$unifiedMaxRows slotH=${slotH}px dayW=${dayW}px")

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

                    // ★ v19k: 课名居中独占主体, 教室做底部小字角标
                    // 卡片窄(~40dp), 双列并排挤死 → 改成: 课名竖排居中 + 教室缩到 0.6× 字号横排在底部
                    val textColor = if (isDarkOn(baseColor)) Color.WHITE else 0xFF1D1B20.toInt()
                    p.color = textColor
                    p.textAlign = Paint.Align.CENTER

                    val nameChars = course.courseName.filter { it != '\n' && it != ' ' }.toList()
                    val roomChars = course.room.takeIf { it.isNotBlank() }
                        ?.filter { it != '\n' && it != ' ' }?.toList() ?: emptyList()

                    // ★ v19l: 边界压力修复 — 课名竖排自适应截断, 教室横排省略截断
                    val charSize = unifiedCharSize
                    // 卡片可用高度: 顶部pad + 课名区 + (可选)教室区 + 底部pad
                    val availCardH = cardRect.height() - unifiedPad * 2
                    val roomReserveH = if (roomChars.isNotEmpty()) charSize * 0.5f else 0f  // 教室占半行高
                    val nameAvailH = (availCardH - roomReserveH).coerceAtLeast(charSize)  // 至少放1字

                    // ★ 课名垂直截断: 字数 × charSize 超过 nameAvailH → 砍到能放下字数, 末字换省略号
                    val maxNameRows = (nameAvailH / charSize).toInt().coerceIn(1, nameChars.size)
                    val nameVisible = if (nameChars.size > maxNameRows) {
                        nameChars.take(maxNameRows - 1) + '…'
                    } else nameChars

                    // ★ 课名: 竖排居中, BOLD
                    p.textSize = charSize
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    p.alpha = 255
                    val nameCenterX = cardRect.centerX()
                    val nameBlockH = charSize * nameVisible.size
                    val nameBlockTop = cardRect.top + unifiedPad + (availCardH - roomReserveH - nameBlockH) / 2f
                    for ((i, ch) in nameVisible.withIndex()) {
                        val cy = nameBlockTop + charSize * (i + 0.82f)
                        c.drawText(ch.toString(), nameCenterX, cy, p)
                    }

                    // ★ 教室: 底部横排小字角标, 0.62× 字号, 半透明, 按卡片宽截断省略
                    if (roomChars.isNotEmpty()) {
                        val roomStr = course.room.filter { it != '\n' && it != ' ' }
                        val roomSize = (charSize * 0.62f).coerceAtMost(dp(8f).toFloat()).coerceAtLeast(dp(5f).toFloat())
                        p.textSize = roomSize
                        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        p.alpha = 160
                        // 按卡片可用宽算能放几个字符, 超了截断 + …
                        val availRoomW = (cardRect.width() - unifiedPad * 2)
                        val maxRoomChars = ((availRoomW / (roomSize * 0.55f)).toInt()).coerceAtLeast(2)  // 中文≈0.55em宽
                        val roomVisible = if (roomStr.length > maxRoomChars) {
                            roomStr.take(maxRoomChars - 1) + "…"
                        } else roomStr
                        val roomCy = cardRect.bottom - unifiedPad - roomSize * 0.3f
                        c.drawText(roomVisible, nameCenterX, roomCy, p)
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
                    // 策略：
                    // 1. 若存在非默认表（用户导入/手动创建的）→ 取最新一个（按 id 倒序）
                    // 2. 否则用 default 表
                    // 3. 否则 seed mock（fallback）
                    val all = repo.getAllTables()
                    val userTable = all.filter { !it.isDefault }.maxByOrNull { it.id }
                    val t = userTable ?: repo.getDefaultTable()
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
