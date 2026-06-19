package com.lingion.sleepy.ui.screen.imports

import android.net.Uri
import org.json.JSONArray
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.data.parser.ScheduleParser
import com.lingion.sleepy.ui.component.DatePickerField
import com.lingion.sleepy.ui.component.TimePickerField
import com.lingion.sleepy.ui.component.TimeSlotEditor
import com.lingion.sleepy.ui.screen.schedule.ScheduleState
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.SleepyTheme
import kotlinx.coroutines.launch

@Composable
fun ImportScreen(
    onImported: () -> Unit,
    onBack: () -> Unit = {},
    onManualAdd: () -> Unit = {},
    onOpenEditTable: (Long) -> Unit = {},
    onJwImportRequested: () -> Unit = {},
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var pendingMode by remember { mutableStateOf<ImportApplyMode?>(null) }
    var confirmedStartDate by remember { mutableStateOf("") }
    var confirmedTimeJson by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val colors = SleepyTheme.colors
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
                        ?: throw Exception("无法读取文件")
                    preview = buildImportPreview(text, state) { msg -> errorMsg = msg }
                } catch (e: Exception) {
                    errorMsg = "读取失败: ${e.message}"
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = colors.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Text(
                        text = stringResource(R.string.import_title),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium),
                        color = colors.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "先预览，再导入，不让课表被一把覆盖",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ImportMethodChip(
                        icon = Icons.Outlined.FileUpload,
                        label = stringResource(R.string.import_file),
                        modifier = Modifier.weight(1f),
                        onClick = { filePicker.launch("*/*") }
                    )
                    ImportMethodChip(
                        icon = Icons.Outlined.QrCode2,
                        label = stringResource(R.string.import_jw),
                        modifier = Modifier.weight(1f),
                        onClick = onJwImportRequested
                    )
                    ImportMethodChip(
                        icon = Icons.Outlined.Edit,
                        label = stringResource(R.string.import_manual),
                        modifier = Modifier.weight(1f),
                        onClick = onManualAdd
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surfaceContainer)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.import_paste),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurface,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        placeholder = { Text("粘贴课表文本…", color = colors.onSurfaceVariant) },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                try {
                                    preview = buildImportPreview(inputText, state) { msg -> errorMsg = msg }
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = !isLoading && inputText.isNotBlank(),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Text(
                            text = if (isLoading) "解析中…" else "预览导入",
                            color = colors.onPrimary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surfaceContainer)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "支持格式",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FormatRow("WakeUp 分享文本", "【来自WakeUp课程表】开头")
                    FormatRow("WakeUp JSON", "导出文件 .json")
                    FormatRow("ICS 日历", "可从学校教务处导出")
                    FormatRow("CSV 文件", "含表头的 .csv，逗号分隔")
                    FormatRow("HTML 表格", "<table> 形式，识别表头后逐行解析")
                    FormatRow("纯文本", "一行一课，制表符分隔")
                }
            }
        }
    }

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
private fun ImportMethodChip(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = SleepyTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainer)
            .padding(vertical = 16.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurface
        )
    }
}

