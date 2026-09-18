package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.component.DatePickerField
import com.lingion.sleepy.ui.component.SectionHeader
import com.lingion.sleepy.ui.component.SegmentedSwitcher
import com.lingion.sleepy.ui.component.SettingToggleRow
import com.lingion.sleepy.ui.component.SettingsFlatCard
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.HolidayEntry
import com.lingion.sleepy.util.HolidayManager
import com.lingion.sleepy.util.HolidayRange
import com.lingion.sleepy.util.HolidayRangeOps
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate

private sealed interface HolidayUiState {
    data object Loading : HolidayUiState
    data object Failed : HolidayUiState
    data object Empty : HolidayUiState
    data class Loaded(val entries: List<HolidayEntry>) : HolidayUiState
}

/** 弹窗编辑目标: isNew=true 添加模式; 网络段派生目标会预填 sourceKey */
private data class EditingTarget(val range: HolidayRange, val isNew: Boolean)

private const val MIN_YEAR = 2005
private const val MAX_YEAR = 2049

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolidaySettingsScreen(
    onBack: () -> Unit,
    tableId: Long? = null,
    viewModel: com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val colors = SleepyTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var year by rememberSaveable { mutableStateOf(LocalDate.now().year) }
    var holidayGrey by remember { mutableStateOf(AppPrefs.isHolidayGreyHoliday(context)) }
    var weekendGrey by remember { mutableStateOf(AppPrefs.isHolidayGreyWeekend(context)) }
    var ignoreWorkday by remember { mutableStateOf(AppPrefs.isHolidayIgnoreWorkday(context)) }
    var style by remember { mutableStateOf(AppPrefs.getHolidayStyle(context)) }
    var state by remember { mutableStateOf<HolidayUiState>(HolidayUiState.Loading) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var overrides by remember { mutableStateOf(AppPrefs.getHolidayRanges(context)) }
    var editing by remember { mutableStateOf<EditingTarget?>(null) }
    // issue#44 调休映射(按课表, 放假日→补班日): 空 = 该放假日按自然星期取课。
    // 卡内课表切换用局部 activeTableId — 只切"正在编辑哪张表", 不动全局选中课表。
    var activeTableId by remember(tableId) { mutableStateOf(tableId) }
    var transfers by remember(activeTableId) {
        mutableStateOf(activeTableId?.let { AppPrefs.getHolidayTransfers(context, it) } ?: emptyList())
    }
    val vmState by viewModel.state.collectAsState()
    val dayNames = remember { context.resources.getStringArray(com.lingion.sleepy.R.array.day_names) }

    fun reload() { overrides = AppPrefs.getHolidayRanges(context) }

    /** 设/清某放假日的"调到哪天上课"; 表 id 为空不落盘(卡已隐藏, 此为双保险) */
    fun saveTransfer(sourceDate: LocalDate, targetDate: LocalDate?, segmentId: String) {
        val id = activeTableId ?: return
        AppPrefs.updateHolidayTransfer(context, id, sourceDate, targetDate, segmentId)
        transfers = AppPrefs.getHolidayTransfers(context, id)
        // issue#44: 映射是按表存的, 只有改到当前选中的那张表才需要刷新课表页/widget/闹钟
        if (id == tableId) {
            viewModel.refreshTransfer()
            val app = context.applicationContext
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                try { com.lingion.sleepy.widget.WidgetUpdater.notifyDataChanged(app) } catch (_: Throwable) {}
                try {
                    (app as? com.lingion.sleepy.SleepyApp)?.notificationScheduler?.scheduleAll()
                } catch (_: Throwable) {}
            }
        }
    }

    /** 保存(新增或替换同 id)一段覆盖 */
    fun saveRange(range: HolidayRange) {
        val next = overrides.filter { it.id != range.id }.toMutableList()
        next.add(range)
        AppPrefs.setHolidayRanges(context, next)
        reload()
    }

    /**
     * 删除一段: 段 id 在 overrides 里 → 直接移除;
     * 是网络段(聚合生成、无对应覆盖) → 写 type=REMOVED + sourceKey 的覆盖挂接该网络段。
     */
    fun deleteRange(range: HolidayRange) {
        val known = overrides.any { it.id == range.id }
        val next = overrides.filter { it.id != range.id }.toMutableList()
        if (!known) {
            next.add(
                HolidayRange(
                    HolidayRangeOps.newId(), range.name, range.startDate, range.endDate,
                    HolidayRangeOps.REMOVED, networkKeyOf(range.type, range.startDate)
                )
            )
        }
        AppPrefs.setHolidayRanges(context, next)
        reload()
    }

    /** 恢复默认: 移除该 id 的覆盖(含 REMOVED 型), 网络段随之回来 */
    fun restoreRange(range: HolidayRange) {
        AppPrefs.setHolidayRanges(context, overrides.filter { it.id != range.id })
        reload()
    }

    fun load(targetYear: Int, force: Boolean = false) {
        loadJob?.cancel()
        loadJob = scope.launch {
            state = HolidayUiState.Loading
            val entries = if (force) {
                HolidayManager.refreshYearEntries(context, targetYear)
            } else {
                HolidayManager.getYearEntries(context, targetYear)
            }
            overrides = AppPrefs.getHolidayRanges(context)
            state = when {
                entries.isEmpty() && HolidayManager.isYearFetchFailed(targetYear) -> HolidayUiState.Failed
                else -> HolidayUiState.Loaded(entries)
            }
        }
    }

    LaunchedEffect(year) { load(year) }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(colors.background),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.holiday_page_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SleepyTheme.shapes.large)
                        .background(colors.surfaceContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { year-- },
                        enabled = year > MIN_YEAR
                    ) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = stringResource(R.string.holiday_year_prev))
                    }
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    IconButton(
                        onClick = { year++ },
                        enabled = year < MAX_YEAR
                    ) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = stringResource(R.string.holiday_year_next))
                    }
                }
            }

            item {
                // 单行卡(用户 2026-09-04): 标题+URL 同行, 刷新=图标(语言无关, 不随文案伸缩)。
                // Loading 态图标位转圈占位(行高稳定); Failed 提示行单独挂卡底(信息行不可省)。
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SleepyTheme.shapes.large)
                        .background(colors.surfaceContainer)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.holiday_data_source),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurface
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.holiday_source_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        when (state) {
                            HolidayUiState.Loading -> CircularProgressIndicator(
                                modifier = Modifier.size(18.dp).padding(2.dp),
                                color = colors.primary,
                                strokeWidth = 2.dp
                            )
                            else -> IconButton(
                                onClick = { load(year, force = true) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Refresh,
                                    contentDescription = stringResource(R.string.holiday_data_refresh),
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    if (state is HolidayUiState.Failed) {
                        Text(
                            stringResource(R.string.holiday_data_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.error,
                            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                        )
                    } else if (state is HolidayUiState.Empty) {
                        Text(
                            stringResource(R.string.holiday_data_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                        )
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SleepyTheme.shapes.large)
                        .background(colors.surfaceContainer)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SettingToggleRow(
                        label = stringResource(R.string.settings_holiday_holiday),
                        subtitle = stringResource(R.string.settings_holiday_holiday_sub),
                        checked = holidayGrey,
                        onCheckedChange = { holidayGrey = it; AppPrefs.setHolidayGreyHoliday(context, it) }
                    )
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                    SettingToggleRow(
                        label = stringResource(R.string.settings_holiday_weekend),
                        subtitle = stringResource(R.string.settings_holiday_weekend_sub),
                        checked = weekendGrey,
                        onCheckedChange = { weekendGrey = it; AppPrefs.setHolidayGreyWeekend(context, it) }
                    )
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                    SettingToggleRow(
                        label = stringResource(R.string.settings_holiday_workday),
                        subtitle = stringResource(R.string.settings_holiday_workday_sub),
                        checked = ignoreWorkday,
                        onCheckedChange = { ignoreWorkday = it; AppPrefs.setHolidayIgnoreWorkday(context, it) }
                    )
                }
            }

            item {
                // issue#44 调休说明卡: 说明 + 卡内课表切换(局部 activeTableId, 不动全局选中)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SleepyTheme.shapes.large)
                        .background(colors.surfaceContainer)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.holiday_makeup_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface
                    )
                    Text(
                        text = if (tableId == null) stringResource(R.string.holiday_makeup_no_table)
                        else stringResource(R.string.holiday_makeup_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    if (tableId != null && vmState.tables.size > 1) {
                        Spacer(Modifier.height(4.dp))
                        SegmentedSwitcher(
                            options = vmState.tables.map { it.id to it.name },
                            selected = activeTableId,
                            onSelect = { id -> activeTableId = id },
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = colors.surfaceContainerHighest
                        )
                    }
                }
            }

            item {
                // 标题行右贴 SegmentedSwitcher — 与通用设置页各平铺卡同款(用户 2026-09-04)
                SettingsFlatCard(
                    title = stringResource(R.string.settings_holiday_style),
                    options = listOf(
                        stringResource(R.string.settings_holiday_style_grey),
                        stringResource(R.string.settings_holiday_style_strikethrough)
                    ),
                    selectedKey = if (style == "strikethrough") 1 else 0,
                    onSelect = { idx ->
                        val next = if (idx == 1) "strikethrough" else "grey"
                        if (style != next) { style = next; AppPrefs.setHolidayStyle(context, next) }
                    }
                )
            }

            val loaded = state as? HolidayUiState.Loaded
            if (loaded != null) {
                // 覆盖变化时基于原始网络数据即时重合并, 不重新走网络
                val merged = HolidayRangeOps.mergeSegments(loaded.entries, overrides)
                val userRangeIds = overrides.map { it.id }.toSet()
                val holidaySegments = merged.active.filter { it.type == HolidayManager.TYPE_PUBLIC_HOLIDAY }
                val workdaySegments = merged.active.filter { it.type == HolidayManager.TYPE_TRANSFER_WORKDAY }

                if (holidaySegments.isNotEmpty()) {
                    // issue#44 第二轮: 节卡树状视图 — 每个放假日一行, 行尾右格选补班日。
                    // 段编辑是次要路径, 折叠到下方"可编辑假期段"卡(默认收起)。
                    holidaySegments.forEach { seg ->
                        item {
                            HolidayTransferCard(
                                segment = seg,
                                transfers = transfers,
                                workdayDates = workdaySegments
                                    .flatMap { s -> generateSequence(s.startDate) { if (it < s.endDate) it.plusDays(1) else null } }
                                    .distinct()
                                    .sorted(),
                                dayNames = dayNames,
                                onPick = { source, target -> saveTransfer(source, target, seg.id) },
                                // 用户 2026-09-18 定稿: 每张节卡标题行最右编辑按钮 —
                                // 国务院数据段只许编辑持续时间(段起止), 走原 HolidayRangeEditDialog
                                onEditSegment = { editing = resolveEditTarget(seg, userRangeIds) }
                            )
                        }
                    }
                    // 孤儿映射: sourceDate 已不在今年任何放假日段里(改年份/换数据源后残留)。
                    // 不删 — 灰卡列出, 行尾"清除"手动处理 (用户决定 C1)
                    val yearDates = holidaySegments
                        .flatMap { s -> generateSequence(s.startDate) { if (it < s.endDate) it.plusDays(1) else null } }
                        .toSet()
                    val orphanEntries = transfers.filter { it.sourceDate !in yearDates }.sortedBy { it.sourceDate }
                    if (orphanEntries.isNotEmpty()) {
                        item {
                            HolidayOrphanCard(
                                entries = orphanEntries,
                                dayNames = dayNames,
                                onClear = { source -> saveTransfer(source, null, "orphan") }
                            )
                        }
                    }
                }
                if (merged.removed.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.holiday_removed_section)) }
                    item {
                        HolidayRemovedCard(
                            segments = merged.removed,
                            onRestore = { restoreRange(it) }
                        )
                    }
                }
                item {
                    FilledTonalButton(
                        onClick = {
                            editing = EditingTarget(
                                HolidayRange(
                                    id = HolidayRangeOps.newId(),
                                    name = "",
                                    startDate = LocalDate.of(year, 1, 1),
                                    endDate = LocalDate.of(year, 1, 1),
                                    type = HolidayManager.TYPE_PUBLIC_HOLIDAY,
                                    sourceKey = null
                                ),
                                isNew = true
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.regularHeight),
                        shape = SleepyTheme.Buttons.shape
                    ) { Text(stringResource(R.string.holiday_add_entry)) }
                }
            }
        }
    }

    editing?.let { t ->
        HolidayRangeEditDialog(
            target = t.range,
            isNew = t.isNew,
            onDismiss = { editing = null },
            onSave = { range ->
                saveRange(range)
                editing = null
            },
            onDelete = { range ->
                deleteRange(range)
                editing = null
            }
        )
    }
}

