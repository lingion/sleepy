package com.lingion.sleepy.ui.screen.imports

import android.net.Uri
import org.json.JSONArray
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.data.parser.ScheduleParser
import com.lingion.sleepy.ui.component.DatePickerField
import com.lingion.sleepy.ui.component.TimeSlotEditor
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.SleepyTheme
import kotlinx.coroutines.launch

/**
 * 导入课表弹窗 — 取代原 ImportScreen 整页
 *
 * 结构（自上而下）：
 *  - 标题栏 "导入课表"
 *  - 教务直连（一行可点）
 *  - 从文本导入（默认折叠，展开后是输入框 + 预览按钮）
 *  - 从文件导入（一行可点，触发系统选择器）
 *  - 支持的导入类型（说明列表）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onJwImportRequested: () -> Unit,
    onImported: () -> Unit,
    onOpenEditTable: (Long) -> Unit,
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val colors = SleepyTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var textExpanded by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var pendingMode by remember { mutableStateOf<ImportApplyMode?>(null) }
    var confirmedStartDate by remember { mutableStateOf("") }
    var confirmedTimeJson by remember { mutableStateOf("") }
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.onSurface,
        unfocusedTextColor = colors.onSurface,
        focusedLabelColor = colors.primary,
        unfocusedLabelColor = colors.onSurfaceVariant,
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = colors.outlineVariant,
        cursorColor = colors.primary,
        focusedContainerColor = colors.surfaceContainerLowest,
        unfocusedContainerColor = colors.surfaceContainerLowest
    )

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isLoading = true
                try {
                    val text = context.contentResolver.openInputStream(it)?.bufferedReader()?.readText()
                        ?: throw Exception(context.getString(R.string.cannot_read_file))
                    preview = buildImportPreview(text, state, context) { msg -> errorMsg = msg }
                    if (preview != null) onDismiss()
                } catch (e: Exception) {
                    errorMsg = context.getString(R.string.read_failed, e.message)
                } finally {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(errorMsg) {
        errorMsg?.let {
            snackbar.showSnackbar(it)
            errorMsg = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 标题
            Text(
                text = stringResource(R.string.import_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = stringResource(R.string.import_preview_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 行 1：教务直连
            ImportMethodRow(
                icon = Icons.Outlined.QrCode2,
                label = stringResource(R.string.import_jw),
                onClick = {
                    onDismiss()
                    onJwImportRequested()
                }
            )

            // 行 2：从文本导入（可折叠）
            ImportMethodRow(
                icon = Icons.Outlined.Description,
                label = stringResource(R.string.import_paste),
                trailing = if (textExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                onClick = { textExpanded = !textExpanded }
            )
            AnimatedVisibility(
                visible = textExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 56.dp, top = 4.dp, bottom = 8.dp, end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        placeholder = { Text(stringResource(R.string.import_paste_hint), color = colors.onSurfaceVariant) },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                try {
                                    val p = buildImportPreview(inputText, state, context) { msg -> errorMsg = msg }
                                    if (p != null) {
                                        preview = p
                                        onDismiss()
                                    }
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        enabled = !isLoading && inputText.isNotBlank(),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Text(
                            text = if (isLoading) stringResource(R.string.import_parsing) else stringResource(R.string.import_preview),
                            color = colors.onPrimary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            // 行 3：从文件导入
            ImportMethodRow(
                icon = Icons.Outlined.FileUpload,
                label = stringResource(R.string.import_file),
                onClick = {
                    filePicker.launch("*/*")
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 支持的导入类型
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceContainer)
                    .padding(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.import_supported_formats),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                FormatRow(stringResource(R.string.format_wakeup_share), stringResource(R.string.format_wakeup_desc))
                FormatRow(stringResource(R.string.format_wakeup_json), stringResource(R.string.format_json_desc))
                FormatRow(stringResource(R.string.format_ics), stringResource(R.string.format_ics_desc))
                FormatRow(stringResource(R.string.format_csv), stringResource(R.string.format_csv_desc))
                FormatRow(stringResource(R.string.format_html), stringResource(R.string.format_html_desc))
                FormatRow(stringResource(R.string.format_plain), stringResource(R.string.format_plain_desc))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // 预览对话框
    preview?.let { currentPreview ->
        ImportPreviewDialog(
            preview = currentPreview,
            onDismiss = { preview = null },
            onApply = { mode ->
                val existingTable = state.currentTable
                confirmedStartDate = currentPreview.parseResult.startDate.ifBlank {
                    existingTable?.startDate ?: java.time.LocalDate.now().toString()
                }
                confirmedTimeJson = existingTable?.timeJson ?: TimeTableUtils.DEFAULT_TIME_JSON
                pendingMode = mode
            }
        )
    }

    if (preview != null && pendingMode != null) {
        ImportConfirmDialog(
            startDate = confirmedStartDate,
            timeJson = confirmedTimeJson,
            onStartDateChange = { confirmedStartDate = it },
            onTimeJsonChange = { confirmedTimeJson = it },
            onDismiss = { pendingMode = null },
            onConfirm = {
                val mode = pendingMode ?: return@ImportConfirmDialog
                val currentPreview = preview ?: return@ImportConfirmDialog
                scope.launch {
                    isLoading = true
                    try {
                        val resultTableId = applyImportPreview(
                            preview = currentPreview,
                            mode = mode,
                            confirmedStartDate = confirmedStartDate,
                            confirmedTimeJson = confirmedTimeJson,
                            context = context,
                            onImported = onImported
                        ) { msg -> errorMsg = msg }
                        preview = null
                        pendingMode = null
                        if (resultTableId != null) {
                            onOpenEditTable(resultTableId)
                        }
                    } finally {
                        isLoading = false
                    }
                }
            }
        )
    }
}

