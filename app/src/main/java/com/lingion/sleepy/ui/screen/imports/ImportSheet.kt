package com.lingion.sleepy.ui.screen.imports

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.net.Uri
import com.lingion.sleepy.BuildConfig
import com.lingion.sleepy.console.ui.dbgTag
import org.json.JSONArray
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
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
import com.lingion.sleepy.data.jw.WakeUpShareClient
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.SmartPeriodConfig
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.data.parser.ScheduleParser
import com.lingion.sleepy.ui.component.DatePickerField
import com.lingion.sleepy.ui.component.TimeSlotEditor
import com.lingion.sleepy.ui.component.resolveAutoPeriodConfig
import com.lingion.sleepy.ui.component.sleepyExpandSpecs
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import kotlinx.coroutines.launch
import com.lingion.sleepy.util.AppClock

/** 全屏导入入口：新建课表或向指定课表添加课程。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTableScreen(
    onBack: () -> Unit,
    onCreateNewTable: () -> Unit,
    onJwImportRequested: () -> Unit,
    onImported: () -> Unit,
    targetTableId: Long = 0L,
    addingCourses: Boolean = false,
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cannotReadFileMessage = stringResource(R.string.cannot_read_file)
    val readFailedFormat = stringResource(R.string.read_failed)
    val importSuccessMessage = stringResource(R.string.import_success)
    val defaultTableName = stringResource(R.string.default_table_name)

    var textExpanded by rememberSaveable { mutableStateOf(false) }
    var inputText by rememberSaveable { mutableStateOf("") }
    var detailFormat by remember { mutableStateOf<ImportFormat?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var confirmedTableName by remember { mutableStateOf("") }
    var confirmedStartDate by remember { mutableStateOf("") }
    var confirmedTimeJson by remember { mutableStateOf("") }
    // 第三 Tab「作息表」的选择随新课表一起保存。
    var confirmedBindPeriodTableId by remember { mutableStateOf<Long?>(null) }
    // v1.0.56 T9: 纯作息导入 — 解析结果只有作息表没课程时, 走独立确认框(只建作息表)
    var purePeriodName by remember { mutableStateOf("") }
    val allPeriodTables by viewModel.allPeriodTables.collectAsState(initial = emptyList())
    var importJustApplied by remember { mutableStateOf(false) }
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    var showExistingTables by remember { mutableStateOf(false) }
    // 2026-10-26 用户定令: 加课模式每条导入路径解析后先进入逐组勾选界面。
    var pendingSelection by remember { mutableStateOf<ImportPreview?>(null) }
    val canImport = !isLoading && (!addingCourses || state.tables.any { it.id == targetTableId })

    fun commitPreview(value: ImportPreview) {
        confirmedStartDate = value.parseResult.startDate.ifBlank { java.time.LocalDate.now(AppClock).toString() }
        confirmedTableName = value.parseResult.tableName.ifBlank { defaultTableName }
        confirmedTimeJson = TimeTableUtils.mergeMostComplete(
            currentJson = "",
            incomingJson = value.parseResult.timeJson,
            requiredNodeCount = maxOf(value.parseResult.nodesPerDay,
                value.parseResult.courses.maxOfOrNull { it.startNode + it.step - 1 } ?: 0)
        )
        confirmedBindPeriodTableId = null
        preview = value
    }

    fun acceptPreview(value: ImportPreview?) {
        value ?: return
        if (addingCourses && value.parseResult.courses.isEmpty()) {
            errorMsg = context.getString(R.string.import_content_empty)
            return
        }
        if (addingCourses) {
            pendingSelection = value
            return
        }
        commitPreview(value)
    }

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
                val p = buildImportPreview(text, targetTableId, context) { msg -> errorMsg = msg }
                acceptPreview(p)
            } catch (e: Throwable) {
                android.util.Log.e("Sleepy", "pending import preview failed", e)
                errorMsg = readFailedFormat.format(e.message)
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
                    val p = buildImportPreview(text, targetTableId, context) { msg -> errorMsg = msg }
                    acceptPreview(p)
                } catch (e: Exception) {
                    errorMsg = readFailedFormat.format(e.message)
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
                    acceptPreview(buildImportPreview(text, targetTableId, context) { msg -> errorMsg = msg })
                    // 保持页面，预览对话框依赖这里的 preview 状态。
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
                TextButton(
                    onClick = { errorMsg = null },
                    modifier = Modifier.dbgTag("import_err_dismiss", role = "button")
                ) {
                    Text(stringResource(R.string.jw_err_dismiss))
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (addingCourses) R.string.add_course_title else R.string.create_table_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.dbgTag("import_back", role = "button")
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            if (addingCourses) {
                ImportMethodRow(
                    icon = Icons.Outlined.ContentCopy,
                    label = stringResource(R.string.add_course_from_existing),
                    enabled = canImport,
                    dbgTag = "import_existing",
                    onClick = { showExistingTables = true }
                )
                if (!state.tables.any { it.id == targetTableId }) {
                    Text(stringResource(R.string.manage_no_table_hint), color = colors.onSurfaceVariant)
                }
            }
            // 2026-10-26 用户定令: 子页面形态下「（将作为新课表创建）」提示行多余, 删除。
            // 键 import_new_table_hint 保留 — 预览对话框标题区仍引用它。

            // 行 1：教务直连
            ImportMethodRow(
                icon = Icons.Outlined.QrCode2,
                label = stringResource(R.string.import_jw),
                enabled = canImport,
                dbgTag = "import_jw",
                onClick = onJwImportRequested
            )

            // 行 2：从文本导入（可折叠）
            ImportMethodRow(
                icon = Icons.Outlined.Description,
                label = stringResource(R.string.import_paste),
                trailing = if (textExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                enabled = canImport,
                dbgTag = "import_paste",
                onClick = { textExpanded = !textExpanded }
            )
            // MD3E (Task 7a): 走展开家族 (尺寸 fastSpatial + 淡入淡出 motionScheme);
            // 旧版无 fadeIn, 工厂补齐 — 与全应用折叠一致性优先
            val expandSpecs = sleepyExpandSpecs()
            AnimatedVisibility(
                visible = textExpanded,
                enter = expandSpecs.enter,
                exit = expandSpecs.exit,
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
                            .height(160.dp)
                            .dbgTag("import_paste_field", role = "textbox"),
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
                                    val p = buildImportPreview(inputText, targetTableId, context) { msg -> errorMsg = msg }
                                    if (p != null) {
                                        acceptPreview(p)
                                        // 预览对话框叠在当前页面上显示。
                                    }
                                } catch (e: Exception) {
                                    errorMsg = readFailedFormat.format(e.message)
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.regularHeight)
                            .dbgTag("import_parse", role = "button"),
                        enabled = canImport && inputText.isNotBlank(),
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

            // 手动入口行(2026-10-26 用户定令): 由底部通栏大按钮改为与其他路径一致的方法行,
            // 位置固定在「粘贴课表文本」折叠块之后、「从文件导入」之前。
            // 加课模式 = 手动添加课程(跳课程录入页); 新建模式 = 新建空白课表。
            ImportMethodRow(
                icon = Icons.Outlined.Add,
                label = stringResource(if (addingCourses) R.string.add_course_manual else R.string.manage_create_blank_table),
                enabled = canImport,
                dbgTag = "import_manual",
                onClick = onCreateNewTable
            )

            // 行 3：从文件导入
            ImportMethodRow(
                icon = Icons.Outlined.FileUpload,
                label = stringResource(R.string.import_file),
                enabled = canImport,
                dbgTag = "import_file",
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
                    dbgTag = "import_format#WAKEUP_SHARE",
                    onDetail = { detailFormat = ImportFormat.WAKEUP_SHARE }
                )
                FormatRow(
                    name = stringResource(R.string.format_wakeup_json),
                    desc = stringResource(R.string.format_json_desc),
                    dbgTag = "import_format#WAKEUP_JSON",
                    onDetail = { detailFormat = ImportFormat.WAKEUP_JSON }
                )
                FormatRow(
                    name = stringResource(R.string.format_ics),
                    desc = stringResource(R.string.format_ics_desc),
                    dbgTag = "import_format#ICS",
                    onDetail = { detailFormat = ImportFormat.ICS }
                )
                FormatRow(
                    name = stringResource(R.string.format_csv),
                    desc = stringResource(R.string.format_csv_desc),
                    dbgTag = "import_format#CSV",
                    onDetail = { detailFormat = ImportFormat.CSV }
                )
                FormatRow(
                    name = stringResource(R.string.format_html),
                    desc = stringResource(R.string.format_html_desc),
                    dbgTag = "import_format#HTML",
                    onDetail = { detailFormat = ImportFormat.HTML }
                )
                FormatRow(
                    name = stringResource(R.string.format_plain),
                    desc = stringResource(R.string.format_plain_desc),
                    dbgTag = "import_format#PLAIN",
                    onDetail = { detailFormat = ImportFormat.PLAIN }
                )
            }

            // 2026-10-26 用户定令: 原底部通栏大按钮改方法行, 移入上方路径列表(行位 356)。
            Spacer(modifier = Modifier.height(24.dp))
        }

        // 导入成功提示: 不再跳编辑课表页(假保存闸), 用 snackbar 明示已落库
        LaunchedEffect(preview, importJustApplied) {
            if (preview == null && importJustApplied) {
                importJustApplied = false
                snackbar.showSnackbar(importSuccessMessage)
            }
        }
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
                onDismissRequest = { if (!isLoading) preview = null },
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
                            modifier = Modifier.fillMaxWidth()
                                .dbgTag("import_pure_period_field", role = "textbox"),
                            shape = SleepyTheme.fieldShape,
                            colors = SleepyTheme.fieldColors()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        // 2026-09-16 用户: 裸 TextButton 无边界无色块 — 统一色块按钮行
                        com.lingion.sleepy.ui.component.DialogActionButtons(
                            confirmText = stringResource(R.string.period_table_import_confirm),
                            onConfirm = {
                                if (isLoading) return@DialogActionButtons
                                isLoading = true
                                scope.launch {
                                    try {
                                        applyPurePeriodImport(
                                            name = candidate,
                                            parsed = pt,
                                            onImported = { preview = null; importJustApplied = true; onImported() },
                                            onError = { msg -> errorMsg = msg }
                                        )
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            dismissText = stringResource(R.string.cancel),
                            onDismiss = { if (!isLoading) preview = null },
                            dbgTagPrefix = "import_pure_period",
                            confirmEnabled = !isLoading && candidate.isNotBlank() && !nameTaken
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {}
            )
            return@let
        }
        if (!addingCourses) {
            ImportConfirmDialog(
                startDate = confirmedStartDate,
                tableName = confirmedTableName,
                timeJson = confirmedTimeJson,
                showTableName = true,
                isApplying = isLoading,
                parseResult = currentPreview.parseResult,
                onTableNameChange = { confirmedTableName = it },
                onStartDateChange = { confirmedStartDate = it },
                onTimeJsonChange = { confirmedTimeJson = it },
                onDismiss = { if (!isLoading) preview = null },
                periodTableOptions = allPeriodTables.map {
                    com.lingion.sleepy.ui.component.PeriodTableOption(it.id, it.name, it.nodesPerDay)
                },
                selectedPeriodTableId = confirmedBindPeriodTableId,
                onSelectPeriodTable = { confirmedBindPeriodTableId = it },
                periodTableTimeJsonById = allPeriodTables.associate { it.id to it.timeJson },
                onConfirm = {
                    if (!isLoading) {
                        isLoading = true
                        scope.launch {
                            try {
                                applyNewTableImport(
                                    preview = currentPreview,
                                    confirmedStartDateRaw = confirmedStartDate,
                                    confirmedTableName = confirmedTableName,
                                    confirmedTimeJson = confirmedTimeJson,
                                    context = context,
                                    bindPeriodTableId = confirmedBindPeriodTableId,
                                    onImported = { preview = null; importJustApplied = true; onImported() },
                                )
                            } catch (e: Exception) {
                                errorMsg = readFailedFormat.format(e.message)
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                }
            )
        } else {
            ImportPreviewDialog(
                preview = currentPreview,
                onDismiss = { if (!isLoading) preview = null },
                isApplying = isLoading,
                dbgTagPrefix = "import_preview",
                onApply = { decision ->
                    if (!isLoading) {
                        isLoading = true
                        scope.launch {
                            try {
                                applyCourseImport(
                                    preview = currentPreview,
                                    decision = decision,
                                    context = context,
                                    onImported = { preview = null; importJustApplied = true; onImported() },
                                    onError = { errorMsg = it },
                                )
                            } catch (e: Exception) {
                                errorMsg = readFailedFormat.format(e.message)
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                }
            )
        }
    }

    // 2026-10-26 用户定令: 加课模式解析后先勾选, 确认才进 ImportPreviewDialog。
    pendingSelection?.let { staged ->
        CourseSelectionSheet(
            parseResult = staged.parseResult,
            onDismiss = { pendingSelection = null },
            onConfirm = { selectedKeys ->
                pendingSelection = null
                val filtered = staged.parseResult.courses.filter { importCourseGroupKey(it) in selectedKeys }
                // 勾选为空 = 全部取消 → 提示而非静默回到空预览
                if (filtered.isEmpty()) {
                    errorMsg = context.getString(R.string.import_selection_empty)
                } else {
                    scope.launch {
                        commitPreview(buildImportPreview(staged.parseResult.copy(courses = filtered), targetTableId, context))
                    }
                }
            }
        )
    }

    if (showExistingTables) {
        val sources = state.tables.filter { it.id != targetTableId }
        AlertDialog(
            onDismissRequest = { showExistingTables = false },
            title = { Text(stringResource(R.string.add_course_from_existing)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (sources.isEmpty()) Text(stringResource(R.string.add_course_no_source))
                    sources.forEach { source ->
                        TextButton(
                            enabled = !isLoading,
                            onClick = {
                                isLoading = true
                                scope.launch {
                                    try {
                                        val repo = SleepyApp.get().repository
                                        val table = repo.getTable(source.id)
                                            ?: error(context.getString(R.string.manage_no_table))
                                        val effective = table.hydratedWith(repo.effectivePeriodTable(source.id))
                                        val courses = repo.getCourses(source.id)
                                        if (courses.isEmpty()) {
                                            errorMsg = context.getString(R.string.import_content_empty)
                                        } else {
                                            acceptPreview(buildImportPreview(
                                                ScheduleParser.ParseResult(
                                                    tableName = effective.name,
                                                    startDate = effective.startDate,
                                                    courses = courses,
                                                    timeJson = effective.timeJson,
                                                    nodesPerDay = effective.nodesPerDay,
                                                    maxWeek = effective.maxWeek,
                                                    groupIdsAuthoritative = true,
                                                ), targetTableId, context
                                            ))
                                            showExistingTables = false
                                        }
                                    } catch (e: Exception) {
                                        errorMsg = readFailedFormat.format(e.message)
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                                .dbgTag("import_source", text = source.name, role = "button")
                        ) { Text(source.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showExistingTables = false },
                    modifier = Modifier.dbgTag("import_source_cancel", role = "button")
                ) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

}

@Composable
private fun ImportMethodRow(
    icon: ImageVector,
    label: String,
    trailing: ImageVector? = null,
    enabled: Boolean = true,
    dbgTag: String? = null,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SleepyTheme.shapes.medium)
            .noRippleClickable(enabled = enabled, onClick = onClick)
            .then(dbgTag?.let { Modifier.dbgTag(it, text = label, role = "button") } ?: Modifier)
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
private fun FormatRow(name: String, desc: String, dbgTag: String? = null, onDetail: () -> Unit) {
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
                .then(dbgTag?.let { Modifier.dbgTag(it, text = name, role = "button") } ?: Modifier)
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
                            modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.regularHeight)
                                .dbgTag("import_copy_prompt", role = "button"),
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
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.dbgTag("import_format_close", role = "button")
            ) {
                Text(stringResource(R.string.format_help_close))
            }
        },
        dismissButton = {}
    )
}

// --- shared types / dialogs (copied from ImportScreen to keep sheet self-contained) ---

internal data class CourseConflict(
    val incoming: CourseEntity,
    val existing: CourseEntity
)

internal data class ImportPreview(
    val targetTableId: Long,
    val targetTableName: String,
    val parseResult: ScheduleParser.ParseResult,
    val existingCourses: List<CourseEntity>,
    val conflicts: List<CourseConflict>,
    // issue#22: 同 groupId 多地点提示 — 不阻塞导入,只让用户心里有数
    val multiLocationWarnings: List<String> = emptyList(),
    val effectiveTimeJson: String = "",
) {
    val incomingCount: Int get() = parseResult.courses.size
    val conflictCount: Int get() = conflicts.size
    val cleanCount: Int get() = incomingCount - conflictCount
}

internal data class ImportDecision(
    val backupOriginal: Boolean = true,
    val appendConflicts: Boolean = false,
)

@Composable
internal fun ImportPreviewDialog(
    preview: ImportPreview,
    parserWarning: String? = null,
    onDismiss: () -> Unit,
    onApply: (ImportDecision) -> Unit,
    isApplying: Boolean = false,
    // 调试台语义身份前缀: <prefix>_backup/_append/_dismiss/_apply; null = 不打标
    dbgTagPrefix: String? = null
) {
    // Prevent repeated submissions while the selected import is being saved.
    var backupOriginal by rememberSaveable(preview.targetTableId) { mutableStateOf(true) }
    var appendConflicts by rememberSaveable(preview.targetTableId) { mutableStateOf(false) }
    val apply = { if (!isApplying) onApply(ImportDecision(backupOriginal, appendConflicts)) }
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = { if (!isApplying) onDismiss() },
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (parserWarning != null) {
                    Text(parserWarning, color = colors.onErrorContainer,
                        modifier = Modifier.fillMaxWidth().background(colors.errorContainer).padding(12.dp))
                }
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
                if (preview.multiLocationWarnings.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SleepyTheme.shapes.large)
                            .background(colors.secondaryContainer)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.import_multi_location_warning),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSecondaryContainer
                        )
                        preview.multiLocationWarnings.take(5).forEach { warning ->
                            Text(
                                text = "• $warning",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSecondaryContainer
                            )
                        }
                        if (preview.multiLocationWarnings.size > 5) {
                            Text(
                                text = stringResource(
                                    R.string.more_unexpanded,
                                    preview.multiLocationWarnings.size - 5
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSecondaryContainer
                            )
                        }
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
                // sleepy-v1 (§7.3): 表级提示(非行级) — T行钳制/节点抬升/chk不符/n=不符/二次表头
                val warnings = preview.parseResult.warnings
                if (warnings.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SleepyTheme.shapes.large)
                            .background(colors.secondaryContainer)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.import_warnings_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSecondaryContainer
                        )
                        warnings.take(4).forEach { line ->
                            Text(
                                text = "• $line",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSecondaryContainer
                            )
                        }
                        if (warnings.size > 4) {
                            Text(
                                text = stringResource(R.string.import_conflict_more, warnings.size - 4),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            // AlertDialog reserves its action area before measuring the scrollable details.
            // Keep import choices here so conflicts and warnings cannot scroll them out of view.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (preview.targetTableId != 0L) {
                    ImportOptionSwitch(
                        title = stringResource(R.string.import_backup_original),
                        subtitle = stringResource(R.string.import_backup_original_sub),
                        checked = backupOriginal,
                        enabled = !isApplying,
                        dbgTag = dbgTagPrefix?.let { "${it}_backup" },
                        onCheckedChange = { backupOriginal = it },
                    )
                    ImportOptionSwitch(
                        title = stringResource(R.string.import_append_conflicts_option),
                        subtitle = stringResource(R.string.import_append_conflicts_option_sub),
                        checked = appendConflicts,
                        enabled = !isApplying,
                        dbgTag = dbgTagPrefix?.let { "${it}_append" },
                        onCheckedChange = { appendConflicts = it },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val cancelLabel = stringResource(R.string.cancel)
                    val applyLabel = stringResource(R.string.import_confirm)
                    TextButton(
                        enabled = !isApplying,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                            .then(dbgTagPrefix?.let { Modifier.dbgTag("${it}_dismiss", text = cancelLabel, role = "button") } ?: Modifier)
                    ) {
                        Text(cancelLabel, color = colors.onSurfaceVariant)
                    }
                    Button(
                        enabled = !isApplying,
                        onClick = apply,
                        modifier = Modifier.weight(1f)
                            .then(dbgTagPrefix?.let { Modifier.dbgTag("${it}_apply", text = applyLabel, role = "button") } ?: Modifier)
                    ) {
                        Text(applyLabel)
                    }
                }
            }
        },
        dismissButton = {}
    )
}

@Composable
private fun ImportOptionSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    dbgTag: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = dbgTag?.let { Modifier.dbgTag(it, text = title, role = "switch") } ?: Modifier
        )
    }
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
    val colors = MaterialTheme.colorScheme
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
    isApplying: Boolean,
    parseResult: ScheduleParser.ParseResult,
    onTableNameChange: (String) -> Unit,
    onStartDateChange: (String) -> Unit,
    onTimeJsonChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    // v1.0.56 T6: 第三 Tab「作息表」— 绑定现有作息表直接用; null=未绑定(用解析出的节次)
    periodTableOptions: List<com.lingion.sleepy.ui.component.PeriodTableOption> = emptyList(),
    selectedPeriodTableId: Long? = null,
    onSelectPeriodTable: (Long?) -> Unit = {},
    // id -> timeJson, 绑表时确认校验/落库以表 timeJson 为真源 (用户反馈 2026-09-20 误报修复)
    periodTableTimeJsonById: Map<Long, String> = emptyMap()
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val fieldColors = SleepyTheme.fieldColors()
    val startDateRequiredMessage = stringResource(R.string.import_start_date_required)
    val startDateFormatMessage = stringResource(R.string.start_date_format)
    val slotTimeRequiredFormat = stringResource(R.string.slot_time_required)
    val slotTimeInvalidFormat = stringResource(R.string.slot_time_invalid)
    var rows by remember(timeJson) {
        mutableStateOf(TimeTableUtils.parseTimeSlotRows(timeJson))
    }
    // issue#28 P2: 自动模式状态必须真实持有并回传 — 旧代码没传 smartConfig/
    // onSmartConfigChange, 落到默认 no-op, "添加课间"点了没有任何反应。
    // issue#23 T5: 初值 = 共享推断从导入解析出的行播种(45 分钟主时长 + 混合时长组
    // 一并识别, 与 EditTableScreen/PeriodTableEditScreen 同规); 行不可推断
    // (缺时间/畸形/断号) → 最简默认兜底, TimeSlotEditor 保持手动模式 + 既有校验。
    var smartConfig by remember {
        mutableStateOf(
            resolveAutoPeriodConfig(rows.toList(), null)
                ?: SmartPeriodConfig(
                    totalPeriods = rows.size.coerceAtLeast(1),
                    startTime = rows.firstOrNull()?.start?.takeIf { it.isNotBlank() } ?: "08:00"
                )
        )
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!isApplying) onDismiss() },
        title = { Text(stringResource(R.string.import_confirm_title), color = colors.onSurface) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.export_course_count, parseResult.courses.size))
                if (parseResult.droppedLines.isNotEmpty()) {
                    Text(stringResource(R.string.import_dropped_title, parseResult.droppedLines.size), color = colors.error)
                    parseResult.droppedLines.take(3).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                parseResult.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
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
                        modifier = Modifier.fillMaxWidth()
                            .dbgTag("import_name_field", role = "textbox"),
                        shape = SleepyTheme.fieldShape,
                        colors = fieldColors
                    )
                }
                DatePickerField(
                    value = startDate,
                    onValueChange = onStartDateChange,
                    label = stringResource(R.string.import_week_start),
                    modifier = Modifier.fillMaxWidth(),
                    dbgTag = "import_date_field",
                    // 只在日期错误时标红; 节次错误标到节次区(2026-09-20 反馈: 节次错也标日期框误导)
                    isError = errorMsg != null && (startDate.isBlank() ||
                        !Regex("""^\d{4}-\d{2}-\d{2}$""").matches(startDate))
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
                        },
                        smartConfig = smartConfig,
                        onSmartConfigChange = { smartConfig = it },
                        // v1.0.56 T6: 第三 Tab「作息表」
                        periodTableOptions = periodTableOptions,
                        selectedPeriodTableId = selectedPeriodTableId,
                        onSelectPeriodTable = onSelectPeriodTable,
                        dbgTagPrefix = "import_confirm_slot"
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                // 2026-09-16 用户: 裸 TextButton 无边界无色块 — 统一色块按钮行
                com.lingion.sleepy.ui.component.DialogActionButtons(
                    confirmText = stringResource(R.string.import_confirm),
                    confirmEnabled = !isApplying,
                    onConfirm = {
                        if (isApplying) return@DialogActionButtons
                        if (startDate.isBlank()) {
                            errorMsg = startDateRequiredMessage
                            return@DialogActionButtons
                        }
                        val dateRegex = Regex("""^\d{4}-\d{2}-\d{2}$""")
                        if (!dateRegex.matches(startDate) || runCatching { java.time.LocalDate.parse(startDate) }.isFailure) {
                            errorMsg = startDateFormatMessage
                            return@DialogActionButtons
                        }
                        // v1.0.56 T6 修正: 绑了作息表(id>0)时以表的 timeJson 为真源;
                        // 旧代码无条件校验手动 rows, 解析源没回节次时间 → 误报「第 X 节时间不能为空」(用户反馈 2026-09-20)
                        val effectiveRows = TimeTableUtils.effectiveRowsForConfirm(
                            manualRows = rows,
                            bindId = selectedPeriodTableId,
                            tables = periodTableTimeJsonById.map { it.key to it.value }
                        )
                        val emptyRows = effectiveRows.filter { it.start.isBlank() || it.end.isBlank() }
                        if (emptyRows.isNotEmpty()) {
                            errorMsg = slotTimeRequiredFormat.format(emptyRows.first().node)
                            return@DialogActionButtons
                        }
                        val timeRegex = Regex("""^\d{2}:\d{2}$""")
                        val invalidRows = effectiveRows.filter {
                            !timeRegex.matches(it.start) || !timeRegex.matches(it.end) ||
                            it.start >= it.end
                        }
                        if (invalidRows.isNotEmpty()) {
                            errorMsg = slotTimeInvalidFormat.format(invalidRows.first().node)
                            return@DialogActionButtons
                        }
                        errorMsg = null
                        onTimeJsonChange(TimeTableUtils.buildTimeJsonFromRows(effectiveRows))
                        onConfirm()
                    },
                    dismissText = stringResource(R.string.cancel),
                    onDismiss = { if (!isApplying) onDismiss() },
                    dbgTagPrefix = "import_confirm_dialog"
                )
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

private suspend fun buildImportPreview(
    text: String,
    tableId: Long,
    context: android.content.Context,
    onError: (String) -> Unit
): ImportPreview? {
    if (text.isBlank()) {
        onError(context.getString(R.string.import_content_empty))
        return null
    }
    // WakeUP 分享口令 → 拾光 wake_up.js v6.1.70 等价在线协议链(两段官方 POST)。
    // looksLikeShareCode 窄窗判别 — JSON/ICS/HTML/纯文本等离线形态不可能命中, 不与既有解析通路抢入口;
    // 口令、签名与 token 不落日志。
    val payload = if (WakeUpShareClient.looksLikeShareCode(text)) {
        runCatching { WakeUpShareClient.fetchScheduleJson(text) }
            .getOrElse {
                onError(context.getString(R.string.import_failed, it.message))
                return null
            }
    } else {
        text
    }
    // 新建入口使用 0L；加课入口始终携带用户进入页面时的目标 ID。
    val result = ScheduleParser.parse(payload, tableId)
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
    val repo = SleepyApp.get().repository
    val existingTable = if (tableId == 0L) null else repo.getTable(tableId)
    if (tableId != 0L && existingTable == null) error(context.getString(R.string.manage_no_table))
    val effective = existingTable?.hydratedWith(repo.effectivePeriodTable(tableId))
    val effectiveTimeJson = com.lingion.sleepy.util.CourseImportPolicy.mergeTimeJson(
        effective?.timeJson ?: "", parseResult.timeJson,
        maxOf(parseResult.nodesPerDay, parseResult.courses.maxOfOrNull { it.startNode + it.step - 1 } ?: 0)
    )
    val existingCourses = if (tableId == 0L) emptyList() else repo.getCourses(tableId)
    // issue#22: 同 groupId 多地点提示文案 — 按 (groupId) 聚合,
    // 有 ≥2 个非空不同地点时给一句提示,导入后会作为独立节次展示
    val multiLocWarnings = mutableListOf<String>()
    parseResult.courses.groupBy { it.groupId }.forEach { (gid, cs) ->
        if (gid.isBlank()) return@forEach
        val distinctRooms = cs.map { it.room.trim() }.distinct().filter { it.isNotEmpty() }
        if (distinctRooms.size >= 2) {
            multiLocWarnings += context.getString(
                R.string.import_multi_location_warning_detail,
                cs.first().courseName,
                distinctRooms.size
            )
        }
    }
    return createImportPreview(
        targetTableId = tableId,
        targetTableName = existingTable?.name ?: context.getString(R.string.manage_current_table),
        parseResult = parseResult,
        existingCourses = existingCourses,
        multiLocationWarnings = multiLocWarnings,
        effectiveTimeJson = effectiveTimeJson,
    )
}

internal fun createImportPreview(
    parseResult: ScheduleParser.ParseResult,
    targetTableId: Long,
    targetTableName: String,
    existingCourses: List<CourseEntity>,
    multiLocationWarnings: List<String> = emptyList(),
    effectiveTimeJson: String = "",
): ImportPreview = ImportPreview(
    targetTableId = targetTableId,
    targetTableName = targetTableName,
    parseResult = parseResult,
    existingCourses = existingCourses,
    conflicts = parseResult.courses.mapNotNull { incoming ->
        existingCourses.firstOrNull { existing -> com.lingion.sleepy.util.CourseImportPolicy.conflicts(incoming, existing, effectiveTimeJson) }
            ?.let { CourseConflict(incoming, it) }
    },
    multiLocationWarnings = multiLocationWarnings,
    effectiveTimeJson = effectiveTimeJson,
)

internal suspend fun applyCourseImport(
    preview: ImportPreview,
    decision: ImportDecision,
    context: Context,
    onImported: () -> Unit,
    onError: (String) -> Unit,
) {
    val repo = SleepyApp.get().repository
    val target = repo.getTable(preview.targetTableId)
    if (target == null) {
        onError(context.getString(R.string.manage_no_table))
        return
    }
    val added = repo.appendImportedCourses(
        tableId = preview.targetTableId,
        incoming = preview.parseResult.courses,
        incomingTimeJson = preview.parseResult.timeJson,
        incomingNodes = preview.parseResult.nodesPerDay,
        backupOriginal = decision.backupOriginal,
        appendConflicts = decision.appendConflicts,
        authoritativeGroups = preview.parseResult.groupIdsAuthoritative,
        backupName = context.getString(R.string.import_backup_name, target.name),
    )
    if (added == 0) onError(context.getString(R.string.import_all_conflict)) else onImported()
}

/**
 * v1.0.56 T9: 纯作息导入 — 只建一张作息表, 不建空课表。
 * 名字已由确认框查重; 落库前再走 suggestUniqueName 兜底(同屏并发导入等边缘)。
 */
