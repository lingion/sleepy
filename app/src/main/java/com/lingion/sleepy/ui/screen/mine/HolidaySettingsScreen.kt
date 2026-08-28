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
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.component.DatePickerField
import com.lingion.sleepy.ui.component.HolidayStyleChip
import com.lingion.sleepy.ui.component.SectionHeader
import com.lingion.sleepy.ui.component.SettingToggleRow
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
fun HolidaySettingsScreen(onBack: () -> Unit) {
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

    fun reload() { overrides = AppPrefs.getHolidayRanges(context) }

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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SleepyTheme.shapes.large)
                        .background(colors.surfaceContainer)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.holiday_data_source),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (state is HolidayUiState.Loaded || state is HolidayUiState.Empty) {
                            Button(
                                onClick = { load(year, force = true) },
                                enabled = state !is HolidayUiState.Loading,
                                modifier = Modifier.height(SleepyTheme.Buttons.regularHeight),
                                shape = SleepyTheme.Buttons.shape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.secondaryContainer,
                                    contentColor = colors.onSecondaryContainer
                                )
                            ) {
                                Text(stringResource(R.string.holiday_data_refresh))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    when (state) {
                        HolidayUiState.Loading -> CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = colors.primary,
                            strokeWidth = 2.dp
                        )
                        HolidayUiState.Failed -> {
                            Text(stringResource(R.string.holiday_data_failed), style = MaterialTheme.typography.bodySmall, color = colors.error)
                            Spacer(Modifier.height(12.dp))
                            FilledTonalButton(
                                onClick = { load(year, force = true) },
                                modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.regularHeight),
                                shape = SleepyTheme.Buttons.shape
                            ) { Text(stringResource(R.string.holiday_data_retry)) }
                        }
                        HolidayUiState.Empty -> Text(stringResource(R.string.holiday_data_empty), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        is HolidayUiState.Loaded -> Text(
                            "unpkg.com/holiday-calendar · CN",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SleepyTheme.shapes.large)
                        .background(colors.surfaceContainer)
                        .padding(16.dp)
                ) {
                    Text(stringResource(R.string.settings_holiday_style), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
                    Text(stringResource(R.string.settings_holiday_style_sub), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HolidayStyleChip(
                            label = stringResource(R.string.settings_holiday_style_grey),
                            selected = style == "grey",
                            onClick = { style = "grey"; AppPrefs.setHolidayStyle(context, "grey") }
                        )
                        HolidayStyleChip(
                            label = stringResource(R.string.settings_holiday_style_strikethrough),
                            selected = style == "strikethrough",
                            onClick = { style = "strikethrough"; AppPrefs.setHolidayStyle(context, "strikethrough") }
                        )
                    }
                }
            }

            val loaded = state as? HolidayUiState.Loaded
            if (loaded != null) {
                // 覆盖变化时基于原始网络数据即时重合并, 不重新走网络
                val merged = HolidayRangeOps.mergeSegments(loaded.entries, overrides)
                val userRangeIds = overrides.map { it.id }.toSet()
                val holidaySegments = merged.active.filter { it.type == HolidayManager.TYPE_PUBLIC_HOLIDAY }
                val workdaySegments = merged.active.filter { it.type == HolidayManager.TYPE_TRANSFER_WORKDAY }

                if (holidaySegments.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.holiday_list_holidays)) }
                    item {
                        HolidayRangeListCard(
                            segments = holidaySegments,
                            userRangeIds = userRangeIds,
                            onEdit = { editing = resolveEditTarget(it, userRangeIds) }
                        )
                    }
                }
                if (workdaySegments.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.holiday_list_workdays)) }
                    item {
                        HolidayRangeListCard(
                            segments = workdaySegments,
                            userRangeIds = userRangeIds,
                            showWorkdayBadge = true,
                            onEdit = { editing = resolveEditTarget(it, userRangeIds) }
                        )
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

/** 段列表卡: 名称 + 自定义 badge(若是用户段) + 补班 badge(可选) + 起止日期; 行点击进入编辑 */
@Composable
private fun HolidayRangeListCard(
    segments: List<HolidayRange>,
    userRangeIds: Set<String>,
    showWorkdayBadge: Boolean = false,
    onEdit: (HolidayRange) -> Unit
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
                    .noRippleClickable { onEdit(segment) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = segment.name.ifBlank { DateUtils.shortDateSlash(segment.startDate) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (segment.id in userRangeIds) {
                    Box(
                        modifier = Modifier
                            .clip(SleepyTheme.shapes.small)
                            .background(colors.onSurfaceVariant.copy(alpha = SleepyTheme.Alpha.tinted))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(stringResource(R.string.holiday_custom_badge), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(12.dp))
                }
                if (showWorkdayBadge) {
                    Box(
                        modifier = Modifier
                            .clip(SleepyTheme.shapes.small)
                            .background(colors.primary.copy(alpha = SleepyTheme.Alpha.tinted))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(stringResource(R.string.holiday_workday_badge), style = MaterialTheme.typography.labelSmall, color = colors.primary)
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Text(segmentDateLabel(segment), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            if (index != segments.lastIndex) HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
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
        title = { Text(stringResource(if (isNew) R.string.holiday_add_title else R.string.holiday_edit_title), color = colors.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DatePickerField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = stringResource(R.string.holiday_name_label_date),
                    isError = startText.isNotBlank() && startDate == null
                )
                DatePickerField(
                    value = endText,
                    onValueChange = { endText = it },
                    label = stringResource(R.string.holiday_name_label_end),
                    isError = endText.isNotBlank() && (endDate == null || (startDate != null && endDate.isBefore(startDate)))
                )
                if (!datesValid && (startText.isNotBlank() || endText.isNotBlank())) {
                    Text(
                        stringResource(R.string.holiday_date_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.holiday_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HolidayStyleChip(
                        label = stringResource(R.string.holiday_type_holiday),
                        selected = type == HolidayManager.TYPE_PUBLIC_HOLIDAY,
                        onClick = { type = HolidayManager.TYPE_PUBLIC_HOLIDAY }
                    )
                    HolidayStyleChip(
                        label = stringResource(R.string.holiday_type_workday),
                        selected = type == HolidayManager.TYPE_TRANSFER_WORKDAY,
                        onClick = { type = HolidayManager.TYPE_TRANSFER_WORKDAY }
                    )
                }
                if (!isNew) {
                    // 删除走 errorContainer 色块 — 纯色块禁描边规则。
                    // 弹窗不设"恢复": 已保存段删除=移除覆盖(可从已删除区恢复), 网络段删除=REMOVED 覆盖(同入口恢复);
                    // 弹窗内两个按钮会做同一件事, 恢复入口统一收敛到"已删除"区块。
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onDelete(target) },
                            modifier = Modifier.weight(1f).height(SleepyTheme.Buttons.regularHeight),
                            shape = SleepyTheme.Buttons.shape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.errorContainer,
                                contentColor = colors.onErrorContainer
                            )
                        ) { Text(stringResource(R.string.holiday_delete_range)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = datesValid, onClick = {
                val start = startDate ?: return@TextButton
                val end = endDate ?: return@TextButton
                // sourceKey 由 resolveEditTarget 填好: 网络段派生=挂接键, 纯用户段=保持 null
                onSave(HolidayRange(target.id, name.trim(), start, end, type, target.sourceKey))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
