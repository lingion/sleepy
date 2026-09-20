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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.SmartPeriodConfig
import com.lingion.sleepy.ui.component.PeriodTableOption as TimeSlotEditorPeriodTableOption
import com.lingion.sleepy.ui.component.TimeSlotEditor
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 独立时间节次表编辑页(issue#40 设计 §4.2) — 复用 [TimeSlotEditor]:
 * 手动/智慧节次、保存前预览(§5.2 决策 3)、保存写 period_tables + 全部兼容列同步。
 * 编辑保存走 [ScheduleViewModel.updatePeriodTableContent](→ repo.savePeriodTable,
 * 课程行零改动); 取消预览则数据库零改动。
 *
 * @param periodTableId 目标时间表 id(管理页保证已存在; null = 无效直接返回)
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PeriodTableEditScreen(
    periodTableId: Long,
    /** issue#40: 本表是否为"新建后直接进入"的未保存表 — 返回(未保存)时整行丢弃, 管理页不留空壳 */
    isNewUnsaved: Boolean = false,
    onBack: () -> Unit,
    viewModel: ScheduleViewModel = viewModel()
) {
    val colors = SleepyTheme.colors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val periodTables by viewModel.allPeriodTables.collectAsState()
    val scheduleState by viewModel.state.collectAsState()

    val periodTable = periodTables.find { it.id == periodTableId }
    if (periodTable == null) {
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

    var name by remember(periodTable.id) { mutableStateOf(periodTable.name) }
    var timeSlotsExpanded by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // 预览态: 非 null = 显示确认弹窗; 确认才落库, 取消只清 state(§5.2 用户取消权)
    var pendingPreview by remember { mutableStateOf<TimeTableUtils.PeriodTablePreview?>(null) }
    var pendingSave by remember { mutableStateOf<PeriodTableEntity?>(null) }
    // issue#40: 新建未保存表的丢弃标记 — 用户确认保存后翻 false, 返回不再删行
    var unsavedNew by remember { mutableStateOf(isNewUnsaved) }
    // v1.0.56 T6: 第三 Tab「作息表」— 作息表编辑页同样有(用户 2026-09-16: 有手动/自动就有第三个)。
    // 语义 = 取入: 选中另一张作息表, 把它的节次内容拷进当前编辑区(成为本表内容的起点),
    // 非活绑 — period_tables 自身无绑定字段, 绑定只存在于课表上。排除自己禁自引用。
    var selectedImportTableId by remember(periodTable.id) { mutableStateOf<Long?>(null) }
    // v1.0.56 T7: 删除入口迁入本页 — 确认弹窗 + 绑定拦截提示(从管理页列表行整体搬迁)
    var showDeleteConfirm by remember(periodTable.id) { mutableStateOf(false) }
    var deleteBlockedMsg by remember(periodTable.id) { mutableStateOf<String?>(null) }
    // v1.0.56 T8: 复制先命名, 确认后才建; 成功后回管理页
    var showCopyDialog by remember(periodTable.id) { mutableStateOf(false) }
    var copyName by remember(periodTable.id) { mutableStateOf("") }
    // v1.0.56 T11: 分享底部弹窗(原生格式/JSON 二选一)
    var showShareSheet by remember(periodTable.id) { mutableStateOf(false) }

    val slotRows = remember(periodTable.id, periodTable.updatedAt, periodTable.timeJson) {
        mutableStateListOf<TimeTableUtils.TimeSlotRow>().apply {
            addAll(TimeTableUtils.parseTimeSlotRows(periodTable.timeJson))
        }
    }
    // issue#23 Task 4: 已存配置仍能 derive 出当前行 → 原样保留; 否则从当前行重推断;
    // 行不可推断 → 最简默认兜底(旧行为)。
    val smartConfig = remember(periodTable.id, periodTable.smartConfigJson) {
        val stored = com.lingion.sleepy.ui.component.decodeSmartPeriodConfig(periodTable.smartConfigJson)
        mutableStateOf(
            com.lingion.sleepy.ui.component.resolveAutoPeriodConfig(slotRows.toList(), stored)
                ?: SmartPeriodConfig(
                    totalPeriods = slotRows.size.coerceAtLeast(1),
                    startTime = slotRows.firstOrNull()?.start?.takeIf { it.isNotBlank() } ?: "08:00"
                )
        )
    }

    val fieldColors = SleepyTheme.fieldColors()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.period_tables_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        // issue#40: 未保存的新表返回 = 放弃 — 清掉建表残留行, 与
                        // 课表侧 discardNewTable 同语义(§4.2 新建取消不遗留空壳)
                        if (unsavedNew) viewModel.discardNewPeriodTable(periodTableId)
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    // v1.0.56 T11: 分享作息表(原生格式 / JSON 二选一)
                    IconButton(onClick = { showShareSheet = true }) {
                        Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.period_table_share_sheet_title), tint = colors.onBackground)
                    }
                    // v1.0.56 T8: 复制先弹命名框, 确认后才落库; 成功回管理页
                    IconButton(onClick = {
                        scope.launch {
                            val suggested = viewModel.suggestPeriodTableCopyName(periodTable.id)
                            if (suggested != null) {
                                copyName = suggested
                                showCopyDialog = true
                            }
                        }
                    }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.period_table_copy), tint = colors.onBackground)
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

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SleepyTheme.shapes.extraLarge)
                        .background(colors.surfaceContainer)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    androidx.compose.material3.TextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.period_table_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = SleepyTheme.fieldShape,
                        colors = fieldColors
                    )
                }
            }

            // 节次时间表(可折叠, 与 EditTableScreen 同款交互)
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.edit_table_time_slots),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.onSurface
                            )
                            Text(
                                text = stringResource(R.string.n_periods, slotRows.size) + " · " +
                                    if (timeSlotsExpanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
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
                            TimeSlotEditor(
                                rows = slotRows.toList(),
                                onRowsChange = { newRows ->
                                    slotRows.clear()
                                    slotRows.addAll(newRows)
                                },
                                smartConfig = smartConfig.value,
                                onSmartConfigChange = { smartConfig.value = it },
                                // v1.0.56 T6: 第三 Tab「作息表」— 列表排除自己;
                                // 选中 = 把该表节次取入当前编辑区(取入非活绑)
                                periodTableOptions = periodTables.map {
                                    TimeSlotEditorPeriodTableOption(it.id, it.name, it.nodesPerDay)
                                },
                                selectedPeriodTableId = selectedImportTableId,
                                excludePeriodTableId = periodTable.id,
                                onSelectPeriodTable = { pickedId ->
                                    selectedImportTableId = pickedId
                                    val picked = periodTables.find { it.id == pickedId } ?: return@TimeSlotEditor
                                    val imported = TimeTableUtils.parseTimeSlotRows(picked.timeJson)
                                    slotRows.clear()
                                    slotRows.addAll(imported)
                                    // issue#23 Task 4: 取入后配置对齐同一规则 —— 取入表的配置
                                    // 仍 derive 得到取入行 → 保留; 否则从取入行重推断; 不可推断
                                    // → 维持当前配置。
                                    val importedStored = com.lingion.sleepy.ui.component
                                        .decodeSmartPeriodConfig(picked.smartConfigJson)
                                    smartConfig.value = com.lingion.sleepy.ui.component
                                        .resolveAutoPeriodConfig(imported, importedStored)
                                        ?: smartConfig.value
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

            // 保存 — 先预览后确认(§5.2)
            item {
                Button(
                    onClick = {
                        val valid = slotRows.isNotEmpty() &&
                            slotRows.all { it.start.matches(Regex("\\d{2}:\\d{2}")) && it.end.matches(Regex("\\d{2}:\\d{2}")) } &&
                            slotRows.all { it.start < it.end }
                        if (!valid) {
                            error = context.getString(R.string.edit_table_validation_error)
                            return@Button
                        }
                        error = null
                        val smartConfigJson = try {
                            Json.encodeToString(smartConfig.value)
                        } catch (e: Exception) {
                            ""
                        }
                        val newTimeJson = TimeTableUtils.buildTimeJsonFromRows(slotRows.toList())
                        val updated = periodTable.copy(
                            name = name.ifBlank { periodTable.name },
                            timeJson = newTimeJson,
                            smartConfigJson = smartConfigJson,
                            nodesPerDay = slotRows.size.coerceAtLeast(1)
                        )
                        pendingSave = updated
                        // 预览: 全库范围内所有绑定本表的课程(§5.2 受影响课表列表)。
                        // 修复: state.courses 只装当前选中表的课, 直接用它统计会漏掉其他绑定表 —
                        // 从 repo 拉全库课程再按绑定表过滤
                        scope.launch {
                            val boundIds = scheduleState.tables
                                .filter { it.periodTableId == periodTable.id }
                                .map { it.id }
                                .toSet()
                            val allCourses = viewModel.getAllCourses().filter { it.tableId in boundIds }
                            val oldJson = periodTable.timeJson
                            pendingPreview = TimeTableUtils.previewPeriodTableChange(oldJson, newTimeJson, allCourses)
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

            // v1.0.56 T7: 删除键从管理页列表行挪到这里(用户 2026-09-16)。
            // 新建未保存的表不显示 — 退出即丢弃, 无"已存在的东西"可删。
            // 删除成功后回管理页; 被绑定拦截时弹 blocked 提示(与原列表行删除同语义)。
            if (!unsavedNew) {
                item {
                    Button(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.regularHeight),
                        shape = SleepyTheme.Buttons.shape,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = colors.errorContainer
                        )
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = colors.onErrorContainer)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.period_table_delete_confirm), color = colors.onErrorContainer)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(28.dp)) }
        }
    }

    // 保存前预览确认弹窗(§9 决策 3): 取消 = 零写库零快照
    if (pendingPreview != null && pendingSave != null) {
        val boundCount = scheduleState.tables.count { it.periodTableId == periodTable.id }
        AlertDialog(
            onDismissRequest = { pendingPreview = null; pendingSave = null },
            title = { Text(stringResource(R.string.period_table_save_preview_title), color = colors.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            R.string.period_table_preview_summary,
                            boundCount,
                            pendingPreview!!.changedCourses.size,
                            pendingPreview!!.unchangedCount
                        ),
                        color = colors.onSurfaceVariant
                    )
                    // 逐课旧时间→新时间(§5.2) + 变化节次标注, 最多列 8 行防溢出。
                    // 2026-09-16 用户要求: 改早八必须列出所有第一节课的课程名+几点到几点。
                    pendingPreview!!.changedCourses.take(8).forEach { change ->
                        val oldT = change.oldTime ?: "?"
                        val newT = change.newTime ?: "?"
                        val nodesTag = if (change.changedNodes.size == 1) {
                            context.getString(R.string.course_node_format, change.changedNodes.first().toString())
                        } else {
                            "${change.changedNodes.first()}-${change.changedNodes.last()}"
                        }
                        Text(
                            "${change.courseName}($nodesTag): $oldT → $newT",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurface
                        )
                    }
                    if (pendingPreview!!.changedCourses.size > 8) {
                        Text(
                            stringResource(R.string.period_table_preview_more, pendingPreview!!.changedCourses.size - 8),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    // 2026-09-16 用户: 裸 TextButton 无边界无色块 — 统一色块按钮行
                    com.lingion.sleepy.ui.component.DialogActionButtons(
                        confirmText = stringResource(R.string.period_table_preview_confirm),
                        onConfirm = {
                            val toSave = pendingSave!!
                            pendingPreview = null
                            pendingSave = null
                            // issue#40: 已确认保存 — 新建行的丢弃标记解除, 返回不再清行
                            unsavedNew = false
                            scope.launch {
                                viewModel.updatePeriodTableContent(toSave)
                                onBack()
                            }
                        },
                        dismissText = stringResource(R.string.cancel),
                        onDismiss = { pendingPreview = null; pendingSave = null }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    // v1.0.56 T7: 删除确认弹窗(原管理页逻辑整体迁移, 语义不变)
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.period_table_delete_confirm), color = colors.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.period_table_delete_msg_body, periodTable.name), color = colors.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    com.lingion.sleepy.ui.component.DialogActionButtons(
                        confirmText = stringResource(R.string.delete),
                        onConfirm = {
                            showDeleteConfirm = false
                            scope.launch {
                                val ok = viewModel.deletePeriodTable(periodTable.id)
                                if (ok) {
                                    onBack()
                                } else {
                                    val bound = scheduleState.tables.count { it.periodTableId == periodTable.id }
                                    deleteBlockedMsg = context.getString(R.string.period_table_delete_blocked, bound)
                                }
                            }
                        },
                        dismissText = stringResource(R.string.cancel),
                        onDismiss = { showDeleteConfirm = false },
                        destructive = true
                    )
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    // v1.0.56 T7: 绑定拦截提示(删不掉 = 仍有课表绑着本表)
    deleteBlockedMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = { deleteBlockedMsg = null },
            title = { Text(stringResource(R.string.period_table_delete_confirm), color = colors.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(msg, color = colors.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    com.lingion.sleepy.ui.component.DialogActionButtons(
                        confirmText = stringResource(R.string.ok),
                        onConfirm = { deleteBlockedMsg = null }
                    )
                }
            },
            confirmButton = {}
        )
    }

    // v1.0.56 T11: 分享格式选择底部弹窗
    if (showShareSheet) {
        com.lingion.sleepy.ui.component.PeriodTableShareSheet(
            periodTable = periodTable,
            onDismiss = { showShareSheet = false }
        )
    }

    if (showCopyDialog) {
        val candidate = copyName.trim()
        val nameTaken = candidate.isNotBlank() && TimeTableUtils.isTableNameTaken(
            candidate,
            scheduleState.tables.map { it.name },
            periodTables.map { it.name }
        )
        AlertDialog(
            onDismissRequest = { showCopyDialog = false },
            title = { Text(stringResource(R.string.period_table_copy_dialog_title), color = colors.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.TextField(
                        value = copyName,
                        onValueChange = { copyName = it },
                        label = { Text(stringResource(R.string.period_table_name_label)) },
                        singleLine = true,
                        isError = nameTaken,
                        supportingText = if (nameTaken) {
                            { Text(stringResource(R.string.period_table_name_taken)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = SleepyTheme.fieldShape,
                        colors = fieldColors
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    com.lingion.sleepy.ui.component.DialogActionButtons(
                        confirmText = stringResource(R.string.ok),
                        onConfirm = {
                            scope.launch {
                                val newId = viewModel.copyPeriodTableAs(periodTable.id, candidate)
                                if (newId > 0) {
                                    showCopyDialog = false
                                    onBack()
                                }
                            }
                        },
                        dismissText = stringResource(R.string.cancel),
                        onDismiss = { showCopyDialog = false },
                        confirmEnabled = candidate.isNotBlank() && !nameTaken
                    )
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }
}
