package com.lingion.sleepy.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
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
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.theme.ThemePresets
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
    val dayName: String get() = DateUtils.chineseDay(date.dayOfWeek.value)
    val dateLabel: String get() = "${date.monthValue}月${date.dayOfMonth}日"

    companion object {
        const val MAX_COURSES = 3
    }
}

@Composable
fun WidgetContent(data: WidgetData, openAppAction: Action, openCourseAction: (Long) -> Action) {
    val scheme = resolveScheme(data.themeKey, data.isDark)

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
                text = "今日 · ${data.dayName}",
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
private data class WidgetScheme(
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
private val courseRules = listOf<Pair<(String)->Boolean, WidgetScheme.()->Color>>(
    ({ s: String -> "英语" in s }) to ({ courseEnglish }),
    ({ s: String -> "军事" in s || "国防" in s }) to ({ courseMilitary }),
    ({ s: String -> "物理" in s }) to ({ coursePhysics }),
    ({ s: String -> "历史" in s || "史纲" in s || "近代史" in s }) to ({ courseHistory }),
    ({ s: String -> "心理" in s }) to ({ coursePsychology }),
    ({ s: String -> "实践" in s || "实习" in s || "实验" in s }) to ({ coursePractice }),
    ({ s: String -> "高数" in s || "数学" in s || "电路" in s }) to ({ coursePrimary }),
    ({ s: String -> "思政" in s || "马原" in s || "毛概" in s || "形势" in s }) to ({ courseTertiary })
)
private val hashPalette = listOf<WidgetScheme.()->Color>(
    { coursePrimary }, { courseSecondary }, { courseTertiary },
    { courseEnglish }, { coursePhysics }, { coursePsychology }
)
private fun courseColor(name: String, scheme: WidgetScheme): Color {
    courseRules.firstOrNull { (m, _) -> m(name) }?.let { (_, s) -> return scheme.s() }
    return hashPalette[(name.hashCode() and 0x7FFFFFFF) % hashPalette.size].invoke(scheme)
}

/**
 * 按 themeKey + isDark 派生小组件配色。
 * "system" / 未知 key / 拿不到 dynamic 上下文 → 退到默认预设的 light/dark。
 */
private fun resolveScheme(themeKey: String, isDark: Boolean): WidgetScheme {
    val preset = ThemePresets.byKey(themeKey)
    val s = if (isDark) preset.dark else preset.light
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
        // 课程色从主题 container 色派生
        coursePrimary = s.primaryContainer,
        courseSecondary = s.secondaryContainer,
        courseTertiary = s.tertiaryContainer,
        courseEnglish = s.primaryContainer,
        courseMilitary = s.secondaryContainer,
        coursePhysics = s.tertiaryContainer,
        courseHistory = s.primaryContainer,
        coursePsychology = s.secondaryContainer,
        coursePractice = s.tertiaryContainer
    )
}

@Composable
private fun EmptyTableState(scheme: WidgetScheme) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "请先创建课表",
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = ColorProvider(scheme.onSurface)
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = "打开 Sleepy 开始添加",
            style = TextStyle(
                fontSize = 12.sp,
                color = ColorProvider(scheme.onSurfaceVariant)
            )
        )
    }
}

@Composable
private fun NoCourseState(scheme: WidgetScheme) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "今天没有课",
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = ColorProvider(scheme.onSurface)
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = "休息 / 自习 都可以",
            style = TextStyle(
                fontSize = 12.sp,
                color = ColorProvider(scheme.onSurfaceVariant)
            )
        )
    }
}

@Composable
private fun CourseList(data: WidgetData, openCourseAction: (Long) -> Action, scheme: WidgetScheme) {
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
                text = "还有 $hidden 节…",
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
    val timeStr = TimeTableUtils.courseTimeString(
        courseStartNode = course.startNode,
        courseStep = course.step,
        timeJson = timeJson,
        ownTime = course.ownTime,
        startTime = course.startTime,
        endTime = course.endTime
    ) ?: "第 ${course.startNode} 节"

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
                    color = ColorProvider(scheme.onSurface.copy(alpha = 0.72f))
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
    val dayName: String get() = DateUtils.chineseDay(dayOfWeek)
    val subtitle: String get() = if (date == LocalDate.now()) "今天" else dayName
    val isToday: Boolean get() = date == LocalDate.now()
    val isTomorrow: Boolean get() = date == LocalDate.now().plusDays(1)
}

