package com.lingion.sleepy.ui.screen.mine

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.SmartPeriodConfig
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.ui.component.DatePickerField
import com.lingion.sleepy.ui.component.PeriodTableOption as TimeSlotEditorPeriodTableOption
import com.lingion.sleepy.ui.component.TimeSlotEditor
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Time slot editing uses mutableStateListOf for reactive TextField binding

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EditTableScreen(
    tableId: Long? = null,
    pendingNewTableId: Long? = null,
    onBack: () -> Unit,
    onDiscardPending: () -> Unit = onBack,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    // issue#40: 全部时间节次表(换绑选择器数据源 §4.3)
    val allPeriodTables by viewModel.allPeriodTables.collectAsState()
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val validationErrorMessage = stringResource(R.string.edit_table_validation_error)

    // tableId == null means edit current table
    val table = if (tableId != null) state.tables.find { it.id == tableId } else state.currentTable

    if (table == null) {
        Scaffold(containerColor = colors.background) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(stringResource(R.string.edit_table_not_found), color = colors.onBackground)
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
    // issue#40: 换绑选择(§5.3) — null 起始 = 未动过; 确认时才写 periodTableId。
    // pendingBind != table.periodTableId 时保存流程走换绑分支。
    var pendingBind by remember(table.id, table.periodTableId) { mutableStateOf<Long?>(table.periodTableId) }
    // v1.0.56 T6: bindExpanded 已随独立换绑卡拆除 — 绑定入口唯一化(第三 Tab)
    // issue#40 §5.3: 换绑确认弹窗 — 非 null 时弹「确认换绑」, 确认才真正写 periodTableId
    var pendingRebind by remember { mutableStateOf<Long?>(null) }
    // 换绑确认跨越保存按钮与弹窗回调, 暂存待写回的课程表元数据。
    var pendingRebindTable by remember { mutableStateOf<TimeTableEntity?>(null) }
    // 用户在编辑区对目标作息表的节次/智慧节次编辑也一并暂存, 确认时落 target period。
    var pendingRebindPeriod by remember {
        mutableStateOf<com.lingion.sleepy.data.entity.PeriodTableEntity?>(null)
    }

    // issue#40: 编辑的就是"有效时间表" — 绑定了独立时间节次表时, 节次编辑区
    // 展示/修改的是该时间节次表(多张绑定课表同享), 保存写回 period_tables;
    // 未绑定时行为不变(编辑本表兼容列)。
    // 换绑修复: 有效表跟随 pendingBind(用户在下拉里改选时立即切换编辑区来源),
    // 否则已绑定的表 effectivePeriodTable 恒非空, 保存永远走"写回旧表"分支, 换绑成死代码。
    val effectivePeriodTable = state.effectivePeriodTable?.takeIf {
        pendingBind != null && it.id == pendingBind
    } ?: allPeriodTables.find { it.id == pendingBind }
    val timeJson = effectivePeriodTable?.timeJson ?: table.timeJson
    val slotRows = remember(table.id, effectivePeriodTable?.id, timeJson) {
        mutableStateListOf<TimeTableUtils.TimeSlotRow>().apply {
            addAll(TimeTableUtils.parseTimeSlotRows(timeJson))
        }
    }
    // v1.0.16 自动模式配置（编辑当前课表时使用）
    // issue#23 Task 4: 已存配置仍能 derive 出当前行 → 原样保留; 否则从当前行重推断;
    // 行不可推断 → 最简默认兜底(旧行为)。
    val smartConfig = remember(table.id, effectivePeriodTable?.id, timeJson) {
        val stored = com.lingion.sleepy.ui.component.decodeSmartPeriodConfig(
            effectivePeriodTable?.smartConfigJson ?: table.smartConfigJson
        )
        mutableStateOf(
            com.lingion.sleepy.ui.component.resolveAutoPeriodConfig(slotRows.toList(), stored)
                ?: SmartPeriodConfig(
                    totalPeriods = slotRows.size.coerceAtLeast(1),
                    startTime = slotRows.firstOrNull()?.start?.takeIf { it.isNotBlank() } ?: "08:00"
                )
        )
    }

    val fieldColors = SleepyTheme.fieldColors()

    val handleBack = {
        if (pendingNewTableId != null) onDiscardPending() else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_table_title)) },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
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
                CardSection(stringResource(R.string.edit_table_basic_info), "") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.edit_table_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = SleepyTheme.fieldShape,
                            colors = fieldColors
                        )
                        // 学期开始日期: 手输 + 日历图标弹原生 DatePicker(与导入确认弹窗同款组件)。
                        //   之前是裸 TextField 只能输数字, 用户没法直观改日期。
                        DatePickerField(
                            value = startDate,
                            onValueChange = { startDate = it },
                            label = stringResource(R.string.edit_table_start_date),
                            modifier = Modifier.fillMaxWidth(),
                            isError = error != null
                        )
                        TextField(
                            value = maxWeekText,
                            onValueChange = { maxWeekText = it.filter { ch -> ch.isDigit() } },
                            label = { Text(stringResource(R.string.edit_table_max_week)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = SleepyTheme.fieldShape,
                            colors = fieldColors
                        )
                    }
                }
            }

            // v1.0.56 T6: 原独立「时间节次表」换绑卡已拆 — 绑定选择收编进下方节次编辑卡
            // 的第三 Tab「作息表」(未绑定/选中语义零变化, pendingBind 保存链原样)

            // 节次时间表（可折叠）
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SleepyTheme.shapes.extraLarge)
                        .background(colors.surfaceContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .noRippleClickable { timeSlotsExpanded = !timeSlotsExpanded }
                            .padding(16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.edit_table_time_slots),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.onSurface
                            )
                            Text(
                            text = stringResource(R.string.n_periods, slotRows.size) + " · " + if (timeSlotsExpanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.rotate(if (timeSlotsExpanded) 180f else 0f)
                        )
                    }

                    AnimatedVisibility(
                        visible = timeSlotsExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        ) {
                            // v1.0.56 T6: 第三 Tab「作息表」= 原独立换绑卡收编于此(卡已拆,
                            // 未绑定/选中语义零变化, pendingBind 保存链原样)
                            TimeSlotEditor(
                                rows = slotRows.toList(),
                                onRowsChange = { newRows ->
                                    slotRows.clear()
                                    slotRows.addAll(newRows)
                                },
                                smartConfig = smartConfig.value,
                                onSmartConfigChange = { smartConfig.value = it },
                                periodTableOptions = allPeriodTables.map {
                                    TimeSlotEditorPeriodTableOption(it.id, it.name, it.nodesPerDay)
                                },
                                selectedPeriodTableId = pendingBind,
                                onSelectPeriodTable = { pendingBind = it }
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

            // 保存
            item {
                Button(
                    onClick = {
                        val maxWeek = maxWeekText.toIntOrNull() ?: 20
                        val valid = startDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) &&
                            slotRows.all { it.start.matches(Regex("\\d{2}:\\d{2}")) && it.end.matches(Regex("\\d{2}:\\d{2}")) } &&
                            slotRows.all { it.start < it.end }
                        if (!valid) {
                            error = validationErrorMessage
                            return@Button
                        }
                        // 绑定保持不变时, 有效作息数据尚未加载 = 流还在初始化, 禁止把过期
                        // 兼容列当真值写回共享作息表; 用户主动改绑(含解绑)时编辑区已切到
                        // 目标表/本表数据, 不受此闸限制。
                        val bindChanged = pendingBind != table.periodTableId
                        if (!bindChanged && table.periodTableId != null && effectivePeriodTable == null) {
                            error = context.getString(R.string.edit_table_period_loading)
                            return@Button
                        }
                        error = null
                        val smartConfigJson = try {
                            Json.encodeToString(smartConfig.value)
                        } catch (e: Exception) {
                            ""
                        }
                        val newTimeJson = TimeTableUtils.buildTimeJsonFromRows(slotRows.toList())
                        val updated = table.copy(
                            name = name.ifBlank { table.name },
                            startDate = DateUtils.normalizeStartDate(startDate),
                            maxWeek = maxWeek,
                            timeJson = newTimeJson,
                            smartConfigJson = smartConfigJson
                        )
                        if (bindChanged && pendingBind != null) {
                            // issue#40 §5.3: 换绑须先预览确认 — 弹换绑确认框, 确认才写;
                            // 用户在编辑区对目标表的节次编辑一并暂存(确认时落 target period)
                            pendingRebind = pendingBind
                            pendingRebindTable = updated
                            pendingRebindPeriod = effectivePeriodTable?.copy(
                                timeJson = newTimeJson,
                                smartConfigJson = smartConfigJson,
                                nodesPerDay = slotRows.size.coerceAtLeast(1)
                            )
                            return@Button
                        }
                        scope.launch {
                            if (bindChanged) {
                                // 解绑: 元数据+时间域(解绑后兼容列是真值)+periodTableId=null,
                                // 单事务原子完成 — 课程行零改动(issue#40 §5.3)
                                viewModel.updateTableMetadataAndBind(updated, null)
                            } else if (effectivePeriodTable != null) {
                                // 元数据 + 共享作息表内容: 单事务原子双写(2026-09-23 症状3)
                                viewModel.updateTableMetadataWithPeriodTable(
                                    updated,
                                    effectivePeriodTable.copy(
                                        timeJson = newTimeJson,
                                        smartConfigJson = smartConfigJson,
                                        nodesPerDay = slotRows.size.coerceAtLeast(1)
                                    )
                                )
                            } else {
                                // issue#28 P3: timeJson 变了课程节次必须自适应(16→12 节后
                                // 课程不能再停在 13-16 节)
                                viewModel.updateTableRemappingCourses(updated)
                            }
                            onSaved()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.ctaHeight),
                    shape = SleepyTheme.Buttons.shape
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.edit_table_save))
                }
            }

            // 删除（新建未保存的表不显示此按钮——退出即丢弃; 用户 2026-09-03 指令: 允许删完,
            // 最后一张表也可删 — ScheduleScreen 的真空态(EmptyState)兜底)
            if (pendingNewTableId == null) {
                item {
                    // [intentional custom] 官方 Button 无 error 语义变体; 沿用 errorContainer
                    // 色块 = Sleepy 视觉语言(同 AddCourseScreen 删除键)。
                    Button(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.regularHeight),
                        shape = SleepyTheme.Buttons.shape,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.errorContainer)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = colors.onErrorContainer)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.edit_table_delete), color = colors.onErrorContainer)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(28.dp)) }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {},
            // 防呆: 删表=连带删全部课程, 明示数量让用户知道要失去多少数据
            // 2026-09-16 用户: 裸 TextButton 无边界无色块 — 统一色块按钮行
            title = { Text(stringResource(R.string.edit_table_delete_confirm), color = colors.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            if (state.courses.isNotEmpty()) R.string.edit_table_delete_msg_count
                            else R.string.edit_table_delete_msg,
                            table.name,
                            state.courses.size
                        ),
                        color = colors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    com.lingion.sleepy.ui.component.DialogActionButtons(
                        confirmText = stringResource(R.string.delete),
                        onConfirm = {
                            showDeleteConfirm = false
                            scope.launch {
                                viewModel.deleteTable(table.id)
                                onDeleted()
                            }
                        },
                        dismissText = stringResource(R.string.cancel),
                        onDismiss = { showDeleteConfirm = false },
                        destructive = true
                    )
                }
            }
        )
    }

    // issue#40 §5.3: 换绑确认 — 换绑后本课表按新节次表解释节次时间, 自定义时间课程不受影响
    if (pendingRebind != null) {
        val targetId = pendingRebind
        AlertDialog(
            onDismissRequest = { pendingRebind = null },
            title = { Text(stringResource(R.string.period_table_bind_preview_title), color = colors.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.period_table_bind_preview_body),
                        color = colors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    com.lingion.sleepy.ui.component.DialogActionButtons(
                        confirmText = stringResource(R.string.period_table_preview_confirm),
                        onConfirm = {
                            pendingRebind = null
                            val metadata = pendingRebindTable
                            val periodContent = pendingRebindPeriod
                            pendingRebindTable = null
                            pendingRebindPeriod = null
                            scope.launch {
                                if (metadata != null) {
                                    viewModel.updateTableMetadataAndBind(metadata, targetId, periodContent)
                                } else {
                                    viewModel.bindPeriodTable(table.id, targetId)
                                }
                                onSaved()
                            }
                        },
                        dismissText = stringResource(R.string.cancel),
                        onDismiss = { pendingRebind = null }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }
}

@Composable
private fun CardSection(title: String, subtitle: String, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, SleepyTheme.shapes.extraLarge)
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

// BindOptionRow 已删(issue#40 §4.3 换绑卡拆除) — 选项行 UI 由 TimeSlotEditor 第三 Tab
// 的 BindChoiceRow 接管(v1.0.56 T6, 样式同构 primaryContainer 色块+对勾)