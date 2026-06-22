package com.lingion.sleepy.ui.screen.edit

import com.lingion.sleepy.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.component.TimePickerField
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.LocalTime

enum class MeetingInputMode { ByNode, ByClock }

private class MeetingBlockDraft(
    val id: Int,
    val days: androidx.compose.runtime.snapshots.SnapshotStateList<Int>,
    initialMode: MeetingInputMode,
    startNode: Int,
    step: Int,
    startTime: String,
    endTime: String
) {
    var mode by mutableStateOf(initialMode)
    var startNode by mutableStateOf(startNode)
    var step by mutableStateOf(step)
    var startTime by mutableStateOf(startTime)
    var endTime by mutableStateOf(endTime)
}

private data class ValidationIssue(
    val blockId: Int?,
    val message: String
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddCourseScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    editingCourse: CourseEntity? = null,
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val colors = SleepyTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentTable = state.currentTable
    val fieldShape = RoundedCornerShape(18.dp)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.onSurface,
        unfocusedTextColor = colors.onSurface,
        focusedLabelColor = colors.primary,
        unfocusedLabelColor = colors.onSurfaceVariant,
        focusedPlaceholderColor = colors.onSurfaceVariant,
        unfocusedPlaceholderColor = colors.onSurfaceVariant,
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = colors.outlineVariant,
        cursorColor = colors.primary,
        focusedContainerColor = colors.surfaceContainerLowest,
        unfocusedContainerColor = colors.surfaceContainerLowest
    )

    var courseName by remember(editingCourse?.id) { mutableStateOf(editingCourse?.courseName ?: "") }
    var teacher by remember(editingCourse?.id) { mutableStateOf(editingCourse?.teacher ?: "") }
    var room by remember(editingCourse?.id) { mutableStateOf(editingCourse?.room ?: "") }
    var note by remember(editingCourse?.id) { mutableStateOf(editingCourse?.note ?: "") }
    var startWeek by remember(editingCourse?.id) { mutableIntStateOf(editingCourse?.startWeek ?: 1) }
    var endWeek by remember(editingCourse?.id) { mutableIntStateOf(editingCourse?.endWeek ?: 16) }
    var nextBlockId by remember(editingCourse?.id) { mutableIntStateOf(2) }
    var validationIssues by remember { mutableStateOf<List<ValidationIssue>>(emptyList()) }

    val meetingBlocks = remember(editingCourse?.id) {
        mutableStateListOf(initialMeetingBlock(editingCourse))
    }

    // 编辑模式：查同 groupId 全部课程，按时段分组回填多个 block
    LaunchedEffect(editingCourse?.groupId) {
        val eg = editingCourse
        if (eg != null && eg.groupId.isNotBlank()) {
            val tid = state.selectedTableId ?: return@LaunchedEffect
            val groupCourses = SleepyApp.get().repository.getGroupCourses(tid, eg.groupId)
            if (groupCourses.isNotEmpty()) {
                // 按 (startNode, step, startTime, endTime) 分组
                val slots = groupCourses.groupBy { c ->
                    Triple(c.ownTime, c.startNode, c.step)
                }
                meetingBlocks.clear()
                var bid = 1
                for ((_, courses) in slots) {
                    val first = courses.first()
                    meetingBlocks.add(MeetingBlockDraft(
                        id = bid++,
                        days = androidx.compose.runtime.mutableStateListOf<Int>().apply {
                            addAll(courses.map { it.day }.distinct().sorted())
                        },
                        initialMode = if (first.ownTime) MeetingInputMode.ByClock else MeetingInputMode.ByNode,
                        startNode = first.startNode,
                        step = first.step,
                        startTime = first.startTime.ifBlank { "08:00" },
                        endTime = first.endTime.ifBlank { "09:40" }
                    ))
                }
            }
        }
    }

    val canSave = courseName.isNotBlank() && meetingBlocks.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (editingCourse != null) R.string.edit_course else R.string.create_course)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground,
                    actionIconContentColor = colors.onBackground
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

            if (validationIssues.isNotEmpty()) {
                item {
                    ValidationCard(issues = validationIssues)
                }
            }

            item {
                CardSection(
                    title = stringResource(R.string.course_basic_info),
                    subtitle = stringResource(R.string.course_basic_info_sub)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = courseName,
                            onValueChange = { courseName = it },
                            label = { Text(stringResource(R.string.course_name_required)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = fieldShape,
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = teacher,
                            onValueChange = { teacher = it },
                            label = { Text(stringResource(R.string.course_teacher)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = fieldShape,
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = room,
                            onValueChange = { room = it },
                            label = { Text(stringResource(R.string.course_room)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = fieldShape,
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text(stringResource(R.string.course_note)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            shape = fieldShape,
                            colors = fieldColors
                        )
                    }
                }
            }

            item {
                CardSection(
                    title = stringResource(R.string.week_range),
                    subtitle = stringResource(R.string.week_range_sub)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        NumberField(
                            label = stringResource(R.string.start_week),
                            value = startWeek,
                            min = 1,
                            max = 30,
                            modifier = Modifier.weight(1f),
                            shape = fieldShape,
                            colors = fieldColors
                        ) { startWeek = it }
                        NumberField(
                            label = stringResource(R.string.end_week),
                            value = endWeek,
                            min = 1,
                            max = 30,
                            modifier = Modifier.weight(1f),
                            shape = fieldShape,
                            colors = fieldColors
                        ) { endWeek = it }
                    }
                }
            }

            // 上课时段 — 标题（blocks 懒加载以支持大量时段）
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.meeting_slots),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface
                    )
                    Text(
                        text = stringResource(R.string.meeting_slots_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            // 每个 block 独立懒加载，不再一次性全渲染
            itemsIndexed(meetingBlocks) { index, block ->
                val blockIssues = validationIssues.filter { it.blockId == block.id }.map { it.message }
                MeetingBlockEditor(
                    title = stringResource(R.string.slot_n, index + 1),
                    block = block,
                    canRemove = meetingBlocks.size > 1,
                    issues = blockIssues,
                    fieldShape = fieldShape,
                    fieldColors = fieldColors,
                    onRemove = { meetingBlocks.remove(block) }
                )
            }

            // 新增时段按钮
            item {
                Button(
                    onClick = {
                        meetingBlocks.add(
                            MeetingBlockDraft(
                                id = nextBlockId,
                                days = mutableStateListOf(2),
                                initialMode = MeetingInputMode.ByNode,
                                startNode = 3,
                                step = 2,
                                startTime = "10:00",
                                endTime = "11:40"
                            )
                        )
                        nextBlockId += 1
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.secondaryContainer)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.onSecondaryContainer)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.add_slot), color = colors.onSecondaryContainer)
                }
            }

            item {
                Button(
                    onClick = {
                        val issues = validateCourseDraft(
                            courseName = courseName,
                            blocks = meetingBlocks,
                            startWeek = startWeek,
                            endWeek = endWeek,
                            table = currentTable,
                            context = context
                        )
                        validationIssues = issues
                        if (issues.isNotEmpty()) return@Button

                        val tableId = state.selectedTableId ?: return@Button
                        val normalizedStartWeek = minOf(startWeek, endWeek)
                        val normalizedEndWeek = maxOf(startWeek, endWeek)
                        val drafts = meetingBlocks.flatMap { block ->
                            block.days.sorted().map { day ->
                                buildCourseEntity(
                                    tableId = tableId,
                                    groupId = "", // 临时占位，下面统一替换
                                    courseName = courseName.trim(),
                                    teacher = teacher.trim(),
                                    room = room.trim(),
                                    note = note.trim(),
                                    day = day,
                                    block = block,
                                    startWeek = normalizedStartWeek,
                                    endWeek = normalizedEndWeek
                                )
                            }
                        }
                        scope.launch {
                            val repo = SleepyApp.get().repository
                            if (editingCourse != null) {
                                // 编辑：删同 groupId 全部记录，插入所有新草稿
                                val gid = editingCourse.groupId
                                val toInsert = drafts.map { it.copy(groupId = gid) }
                                repo.updateCourseGroup(
                                    tableId = state.selectedTableId!!,
                                    groupId = gid,
                                    newCourses = toInsert
                                )
                            } else {
                                // 新建：所有草稿共享同一个 groupId
                                val gid = java.util.UUID.randomUUID().toString()
                                repo.insertCourses(drafts.map { it.copy(groupId = gid) })
                            }
                            onSaved()
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(if (editingCourse != null) R.string.save_course else R.string.create_course_btn))
                }
            }

            if (editingCourse != null) {
                item {
                    var showDeleteConfirm by remember { mutableStateOf(false) }

                    Button(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.errorContainer)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = colors.onErrorContainer)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.delete_course), color = colors.onErrorContainer)
                    }

                    if (showDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            containerColor = colors.surface,
                            title = { Text(stringResource(R.string.confirm_delete), color = colors.onSurface) },
                            text = { Text(stringResource(R.string.delete_course_confirm, editingCourse.courseName), color = colors.onSurfaceVariant) },
                            confirmButton = {
                                TextButton(onClick = {
                                    showDeleteConfirm = false
                                    scope.launch {
                                        val repo = SleepyApp.get().repository
                                        repo.deleteCourseGroup(state.selectedTableId!!, editingCourse.groupId)
                                        onSaved()
                                    }
                                }) { Text(stringResource(R.string.delete), color = colors.error) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
                            }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}



private fun initialMeetingBlock(course: CourseEntity?): MeetingBlockDraft {
    if (course == null) {
        return MeetingBlockDraft(
            id = 1,
            days = androidx.compose.runtime.mutableStateListOf(1),
            initialMode = MeetingInputMode.ByNode,
            startNode = 1,
            step = 2,
            startTime = "08:00",
            endTime = "09:40"
        )
    }
    val days = androidx.compose.runtime.mutableStateListOf(course.day)
    return MeetingBlockDraft(
        id = 1,
        days = days,
        initialMode = if (course.ownTime) MeetingInputMode.ByClock else MeetingInputMode.ByNode,
        startNode = course.startNode,
        step = course.step,
        startTime = course.startTime.ifBlank { "08:00" },
        endTime = course.endTime.ifBlank { "09:40" }
    )
}

private fun buildCourseEntity(
    tableId: Long,
    groupId: String,
    courseName: String,
    teacher: String,
    room: String,
    note: String,
    day: Int,
    block: MeetingBlockDraft,
    startWeek: Int,
    endWeek: Int
): CourseEntity {
    val ownTime = block.mode == MeetingInputMode.ByClock
    return CourseEntity(
        groupId = groupId,
        tableId = tableId,
        courseName = courseName,
        teacher = teacher,
        room = room,
        note = note,
        day = day,
        startNode = block.startNode,
        step = block.step,
        startWeek = startWeek,
        endWeek = endWeek,
        type = 0,
        color = "#FF6750A4",
        ownTime = ownTime,
        startTime = if (ownTime) block.startTime else "",
        endTime = if (ownTime) block.endTime else ""
    )
}

private fun validateCourseDraft(
    courseName: String,
    blocks: List<MeetingBlockDraft>,
    startWeek: Int,
    endWeek: Int,
    table: TimeTableEntity?,
    context: android.content.Context
): List<ValidationIssue> {
    val issues = mutableListOf<ValidationIssue>()
    if (courseName.isBlank()) issues += ValidationIssue(null, context.getString(R.string.course_name_empty))
    if (startWeek <= 0 || endWeek <= 0) issues += ValidationIssue(null, context.getString(R.string.week_must_be_positive))

    blocks.forEachIndexed { index, block ->
        if (block.days.isEmpty()) {
            issues += ValidationIssue(block.id, context.getString(R.string.slot_at_least_one_day, index + 1))
        }
        when (block.mode) {
            MeetingInputMode.ByNode -> {
                if (block.startNode <= 0) issues += ValidationIssue(block.id, context.getString(R.string.slot_start_node_positive, index + 1))
                if (block.step <= 0) issues += ValidationIssue(block.id, context.getString(R.string.slot_step_positive, index + 1))
            }
            MeetingInputMode.ByClock -> {
                val start = parseHm(block.startTime)
                val end = parseHm(block.endTime)
                if (start == null || end == null) {
                    issues += ValidationIssue(block.id, context.getString(R.string.slot_time_format, index + 1))
                } else if (!start.isBefore(end)) {
                    issues += ValidationIssue(block.id, context.getString(R.string.slot_time_order, index + 1))
                }
            }
        }
    }

    for (i in blocks.indices) {
        for (j in i + 1 until blocks.size) {
            val first = blocks[i]
            val second = blocks[j]
            val overlapDays = first.days.intersect(second.days)
            if (overlapDays.isEmpty()) continue
            val firstRange = blockRangeMinutes(first, table)
            val secondRange = blockRangeMinutes(second, table)
            if (firstRange == null || secondRange == null) continue
            if (firstRange.first < secondRange.second && secondRange.first < firstRange.second) {
                val dayText = overlapDays.sorted().joinToString(" / ") { DateUtils.localizedDay(it, context) }
                issues += ValidationIssue(
                    second.id,
                    context.getString(R.string.slot_time_overlap, i + 1, j + 1, dayText)
                )
            }
        }
    }
    return issues
}

private fun blockRangeMinutes(block: MeetingBlockDraft, table: TimeTableEntity?): Pair<Int, Int>? {
    return when (block.mode) {
        MeetingInputMode.ByClock -> {
            val start = parseHm(block.startTime) ?: return null
            val end = parseHm(block.endTime) ?: return null
            start.hour * 60 + start.minute to end.hour * 60 + end.minute
        }
        MeetingInputMode.ByNode -> {
            val timeJson = table?.timeJson ?: TimeTableUtils.DEFAULT_TIME_JSON
            val nodes = parseNodeMinuteMap(timeJson)
            val start = nodes[block.startNode]?.first ?: return null
            val end = nodes[block.startNode + block.step - 1]?.second ?: return null
            start to end
        }
    }
}

private fun parseNodeMinuteMap(timeJson: String): Map<Int, Pair<Int, Int>> = try {
    val arr = JSONArray(timeJson)
    buildMap {
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val node = o.getInt("node")
            val start = parseHm(o.getString("start")) ?: continue
            val end = parseHm(o.getString("end")) ?: continue
            put(node, start.hour * 60 + start.minute to end.hour * 60 + end.minute)
        }
    }
} catch (_: Exception) {
    emptyMap()
}

