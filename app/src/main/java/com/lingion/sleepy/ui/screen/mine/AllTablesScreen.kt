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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import com.lingion.sleepy.ui.component.liquidglass.GlassTonalButton as FilledTonalButton
import androidx.compose.material3.Icon
import com.lingion.sleepy.ui.component.liquidglass.GlassIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import com.lingion.sleepy.ui.component.liquidglass.GlassTopAppBar as TopAppBar
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
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.ui.component.DialogActionButtons
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AllTablesScreen(
    onBack: () -> Unit,
    onCreateNewTable: () -> Unit,
    onOpenEditTable: (Long) -> Unit,
    onUpdateFromJw: (Long) -> Unit,
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val periodTables by viewModel.allPeriodTables.collectAsState()
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var copyTarget by remember { mutableStateOf<TimeTableEntity?>(null) }
    var copyName by remember { mutableStateOf("") }
    var copying by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.all_tables)) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            itemsIndexed(state.tables, key = { _, table -> table.id }) { _, table ->
                val isCurrent = table.id == state.selectedTableId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SleepyTheme.shapes.large)
                        .background(if (isCurrent) colors.primaryContainer else colors.surfaceContainer)
                        .noRippleClickable {
                            if (!isCurrent) {
                                // v7.10.16w 用户 2026-09-10: 选表后留在本页(选中态就地高亮),
                                // 不再强制弹回 — 用户可能还要复制/编辑其他副本。返回键/← 离开。
                                viewModel.selectTable(table.id)
                            }
                        }
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isCurrent) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(SleepyTheme.shapes.medium)
                                    .background(colors.outlineVariant)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            // M3 对比度修正：当前行背景是 primaryContainer，文字/副标题应配对
                            // onPrimaryContainer 系（之前用 onSurface/onSurfaceVariant，自定义高对比主题下对比度不足）。
                            // 非当前行背景 surfaceContainer 维持 onSurface/onSurfaceVariant。
                            val (titleColor, subtitleColor) = if (isCurrent) {
                                colors.onPrimaryContainer to colors.onPrimaryContainer.copy(alpha = SleepyTheme.Alpha.highContent)
                            } else {
                                colors.onSurface to colors.onSurfaceVariant
                            }
                            Text(
                                text = table.name,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = titleColor,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (isCurrent) stringResource(R.string.current_table_week, state.currentWeek) else stringResource(R.string.table_start_date, table.startDate),
                                style = MaterialTheme.typography.bodySmall,
                                color = subtitleColor
                            )
                        }
                        // 与复制作息表一致：先命名，确认后才创建副本。
                        IconButton(onClick = {
                            copyName = TimeTableUtils.suggestUniqueName(
                                table.name,
                                state.tables.map { it.name },
                                periodTables.map { it.name }
                            )
                            copyTarget = table
                        }) {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                contentDescription = stringResource(R.string.all_tables_duplicate),
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = { onUpdateFromJw(table.id) }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.update_from_jw), tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { onOpenEditTable(table.id) }) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.action_settings),
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    // The timestamp uses the card width instead of competing with three actions.
                    if (table.createdAt > 0) {
                        Text(
                            text = stringResource(
                                R.string.table_created_at,
                                java.time.Instant.ofEpochMilli(table.createdAt)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isCurrent) colors.onPrimaryContainer.copy(alpha = SleepyTheme.Alpha.highContent) else colors.onSurfaceVariant,
                            modifier = Modifier.padding(start = 36.dp, top = 4.dp),
                        )
                    }
                }
            }

            item {
                FilledTonalButton(
                    onClick = onCreateNewTable,
                    modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.regularHeight),
                    shape = SleepyTheme.Buttons.shape
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.all_tables_new))
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    copyTarget?.let { target ->
        val candidate = copyName.trim()
        val nameTaken = candidate.isNotBlank() && TimeTableUtils.isTableNameTaken(
            candidate,
            state.tables.map { it.name },
            periodTables.map { it.name }
        )
        AlertDialog(
            onDismissRequest = { if (!copying) copyTarget = null },
            title = { Text(stringResource(R.string.all_tables_duplicate), color = colors.onSurface) },
            confirmButton = {},
            dismissButton = {},
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = copyName,
                        onValueChange = { copyName = it },
                        label = { Text(stringResource(R.string.edit_table_name)) },
                        singleLine = true,
                        enabled = !copying,
                        isError = nameTaken,
                        supportingText = if (nameTaken) {
                            { Text(stringResource(R.string.period_table_name_taken)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = SleepyTheme.fieldShape,
                        colors = SleepyTheme.fieldColors()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    DialogActionButtons(
                        confirmText = stringResource(R.string.ok),
                        onConfirm = {
                            if (!copying) {
                                copying = true
                                scope.launch {
                                    try {
                                        val newId = viewModel.duplicateTable(target.id, candidate)
                                        if (newId > 0) copyTarget = null
                                    } finally {
                                        copying = false
                                    }
                                }
                            }
                        },
                        dismissText = stringResource(R.string.cancel),
                        onDismiss = { if (!copying) copyTarget = null },
                        confirmEnabled = candidate.isNotBlank() && !nameTaken && !copying
                    )
                }
            }
        )
    }
}
