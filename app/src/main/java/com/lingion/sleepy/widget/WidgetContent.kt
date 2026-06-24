package com.lingion.sleepy.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import android.util.Log
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.component.TimeSlot
import com.lingion.sleepy.ui.theme.ThemePresets
import com.lingion.sleepy.ui.theme.LightCoursePalette
import com.lingion.sleepy.ui.theme.DarkCoursePalette
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
import java.time.LocalDate

/**
 * 小组件渲染数据 — 让 Composable 单纯渲染，不读 DB。
 * TodayWidget.provideGlance 在 IO 线程上拉数据，组装成这个 model 喂给 Composable。
 */
data class WidgetData(
    /** 今日日期 */
    val date: LocalDate,
    /** 今日课程（已按当前周次过滤 + 排序，最多 MAX_COURSES 节） */
    val courses: List<CourseEntity>,
    /** timeJson（用于查开始/结束时间） */
    val timeJson: String,
    /** 是否有课表 */
    val hasTable: Boolean,
    /** 跟 app 主题保持一致：true=深色小组件 */
    val isDark: Boolean = false,
    /** 跟 app 主题色（ThemePresets key） */
    val themeKey: String = ThemePresets.KEY_DEFAULT
) {
    val dayName: String get() = DateUtils.localizedDay(date.dayOfWeek.value, com.lingion.sleepy.SleepyApp.get())
    val dateLabel: String get() = "${date.monthValue}/${date.dayOfMonth}"

    companion object {
        const val MAX_COURSES = 3
    }
}

@Composable
fun WidgetContent(data: WidgetData, openAppAction: Action, openCourseAction: (Long) -> Action) {
    val context = LocalContext.current
    val scheme = resolveSchemePublic(data.themeKey, data.isDark)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(scheme.bg))
            .cornerRadius(20.dp)
            .padding(14.dp)
            .clickable(openAppAction),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${context.getString(R.string.today_today)} · ${data.dayName}",
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = ColorProvider(scheme.primary)
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = data.dateLabel,
                style = TextStyle(
                    fontSize = 12.sp,
                    color = ColorProvider(scheme.onSurfaceVariant)
                )
            )
        }
        Spacer(modifier = GlanceModifier.height(8.dp))

        when {
            !data.hasTable -> EmptyTableState(scheme)
            data.courses.isEmpty() -> NoCourseState(scheme)
            else -> CourseList(data, openCourseAction, scheme)
        }
    }
}

/**
 * 4 元组：背景 / 主题强调色 / 正文色 / 次要色
 * 跟 app M3 scheme 派生方式相同：surface / primary / onSurface / onSurfaceVariant
 * 额外携带课程调色板（从主题 container 色派生，跟随主题切换）
 */
data class WidgetScheme(
    val bg: Color = Color(0xFFFDFCFF),
    val surface: Color = Color(0xFFFFFBFE),
    val primary: Color = Color(0xFF6750A4),
    val primaryContainer: Color = Color(0xFFEADDFF),
    val onPrimaryContainer: Color = Color(0xFF1C1B1F),
    val onSurface: Color = Color(0xFF1C1B1F),
    val onSurfaceVariant: Color = Color(0xFF79747E),
    val surfaceContainer: Color = Color(0xFFF3EDF7),
    val surfaceVariant: Color = Color(0xFFE7E0EC),
    val isDark: Boolean = false,
    // 课程色 — 从主题 container 色派生
    val coursePrimary: Color = Color(0xFFEADDFF),
    val courseSecondary: Color = Color(0xFFE8DEF8),
    val courseTertiary: Color = Color(0xFFFFD8E4),
    val courseEnglish: Color = Color(0xFFD8F2FF),
    val courseMilitary: Color = Color(0xFFE7F3DC),
    val coursePhysics: Color = Color(0xFFFFE7C7),
    val courseHistory: Color = Color(0xFFF7D9D9),
    val coursePsychology: Color = Color(0xFFE6DDFB),
    val coursePractice: Color = Color(0xFFD7F0E8)
)

