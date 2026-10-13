package com.lingion.sleepy.ui.screen.imports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.SmartPeriodConfig
import com.lingion.sleepy.data.imports.*
import com.lingion.sleepy.data.parser.ScheduleParser
import com.lingion.sleepy.ui.component.DatePickerField
import com.lingion.sleepy.ui.component.TimeSlotEditor
import com.lingion.sleepy.ui.component.decodeSmartPeriodConfig
import com.lingion.sleepy.ui.component.resolveAutoPeriodConfig
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
internal fun ImportDecisionSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow,
        SleepyTheme.shapes.large).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
internal fun ImportDecisionChoice(label: String, selected: Boolean, onSelect: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, description: String? = null) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).clip(SleepyTheme.shapes.medium)
        .background(if (selected) colors.secondaryContainer else colors.surfaceContainerHighest)
        .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
        .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(label, color = if (selected) colors.onSecondaryContainer else colors.onSurface)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant) }
        }
    }
}

@Composable
internal fun ImportDecisionToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).clip(SleepyTheme.shapes.medium)
        .toggleable(checked, role = Role.Checkbox, onValueChange = onChange).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = null)
        Text(label, Modifier.weight(1f).padding(start = 12.dp))
    }
}

@Composable
internal fun ImportDecisionSecondaryButton(label: String, onClick: () -> Unit, enabled: Boolean = true,
    modifier: Modifier = Modifier) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled,
        shape = SleepyTheme.shapes.medium, colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) { Text(label) }
}

@Composable
private fun ImportDecisionPicker(label: String, current: String?, options: List<Pair<Long, String>>,
    onSelect: (Long) -> Unit, tag: String) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            ImportDecisionSecondaryButton(current ?: stringResource(R.string.id_choose),
                { expanded = true }, options.isNotEmpty(), Modifier.testTag(tag))
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (id, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onSelect(id) },
                        modifier = Modifier.heightIn(min = 48.dp))
                }
            }
        }
        if (options.isEmpty()) Text(stringResource(R.string.id_no_tables))
    }
}

@Composable
internal fun ImportDecisionDestinationSection(source: ScheduleParser.ParseResult, snapshot: ImportSnapshot,
    configuration: ImportConfiguration, onChange: (ImportConfiguration) -> Unit) {
    val defaultName = stringResource(R.string.id_default_table)
    val defaultPeriod = stringResource(R.string.id_default_period)
    ImportDecisionSection(stringResource(R.string.id_destination)) {
        Text(stringResource(R.string.id_preset_help), style = MaterialTheme.typography.bodySmall)
        ImportPreset.entries.forEach { preset ->
            ImportDecisionSecondaryButton(stringResource(when (preset) {
                ImportPreset.AddToExisting -> R.string.id_preset_add
                ImportPreset.ReplaceExistingCourses -> R.string.id_preset_replace
                ImportPreset.NewWithImportedCourses -> R.string.id_preset_new
            }), { onChange(applyImportDecisionPreset(preset, configuration, source, snapshot, defaultName, defaultPeriod)) },
                enabled = preset == ImportPreset.NewWithImportedCourses || snapshot.tables.isNotEmpty(),
                modifier = Modifier.testTag("import-preset-${preset.name}"))
        }
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ImportDestination.entries.forEach { destination ->
                ImportDecisionChoice(stringResource(if (destination == ImportDestination.Existing) R.string.id_existing else R.string.id_new),
                    configuration.destination == destination,
                    { onChange(changeImportDecisionDestination(destination, configuration, source, snapshot, defaultName, defaultPeriod)) },
                    Modifier.testTag("import-destination-${destination.name}"),
                    enabled = destination == ImportDestination.New || snapshot.tables.isNotEmpty())
            }
        }
        if (configuration.destination == ImportDestination.Existing || configuration.content == ImportContent.Merge ||
            configuration.scheduleMode == ScheduleMode.KeepBase || configuration.scheduleMode == ScheduleMode.Merge) {
            ImportDecisionPicker(stringResource(if (configuration.destination == ImportDestination.Existing)
                R.string.id_target_table else R.string.id_copy_table),
                snapshot.tables.firstOrNull { it.id == configuration.baseTableId }?.name,
                snapshot.tables.map { it.id to it.name },
                { onChange(changeImportDecisionBase(it, configuration, source, snapshot, defaultName, defaultPeriod)) }, "import-base")
        }
        Text(stringResource(R.string.id_content), style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ImportContent.entries.forEach { content ->
                ImportDecisionChoice(stringResource(if (content == ImportContent.Merge) R.string.id_merge_courses else R.string.id_import_only),
                    configuration.content == content,
                    { onChange(changeImportDecisionContent(content, configuration)) },
                    Modifier.testTag("import-content-${content.name}"),
                    description = if (content == ImportContent.ImportOnly && configuration.destination == ImportDestination.Existing)
                        stringResource(R.string.id_import_only_warning) else null)
            }
        }
    }
}

