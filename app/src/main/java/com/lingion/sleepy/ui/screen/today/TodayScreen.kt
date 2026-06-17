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
    val todayCourses = state.courses.filter {
        it.day == dayOfWeek && it.inWeek(state.currentWeek)
    }.sortedBy { it.startNode }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SleepyTheme.colors.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { TodayHeader(date = today, week = state.currentWeek, count = todayCourses.size) }

        if (todayCourses.isEmpty()) {
            item { EmptyToday() }
        } else {
            item {
                SectionHead(title = "今日课程", action = "${todayCourses.size} 节")
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainer)
            .padding(16.dp)
    ) {
        Text(
            text = "今天",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${date.monthValue}月${date.dayOfMonth}日",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onSurface
            )
            Text(
                text = DateUtils.chineseDay(date.dayOfWeek.value),
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
            Stat(label = "第 $week 周", bg = colors.primaryContainer, fg = colors.onPrimaryContainer)
            Stat(
                label = if (count == 0) "无课程" else "$count 节课程",
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
            text = "今日无课程",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun TodayCourseCard(course: CourseEntity, timeJson: String? = null) {
    val colors = SleepyTheme.colors
    val palette = SleepyTheme.palette
    val bg = pickCourseColor(course, palette)
    val time = timeJson?.let { TimeTableUtils.courseTimeString(course.startNode, course.step, it) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 时间槽 — 固定宽度避免 “10:20-12:45” 被截断
        Column(
            modifier = Modifier.width(76.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = course.shortNodeString,
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
        else -> palette.surface
    }
}