/** 网络段键: "holiday:<start>"/"workday:<start>", 与 HolidayRangeOps.mergeSegments 的命中规则一致 */
private fun networkKeyOf(type: String, date: LocalDate) =
    "${if (type == HolidayManager.TYPE_TRANSFER_WORKDAY) "workday" else "holiday"}:$date"

/**
 * 行点击 → 弹窗编辑目标。网络段(聚合生成的 id 不在 overrides 里)复制一份并
 * 立即补上 sourceKey, 保存/删除时即按该键整段挂接替换/删除, 不产生重复行。
 */
private fun resolveEditTarget(segment: HolidayRange, userRangeIds: Set<String>): EditingTarget =
    if (segment.id in userRangeIds) {
        EditingTarget(segment, isNew = false)
    } else {
        EditingTarget(
            segment.copy(sourceKey = networkKeyOf(segment.type, segment.startDate)),
            isNew = false
        )
    }

/** 段日期展示: 单日 M/d, 跨日 M/d – M/d */
private fun segmentDateLabel(seg: HolidayRange): String =
    if (seg.startDate == seg.endDate) {
        DateUtils.shortDateSlash(seg.startDate)
    } else {
        "${DateUtils.shortDateSlash(seg.startDate)} – ${DateUtils.shortDateSlash(seg.endDate)}"
    }


