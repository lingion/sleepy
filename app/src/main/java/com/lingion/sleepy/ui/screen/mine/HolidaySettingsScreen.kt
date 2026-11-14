package com.lingion.sleepy.ui.screen.mine

import com.lingion.sleepy.ui.component.AccordionHeader
import com.lingion.sleepy.ui.component.AccordionContent

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import com.lingion.sleepy.ui.component.liquidglass.GlassAlertDialog as AlertDialog
import com.lingion.sleepy.ui.component.liquidglass.GlassDatePickerDialog as DatePickerDialog
import com.lingion.sleepy.ui.component.liquidglass.GlassButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.lingion.sleepy.ui.component.liquidglass.GlassTonalButton as FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.lingion.sleepy.ui.component.liquidglass.GlassIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import com.lingion.sleepy.ui.component.liquidglass.GlassTopAppBar as TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.component.SettingsLayout
import com.lingion.sleepy.console.ui.dbgTag
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.component.DatePickerField
import com.lingion.sleepy.ui.component.DialogActionButtons
import com.lingion.sleepy.ui.component.SectionHeader
import com.lingion.sleepy.ui.component.liquidglass.SingleChoiceDropdown
import com.lingion.sleepy.ui.component.liquidglass.LocalGlassHost
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.UiStyle
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.HolidayEntry
import com.lingion.sleepy.util.HolidayManager
import com.lingion.sleepy.util.HolidayRange
import com.lingion.sleepy.util.HolidayRangeOps
import com.lingion.sleepy.widget.notification.ReminderRescheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import com.lingion.sleepy.util.AppClock

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

