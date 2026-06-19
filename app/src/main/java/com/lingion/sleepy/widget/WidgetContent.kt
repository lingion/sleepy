package com.lingion.sleepy.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
 */
private data class WidgetScheme(
    val bg: Color = Color(0xFFFDFCFF),
    val primary: Color = Color(0xFF6750A4),
    val onSurface: Color = Color(0xFF1C1B1F),
    val onSurfaceVariant: Color = Color(0xFF79747E)
)

/**
 * 按 themeKey + isDark 派生小组件配色。
 * "system" / 未知 key / 拿不到 dynamic 上下文 → 退到默认预设的 light/dark。
 */
private fun resolveScheme(themeKey: String, isDark: Boolean): WidgetScheme {
    val preset = ThemePresets.byKey(themeKey)
    val s = if (isDark) preset.dark else preset.light
    return WidgetScheme(
        bg = s.surface,
        primary = s.primary,
        onSurface = s.onSurface,
        onSurfaceVariant = s.onSurfaceVariant
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

    // 解析 CourseEntity.color（格式 "#AARRGGBB" 或 "#RRGGBB"）
    val courseColor = parseColor(course.color)

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .size(width = 4.dp, height = 36.dp)
                .background(ColorProvider(courseColor))
                .cornerRadius(2.dp)
        ) {}
        Spacer(modifier = GlanceModifier.width(8.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = course.courseName,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = ColorProvider(scheme.onSurface)
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = timeStr + (if (course.room.isNotBlank()) "  ·  ${course.room}" else ""),
                style = TextStyle(
                    fontSize = 11.sp,
                    color = ColorProvider(scheme.onSurfaceVariant)
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
            text = "本周课表",
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = ColorProvider(scheme.primary)
            )
        )
        Spacer(modifier = GlanceModifier.height(6.dp))

        when {
            !data.hasTable -> EmptyTableState(scheme)
            data.days.isEmpty() -> EmptyTableState(scheme)
            else -> {
                data.days.forEach { day ->
                    WeekDayRow(day = day, scheme = scheme)
                }
            }
        }
    }
}

@Composable
private fun WeekDayRow(day: DayData, scheme: WidgetScheme) {
    val hasCourses = day.courses.isNotEmpty()
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 星期标签
        Text(
            text = day.dayName,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                color = ColorProvider(if (day.isToday) scheme.primary else scheme.onSurfaceVariant)
            ),
            modifier = GlanceModifier.width(36.dp)
        )
        Spacer(modifier = GlanceModifier.width(4.dp))

        if (hasCourses) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                day.courses.take(3).forEachIndexed { idx, c ->
                    Text(
                        text = c.courseName + (if (c.room.isNotBlank()) " ${c.room}" else ""),
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = ColorProvider(scheme.onSurface)
                        ),
                        maxLines = 1
                    )
                    if (idx < minOf(day.courses.size, 3) - 1) {
                        Spacer(modifier = GlanceModifier.height(1.dp))
                    }
                }
                if (day.courses.size > 3) {
                    Text(
                        text = "+${day.courses.size - 3}",
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = ColorProvider(scheme.onSurfaceVariant)
                        )
                    )
                }
            }
        } else {
            Text(
                text = "无课",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = ColorProvider(scheme.onSurfaceVariant)
                ),
                modifier = GlanceModifier.defaultWeight()
            )
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
                    fontWeight = FontWeight.Medium,
                    color = ColorProvider(scheme.primary)
                )
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = day.dayLabel,
                style = TextStyle(
                    fontSize = 11.sp,
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
            Spacer(modifier = GlanceModifier.height(3.dp))
            day.courses.take(3).forEachIndexed { idx, c ->
                Text(
                    text = "${TimeTableUtils.courseTimeString(c.startNode, c.step, day.timeJson, c.ownTime, c.startTime, c.endTime) ?: "第${c.startNode}节"}  ${c.courseName}",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = ColorProvider(scheme.onSurface)
                    ),
                    maxLines = 1
                )
                if (idx < minOf(day.courses.size, 3) - 1) {
                    Spacer(modifier = GlanceModifier.height(1.dp))
                }
            }
            if (day.courses.size > 3) {
                Text(
                    text = "…还有${day.courses.size - 3}节",
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
    // 找最大节次（用于决定网格行数）
    val maxNode = if (data.days.isEmpty()) 0 else data.days.maxOf { d ->
        d.courses.maxOfOrNull { it.startNode + it.step - 1 } ?: 0
    }
    val rows = minOf(maxOf(maxNode, 4), 12) // 至少4行，最多12行

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(scheme.bg))
            .cornerRadius(20.dp)
            .padding(10.dp)
            .clickable(openAppAction),
        verticalAlignment = Alignment.Top
    ) {
        // 标题行
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "节",
                style = TextStyle(
                    fontSize = 10.sp,
                    color = ColorProvider(scheme.onSurfaceVariant)
                ),
                modifier = GlanceModifier.width(18.dp)
            )
            // 7 天表头
            dayLabels.forEachIndexed { idx, label ->
                val isToday = (idx + 1) == todayDow
                Text(
                    text = label,
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = ColorProvider(if (isToday) scheme.primary else scheme.onSurfaceVariant)
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
            }
        }
        Spacer(modifier = GlanceModifier.height(4.dp))

        when {
            !data.hasTable -> EmptyTableState(scheme)
            data.days.isEmpty() -> EmptyTableState(scheme)
            else -> {
                // 网格主体：每行一节
                (1..rows).forEach { node ->
                    GridRow(
                        node = node,
                        days = data.days,
                        scheme = scheme,
                        todayDow = todayDow
                    )
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
    todayDow: Int
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // 节次标签
        Text(
            text = node.toString(),
            style = TextStyle(
                fontSize = 9.sp,
                color = ColorProvider(scheme.onSurfaceVariant)
            ),
            modifier = GlanceModifier.width(18.dp)
        )
        // 7 天格子
        days.forEach { day ->
            val course = day.courses.find { node >= it.startNode && node < it.startNode + it.step }
            val isStart = course != null && course.startNode == node
            val isTodayCol = day.dayOfWeek == todayDow

            if (isStart && course != null) {
                // 课程起始格 — 显示课程名
                val courseColor = parseColor(course.color)
                val alpha = if (day.dayOfWeek == todayDow) 1f else 0.92f
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .padding(horizontal = 1.dp, vertical = 1.dp)
                        .background(ColorProvider(courseColor))
                        .cornerRadius(4.dp)
                        .padding(3.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = course.courseName,
                        style = TextStyle(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = ColorProvider(Color.White)
                        ),
                        maxLines = 2
                    )
                    if (course.room.isNotBlank()) {
                        Text(
                            text = course.room,
                            style = TextStyle(
                                fontSize = 8.sp,
                                color = ColorProvider(Color(0xCCFFFFFF))
                            ),
                            maxLines = 1
                        )
                    }
                }
            } else if (course != null) {
                // 课程跨节但非起始格 — 空占位（被起始格覆盖）
                Spacer(modifier = GlanceModifier.defaultWidth())
            } else {
                // 无课格子
                Box(
                    modifier = GlanceModifier
                        .defaultWidth()
                        .padding(horizontal = 1.dp, vertical = 2.dp)
                        .height(22.dp)
                        .background(ColorProvider(
                            if (isTodayCol) scheme.primary.copy(alpha = 0.06f)
                            else Color.Transparent
                        ))
                        .cornerRadius(4.dp)
                ) {}
            }
        }
    }
    Spacer(modifier = GlanceModifier.height(1.dp))
}
