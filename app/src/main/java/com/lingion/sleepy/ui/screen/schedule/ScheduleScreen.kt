package com.lingion.sleepy.ui.screen.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.component.CardsGridView
import com.lingion.sleepy.ui.component.CourseDetailSheet
import com.lingion.sleepy.ui.component.FullWeekView
import com.lingion.sleepy.ui.component.SectionHead
import com.lingion.sleepy.ui.component.SegmentedSwitcher
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.TimeTableUtils

private enum class ViewMode(val labelRes: Int) {
    Full(R.string.view_full),
    Cards(R.string.view_cards)
}

@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var viewMode by remember { mutableStateOf(ViewMode.Full) }
    var selectedCourse by remember { mutableStateOf<CourseEntity?>(null) }

    Scaffold(
        containerColor = SleepyTheme.colors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TopBar(
                currentWeek = state.currentWeek,
                onPrevWeek = { viewModel.changeWeek(state.currentWeek - 1) },
                onNextWeek = { viewModel.changeWeek(state.currentWeek + 1) }
            )

            // Segmented Switcher
            SegmentedSwitcher(
                options = ViewMode.entries.map { it to stringResource(it.labelRes) },
                selected = viewMode,
                onSelect = { viewMode = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 主体视图
            Box(modifier = Modifier.fillMaxSize()) {
                when (viewMode) {
                    ViewMode.Full -> FullWeekView(
                        courses = state.currentWeekCourses,
                        onCourseClick = { selectedCourse = it }
                    )
                    ViewMode.Cards -> CardsGridView(
                        courses = state.currentWeekCourses,
                        timeSlots = TimeTableUtils.timeSlotsFor(state.currentTable),
                        onCourseClick = { selectedCourse = it }
                    )
                }

                if (state.courses.isEmpty()) {
                    EmptyState(modifier = Modifier.align(Alignment.Center))
                }
            }
        }

        // 详情 Bottom Sheet
        CourseDetailSheet(
            course = selectedCourse,
            timeString = selectedCourse?.let { it.nodeString },
            onDismiss = { selectedCourse = null }
        )
    }
}

@Composable
private fun TopBar(
    currentWeek: Int,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit
) {
    val colors = SleepyTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .border(0.5.dp, colors.outline.copy(alpha = 0.10f))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                color = colors.onSurface
            )
            Text(
                text = "第 $currentWeek 周",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeekNavButton(icon = Icons.Outlined.ChevronLeft, onClick = onPrevWeek)
            Text(
                text = "切换周次",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant
            )
            WeekNavButton(icon = Icons.Outlined.ChevronRight, onClick = onNextWeek)
        }
    }
}

@Composable
private fun WeekNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val colors = SleepyTheme.colors
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            .padding(8.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "还没有课表",
            style = MaterialTheme.typography.titleMedium,
            color = SleepyTheme.colors.onSurface
        )
        Text(
            text = stringResource(R.string.schedule_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = SleepyTheme.colors.onSurfaceVariant
        )
    }
}

