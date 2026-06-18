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

private val CELL_H = 52.dp

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
    val maxNode = timeSlots.maxOfOrNull { it.nodeEnd } ?: 12
    val scrollState = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current

    // Layout constants
    val headH = 58.dp  // 52dp header + 6dp bottom padding
    val timeW = 68.dp
    val slotH = 52.dp
    val gapH = 4.dp
    val gapW = 5.dp
    val totalH = headH + slotH * maxNode + gapH * maxNode

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
                .verticalScroll(scrollState)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalH)
            ) {
                // Layer 1: day headers + time labels (Compose flow)
                Column(modifier = Modifier.matchParentSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(gapW)
                    ) {
                        Box(modifier = Modifier.width(timeW))
                        for (day in 1..7) {
                            DayHeadCell(
                                day = day,
                                isToday = day == today,
                                courseCount = courses.count { it.day == day },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    for (slot in timeSlots) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(slotH)
                                .padding(bottom = gapH),
                            horizontalArrangement = Arrangement.spacedBy(gapW)
                        ) {
                            TimeHeadCell(slot = slot, modifier = Modifier.width(timeW))
                            repeat(7) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }

                // Layer 2: cards — Layout for pixel-perfect positioning
                if (courses.isNotEmpty()) {
                    val timeWPx = with(density) { timeW.roundToPx() }
                    val gapWPx = with(density) { gapW.roundToPx() }
                    val headHPx = with(density) { headH.roundToPx() }
                    val slotHPx = with(density) { slotH.roundToPx() }
                    val gapHPx = with(density) { gapH.roundToPx() }

                    androidx.compose.ui.layout.Layout(
                        content = {
                            courses.forEach { course ->
                                CourseCardCell(
                                    course = course,
                                    steps = course.step.coerceAtLeast(1),
                                    onClick = { onCourseClick(course) }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) { measurables, constraints ->
                        val w = constraints.maxWidth
                        val h = constraints.maxHeight
                        val colW = (w - timeWPx - gapWPx * 7) / 7

                        val placeables = measurables.mapIndexed { idx, measurable ->
                            val course = courses[idx]
                            val dayIdx = course.day - 1
                            val nodeIdx = course.startNode - 1
                            val steps = course.step.coerceAtLeast(1)

                            val x = timeWPx + gapWPx + dayIdx * (colW + gapWPx)
                            val y = headHPx + nodeIdx * (slotHPx + gapHPx)
                            val cardW = colW
                            val cardH = steps * slotHPx + (steps - 1) * gapHPx

                            val placeable = measurable.measure(
                                androidx.compose.ui.unit.Constraints.fixed(cardW, cardH)
                            )
                            Triple(x, y, placeable)
                        }

                        layout(w, h) {
                            placeables.forEach { (x, y, placeable) ->
                                placeable.placeRelative(x, y)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseCardCell(
    course: CourseEntity,
    steps: Int,
    onClick: () -> Unit
) {
    val palette = SleepyTheme.palette
    val colors = SleepyTheme.colors
    val bg = pickCourseColor(course, palette)
    val fg = colors.onSurface
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(2.dp)
            .clip(shape)
            .background(bg)
            .border(0.5.dp, colors.outline.copy(alpha = 0.12f), shape)
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            if (steps > 1) {
                Text(
                    text = "${course.startNode}-${course.startNode + steps - 1}节",
                    style = SleepyTextStyle.micro,
                    color = fg.copy(alpha = 0.65f)
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
private fun TimeHeadCell(slot: TimeSlot, modifier: Modifier = Modifier) {
    val colors = SleepyTheme.colors
    Box(
        modifier = modifier
            .height(CELL_H)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(0.5.dp, colors.outline.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .padding(4.dp),
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        WeekStrip(
            byDay = byDay,
            today = today,
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (day in 1..7) {
            val dayCourses = byDay[day].orEmpty()
            val isToday = day == today
            DaySummaryCell(
                day = day,
                courses = dayCourses,
                isToday = isToday,
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
    modifier: Modifier = Modifier
) {
    val colors = SleepyTheme.colors
    val bg = if (isToday) colors.primaryContainer else colors.surfaceContainer
    val fg = if (isToday) colors.onPrimaryContainer else colors.onSurface
    // Chip: solid surfaceVariant with full alpha for dark mode readability
    val chipBg = colors.surfaceVariant
    val chipFg = colors.onSurfaceVariant

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
        // 头部：星期 + 今天标记
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = DateUtils.chineseDay(day) + if (isToday) " · 今天" else "",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface
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