@Composable
internal fun ImportDecisionPolicySection(configuration: ImportConfiguration, onChange: (ImportConfiguration) -> Unit) {
    ImportDecisionSection(stringResource(R.string.id_policies)) {
        Text(stringResource(R.string.id_duplicates), style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DuplicatePolicy.entries.forEach { policy ->
                ImportDecisionChoice(stringResource(if (policy == DuplicatePolicy.Skip) R.string.id_duplicate_skip else R.string.id_duplicate_keep),
                    configuration.duplicates == policy, { onChange(configuration.copy(duplicates = policy)) },
                    Modifier.testTag("import-duplicates-${policy.name}"))
            }
        }
        Text(stringResource(if (configuration.content == ImportContent.Merge) R.string.id_overlaps else R.string.id_source_decisions), style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val overlapPolicies = if (configuration.content == ImportContent.Merge) OverlapPolicy.entries
                else listOf(OverlapPolicy.KeepBoth, OverlapPolicy.PerItem)
            overlapPolicies.forEach { policy ->
                ImportDecisionChoice(stringResource(when (policy) {
                    OverlapPolicy.SkipIncoming -> R.string.id_overlap_skip
                    OverlapPolicy.ReplaceExisting -> R.string.id_overlap_replace
                    OverlapPolicy.KeepBoth -> if (configuration.content == ImportContent.ImportOnly) R.string.id_item_keep else R.string.id_overlap_keep
                    OverlapPolicy.PerItem -> R.string.id_overlap_item
                }), configuration.overlaps == policy,
                    { onChange(configuration.copy(overlaps = policy, itemOverrides = emptyMap())) },
                    Modifier.testTag("import-overlaps-${policy.name}"))
            }
        }
        Text(stringResource(R.string.id_internal_help), style = MaterialTheme.typography.bodySmall)
        if (configuration.content == ImportContent.Merge && (configuration.overlaps == OverlapPolicy.ReplaceExisting || configuration.overlaps == OverlapPolicy.PerItem)) {
            Text(stringResource(R.string.id_replacement), style = MaterialTheme.typography.titleSmall)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReplacementScope.entries.forEach { replacement ->
                    ImportDecisionChoice(stringResource(if (replacement == ReplacementScope.OverlapWeeks) R.string.id_overlap_weeks else R.string.id_whole_row),
                        configuration.replacementScope == replacement, { onChange(configuration.copy(replacementScope = replacement)) },
                        description = if (replacement == ReplacementScope.OverlapWeeks) stringResource(R.string.id_overlap_weeks_help) else null)
                }
            }
        }
    }
}