/**
 * issue#44 调休映射卡: 一个放假日段一张卡。每行 = 一个放假日 → 右侧灰圆角格。
 * 右侧下拉菜单固定三项: ① 当年所有官方补班日(扁平全量, 升序, 不分组/不禁用/不猜 — 即使用户已经有 N 天映射,
 * 其它天照列)② 无(清除)③ 其他日期…(弹系统 DatePickerDialog)。
 * 同目标日互斥: 已在 AppPrefs.updateHolidayTransfer → withTargetExclusivity 落实; 选同一 targetDate
 * 会自动把之前选它的那行回灰。UI 不禁用任何项, 不打扰用户操作。
 * 孤儿行(sourceDate 不在今年放假日集合里)直接灰 + 提示"对应放假日已不存在", 单击清掉。
 */
@Composable
private fun HolidayTransferCard(
    segment: HolidayRange,
    transfers: List<com.lingion.sleepy.util.HolidayTransferEntry>,
    workdayDates: List<LocalDate>,
    dayNames: Array<String>,
    onPick: (LocalDate, LocalDate?) -> Unit,
    onEditSegment: () -> Unit
) {
    val colors = SleepyTheme.colors
    val title = segment.name.ifBlank { DateUtils.shortDateSlash(segment.startDate) }
    val subtitle = segmentDateLabel(segment)
    val dates = remember(segment) {
        generateSequence(segment.startDate) { if (it < segment.endDate) it.plusDays(1) else null }
            .toList()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SleepyTheme.shapes.large)
            .background(colors.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            // 标题行最右编辑按钮: 编辑该段的持续时间(起止日期) — 国务院数据段同样开放,
            // 底部"+"按钮才是新增自定义补班日/自定义假期的入口
            IconButton(onClick = onEditSegment) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.holiday_transfer_edit_segment),
                    tint = colors.onSurfaceVariant
                )
            }
        }
        dates.forEach { date ->
            HolidayTransferRow(
                date = date,
                targetDate = transfers.lastOrNull { it.sourceDate == date }?.targetDate,
                workdayDates = workdayDates,
                dayNames = dayNames,
                onPick = { picked -> onPick(date, picked) }
            )
        }
    }
}