@Composable
private fun ImportMethodRow(
    icon: ImageVector,
    label: String,
    trailing: ImageVector? = null,
    onClick: () -> Unit
) {
    val colors = SleepyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = colors.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        )
        if (trailing != null) {
            Icon(
                imageVector = trailing,
                contentDescription = null,
                tint = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FormatRow(name: String, desc: String) {
    val colors = SleepyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodySmall,
            color = colors.primary,
            modifier = Modifier.padding(end = 8.dp, top = 2.dp)
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = colors.onSurface,
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
    }
}

// --- shared types / dialogs (copied from ImportScreen to keep sheet self-contained) ---

private enum class ImportApplyMode {
    ReplaceCurrent,
    ImportAsNew,
    AppendNonConflict
}

private data class CourseConflict(
    val incoming: CourseEntity,
    val existing: CourseEntity
)

private data class ImportPreview(
    val targetTableId: Long,
    val targetTableName: String,
    val parseResult: ScheduleParser.ParseResult,
    val existingCourses: List<CourseEntity>,
    val conflicts: List<CourseConflict>
) {
    val incomingCount: Int get() = parseResult.courses.size
    val conflictCount: Int get() = conflicts.size
    val cleanCount: Int get() = incomingCount - conflictCount
}

@Composable
private fun ImportPreviewDialog(
    preview: ImportPreview,
    onDismiss: () -> Unit,
    onApply: (ImportApplyMode) -> Unit
) {
    val colors = SleepyTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.import_preview_title), style = MaterialTheme.typography.titleLarge)
                if (preview.targetTableId == 0L) {
                    Text(
                        text = stringResource(R.string.import_new_table_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primary
                    )
                } else {
                    Text(
                        text = stringResource(R.string.import_target_table, preview.targetTableName),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PreviewMetricCard(
                        label = stringResource(R.string.import_courses),
                        value = preview.incomingCount.toString(),
                        bg = colors.primaryContainer,
                        fg = colors.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    if (preview.targetTableId != 0L) {
                        PreviewMetricCard(
                            label = stringResource(R.string.import_conflicts),
                            value = preview.conflictCount.toString(),
                            bg = if (preview.conflictCount > 0) Color(0xFFFFE1DE) else colors.secondaryContainer,
                            fg = if (preview.conflictCount > 0) Color(0xFF8C1D18) else colors.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        PreviewMetricCard(
                            label = stringResource(R.string.import_appendable),
                            value = preview.cleanCount.toString(),
                            bg = colors.tertiaryContainer,
                            fg = colors.onTertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceContainer)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PreviewInfoRow(stringResource(R.string.import_table_name), preview.parseResult.tableName)
                    PreviewInfoRow(stringResource(R.string.import_start_date), preview.parseResult.startDate)
                    if (preview.targetTableId != 0L) {
                        PreviewInfoRow(
                            stringResource(R.string.import_suggestion),
                            when {
                                preview.conflictCount == 0 -> stringResource(R.string.import_no_conflict)
                                else -> stringResource(R.string.import_conflict_count, preview.conflictCount)
                            }
                        )
                    }
                }
                if (preview.conflicts.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfaceContainer)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.import_conflicts),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSurface
                        )
                        preview.conflicts.take(3).forEach { conflict ->
                            Text(
                                text = "• ${conflict.incoming.courseName} ↔ ${conflict.existing.courseName}（${DateUtils.localizedDay(conflict.incoming.day, LocalContext.current)} ${conflict.incoming.shortNodeString(LocalContext.current)}）",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                        if (preview.conflicts.size > 3) {
                            Text(
                                text = stringResource(R.string.import_conflict_more, preview.conflicts.size - 3),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (preview.targetTableId == 0L) {
                    // 没有任何课表时只允许 "作为新课表导入"
                    Button(
                        onClick = { onApply(ImportApplyMode.ImportAsNew) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Text(stringResource(R.string.import_as_new), maxLines = 1)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onApply(ImportApplyMode.AppendNonConflict) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                        ) {
                            Text(stringResource(R.string.import_append_only), maxLines = 1)
                        }
                        Button(
                            onClick = { onApply(ImportApplyMode.ImportAsNew) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                        ) {
                            Text(stringResource(R.string.import_as_new), maxLines = 1)
                        }
                    }
                    OutlinedButton(
                        onClick = { onApply(ImportApplyMode.ReplaceCurrent) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error)
                    ) {
                        Text(stringResource(R.string.import_overwrite))
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cancel), color = colors.onSurfaceVariant)
                }
            }
        },
        dismissButton = {}
    )
}

@Composable
private fun PreviewMetricCard(
    label: String,
    value: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.92f))
        Text(text = value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = fg)
    }
}

