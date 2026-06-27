package com.lingion.sleepy.ui.screen.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.lingion.sleepy.util.DateUtils
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

    val hasTable = state.tables.isNotEmpty()
    val hasCourses = state.courses.isNotEmpty()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        if (!hasTable) {
            // 真的没表：去创建
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
        } else if (!hasCourses) {
            // 有表无课：直接打开加课弹窗（addEmptyCourse 内部若 selectedTableId 为空会自动建表）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                NoCourseState(
                    tableName = state.currentTable?.name ?: "",
                    onAddCourse = onManualAdd,
                    onImport = onGoImport
                )
            }
        } else {
            TopBar(
                currentWeek = state.currentWeek,
                maxWeek = state.currentTable?.maxWeek ?: 20,
                startDate = state.currentTable?.startDate ?: "",
                onPrevWeek = { viewModel.changeWeek(state.currentWeek - 1) },
                onNextWeek = { viewModel.changeWeek(state.currentWeek + 1) },
                onJumpToActual = {
                    val start = state.currentTable?.startDate ?: return@TopBar
                    viewModel.changeWeek(DateUtils.currentWeek(start))
                },
                onSelectWeek = { week -> viewModel.changeWeek(week) }
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

            // 主体视图 — 左右滑动切换周次
            val pagerMaxWeek = state.currentTable?.maxWeek ?: 20
            val pagerState = rememberPagerState(
                initialPage = (state.currentWeek - 1).coerceIn(0, (pagerMaxWeek - 1).coerceAtLeast(0)),
                pageCount = { pagerMaxWeek.coerceAtLeast(1) }
            )
            val coroutineScope = rememberCoroutineScope()

            // Pager 滑动 → 更新 ViewModel（驱动 TopBar 箭头同步）
            LaunchedEffect(pagerState.currentPage) {
                viewModel.changeWeek(pagerState.currentPage + 1)
            }

            // ViewModel 变化 → 同步 Pager（TopBar 箭头/下拉菜单点击时）
            LaunchedEffect(state.currentWeek) {
                val targetPage = (state.currentWeek - 1).coerceIn(0, pagerMaxWeek - 1)
                if (pagerState.currentPage != targetPage) {
                    pagerState.animateScrollToPage(targetPage)
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                // page 是 0-based 周索引，独立于 state.currentWeek 过滤课程
                val weekCourses = state.courses.filter { it.inWeek(page + 1) }
                    .let { list ->
                        val tj = state.currentTable?.timeJson
                        if (tj == null) list else list.map { c -> c.normalizeNode(tj) }
                    }
                when (viewMode) {
                    ViewMode.Full -> FullWeekView(
                        courses = weekCourses,
                        visibleDays = visibleDays,
                        displayMode = displayMode,
                        timeJson = state.currentTable?.timeJson ?: "",
                        onCourseClick = { selectedCourse = it }
                    )
                    ViewMode.Cards -> CardsGridView(
                        courses = weekCourses,
                        timeSlots = TimeTableUtils.timeSlotsFor(state.currentTable),
                        visibleDays = visibleDays,
                        showDate = showDate,
                        startDate = state.currentTable?.startDate ?: "",
                        currentWeek = page + 1,
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
    maxWeek: Int,
    startDate: String,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onJumpToActual: () -> Unit,
    onSelectWeek: (Int) -> Unit
) {
    val colors = SleepyTheme.colors
    // 实时计算当前实际周（不依赖 state.currentWeek — 用户可能切到了别的周）
    val actualWeek = remember(startDate) {
        if (startDate.isBlank()) 1 else DateUtils.currentWeek(startDate)
    }
    var menuOpen by remember { mutableStateOf(false) }
    val isOnActual = currentWeek == actualWeek

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

        // 第 N 周 标签 — 点击行为根据是否在当前实际周而不同
        Box {
            Text(
                text = stringResource(R.string.schedule_current_week, currentWeek),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (isOnActual) colors.onPrimaryContainer else colors.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isOnActual) colors.primaryContainer else colors.primaryContainer.copy(alpha = 0.6f))
                    .clickable {
                        if (isOnActual) {
                            // 在当前实际周 → 弹下拉菜单
                            menuOpen = true
                        } else {
                            // 不在当前实际周 → 一键跳回
                            onJumpToActual()
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            )

            // Material3 DropdownMenu — FlowRow 标签式选周
            @OptIn(ExperimentalLayoutApi::class)
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.width(280.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "跳转到周次",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        (1..maxWeek).forEach { w ->
                            val isCurrent = w == currentWeek
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isCurrent) colors.primary
                                        else colors.surfaceContainerHigh
                                    )
                                    .clickable {
                                        onSelectWeek(w)
                                        menuOpen = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = w.toString(),
                                    fontSize = 14.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) colors.onPrimary else colors.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

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
private fun NoCourseState(
    tableName: String,
    onAddCourse: () -> Unit,
    onImport: () -> Unit
) {
    val colors = SleepyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainer)
            .padding(horizontal = 22.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "「$tableName」还是空的",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface
        )
        Text(
            text = "往这张课表里加课，或导入新的课表",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
        Button(
            onClick = onAddCourse,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
        ) {
            Text(stringResource(R.string.schedule_manual_first), color = colors.onPrimary)
        }
        Button(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.secondaryContainer)
        ) {
            Text(stringResource(R.string.schedule_go_manage), color = colors.onSecondaryContainer)
        }
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

