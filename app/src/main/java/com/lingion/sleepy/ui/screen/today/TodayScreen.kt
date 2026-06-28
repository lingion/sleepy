package com.lingion.sleepy.ui.screen.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.component.SectionHead
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
import java.time.LocalDate

@Composable
fun TodayScreen(
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val today = LocalDate.now()
    val dayOfWeek = DateUtils.todayDayOfWeek(today)
    val actualWeek = state.currentTable?.let { DateUtils.currentWeek(it.startDate, today) } ?: state.currentWeek
    val todayCourses = state.courses.filter {
        it.day == dayOfWeek && it.inWeek(actualWeek)
    }.sortedBy { it.startNode }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SleepyTheme.colors.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { TodayHeader(date = today, week = actualWeek, count = todayCourses.size) }

        if (todayCourses.isEmpty()) {
            item { EmptyToday() }
        } else {
            item {
                SectionHead(title = stringResource(R.string.widget_today_label), action = stringResource(R.string.n_periods, todayCourses.size))
            }
            items(todayCourses, key = { it.id }) { course ->
                TodayCourseCard(course = course, timeJson = state.currentTable?.timeJson)
            }
        }
    }
}

@Composable
private fun TodayHeader(date: LocalDate, week: Int, count: Int) {
    val colors = SleepyTheme.colors
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainer)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.today_today),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.date_long_format, date.monthValue, date.dayOfMonth),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onSurface
            )
            Text(
                text = DateUtils.localizedDay(date.dayOfWeek.value, context),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Stat(label = stringResource(R.string.schedule_current_week, week), bg = colors.primaryContainer, fg = colors.onPrimaryContainer)
            Stat(
                label = if (count == 0) stringResource(R.string.no_course) else stringResource(R.string.n_course_periods, count),
                bg = colors.tertiaryContainer,
                fg = colors.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun Stat(label: String, bg: Color, fg: Color) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun EmptyToday() {
    val colors = SleepyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainer)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = stringResource(R.string.schedule_no_course_today),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface
        )
        Text(
            text = stringResource(R.string.today_no_course),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun TodayCourseCard(course: CourseEntity, timeJson: String? = null) {
    val colors = SleepyTheme.colors
    val palette = SleepyTheme.palette
    val context = LocalContext.current
    val bg = pickCourseColor(course, palette)
    val time = if (course.ownTime && course.startTime.isNotBlank() && course.endTime.isNotBlank()) {
        "${course.startTime}-${course.endTime}"
    } else {
        timeJson?.let { TimeTableUtils.courseTimeString(course.startNode, course.step, it) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 时间槽 — 固定宽度避免 "10:20-12:45" 被截断
        Column(
            modifier = Modifier.width(76.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = course.shortNodeString(context),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )
            if (time != null) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurface.copy(alpha = 0.72f),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = course.courseName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
                maxLines = 2
            )
            if (course.teacher.isNotBlank() || course.room.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                val meta = buildString {
                    if (course.teacher.isNotBlank()) append(course.teacher)
                    if (course.room.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(course.room)
                    }
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurface.copy(alpha = 0.72f)
                )
            }
        }
    }
}

private fun findCourseTime(course: CourseEntity): String? {
    // 已被 TimeTableUtils.courseTimeString 取代，保留为空函数避免其他地方误调
    return null
}

private fun pickCourseColor(course: CourseEntity, palette: com.lingion.sleepy.ui.theme.CoursePalette): Color {
    val userColor = course.color
    if (userColor.isNotBlank() && !userColor.equals("#FF6750A4", ignoreCase = true)) {
        runCatching { return Color(android.graphics.Color.parseColor(userColor)) }
    }
    val isDark = isPaletteDark(palette)
    val id = (course.id % 360).toInt()
    val hue = ((id * 137.508f) % 360f + 360f) % 360f
    val s = if (isDark) 0.40f else 0.55f
    val l = if (isDark) 0.28f else 0.82f
    return hslToColor(hue, s, l)
}

private fun isPaletteDark(p: com.lingion.sleepy.ui.theme.CoursePalette): Boolean {
    val c = p.primary
    val lum = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
    return lum < 0.5f
}

private fun hslToColor(h: Float, s: Float, l: Float): Color {
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        h < 60f   -> Triple(c, x, 0f)
        h < 120f  -> Triple(x, c, 0f)
        h < 180f  -> Triple(0f, c, x)
        h < 240f  -> Triple(0f, x, c)
        h < 300f  -> Triple(x, 0f, c)
        else      -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}