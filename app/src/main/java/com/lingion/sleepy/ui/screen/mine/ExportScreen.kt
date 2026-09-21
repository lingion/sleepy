package com.lingion.sleepy.ui.screen.mine

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.SleepyApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.lingion.sleepy.data.parser.ScheduleExporter
import com.lingion.sleepy.data.parser.SleepyNativeExporter
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 导出页 — 2026-09-16 用户重构:
 * 顶部展开框上半列全部课表、横向分隔线、下半列全部作息表(优先课表全部展开完)。
 * 选中课表 → 四个格式项(JSON/分享文本/ICS/原生); 选中作息表 → 自动只剩两项
 * (Sleepy 原生 + JSON), 其余格式对纯作息无意义。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    onBack: () -> Unit,
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val allPeriodTables by viewModel.allPeriodTables.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = SleepyTheme.colors
    val snackbarHostState = remember { SnackbarHostState() }

    // 导出目标 — 本地选择, 不污染主页 selectedTableId/widget 默认表。
    // 二元选择: courseTableId(课表) / periodTableId(作息表); null,null = 未选(跟随当前课表)
    var exportTableId by remember { mutableStateOf<Long?>(null) }
    var exportPeriodTableId by remember { mutableStateOf<Long?>(null) }
    val effectiveId = if (exportPeriodTableId != null) null else (exportTableId ?: state.selectedTableId)
    val table = state.tables.find { it.id == effectiveId } ?: state.currentTable
    val selectedPeriodTable = allPeriodTables.find { it.id == exportPeriodTableId }
    val tables = state.tables

    // 选中表的课程: 当前表直接用 state.courses(已观察), 其他表选中时本地加载一次
    var loadedCourses by remember(effectiveId) { mutableStateOf<List<CourseEntity>?>(null) }
    LaunchedEffect(effectiveId, state.courses) {
        val tid = effectiveId
        if (table != null && tid != null && tid != state.selectedTableId) {
            loadedCourses = withContext(Dispatchers.IO) {
                SleepyApp.get().repository.getCourses(tid)
            }
        } else {
            loadedCourses = null
        }
    }
    val courses = loadedCourses ?: state.courses

    var showTablePicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(colors.background),
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.export_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
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
        if (table == null && selectedPeriodTable == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.export_no_table), color = colors.onSurfaceVariant)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 顶部信息卡 — 点击拉出「课表/作息表」展开框(上半课表+分隔线+下半作息表)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SleepyTheme.shapes.large)
                        .background(colors.primaryContainer)
                        .noRippleClickable { showTablePicker = true }
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = selectedPeriodTable?.name ?: table?.name ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Outlined.ExpandMore,
                            contentDescription = stringResource(R.string.export_pick_table),
                            tint = colors.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (selectedPeriodTable != null) {
                            ctx.getString(R.string.period_tables_title) + " · " +
                                ctx.getString(R.string.period_table_nodes_count, selectedPeriodTable.nodesPerDay)
                        } else {
                            "${ctx.getString(R.string.export_course_count, courses.size)} · ${ctx.getString(R.string.export_start_date, table?.startDate ?: "")}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onPrimaryContainer
                    )
                }
            }

            // 格式选项 — 选中作息表 → 只剩 JSON + 原生两项(用户 2026-09-16);
            // 选中课表 → 完整四项
            if (selectedPeriodTable != null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SleepyTheme.shapes.large)
                            .background(colors.surfaceContainer)
                    ) {
                        ExportItem(
                            icon = Icons.Outlined.Star,
                            title = stringResource(R.string.period_table_share_native_title),
                            subtitle = stringResource(R.string.period_table_share_native_sub),
                            onClick = {
                                scope.launch {
                                    exportAndShare(
                                        ctx = ctx,
                                        fileName = "sleepy_${selectedPeriodTable.name}_${stamp()}.sleepy",
                                        mime = "text/plain",
                                        content = SleepyNativeExporter.exportPeriodTableShareText(selectedPeriodTable),
                                        displayName = selectedPeriodTable.name,
                                        onResult = { msg -> snackbarHostState.showSnackbar(msg) }
                                    )
                                }
                            }
                        )
                        Divider(colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                        ExportItem(
                            icon = Icons.Outlined.Code,
                            title = stringResource(R.string.period_table_share_json_title),
                            subtitle = stringResource(R.string.period_table_share_json_sub),
                            onClick = {
                                scope.launch {
                                    exportAndShare(
                                        ctx = ctx,
                                        fileName = "sleepy_${selectedPeriodTable.name}_${stamp()}.json",
                                        mime = "application/json",
                                        content = SleepyNativeExporter.exportPeriodTableJson(selectedPeriodTable),
                                        displayName = selectedPeriodTable.name,
                                        onResult = { msg -> snackbarHostState.showSnackbar(msg) }
                                    )
                                }
                            }
                        )
                    }
                }
            } else if (table != null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SleepyTheme.shapes.large)
                        .background(colors.surfaceContainer)
                ) {
                    ExportItem(
                        icon = Icons.Outlined.Code,
                        title = stringResource(R.string.export_json_title),
                        subtitle = stringResource(R.string.export_json_subtitle),
                        onClick = {
                            scope.launch {
                                exportAndShare(
                                    ctx = ctx,
                                    fileName = "sleepy_${table.name}_${stamp()}.json",
                                    mime = "application/json",
                                    content = ScheduleExporter.exportWakeUpJson(table, courses),
                                    displayName = table.name,
                                    onResult = { msg -> snackbarHostState.showSnackbar(msg) }
                                )
                            }
                        }
                    )
                    Divider(colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                    ExportItem(
                        icon = Icons.Outlined.Share,
                        title = stringResource(R.string.export_share_title),
                        subtitle = stringResource(R.string.export_share_subtitle),
                        onClick = {
                            scope.launch {
                                shareText(
                                    ctx = ctx,
                                    content = ScheduleExporter.exportWakeUpShareText(table, courses),
                                    subject = table.name,
                                    onResult = { msg -> snackbarHostState.showSnackbar(msg) }
                                )
                            }
                        }
                    )
                    Divider(colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                    ExportItem(
                        icon = Icons.Outlined.CalendarMonth,
                        title = stringResource(R.string.export_ics_title),
                        subtitle = stringResource(R.string.export_ics_subtitle),
                        onClick = {
                            scope.launch {
                                exportAndShare(
                                    ctx = ctx,
                                    fileName = "sleepy_${table.name}_${stamp()}.ics",
                                    mime = "text/calendar",
                                    content = ScheduleExporter.exportIcs(table, courses),
                                    displayName = table.name,
                                    onResult = { msg -> snackbarHostState.showSnackbar(msg) }
                                )
                            }
                        }
                    )
                    Divider(colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                    ExportItem(
                        icon = Icons.Outlined.Star,
                        title = stringResource(R.string.export_native_title),
                        subtitle = stringResource(R.string.export_native_subtitle),
                        onClick = {
                            scope.launch {
                                exportAndShare(
                                    ctx = ctx,
                                    fileName = "sleepy_${table.name}_${stamp()}.sleepy",
                                    mime = "text/plain",
                                    // MIME 用 text/plain 规避 ImportReceiverActivity MIME 收窄问题(调查报告 P3)
                                    content = SleepyNativeExporter.exportFile(
                                        table.name, table.startDate, table.maxWeek, table.nodesPerDay,
                                        table.timeJson, courses,
                                        // issue#40 §6: 绑定了独立时间节次表时携带 P 区块(共享关系可往返)
                                        periodTable = state.tables
                                            .firstOrNull { it.id == table.id }
                                            ?.periodTableId
                                            ?.let { boundId -> allPeriodTables.find { it.id == boundId } }
                                            ?.let {
                                                SleepyNativeExporter.PeriodTableExport(
                                                    id = it.id, name = it.name,
                                                    nodesPerDay = it.nodesPerDay, timeJson = it.timeJson
                                                )
                                            }
                                    ),
                                    displayName = table.name,
                                    onResult = { msg -> snackbarHostState.showSnackbar(msg) }
                                )
                            }
                        }
                    )
                }
            }
            }
        }
    }

    // 2026-09-16 展开框重构: 上半 = 全部课表展开, 横向分隔线, 下半 = 全部作息表。
    // 用户指定顺序 — 课表优先展开完, 再作息表; 行样式对齐 TableSwitcherDialog(用户定版视觉)。
    if (showTablePicker) {
        AlertDialog(
            onDismissRequest = { showTablePicker = false },
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurfaceVariant,
            title = { Text(stringResource(R.string.export_pick_table)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // ---- 上半: 全部课表 ----
                    tables.forEach { t ->
                        val isSelected = exportPeriodTableId == null && t.id == effectiveId
                        PickerRow(
                            title = t.name,
                            subtitle = if (t.id == state.selectedTableId) stringResource(R.string.export_current_table_badge) else null,
                            isSelected = isSelected,
                            onClick = {
                                exportPeriodTableId = null
                                exportTableId = t.id
                                showTablePicker = false
                            }
                        )
                    }
                    // ---- 横向分隔线 ----
                    if (allPeriodTables.isNotEmpty()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            thickness = 0.5.dp,
                            color = colors.outlineVariant
                        )
                    }
                    // ---- 下半: 全部作息表 ----
                    allPeriodTables.forEach { pt ->
                        val isSelected = exportPeriodTableId == pt.id
                        PickerRow(
                            title = pt.name,
                            subtitle = stringResource(R.string.period_tables_title),
                            isSelected = isSelected,
                            onClick = {
                                exportPeriodTableId = pt.id
                                exportTableId = null
                                showTablePicker = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }
}

/** 展开框行 — 课表/作息表共用视觉(选中态 primaryContainer 色块+对勾, 禁描边规则) */
@Composable
private fun PickerRow(title: String, subtitle: String?, isSelected: Boolean, onClick: () -> Unit) {
    val colors = SleepyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SleepyTheme.shapes.small)
            .background(if (isSelected) colors.primaryContainer else colors.surfaceContainer)
            .noRippleClickable(onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (isSelected) colors.onPrimaryContainer else colors.onSurface,
                maxLines = 2
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant
                )
            }
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** internal: 顶栏分享底部弹窗(ShareScheduleSheet)复用同款条目视觉 */
@Composable
internal fun ExportItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = SleepyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(SleepyTheme.shapes.medium)
                // 对齐 MineScreen.SettingsItem 同语义图标容器（primaryContainer），
                // 之前 primary.copy(0.12f) 与本文件顶部信息卡的 primaryContainer 也不一致
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = colors.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun Divider(color: Color) {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = color
    )
}

internal fun stamp(): String =
    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

/** 用 MediaStore API 写到公共 Downloads 目录（无需存储权限，Android 10+），然后触发分享。
 *  API 26-28 无 MediaStore.Downloads → 回退写到应用 cache 目录经 FileProvider 分享
 *  internal: 顶栏分享底部弹窗(ShareScheduleSheet)复用 */
internal suspend fun exportAndShare(
    ctx: android.content.Context,
    fileName: String,
    mime: String,
    content: String,
    displayName: String,
    onResult: suspend (String) -> Unit
) {
    withContext(Dispatchers.IO) {
        val uri = withContext(Dispatchers.IO) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                writeToDownloads(ctx, fileName, mime, content)
            } else {
                writeToCacheViaFileProvider(ctx, fileName, content)
            }
        }
        if (uri == null) {
            withContext(Dispatchers.Main) {
                onResult(ctx.getString(R.string.export_failed))
            }
            return@withContext
        }
        withContext(Dispatchers.Main) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, displayName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.export_share_chooser)))
            onResult(ctx.getString(R.string.export_saved_to, fileName))
        }
    }
}