private suspend fun applyPurePeriodImport(
    name: String,
    parsed: ScheduleParser.ParsedPeriodTable,
    onImported: () -> Unit,
    onError: (String) -> Unit
) {
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
        onImported()
    } catch (e: Exception) {
        onError(e.message ?: "import failed")
    } finally {
        com.lingion.sleepy.data.undo.UndoManager.endBatch()
    }
}

/** New-table imports cannot modify an existing timetable. */
internal suspend fun applyNewTableImport(
    preview: ImportPreview,
    confirmedStartDateRaw: String,
    confirmedTableName: String,
    confirmedTimeJson: String,
    context: Context,
    onImported: () -> Unit,
    bindPeriodTableId: Long? = null,
    confirmedSmartConfigJson: String = "",
) {
    val repo = SleepyApp.get().repository
    val confirmedStartDate = DateUtils.normalizeStartDate(confirmedStartDateRaw)
    val nodes = TimeTableUtils.parseTimeSlotRows(confirmedTimeJson).maxOfOrNull { it.node }
        ?: preview.parseResult.nodesPerDay.coerceAtLeast(1)
    val tableName = confirmedTableName.trim().ifBlank { context.getString(R.string.default_table_name) }
    val importedPeriod = com.lingion.sleepy.util.importedPeriodTableDraft(
        bindingId = bindPeriodTableId,
        name = preview.parseResult.periodTable?.name?.takeIf { it.isNotBlank() } ?: tableName,
        confirmedTimeJson = confirmedTimeJson,
    )?.copy(nodesPerDay = nodes, smartConfigJson = confirmedSmartConfigJson)
    repo.importNewTable(
        table = TimeTableEntity(
            name = tableName,
            startDate = confirmedStartDate,
            maxWeek = maxOf(preview.parseResult.maxWeek, preview.parseResult.courses.maxOfOrNull { it.endWeek } ?: 20),
            nodesPerDay = nodes,
            timeJson = confirmedTimeJson,
            smartConfigJson = confirmedSmartConfigJson,
            periodTableId = bindPeriodTableId,
        ),
        courses = preview.parseResult.courses,
        periodTable = importedPeriod,
        bindPeriodTableId = bindPeriodTableId,
        keepGroups = preview.parseResult.groupIdsAuthoritative,
    )
    onImported()
}
