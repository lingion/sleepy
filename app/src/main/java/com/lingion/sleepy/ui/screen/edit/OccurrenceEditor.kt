package com.lingion.sleepy.ui.screen.edit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.ui.component.DialogActionButtons
import com.lingion.sleepy.ui.component.IntRangeSlider
import com.lingion.sleepy.util.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OccurrenceEditor(occurrence: CourseOccurrence, table: TimeTableEntity, onDismiss: () -> Unit, startInEdit: Boolean = false) {
    val course = occurrence.course
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dialogs = remember(context) { OccurrenceSaveDialogs(context) }
    // startInEdit: 浮窗「调整本次」入口已表达调整意图, 打开即表单态, 不再二次点击菜单。
    var editing by remember { mutableStateOf(startInEdit) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var date by remember { mutableStateOf(occurrence.displayDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
        .withLocale(LocalConfiguration.current.locales[0])
    var node by remember { mutableStateOf(course.startNode.toString()) }
    var step by remember { mutableStateOf(course.step.toString()) }
    var custom by remember { mutableStateOf(course.ownTime || course.isIrregularTime) }
    val range = TimeTableUtils.effectiveCourseTime(custom, course.startTime, course.endTime, course.startNode, course.step, table.timeJson)
    var start by remember { mutableStateOf(range?.first ?: "08:00") }
    var end by remember { mutableStateOf(range?.second ?: "09:40") }
    // 节次行双头滑杆 (需求 2026-10-29 用户裁决「跟编辑节次完全对齐」「连续节数当然
    // 删掉」, spec §3.2 第八站点): 标准区间卡 = 起始节次在 1..maxStd 连续标准域内
    // 且非非常规节次卡; 非常规/越界落回文本框 (用户原话: 一旦使用非常规截词了,
    // 就不归这个滑块管)。
    val maxStd = remember(table) { TimeTableUtils.maxStandardNode(table.timeJson) }
    val standardRange = course.startNode in 1..maxStd && !course.isIrregularNode
    // 幽灵基线 = 打开弹窗时刻的原始 (startNode, step); raw 值直存, 显示口径的
    // 夹取由 IntRangeSlider 内部与 displayStart/displayEnd 同构完成 (脏数据不会
    // 画出与摘要矛盾的幽灵)。key=course → 重开弹窗随实体重算, 保存落库后无幽灵。
    val ghostBaseline = remember(course) { course.startNode to course.step }

    fun save(delete: Boolean) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                val repo = SleepyApp.get().repository
                val snapshot = repo.getCourses(table.id)
                val transfers = AppPrefs.getHolidayTransfers(context, table.id)
                val originalTransfers = HolidayRangeOps.HolidayTransferOps.courseDateForDate(occurrence.displayDate, transfers)
                require(course.fixedDate != null || originalTransfers == occurrence.sourceDate) { context.getString(R.string.occurrence_transfer_changed) }
                var replacement: com.lingion.sleepy.data.entity.CourseEntity? = null
                if (delete) {
                    if (dialogs.ask(context.getString(R.string.occurrence_delete_title), "${course.courseName}\n${occurrence.displayDate} · ${course.nodeString(context)}", context.getString(R.string.occurrence_delete), destructive = true) == null) return@launch
                } else {
                    val target = date
                    require(target == occurrence.displayDate || DateUtils.semesterStatus(table.startDate, table.maxWeek, target) == DateUtils.SemesterStatus.IN_RANGE) { context.getString(R.string.occurrence_invalid_date) }
                    val n = node.toIntOrNull()
                    val count = step.toIntOrNull()
                    val rows = TimeTableUtils.parseTimeSlotRows(table.timeJson)
                    require(n != null && rows.any { it.node == n } && count != null && count > 0 && count <= rows.size &&
                        n.toLong() + count <= Int.MAX_VALUE && (n until n + count).all { value -> rows.any { it.node == value } }) {
                        context.getString(R.string.occurrence_invalid_nodes)
                    }
                    if (custom) {
                        val s = runCatching { LocalTime.parse(start.trim()) }.getOrNull()
                        val e = runCatching { LocalTime.parse(end.trim()) }.getOrNull()
                        require(s != null && e != null && e > s) { context.getString(R.string.occurrence_invalid_time) }
                    }
                    val edited = course.copy(startNode = n, step = count,
                        ownTime = custom, isIrregularTime = custom,
                        isIrregularNode = rows.any { it.node == n && it.edgeClass != null },
                        startTime = if (custom) start.trim() else "", endTime = if (custom) end.trim() else "").normalizeNode(table.timeJson)
                    // A time-only edit retains the occurrence's provenance without asking to choose it again.
                    replacement = if (target == occurrence.displayDate) {
                        CourseOccurrences.atDate(edited, occurrence.sourceDate, table, course.fixedDate != null)
                    } else {
                        dialogs.destinations(listOf(CourseOccurrences.atDate(edited, target, table, false)), table, transfers)?.single() ?: return@launch
                    }
                    val remaining = CourseOccurrences.without(course, occurrence.sourceDate, table.startDate)
                    val details = CourseOccurrences.conflictDetails(listOf(replacement), snapshot.filter { it.id != course.id } + remaining,
                        table, transfers, context.resources.getStringArray(R.array.day_names), context.getString(R.string.conflict_detail_line))
                    if (!dialogs.confirmConflicts(details)) return@launch
                }
                if (repo.saveOccurrence(occurrence, replacement, snapshot, table, transfers)) onDismiss()
                else error = context.getString(R.string.occurrence_stale)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (e: Exception) { error = e.message ?: context.getString(R.string.occurrence_save_failed)
            } finally { busy = false }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheetState, modifier = Modifier.testTag("occurrence-sheet")) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(course.courseName, style = MaterialTheme.typography.titleLarge)
            Text("${occurrence.displayDate} · ${course.nodeString(context)}")
            if (!editing) {
                FilledTonalButton(onClick = { editing = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(context.getString(R.string.occurrence_adjust)) }
                FilledTonalButton(onClick = { save(true) }, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer)) { Text(context.getString(R.string.occurrence_delete)) }
            } else {
                OutlinedCard(onClick = { showDatePicker = true }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("occurrence-date")) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(context.getString(R.string.occurrence_date), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(date.format(dateFormatter), style = MaterialTheme.typography.bodyLarge)
                        }
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = context.getString(R.string.select_date),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (standardRange) {
                    // 现值 = node/step 字符串态 (拖动逐帧写回, 解析失败落回打开时刻原值);
                    // 显示口径统一夹进 1..maxStd 后驱动摘要与滑块, 与 AddCourseScreen:1185
                    // 标准卡 (draft 现值 → 夹取 → 摘要/滑块) 逐层同构。落库仍走 save() 里
                    // node/step 原字符串校验, 一克不动。
                    val rawStart = node.toIntOrNull() ?: course.startNode
                    val rawCount = step.toIntOrNull() ?: course.step
                    val nodeStart = rawStart.coerceIn(1, maxStd)
                    val nodeEnd = (rawStart + rawCount - 1).coerceIn(nodeStart, maxStd)
                    val nodeCount = nodeEnd - nodeStart + 1
                    val nodeSummary = if (nodeCount <= 1) {
                        context.getString(R.string.slot_summary_period_single, nodeStart)
                    } else {
                        context.getString(R.string.slot_summary_period_range, nodeStart, nodeEnd, nodeCount)
                    }
                    IntRangeSlider(
                        summary = nodeSummary,
                        minValue = 1,
                        maxValue = maxStd,
                        startValue = nodeStart,
                        endValue = nodeEnd,
                        onValueChange = { s, e ->
                            node = s.toString()
                            step = (e - s + 1).toString()
                        },
                        initialStart = ghostBaseline.first,
                        initialEnd = ghostBaseline.first + ghostBaseline.second - 1,
                        modifier = Modifier.testTag("occurrence-node-slider"),
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(node, { node = it }, label = { Text(context.getString(R.string.occurrence_node)) }, enabled = !busy, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(step, { step = it }, label = { Text(context.getString(R.string.occurrence_step)) }, enabled = !busy, singleLine = true, modifier = Modifier.weight(1f))
                    }
                }
                Row {
                    Checkbox(custom, { enabled ->
                        if (enabled) TimeTableUtils.effectiveCourseTime(false, "", "", node.toIntOrNull() ?: course.startNode,
                            step.toIntOrNull() ?: course.step, table.timeJson)?.let { start = it.first; end = it.second }
                        custom = enabled
                    }, enabled = !busy)
                    Text(context.getString(R.string.occurrence_custom_time), Modifier.padding(top = 12.dp))
                }
                if (custom) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(start, { start = it }, label = { Text(context.getString(R.string.occurrence_start)) }, enabled = !busy, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(end, { end = it }, label = { Text(context.getString(R.string.occurrence_end)) }, enabled = !busy, singleLine = true, modifier = Modifier.weight(1f))
                }
                Button(onClick = { save(false) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) context.getString(R.string.occurrence_busy) else context.getString(R.string.occurrence_save)) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (showDatePicker) {
        // Material date pickers represent calendar days at UTC midnight, regardless of device time zone.
        val selectableDates = remember(table.startDate, table.maxWeek, occurrence.displayDate) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val candidate = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return candidate == occurrence.displayDate ||
                        DateUtils.semesterStatus(table.startDate, table.maxWeek, candidate) == DateUtils.SemesterStatus.IN_RANGE
                }
            }
        }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = selectableDates
        )
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
            DialogActionButtons(
                confirmText = context.getString(R.string.ok),
                confirmEnabled = pickerState.selectedDateMillis != null,
                onConfirm = {
                    pickerState.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                },
                dismissText = context.getString(R.string.cancel),
                onDismiss = { showDatePicker = false },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }) {
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }
    OccurrenceSaveDialogHost(dialogs)
}
