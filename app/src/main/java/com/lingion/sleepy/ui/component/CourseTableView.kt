package com.lingion.sleepy.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.theme.SleepyTextStyle
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.DateUtils
import java.time.LocalTime

/**
 * 时段定义 — 5 个时段（对应 HTML 里的 5 个 slot-row）
 * 与 WakeUp 默认 12 节对应: 1-2 / 3-5 / 6-7 / 8-10 / 11-13
 */
data class TimeSlot(
    val label: String,         // "1-2节"
    val start: LocalTime,
    val end: LocalTime,
    val displayStart: String,  // "08:00"
    val displayEnd: String,    // "09:35"
    val nodeStart: Int,
    val nodeEnd: Int
) {
    val nodeString: String get() = "第$nodeStart-${nodeEnd}节"
    val timeString: String get() = "$displayStart-$displayEnd"
}

private val CELL_H = 40.dp
private val CELL_GAP = 3.dp

/**
 * 每节独立行 — 不再是分组
 */
val DEFAULT_TIME_SLOTS = (1..12).map { node ->
    TimeSlot(
        label = "$node",
        start = when {
            node <= 2 -> LocalTime.of(8, 0)
            node <= 4 -> LocalTime.of(10, 20)
            node <= 6 -> LocalTime.of(14, 0)
            node <= 8 -> LocalTime.of(16, 10)
            node <= 10 -> LocalTime.of(19, 0)
            else -> LocalTime.of(20, 50)
        },
        end = when {
            node <= 2 -> LocalTime.of(9, 35)
            node <= 4 -> LocalTime.of(11, 55)
            node <= 6 -> LocalTime.of(15, 35)
            node <= 8 -> LocalTime.of(17, 55)
            node <= 10 -> LocalTime.of(20, 40)
            else -> LocalTime.of(22, 25)
        },
        displayStart = when {
            node <= 2 -> "08:00"; node <= 4 -> "10:20"
            node <= 6 -> "14:00"; node <= 8 -> "16:10"
            node <= 10 -> "19:00"; else -> "20:50"
        },
        displayEnd = when {
            node <= 2 -> "09:35"; node <= 4 -> "11:55"
            node <= 6 -> "15:35"; node <= 8 -> "17:55"
            node <= 10 -> "20:40"; else -> "22:25"
        },
        nodeStart = node,
        nodeEnd = node
    )
}

/**
 * Cards 网格视图 — 7 列 × 5 时段 (对应 switchable.html #cardsView)
 *
 * ┌────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┐
 * │    │ 周一│ 周二│ ... │ 周日│
 * ├────┼─────┼─────┼─────┼─────┼─────┼─────┼─────┤
 * │1-2 │     │ 高数│     │ ... │     │
 * │08:00-09:35│ │     │     │     │     │     │
 * ├────┼─────┼─────┼─────┼─────┼─────┼─────┼─────┤
 * │3-5 │ ... │
 */
@Composable
fun CardsGridView(
    courses: List<CourseEntity>,
    timeSlots: List<TimeSlot> = DEFAULT_TIME_SLOTS,
    today: Int = DateUtils.todayDayOfWeek(),
    onCourseClick: (CourseEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SleepyTheme.colors
    val shape16 = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceContainerHigh, RoundedCornerShape(18.dp))
            .border(0.5.dp, colors.outline.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 表头：周一~周日
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // 左上角空白格
                Box(modifier = Modifier.width(66.dp))

                for (day in 1..7) {
                    DayHeadCell(
                        day = day,
                        isToday = day == today,
                        courseCount = courses.count { it.day == day },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 每行一个时段
            for (slot in timeSlots) {
                SlotRow(
                    slot = slot,
                    allCourses = courses,
                    today = today,
                    onCourseClick = onCourseClick,
                    modifier = Modifier.fillMaxWidth().padding(bottom = CELL_GAP)
                )
            }
        }
    }
}

@Composable
private fun DayHeadCell(day: Int, isToday: Boolean, courseCount: Int, modifier: Modifier = Modifier) {
    val colors = SleepyTheme.colors
    val bg = if (isToday) colors.primaryContainer else colors.surface
    val fg = if (isToday) colors.onPrimaryContainer else colors.onSurface
    val subFg = if (isToday) colors.onPrimaryContainer.copy(alpha = 0.78f) else colors.onSurfaceVariant

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(0.5.dp, colors.outline.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = DateUtils.chineseDay(day),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = fg
            )
            Text(
                text = if (courseCount == 0) "无课" else "$courseCount 门",
                style = SleepyTextStyle.micro,
                color = subFg
            )
        }
    }
}