@Composable
private fun FormatRow(name: String, desc: String) {
    val colors = SleepyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.primary,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.onSurface,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
    }
}

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
                Text("导入预览", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "目标课表：${preview.targetTableName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PreviewMetricCard(
                        label = "导入课程",
                        value = preview.incomingCount.toString(),
                        bg = colors.primaryContainer,
                        fg = colors.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    PreviewMetricCard(
                        label = "冲突课程",
                        value = preview.conflictCount.toString(),
                        bg = if (preview.conflictCount > 0) Color(0xFFFFE1DE) else colors.secondaryContainer,
                        fg = if (preview.conflictCount > 0) Color(0xFF8C1D18) else colors.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    PreviewMetricCard(
                        label = "可追加",
                        value = preview.cleanCount.toString(),
                        bg = colors.tertiaryContainer,
                        fg = colors.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceContainer)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PreviewInfoRow("导入后课表名", preview.parseResult.tableName)
                    PreviewInfoRow("学期开始日期", preview.parseResult.startDate)
                    PreviewInfoRow(
                        "建议",
                        when {
                            preview.conflictCount == 0 -> "没有冲突，可直接追加或覆盖"
                            else -> "发现 ${preview.conflictCount} 门冲突课程。建议导入为新课表，或仅追加无冲突部分。"
                        }
                    )
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
                            text = "冲突预览",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSurface
                        )
                        preview.conflicts.take(3).forEach { conflict ->
                            Text(
                                text = "• ${conflict.incoming.courseName} ↔ ${conflict.existing.courseName}（周${conflict.incoming.day} ${conflict.incoming.shortNodeString}）",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                        if (preview.conflicts.size > 3) {
                            Text(
                                text = "还有 ${preview.conflicts.size - 3} 条冲突未展开",
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
                        Text("仅追加无冲突", maxLines = 1)
                    }
                    Button(
                        onClick = { onApply(ImportApplyMode.ImportAsNew) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Text("导入为新课表", maxLines = 1)
                    }
                }
                OutlinedButton(
                    onClick = { onApply(ImportApplyMode.ReplaceCurrent) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error)
                ) {
                    Text("⚠ 覆盖当前（谨慎）")
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("取消", color = colors.onSurfaceVariant)
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
        title = { Text("导入前确认", color = colors.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "先确认学期开始日期和每节时间，不然当前周次会一直不准。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                DatePickerField(
                    value = startDate,
                    onValueChange = onStartDateChange,
                    label = "第一周从哪天开始",
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
                    modifier = Modifier.fillMaxWidth().height(260.dp),
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
                // Validate
                if (startDate.isBlank()) {
                    errorMsg = "请填写学期开始日期"
                    return@TextButton
                }
                val dateRegex = Regex("""^\d{4}-\d{2}-\d{2}$""")
                if (!dateRegex.matches(startDate)) {
                    errorMsg = "日期格式应为 YYYY-MM-DD"
                    return@TextButton
                }
                val emptyRows = rows.filter { it.start.isBlank() || it.end.isBlank() }
                if (emptyRows.isNotEmpty()) {
                    errorMsg = "第${emptyRows.first().node}节时间不能为空"
                    return@TextButton
                }
                val timeRegex = Regex("""^\d{2}:\d{2}$""")
                val invalidRows = rows.filter {
                    !timeRegex.matches(it.start) || !timeRegex.matches(it.end) ||
                    it.start >= it.end
                }
                if (invalidRows.isNotEmpty()) {
                    errorMsg = "第${invalidRows.first().node}节时间无效（开始须早于结束）"
                    return@TextButton
                }
                errorMsg = null
                onTimeJsonChange(TimeTableUtils.buildTimeJsonFromRows(rows))
                onConfirm()
            }) {
                Text("确认导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("返回")
            }
        }
    )
}

// TimeConfirmRow 已被迁移到 TimeTableUtils.TimeSlotRow

private suspend fun buildImportPreview(
    text: String,
    state: ScheduleState,
    onError: (String) -> Unit
): ImportPreview? {
    if (text.isBlank()) {
        onError("内容为空")
        return null
    }
    val tableId = state.selectedTableId
    if (tableId == null) {
        onError("请先选择课表")
        return null
    }

    val result = ScheduleParser.parse(text, tableId)
    return result.fold(
        onSuccess = { parseResult ->
            val repo = SleepyApp.get().repository
            val existingTable = repo.getTable(tableId)
            val existingCourses = repo.getCourses(tableId)
            val conflicts = parseResult.courses.mapNotNull { incoming ->
                existingCourses.firstOrNull { existing -> coursesConflict(incoming, existing) }
                    ?.let { CourseConflict(incoming = incoming, existing = it) }
            }
            ImportPreview(
                targetTableId = tableId,
                targetTableName = existingTable?.name ?: "当前课表",
                parseResult = parseResult,
                existingCourses = existingCourses,
                conflicts = conflicts
            )
        },
        onFailure = { e ->
            onError("解析失败: ${e.message}")
            null
        }
    )
}

private suspend fun applyImportPreview(
    preview: ImportPreview,
    mode: ImportApplyMode,
    confirmedStartDate: String,
    confirmedTimeJson: String,
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
                    name = uniqueImportedTableName(preview.parseResult.tableName, repo.getAllTables().map { it.name }),
                    startDate = confirmedStartDate,
                    maxWeek = base?.maxWeek ?: 20,
                    nodesPerDay = base?.nodesPerDay ?: 12,
                    timeJson = confirmedTimeJson,
                    color = base?.color ?: "#FF6750A4",
                    isDefault = false
                )
            )
            repo.insertCourses(preview.parseResult.courses.map { it.copy(id = 0, tableId = newTableId) })
            repo.setDefault(newTableId)  // 自动切换到新导入的课表
            onImported()
            return newTableId
        }

        ImportApplyMode.AppendNonConflict -> {
            val cleanCourses = preview.parseResult.courses.filterNot { incoming ->
                preview.existingCourses.any { existing -> coursesConflict(incoming, existing) }
            }
            if (cleanCourses.isEmpty()) {
                onError("没有可追加课程，全部与现有课表冲突")
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

private fun uniqueImportedTableName(base: String, existingNames: List<String>): String {
    val effective = base.ifBlank { "默认" }
    if (effective !in existingNames) return effective.ifBlank { "默认1" }
    var index = 2
    while ("${effective}$index" in existingNames || "${effective}($index)" in existingNames) index++
    return "${effective}$index"
}