@Composable
private fun PreviewInfoRow(label: String, value: String) {
    val colors = SleepyTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
    }
}

@Composable
private fun ImportConfirmDialog(
    startDate: String,
    timeJson: String,
    onStartDateChange: (String) -> Unit,
    onTimeJsonChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val colors = SleepyTheme.colors
    val context = LocalContext.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.onSurface,
        unfocusedTextColor = colors.onSurface,
        focusedLabelColor = colors.primary,
        unfocusedLabelColor = colors.onSurfaceVariant,
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = colors.outlineVariant,
        cursorColor = colors.primary,
        focusedContainerColor = colors.surfaceContainerLowest,
        unfocusedContainerColor = colors.surfaceContainerLowest
    )
    var rows by remember(timeJson) {
        mutableStateOf(TimeTableUtils.parseTimeSlotRows(timeJson))
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(stringResource(R.string.import_confirm_title), color = colors.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.import_confirm_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                DatePickerField(
                    value = startDate,
                    onValueChange = onStartDateChange,
                    label = stringResource(R.string.import_week_start),
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMsg != null
                )
                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = colors.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TimeSlotEditor(
                        rows = rows,
                        onRowsChange = { newRows ->
                            rows = newRows
                            onTimeJsonChange(TimeTableUtils.buildTimeJsonFromRows(newRows))
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (startDate.isBlank()) {
                    errorMsg = context.getString(R.string.import_start_date_required)
                    return@TextButton
                }
                val dateRegex = Regex("""^\d{4}-\d{2}-\d{2}$""")
                if (!dateRegex.matches(startDate)) {
                    errorMsg = context.getString(R.string.start_date_format)
                    return@TextButton
                }
                val emptyRows = rows.filter { it.start.isBlank() || it.end.isBlank() }
                if (emptyRows.isNotEmpty()) {
                    errorMsg = context.getString(R.string.slot_time_required, emptyRows.first().node)
                    return@TextButton
                }
                val timeRegex = Regex("""^\d{2}:\d{2}$""")
                val invalidRows = rows.filter {
                    !timeRegex.matches(it.start) || !timeRegex.matches(it.end) ||
                    it.start >= it.end
                }
                if (invalidRows.isNotEmpty()) {
                    errorMsg = context.getString(R.string.slot_time_invalid, invalidRows.first().node)
                    return@TextButton
                }
                errorMsg = null
                onTimeJsonChange(TimeTableUtils.buildTimeJsonFromRows(rows))
                onConfirm()
            }) {
                Text(stringResource(R.string.import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.back))
            }
        }
    )
}