// ── 课程色规则 — 跟首页 courseColorRules 一致 ──
internal val courseRules = listOf<Pair<(String)->Boolean, WidgetScheme.()->Color>>(
    ({ s: String -> "英语" in s }) to ({ courseEnglish }),
    ({ s: String -> "军事" in s || "国防" in s }) to ({ courseMilitary }),
    ({ s: String -> "物理" in s }) to ({ coursePhysics }),
    ({ s: String -> "历史" in s || "史纲" in s || "近代史" in s }) to ({ courseHistory }),
    ({ s: String -> "心理" in s }) to ({ coursePsychology }),
    ({ s: String -> "实践" in s || "实习" in s || "实验" in s }) to ({ coursePractice }),
    ({ s: String -> "高数" in s || "数学" in s || "电路" in s }) to ({ coursePrimary }),
    ({ s: String -> "思政" in s || "马原" in s || "毛概" in s || "形势" in s }) to ({ courseTertiary })
)
internal val hashPalette = listOf<WidgetScheme.()->Color>(
    { coursePrimary }, { courseSecondary }, { courseTertiary },
    { courseEnglish }, { coursePhysics }, { coursePsychology }
)
internal fun courseColor(name: String, scheme: WidgetScheme): Color {
    courseRules.firstOrNull { (m, _) -> m(name) }?.let { (_, s) -> return scheme.s() }
    return hashPalette[(name.hashCode() and 0x7FFFFFFF) % hashPalette.size].invoke(scheme)
}

/**
 * 按 themeKey + isDark 派生小组件配色。
 * 课程色使用 app 的 LightCoursePalette / DarkCoursePalette（全局统一，不随主题变）。
 */
internal fun resolveSchemePublic(themeKey: String, isDark: Boolean): WidgetScheme {
    val preset = ThemePresets.byKey(themeKey)
    val s = if (isDark) preset.dark else preset.light
    val palette = if (isDark) DarkCoursePalette else LightCoursePalette
    return WidgetScheme(
        bg = s.surface,
        surface = s.surface,
        primary = s.primary,
        primaryContainer = s.primaryContainer,
        onPrimaryContainer = s.onPrimaryContainer,
        onSurface = s.onSurface,
        onSurfaceVariant = s.onSurfaceVariant,
        surfaceContainer = s.surfaceContainer,
        surfaceVariant = s.surfaceVariant,
        isDark = isDark,
        coursePrimary = palette.primary,
        courseSecondary = palette.secondary,
        courseTertiary = palette.tertiary,
        courseEnglish = palette.english,
        courseMilitary = palette.military,
        coursePhysics = palette.physics,
        courseHistory = palette.history,
        coursePsychology = palette.psychology,
        coursePractice = palette.practice
    )
}

@Composable
private fun EmptyTableState(scheme: WidgetScheme) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = context.getString(R.string.widget_create_schedule),
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = ColorProvider(scheme.onSurface)
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = context.getString(R.string.widget_open_sleepy),
            style = TextStyle(
                fontSize = 12.sp,
                color = ColorProvider(scheme.onSurfaceVariant)
            )
        )
    }
}

@Composable
private fun NoCourseState(scheme: WidgetScheme) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = context.getString(R.string.today_no_course),
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = ColorProvider(scheme.onSurface)
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = context.getString(R.string.today_rest),
            style = TextStyle(
                fontSize = 12.sp,
                color = ColorProvider(scheme.onSurfaceVariant)
            )
        )
    }
}

@Composable
private fun CourseList(data: WidgetData, openCourseAction: (Long) -> Action, scheme: WidgetScheme) {
    val context = LocalContext.current
    val visible = data.courses.take(WidgetData.MAX_COURSES)
    val hidden = data.courses.size - visible.size

    Column(modifier = GlanceModifier.fillMaxSize()) {
        visible.forEachIndexed { idx, course ->
            if (idx > 0) Spacer(modifier = GlanceModifier.height(6.dp))
            CourseRow(
                course = course,
                timeJson = data.timeJson,
                scheme = scheme,
                onClick = openCourseAction(course.id)
            )
        }
        if (hidden > 0) {
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = context.getString(R.string.more_sections, hidden),
                style = TextStyle(
                    fontSize = 11.sp,
                    color = ColorProvider(scheme.onSurfaceVariant)
                )
            )
        }
    }
}

