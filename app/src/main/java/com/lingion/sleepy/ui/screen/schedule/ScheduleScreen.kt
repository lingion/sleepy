package com.lingion.sleepy.ui.screen.schedule

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.IosShare
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalDate
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
import com.lingion.sleepy.ui.component.ShareScheduleSheet
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.HolidayManager
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
    val context = androidx.compose.ui.platform.LocalContext.current
    var viewMode by remember { mutableStateOf(if (AppPrefs.getStartView(context) == "cards") ViewMode.Cards else ViewMode.Full) }
    var selectedCourse by remember { mutableStateOf<CourseEntity?>(null) }
    // v7.10.5 会话级置顶 override — 网格 onPickTop 与详情弹窗 radio 共用真相源。
    // radio 点击 → 这里瞬时换层(同帧) + AppPrefs 持久化(跨会话),两条通道一次写齐。
    var topOverrides by remember { mutableStateOf(mapOf<String, Long>()) }
    fun setTopOverride(key: String, courseId: Long?) {
        topOverrides = if (courseId == null) topOverrides - key else topOverrides + (key to courseId)
    }
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
            // v7.10.7 顶栏分享 → 底部弹窗(格式选择)
            var showShareSheet by remember { mutableStateOf(false) }
            TopBar(
                currentWeek = state.selectedWeek,
                maxWeek = state.currentTable?.maxWeek ?: 20,
                startDate = state.currentTable?.startDate ?: "",
                onPrevWeek = { viewModel.changeWeek(state.selectedWeek - 1) },
                onNextWeek = { viewModel.changeWeek(state.selectedWeek + 1) },
                onJumpToActual = {
                    val start = state.currentTable?.startDate ?: return@TopBar
                    viewModel.changeWeek(DateUtils.currentWeek(start))
                },
                onSelectWeek = { week -> viewModel.changeWeek(week) },
                onAddCourse = onManualAdd,
                onShare = { showShareSheet = true }
            )

            if (showShareSheet) {
                state.currentTable?.let { table ->
                    ShareScheduleSheet(
                        table = table,
                        courses = state.courses,
                        onDismiss = { showShareSheet = false }
                    )
                }
            }

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
                initialPage = (state.selectedWeek - 1).coerceIn(0, (pagerMaxWeek - 1).coerceAtLeast(0)),
                pageCount = { pagerMaxWeek.coerceAtLeast(1) }
            )

            // 标记：是否正在由 ViewModel 驱动 Pager 滚动（防止双向同步打架）
            var syncingFromState by remember { mutableStateOf(false) }

            // Pager 滑动（用户手势）→ 更新 ViewModel
            LaunchedEffect(pagerState.currentPage) {
                if (!syncingFromState) {
                    viewModel.changeWeek(pagerState.currentPage + 1)
                }
            }

            // ViewModel 变化（TopBar 箭头/下拉菜单点击）→ 同步 Pager
            LaunchedEffect(state.selectedWeek) {
                val targetPage = (state.selectedWeek - 1).coerceIn(0, pagerMaxWeek - 1)
                if (pagerState.currentPage != targetPage) {
                    pagerState.scrollToPage(targetPage)
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
                // 计算本周哪些天是节假日/周末(灰显用)
                val greyDays by produceState<Set<Int>>(emptySet(), page, state.currentTable?.startDate) {
                    val start = state.currentTable?.startDate
                    if (start.isNullOrBlank()) {
                        value = emptySet()
                    } else {
                        val greySet = mutableSetOf<Int>()
                        for (day in 1..7) {
                            val date = DateUtils.dateOfWeek(start, page + 1, day)
                            if (HolidayManager.shouldGrey(context, date)) greySet.add(day)
                        }
                        value = greySet
                    }
                }
                when (viewMode) {
                    ViewMode.Full -> FullWeekView(
                        courses = weekCourses,
                        visibleDays = visibleDays,
                        displayMode = displayMode,
                        timeJson = state.currentTable?.timeJson ?: "",
                        onCourseClick = { selectedCourse = it },
                        greyDays = greyDays
                    )
                    ViewMode.Cards -> CardsGridView(
                        courses = weekCourses,
                        timeSlots = TimeTableUtils.timeSlotsFor(state.currentTable),
                        visibleDays = visibleDays,
                        showDate = showDate,
                        startDate = state.currentTable?.startDate ?: "",
                        currentWeek = page + 1,
                        onCourseClick = { selectedCourse = it },
                        greyDays = greyDays,
                        topOverrides = topOverrides,
                        onSetTopOverride = ::setTopOverride
                    )
                }
            }
        }

        // 详情 Bottom Sheet
        CourseDetailSheet(
            course = selectedCourse,
            timeString = selectedCourse?.let { it.nodeString(LocalContext.current) },
            allCourses = state.courses,
            onDismiss = { selectedCourse = null },
            onEdit = { course ->
                selectedCourse = null
                onEditCourse(course)
            },
            onDefaultTopChanged = { clusterKey, repId ->
                // 勾选瞬间: 会话级换层(网格同帧刷新) + 持久化(跨会话默认)
                setTopOverride(clusterKey, repId)
                AppPrefs.putConflictDefaultTop(context, clusterKey, repId)
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
    onSelectWeek: (Int) -> Unit,
    onAddCourse: () -> Unit,
    onShare: () -> Unit
) {
    val colors = SleepyTheme.colors
    // 实时计算当前实际周（不依赖 state.currentWeek — 用户可能切到了别的周）
    val actualWeek = remember(startDate) {
        if (startDate.isBlank()) 1 else DateUtils.currentWeek(startDate)
    }
    var menuOpen by remember { mutableStateOf(false) }
    val isOnActual = currentWeek == actualWeek
    val semesterStatus = DateUtils.semesterStatus(startDate, maxWeek)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // v7.10.12: 三件套改 Box 叠加实现屏幕正中 —
        // 旧 weight(1f)+Center 是在"扣除右侧按钮后的剩余空间"里居中, 视觉偏左;
        // Box 叠加让三件套对齐全宽正中, 加课/分享绝对定位右缘(用户 2026-09-02)。
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            // 翻页三件套(箭头+胶囊+箭头) — 箭头紧贴胶囊
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
            WeekNavButton(icon = Icons.Outlined.ChevronLeft, onClick = onPrevWeek)

            Spacer(modifier = Modifier.width(8.dp))

            // 第 N 周 标签 — 点击行为根据是否在当前实际周而不同
            // 学期外: 标签带上周数(学期未开始 · 第 3 周), 翻周时数字跟着变, 用户才知道自己看到第几周
            Box {
                val statusRes = when (semesterStatus) {
                    DateUtils.SemesterStatus.BEFORE_START -> R.string.semester_not_started
                    DateUtils.SemesterStatus.AFTER_END -> R.string.semester_ended
                    else -> 0
                }
                Text(
                    text = if (statusRes == 0)
                        stringResource(R.string.schedule_current_week, currentWeek)
                    else "${stringResource(statusRes)} · ${stringResource(R.string.schedule_week_prefix, currentWeek)}",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isOnActual) colors.onPrimaryContainer else colors.primary,
                    modifier = Modifier
                        .clip(SleepyTheme.shapes.medium)
                        .background(if (isOnActual) colors.primaryContainer else colors.primaryContainer.copy(alpha = SleepyTheme.Alpha.inactive))
                        .noRippleClickable {
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
                    modifier = Modifier.width(280.dp),
                    // 菜单浮在 surfaceContainer 背景上, 用 Highest 拉开对比(默认 High 与背景几乎同色=隐形)
                    containerColor = colors.surfaceContainerHighest
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.schedule_jump_week),
                            style = MaterialTheme.typography.labelMedium,
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
                                        .noRippleClickable {
                                            onSelectWeek(w)
                                            menuOpen = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = w.toString(),
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isCurrent) colors.onPrimary else colors.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            WeekNavButton(icon = Icons.Outlined.ChevronRight, onClick = onNextWeek)
        }

            // 右侧操作区: 加课 + 分享 — 与翻页箭头同款圆形底, Box 右缘绝对定位
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WeekNavButton(
                    icon = Icons.Outlined.Add,
                    contentDescriptionRes = R.string.schedule_add_course,
                    onClick = onAddCourse
                )
                Spacer(modifier = Modifier.width(6.dp))
                WeekNavButton(
                    icon = Icons.Outlined.IosShare,
                    contentDescriptionRes = R.string.schedule_share_table,
                    onClick = onShare
                )
            }
        }
    }
}