/**
 * 失效映射卡: 孤儿 entries 的收纳处 — sourceDate 已不在今年任何放假日段里
 * (用户改年份/删段/数据源变动后残留)。不自动删, 灰色列出 + 行尾"清除"手动处理。
 */
@Composable
private fun HolidayOrphanCard(
    entries: List<com.lingion.sleepy.util.HolidayTransferEntry>,
    dayNames: Array<String>,
    onClear: (LocalDate) -> Unit
) {
    val colors = SleepyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SleepyTheme.shapes.large)
            .background(colors.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.holiday_transfer_orphan_title),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.holiday_transfer_orphan_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
        entries.forEach { entry ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${DateUtils.shortDateSlash(entry.sourceDate)} → ${DateUtils.shortDateSlash(entry.targetDate)}" +
                        " (${dayNames[entry.targetDate.dayOfWeek.value - 1]})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onClear(entry.sourceDate) },
                    modifier = Modifier.height(SleepyTheme.Buttons.regularHeight),
                    shape = SleepyTheme.Buttons.shape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.secondaryContainer,
                        contentColor = colors.onSecondaryContainer
                    )
                ) { Text(stringResource(R.string.holiday_transfer_clear)) }
            }
        }
    }
}

@Composable
private fun HolidayTransferRow(
    date: LocalDate,
    targetDate: LocalDate?,
    workdayDates: List<LocalDate>,
    dayNames: Array<String>,
    onPick: (LocalDate?) -> Unit
) {
    // 用户 2026-09-18 三次定稿: 用 Material3 原生 ExposedDropdownMenuBox —
    // 锚字段(右格)尺寸不变, 展开的菜单锚到锚字段下方, 浮层一个图层叠上去, 视觉 = 这个矩形自己长高了浮起来。
    // 第 0 行 = 完全空白的 DropdownMenuItem(text = { Text("") }) = 空白状态占位, 防止首项直接选中下面候选。
    // 候选从第 1 行开始: ① 当年所有官方补班日(扁平全量, 升序, 不分组/不禁用/不猜) ② 无 ③ 其他日期…
    val colors = SleepyTheme.colors
    val leftLabel = remember(date, dayNames) {
        "${DateUtils.shortDateSlash(date)} (${dayNames[date.dayOfWeek.value - 1]})"
    }
    val rightLabel = remember(targetDate, dayNames) {
        if (targetDate == null) "—"
        else "${DateUtils.shortDateSlash(targetDate)} (${dayNames[targetDate.dayOfWeek.value - 1]})"
    }
    val pickOtherLabel = stringResource(R.string.holiday_transfer_pick_other)
    val noMappingLabel = stringResource(R.string.holiday_makeup_unset)
    var menuOpen by remember(date) { mutableStateOf(false) }
    var showDatePicker by remember(date) { mutableStateOf(false) }
    val datePickerState = androidx.compose.material3.rememberDatePickerState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = leftLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        // 右格本体: Material3 ExposedDropdownMenuBox 的 anchor field。
        // anchor 字段尺寸始终等于右格原本尺寸(width 不变); 菜单展开后 anchor 不动, 浮层 menu 抬高一层叠在 anchor 下方 —
        // 视觉 = 10×2 矩形自己变 10×X, 锚点不变, 内容自适应撑高, 关闭后回到原 10×2 状态。
        val targetColor = if (targetDate == null) colors.surfaceContainerHighest else colors.primaryContainer
        val targetContentColor = if (targetDate == null) colors.onSurfaceVariant else colors.onPrimaryContainer
        ExposedDropdownMenuBox(
            expanded = menuOpen,
            onExpandedChange = { menuOpen = it },
            modifier = Modifier.weight(1f)
        ) {
            // anchor 字段: 右格圆角矩形 (text + 向下箭头)
            Row(
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth()
                    .clip(SleepyTheme.shapes.medium)
                    .background(targetColor)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rightLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = targetContentColor,
                    modifier = Modifier.weight(1f)
                )
                // ExposedDropdownMenuDefaults.TrailingIcon(expanded) 自动随菜单开合旋转箭头;
                // 无 tint 参数, 色取 LocalContentColor → 用 CompositionLocalProvider 着色
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides targetContentColor
                ) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOpen)
                }
            }
            // 浮层菜单: 锚在 anchor 字段下方, 宽度 = anchor 字段宽, 高度由内容自适应 → 视觉 = 矩形长高浮起来
            ExposedDropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                // 第 0 行: 空白占位 (用户原话: 第一行不选任何东西, 完全是空的作为空白状态)
                DropdownMenuItem(
                    text = { Text("") },
                    onClick = { menuOpen = false },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                )
                // ① 当年所有官方补班日(扁平全量, 升序, 不分组/不禁用/不猜)
                workdayDates.forEach { wd ->
                    DropdownMenuItem(
                        text = { Text("${DateUtils.shortDateSlash(wd)} (${dayNames[wd.dayOfWeek.value - 1]})") },
                        onClick = { onPick(wd); menuOpen = false }
                    )
                }
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                // ② 无: 清除该日映射
                DropdownMenuItem(
                    text = { Text(noMappingLabel) },
                    onClick = { onPick(null); menuOpen = false }
                )
                // ③ 其他日期: 弹系统 DatePickerDialog
                DropdownMenuItem(
                    text = { Text(pickOtherLabel) },
                    onClick = { menuOpen = false; showDatePicker = true }
                )
            }
        }
    }
    if (showDatePicker) {
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val picked = java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                                .toLocalDate()
                            onPick(picked)
                        }
                        showDatePicker = false
                    },
                    shape = SleepyTheme.shapes.medium
                ) { Text(stringResource(R.string.ok), maxLines = 1) }
            },
            dismissButton = {
                androidx.compose.material3.Button(
                    onClick = { showDatePicker = false },
                    shape = SleepyTheme.shapes.medium,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = colors.secondaryContainer,
                        contentColor = colors.onSecondaryContainer
                    )
                ) { Text(stringResource(R.string.cancel), maxLines = 1) }
            }
        ) {
            androidx.compose.material3.DatePicker(state = datePickerState)
        }
    }
}