@Composable
private fun CourseRow(course: CourseEntity, timeJson: String, scheme: WidgetScheme, onClick: Action) {
    val context = LocalContext.current
    val timeStr = TimeTableUtils.courseTimeString(
        courseStartNode = course.startNode,
        courseStep = course.step,
        timeJson = timeJson,
        ownTime = course.ownTime,
        startTime = course.startTime,
        endTime = course.endTime
    ) ?: context.getString(R.string.course_node_format, course.startNode.toString())

    val bgColor = courseColor(course.courseName, scheme)

    // 课程色胶囊 — 复刻首页 LessonRow 样式
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ColorProvider(bgColor))
            .cornerRadius(10.dp)
            .clickable(onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = course.courseName,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = ColorProvider(scheme.onSurface)
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = timeStr + (if (course.room.isNotBlank()) "  ·  ${course.room}" else ""),
                style = TextStyle(
                    fontSize = 10.sp,
                    color = ColorProvider(scheme.onSurfaceVariant)  // lighter secondary
                ),
                maxLines = 1
            )
        }
    }
}

/**
 * 解析 #AARRGGBB / #RRGGBB / 8 位 hex 字符串为 Color。
 * 失败时回退默认淡紫。
 */
private fun parseColor(hex: String): Color {
    val cleaned = hex.removePrefix("#").removePrefix("0x")
    return try {
        when (cleaned.length) {
            8 -> Color(cleaned.toLong(16) or 0xFF000000L)
            6 -> Color(0xFF000000L or cleaned.toLong(16))
            else -> Color(0xFF6750A4)
        }
    } catch (_: Exception) {
        Color(0xFF6750A4)
    }
}

// ═══════════════════════════════════════════════════════
// Multi-day widget data & composables
// ═══════════════════════════════════════════════════════

/** 单天数据 */
data class DayData(
    val date: LocalDate,
    val dayOfWeek: Int,
    val courses: List<CourseEntity>,
    val timeJson: String
) {
    val dayLabel: String get() = DateUtils.shortDate(date)
    val dayName: String get() = DateUtils.localizedDay(dayOfWeek, com.lingion.sleepy.SleepyApp.get())
    val subtitle: String get() = if (date == LocalDate.now()) com.lingion.sleepy.SleepyApp.get().getString(com.lingion.sleepy.R.string.today_text) else dayName
    val isToday: Boolean get() = date == LocalDate.now()
    val isTomorrow: Boolean get() = date == LocalDate.now().plusDays(1)
}

/** 周视图数据 */
data class WeekData(
    val days: List<DayData>,
    val hasTable: Boolean,
    val isDark: Boolean = false,
    val themeKey: String = ThemePresets.KEY_DEFAULT,
    val displayMode: String = "node",
    val showDate: Boolean = false,
    val visibleDays: Set<Int> = (1..7).toSet()
)

/** 两天视图数据 */
data class TwoDayData(
    val days: List<DayData>,
    val hasTable: Boolean,
    val isDark: Boolean = false,
    val themeKey: String = ThemePresets.KEY_DEFAULT
)

