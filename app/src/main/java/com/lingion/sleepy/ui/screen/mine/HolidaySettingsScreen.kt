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
    data class Loaded(val holidays: List<HolidayEntry>, val workdays: List<HolidayEntry>) : HolidayUiState
}

/** 弹窗编辑目标: null=添加模式, 非 null=编辑该日期 */
private data class EditingEntry(val date: LocalDate, val name: String, val type: String)

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
    var editing by remember { mutableStateOf<EditingEntry?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    fun reload() { overrides = AppPrefs.getHolidayRanges(context) }

    fun load(targetYear: Int, force: Boolean = false) {
        loadJob?.cancel()
        loadJob = scope.launch {
            state = HolidayUiState.Loading
            val entries = if (force) {
                HolidayManager.refreshYearEntries(targetYear)
            } else {
                HolidayManager.getYearEntries(targetYear)
            }
            overrides = AppPrefs.getHolidayRanges(context)
            state = when {
                entries.isEmpty() && HolidayManager.isYearFetchFailed(targetYear) -> HolidayUiState.Failed
                else -> HolidayUiState.Loaded(
                    holidays = entries.filter { it.type == HolidayManager.TYPE_PUBLIC_HOLIDAY },
                    workdays = entries.filter { it.type == HolidayManager.TYPE_TRANSFER_WORKDAY }
                )
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
                    Text(
                        text = stringResource(R.string.holiday_data_source),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface
                    )
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
                val rawEntries = loaded.holidays + loaded.workdays
                val merged = HolidayRangeOps.mergeSegments(rawEntries, overrides)
                val mergedEntries = merged.active.flatMap { seg ->
                    buildList {
                        var d = seg.startDate
                        while (!d.isAfter(seg.endDate)) { add(HolidayEntry(d, seg.name, seg.type)); d = d.plusDays(1) }
                    }
                }
                val mergedHolidays = mergedEntries.filter { it.type == HolidayManager.TYPE_PUBLIC_HOLIDAY }
                val mergedWorkdays = mergedEntries.filter { it.type == HolidayManager.TYPE_TRANSFER_WORKDAY }

                if (mergedHolidays.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.holiday_list_holidays)) }
                    item {
                        HolidayEntryListCard(
                            entries = mergedHolidays,
                            showBadge = false,
                            overrides = overrides,
                            onEdit = { editing = EditingEntry(it.date, it.name, it.type) }
                        )
                    }
                }
                if (mergedWorkdays.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.holiday_list_workdays)) }
                    item {
                        HolidayEntryListCard(
                            entries = mergedWorkdays,
                            showBadge = true,
                            overrides = overrides,
                            onEdit = { editing = EditingEntry(it.date, it.name, it.type) }
                        )
                    }
                }
                item {
                    FilledTonalButton(
                        onClick = { showAdd = true },
                        modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.regularHeight),
                        shape = SleepyTheme.Buttons.shape
                    ) { Text(stringResource(R.string.holiday_add_entry)) }
                }
            }
        }
    }

    editing?.let { target ->
        HolidayEntryEditDialog(
            target = target,
            isNew = overrides.none { it.startDate <= target.date && target.date <= it.endDate },
            onDismiss = { editing = null },
            onSave = { date, name, type ->
                val next = overrides.filter { !(it.startDate <= date && date <= it.endDate) }.toMutableList()
                next.add(HolidayRange(HolidayRangeOps.newId(), name, date, date, type, null))
                AppPrefs.setHolidayRanges(context, next)
                reload()
                editing = null
            },
            onRemove = { date ->
                val next = overrides.filter { !(it.startDate <= date && date <= it.endDate) }
                AppPrefs.setHolidayRanges(context, next)
                reload()
                editing = null
            }
        )
    }

    if (showAdd) {
        HolidayEntryEditDialog(
            target = null,
            isNew = true,
            onDismiss = { showAdd = false },
            onSave = { date, name, type ->
                val next = overrides.filter { !(it.startDate <= date && date <= it.endDate) }.toMutableList()
                next.add(HolidayRange(HolidayRangeOps.newId(), name, date, date, type, null))
                AppPrefs.setHolidayRanges(context, next)
                reload()
                showAdd = false
            },
            onRemove = null
        )
    }
}

@Composable
private fun HolidayEntryListCard(
    entries: List<HolidayEntry>,
    showBadge: Boolean,
    overrides: List<HolidayRange>,
    onEdit: (HolidayEntry) -> Unit
) {
    val colors = SleepyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SleepyTheme.shapes.large)
            .background(colors.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        entries.forEachIndexed { index, entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .noRippleClickable { onEdit(entry) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.name.ifBlank { DateUtils.shortDateSlash(entry.date) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (overrides.any { it.type != HolidayRangeOps.REMOVED && it.startDate <= entry.date && entry.date <= it.endDate }) {
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
                if (showBadge) {
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
                Text(DateUtils.shortDateSlash(entry.date), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            if (index != entries.lastIndex) HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
        }
    }
}

/**
 * 编辑/添加弹窗。target=null 为添加模式(日期必填+日期选择器),
 * 否则编辑模式(日期固定展示, 可改名/切类型; onRemove 非空时提供"恢复默认")。
 */
@Composable
private fun HolidayEntryEditDialog(
    target: EditingEntry?,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (date: LocalDate, name: String, type: String) -> Unit,
    onRemove: ((date: LocalDate) -> Unit)?
) {
    val colors = SleepyTheme.colors
    var dateText by remember(target) { mutableStateOf(target?.date?.toString() ?: "") }
    var name by remember(target) { mutableStateOf(target?.name ?: "") }
    var type by remember(target) { mutableStateOf(target?.type ?: HolidayManager.TYPE_PUBLIC_HOLIDAY) }
    val parsedDate = try { LocalDate.parse(dateText) } catch (_: Exception) { null }
    val canSave = parsedDate != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (target == null) R.string.holiday_add_title else R.string.holiday_edit_title), color = colors.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (target == null) {
                    DatePickerField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        label = stringResource(R.string.holiday_name_label_date),
                        isError = dateText.isNotBlank() && parsedDate == null
                    )
                } else {
                    Text(
                        DateUtils.shortDateSlash(target.date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
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
                if (onRemove != null && target != null && !isNew) {
                    TextButton(onClick = { onRemove(target.date) }) {
                        Text(stringResource(R.string.holiday_remove_override), color = colors.primary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = {
                parsedDate?.let { onSave(it, name.trim(), type) }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