private suspend fun buildImportPreview(
    text: String,
    state: com.lingion.sleepy.ui.screen.schedule.ScheduleState,
    context: android.content.Context,
    onError: (String) -> Unit
): ImportPreview? {
    if (text.isBlank()) {
        onError(context.getString(R.string.import_content_empty))
        return null
    }
    // selectedTableId 缺失时也能导入 — 没有 tableId 就用 0L，apply 时按 ImportAsNew 自动建表。
    val tableId = state.selectedTableId ?: 0L
    val result = ScheduleParser.parse(text, tableId)
    return result.fold(
        onSuccess = { parseResult ->
            val repo = SleepyApp.get().repository
            val existingTable = if (tableId == 0L) null else repo.getTable(tableId)
            val existingCourses = if (tableId == 0L) emptyList() else repo.getCourses(tableId)
            val conflicts = if (tableId == 0L) emptyList() else parseResult.courses.mapNotNull { incoming ->
                existingCourses.firstOrNull { existing -> coursesConflict(incoming, existing) }
                    ?.let { CourseConflict(incoming = incoming, existing = it) }
            }
            ImportPreview(
                targetTableId = tableId,
                targetTableName = existingTable?.name ?: context.getString(R.string.manage_current_table),
                parseResult = parseResult,
                existingCourses = existingCourses,
                conflicts = conflicts
            )
        },
        onFailure = { e ->
            onError(context.getString(R.string.import_failed, e.message))
            null
        }
    )
}

private suspend fun applyImportPreview(
    preview: ImportPreview,
    mode: ImportApplyMode,
    confirmedStartDate: String,
    confirmedTimeJson: String,
    context: android.content.Context,
    onImported: () -> Unit,
    onError: (String) -> Unit
): Long? {
    val repo = SleepyApp.get().repository
    when (mode) {
        ImportApplyMode.ReplaceCurrent -> {
            val existing = repo.getTable(preview.targetTableId)
            if (existing != null) {
                repo.updateTable(
                    existing.copy(
                        name = preview.parseResult.tableName,
                        startDate = confirmedStartDate,
                        timeJson = confirmedTimeJson
                    )
                )
            }
            repo.replaceCourses(preview.targetTableId, preview.parseResult.courses)
            onImported()
            return preview.targetTableId
        }
        ImportApplyMode.ImportAsNew -> {
            val base = repo.getTable(preview.targetTableId)
            val newTableId = repo.insertTable(
                TimeTableEntity(
                    name = uniqueImportedTableName(preview.parseResult.tableName, repo.getAllTables().map { it.name }, context),
                    startDate = confirmedStartDate,
                    maxWeek = base?.maxWeek ?: 20,
                    nodesPerDay = base?.nodesPerDay ?: 12,
                    timeJson = confirmedTimeJson,
                    color = base?.color ?: "#FF6750A4",
                    isDefault = false
                )
            )
            repo.insertCourses(preview.parseResult.courses.map { it.copy(id = 0, tableId = newTableId) })
            repo.setDefault(newTableId)
            onImported()
            return newTableId
        }
        ImportApplyMode.AppendNonConflict -> {
            val cleanCourses = preview.parseResult.courses.filterNot { incoming ->
                preview.existingCourses.any { existing -> coursesConflict(incoming, existing) }
            }
            if (cleanCourses.isEmpty()) {
                onError(context.getString(R.string.import_all_conflict))
                return null
            }
            repo.insertCourses(cleanCourses.map { it.copy(id = 0, tableId = preview.targetTableId) })
            onImported()
            return preview.targetTableId
        }
    }
}

private fun coursesConflict(a: CourseEntity, b: CourseEntity): Boolean {
    if (a.day != b.day) return false
    if (a.endWeek < b.startWeek || b.endWeek < a.startWeek) return false
    val aStart = a.startNode
    val aEnd = a.startNode + a.step - 1
    val bStart = b.startNode
    val bEnd = b.startNode + b.step - 1
    return aStart <= bEnd && bStart <= aEnd
}

private fun uniqueImportedTableName(base: String, existingNames: List<String>, context: android.content.Context): String {
    val default = context.getString(R.string.default_table_name)
    val effective = base.ifBlank { default }
    if (effective !in existingNames) return effective.ifBlank { "${default}1" }
    var index = 2
    while ("${effective}$index" in existingNames || "${effective}($index)" in existingNames) index++
    return "${effective}$index"
}
