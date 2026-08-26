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
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.CourseColorUtil
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // ★ 类型化重载 getParcelableArrayList(key, Class) 是 API 33 新增,
            //   API 31/32 调用会 NoSuchMethodError → 守卫必须用 TIRAMISU 而非 S
            val sizes = opts.getParcelableArrayList(
                AppWidgetManager.OPTION_APPWIDGET_SIZES, android.util.SizeF::class.java)
            sizes?.maxByOrNull { it.width * it.height }?.let { s -> wDp = s.width.toInt(); hDp = s.height.toInt() }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // API 31/32: OPTION_APPWIDGET_SIZES 已存在但只有无类型重载(开发期过时警告, 运行时安全)
            @Suppress("DEPRECATION", "UncheckedCast")
            val legacy = opts.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            legacy?.maxByOrNull { it.width * it.height }?.let { s -> wDp = s.width.toInt(); hDp = s.height.toInt() }
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
            val colorless       = AppPrefs.isWidgetColorless(context)

            // ★ v23: 课程颜色完全对齐 CourseTableView — 黄金角 HSL 分配
            // hue = groupId.hashCode() * 137.508° → 相邻课色差最大化, 同门课永远同色
            // 亮色 S=0.55 L=0.82 (粉彩), 暗色 S=0.40 L=0.28 (沉稳)
            // 用户自定义 color 优先 (#FF6750A4 视为未设置)
            // (本地 hslToColorInt/pickCourseColor 副本已收敛至 util/CourseColorUtil.kt, 决策 D3)

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

            // ★ 空状态: 无课表时显示占位提示, 不渲染空白网格
            // ★ 学期后课程被清空 → 落到这分支; 学期状态文案优先于"去创建课表"
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

            // ★ v19b: 字号完全根据 widget 宽高自适应 (用户原话: "能不能自动根据这个宽度, 高度调整")
            // 1dp 永远 = 1dp, 但用 widget 尺寸作为 scale 单位
            // dayW = (bodyW-timeW) / 7, cardH = slotH * step
            // 单节 course 卡片: cardH = slotH (小卡), 多节: cardH = slotH*N (大卡)

            // time column 角落
            p.color = bgSurface
            c.drawRoundRect(RectF(x, y, x + timeW, y + headH),
                dp(14f).toFloat(), dp(14f).toFloat(), p)

            // ★ 学期前(课照常显示供预习): 角落画学期状态, 用户知道现在学期没开始
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

            // ★ v21: 竖排(直书) — token 化 + 拉丁组旋转 + 标点优化
            val useVertForms = AppPrefs.isVertPunctReplace(context)  // 方案B开关(默认false=方案A'旋转)

            // ★ v20b: 字号统一到「全表最小理想值」— 自适应算法 + 统一字号
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
                    val tokens = tokenizeName(course.courseName, useVertForms)
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

                    // 卡片背景色 (v19e: 对齐 CourseTableView palette) — 统一入口 CourseColorUtil (决策 D3)
                    // colorless 灰底传 gridLine(即 surfaceVariant 的 Int), 与原实现一致
                    val baseColor = CourseColorUtil.pickCourseColorInt(course, isDark, gridLine, colorless)
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

                    // nameChars 死变量已删 (v21 起 token 化走 tokenizeName, 不再用字符列表)
                    val roomChars = course.room.takeIf { it.isNotBlank() }
                        ?.filter { it != '\n' && it != ' ' }?.toList() ?: emptyList()

                    // ★ v20b: 用全表统一字号(unifiedCharSize), 截断逻辑保留
                    val availCardH = cardRect.height() - unifiedPad * 2
                    val hasRoom = roomChars.isNotEmpty()
                    val roomReserveH = if (hasRoom) (nameMinDp * 0.7f).coerceAtMost(availCardH * 0.35f) else 0f
                    val nameAvailH = (availCardH - roomReserveH).coerceAtLeast(0f)
                    val charSize = unifiedCharSize

                    // ★ v21: token 化课名 → 截断 → 按类型绘制
                    val nameCenterX = cardRect.centerX()
                    p.textSize = charSize
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    p.alpha = 255
                    p.textAlign = Paint.Align.CENTER

                    val tokens = tokenizeName(course.courseName, useVertForms)

                    // ★ v22: 字符级贪心截断 — 任何 token 都可拆到字符级, 彻底杜绝溢出
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
                    // 循环内 Log.d 渲染调试日志已删（每张课程卡都求值字符串模板, Release 也无法被 R8 消除）
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

        fun loadWeekData(context: Context): WeekData {
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
                    // 选表逻辑统一走 WidgetTableResolver（默认表优先），避免与 App 选中表不同步
                    val t = WidgetTableResolver.resolveCurrentTable()
                    val status = if (t != null)
                        DateUtils.semesterStatus(t.startDate, t.maxWeek, today)
                    else DateUtils.SemesterStatus.IN_RANGE
                    val map = if (t != null) {
                        val week = DateUtils.currentWeek(t.startDate, today)
                        (1..7).map { dow ->
                            // ★ 学期前: 第 1 周课照常显示(预习); 学期后: 课程清空, renderer 画状态行
                            val courses = if (status == DateUtils.SemesterStatus.AFTER_END) emptyList() else
                                repo.getCoursesByDayOnce(t.id, dow)
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
                        DayData(date = date, dayOfWeek = dow, courses = courses, timeJson = t.timeJson)
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