/** 周视图数据 */
data class WeekData(
    val days: List<DayData>,
    val hasTable: Boolean,
    val isDark: Boolean = false,
    val themeKey: String = ThemePresets.KEY_DEFAULT
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
    val scheme = resolveScheme(data.themeKey, data.isDark)
    val todayDow = LocalDate.now().dayOfWeek.value
    val dayLabels = listOf("", "一", "二", "三", "四", "五", "六", "日")
    // 固定間距，不依賴 LocalSize（可能返回不準確的值）
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
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight(),
                    verticalAlignment = Alignment.Top
                ) {
                    data.days.forEachIndexed { dayIdx, day ->
                        if (dayIdx > 0) {
                            Spacer(modifier = GlanceModifier.width(colGap))
                        }
                        val isToday = day.dayOfWeek == todayDow
                        val cardBg = if (isToday) scheme.primaryContainer else scheme.surfaceContainer
                        val titleColor = if (isToday) scheme.onPrimaryContainer else scheme.onSurface
                        val nameColor = if (isToday) scheme.onPrimaryContainer else scheme.onSurfaceVariant
                        val chipBg = scheme.surfaceVariant
                        val chipFg = scheme.onSurfaceVariant

                        Column(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .fillMaxHeight()
                                .background(ColorProvider(cardBg))
                                .cornerRadius(14.dp)
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 星期标题
                            Text(
                                text = dayLabels[day.dayOfWeek],
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(titleColor)
                                )
                            )
                            Spacer(modifier = GlanceModifier.height(6.dp))

                            if (day.courses.isNotEmpty()) {
                                // Chip「X门」— surfaceVariant 背景
                                Box(
                                    modifier = GlanceModifier
                                        .background(ColorProvider(chipBg))
                                        .cornerRadius(50.dp)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${day.courses.size}门",
                                        style = TextStyle(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorProvider(chipFg)
                                        )
                                    )
                                }
                                Spacer(modifier = GlanceModifier.height(4.dp))

                                // 课程名列表
                                day.courses.take(5).forEachIndexed { idx, c ->
                                    Text(
                                        text = c.courseName,
                                        style = TextStyle(
                                            fontSize = 9.sp,
                                            color = ColorProvider(nameColor)
                                        ),
                                        maxLines = 2
                                    )
                                    if (idx < minOf(day.courses.size, 5) - 1) {
                                        Spacer(modifier = GlanceModifier.height(2.dp))
                                    }
                                }
                                if (day.courses.size > 5) {
                                    Text(
                                        text = "+${day.courses.size - 5}",
                                        style = TextStyle(
                                            fontSize = 8.sp,
                                            color = ColorProvider(chipFg)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TwoDayContent(data: TwoDayData, openAppAction: Action) {
    val scheme = resolveScheme(data.themeKey, data.isDark)

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
            text = "最近两天",
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
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        // 天标题
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (day.isToday) "今天" else if (day.isTomorrow) "明天" else day.dayName,
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
                text = "无课",
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
                            append(TimeTableUtils.courseTimeString(c.startNode, c.step, day.timeJson, c.ownTime, c.startTime, c.endTime) ?: "第 ${c.startNode} 节")
                            if (c.room.isNotBlank()) append("  ·  ${c.room}")
                        }
                        Text(
                            text = meta,
                            style = TextStyle(
                                fontSize = 9.sp,
                                color = ColorProvider(scheme.onSurface.copy(alpha = 0.72f))
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
                    text = "…还有 ${day.courses.size - 3} 节",
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
fun WeekGridContent(data: WeekData, openAppAction: Action) {
    val scheme = resolveScheme(data.themeKey, data.isDark)
    val todayDow = LocalDate.now().dayOfWeek.value
    val dayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    val maxNode = if (data.days.isEmpty()) 0 else data.days.maxOf { d ->
        d.courses.maxOfOrNull { it.startNode + it.step - 1 } ?: 0
    }
    val rows = minOf(maxOf(maxNode, 4), 12)
    // 按比例算间距
    val totalWidth = LocalSize.current.width
    val gridGap = (totalWidth.value * 0.01f).dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(scheme.bg))
            .cornerRadius(20.dp)
            .padding(10.dp)
            .clickable(openAppAction),
        verticalAlignment = Alignment.Top
    ) {
        // 表头
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "",
                modifier = GlanceModifier.width(14.dp)
            )
            dayLabels.forEachIndexed { idx, label ->
                val isToday = (idx + 1) == todayDow
                Box(
                    modifier = GlanceModifier.defaultWeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = ColorProvider(if (isToday) scheme.primary else scheme.onSurfaceVariant)
                        )
                    )
                }
            }
        }

        when {
            !data.hasTable -> EmptyTableState(scheme)
            data.days.isEmpty() -> EmptyTableState(scheme)
            else -> {
                (1..rows).forEach { node ->
                    GridRow(node, data.days, scheme, todayDow, gridGap)
                }
            }
        }
    }
}

@Composable
private fun GridRow(
    node: Int,
    days: List<DayData>,
    scheme: WidgetScheme,
    todayDow: Int,
    gap: androidx.compose.ui.unit.Dp
) {
    val cellH = 26.dp

    Row(
        modifier = GlanceModifier.fillMaxWidth().height(cellH),
        verticalAlignment = Alignment.Top
    ) {
        // 节次标签
        Text(
            text = node.toString(),
            style = TextStyle(
                fontSize = 9.sp,
                color = ColorProvider(scheme.onSurfaceVariant)
            ),
            modifier = GlanceModifier.width(14.dp).padding(top = 4.dp)
        )
        // 7 天格子 — 一比一复刻首页 CourseCardCell
        days.forEach { day ->
            val course = day.courses.find { node >= it.startNode && node < it.startNode + it.step }
            val isStart = course != null && course.startNode == node
            val isLastRow = course != null && node == course.startNode + course.step - 1
            val isTodayCol = day.dayOfWeek == todayDow

            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(cellH)
                    .padding(horizontal = gap, vertical = gap * 0.5f)
            ) {
                if (isStart && course != null) {
                    val bgColor = courseColor(course.courseName, scheme)
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .background(ColorProvider(bgColor))
                            .cornerRadius(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = course.courseName,
                            style = TextStyle(
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(scheme.onSurface)
                            ),
                            maxLines = 3
                        )
                    }
                } else if (course != null) {
                    val bgColor = courseColor(course.courseName, scheme)
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .background(ColorProvider(bgColor))
                            .cornerRadius(if (isLastRow) 12.dp else 0.dp)
                    ) {}
                } else {
                    if (isTodayCol) {
                        Box(
                            modifier = GlanceModifier
                                .fillMaxSize()
                                .background(ColorProvider(scheme.primary.copy(alpha = 0.08f)))
                                .cornerRadius(8.dp)
                        ) {}
                    }
                }
            }
        }
    }
    Spacer(modifier = GlanceModifier.height(gap))
}
