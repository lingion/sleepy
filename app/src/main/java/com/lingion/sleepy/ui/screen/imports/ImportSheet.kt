package com.lingion.sleepy.ui.screen.imports

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.net.Uri
import com.lingion.sleepy.BuildConfig
import org.json.JSONArray
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
import com.lingion.sleepy.ui.theme.noRippleClickable
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
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val colors = SleepyTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var textExpanded by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var detailFormat by remember { mutableStateOf<ImportFormat?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var pendingMode by remember { mutableStateOf<ImportApplyMode?>(null) }
    var confirmedTableName by remember { mutableStateOf("") }
    var confirmedStartDate by remember { mutableStateOf("") }
    var confirmedTimeJson by remember { mutableStateOf("") }
    var importJustApplied by remember { mutableStateOf(false) }
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }

    // 外部 app (文件管理器 / 其他课表 app) 通过 Intent 打开 json 时,
    // MainActivity 已把课表文本挂到 companion.pendingImportText;
    // 这里读到则自动触发 paste 路径 buildImportPreview, 弹预览对话框。
    // 一次性消费: 读完即清空 companion 字段。
    // 用 pendingImportText 引用做 key, 这样 ImportReceiverActivity 后续塞 text 进来会重新触发
    androidx.compose.runtime.LaunchedEffect(com.lingion.sleepy.MainActivity.pendingImportText) {
        val text = com.lingion.sleepy.MainActivity.pendingImportText
        if (!text.isNullOrBlank()) {
            com.lingion.sleepy.MainActivity.pendingImportText = null
            isLoading = true
            try {
                val p = buildImportPreview(text, state, context) { msg -> errorMsg = msg }
                if (p != null) preview = p
            } catch (e: Throwable) {
                android.util.Log.e("Sleepy", "pending import preview failed", e)
            } finally {
                isLoading = false
            }
        }
    }

    // 仅 debug: 监听 SharedPreferences 里 "debug_import_text" key, 若非空则自动触发 paste 路径 buildImportPreview
    // 用于 adb 自动化验证 (不需要 UI 点击): run-as com.lingion.sleepy.debug sh -c 'cat > shared_prefs/debug_import.xml <<EOF ... EOF'
    if (BuildConfig.DEBUG) {
        LaunchedEffect(Unit) {
            val ctx = context.applicationContext
            val prefs = ctx.getSharedPreferences("debug_import", Context.MODE_PRIVATE)
            val text = prefs.getString("pending_text", null)
            if (!text.isNullOrBlank()) {
                prefs.edit().remove("pending_text").apply()
                isLoading = true
                try {
                    val p = buildImportPreview(text, state, context) { msg -> errorMsg = msg }
                    if (p != null) preview = p
                } finally {
                    isLoading = false
                }
            }
        }
    }

    val fieldColors = SleepyTheme.fieldColors()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isLoading = true
                try {
                    val text = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() }
                        ?: throw Exception(context.getString(R.string.cannot_read_file))
                    preview = buildImportPreview(text, state, context) { msg -> errorMsg = msg }
                    // 注意: 不要在这里 onDismiss() —— sheet 关掉后 preview state 会随之销毁, dialog 永远不弹。
                    // preview != null 时 ImportPreviewDialog 会在 sheet 之上显示; 用户点确认/取消后再清 state。
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
    ) {
        BoxWithConstraints {
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
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        placeholder = { Text(stringResource(R.string.import_paste_hint), color = colors.onSurfaceVariant) },
                        enabled = !isLoading,
                        shape = SleepyTheme.fieldShape,
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
                                        // 不要 onDismiss() —— dialog 叠在 sheet 上显示; 用户在 dialog 操作完后再关 sheet。
                                    }
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.regularHeight),
                        enabled = !isLoading && inputText.isNotBlank(),
                        shape = SleepyTheme.Buttons.shape,
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
                    // OpenDocument() 接受 MIME 数组, 让 picker 只显示 json / 文本文件
                    filePicker.launch(arrayOf("application/json", "text/plain", "text/csv", "text/html", "*/*"))
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 支持的导入类型
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SleepyTheme.shapes.large)
                    .background(colors.surfaceContainer)
                    .padding(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.import_supported_formats),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                FormatRow(
                    name = stringResource(R.string.format_wakeup_share),
                    desc = stringResource(R.string.format_wakeup_desc),
                    onDetail = { detailFormat = ImportFormat.WAKEUP_SHARE }
                )
                FormatRow(
                    name = stringResource(R.string.format_wakeup_json),
                    desc = stringResource(R.string.format_json_desc),
                    onDetail = { detailFormat = ImportFormat.WAKEUP_JSON }
                )
                FormatRow(
                    name = stringResource(R.string.format_ics),
                    desc = stringResource(R.string.format_ics_desc),
                    onDetail = { detailFormat = ImportFormat.ICS }
                )
                FormatRow(
                    name = stringResource(R.string.format_csv),
                    desc = stringResource(R.string.format_csv_desc),
                    onDetail = { detailFormat = ImportFormat.CSV }
                )
                FormatRow(
                    name = stringResource(R.string.format_html),
                    desc = stringResource(R.string.format_html_desc),
                    onDetail = { detailFormat = ImportFormat.HTML }
                )
                FormatRow(
                    name = stringResource(R.string.format_plain),
                    desc = stringResource(R.string.format_plain_desc),
                    onDetail = { detailFormat = ImportFormat.PLAIN }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // 错误反馈通道: 上面 errorMsg → snackbar.showSnackbar 依赖此 host,
        // 之前 sheet 内无 host → 导入失败提示被静默吞掉。默认 M3 配色, 与其余 5 处一致。
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        // 导入成功提示: 不再跳编辑课表页(假保存闸), 用 snackbar 明示已落库
        LaunchedEffect(preview, pendingMode) {
            if (preview == null && pendingMode == null && importJustApplied) {
                importJustApplied = false
                snackbar.showSnackbar(context.getString(R.string.import_success))
            }
        }
        }
    }

    // 格式详情弹窗 ("支持格式"每行 ⓘ 点开)
    detailFormat?.let { fmt ->
        FormatDetailDialog(format = fmt, onDismiss = { detailFormat = null })
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
                confirmedTableName = currentPreview.parseResult.tableName.ifBlank {
                    existingTable?.name ?: context.getString(R.string.default_table_name)
                }
                // 时间表优先级: ICS 收割的全校作息 > 现有表 > 默认
                confirmedTimeJson = currentPreview.parseResult.timeJson.ifBlank {
                    existingTable?.timeJson ?: TimeTableUtils.DEFAULT_TIME_JSON
                }
                pendingMode = mode
            }
        )
    }

    if (preview != null && pendingMode != null) {
        // 追加模式: 追加到已存在的课表, 命名由目标课表自带, 不需要再问用户
        // 直接走 applyImportPreview, 跳过 ImportConfirmDialog
        val pending = pendingMode!!
        if (pending == ImportApplyMode.AppendNonConflict || pending == ImportApplyMode.AppendAll) {
            LaunchedEffect(Unit) {
                scope.launch {
                    isLoading = true
                    try {
                        applyImportPreview(
                            preview = preview!!,
                            mode = pending,
                            confirmedStartDateRaw = preview!!.parseResult.startDate.ifBlank {
                                state.currentTable?.startDate ?: java.time.LocalDate.now().toString()
                            },
                            confirmedTableName = state.currentTable?.name ?: "",
                            confirmedTimeJson = preview!!.parseResult.timeJson.ifBlank {
                                state.currentTable?.timeJson ?: TimeTableUtils.DEFAULT_TIME_JSON
                            },
                            context = context,
                            onImported = onImported
                        ) { msg -> errorMsg = msg }
                        preview = null
                        pendingMode = null
                        importJustApplied = true
                    } finally {
                        isLoading = false
                    }
                }
            }
        } else {
            ImportConfirmDialog(
                startDate = confirmedStartDate,
                tableName = confirmedTableName,
                timeJson = confirmedTimeJson,
                // 仅"创建新课表"或"追加为新课表"需要命名; 覆盖课表用户已在用同一个, 不强制重命名
                showTableName = pending == ImportApplyMode.ImportAsNew || pending == ImportApplyMode.AppendAsNew,
                onTableNameChange = { confirmedTableName = it },
                onStartDateChange = { confirmedStartDate = it },
                onTimeJsonChange = { confirmedTimeJson = it },
                onDismiss = { pendingMode = null },
                onConfirm = {
                    val mode = pendingMode ?: return@ImportConfirmDialog
                    val currentPreview = preview ?: return@ImportConfirmDialog
                    scope.launch {
                        isLoading = true
                        try {
                            // 「确认导入」= 唯一写库点, 点下即落库。
                            // 之后不再跳编辑课表页 — 那个页面有「保存」按钮, 会造成
                            // "没点保存数据也在"的假保存闸误导(用户以为还有反悔机会, 实际已提交)。
                            applyImportPreview(
                                preview = currentPreview,
                                mode = mode,
                                confirmedStartDateRaw = confirmedStartDate,
                                confirmedTableName = confirmedTableName,
                                confirmedTimeJson = confirmedTimeJson,
                                context = context,
                                onImported = onImported
                            ) { msg -> errorMsg = msg }
                            preview = null
                            pendingMode = null
                            importJustApplied = true
                        } finally {
                            isLoading = false
                        }
                    }
                }
            )
        }
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
            .clip(SleepyTheme.shapes.medium)
            .noRippleClickable(onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(SleepyTheme.shapes.medium)
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
private fun FormatRow(name: String, desc: String, onDetail: () -> Unit) {
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
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = stringResource(R.string.format_detail_content_desc),
            tint = colors.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 6.dp, top = 2.dp)
                .size(16.dp)
                .clip(SleepyTheme.shapes.small)
                .noRippleClickable(onClick = onDetail)
        )
    }
}

