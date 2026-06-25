package com.lingion.sleepy.ui.screen.imports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.component.DatePickerField
import com.lingion.sleepy.ui.component.TimeSlotEditor
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.TimeTableUtils

/**
 * 教务导入 → 解析成功后 → 配置节次时间 / 开始日期 / 表名 → 确认入库。
 *
 * 这一步是 WakeUp 课程表的标准流程。app 早期版本 [JwImportActivity] 缺这一步导致
 * "导入即落库，跳过节次时间/开始日期询问" 的 bug（所有学校都受影响）。
 *
 * 该 Composable 完全复用：
 *  - [TimeSlotEditor] 编辑节次时间（与 [EditTableScreen] 同源）
 *  - [DatePickerField] 选学期开始日期（Material3 原生 DatePicker）
 *  - [TimeTableUtils.parseTimeSlotRows] / [TimeTableUtils.buildTimeJsonFromRows] 互转 JSON
 *
 * 表单校验规则与 EditTableScreen 完全一致：
 *  - startDate 匹配 `\d{4}-\d{2}-\d{2}`
 *  - 每节 start/end 匹配 `\d{2}:\d{2}`
 *  - 每节 start < end
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun JwImportConfigScreen(
    schoolName: String,
    courseCount: Int,
    defaultStartDate: String,
    defaultTableName: String,
    initialTimeJson: String? = null,
    onBack: () -> Unit,
    onConfirm: (tableName: String, startDate: String, timeJson: String) -> Unit,
) {
    val colors = SleepyTheme.colors
    val context = LocalContext.current

    var name by remember { mutableStateOf(defaultTableName) }
    var startDate by remember { mutableStateOf(defaultStartDate) }
    var timeSlotsExpanded by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val slotRows = remember {
        mutableStateListOf<TimeTableUtils.TimeSlotRow>().apply {
            addAll(TimeTableUtils.parseTimeSlotRows(initialTimeJson ?: TimeTableUtils.DEFAULT_TIME_JSON))
        }
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.jw_config_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
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
        },
        containerColor = colors.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

            // 摘要
            item {
                SummaryCard(
                    schoolName = schoolName,
                    courseCount = courseCount,
                    periodCount = slotRows.size
                )
            }

            // 基础信息：表名 + 开始日期
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.surfaceContainer)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.edit_table_basic_info),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.import_table_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = fieldColors
                    )
                    DatePickerField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = stringResource(R.string.import_start_date),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        isError = error != null
                    )
                }
            }

            // 节次时间表（可折叠）
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.surfaceContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { timeSlotsExpanded = !timeSlotsExpanded }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                stringResource(R.string.edit_table_time_slots),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.onSurface
                            )
                            Text(
                                text = stringResource(R.string.n_periods, slotRows.size) + " · " +
                                    if (timeSlotsExpanded) stringResource(R.string.collapse)
                                    else stringResource(R.string.expand),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                        Icon(
                            if (timeSlotsExpanded) Icons.Outlined.ExpandLess
                            else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant
                        )
                    }
                    if (timeSlotsExpanded) {
                        Column(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        ) {
                            TimeSlotEditor(
                                rows = slotRows.toList(),
                                onRowsChange = { newRows ->
                                    slotRows.clear()
                                    slotRows.addAll(newRows)
                                }
                            )
                        }
                    }
                }
            }

            error?.let { msg ->
                item {
                    Text(text = msg, color = colors.error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // 确认按钮
            item {
                Button(
                    onClick = {
                        val valid = startDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) &&
                            slotRows.isNotEmpty() &&
                            slotRows.all {
                                it.start.matches(Regex("\\d{2}:\\d{2}")) &&
                                it.end.matches(Regex("\\d{2}:\\d{2}")) &&
                                it.start < it.end
                            }
                        if (!valid) {
                            error = context.getString(R.string.edit_table_validation_error)
                            return@Button
                        }
                        error = null
                        val timeJson = TimeTableUtils.buildTimeJsonFromRows(slotRows.toList())
                        onConfirm(name, startDate, timeJson)
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.jw_config_confirm))
                }
            }

            item { Spacer(modifier = Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun SummaryCard(schoolName: String, courseCount: Int, periodCount: Int) {
    val colors = SleepyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = schoolName,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface
        )
        Text(
            text = stringResource(R.string.jw_config_summary, courseCount, periodCount),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
    }
}