@Composable
internal fun ImportDecisionMetadataSection(source: ScheduleParser.ParseResult, snapshot: ImportSnapshot,
    configuration: ImportConfiguration, plan: ImportPlan?, onChange: (ImportConfiguration) -> Unit) {
    val base = importDecisionBase(configuration, snapshot)
    var expanded by rememberSaveable { mutableStateOf(false) }
    val needsAttention = importDecisionMetadataNeedsAttention(plan?.issues.orEmpty())
    LaunchedEffect(needsAttention) { if (needsAttention) expanded = true }
    val title = stringResource(R.string.id_metadata)
    ImportDecisionSection(title) {
        Text(stringResource(R.string.id_final_table, configuration.name,
            importDecisionMonday(configuration.startDate) ?: configuration.startDate, configuration.maxWeek))
        ImportDecisionSecondaryButton(stringResource(if (expanded) R.string.id_hide_detail else R.string.id_show_detail, title),
            { expanded = !expanded }, modifier = Modifier.testTag("import-metadata-details"))
        if (!expanded) return@ImportDecisionSection
        TextField(configuration.name, { onChange(configuration.copy(name = it)) },
            Modifier.fillMaxWidth().testTag("import-name"), label = { Text(stringResource(R.string.id_name)) },
            colors = SleepyTheme.fieldColors(), shape = SleepyTheme.fieldShape)
        if (source.tableName.isNotBlank()) ImportDecisionSecondaryButton(stringResource(R.string.id_source_value, source.tableName), {
            val name = if (configuration.destination == ImportDestination.New) TimeTableUtils.suggestUniqueName(source.tableName,
                snapshot.tables.map { it.name }, snapshot.periodTables.map { it.name }) else source.tableName
            onChange(configuration.copy(name = name))
        })
        base?.let { ImportDecisionSecondaryButton(stringResource(R.string.id_base_value, it.name), {
            val name = if (configuration.destination == ImportDestination.New) TimeTableUtils.suggestUniqueName(it.name,
                snapshot.tables.map { table -> table.name }, snapshot.periodTables.map { period -> period.name }) else it.name
            onChange(configuration.copy(name = name))
        }) }
        DatePickerField(configuration.startDate,
            { onChange(configuration.copy(startDate = it, dateInterpretationAcknowledged = false)) },
            stringResource(R.string.id_date), Modifier.fillMaxWidth(), isError = importDecisionMonday(configuration.startDate) == null)
        if (source.startDate.isNotBlank()) ImportDecisionSecondaryButton(stringResource(R.string.id_source_value, source.startDate), {
            onChange(configuration.copy(startDate = source.startDate, dateInterpretationAcknowledged = false))
        })
        base?.let { ImportDecisionSecondaryButton(stringResource(R.string.id_base_value, it.startDate), {
            onChange(configuration.copy(startDate = it.startDate, dateInterpretationAcknowledged = false))
        }) }
        val finalMonday = importDecisionMonday(configuration.startDate)
        if (finalMonday != null) {
            Text(stringResource(R.string.id_final_date, finalMonday))
            if (configuration.startDate != finalMonday) Text(stringResource(R.string.id_normalized_date))
            Text(stringResource(R.string.id_week_mapping, finalMonday), style = MaterialTheme.typography.bodySmall)
            ImportDecisionToggle(stringResource(R.string.id_date_ack), configuration.dateInterpretationAcknowledged,
                { onChange(configuration.copy(dateInterpretationAcknowledged = it)) }, Modifier.testTag("import-date-ack"))
        }
        TextField(if (configuration.maxWeek > 0) configuration.maxWeek.toString() else "",
            { text -> if (text.all(Char::isDigit)) onChange(configuration.copy(maxWeek = text.toIntOrNull() ?: 0)) },
            Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.id_max_week)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = configuration.maxWeek < 1, colors = SleepyTheme.fieldColors(), shape = SleepyTheme.fieldShape)
        if (source.maxWeek > 0) ImportDecisionSecondaryButton(stringResource(R.string.id_source_value, source.maxWeek.toString()), {
            onChange(configuration.copy(maxWeek = source.maxWeek))
        })
        base?.let { ImportDecisionSecondaryButton(stringResource(R.string.id_base_value, it.maxWeek.toString()), {
            onChange(configuration.copy(maxWeek = it.maxWeek))
        }) }
        ImportDecisionToggle(stringResource(R.string.id_default), configuration.isDefault,
            { onChange(configuration.copy(isDefault = it)) })
    }
}