/** 导入格式标识 — 对应"支持格式"列表的 6 行, 详情弹窗按它取 strings */
private enum class ImportFormat {
    WAKEUP_SHARE, WAKEUP_JSON, ICS, CSV, HTML, PLAIN
}

/**
 * 格式详情弹窗 — "支持格式"每行 ⓘ 点开。
 *
 * 文案全部来自 strings.xml (与 ScheduleParser 实际行为一一对应, 改解析器必须同步改文案):
 *  - 什么时候用: format_*_when
 *  - 识别要求:   format_*_spec (string-array, 逐条)
 *  - 示例:       format_*_example (monospace 块)
 * 纯文本格式额外带 "AI 截图转换" 区: 可复制 Prompt, 让豆包等识图生成纯文本。
 */
@Composable
private fun FormatDetailDialog(format: ImportFormat, onDismiss: () -> Unit) {
    val colors = SleepyTheme.colors
    val context = LocalContext.current

    val titleRes = when (format) {
        ImportFormat.WAKEUP_SHARE -> R.string.format_wakeup_share
        ImportFormat.WAKEUP_JSON -> R.string.format_wakeup_json
        ImportFormat.ICS -> R.string.format_ics
        ImportFormat.CSV -> R.string.format_csv
        ImportFormat.HTML -> R.string.format_html
        ImportFormat.PLAIN -> R.string.format_plain
    }
    val whenRes = when (format) {
        ImportFormat.WAKEUP_SHARE -> R.string.format_wakeup_share_when
        ImportFormat.WAKEUP_JSON -> R.string.format_wakeup_json_when
        ImportFormat.ICS -> R.string.format_ics_when
        ImportFormat.CSV -> R.string.format_csv_when
        ImportFormat.HTML -> R.string.format_html_when
        ImportFormat.PLAIN -> R.string.format_plain_when
    }
    val specRes = when (format) {
        ImportFormat.WAKEUP_SHARE -> R.array.format_wakeup_share_spec
        ImportFormat.WAKEUP_JSON -> R.array.format_wakeup_json_spec
        ImportFormat.ICS -> R.array.format_ics_spec
        ImportFormat.CSV -> R.array.format_csv_spec
        ImportFormat.HTML -> R.array.format_html_spec
        ImportFormat.PLAIN -> R.array.format_plain_spec
    }
    val exampleRes = when (format) {
        ImportFormat.WAKEUP_SHARE -> R.string.format_wakeup_share_example
        ImportFormat.WAKEUP_JSON -> R.string.format_wakeup_json_example
        ImportFormat.ICS -> R.string.format_ics_example
        ImportFormat.CSV -> R.string.format_csv_example
        ImportFormat.HTML -> R.string.format_html_example
        ImportFormat.PLAIN -> R.string.format_plain_example
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
        title = { Text(stringResource(titleRes), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(whenRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.format_help_spec),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onSurface
                )
                stringArrayResource(specRes).forEach { item ->
                    Row {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.format_help_example),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onSurface
                )
                Text(
                    text = stringResource(exampleRes),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SleepyTheme.shapes.medium)
                        .background(colors.surfaceContainer)
                        .padding(12.dp)
                )
                // 纯文本独有: AI 截图转换 Prompt (可复制)
                if (format == ImportFormat.PLAIN) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SleepyTheme.shapes.large)
                            .background(colors.primaryContainer)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ai_prompt_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onPrimaryContainer
                        )
                        Text(
                            text = stringResource(R.string.ai_prompt_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onPrimaryContainer
                        )
                        Text(
                            text = stringResource(R.string.ai_prompt_text).replace("\\n", "\n"),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = MaterialTheme.typography.labelSmall.fontSize),
                            color = colors.onPrimaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SleepyTheme.shapes.medium)
                                .background(colors.surfaceContainer)
                                .padding(10.dp)
                        )
                        // 2026-08-25 用户指令: 全 app 纯色块禁描线 — 用 surface 色块按钮, 非 OutlinedButton
                        Button(
                            onClick = {
                                val cm = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(
                                    ClipData.newPlainText(
                                        "prompt",
                                        context.getString(R.string.ai_prompt_text)
                                            .replace("\\n", "\n")
                                            .replace("\\t", "\t")
                                            .replace("&lt;", "<")
                                            .replace("&gt;", ">")
                                            .replace("&amp;", "&")
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.regularHeight),
                            shape = SleepyTheme.Buttons.shape,
                            colors = ButtonDefaults.buttonColors(
                                // 纯文字伪按钮不可接受：用 primaryContainer 色块和背景拉开层级，仍不加描边
                                containerColor = colors.primaryContainer,
                                contentColor = colors.onPrimaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                tint = colors.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.copy_prompt), color = colors.onPrimaryContainer)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.format_help_close))
            }
        },
        dismissButton = {}
    )
}

