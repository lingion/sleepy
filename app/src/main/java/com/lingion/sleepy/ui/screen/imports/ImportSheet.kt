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
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.IconButton
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
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.data.parser.ScheduleParser
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
    drafts: List<ImportDraft> = emptyList(),
    onRestoreDraft: (String) -> Unit = {},
    onDeleteDraft: (String) -> Unit = {},
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cannotReadFileMessage = stringResource(R.string.cannot_read_file)
    val readFailedFormat = stringResource(R.string.read_failed)
    val importSuccessMessage = stringResource(R.string.import_success)

    var textExpanded by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var detailFormat by remember { mutableStateOf<ImportFormat?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var decisionVisible by remember { mutableStateOf(true) }
    var lastPreviewText by remember { mutableStateOf<String?>(null) }
    var decisionConfiguration by remember {
        mutableStateOf<com.lingion.sleepy.data.imports.ImportConfiguration?>(null)
    }
    fun showPreview(parsed: ImportPreview, text: String? = null) {
        preview = parsed
        lastPreviewText = text
        decisionConfiguration = null
        decisionVisible = true
    }
    // v1.0.56 T9: 纯作息导入 — 解析结果只有作息表没课程时, 走独立确认框(只建作息表)
    var purePeriodName by remember { mutableStateOf("") }
    val allPeriodTables by viewModel.allPeriodTables.collectAsState(initial = emptyList())
    var importJustApplied by remember { mutableStateOf(false) }
    var showDrafts by remember { mutableStateOf(false) }
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
                if (p != null) showPreview(p)
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
                    if (p != null) showPreview(p)
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
                        ?: throw Exception(cannotReadFileMessage)
                    val parsed = buildImportPreview(text, state, context) { msg -> errorMsg = msg }
                    if (parsed != null) showPreview(parsed)
                    // 注意: 不要在这里 onDismiss() —— sheet 关掉后 preview state 会随之销毁, dialog 永远不弹。
                    // preview != null 时 ImportDecisionDialog 会在 sheet 之上显示; 用户点确认/取消后再清 state。
                } catch (e: Exception) {
                    errorMsg = readFailedFormat.format(e.message)
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // 2026-09-18 用户: 报错必须统一弹窗(不再是 Snackbar 一闪即逝) — 文件导入场景
    // 无 WebView/dump 可导, 单确定按钮。成功/状态类提示仍走 snackbar 不动。
    if (errorMsg != null) {
        AlertDialog(
            onDismissRequest = { errorMsg = null },
            title = { Text(stringResource(R.string.jw_error_dialog_title)) },
            text = {
                Text(
                    text = errorMsg!!,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { errorMsg = null }) {
                    Text(stringResource(R.string.jw_err_dismiss))
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isLoading) onDismiss() },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 标题与草稿入口
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.import_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showDrafts = true },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(colors.primaryContainer)
                ) {
                    // 2026-09-16 用户: 书签不像草稿箱 — 换带盖收纳箱 Inventory2
                    Icon(
                        imageVector = Icons.Outlined.Inventory2,
                        contentDescription = stringResource(R.string.import_drafts),
                        tint = colors.onPrimaryContainer
                    )
                }
            }
            Text(
                text = stringResource(R.string.import_preview_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (preview != null && !decisionVisible) {
                Button(
                    onClick = { decisionVisible = true },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = SleepyTheme.Buttons.shape
                ) {
                    Text(stringResource(R.string.import_preview))
                }
            }

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
                            if (preview != null && inputText == lastPreviewText) {
                                decisionVisible = true
                            } else scope.launch {
                                isLoading = true
                                try {
                                    val p = buildImportPreview(inputText, state, context) { msg -> errorMsg = msg }
                                    if (p != null) showPreview(p, inputText)
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.regularHeight),
                        enabled = !isLoading && inputText.isNotBlank(),
                        shape = SleepyTheme.Buttons.shape,
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
        // (原 BoxWithConstraints 包裹层已删: scope 内 maxWidth/maxHeight 从未被消费, lint UnusedBoxWithConstraintsScope)
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        // 导入成功提示: 不再跳编辑课表页(假保存闸), 用 snackbar 明示已落库
        LaunchedEffect(preview, importJustApplied) {
            if (preview == null && importJustApplied) {
                importJustApplied = false
                snackbar.showSnackbar(importSuccessMessage)
            }
        }
    }

    if (showDrafts) {
        ImportDraftSheet(
            drafts = drafts,
            onDismiss = { showDrafts = false },
            onRestore = { id ->
                showDrafts = false
                onRestoreDraft(id)
            },
            onDelete = onDeleteDraft
        )
    }

    // 格式详情弹窗 ("支持格式"每行 ⓘ 点开)
    detailFormat?.let { fmt ->
        FormatDetailDialog(format = fmt, onDismiss = { detailFormat = null })
    }

    // 预览对话框
    preview?.let { currentPreview ->
        // v1.0.56 T9: 纯作息导入(只有 P 区块/节次, 零课程) — 不进课程确认框,
        // 弹独立命名框(预填全局唯一名, 用户可改), 确认只建作息表不建空课表。
        val isPurePeriod = currentPreview.parseResult.courses.isEmpty() &&
            currentPreview.parseResult.periodTable != null
        if (isPurePeriod) {
            val pt = currentPreview.parseResult.periodTable!!
            androidx.compose.runtime.LaunchedEffect(currentPreview) {
                // 预填名 = 源名参与全局唯一名顺延(课程表∪作息表), 后缀 2/3 预览即可见
                val courseNames = viewModel.getAllTableNamesOnce()
                val periodNames = viewModel.getAllPeriodTableNamesOnce()
                purePeriodName = TimeTableUtils.suggestUniqueName(pt.name, courseNames, periodNames)
            }
            val candidate = purePeriodName.trim()
            val nameTaken = candidate.isNotBlank() && TimeTableUtils.isTableNameTaken(
                candidate,
                state.tables.map { it.name },
                allPeriodTables.map { it.name }
            )
            AlertDialog(
                onDismissRequest = { preview = null },
                title = { Text(stringResource(R.string.period_table_import_title), color = colors.onSurface) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(R.string.period_table_import_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                        TextField(
                            value = purePeriodName,
                            onValueChange = { purePeriodName = it },
                            label = { Text(stringResource(R.string.period_table_name_label)) },
                            singleLine = true,
                            isError = nameTaken,
                            supportingText = if (nameTaken) {
                                { Text(stringResource(R.string.period_table_name_taken)) }
                            } else null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = SleepyTheme.fieldShape,
                            colors = SleepyTheme.fieldColors()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        // 2026-09-16 用户: 裸 TextButton 无边界无色块 — 统一色块按钮行
                        com.lingion.sleepy.ui.component.DialogActionButtons(
                            confirmText = stringResource(R.string.period_table_import_confirm),
                            onConfirm = {
                                if (!isLoading) {
                                    isLoading = true
                                    scope.launch {
                                        try {
                                            val applied = applyPurePeriodImport(
                                                name = candidate,
                                                parsed = pt,
                                                onError = { msg -> errorMsg = msg }
                                            )
                                            if (applied) {
                                                preview = null
                                                importJustApplied = true
                                                onImported()
                                            }
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            },
                            dismissText = stringResource(R.string.cancel),
                            onDismiss = { preview = null },
                            confirmEnabled = candidate.isNotBlank() && !nameTaken && !isLoading
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {}
            )
            return@let
        }
        if (decisionVisible) {
            ImportDecisionDialog(
                source = currentPreview.parseResult,
                initialTargetId = currentPreview.targetTableId,
                initialConfiguration = decisionConfiguration,
                onConfigurationChange = { decisionConfiguration = it },
                onApplyingChange = { isLoading = it },
                onDismiss = { decisionVisible = false },
                onImported = {
                    preview = null
                    decisionConfiguration = null
                    importJustApplied = true
                    onImported()
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
    val colors = MaterialTheme.colorScheme
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
    val colors = MaterialTheme.colorScheme
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
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val aiPromptText = stringResource(R.string.ai_prompt_text)

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
                    // strings.xml 里 \n/\t 是字面两字符(formatted="false"), 渲染前手动还原 —
                    // 与下方 ai_prompt_text 同一约定; 否则示例挤成一行, 用户没法照着写
                    text = stringResource(exampleRes)
                        .replace("\\n", "\n")
                        .replace("\\t", "\t"),
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
                                        aiPromptText
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

internal data class ImportPreview(
    val targetTableId: Long,
    val parseResult: ScheduleParser.ParseResult
)

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
    val tableId = state.selectedTableId ?: 0L
    val result = ScheduleParser.parse(text, tableId)
    return result.fold(
        onSuccess = { parseResult ->
            buildImportPreview(parseResult, tableId, context)
        },
        onFailure = { e ->
            onError(context.getString(R.string.import_failed, e.message))
            null
        }
    )
}

internal suspend fun buildImportPreview(
    parseResult: ScheduleParser.ParseResult,
    tableId: Long,
    context: android.content.Context
): ImportPreview {
    val multiLocationWarnings = parseResult.courses.groupBy { it.groupId }.mapNotNull { (groupId, courses) ->
        if (groupId.isBlank()) return@mapNotNull null
        val rooms = courses.map { it.room.trim() }.filter { it.isNotBlank() }.distinct()
        if (rooms.size < 2) null else context.getString(
            R.string.import_multi_location_warning_detail, courses.first().courseName, rooms.size
        )
    }
    return ImportPreview(tableId, parseResult.copy(warnings = parseResult.warnings + multiLocationWarnings))
}

/**
 * v1.0.56 T9: 纯作息导入 — 只建一张作息表, 不建空课表。
 * 名字已由确认框查重; 落库前再走 suggestUniqueName 兜底(同屏并发导入等边缘)。
 */
private suspend fun applyPurePeriodImport(
    name: String,
    parsed: ScheduleParser.ParsedPeriodTable,
    onError: (String) -> Unit
): Boolean {
    val repo = SleepyApp.get().repository
    com.lingion.sleepy.data.undo.UndoManager.beginBatch()
    try {
        val courseNames = repo.getAllTables().map { it.name }
        val periodNames = repo.getAllPeriodTables().map { it.name }
        val unique = TimeTableUtils.suggestUniqueName(name, courseNames, periodNames)
        repo.insertPeriodTable(
            PeriodTableEntity(
                name = unique,
                nodesPerDay = parsed.nodesPerDay.coerceAtLeast(1),
                timeJson = parsed.timeJson
            )
        )
        return true
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(e.message ?: SleepyApp.get().getString(R.string.import_failed, ""))
        return false
    } finally {
        com.lingion.sleepy.data.undo.UndoManager.endBatch()
    }
}
