package com.lingion.sleepy.ui.screen.imports

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.R
import com.lingion.sleepy.data.parser.ScheduleParser
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.SleepyTheme
import kotlinx.coroutines.launch

@Composable
fun ImportScreen(
    onImported: () -> Unit,
    onManualAdd: () -> Unit = {},
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val colors = SleepyTheme.colors

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isLoading = true
                try {
                    val text = context.contentResolver.openInputStream(it)?.bufferedReader()?.readText()
                        ?: throw Exception("无法读取文件")
                    processImport(text, state, viewModel, onImported) { msg -> errorMsg = msg }
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
                        text = "支持多种格式导入",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            // 方式选择 - 4 个图标卡
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
                        label = stringResource(R.string.import_qr),
                        modifier = Modifier.weight(1f),
                        onClick = { errorMsg = "扫码功能开发中" }
                    )
                    ImportMethodChip(
                        icon = Icons.Outlined.Edit,
                        label = stringResource(R.string.import_manual),
                        modifier = Modifier.weight(1f),
                        onClick = onManualAdd
                    )
                }
            }

            // 文本粘贴卡
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
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        placeholder = { Text("粘贴课表文本…", color = colors.onSurfaceVariant) },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                try {
                                    processImport(inputText, state, viewModel, onImported) { msg -> errorMsg = msg }
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !isLoading && inputText.isNotBlank(),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Text(
                            text = if (isLoading) "导入中…" else "导入",
                            color = colors.onPrimary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            // 格式说明卡
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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

private suspend fun processImport(
    text: String,
    state: com.lingion.sleepy.ui.screen.schedule.ScheduleState,
    viewModel: ScheduleViewModel,
    onImported: () -> Unit,
    onError: (String) -> Unit
) {
    if (text.isBlank()) {
        onError("内容为空")
        return
    }
    val tableId = state.selectedTableId
    if (tableId == null) {
        onError("请先选择课表")
        return
    }

    val result = ScheduleParser.parse(text, tableId)
    result.fold(
        onSuccess = { parseResult ->
            val repo = com.lingion.sleepy.SleepyApp.get().repository
            val existing = repo.getTable(tableId)
            if (existing != null) {
                // 保留用户已设置的 startDate；仅在原表 startDate 为空或与 parser 返回一致时才覆盖
                // 这样 重复导入同一份 CSV 不会把学期开始日期重置成“今天”
                val keepStart = existing.startDate.isNotBlank()
                repo.updateTable(existing.copy(
                    name = parseResult.tableName,
                    startDate = if (keepStart) existing.startDate else parseResult.startDate
                ))
            }
            repo.replaceCourses(tableId, parseResult.courses)
            onImported()
        },
        onFailure = { e ->
            onError("解析失败: ${e.message}")
        }
    )
}