// --- shared types / dialogs (copied from ImportScreen to keep sheet self-contained) ---

private enum class ImportApplyMode {
    ReplaceCurrent,
    ImportAsNew,
    AppendNonConflict,
    /** 当前课表 + 导入数据合并, 创建新课表保存, 用户命名 */
    AppendAsNew,
    /** 连冲突课一起追加进当前课表(红标: 会形成同格多层) */
    AppendAll
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
                            bg = if (preview.conflictCount > 0) colors.errorContainer else colors.secondaryContainer,
                            fg = if (preview.conflictCount > 0) colors.onErrorContainer else colors.onSecondaryContainer,
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
                        .clip(SleepyTheme.shapes.large)
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
                            .clip(SleepyTheme.shapes.large)
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
                // 防呆: 输入里有行没解析成功 → 明确告诉用户哪些行被跳过, 不静默丢
                val dropped = preview.parseResult.droppedLines
                if (dropped.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SleepyTheme.shapes.large)
                            .background(colors.errorContainer)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.import_dropped_title, dropped.size),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onErrorContainer
                        )
                        Text(
                            text = stringResource(R.string.import_dropped_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onErrorContainer
                        )
                        dropped.take(3).forEach { line ->
                            Text(
                                text = "• $line",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = colors.onErrorContainer
                            )
                        }
                        if (dropped.size > 3) {
                            Text(
                                text = stringResource(R.string.import_conflict_more, dropped.size - 3),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onErrorContainer
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
                        shape = SleepyTheme.shapes.medium,
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
                            shape = SleepyTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                        ) {
                            Text(stringResource(R.string.import_append_only), maxLines = 1)
                        }
                        Button(
                            onClick = { onApply(ImportApplyMode.ImportAsNew) },
                            modifier = Modifier.weight(1f),
                            shape = SleepyTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                        ) {
                            Text(stringResource(R.string.import_as_new), maxLines = 1)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 追加冲突课表 — 危险动作(同格多层), errorContainer 色块底, 与覆盖按钮同款
                        Button(
                            onClick = { onApply(ImportApplyMode.AppendAll) },
                            modifier = Modifier.weight(1f),
                            shape = SleepyTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.errorContainer,
                                contentColor = colors.onErrorContainer
                            )
                        ) {
                            Text(stringResource(R.string.import_append_conflict), maxLines = 1)
                        }
                        Button(
                            onClick = { onApply(ImportApplyMode.AppendAsNew) },
                            modifier = Modifier.weight(1f),
                            shape = SleepyTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                        ) {
                            Text(stringResource(R.string.import_append_as_new), maxLines = 1)
                        }
                    }
                    // 描线→色块 (2026-08-25 统一指令): 覆盖课表为危险动作,
                    //   errorContainer 色块底 + onErrorContainer 文字
                    Button(
                        onClick = { onApply(ImportApplyMode.ReplaceCurrent) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = SleepyTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.errorContainer,
                            contentColor = colors.onErrorContainer
                        )
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
            .clip(SleepyTheme.shapes.large)
            .background(bg)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = SleepyTheme.Alpha.highContent))
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
    tableName: String,
    timeJson: String,
    showTableName: Boolean,
    onTableNameChange: (String) -> Unit,
    onStartDateChange: (String) -> Unit,
    onTimeJsonChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val colors = SleepyTheme.colors
    val context = LocalContext.current
    val fieldColors = SleepyTheme.fieldColors()
    var rows by remember(timeJson) {
        mutableStateOf(TimeTableUtils.parseTimeSlotRows(timeJson))
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_confirm_title), color = colors.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.import_confirm_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                if (showTableName) {
                    TextField(
                        value = tableName,
                        onValueChange = onTableNameChange,
                        label = { Text(stringResource(R.string.import_table_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = SleepyTheme.fieldShape,
                        colors = fieldColors
                    )
                }
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
    confirmedStartDateRaw: String,
    confirmedTableName: String,
    confirmedTimeJson: String,
    context: android.content.Context,
    onImported: () -> Unit,
    onError: (String) -> Unit
) {
    val repo = SleepyApp.get().repository
    // v7.10.16 撤回: 整个导入是一个动作 — 批内只保首快照, 撤回一次回退到导入前。
    // try/finally 收口: 分支里的 early return 也要退出批边界。
    com.lingion.sleepy.data.undo.UndoManager.beginBatch()
    try {
    // 应用约定 startDate=周一；用户在确认框可能手填非周一日期，落库前归一（issue #5）
    val confirmedStartDate = DateUtils.normalizeStartDate(confirmedStartDateRaw)
    when (mode) {
        ImportApplyMode.ReplaceCurrent -> {
            val existing = repo.getTable(preview.targetTableId)
            if (existing != null) {
                repo.updateTable(
                    existing.copy(
                        name = confirmedTableName.trim().ifBlank { preview.parseResult.tableName },
                        startDate = confirmedStartDate,
                        timeJson = confirmedTimeJson,
                        nodesPerDay = if (preview.parseResult.nodesPerDay > 0) preview.parseResult.nodesPerDay else existing.nodesPerDay
                    )
                )
            }
            repo.replaceCourses(preview.targetTableId, preview.parseResult.courses)
            // v7.10.12: 整表替换不过闸门(换的是整张表, 拦截太武断), 但超层时提示
            val badDays = com.lingion.sleepy.util.ConflictLayoutEngine
                .daysExceedingTwoLanes(preview.parseResult.courses)
            if (badDays.isNotEmpty()) {
                onError(context.getString(R.string.import_three_layers_kept, dayNames(badDays, context)))
            }
            onImported()
        }
        ImportApplyMode.ImportAsNew -> {
            val base = repo.getTable(preview.targetTableId)
            val newTableId = repo.insertTable(
                TimeTableEntity(
                    name = uniqueImportedTableName(confirmedTableName, repo.getAllTables().map { it.name }, context),
                    startDate = confirmedStartDate,
                    maxWeek = base?.maxWeek ?: 20,
                    nodesPerDay = if (preview.parseResult.nodesPerDay > 0) preview.parseResult.nodesPerDay else base?.nodesPerDay ?: 12,
                    timeJson = confirmedTimeJson,
                    color = base?.color ?: "#FF6750A4",
                    isDefault = false
                )
            )
            repo.insertCourses(preview.parseResult.courses.map { it.copy(id = 0, tableId = newTableId) })
            repo.setDefault(newTableId)
            // v7.10.12: 整表新建不过闸门, 超层时提示(同 ReplaceCurrent 策略)
            val badDaysNew = com.lingion.sleepy.util.ConflictLayoutEngine
                .daysExceedingTwoLanes(preview.parseResult.courses)
            if (badDaysNew.isNotEmpty()) {
                onError(context.getString(R.string.import_three_layers_kept, dayNames(badDaysNew, context)))
            }
            onImported()
        }
        ImportApplyMode.AppendNonConflict -> {
            val cleanCourses = preview.parseResult.courses.filterNot { incoming ->
                preview.existingCourses.any { existing -> coursesConflict(incoming, existing) }
            }
            if (cleanCourses.isEmpty()) {
                onError(context.getString(R.string.import_all_conflict))
                return
            }
            // v7.10.12 三层闸门(追加模式) — 合并现有课+新课后按区域数栏, 超 2 栏的课剔除;
            // 全被剔除则报错, 部分剔除则提示跳过了哪些天的课
            val survivors = dropThreeLayerCourses(preview.existingCourses, cleanCourses)
            if (survivors.isEmpty()) {
                onError(context.getString(R.string.import_all_conflict))
                return
            }
            if (survivors.size < cleanCourses.size) {
                val droppedDays = conflictDaysBetween(cleanCourses, survivors)
                onError(context.getString(R.string.import_three_layers_dropped, dayNames(droppedDays, context)))
            }
            repo.insertCourses(survivors.map { it.copy(id = 0, tableId = preview.targetTableId) })
            // 用户反馈: 追加课程时, 若导入数据节次超过课表, 课表应自动延伸
            // (课表仍只有 10 节 → 11-13 节课程入库但渲染期看不到, 等于静默丢)
            val existingTable = repo.getTable(preview.targetTableId)
            if (existingTable != null && preview.parseResult.timeJson.isNotBlank()) {
                val extended = TimeTableUtils.extendTimeJsonWith(existingTable.timeJson, preview.parseResult.timeJson)
                if (extended != existingTable.timeJson) {
                    val newMaxNode = TimeTableUtils.parseTimeSlotRows(extended).maxOfOrNull { it.node } ?: existingTable.nodesPerDay
                    repo.updateTable(existingTable.copy(timeJson = extended, nodesPerDay = newMaxNode))
                }
            }
            onImported()
        }
        ImportApplyMode.AppendAsNew -> {
            // 当前课表 + 导入数据合并 → 新课表(用户命名)
            val base = repo.getTable(preview.targetTableId)
            val incoming = preview.parseResult
            val mergedTimeJson = TimeTableUtils.extendTimeJsonWith(
                base?.timeJson ?: TimeTableUtils.DEFAULT_TIME_JSON,
                incoming.timeJson.ifBlank { base?.timeJson ?: TimeTableUtils.DEFAULT_TIME_JSON }
            )
            val mergedRows = TimeTableUtils.parseTimeSlotRows(mergedTimeJson)
            val newTableId = repo.insertTable(
                TimeTableEntity(
                    name = uniqueImportedTableName(confirmedTableName, repo.getAllTables().map { it.name }, context),
                    startDate = confirmedStartDate,
                    maxWeek = base?.maxWeek ?: 20,
                    nodesPerDay = mergedRows.size,
                    timeJson = mergedTimeJson,
                    color = base?.color ?: "#FF6750A4",
                    isDefault = false
                )
            )
            // 当前课表原课 + 导入课程合并导入新课表(跳过与原课冲突的)
            // v7.10.12: 合并后同样过三层闸门
            val cleanIncoming = incoming.courses.filterNot { inc ->
                preview.existingCourses.any { existing -> coursesConflict(inc, existing) }
            }
            val oldCourses = base?.let { repo.getCourses(it.id) } ?: emptyList()
            val allCourses = oldCourses + cleanIncoming
            val gated = dropThreeLayerCourses(oldCourses, cleanIncoming)
            if (gated.size < allCourses.size) {
                val droppedDays = conflictDaysBetween(allCourses, gated)
                onError(context.getString(R.string.import_three_layers_dropped, dayNames(droppedDays, context)))
            }
            repo.insertCourses(gated.map { it.copy(id = 0, tableId = newTableId) })
            repo.setDefault(newTableId)
            onImported()
        }
        ImportApplyMode.AppendAll -> {
            // 连冲突课一起追加进当前课表 — 冲突/闸门全部放行, 仅提示(与整表新建同策略)。
            // 用户 2026-09-03: 该按钮的存在意义就是"冲突也要进来", 剔除即违背语义。
            val cleanCourses = preview.parseResult.courses
            if (cleanCourses.isEmpty()) {
                onError(context.getString(R.string.import_content_empty))
                return
            }
            val badDays = com.lingion.sleepy.util.ConflictLayoutEngine
                .daysExceedingTwoLanes(preview.existingCourses + cleanCourses)
            if (badDays.isNotEmpty()) {
                onError(context.getString(R.string.import_three_layers_kept, dayNames(badDays, context)))
            }
            repo.insertCourses(cleanCourses.map { it.copy(id = 0, tableId = preview.targetTableId) })
            // 节次自动延伸 — 与 AppendNonConflict 同策略
            val existingTable = repo.getTable(preview.targetTableId)
            if (existingTable != null && preview.parseResult.timeJson.isNotBlank()) {
                val extended = TimeTableUtils.extendTimeJsonWith(existingTable.timeJson, preview.parseResult.timeJson)
                if (extended != existingTable.timeJson) {
                    val newMaxNode = TimeTableUtils.parseTimeSlotRows(extended).maxOfOrNull { it.node } ?: existingTable.nodesPerDay
                    repo.updateTable(existingTable.copy(timeJson = extended, nodesPerDay = newMaxNode))
                }
            }
            onImported()
        }
    }
    } finally {
        // 批边界收口 — 无论哪个分支 return, 撤回批都到此结束
        com.lingion.sleepy.data.undo.UndoManager.endBatch()
    }
}

/**
 * v7.10.12 三层冲突闸门(导入路径) — keepers 保持不变, 候选逐门试探:
 * 加入后若使其所在 day 的 chainGroups 分组数 > 2 则剔除该候选。
 * 策略: 导入数据服从闸门(超层课不入库), 现有课永不动。
 */
private fun dropThreeLayerCourses(
    keepers: List<com.lingion.sleepy.data.entity.CourseEntity>,
    candidates: List<com.lingion.sleepy.data.entity.CourseEntity>
): List<com.lingion.sleepy.data.entity.CourseEntity> {
    val out = keepers.toMutableList()
    return candidates.filter { cand ->
        val trial = out + cand
        com.lingion.sleepy.util.ConflictLayoutEngine.daysExceedingTwoLanes(trial).isEmpty().also { ok ->
            if (ok) out.add(cand)
        }
    }
}

/** 被剔除课所在的 day 集合(用于提示文案)。 */
private fun conflictDaysBetween(
    before: List<com.lingion.sleepy.data.entity.CourseEntity>,
    after: List<com.lingion.sleepy.data.entity.CourseEntity>
): Set<Int> = (before.toSet() - after.toSet()).map { it.day }.toSet()

private fun dayNames(days: Set<Int>, context: android.content.Context): String =
    days.sorted().joinToString(" / ") { com.lingion.sleepy.util.DateUtils.localizedDay(it, context) }

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