/** 已删除区块: 被用户删除的网络段, 行尾"恢复默认"移除覆盖使网络段回来 */
@Composable
private fun HolidayRemovedCard(
    segments: List<HolidayRange>,
    onRestore: (HolidayRange) -> Unit
) {
    val colors = SleepyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SleepyTheme.shapes.large)
            .background(colors.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        segments.forEachIndexed { index, segment ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = segment.name.ifBlank { DateUtils.shortDateSlash(segment.startDate) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(segmentDateLabel(segment), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                // 恢复用 secondaryContainer 色块 — 与删除/刷新同风格, 禁悬空文字按钮
                Button(
                    onClick = { onRestore(segment) },
                    modifier = Modifier.height(SleepyTheme.Buttons.regularHeight),
                    shape = SleepyTheme.Buttons.shape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.secondaryContainer,
                        contentColor = colors.onSecondaryContainer
                    )
                ) { Text(stringResource(R.string.holiday_restore)) }
            }
            if (index != segments.lastIndex) HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
        }
    }
}

/**
 * 编辑/添加弹窗(起止日期范围段)。
 * target 为网络段时, 保存时补 sourceKey="holiday|workday:<首日>" 挂接替换该网络段。
 * 校验: start/end 均有效且 end >= start, 否则禁用保存并提示 holiday_date_invalid。
 * 输入框全走项目 token(TextField + fieldShape + fieldColors, 色块无描线);
 * 类型选择 = SegmentedSwitcher 全宽(与设置页 Tab 同款, 替代散排 chip)。
 */