@Composable
private fun SlotRow(
    slot: TimeSlot,
    allCourses: List<CourseEntity>,
    today: Int,
    onCourseClick: (CourseEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // 时段标签
        TimeHeadCell(slot = slot, modifier = Modifier.width(66.dp))

        // 7 列
        for (day in 1..7) {
            val node = slot.nodeStart
            // 找到 day 这天覆盖此节的所有课程
            val covering = allCourses.filter { c ->
                c.day == day && c.startNode <= node && c.startNode + c.step - 1 >= node
            }
            when {
                // 没有课程覆盖此节 → 空单元格
                covering.isEmpty() -> {
                    EmptyCell(
                        modifier = Modifier.weight(1f),
                        isToday = day == today
                    )
                }
                // 有课程覆盖，取第一个。如果是起始节 → 渲染卡片；否则跳过
                covering.first().startNode == node -> {
                    val course = covering.first()
                    CourseCardCell(
                        course = course,
                        onClick = { onCourseClick(course) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // 被覆盖但不是起始节 → 透明占位，保持列对齐
                else -> {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TimeHeadCell(slot: TimeSlot, modifier: Modifier = Modifier) {
    val colors = SleepyTheme.colors
    Box(
        modifier = modifier
            .height(CELL_H)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(0.5.dp, colors.outline.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "第${slot.label}节",
                style = SleepyTextStyle.smallMeta.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = slot.timeString,
                style = SleepyTextStyle.micro,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyCell(modifier: Modifier = Modifier, isToday: Boolean) {
    val colors = SleepyTheme.colors
    Box(
        modifier = modifier
            .height(CELL_H)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = colors.outlineVariant.copy(alpha = 0.50f),
                shape = RoundedCornerShape(12.dp)
            )
    )
}

@Composable
private fun CourseCardCell(
    course: CourseEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = SleepyTheme.palette
    val colors = SleepyTheme.colors

    // 根据课程名 hash 选择调色板中的某个变体
    val bg = pickCourseColor(course, palette)
    val fg = colors.onSurface

    // 卡片高度 = 每节 CELL_H × 节数 + 节间 CELL_GAP
    val steps = course.step.coerceAtLeast(1)
    val cellHeight = CELL_H * steps + CELL_GAP * (steps - 1)

    Box(
        modifier = modifier
            .height(cellHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(0.5.dp, colors.outline.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = course.courseName,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                lineHeight = 13.sp
            ),
            color = fg,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun pickCourseColor(course: CourseEntity, palette: com.lingion.sleepy.ui.theme.CoursePalette): Color {
    val name = course.courseName
    return when {
        name.contains("英语") -> palette.english
        name.contains("军事") || name.contains("国防") -> palette.military
        name.contains("物理") -> palette.physics
        name.contains("历史") || name.contains("史纲") || name.contains("近代史") -> palette.history
        name.contains("心理") -> palette.psychology
        name.contains("实践") || name.contains("实习") || name.contains("实验") -> palette.practice
        name.contains("高数") || name.contains("数学") || name.contains("电路") -> palette.primary
        name.contains("体育") -> palette.surface
        name.hashCode().mod(7) == 0 -> palette.primary
        name.hashCode().mod(7) == 1 -> palette.secondary
        name.hashCode().mod(7) == 2 -> palette.tertiary
        name.hashCode().mod(7) == 3 -> palette.english
        name.hashCode().mod(7) == 4 -> palette.physics
        name.hashCode().mod(7) == 5 -> palette.psychology
        else -> palette.surface
    }
}

// =====================================================================================
// 7days full 视图 — switchable.html #fullView
// =====================================================================================

@Composable
fun FullWeekView(
    courses: List<CourseEntity>,
    today: Int = DateUtils.todayDayOfWeek(),
    onCourseClick: (CourseEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val byDay = courses.groupBy { it.day }
    val maxCount = byDay.values.maxOfOrNull { it.size } ?: 0
    val busiestDays = if (maxCount > 0) byDay.filter { it.value.size == maxCount }.keys else emptySet()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        WeekStrip(
            byDay = byDay,
            today = today,
            busiestDays = busiestDays,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        DetailPanel(
            byDay = byDay,
            today = today,
            onCourseClick = onCourseClick
        )
    }
}

@Composable
private fun WeekStrip(
    byDay: Map<Int, List<CourseEntity>>,
    today: Int,
    busiestDays: Set<Int>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (day in 1..7) {
            val dayCourses = byDay[day].orEmpty()
            val isToday = day == today
            val isBusiest = day in busiestDays && dayCourses.isNotEmpty()
            DaySummaryCell(
                day = day,
                courses = dayCourses,
                isToday = isToday,
                isBusiest = isBusiest,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DaySummaryCell(
    day: Int,
    courses: List<CourseEntity>,
    isToday: Boolean,
    isBusiest: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = SleepyTheme.colors
    val bg = if (isToday) colors.primaryContainer else colors.surfaceContainer
    val fg = if (isToday) colors.onPrimaryContainer else colors.onSurface

    Column(
        modifier = modifier
            .height(132.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 8.dp)
    ) {
        // 日期
        Text(
            text = DateUtils.chineseDay(day),
            style = SleepyTextStyle.dayLabel,
            color = fg,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Chip: 课程数
        if (courses.isEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
        } else {
            // Busiest chip: warm gray with a tiny red tint
            val chipBg = if (isBusiest) {
                Color(
                    red = (colors.surfaceVariant.red + 0.12f).coerceAtMost(1f),
                    green = colors.surfaceVariant.green,
                    blue = colors.surfaceVariant.blue,
                    alpha = 0.70f
                )
            } else {
                colors.surfaceVariant.copy(alpha = 0.70f)
            }
            val chipFg = colors.onSurfaceVariant
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(chipBg)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "${courses.size} 门",
                    style = SleepyTextStyle.smallMeta.copy(fontWeight = FontWeight.SemiBold),
                    color = chipFg
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Mini-list: 前 3 门课名
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            courses.take(3).forEach { c ->
                Text(
                    text = c.courseName,
                    style = SleepyTextStyle.micro,
                    color = if (isToday) colors.onPrimaryContainer.copy(alpha = 0.82f) else colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DetailPanel(
    byDay: Map<Int, List<CourseEntity>>,
    today: Int,
    onCourseClick: (CourseEntity) -> Unit
) {
    val colors = SleepyTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceContainerHigh)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        for (day in 1..7) {
            val dayCourses = byDay[day].orEmpty().sortedBy { it.startNode }
            DetailDayCard(
                day = day,
                courses = dayCourses,
                isToday = day == today,
                onCourseClick = onCourseClick
            )
        }
    }
}

@Composable
private fun DetailDayCard(
    day: Int,
    courses: List<CourseEntity>,
    isToday: Boolean,
    onCourseClick: (CourseEntity) -> Unit
) {
    val colors = SleepyTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (courses.isEmpty()) Color.Transparent else colors.surface)
            .let { m ->
                if (courses.isEmpty()) m.border(
                    1.dp, colors.outlineVariant, RoundedCornerShape(14.dp)
                ) else m
            }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 头部：星期 + 课程数
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = DateUtils.chineseDay(day) + if (isToday) " · 今天" else "",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface
            )
            Text(
                text = if (courses.isEmpty()) "无课程" else "${courses.size} 门课程",
                style = SleepyTextStyle.smallMeta,
                color = colors.onSurfaceVariant
            )
        }

        if (courses.isEmpty()) {
            Text(
                text = "${DateUtils.chineseDay(day)} · 无课程",
                style = SleepyTextStyle.smallMeta.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = colors.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                courses.forEach { c ->
                    LessonRow(course = c, onClick = { onCourseClick(c) })
                }
            }
        }
    }
}

@Composable
private fun LessonRow(course: CourseEntity, onClick: () -> Unit) {
    val colors = SleepyTheme.colors
    val palette = SleepyTheme.palette
    val bg = pickCourseColor(course, palette)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = course.shortNodeString,
            style = SleepyTextStyle.smallMeta.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface,
            modifier = Modifier.width(42.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = course.courseName,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = buildString {
                if (course.teacher.isNotBlank()) append(course.teacher)
                if (course.room.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(course.room)
                }
            }
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = SleepyTextStyle.smallMeta,
                    color = colors.onSurface.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// =====================================================================================
// 公共小组件
// =====================================================================================

@Composable
fun SectionHead(title: String, action: String? = null) {
    val colors = SleepyTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = SleepyTextStyle.sectionHead,
            color = colors.onSurface
        )
        if (action != null) {
            Text(
                text = action,
                style = SleepyTextStyle.smallMeta.copy(
                    fontWeight = FontWeight.Medium,
                    color = colors.primary
                )
            )
        }
    }
}


// sp 已被上面 textStyle 直接用 inline
val sp = 0.sp
