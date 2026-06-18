package com.lingion.sleepy.ui.screen.mine

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.ui.component.TimePickerField
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.SleepyTheme
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

// Time slot editing uses mutableStateListOf for reactive TextField binding

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EditTableScreen(
    tableId: Long? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val colors = SleepyTheme.colors
    val scope = rememberCoroutineScope()

    // tableId == null means edit current table
    val table = if (tableId != null) state.tables.find { it.id == tableId } else state.currentTable

    if (table == null) {
        Scaffold(containerColor = colors.background) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("课表数据未找到", color = colors.onBackground)
            }
        }
        return
    }

    var name by remember(table.id) { mutableStateOf(table.name) }
    var startDate by remember(table.id) { mutableStateOf(table.startDate) }
    var maxWeekText by remember(table.id) { mutableStateOf(table.maxWeek.toString()) }
    var timeSlotsExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val timeJson = table.timeJson
    val slotNodes = remember(table.id) {
        parseTimeSlotNodes(timeJson)
    }
    val startValues = remember(table.id) {
        mutableStateListOf<String>().apply {
            addAll(slotNodes.indices.map { i -> parseSlotTime(timeJson, i, "start") })
        }
    }
    val endValues = remember(table.id) {
        mutableStateListOf<String>().apply {
            addAll(slotNodes.indices.map { i -> parseSlotTime(timeJson, i, "end") })
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
                title = { Text("编辑课表") },
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
        containerColor = colors.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

            // 基础信息
            item {
                CardSection("基础信息", "") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("课表名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = startDate,
                            onValueChange = { startDate = it },
                            label = { Text("学期开始日期") },
                            placeholder = { Text("例如 2026-02-24") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = maxWeekText,
                            onValueChange = { maxWeekText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("总周数") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = fieldColors
                        )
                    }
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
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "节次时间表",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.onSurface
                            )
                            Text(
                                text = "${slotNodes.size} 节课 · ${if (timeSlotsExpanded) "点击收起" else "点击展开"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                        Icon(
                            if (timeSlotsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant
                        )
                    }

                    if (timeSlotsExpanded) {
                        Column(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            repeat(slotNodes.size) { index ->
                                key(slotNodes[index]) {
                                val node = slotNodes[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(colors.surfaceContainerLow, RoundedCornerShape(16.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "第${node}节",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.onSurface,
                                        modifier = Modifier.width(48.dp)
                                    )
                                    TimePickerField(
                                        value = startValues[index],
                                        onValueChange = { v -> startValues[index] = v },
                                        label = "开始",
                                        modifier = Modifier.weight(1f)
                                    )
                                    TimePickerField(
                                        value = endValues[index],
                                        onValueChange = { v -> endValues[index] = v },
                                        label = "结束",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                } // end key
                            }
                        }
                    }
                }
            }

            error?.let { msg ->
                item {
                    Text(text = msg, color = colors.error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // 保存
            item {
                Button(
                    onClick = {
                        val maxWeek = maxWeekText.toIntOrNull() ?: 20
                        val valid = startDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && 
                            startValues.all { it.matches(Regex("\\d{2}:\\d{2}")) } &&
                            endValues.all { it.matches(Regex("\\d{2}:\\d{2}")) }
                        if (!valid) {
                            error = "日期需为 yyyy-MM-dd，时间需为 HH:mm"
                            return@Button
                        }
                        error = null
                        val updated = table.copy(
                            name = name.ifBlank { table.name },
                            startDate = startDate,
                            maxWeek = maxWeek,
                            timeJson = buildTimeJson(slotNodes, startValues, endValues)
                        )
                        scope.launch {
                            viewModel.updateTable(updated)
                            onSaved()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("保存课表设置")
                }
            }

            // 删除
            item {
                Button(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.errorContainer)
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = colors.onErrorContainer)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("删除课表", color = colors.onErrorContainer)
                }
            }

            item { Spacer(modifier = Modifier.height(28.dp)) }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = colors.surface,
            title = { Text("确认删除", color = colors.onSurface) },
            text = { Text("确定要删除课表「${table.name}」吗？此操作不可撤销。", color = colors.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        viewModel.deleteTable(table.id)
                        onDeleted()
                    }
                }) { Text("删除", color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun CardSection(title: String, subtitle: String, content: @Composable () -> Unit) {
    val colors = SleepyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        content()
    }
}

private fun parseTimeSlotNodes(timeJson: String): List<Int> = try {
    val arr = JSONArray(timeJson)
    buildList {
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            add(o.optInt("node", i + 1))
        }
    }
} catch (_: Exception) {
    parseTimeSlotNodes(TimeTableUtils.DEFAULT_TIME_JSON)
}

private fun parseSlotTime(timeJson: String, index: Int, key: String): String = try {
    val arr = JSONArray(timeJson)
    if (index < arr.length()) {
        arr.getJSONObject(index).optString(key, if (key == "start") "08:00" else "08:45")
    } else if (key == "start") "08:00" else "08:45"
} catch (_: Exception) {
    if (key == "start") "08:00" else "08:45"
}

private fun buildTimeJson(nodes: List<Int>, startValues: List<String>, endValues: List<String>): String {
    val arr = JSONArray()
    nodes.forEachIndexed { i, node ->
        arr.put(JSONObject().apply { 
            put("node", node)
            put("start", startValues.getOrElse(i) { "08:00" })
            put("end", endValues.getOrElse(i) { "08:45" })
        })
    }
    return arr.toString()
}
