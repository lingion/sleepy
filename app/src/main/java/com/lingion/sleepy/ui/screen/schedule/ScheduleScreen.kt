package com.lingion.sleepy.ui.screen.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.TimeTableUtils

private enum class ViewMode(val labelRes: Int) {
    Full(R.string.view_full),
    Cards(R.string.view_cards)
}

@Composable
fun ScheduleScreen(
    onGoImport: () -> Unit = {},
    onManualAdd: () -> Unit = {},
    onEditCourse: (CourseEntity) -> Unit = {},
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var viewMode by remember { mutableStateOf(ViewMode.Full) }
    var selectedCourse by remember { mutableStateOf<CourseEntity?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val displayMode = remember { AppPrefs.getDisplayMode(context) }
    val showDate = remember { AppPrefs.isShowDate(context) }
    val visibleDays = remember { AppPrefs.getVisibleDays(context) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        if (state.courses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                EmptyState(
                    modifier = Modifier.align(Alignment.TopCenter),
                    onGoImport = onGoImport,
                    onManualAdd = onManualAdd
                )
            }
        } else {
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
                        visibleDays = visibleDays,
                        displayMode = displayMode,
                        timeJson = state.currentTable?.timeJson ?: "",
                        onCourseClick = { selectedCourse = it }
                    )
                    ViewMode.Cards -> CardsGridView(
                        courses = state.currentWeekCourses,
                        timeSlots = TimeTableUtils.timeSlotsFor(state.currentTable),
                        visibleDays = visibleDays,
                        showDate = showDate,
                        startDate = state.currentTable?.startDate ?: "",
                        currentWeek = state.currentWeek,
                        displayMode = displayMode,
                        timeJson = state.currentTable?.timeJson ?: "",
                        onCourseClick = { selectedCourse = it }
                    )
                }
            }
        }

        // 详情 Bottom Sheet
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
}

@Composable
private fun TopBar(
    currentWeek: Int,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit
) {
    val colors = SleepyTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .border(0.5.dp, colors.outline.copy(alpha = 0.10f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        WeekNavButton(icon = Icons.Outlined.ChevronLeft, onClick = onPrevWeek)
        Text(
            text = stringResource(R.string.schedule_current_week, currentWeek),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(colors.primaryContainer)
                .padding(horizontal = 14.dp, vertical = 4.dp)
        )
        WeekNavButton(icon = Icons.Outlined.ChevronRight, onClick = onNextWeek)
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
            .size(32.dp)
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            .padding(6.dp)
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
private fun EmptyState(
    modifier: Modifier = Modifier,
    onGoImport: () -> Unit = {},
    onManualAdd: () -> Unit = {}
) {
    val colors = SleepyTheme.colors
    Column(
        modifier = modifier
            .padding(horizontal = 22.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainer)
            .padding(horizontal = 22.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.schedule_empty),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface
        )
        Text(
            text = stringResource(R.string.schedule_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
        Button(
            onClick = onGoImport,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
        ) {
            Text(stringResource(R.string.schedule_go_manage), color = colors.onPrimary)
        }
        Button(
            onClick = onManualAdd,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.secondaryContainer)
        ) {
            Text(stringResource(R.string.schedule_manual_first), color = colors.onSecondaryContainer)
        }
    }
}