@Composable
fun WeekListContent(data: WeekData, openAppAction: Action) {
    val context = LocalContext.current
    val scheme = resolveSchemePublic(data.themeKey, data.isDark)
    val todayDow = LocalDate.now().dayOfWeek.value
    val dayLabels = listOf("", 
        context.getString(R.string.day_short_1),
        context.getString(R.string.day_short_2),
        context.getString(R.string.day_short_3),
        context.getString(R.string.day_short_4),
        context.getString(R.string.day_short_5),
        context.getString(R.string.day_short_6),
        context.getString(R.string.day_short_7))
    val colGap = 4.dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(scheme.bg))
            .cornerRadius(20.dp)
            .padding(6.dp)
            .clickable(openAppAction),
        verticalAlignment = Alignment.Top
    ) {
        when {
            !data.hasTable -> EmptyTableState(scheme)
            data.days.isEmpty() -> EmptyTableState(scheme)
            else -> {
                // 7 列竖向并排 — 不用 Spacer（LinearLayout weight+固定宽度混用 bug）
                // 间距方案：外层 Box(defaultWeight) padding 透明 → 内层 Column 有背景
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight(),
                    verticalAlignment = Alignment.Top
                ) {
                    data.days.forEach { day ->
                        val isToday = day.dayOfWeek == todayDow
                        val cardBg = if (isToday) scheme.primaryContainer else scheme.surfaceContainer
                        val titleColor = if (isToday) scheme.onPrimaryContainer else scheme.onSurface
                        val nameColor = if (isToday) scheme.onPrimaryContainer else scheme.onSurfaceVariant
                        val chipBg = scheme.surfaceVariant
                        val chipFg = scheme.onSurfaceVariant

                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .fillMaxHeight()
                                .padding(horizontal = 2.dp)
                        ) {
                            LazyColumn(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .background(ColorProvider(cardBg))
                                    .cornerRadius(14.dp)
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // 星期标题
                                item {
                                    Text(
                                        text = dayLabels[day.dayOfWeek],
                                        style = TextStyle(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorProvider(titleColor)
                                        )
                                    )
                                }
                                item { Spacer(modifier = GlanceModifier.height(6.dp)) }

                                if (day.courses.isNotEmpty()) {
                                    // Chip「X门」
                                    item {
                                        Box(
                                            modifier = GlanceModifier
                                                .background(ColorProvider(chipBg))
                                                .cornerRadius(50.dp)
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = context.getString(R.string.n_courses, day.courses.size),
                                                style = TextStyle(
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ColorProvider(chipFg)
                                                )
                                            )
                                        }
                                    }
                                    item { Spacer(modifier = GlanceModifier.height(4.dp)) }

                                    // 每门课单独一个item，分隔线单独一个item，ListView不会混淆
                                    day.courses.forEachIndexed { idx, c ->
                                        item {
                                            Text(
                                                text = c.courseName,
                                                style = TextStyle(
                                                    fontSize = 9.sp,
                                                    color = ColorProvider(nameColor)
                                                ),
                                                maxLines = 2
                                            )
                                        }
                                        // 课程之间加分隔线，最后一门不加
                                        if (idx < day.courses.size - 1) {
                                            item {
                                                Spacer(modifier = GlanceModifier.height(1.dp))
                                                Box(
                                                    modifier = GlanceModifier
                                                        .fillMaxWidth()
                                                        .height(1.dp)
                                                        .background(ColorProvider(Color(0x5979747E)))
                                                ) {}
                                                Spacer(modifier = GlanceModifier.height(1.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }  // end Box
                    }
                }
            }
        }
    }
}

@Composable
fun TwoDayContent(data: TwoDayData, openAppAction: Action) {
    val context = LocalContext.current
    val scheme = resolveSchemePublic(data.themeKey, data.isDark)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(scheme.bg))
            .cornerRadius(20.dp)
            .padding(12.dp)
            .clickable(openAppAction),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = context.getString(R.string.widget_twoday_label),
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = ColorProvider(scheme.primary)
            )
        )
        Spacer(modifier = GlanceModifier.height(8.dp))

        when {
            !data.hasTable -> EmptyTableState(scheme)
            data.days.isEmpty() -> EmptyTableState(scheme)
            else -> {
                data.days.forEach { day ->
                    TwoDaySection(day = day, scheme = scheme)
                    if (day != data.days.last()) {
                        Spacer(modifier = GlanceModifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TwoDaySection(day: DayData, scheme: WidgetScheme) {
    val context = LocalContext.current
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        // 天标题
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (day.isToday) context.getString(R.string.today_today) else if (day.isTomorrow) context.getString(R.string.tomorrow) else day.dayName,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(scheme.primary)
                )
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = day.dayLabel,
                style = TextStyle(
                    fontSize = 10.sp,
                    color = ColorProvider(scheme.onSurfaceVariant)
                )
            )
        }

        if (day.courses.isEmpty()) {
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = context.getString(R.string.no_course),
                style = TextStyle(
                    fontSize = 11.sp,
                    color = ColorProvider(scheme.onSurfaceVariant)
                )
            )
        } else {
            Spacer(modifier = GlanceModifier.height(4.dp))
            day.courses.take(3).forEachIndexed { idx, c ->
                // 课程色胶囊
                val bgColor = courseColor(c.courseName, scheme)
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(ColorProvider(bgColor))
                        .cornerRadius(8.dp)
                        .padding(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = c.courseName,
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = ColorProvider(scheme.onSurface)
                            ),
                            maxLines = 1
                        )
                        val meta = buildString {
                            append(TimeTableUtils.courseTimeString(c.startNode, c.step, day.timeJson, c.ownTime, c.startTime, c.endTime) ?: context.getString(R.string.section_single, c.startNode))
                            if (c.room.isNotBlank()) append("  ·  ${c.room}")
                        }
                        Text(
                            text = meta,
                            style = TextStyle(
                                fontSize = 9.sp,
                                color = ColorProvider(scheme.onSurfaceVariant)  // lighter secondary
                            ),
                            maxLines = 1
                        )
                    }
                }
                if (idx < minOf(day.courses.size, 3) - 1) {
                    Spacer(modifier = GlanceModifier.height(3.dp))
                }
            }
            if (day.courses.size > 3) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = context.getString(R.string.more_sections, day.courses.size - 3),
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = ColorProvider(scheme.onSurfaceVariant)
                    )
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// Week Grid — 7列网格样式
// ═══════════════════════════════════════════════════════