@Composable
private fun HolidayRangeEditDialog(
    target: HolidayRange,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (HolidayRange) -> Unit,
    onDelete: (HolidayRange) -> Unit
) {
    val colors = SleepyTheme.colors
    var name by remember(target) { mutableStateOf(target.name) }
    var startText by remember(target) { mutableStateOf(target.startDate.toString()) }
    var endText by remember(target) { mutableStateOf(target.endDate.toString()) }
    var type by remember(target) { mutableStateOf(target.type) }
    val startDate = try { LocalDate.parse(startText) } catch (_: Exception) { null }
    val endDate = try { LocalDate.parse(endText) } catch (_: Exception) { null }
    val datesValid = startDate != null && endDate != null && !endDate.isBefore(startDate)

    AlertDialog(
        onDismissRequest = onDismiss,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
        title = { Text(stringResource(if (isNew) R.string.holiday_add_title else R.string.holiday_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DatePickerField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = stringResource(R.string.holiday_name_label_date),
                    modifier = Modifier.fillMaxWidth(),
                    isError = startText.isNotBlank() && startDate == null
                )
                DatePickerField(
                    value = endText,
                    onValueChange = { endText = it },
                    label = stringResource(R.string.holiday_name_label_end),
                    modifier = Modifier.fillMaxWidth(),
                    isError = endText.isNotBlank() && (endDate == null || (startDate != null && endDate.isBefore(startDate)))
                )
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.holiday_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = SleepyTheme.fieldShape,
                    colors = SleepyTheme.fieldColors()
                )
                SegmentedSwitcher(
                    options = listOf(
                        HolidayManager.TYPE_PUBLIC_HOLIDAY to stringResource(R.string.holiday_type_holiday),
                        HolidayManager.TYPE_TRANSFER_WORKDAY to stringResource(R.string.holiday_type_workday)
                    ),
                    selected = type,
                    onSelect = { type = it },
                    modifier = Modifier.fillMaxWidth()
                )
                if (!datesValid && (startText.isNotBlank() || endText.isNotBlank())) {
                    Text(
                        stringResource(R.string.holiday_date_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error
                    )
                }
                if (!isNew) {
                    // 删除走 errorContainer 色块 — 纯色块禁描边规则。
                    // 弹窗不设"恢复": 已保存段删除=移除覆盖(可从已删除区恢复), 网络段删除=REMOVED 覆盖(同入口恢复);
                    // 弹窗内两个按钮会做同一件事, 恢复入口统一收敛到"已删除"区块。
                    Button(
                        onClick = { onDelete(target) },
                        modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.regularHeight),
                        shape = SleepyTheme.Buttons.shape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.errorContainer,
                            contentColor = colors.onErrorContainer
                        )
                    ) { Text(stringResource(R.string.holiday_delete_range)) }
                }
                // 2026-09-16 用户: 裸 TextButton 无边界无色块 — 统一色块按钮行
                com.lingion.sleepy.ui.component.DialogActionButtons(
                    confirmText = stringResource(R.string.save),
                    onConfirm = {
                        val start = startDate ?: return@DialogActionButtons
                        val end = endDate ?: return@DialogActionButtons
                        // sourceKey 由 resolveEditTarget 填好: 网络段派生=挂接键, 纯用户段=保持 null
                        onSave(HolidayRange(target.id, name.trim(), start, end, type, target.sourceKey))
                    },
                    dismissText = stringResource(R.string.cancel),
                    onDismiss = onDismiss,
                    confirmEnabled = datesValid
                )
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}
