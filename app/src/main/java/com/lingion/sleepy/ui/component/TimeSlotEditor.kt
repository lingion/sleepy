package com.lingion.sleepy.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.SmartPeriodConfig
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.util.TimeTableUtils.TimeSlotRow
import kotlinx.serialization.json.Json

/**
 * issue#23 Task 4: 进自动模式的配置归一 —— 手动行与智慧节次配置双向同步的纯逻辑。
 *
 * 1) stored 配置仍能 derive 出当前标准行(节点/起止逐行相等) → 原样保留
 *    (保住用户已调好的分组、标签; edge 行不参与比对, 永远留在手动模式);
 * 2) 否则从当前行重新推断(edge 行被排除), stored 作为 previous 传入以按分钟
 *    承接既有分组标签;
 * 3) 行不可推断(畸形/倒序/断号) → 返回 null, 调用方回退最简默认或维持原配置。
 */
fun resolveAutoPeriodConfig(
    rows: List<TimeSlotRow>,
    stored: SmartPeriodConfig?
): SmartPeriodConfig? {
    val standard = rows.filter { it.edgeClass == null }.sortedBy { it.node }
    if (standard.isEmpty() || standard.first().node != 1 ||
        standard.map { it.node } != (1..standard.size).toList()
    ) {
        return TimeTableUtils.inferSmartPeriodConfig(standard, stored)
    }
    if (stored != null) {
        val derived = stored.derive()
        val matches = derived.size == standard.size && derived.withIndex().all { (i, d) ->
            d.node == standard[i].node && d.start == standard[i].start && d.end == standard[i].end
        }
        if (matches) return stored
    }
    return TimeTableUtils.inferSmartPeriodConfig(standard, stored)
}

/** Replace standard rows with automatic output while retaining manual edge rows. */
fun mergeAutoRows(
    existingRows: List<TimeSlotRow>,
    derivedStandardRows: List<TimeSlotRow>
): List<TimeSlotRow> {
    var nextStandard = 0
    val merged = existingRows.mapNotNull { row ->
        if (row.edgeClass != null) {
            row
        } else {
            derivedStandardRows.getOrNull(nextStandard++)
        }
    }.toMutableList()
    if (nextStandard < derivedStandardRows.size) {
        merged += derivedStandardRows.drop(nextStandard)
    }
    return merged
}

/** smartConfigJson -> config(null = 空串/损坏); 两处编辑页 seed 共用 */
fun decodeSmartPeriodConfig(json: String?): SmartPeriodConfig? =
    json?.takeIf { it.isNotBlank() }?.let { raw ->
        runCatching { Json.decodeFromString<SmartPeriodConfig>(raw) }.getOrNull()
    }

/**
 * 节次编辑器 v1.0.16+ / v1.0.56 三 Tab
 *
 * 顶部 Tab 切换：
 *  - [Mode.Manual] 手动模式：原 TimeSlotEditor，逐节编辑 start/end
 *  - [Mode.Auto]   自动模式：智慧节次，三个字段 + break 分组卡片
 *  - [Mode.PeriodTable] 作息表模式(v1.0.56)：绑定一张现成作息表直接用。
 *    仅当调用方传入 [periodTableOptions] 时出现(不传=旧两 Tab, 兼容既有调用)。
 *
 * 调用方持有 rows（手动模式），config（自动模式），切换模式时通过
 * [onRowsChange]/[onConfigChange] 通知。应用自动模式后通过 [onApplyAuto]
 * 把生成的 rows 回填给手动模式。
 *
 * v1.0.56 作息表 Tab 语义:
 *  - [selectedPeriodTableId] null = 未绑定(用解析出的/本表的内置节次), 非 null = 绑定该表;
 *    选中态完全由调用方持有(确认时才落库/落 preview 值), 组件内零写库。
 *  - [onSelectPeriodTable] 点选项行回调(null = 选"未绑定")。
 *  - [excludePeriodTableId] 作息表编辑页用: 列表中排除自己(禁自引用)。
 */
@Composable
fun TimeSlotEditor(
    rows: List<TimeSlotRow>,
    onRowsChange: (List<TimeSlotRow>) -> Unit,
    smartConfig: SmartPeriodConfig = SmartPeriodConfig(),
    onSmartConfigChange: (SmartPeriodConfig) -> Unit = {},
    modifier: Modifier = Modifier,
    periodTableOptions: List<PeriodTableOption> = emptyList(),
    selectedPeriodTableId: Long? = null,
    onSelectPeriodTable: (Long?) -> Unit = {},
    excludePeriodTableId: Long? = null
) {
    var mode by remember { mutableStateOf(Mode.Manual) }

    // Bug 2 fix: 自动模式下，smartConfig 一旦变化就立刻 derive 出 rows 同步给上层，
    // 否则保存时 timeJson 用的还是旧的手动 rows，导致"保存的不是自动模式数据"。
    LaunchedEffect(mode, smartConfig) {
        if (mode == Mode.Auto) {
            onRowsChange(mergeAutoRows(rows, smartConfig.derive()))
        }
    }

    // issue#23 Task 4: 手动→自动切换时, 若现有配置已经对应当前手动行则原样保留,
    // 否则从当前手动行重新推断 —— 禁进自动即重置为全新默认配置。
    val switchToAuto = {
        val resolved = resolveAutoPeriodConfig(rows, smartConfig) ?: smartConfig
        onSmartConfigChange(resolved)
        mode = Mode.Auto
    }

    Column(modifier = modifier) {
        // ===== Tab 切换 =====
        ModeTabSwitch(
            current = mode,
            onChange = { next -> if (next == Mode.Auto) switchToAuto() else mode = next },
            hasPeriodTableTab = periodTableOptions.isNotEmpty() || selectedPeriodTableId != null
        )
        Spacer(Modifier.height(8.dp))

        when (mode) {
            Mode.Manual -> ManualTimeSlotEditor(
                rows = rows,
                onRowsChange = onRowsChange
            )
            Mode.Auto -> SmartPeriodEditor(
                config = smartConfig,
                onConfigChange = onSmartConfigChange
            )
            Mode.PeriodTable -> PeriodTableBindTab(
                options = periodTableOptions.filterNot { it.id == excludePeriodTableId },
                selectedId = selectedPeriodTableId,
                onSelect = onSelectPeriodTable
            )
        }
    }
}