/** 仅在 Q(29)+ 被调用(API<29 由 exportAndShare 分流到 writeToCacheViaFileProvider):
 *    MediaStore.Downloads 整族 API 29 新增, 函数内不再需要 Q 判断 */
@androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
private fun writeToDownloads(
    ctx: android.content.Context,
    fileName: String,
    mime: String,
    content: String
): android.net.Uri? {
    return try {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, mime)
            put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/Sleepy")
            put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val collection = android.provider.MediaStore.Downloads.getContentUri(
            android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
        )
        val uri = resolver.insert(collection, values) ?: return null
        resolver.openOutputStream(uri)?.use { os -> os.write(content.toByteArray(Charsets.UTF_8)) }
        values.clear()
        values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        uri
    } catch (e: Exception) {
        android.util.Log.e("ExportScreen", "writeToDownloads failed", e)
        null
    }
}

/** API 26-28 回退: 写入应用 cache 目录, 经 FileProvider(cache-path, 见 xml/file_paths.xml)生成 content Uri 供分享 */
private fun writeToCacheViaFileProvider(
    ctx: android.content.Context,
    fileName: String,
    content: String
): android.net.Uri? {
    return try {
        val dir = java.io.File(ctx.cacheDir, "exports").apply { mkdirs() }
        val file = java.io.File(dir, fileName)
        file.writeText(content, Charsets.UTF_8)
        androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    } catch (e: Exception) {
        android.util.Log.e("ExportScreen", "writeToCacheViaFileProvider failed", e)
        null
    }
}

/** 直接分享文本 (internal: 顶栏分享底部弹窗复用) */
internal suspend fun shareText(
    ctx: android.content.Context,
    content: String,
    subject: String,
    onResult: suspend (String) -> Unit
) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, content)
        putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    ctx.startActivity(Intent.createChooser(intent, ctx.getString(R.string.export_share_chooser)))
    onResult(ctx.getString(R.string.export_copied_hint))
}