@Composable
private fun WeekNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    contentDescriptionRes: Int? = null
) {
    val colors = SleepyTheme.colors
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            .noRippleClickable(onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescriptionRes?.let { stringResource(it) },
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
            .clip(SleepyTheme.shapes.extraLarge)
            .background(colors.surfaceContainer)
            .padding(horizontal = 22.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.schedule_empty_name, tableName),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface
        )
        Text(
            text = stringResource(R.string.schedule_empty_name_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
        Button(
            onClick = onAddCourse,
            modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.ctaHeight),
            shape = SleepyTheme.Buttons.shape,
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
        ) {
            Text(stringResource(R.string.schedule_manual_first), color = colors.onPrimary)
        }
        Button(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.ctaHeight),
            shape = SleepyTheme.Buttons.shape,
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
            .clip(SleepyTheme.shapes.extraLarge)
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
            modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.ctaHeight),
            shape = SleepyTheme.Buttons.shape,
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
        ) {
            Text(stringResource(R.string.schedule_go_manage), color = colors.onPrimary)
        }
        Button(
            onClick = onManualAdd,
            modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.ctaHeight),
            shape = SleepyTheme.Buttons.shape,
            colors = ButtonDefaults.buttonColors(containerColor = colors.secondaryContainer)
        ) {
            Text(stringResource(R.string.schedule_manual_first), color = colors.onSecondaryContainer)
        }
    }
}