@Composable
fun WeekGridContent(data: WeekData, openAppAction: Action, widgetWidthDp: Int = 320, widgetHeightDp: Int = 280) {
    val scheme = resolveSchemePublic(data.themeKey, data.isDark)
    val todayDow = LocalDate.now().dayOfWeek.value

    val timeJson = data.days.firstOrNull()?.timeJson ?: TimeTableUtils.DEFAULT_TIME_JSON
    val allTimeSlots = TimeTableUtils.timeSlotsFor(timeJson)
    val sortedDays = data.visibleDays.sorted()

    // ★ FIX 1: maxNode = max(startNode + step - 1) — 之前 maxSumStep 是 bug
    val rawMaxNode = data.days.maxOfOrNull { d ->
        d.courses.maxOfOrNull { it.startNode + it.step - 1 } ?: 0
    } ?: 0
    val maxNode = rawMaxNode.coerceIn(4, 10)
    val timeSlots = allTimeSlots.take(maxNode)

    val containerBg = scheme.surfaceContainer
    val cellBg = scheme.surface
    val todayHeadBg = scheme.primaryContainer
    val onSurface = scheme.onSurface
    val onSurfaceVar = scheme.onSurfaceVariant
    val onPrimaryCont = scheme.onPrimaryContainer

    // ★ v1.0.16-rebuild-5: 所有尺寸从 widget 真实宽高算，减 outerPadding
    // 之前 colW = widgetWidthDp/8f → 8×colW = widgetWidthDp，但外层 padding(6dp) → 溢出 12dp
    val outerPad = 12f  // 6dp × 2（上下或左右）
    val headerHdp = 30f
    val bodyW = (widgetWidthDp - outerPad).coerceAtLeast(100f)  // 内宽 = widget 宽 - 左右 pad
    val bodyH = (widgetHeightDp - outerPad - headerHdp).coerceAtLeast(100f)  // body 高 = widget 高 - 上下 pad - header
    val colW = (bodyW / 8f).dp   // 8 列平分内宽
    val slotH = (bodyH / maxNode).dp  // maxNode 行平分 body 高
    val totalBodyH = (slotH.value * maxNode).dp
    val rowH = slotH  // 单节高度 = slotH
    val headerH = 30.dp  // Glance 需要 Dp 类型

    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(ColorProvider(containerBg))
            .cornerRadius(18.dp)
            .padding(6.dp)
            .clickable(openAppAction),
        verticalAlignment = Alignment.Top
    ) {
        if (!data.hasTable || data.days.isEmpty()) {
            EmptyTableState(scheme)
        } else {
            // ====== Header — 8 cells 显式宽度（不用 defaultWeight） ======
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(headerH),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = GlanceModifier.width(colW).fillMaxHeight()) {}
                for (day in 1..7) {
                    val isVisible = day in sortedDays
                    val isToday = day == todayDow
                    val dayData = data.days.firstOrNull { it.dayOfWeek == day }
                    val count = dayData?.courses?.size ?: 0
                    val dateStr = if (data.showDate && dayData != null) DateUtils.shortDate(dayData.date) else null

                    Box(
                        modifier = GlanceModifier.width(colW).fillMaxHeight()
                            .padding(horizontal = 1.dp)
                            .background(if (isVisible) ColorProvider(if (isToday) todayHeadBg else cellBg) else ColorProvider(Color.Transparent))
                            .cornerRadius(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isVisible) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = DateUtils.localizedDay(day, LocalContext.current),
                                    style = TextStyle(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorProvider(if (isToday) onPrimaryCont else onSurface)
                                    )
                                )
                                if (dateStr != null) {
                                    Text(
                                        text = dateStr,
                                        style = TextStyle(fontSize = 7.sp, color = ColorProvider(onSurfaceVar))
                                    )
                                } else {
                                    Text(
                                        text = if (count > 0) "${count}" else "—",
                                        style = TextStyle(fontSize = 7.sp, color = ColorProvider(onSurfaceVar))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ====== Body — 8 cells 显式宽度（跟 header 同宽！） ======
            val totalBodyHeight = (rowH.value * maxNode).dp
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Top
            ) {
                // 时间列 — maxNode 个 Box（每个 rowH 高度）
                Column(
                    modifier = GlanceModifier.width(colW).height(totalBodyHeight).padding(end = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (i in 1..maxNode) {
                        val slot = timeSlots.getOrNull(i - 1)
                        Box(
                            modifier = GlanceModifier.fillMaxWidth().height(rowH),
                            contentAlignment = Alignment.Center
                        ) {
                            if (slot != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${i}",
                                        style = TextStyle(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorProvider(onSurface)
                                        )
                                    )
                                    Text(
                                        text = slot.displayStart,
                                        style = TextStyle(
                                            fontSize = 6.sp,
                                            color = ColorProvider(onSurfaceVar)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // 7 天列 — 固定 7 列位置，visibleDays 之外的列透明空 Box
                for (day in 1..7) {
                    val isVisible = day in sortedDays
                    val dayData = data.days.firstOrNull { it.dayOfWeek == day }
                    val dayCourses = dayData?.courses ?: emptyList()

                    if (isVisible) {
                        Column(
                            modifier = GlanceModifier.width(colW).height(totalBodyHeight)
                                .padding(horizontal = 1.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            var prevEndNode = 0
                            for (i in 1..maxNode) {
                                if (i <= prevEndNode) continue
                                val course = dayCourses.firstOrNull { it.startNode == i }
                                if (course != null) {
                                    // ★ FIX 3: 跨节 Box height = perNodeHeight * step（真 rowspan）
                                    val cardH = (rowH.value * course.step).dp
                                    Box(
                                        modifier = GlanceModifier.fillMaxWidth()
                                            .height(cardH)
                                            .background(ColorProvider(courseColor(course.courseName, scheme)))
                                            .cornerRadius(4.dp)
                                            .padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // ★ FIX 4: 不再显示 "1-2" 标签 — 用户核心诉求
                                        Text(
                                            text = course.courseName,
                                            style = TextStyle(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ColorProvider(onSurface)
                                            ),
                                            maxLines = (course.step * 2).coerceAtLeast(1)
                                        )
                                    }
                                    prevEndNode = course.startNode + course.step - 1
                                } else {
                                    Box(modifier = GlanceModifier.fillMaxWidth().height(rowH)) {}
                                }
                            }
                        }
                    } else {
                        Box(modifier = GlanceModifier.width(colW).height(totalBodyHeight)) {}
                    }
                }
            }
        }
    }
}

/**
 * 课程名自适应字号 — 长名字自动缩小，**绝不截断**。
 * 算法：把字符折算成"等宽字符"（CJK=1.0em, ASCII=0.55em），按总等效宽度反推字号档位；
 * 节数越多卡片越高，字号额外 +1~2sp；下限 6sp 保可读。
 * 额外限制：字号 ≤ dayUnitH × 0.7（保证 1 行文字能放进单节卡片）。
 */
private fun autoFitCourseFontSize(name: String, step: Int, dayUnitH: Dp = 50.dp): Int {
    val len = name.length
    if (len == 0) return 7
    val asciiCount = name.count { it.code < 128 }
    val isAllAscii = asciiCount == len
    val isAllCjk = (len - asciiCount) == len

    val charEm = when {
        isAllCjk -> len.toFloat()
        isAllAscii -> len * 0.55f
        else -> asciiCount * 0.55f + (len - asciiCount) * 1.0f
    }

    val baseSp = when {
        charEm <= 2.5f -> 11
        charEm <= 3.5f -> 10
        charEm <= 5.0f -> 9
        charEm <= 7.0f -> 8
        charEm <= 10.0f -> 7
        charEm <= 14.0f -> 6
        else -> 5
    }

    val boost = (step - 1).coerceIn(0, 2)
    val unitCap = (dayUnitH.value * 0.7f).toInt().coerceAtLeast(6)
    return (baseSp + boost).coerceIn(6, unitCap.coerceAtMost(12))
}