@Composable
internal fun ImportDecisionScheduleSection(source: ScheduleParser.ParseResult, snapshot: ImportSnapshot,
    configuration: ImportConfiguration, plan: ImportPlan?, onChange: (ImportConfiguration) -> Unit) {
    val base = importDecisionBase(configuration, snapshot)
    val incoming = remember(source) { importDecisionIncomingSchedule(source) }
    val defaultPeriodName = stringResource(R.string.id_default_period)
    var expanded by rememberSaveable { mutableStateOf(false) }
    val needsAttention = importDecisionScheduleNeedsAttention(plan?.issues.orEmpty())
    LaunchedEffect(needsAttention) { if (needsAttention) expanded = true }
    val title = stringResource(R.string.id_schedule)
    ImportDecisionSection(title) {
        Text(stringResource(when (configuration.scheduleMode) {
            ScheduleMode.KeepBase -> R.string.id_schedule_base
            ScheduleMode.UseIncoming -> R.string.id_schedule_source
            ScheduleMode.Merge -> R.string.id_schedule_merge
            ScheduleMode.Edit -> R.string.id_schedule_edit
            ScheduleMode.BindExisting -> R.string.id_schedule_bind
            ScheduleMode.CreateNew -> R.string.id_schedule_new
        }))
        val effectiveJson = plan?.finalPeriodTable?.timeJson ?: plan?.finalTable?.timeJson ?: configuration.timeJson
        val rows = importDecisionSlots(effectiveJson).orEmpty()
        val periodName = plan?.finalPeriodTable?.name ?: when (configuration.scheduleMode) {
            ScheduleMode.KeepBase -> snapshot.periodTables.firstOrNull { it.id == base?.periodTableId }?.name
            ScheduleMode.BindExisting -> snapshot.periodTables.firstOrNull { it.id == configuration.selectedPeriodTableId }?.name
            else -> configuration.periodName
        }
        Text(stringResource(R.string.id_schedule_summary, periodName?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.id_missing), rows.count { it.edgeClass == null },
            rows.firstOrNull()?.start?.takeIf { it.isNotBlank() } ?: stringResource(R.string.id_missing),
            rows.lastOrNull()?.end?.takeIf { it.isNotBlank() } ?: stringResource(R.string.id_missing)))
        ImportDecisionSecondaryButton(stringResource(if (expanded) R.string.id_hide_detail else R.string.id_show_detail, title),
            { expanded = !expanded }, modifier = Modifier.testTag("import-schedule-details"))
        if (!expanded) return@ImportDecisionSection
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ScheduleMode.entries.forEach { mode ->
                val enabled = when (mode) {
                    ScheduleMode.KeepBase -> base != null
                    ScheduleMode.UseIncoming -> incoming != null
                    ScheduleMode.Merge -> incoming != null && base != null
                    ScheduleMode.BindExisting -> snapshot.periodTables.isNotEmpty()
                    else -> true
                }
                ImportDecisionChoice(stringResource(when (mode) {
                    ScheduleMode.KeepBase -> R.string.id_schedule_base
                    ScheduleMode.UseIncoming -> R.string.id_schedule_source
                    ScheduleMode.Merge -> R.string.id_schedule_merge
                    ScheduleMode.Edit -> R.string.id_schedule_edit
                    ScheduleMode.BindExisting -> R.string.id_schedule_bind
                    ScheduleMode.CreateNew -> R.string.id_schedule_new
                }), configuration.scheduleMode == mode, {
                    val currentJson = plan?.finalPeriodTable?.timeJson ?: plan?.finalTable?.timeJson ?: configuration.timeJson
                    val currentNodes = plan?.finalPeriodTable?.nodesPerDay ?: plan?.finalTable?.nodesPerDay ?: configuration.nodesPerDay
                    val newOwner = mode == ScheduleMode.Edit || mode == ScheduleMode.CreateNew || mode == ScheduleMode.UseIncoming || mode == ScheduleMode.Merge
                    onChange(configuration.copy(scheduleMode = mode, sharedScope = SharedScope.ThisTable,
                        selectedPeriodTableId = if (mode == ScheduleMode.BindExisting) configuration.selectedPeriodTableId else null,
                        periodName = if (newOwner) suggestImportDecisionPeriodName(configuration, source, snapshot, defaultPeriodName) else configuration.periodName,
                        timeJson = if (mode == ScheduleMode.UseIncoming) incoming!!.timeJson else currentJson,
                        nodesPerDay = if (mode == ScheduleMode.UseIncoming) incoming!!.nodesPerDay else currentNodes,
                        smartConfigJson = if (mode == ScheduleMode.KeepBase) base?.smartConfigJson.orEmpty() else "",
                        itemOverrides = emptyMap()))
                }, Modifier.testTag("import-schedule-${mode.name}"), enabled)
            }
        }
        if (incoming == null) Text(stringResource(R.string.id_no_source_times), style = MaterialTheme.typography.bodySmall)
        if (configuration.scheduleMode == ScheduleMode.BindExisting) {
            ImportDecisionPicker(stringResource(R.string.id_bind_picker),
                snapshot.periodTables.firstOrNull { it.id == configuration.selectedPeriodTableId }?.name,
                snapshot.periodTables.map { it.id to it.name },
                { onChange(configuration.copy(selectedPeriodTableId = it, itemOverrides = emptyMap())) }, "import-period-picker")
            Text(stringResource(R.string.id_bind_help), style = MaterialTheme.typography.bodySmall)
        }
        if (configuration.scheduleMode == ScheduleMode.Edit && base?.periodTableId != null) {
            Text(stringResource(R.string.id_scope), style = MaterialTheme.typography.titleSmall)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SharedScope.entries.forEach { scope ->
                    ImportDecisionChoice(stringResource(if (scope == SharedScope.ThisTable) R.string.id_scope_table else R.string.id_scope_shared),
                        configuration.sharedScope == scope, {
                            val periodName = if (scope == SharedScope.Shared)
                                snapshot.periodTables.firstOrNull { it.id == base.periodTableId }?.name.orEmpty()
                            else suggestImportDecisionPeriodName(configuration, source, snapshot, defaultPeriodName)
                            onChange(configuration.copy(sharedScope = scope, periodName = periodName))
                        }, Modifier.testTag("import-scope-${scope.name}"))
                }
            }
            val affected = importDecisionAffectedTables(configuration, snapshot)
            if (affected.isNotEmpty()) Text(stringResource(R.string.id_affected_tables, affected.joinToString(", ")),
                color = MaterialTheme.colorScheme.error)
        }
        if (configuration.scheduleMode in setOf(ScheduleMode.UseIncoming, ScheduleMode.Merge, ScheduleMode.Edit, ScheduleMode.CreateNew)) {
            TextField(configuration.periodName, { onChange(configuration.copy(periodName = it)) }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.id_period_name)) }, colors = SleepyTheme.fieldColors(), shape = SleepyTheme.fieldShape)
        }
        if (configuration.scheduleMode == ScheduleMode.Edit || configuration.scheduleMode == ScheduleMode.CreateNew) {
            val rows = remember(configuration.timeJson) { importDecisionSlots(configuration.timeJson).orEmpty() }
            val smart = remember(configuration.smartConfigJson, rows) {
                val stored = decodeSmartPeriodConfig(configuration.smartConfigJson)
                resolveAutoPeriodConfig(rows, stored) ?: stored ?: SmartPeriodConfig()
            }
            Text(stringResource(R.string.id_editor_help), style = MaterialTheme.typography.bodySmall)
            TimeSlotEditor(rows, onRowsChange = { edited ->
                onChange(configuration.copy(timeJson = TimeTableUtils.buildTimeJsonFromRows(edited),
                    nodesPerDay = edited.filter { it.edgeClass == null }.maxOfOrNull { it.node } ?: 0,
                    itemOverrides = emptyMap()))
            }, smartConfig = smart, onSmartConfigChange = { config ->
                onChange(configuration.copy(smartConfigJson = Json.encodeToString(config)))
            })
        }
        Text(stringResource(R.string.id_final_schedule), style = MaterialTheme.typography.titleSmall)
        rows.forEach { row ->
            Text(stringResource(R.string.id_slot, row.node, row.start.ifBlank { stringResource(R.string.id_missing) },
                row.end.ifBlank { stringResource(R.string.id_missing) }))
        }
    }
}
