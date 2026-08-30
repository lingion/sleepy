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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.lingion.sleepy.ui.component.CourseDetailSheet
import com.lingion.sleepy.ui.component.SectionHead
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.CourseColorUtil
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
import java.time.LocalDate

@Composable
fun TodayScreen(
    onEditCourse: (CourseEntity) -> Unit = {},
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val today = LocalDate.now()
    val dayOfWeek = DateUtils.todayDayOfWeek(today)
    val actualWeek = state.currentTable?.let { DateUtils.currentWeek(it.startDate, today) } ?: state.currentWeek
    // 学期外感知: BEFORE_START/AFTER_END 时今日课不按周过滤展示
    val semesterStatus = state.currentTable?.let {
        DateUtils.semesterStatus(it.startDate, it.maxWeek, today)
    } ?: DateUtils.SemesterStatus.IN_RANGE
    val isOutOfSemester = semesterStatus != DateUtils.SemesterStatus.IN_RANGE
    val todayCourses = if (isOutOfSemester) emptyList() else state.courses.filter {
        it.day == dayOfWeek && it.inWeek(actualWeek)
    }.sortedBy { it.startNode }

    var selectedCourse by remember { mutableStateOf<CourseEntity?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SleepyTheme.colors.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { TodayHeader(date = today, week = actualWeek, count = todayCourses.size, semesterStatus = semesterStatus) }

        if (todayCourses.isEmpty()) {
            item { EmptyToday(semesterStatus = semesterStatus) }
        } else {
            item {
                SectionHead(title = stringResource(R.string.widget_today_label), action = stringResource(R.string.n_periods, todayCourses.size))
            }
            items(todayCourses, key = { it.id }) { course ->
                TodayCourseCard(
                    course = course,
                    timeJson = state.currentTable?.timeJson,
                    onClick = { selectedCourse = course }
                )
            }
        }
    }

    // 详情 Bottom Sheet — 与课表页同一组件同一交互
    CourseDetailSheet(
        course = selectedCourse,
        timeString = selectedCourse?.let { it.nodeString(LocalContext.current) },
        onDismiss = { selectedCourse = null },
        onEdit = { course ->
            selectedCourse = null
            onEditCourse(course)
        }
    )
}

@Composable
private fun TodayHeader(date: LocalDate, week: Int, count: Int, semesterStatus: DateUtils.SemesterStatus = DateUtils.SemesterStatus.IN_RANGE) {
    val colors = SleepyTheme.colors
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SleepyTheme.shapes.large)
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
            // 学期外: 周次 chip 换学期状态, 不再显示误导性的"第 1 周"
            when (semesterStatus) {
                DateUtils.SemesterStatus.BEFORE_START ->
                    Stat(label = stringResource(R.string.semester_not_started), bg = colors.secondaryContainer, fg = colors.onSecondaryContainer)
                DateUtils.SemesterStatus.AFTER_END ->
                    Stat(label = stringResource(R.string.semester_ended), bg = colors.secondaryContainer, fg = colors.onSecondaryContainer)
                else ->
                    Stat(label = stringResource(R.string.schedule_current_week, week), bg = colors.primaryContainer, fg = colors.onPrimaryContainer)
            }
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
            .clip(SleepyTheme.shapes.medium)
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun EmptyToday(semesterStatus: DateUtils.SemesterStatus = DateUtils.SemesterStatus.IN_RANGE) {
    val colors = SleepyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SleepyTheme.shapes.large)
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
        when (semesterStatus) {
            DateUtils.SemesterStatus.BEFORE_START -> {
                Text(
                    text = stringResource(R.string.semester_not_started),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface
                )
                Text(
                    text = stringResource(R.string.today_semester_out_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }
            DateUtils.SemesterStatus.AFTER_END -> {
                Text(
                    text = stringResource(R.string.semester_ended),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface
                )
                Text(
                    text = stringResource(R.string.today_semester_out_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }
            else -> {
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
    }
}

@Composable
private fun TodayCourseCard(course: CourseEntity, timeJson: String? = null, onClick: (() -> Unit)? = null) {
    val colors = SleepyTheme.colors
    val palette = SleepyTheme.palette
    val context = LocalContext.current
    // 统一取色入口 — hue 源自动对齐 groupId（修复原 course.id%360 导致同门课多节次异色+三屏三色）
    // colorless 读取 AppPrefs course_colorless 独立开关
    val bg = CourseColorUtil.pickCourseColorCompose(
        course = course,
        isDark = CourseColorUtil.isPaletteDark(palette),
        neutralColor = colors.surfaceVariant,
        colorless = AppPrefs.isCourseColorless(context)
    )
    // 文字色亮度自适应（决策 D5-13）— 深色自定义课色上切白字，浅色底仍 onSurface
    val fg = CourseColorUtil.textColorOn(bg, CourseColorUtil.isPaletteDark(palette), colors.onSurface)
    val time = if (course.ownTime && course.startTime.isNotBlank() && course.endTime.isNotBlank()) {
        "${course.startTime}-${course.endTime}"
    } else {
        timeJson?.let { TimeTableUtils.courseTimeString(course.startNode, course.step, it) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SleepyTheme.shapes.large)
            .background(bg)
            .then(if (onClick != null) Modifier.noRippleClickable(onClick = onClick) else Modifier)
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
                color = fg
            )
            if (time != null) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = fg.copy(alpha = SleepyTheme.Alpha.highContent),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = course.courseName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = fg,
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
                    color = fg.copy(alpha = SleepyTheme.Alpha.highContent)
                )
            }
        }
    }
}

// findCourseTime 空函数已删（死代码清理: 注释自述被 TimeTableUtils.courseTimeString 取代, 全库零调用）。
// pickCourseColor / isPaletteDark / hslToColor 三函数已收敛至 util/CourseColorUtil.kt（决策 D3 单一事实来源）