// TimeSlotEditorManualOnly 死包装已删（全库零调用; 导入场景直接用 TimeSlotEditor(mode=Manual)）

enum class Mode { Manual, Auto, PeriodTable }

/** v1.0.56 作息表 Tab 选项 — 调用方从 VM 流映射, 组件不触库 */
data class PeriodTableOption(val id: Long, val name: String, val nodesPerDay: Int)

@Composable
private fun ModeTabSwitch(current: Mode, onChange: (Mode) -> Unit, hasPeriodTableTab: Boolean) {
    // 2026-08-25 用户指令: 全 app 统一色块禁描线 — M3 SegmentedButton 是描边风格,
    // 换项目统一的 SegmentedSwitcher (主页周视图/网格同款)
    val modes = if (hasPeriodTableTab) Mode.entries else listOf(Mode.Manual, Mode.Auto)
    SegmentedSwitcher(
        options = modes.map {
            it to stringResource(
                when (it) {
                    Mode.Manual -> R.string.mode_manual
                    Mode.Auto -> R.string.mode_auto
                    Mode.PeriodTable -> R.string.period_tables_tab
                }
            )
        },
        selected = current,
        onSelect = onChange,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * v1.0.56 作息表 Tab 内容: 未绑定选项 + 全部作息表列表。
 * 选中态=primaryContainer 色块+对勾(与 EditTableScreen 换绑卡同构, 禁描边规则)。
 * maxHeight 限制防弹窗内挤爆; 列表可滚动。
 */
@Composable
private fun PeriodTableBindTab(
    options: List<PeriodTableOption>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 未绑定 = 不绑, 用解析出的/本表内置节次
        BindChoiceRow(
            title = stringResource(R.string.period_table_unbound),
            selected = selectedId == null,
            onClick = { onSelect(null) }
        )
        options.forEach { opt ->
            BindChoiceRow(
                title = opt.name,
                subtitle = stringResource(R.string.period_table_nodes_count, opt.nodesPerDay),
                selected = selectedId == opt.id,
                onClick = { onSelect(opt.id) }
            )
        }
    }
}

/** 选中态=primaryContainer 色块+对勾(UI 纯色块禁描边规则, 与换绑卡 BindOptionRow 同构) */
@Composable
private fun BindChoiceRow(title: String, selected: Boolean, onClick: () -> Unit, subtitle: String? = null) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SleepyTheme.shapes.medium)
            .background(if (selected) colors.primaryContainer else colors.surface)
            .noRippleClickable(onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) colors.onPrimaryContainer else colors.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
        if (selected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = colors.onPrimaryContainer,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ManualTimeSlotEditor(
    rows: List<TimeSlotRow>,
    onRowsChange: (List<TimeSlotRow>) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Column(modifier = modifier) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.n_periods, rows.size),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            // 2026-09-16 用户: 裸 TextButton 无边界无色块 — 小号色块按钮
            Button(
                onClick = { onRowsChange(TimeTableUtils.appendEmptyRow(rows)) },
                shape = SleepyTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer
                )
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.add_period), style = MaterialTheme.typography.labelMedium)
            }
        }

        // Rows
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceContainerLow, SleepyTheme.shapes.large)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            rows.forEach { row ->
                TimeSlotRowItem(
                    row = row,
                    canDelete = rows.size > 1,
                    onStartChange = { newStart ->
                        onRowsChange(rows.map { if (it.node == row.node) it.copy(start = newStart) else it })
                    },
                    onEndChange = { newEnd ->
                        onRowsChange(rows.map { if (it.node == row.node) it.copy(end = newEnd) else it })
                    },
                    onDelete = {
                        onRowsChange(TimeTableUtils.removeAndRenumber(rows, row.node))
                    }
                )
            }
        }
    }
}

@Composable
private fun TimeSlotRowItem(
    row: TimeSlotRow,
    canDelete: Boolean,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.course_node_format, row.node),
            modifier = Modifier.width(44.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface
        )
        TimePickerField(
            value = row.start,
            onValueChange = onStartChange,
            label = stringResource(R.string.start_label),
            modifier = Modifier.weight(1f)
        )
        TimePickerField(
            value = row.end,
            onValueChange = onEndChange,
            label = stringResource(R.string.end_label),
            modifier = Modifier.weight(1f)
        )
        if (canDelete) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Outlined.RemoveCircleOutline,
                    contentDescription = stringResource(R.string.delete_period),
                    tint = colors.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            Spacer(Modifier.width(32.dp))
        }
    }
}