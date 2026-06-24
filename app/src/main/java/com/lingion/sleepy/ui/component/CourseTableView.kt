package com.lingion.sleepy.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.theme.SleepyTextStyle
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
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
    val nodeString: String get() = "第$nodeStart-${nodeEnd}节"  // unused; see nodeString(context) for localized version
    fun nodeString(context: android.content.Context): String =
        context.getString(com.lingion.sleepy.R.string.course_node_format, "$nodeStart-$nodeEnd")
    val timeString: String get() = "$displayStart-$displayEnd"
}

private val CELL_H = 52.dp

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
    timeSlots: List<TimeSlot>,
    visibleDays: Set<Int> = (1..7).toSet(),
    showDate: Boolean = false,
    startDate: String = "",
    currentWeek: Int = 1,
    displayMode: String = "node",
    timeJson: String = "",
    today: Int = DateUtils.todayDayOfWeek(),
    onCourseClick: (CourseEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SleepyTheme.colors
    val maxNode = timeSlots.maxOfOrNull { it.nodeEnd } ?: 12

    // ★ v1.0.16-rebuild-4: Box + offset 绝对定位架构
    // 之前 v1.0.16-rebuild-3 Row.weight(1f) + slotIdx += safeStep 跳过中间 row
    // → Period 4/5/8/9 label 缺失 + 跨节 cell 视觉只占 1 行（被 row 容器裁剪）
    // 现在 Box + offset 绝对定位 — Period 完整 + 跨节真跨 N 行 + 撑满 widget
    val headH = 56.dp
    val timeW = 68.dp
    val gapH = 2.dp
    val gapW = 4.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceContainerHigh, RoundedCornerShape(18.dp))
            .border(0.5.dp, colors.outline.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .padding(8.dp)
    ) {
        val density = LocalDensity.current
        val availableW = maxWidth
        val availableH = maxHeight - headH
        val sortedDays = visibleDays.sorted()
        val totalGapW = gapW * (sortedDays.size + 1)
        val dayW = ((availableW - timeW - totalGapW) / sortedDays.size).coerceAtLeast(40.dp)
        val totalGapH = gapH * (maxNode + 1)
        val slotH = ((availableH - totalGapH) / maxNode).coerceAtLeast(20.dp)
        val rowH = slotH  // 单节 row 高度

        Column(Modifier.fillMaxSize()) {
            // Day header row — fixed height headH
            Row(Modifier.fillMaxWidth().height(headH), horizontalArrangement = Arrangement.spacedBy(gapW)) {
                Box(Modifier.width(timeW))
                for (day in sortedDays) {
                    val dateStr = if (showDate && startDate.isNotBlank()) {
                        try {
                            val d = DateUtils.dateOfWeek(startDate, currentWeek, day)
                            DateUtils.shortDate(d)
                        } catch (_: Exception) { null }
                    } else null
                    DayHeadCell(
                        day = day,
                        isToday = day == today,
                        courseCount = courses.count { it.day == day },
                        dateStr = dateStr,
                        modifier = Modifier.width(dayW)
                    )
                }
            }

            // Body — Box 容器用绝对定位
            Box(Modifier.fillMaxSize().weight(1f)) {
                // Time column — 12 个 Period label 全部显示（不跳过！）
                Column(Modifier.width(timeW).fillMaxHeight()) {
                    for (i in 1..maxNode) {
                        val slot = timeSlots.getOrNull(i - 1)
                        Box(
                            Modifier.fillMaxWidth().height(rowH),
                            contentAlignment = Alignment.Center
                        ) {
                            if (slot != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${i}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    )
                                    Text(
                                        text = slot.displayStart,
                                        style = SleepyTextStyle.micro().copy(fontSize = 7.sp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Day columns — absolute x positioning
                for ((dayIdx, day) in sortedDays.withIndex()) {
                    val dayX = timeW + (dayW + gapW) * dayIdx
                    val dayXpx = with(density) { dayX.roundToPx() }
                    val dayCourses = courses.filter { it.day == day }
                    Box(
                        Modifier
                            .width(dayW)
                            .fillMaxHeight()
                            .offset { IntOffset(x = dayXpx, y = 0) }
                    ) {
                        // Empty placeholder rows — 12 个 Spacer 占位（跨节 cell 覆盖其上）
                        for (i in 1..maxNode) {
                            val yOff = with(density) { ((slotH + gapH) * (i - 1) + gapH).roundToPx() }
                            Spacer(
                                Modifier
                                    .fillMaxWidth()
                                    .height(slotH)
                                    .offset { IntOffset(x = 0, y = yOff) }
                            )
                        }
                        // Course cells — absolute y positioning（跨节 cell 跨 N 行）
                        for (course in dayCourses) {
                            val startIdx = (course.startNode - 1).coerceAtLeast(0)
                            val step = course.step.coerceAtLeast(1).coerceAtMost(maxNode - startIdx)
                            val cellH = slotH * step + gapH * (step - 1)
                            val cellY = with(density) { ((slotH + gapH) * startIdx + gapH).roundToPx() }
                            CourseCardCell(
                                course = course,
                                steps = step,
                                displayMode = displayMode,
                                timeJson = timeJson,
                                onClick = { onCourseClick(course) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(cellH)
                                    .offset { IntOffset(x = 0, y = cellY) }
                            )
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
    displayMode: String = "node",
    timeJson: String = "",
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = SleepyTheme.palette
    val colors = SleepyTheme.colors
    val bg = pickCourseColor(course, palette)
    val fg = colors.onSurface
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
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
                val timeLabel = if (displayMode == "time" && timeJson.isNotBlank()) {
                    TimeTableUtils.courseTimeString(course.startNode, course.step, timeJson, course.ownTime, course.startTime, course.endTime)
                        ?: stringResource(R.string.course_period_range, course.startNode, course.startNode + steps - 1)
                } else {
                    stringResource(R.string.course_period_range, course.startNode, course.startNode + steps - 1)
                }
                Text(
                    text = timeLabel,
                    style = SleepyTextStyle.micro(),
                    color = fg.copy(alpha = 0.65f)
                )
            }
        }
    }
}

@Composable
private fun DayHeadCell(day: Int, isToday: Boolean, courseCount: Int, dateStr: String? = null, dayLabel: String? = null, modifier: Modifier = Modifier) {
    val colors = SleepyTheme.colors
    val context = LocalContext.current
    val resolvedLabel = dayLabel ?: DateUtils.localizedDay(day, context)
    val bg = if (isToday) colors.primaryContainer else colors.surface
    val fg = if (isToday) colors.onPrimaryContainer else colors.onSurface
    val subFg = if (isToday) colors.onPrimaryContainer.copy(alpha = 0.78f) else colors.onSurfaceVariant

    Box(
        modifier = modifier
            .height(if (dateStr != null) 56.dp else 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(0.5.dp, colors.outline.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = resolvedLabel,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = fg,
                maxLines = 1
            )
            if (dateStr != null) {
                Text(
                    text = dateStr,
                    style = SleepyTextStyle.micro().copy(fontSize = 10.sp),
                    color = subFg,
                    maxLines = 1
                )
            } else {
                Text(
                    text = if (courseCount == 0) stringResource(R.string.no_course) else stringResource(R.string.n_courses, courseCount),
                    style = SleepyTextStyle.micro(),
                    color = subFg,
                    maxLines = 1
                )
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
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.course_node_format, slot.label),
                style = SleepyTextStyle.smallMeta().copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = slot.timeString,
                style = SleepyTextStyle.micro(),
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 跨界行的节次栏 — 当某行被 step=N 卡片撑开时，左侧要画 N 个节次标签
 * （比如 "1-2节 08:00-09:35"、"3-4节 10:00-10:45"），不能只画起始节。
 *
 * 复用 TimeHeadCell 的样式，按行均匀分布显示 startIdx..startIdx+span-1 节。
 * 若 span==1 则退化为单节 TimeHeadCell 的等价行为。
 */
@Composable
private fun SpannedTimeHeadCell(
    timeSlots: List<TimeSlot>,
    startIdx: Int,
    span: Int,
    slotH: androidx.compose.ui.unit.Dp,
    gapH: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val colors = SleepyTheme.colors
    val rowH = slotH + gapH
    // Clamp span to what's actually available — courses may reference nodes beyond timeSlots.
    val safeSpan = span.coerceAtMost(timeSlots.size - startIdx).coerceAtLeast(1)
    val shape = RoundedCornerShape(12.dp)
    Column(modifier = modifier) {
        for (i in 0 until safeSpan) {
            val slot = timeSlots[startIdx + i]
            // 每个节次单元格：与 TimeHeadCell 一致（surface 背景 + outline 边框 + 圆角 + 4dp 内边距）
            // 高度：i 是最后一节时 slotH（不留 gap），否则 rowH（slotH + gapH，底部留 gapH 间距给下一个 box）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (i == safeSpan - 1) slotH else rowH)
                    .padding(bottom = if (i == safeSpan - 1) 0.dp else gapH),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .clip(shape)
                        .background(colors.surface)
                        .border(0.5.dp, colors.outline.copy(alpha = 0.10f), shape)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.course_node_format, slot.label),
                            style = SleepyTextStyle.smallMeta().copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurface,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = slot.timeString,
                            style = SleepyTextStyle.micro(),
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * 课程名 → 调色板查表（规则顺序敏感：先精确名 → 后哈希）。
 *
 * 改用查表替代 if-when 链：把"name.contains(关键词)"和"hash.mod(N) → color"两类规则
 * 合并为统一的 `List<Pair<Predicate, PaletteSelector>>`，新规则加一行即可。
 *
 * `and 0x7FFFFFFF` 把 Kotlin hashCode 的负数转成正数，避免 `(-1).mod(7) == -1` 导致永远走 else。
 */
private val courseColorRules: List<Pair<(String) -> Boolean, com.lingion.sleepy.ui.theme.CoursePalette.() -> Color>> =
    listOf(
        { s: String -> s.contains("英语") } to { english },
        { s: String -> s.contains("军事") || s.contains("国防") } to { military },
        { s: String -> s.contains("物理") } to { physics },
        { s: String -> s.contains("历史") || s.contains("史纲") || s.contains("近代史") } to { history },
        { s: String -> s.contains("心理") } to { psychology },
        { s: String -> s.contains("实践") || s.contains("实习") || s.contains("实验") } to { practice },
        { s: String -> s.contains("高数") || s.contains("数学") || s.contains("电路") } to { primary },
        { s: String -> s.contains("思政") || s.contains("马原") || s.contains("毛概") || s.contains("形势") } to { tertiary }
    )

private val courseColorHashPalette: List<com.lingion.sleepy.ui.theme.CoursePalette.() -> Color> = listOf(
    { primary }, { secondary }, { tertiary },
    { english }, { physics }, { psychology }
)

private fun pickCourseColor(course: CourseEntity, palette: com.lingion.sleepy.ui.theme.CoursePalette): Color {
    val name = course.courseName
    courseColorRules.firstOrNull { (match, _) -> match(name) }?.let { (_, selector) ->
        return palette.selector()
    }
    val hash = name.hashCode() and 0x7FFFFFFF
    return courseColorHashPalette[hash % courseColorHashPalette.size].invoke(palette)
}

// =====================================================================================
// 7days full 视图 — switchable.html #fullView
// =====================================================================================

@Composable
fun FullWeekView(
    courses: List<CourseEntity>,
    visibleDays: Set<Int> = (1..7).toSet(),
    displayMode: String = "node",
    timeJson: String = "",
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
            visibleDays = visibleDays,
            today = today,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        DetailPanel(
            byDay = byDay,
            visibleDays = visibleDays,
            displayMode = displayMode,
            timeJson = timeJson,
            today = today,
            onCourseClick = onCourseClick
        )
    }
}

@Composable
private fun WeekStrip(
    byDay: Map<Int, List<CourseEntity>>,
    today: Int,
    visibleDays: Set<Int>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (day in visibleDays.sorted()) {
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
            text = DateUtils.localizedDay(day, LocalContext.current),
            style = SleepyTextStyle.dayLabel(),
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
                    text = stringResource(R.string.n_courses, courses.size),
                    style = SleepyTextStyle.smallMeta().copy(fontWeight = FontWeight.SemiBold),
                    color = chipFg
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Mini-list: 前 5 门课名（改掉 take(3)，空间够就全显示）
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            courses.take(5).forEach { c ->
                Text(
                    text = c.courseName,
                    style = SleepyTextStyle.micro(),
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
    visibleDays: Set<Int>,
    displayMode: String,
    timeJson: String,
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
        for (day in visibleDays.sorted()) {
            val dayCourses = byDay[day].orEmpty().sortedBy { it.startNode }
            DetailDayCard(
                day = day,
                courses = dayCourses,
                isToday = day == today,
                displayMode = displayMode,
                timeJson = timeJson,
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
    displayMode: String = "node",
    timeJson: String = "",
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
                text = DateUtils.localizedDay(day, LocalContext.current) + if (isToday) " · " + stringResource(R.string.today_today) else "",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface
            )
        }

        if (courses.isEmpty()) {
            Text(
                text = "${DateUtils.localizedDay(day, LocalContext.current)} · " + stringResource(R.string.no_course),
                style = SleepyTextStyle.smallMeta().copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = colors.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                courses.forEach { c ->
                    LessonRow(course = c, displayMode = displayMode, timeJson = timeJson, onClick = { onCourseClick(c) })
                }
            }
        }
    }
}

@Composable
private fun LessonRow(course: CourseEntity, displayMode: String, timeJson: String, onClick: () -> Unit) {
    val colors = SleepyTheme.colors
    val palette = SleepyTheme.palette
    val context = LocalContext.current
    val bg = pickCourseColor(course, palette)

    val timeLabel = if (displayMode == "time" && timeJson.isNotBlank()) {
        TimeTableUtils.courseTimeString(course.startNode, course.step, timeJson, course.ownTime, course.startTime, course.endTime)
            ?: course.shortNodeString(context)
    } else {
        course.shortNodeString(context)
    }

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
            text = timeLabel,
            style = SleepyTextStyle.smallMeta().copy(fontWeight = FontWeight.SemiBold),
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
                    style = SleepyTextStyle.smallMeta(),
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
            style = SleepyTextStyle.sectionHead(),
            color = colors.onSurface
        )
        if (action != null) {
            Text(
                text = action,
                style = SleepyTextStyle.smallMeta().copy(
                    fontWeight = FontWeight.Medium,
                    color = colors.primary
                )
            )
        }
    }
}


// sp 已被上面 textStyle 直接用 inline