private fun parseHm(value: String): LocalTime? = try {
    LocalTime.parse(value.trim())
} catch (_: Exception) {
    null
}

@Composable
private fun ValidationCard(issues: List<ValidationIssue>) {
    val colors = SleepyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.errorContainer)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.fix_issues_first),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onErrorContainer
        )
        issues.take(4).forEach { issue ->
            Text(
                text = "• ${issue.message}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onErrorContainer
            )
        }
        if (issues.size > 4) {
            Text(
                text = stringResource(R.string.more_unexpanded, issues.size - 4),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onErrorContainer
            )
        }
    }
}

@Composable
private fun CardSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    val colors = SleepyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
        content()
    }
}

@Composable
private fun MeetingBlockEditor(
    title: String,
    block: MeetingBlockDraft,
    canRemove: Boolean,
    issues: List<String>,
    fieldShape: RoundedCornerShape,
    fieldColors: androidx.compose.material3.TextFieldColors,
    onRemove: () -> Unit
) {
    val colors = SleepyTheme.colors
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainerHigh)
            .border(
                width = if (issues.isNotEmpty()) 1.5.dp else 0.dp,
                color = if (issues.isNotEmpty()) colors.error else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onSurface
                )
                Text(
                    text = if (block.days.isEmpty()) stringResource(R.string.select_at_least_one_day) else stringResource(R.string.selected_days, block.days.sorted().joinToString(" / ") { DateUtils.localizedDay(it, context) }),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
            if (canRemove) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.delete_slot), tint = colors.onSurfaceVariant)
                }
            }
        }

        ModePicker(mode = block.mode, onChange = { block.mode = it })
        MultiDayPicker(selectedDays = block.days.toSet(), onToggleDay = { day ->
            if (day in block.days) block.days.remove(day) else block.days.add(day)
        })

        when (block.mode) {
            MeetingInputMode.ByNode -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NumberField(
                        label = stringResource(R.string.start_node),
                        value = block.startNode,
                        min = 1,
                        max = 12,
                        modifier = Modifier.weight(1f)
                    , shape = fieldShape, colors = fieldColors) { block.startNode = it }
                    NumberField(
                        label = stringResource(R.string.step_count),
                        value = block.step,
                        min = 1,
                        max = 8,
                        modifier = Modifier.weight(1f)
                    , shape = fieldShape, colors = fieldColors) { block.step = it }
                }
            }
            MeetingInputMode.ByClock -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TimePickerField(
                        label = stringResource(R.string.start_time),
                        value = block.startTime,
                        onValueChange = { block.startTime = it },
                        modifier = Modifier.weight(1f)
                    )
                    TimePickerField(
                        label = stringResource(R.string.end_time),
                        value = block.endTime,
                        onValueChange = { block.endTime = it },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (issues.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                issues.forEach { issue ->
                    Text(
                        text = issue,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.error
                    )
                }
            }
        }
    }
}

