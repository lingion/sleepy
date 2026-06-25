package com.lingion.sleepy.ui.screen.mine

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.parser.ScheduleExporter
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.SleepyTheme
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

    val table = state.currentTable
    val courses = state.courses

    Scaffold(
        modifier = Modifier.fillMaxSize().background(colors.background),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.export_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp)
                            .clickable(onClick = onBack)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground
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
            // 顶部信息卡
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.primaryContainer)
                        .padding(20.dp)
                ) {
                    Text(
                        text = table.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimaryContainer
                    )
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
                        .clip(RoundedCornerShape(20.dp))
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
                                    displayName = table.name
                                )
                            }
                        }
                    )
                    Divider(colors.outlineVariant.copy(alpha = 0.5f))
                    ExportItem(
                        icon = Icons.Outlined.Share,
                        title = stringResource(R.string.export_share_title),
                        subtitle = stringResource(R.string.export_share_subtitle),
                        onClick = {
                            scope.launch {
                                shareText(
                                    ctx = ctx,
                                    content = ScheduleExporter.exportWakeUpShareText(table, courses),
                                    subject = table.name
                                )
                            }
                        }
                    )
                    Divider(colors.outlineVariant.copy(alpha = 0.5f))
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
                                    displayName = table.name
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = SleepyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primary
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

private fun stamp(): String =
    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

/** 用 MediaStore API 写到公共 Downloads 目录（无需存储权限，Android 10+），然后触发分享 */
private suspend fun exportAndShare(
    ctx: android.content.Context,
    fileName: String,
    mime: String,
    content: String,
    displayName: String
) {
    withContext(Dispatchers.IO) {
        val uri = withContext(Dispatchers.IO) {
            writeToDownloads(ctx, fileName, mime, content)
        }
        if (uri == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(ctx, R.string.export_failed, Toast.LENGTH_SHORT).show()
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
            Toast.makeText(
                ctx,
                ctx.getString(R.string.export_saved_to, fileName),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

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
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/Sleepy")
                put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
            }
        }
        val resolver = ctx.contentResolver
        val collection = android.provider.MediaStore.Downloads.getContentUri(
            android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
        )
        val uri = resolver.insert(collection, values) ?: return null
        resolver.openOutputStream(uri)?.use { os -> os.write(content.toByteArray(Charsets.UTF_8)) }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            values.clear()
            values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        uri
    } catch (e: Exception) {
        android.util.Log.e("ExportScreen", "writeToDownloads failed", e)
        null
    }
}

/** 直接分享文本 */
private fun shareText(ctx: android.content.Context, content: String, subject: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, content)
        putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    ctx.startActivity(Intent.createChooser(intent, ctx.getString(R.string.export_share_chooser)))
    Toast.makeText(ctx, R.string.export_copied_hint, Toast.LENGTH_SHORT).show()
}