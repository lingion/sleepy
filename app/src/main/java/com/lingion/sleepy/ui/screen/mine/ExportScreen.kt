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
 * 导出课表页 — 把当前课表导出为：
 * 1. WakeUp 兼容 JSON（文件下载 + 分享）
 * 2. WakeUp 分享文本（系统分享面板）
 * 3. ICS 日历（文件下载 + 分享）
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    onBack: () -> Unit,
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = SleepyTheme.colors
    val snackbarHostState = remember { SnackbarHostState() }

    // 导出目标课表 — 本地选择, 不污染主页 selectedTableId/widget 默认表。
    // 默认跟随当前课表; 用户切过一次后(pin)固定, 除非主页切到 pin 掉的表之外又变了。
    var exportTableId by remember { mutableStateOf<Long?>(null) }
    val effectiveId = exportTableId ?: state.selectedTableId
    val table = state.tables.find { it.id == effectiveId } ?: state.currentTable
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
        if (table == null) {
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
            // 顶部信息卡 — 点击拉出课表选择下拉(默认当前课表)
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
                            text = table.name,
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
                        text = "${ctx.getString(R.string.export_course_count, courses.size)} · ${ctx.getString(R.string.export_start_date, table.startDate)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onPrimaryContainer
                    )
                }
            }

            // 格式选项
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
                                        table.timeJson, courses
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

    // 导出课表选择弹窗 — 行样式对齐 ScheduleScreen TableSwitcherDialog(用户定版视觉)
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
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tables.forEach { t ->
                        val isSelected = t.id == effectiveId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SleepyTheme.shapes.small)
                                .background(if (isSelected) colors.primaryContainer else colors.surfaceContainer)
                                .noRippleClickable {
                                    exportTableId = t.id
                                    showTablePicker = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = t.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = if (isSelected) colors.onPrimaryContainer else colors.onSurface,
                                    maxLines = 2
                                )
                                if (t.id == state.selectedTableId) {
                                    Text(
                                        text = stringResource(R.string.export_current_table_badge),
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
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
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