@Composable
private fun ModePicker(
    mode: MeetingInputMode,
    onChange: (MeetingInputMode) -> Unit
) {
    val colors = SleepyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerHighest)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModeChip(
            label = stringResource(R.string.mode_by_node),
            selected = mode == MeetingInputMode.ByNode,
            modifier = Modifier.weight(1f),
            onClick = { onChange(MeetingInputMode.ByNode) }
        )
        ModeChip(
            label = stringResource(R.string.mode_by_time),
            selected = mode == MeetingInputMode.ByClock,
            modifier = Modifier.weight(1f),
            onClick = { onChange(MeetingInputMode.ByClock) }
        )
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = SleepyTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = if (selected) colors.onPrimary else colors.onSurfaceVariant
        )
    }
}

@Composable
private fun MultiDayPicker(
    selectedDays: Set<Int>,
    onToggleDay: (Int) -> Unit
) {
    val colors = SleepyTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in listOf((1..4).toList(), (5..7).toList())) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { day ->
                    val selected = day in selectedDays
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) colors.primary else colors.surfaceContainerHighest)
                            .border(
                                width = if (selected) 0.dp else 1.dp,
                                color = if (selected) Color.Transparent else colors.outlineVariant,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { onToggleDay(day) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = DateUtils.localizedDay(day, LocalContext.current),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                            color = if (selected) colors.onPrimary else colors.onSurface
                        )
                    }
                }
                if (row.size < 4) repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NumberField(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape,
    colors: androidx.compose.material3.TextFieldColors,
    onChange: (Int) -> Unit
) {
    var text by remember { mutableStateOf(value.toString()) }

    // 仅在外部 value 变化且用户当前文本为空/不匹配时同步
    LaunchedEffect(value) {
        val parsed = text.toIntOrNull()
        if (parsed != value && text.isNotEmpty()) {
            text = value.toString()
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { txt ->
            text = txt
            if (txt.isEmpty()) {
                // 清空时回调最小值，保证 model 有合法值
                onChange(min)
            } else {
                val v = txt.toIntOrNull()
                if (v != null) {
                    onChange(v.coerceIn(min, max))
                }
                // 非数字字符不回调，但保留 text 让用户继续编辑
            }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        shape = shape,
        colors = colors
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape,
    colors: androidx.compose.material3.TextFieldColors,
    onChange: (String) -> Unit
) {
    var text by remember(value) { mutableStateOf(value) }

    OutlinedTextField(
        value = text,
        onValueChange = { txt ->
            val filtered = txt.take(5)
            text = filtered
            onChange(filtered)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        shape = shape,
        colors = colors,
        supportingText = { Text(stringResource(R.string.time_format_hint)) }
    )
}