private fun getHolidayTransfers(context: android.content.Context, tableId: Long) =
    AppPrefs.getHolidayTransfers(context, tableId)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolidaySettingsScreen(
    onBack: () -> Unit,
    tableId: Long? = null,
    initialTableId: Long? = null,
    viewModel: com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var year by rememberSaveable { mutableStateOf(LocalDate.now(AppClock).year) }
    // Preloaded years must render their final layout on the first navigation frame.
    var state by remember(year) {
        mutableStateOf<HolidayUiState>(
            HolidayManager.peekYearEntries(year)?.let { HolidayUiState.Loaded(it) }
                ?: HolidayUiState.Loading
        )
    }
    var isLoading by remember(year) { mutableStateOf(state is HolidayUiState.Loading) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var overrides by remember { mutableStateOf(AppPrefs.getHolidayRanges(context)) }
    var editing by remember { mutableStateOf<EditingTarget?>(null) }
    // issue#44 调休映射(按课表, 放假日→补班日): 空 = 该放假日按自然星期取课。
    // 卡内课表切换用局部 activeTableId — 只切"正在编辑哪张表", 不动全局选中课表。
    var activeTableId by rememberSaveable(initialTableId, tableId) { mutableStateOf(initialTableId ?: tableId) }
    // Expansion belongs only to this visit/year/table; newly encountered groups stay collapsed.
    var expandedGroups by remember(year, activeTableId) { mutableStateOf(emptySet<String>()) }
    fun toggleGroup(key: String) {
        expandedGroups = if (key in expandedGroups) expandedGroups - key else expandedGroups + key
    }
    var transfers by remember(activeTableId) {
        mutableStateOf(activeTableId?.let { getHolidayTransfers(context, it) } ?: emptyList())
    }
    val vmState by viewModel.state.collectAsState()
    val dayNames = remember { context.resources.getStringArray(com.lingion.sleepy.R.array.day_names) }

    fun reload() { overrides = AppPrefs.getHolidayRanges(context) }

    /** 设/清某放假日的"调到哪天上课"; 表 id 为空不落盘(卡已隐藏, 此为双保险) */
    fun saveTransfer(sourceDate: LocalDate, targetDate: LocalDate?, segmentId: String) {
        val id = activeTableId ?: return
        AppPrefs.updateHolidayTransfer(context, id, sourceDate, targetDate, segmentId)
        transfers = AppPrefs.getHolidayTransfers(context, id)
        // Only the current table drives the foreground view; bound widgets and reminders
        // can reference any table, including one opened directly from its editor.
        if (id == tableId) viewModel.refreshTransfer()
        val app = context.applicationContext
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            try { com.lingion.sleepy.widget.WidgetUpdater.notifyDataChanged(app) } catch (_: Throwable) {}
            ReminderRescheduler.request()
        }
    }

    /** 保存(新增或替换同 id)一段覆盖 */
    fun saveRange(range: HolidayRange) {
        val next = overrides.filter { it.id != range.id }.toMutableList()
        next.add(range)
        AppPrefs.setHolidayRanges(context, next)
        reload()
        ReminderRescheduler.request()
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
        ReminderRescheduler.request()
    }

    /** 恢复默认: 移除该 id 的覆盖(含 REMOVED 型), 网络段随之回来 */
    fun restoreRange(range: HolidayRange) {
        AppPrefs.setHolidayRanges(context, overrides.filter { it.id != range.id })
        reload()
        ReminderRescheduler.request()
    }

    fun load(targetYear: Int, force: Boolean = false) {
        loadJob?.cancel()
        loadJob = scope.launch {
            isLoading = true
            try {
                // Disk-cache reads/JSON parsing also need IO, not just the network fetch.
                val (entries, ranges) = withContext(Dispatchers.IO) {
                    val entries = if (force) {
                        HolidayManager.refreshYearEntries(context, targetYear)
                    } else {
                        HolidayManager.getYearEntries(context, targetYear)
                    }
                    entries to AppPrefs.getHolidayRanges(context)
                }
                overrides = ranges
                state = when {
                    entries.isEmpty() && HolidayManager.isYearFetchFailed(targetYear) -> HolidayUiState.Failed
                    else -> HolidayUiState.Loaded(entries)
                }
                if (force) ReminderRescheduler.request()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(year) {
        loadJob?.cancel()
        if (state !is HolidayUiState.Loaded) load(year)
    }

    val loaded = state as? HolidayUiState.Loaded
    val merged = remember(loaded, overrides) {
        loaded?.let { HolidayRangeOps.mergeSegments(it.entries, overrides) }
    }
    val workdayDates = remember(merged) {
        merged?.active.orEmpty()
            .filter { it.type == HolidayManager.TYPE_TRANSFER_WORKDAY }
            .flatMap { s -> generateSequence(s.startDate) { if (it < s.endDate) it.plusDays(1) else null } }
            .distinct()
            .sorted()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(colors.background),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_holiday_title_rules)) },
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
            verticalArrangement = Arrangement.spacedBy(SettingsLayout.cardGap)
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
                        style = SettingsLayout.headingStyle,
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
                            style = SettingsLayout.headingStyle,
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
                        HolidayDataSourceAction(
                            isLoading = isLoading,
                            onRefresh = { load(year, force = true) },
                        )
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
                        style = SettingsLayout.headingStyle,
                        color = colors.onSurface
                    )
                    Text(
                        text = if (activeTableId == null) stringResource(R.string.holiday_makeup_no_table)
                        else stringResource(R.string.holiday_makeup_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.holiday_mapping_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    val currentTableId = activeTableId
                    if (currentTableId != null && vmState.tables.size > 1) {
                        Spacer(Modifier.height(4.dp))
                        SingleChoiceDropdown(
                            title = stringResource(R.string.schedule_switch_table),
                            selectedId = currentTableId.toString(),
                            options = vmState.tables.map { it.id.toString() to it.name },
                            onSelect = { activeTableId = it.toLong() },
                            modifier = Modifier.fillMaxWidth(),
                            tagPrefix = "holiday_table",
                        )
                    }
                }
            }


            // Loaded holiday data: per-table mappings precede shared date maintenance.
            if (merged != null) {
                // 覆盖变化时基于原始网络数据即时重合并, 不重新走网络
                val userRangeIds = overrides.map { it.id }.toSet()
                val holidaySegments = merged.active.filter { it.type == HolidayManager.TYPE_PUBLIC_HOLIDAY }

                if (holidaySegments.isNotEmpty()) {
                    // 映射只设置所选课表的补课日期，共享日期编辑统一放在日期管理区。
                    holidaySegments.forEach { seg ->
                        // Network aggregation regenerates ids after edits; use its stable source key.
                        val groupKey = "transfer:${seg.sourceKey ?: if (seg.id in userRangeIds) seg.id else networkKeyOf(seg.type, seg.startDate)}"
                        item(key = groupKey) {
                            HolidayTransferCard(
                                segment = seg,
                                transfers = transfers,
                                workdayDates = workdayDates,
                                dayNames = dayNames,
                                expanded = groupKey in expandedGroups,
                                onToggle = { toggleGroup(groupKey) },
                                onPick = { source, target -> saveTransfer(source, target, seg.id) }
                            )
                        }
                    }
                }
                item(key = "holiday_dates_header") {
                    Column {
                        HolidayGroupHeader(
                            title = stringResource(R.string.settings_holiday_dates),
                            subtitle = stringResource(R.string.settings_holiday_dates_scope),
                            expanded = "dates" in expandedGroups,
                            onToggle = { toggleGroup("dates") },
                            modifier = Modifier.clip(SleepyTheme.shapes.large)
                                .background(colors.surfaceContainer),
                        )
                        AccordionContent(expanded = "dates" in expandedGroups) {
                            Column(
                                modifier = Modifier.padding(top = SettingsLayout.cardGap),
                                verticalArrangement = Arrangement.spacedBy(SettingsLayout.cardGap),
                            ) {
                                merged.active.forEach { seg ->
                                    HolidayDateManagementRow(
                                        segment = seg,
                                        onEdit = { editing = resolveEditTarget(seg, userRangeIds) }
                                    )
                                }
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
                // 孤儿映射: sourceDate 已不在今年任何放假日段里(改年份/换数据源后残留)。
                // 不删 — 灰卡列出, 行尾"清除"手动处理 (用户决定 C1)
                val yearDates = holidaySegments
                    .flatMap { s -> generateSequence(s.startDate) { if (it < s.endDate) it.plusDays(1) else null } }
                    .toSet()
                val orphanEntries = transfers.filter { it.sourceDate !in yearDates }.sortedBy { it.sourceDate }

                if (orphanEntries.isNotEmpty() || merged.removed.isNotEmpty()) {
                    item(key = "holiday_maintenance_header") {
                        Column {
                            HolidayGroupHeader(
                                title = stringResource(R.string.settings_holiday_maintenance),
                                expanded = "maintenance" in expandedGroups,
                                onToggle = { toggleGroup("maintenance") },
                                modifier = Modifier.clip(SleepyTheme.shapes.large)
                                    .background(colors.surfaceContainer),
                            )
                            AccordionContent(expanded = "maintenance" in expandedGroups) {
                                Column(Modifier.padding(top = SettingsLayout.cardGap), verticalArrangement = Arrangement.spacedBy(SettingsLayout.cardGap)) {
                                    if (orphanEntries.isNotEmpty()) {
                                        HolidayOrphanCard(
                                            entries = orphanEntries,
                                            dayNames = dayNames,
                                            onClear = { source -> saveTransfer(source, null, "orphan") },
                                        )
                                    }
                                    if (merged.removed.isNotEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(SettingsLayout.headerGap)) {
                                            SectionHeader(stringResource(R.string.holiday_removed_section))
                                            HolidayRemovedCard(
                                                segments = merged.removed,
                                                onRestore = { restoreRange(it) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
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

@Composable
internal fun HolidayDataSourceAction(isLoading: Boolean, onRefresh: () -> Unit) {
    // Both states occupy the full touch target, including Material's minimum size.
    // Otherwise loading -> loaded changes this row's height and shifts every card below it.
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp).padding(2.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
            )
        } else {
            IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.holiday_data_refresh),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
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

@Composable
private fun HolidayDateManagementRow(segment: HolidayRange, onEdit: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(SleepyTheme.shapes.large)
            .background(colors.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(SettingsLayout.descriptionGap)) {
            Text(
                segment.name.ifBlank { segmentDateLabel(segment) },
                style = SettingsLayout.headingStyle,
                color = colors.onSurface
            )
            Text(segmentDateLabel(segment), style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant)
            Text(
                stringResource(if (segment.type == HolidayManager.TYPE_TRANSFER_WORKDAY)
                    R.string.holiday_type_workday else R.string.holiday_type_holiday),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.holiday_transfer_edit_segment),
                tint = colors.onSurfaceVariant)
        }
    }
}

/**
 * 调休映射卡: 一个放假日段一组，初次进入折叠，展开后每行映射一个放假日。
 * 右侧下拉菜单固定三项: ① 当年所有官方补班日(扁平全量, 升序, 不分组/不禁用/不猜 — 即使用户已经有 N 天映射,
 * 其它天照列)② 无(清除)③ 其他日期…(弹系统 DatePickerDialog)。
 * 同目标日互斥: 已在 AppPrefs.updateHolidayTransfer → withTargetExclusivity 落实; 选同一 targetDate
 * 会自动把之前选它的那行回灰。UI 不禁用任何项, 不打扰用户操作。
 * 失效映射另收纳在维护组，由用户展开后清除。
 */
@Composable
internal fun HolidayTransferCard(
    segment: HolidayRange,
    transfers: List<com.lingion.sleepy.util.HolidayTransferEntry>,
    workdayDates: List<LocalDate>,
    dayNames: Array<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPick: (LocalDate, LocalDate?) -> Unit
) {
    val colors = MaterialTheme.colorScheme
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
            .background(colors.surfaceContainer),
    ) {
        HolidayGroupHeader(title, subtitle, expanded, onToggle)
        AccordionContent(expanded = expanded) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
    }
}

@Composable
private fun HolidayGroupHeader(
    title: String,
    subtitle: String? = null,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    AccordionHeader(expanded = expanded, onToggle = onToggle, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(SettingsLayout.descriptionGap)) {
            Text(title, style = SettingsLayout.headingStyle, color = colors.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
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
    val colors = MaterialTheme.colorScheme
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
            style = SettingsLayout.headingStyle,
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
    // Candidates stay complete: official workdays, clear mapping, or another date.
    val colors = MaterialTheme.colorScheme
    val nativeDialogActions = LocalUiStyle.current == UiStyle.LiquidGlass && LocalGlassHost.current != null
    val leftLabel = remember(date, dayNames) {
        "${DateUtils.shortDateSlash(date)} (${dayNames[date.dayOfWeek.value - 1]})"
    }
    val rightLabel = remember(targetDate, dayNames) {
        if (targetDate == null) "—"
        else "${DateUtils.shortDateSlash(targetDate)} (${dayNames[targetDate.dayOfWeek.value - 1]})"
    }
    val pickOtherLabel = stringResource(R.string.holiday_transfer_pick_other)
    val noMappingLabel = stringResource(R.string.holiday_makeup_unset)
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
        SingleChoiceDropdown(
            title = "",
            selectedId = targetDate?.toString() ?: "none",
            options = workdayDates.map { workday ->
                workday.toString() to "${DateUtils.shortDateSlash(workday)} (${dayNames[workday.dayOfWeek.value - 1]})"
            } + listOf("none" to noMappingLabel, "other" to pickOtherLabel),
            onSelect = { selectedId ->
                when (selectedId) {
                    "none" -> onPick(null)
                    "other" -> Unit
                    else -> onPick(LocalDate.parse(selectedId))
                }
            },
            modifier = Modifier.weight(1f).semantics { contentDescription = leftLabel },
            selectedLabel = rightLabel,
            tagPrefix = "holiday_target_$date",
            onDismissed = { selectedId ->
                if (selectedId == "other") showDatePicker = true
            },
        )
    }
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Button(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val picked = java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                                .toLocalDate()
                            onPick(picked)
                        }
                        showDatePicker = false
                    },
                    shape = if (nativeDialogActions) null else SleepyTheme.shapes.medium
                ) { Text(stringResource(R.string.ok), maxLines = if (nativeDialogActions) Int.MAX_VALUE else 1) }
            },
            dismissButton = {
                Button(
                    onClick = { showDatePicker = false },
                    shape = if (nativeDialogActions) null else SleepyTheme.shapes.medium,
                    colors = if (nativeDialogActions) null else androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = colors.secondaryContainer,
                        contentColor = colors.onSecondaryContainer
                    )
                ) { Text(stringResource(R.string.cancel), maxLines = if (nativeDialogActions) Int.MAX_VALUE else 1) }
            }
        ) {
            com.lingion.sleepy.ui.component.SleepyDatePicker(state = datePickerState)
        }
    }
}

/** 已删除区块: 被用户删除的网络段, 行尾"恢复默认"移除覆盖使网络段回来 */
@Composable
private fun HolidayRemovedCard(
    segments: List<HolidayRange>,
    onRestore: (HolidayRange) -> Unit
) {
    val colors = MaterialTheme.colorScheme
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
 * 弹窗与日期选择器跟随当前样式，Material Design 使用各包装器的原生回退。
 */
@Composable
internal fun HolidayRangeEditDialog(
    target: HolidayRange,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (HolidayRange) -> Unit,
    onDelete: (HolidayRange) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var name by remember(target) { mutableStateOf(target.name) }
    var startText by remember(target) { mutableStateOf(target.startDate.toString()) }
    var endText by remember(target) { mutableStateOf(target.endDate.toString()) }
    var type by remember(target) { mutableStateOf(target.type) }
    val startDate = try { LocalDate.parse(startText) } catch (_: Exception) { null }
    val endDate = try { LocalDate.parse(endText) } catch (_: Exception) { null }
    val datesValid = startDate != null && endDate != null && !endDate.isBefore(startDate)

    val nativeActions = LocalUiStyle.current == UiStyle.LiquidGlass && LocalGlassHost.current != null
    val saveRange: () -> Unit = save@ {
        val start = startDate ?: return@save
        val end = endDate ?: return@save
        onSave(HolidayRange(target.id, name.trim(), start, end, type, target.sourceKey))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.dbgTag("holiday_edit_dialog"),
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
        title = { Text(stringResource(if (isNew) R.string.holiday_add_title else R.string.holiday_edit_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.settings_holiday_dates_scope),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
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
                Column(Modifier.fillMaxWidth().selectableGroup()) {
                    listOf(
                        HolidayManager.TYPE_PUBLIC_HOLIDAY to stringResource(R.string.holiday_type_holiday),
                        HolidayManager.TYPE_TRANSFER_WORKDAY to stringResource(R.string.holiday_type_workday)
                    ).forEach { (value, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(selected = type == value, role = Role.RadioButton, onClick = { type = value }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = type == value, onClick = null)
                            Text(label, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
                if (!datesValid && (startText.isNotBlank() || endText.isNotBlank())) {
                    Text(
                        stringResource(R.string.holiday_date_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error
                    )
                }
                if (!isNew && !nativeActions) {
                    // 弹窗不设"恢复": 已保存段删除=移除覆盖(可从已删除区恢复), 网络段删除=REMOVED 覆盖(同入口恢复);
                    // 弹窗内两个按钮会做同一件事, 恢复入口统一收敛到"已删除"区块。
                    DialogActionButtons(
                        confirmText = stringResource(R.string.holiday_delete_range),
                        onConfirm = { onDelete(target) },
                        destructive = true,
                    )
                }
            }
        },
        confirmButton = {
            if (nativeActions) {
                DialogActionButtons(
                    confirmText = stringResource(R.string.save),
                    onConfirm = saveRange,
                    confirmEnabled = datesValid,
                    dismissText = stringResource(R.string.cancel),
                    onDismiss = onDismiss,
                    thirdText = if (isNew) null else stringResource(R.string.holiday_delete_range),
                    onThird = { onDelete(target) },
                    thirdDestructive = true,
                    dbgTagPrefix = "holiday_edit",
                )
            } else {
                Button(enabled = datesValid, onClick = saveRange) { Text(stringResource(R.string.save)) }
            }
        },
        dismissButton = {
            if (!nativeActions) {
                FilledTonalButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}
