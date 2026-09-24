package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.coroutines.launch

/**
 * 独立时间节次表管理页(issue#40 设计 §4.1) — 我的页入口, 只管时间表增删改查,
 * 不混入课程编辑。列表显示各时间表 + "已绑定 N 张课表"(§4.1 树形图)。
 *
 * @param onOpenEdit 进入某张时间表的编辑页(编辑/复制既有表)
 * @param onCreateNew 新建后进入编辑页 — 由调用方以 pendingNewPeriodTableId 标记,
 *   编辑页未保存返回时丢弃残留行(与课表侧 pendingNewTableId 同款 §4.2 语义)
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PeriodTablesScreen(
    onBack: () -> Unit,
    onOpenEdit: (Long) -> Unit,
    onCreateNew: (Long) -> Unit = onOpenEdit,
    viewModel: ScheduleViewModel = viewModel()
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val newPeriodTableName = stringResource(R.string.period_table_new)
    val periodTables by viewModel.allPeriodTables.collectAsState()
    val tables by viewModel.state.collectAsState()

    // v1.0.56 T7: 删除入口迁至编辑页底部 — 本页不再持有删除弹窗/绑定拦截状态
    // v1.0.56 T8: 复制先命名, 确认后才落库; 复制成功留在管理页
    var copyTarget by remember { mutableStateOf<PeriodTableEntity?>(null) }
    var copyName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.period_tables_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Spacer(modifier = Modifier.height(2.dp)) }
                items(periodTables, key = { it.id }) { pt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SleepyTheme.shapes.extraLarge)
                            .background(colors.surfaceContainer)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pt.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.onSurface
                            )
                            Text(
                                text = stringResource(R.string.period_table_bound_count, pt.boundCount(tables.tables)),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onOpenEdit(pt.id) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.edit_table_title), tint = colors.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            scope.launch {
                                val suggested = viewModel.suggestPeriodTableCopyName(pt.id)
                                if (suggested != null) {
                                    copyTarget = pt
                                    copyName = suggested
                                }
                            }
                        }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.period_table_copy), tint = colors.onSurfaceVariant)
                        }
                        // v1.0.56 T7: 删除键从列表行挪到编辑页底部(用户 2026-09-16);
                        // 行内只留 编辑+复制, 删除弹窗与绑定拦截逻辑整体迁至 PeriodTableEditScreen
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
            Button(
                onClick = {
                    scope.launch {
                        val newId = viewModel.insertPeriodTable(
                            name = newPeriodTableName
                        )
                        // issue#40: 创建即落库(自增 id), 编辑页把它当"未保存新表" —
                        // 未保存返回时由 onCreateNew 调用方/编辑页清掉残留行
                        if (newId > 0) onCreateNew(newId)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).height(SleepyTheme.Buttons.ctaHeight),
                shape = SleepyTheme.Buttons.shape
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.period_table_new))
            }
        }
    }

    copyTarget?.let { target ->
        val candidate = copyName.trim()
        val nameTaken = candidate.isNotBlank() && TimeTableUtils.isTableNameTaken(
            candidate,
            tables.tables.map { it.name },
            periodTables.map { it.name }
        )
        AlertDialog(
            onDismissRequest = { copyTarget = null },
            title = { Text(stringResource(R.string.period_table_copy_dialog_title), color = colors.onSurface) },
            confirmButton = {},
            dismissButton = {},
            // 2026-09-16 用户: 裸 TextButton 无边界无色块 — 统一色块按钮行
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
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
                        colors = SleepyTheme.fieldColors()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // 2026-09-16 用户: 裸 TextButton 无边界无色块 — 统一色块按钮行
                    com.lingion.sleepy.ui.component.DialogActionButtons(
                        confirmText = stringResource(R.string.ok),
                        onConfirm = {
                            scope.launch {
                                // 二次查重: 列表可能在弹窗打开期间发生变化, 不能只信预览态。
                                val newId = viewModel.copyPeriodTableAs(target.id, candidate)
                                if (newId > 0) copyTarget = null
                            }
                        },
                        dismissText = stringResource(R.string.cancel),
                        onDismiss = { copyTarget = null },
                        confirmEnabled = candidate.isNotBlank() && !nameTaken
                    )
                }
            }
        )
    }
}

/** 绑定数 = time_tables 里 periodTableId 指向本表的行数(与 DAO boundTableCount 同口径, 用已加载列表算免额外查询) */
private fun PeriodTableEntity.boundCount(tables: List<com.lingion.sleepy.data.entity.TimeTableEntity>): Int =
    tables.count { it.periodTableId == id }
