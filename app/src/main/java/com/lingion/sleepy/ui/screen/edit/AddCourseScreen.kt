package com.lingion.sleepy.ui.screen.edit

import com.lingion.sleepy.R
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CornerBasedShape
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
import androidx.compose.material3.TextField
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.component.SegmentedSwitcher
import com.lingion.sleepy.ui.component.TimePickerField
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.util.weekRangesOverlap
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
    endTime: String,
    startWeek: Int = 1,
    endWeek: Int = 16,
    weekType: Int = 0
) {
    var mode by mutableStateOf(initialMode)
    var startNode by mutableStateOf(startNode)
    var step by mutableStateOf(step)
    var startTime by mutableStateOf(startTime)
    var endTime by mutableStateOf(endTime)
    var startWeek by mutableStateOf(startWeek)
    var endWeek by mutableStateOf(endWeek)
    var weekType by mutableStateOf(weekType)
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
    val fieldShape = SleepyTheme.fieldShape
    val fieldColors = SleepyTheme.fieldColors()

    // issue#9: 之前 startNode/step 硬编码 max=12/8, 12 节连排时仍允许 step=8 → startNode=12, step=8
    // 会显示成 12-19 越过实际节数。改为从当前 timeJson 解析实际节点数, 默认 12。
    val maxNode = remember(currentTable?.id, currentTable?.timeJson) {
        try {
            val arr = JSONArray(currentTable?.timeJson ?: TimeTableUtils.DEFAULT_TIME_JSON)
            (0 until arr.length()).mapNotNull { arr.getJSONObject(it).optInt("node", 0) }
                .takeIf { it.isNotEmpty() }?.max() ?: 12
        } catch (_: Exception) { 12 }
    }

    var courseName by remember(editingCourse?.id) { mutableStateOf(editingCourse?.courseName ?: "") }
    var teacher by remember(editingCourse?.id) { mutableStateOf(editingCourse?.teacher ?: "") }
    var room by remember(editingCourse?.id) { mutableStateOf(editingCourse?.room ?: "") }
    var note by remember(editingCourse?.id) { mutableStateOf(editingCourse?.note ?: "") }
    var courseColor by remember(editingCourse?.id) {
        val c = editingCourse?.color ?: ""
        mutableStateOf(if (c.isBlank() || c == "#FF6750A4") "" else c)
    }
    var startWeek by remember(editingCourse?.id) { mutableIntStateOf(editingCourse?.startWeek ?: 1) }
    var endWeek by remember(editingCourse?.id) { mutableIntStateOf(editingCourse?.endWeek ?: 16) }
    var nextBlockId by remember(editingCourse?.id) { mutableIntStateOf(2) }
    var validationIssues by remember { mutableStateOf<List<ValidationIssue>>(emptyList()) }
    var showColorPicker by remember { mutableStateOf(false) }

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
                val slots = groupSlotsForEdit(groupCourses)
                meetingBlocks.clear()
                var bid = 1
                for (courses in slots) {
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
                        endTime = first.endTime.ifBlank { "09:40" },
                        startWeek = first.startWeek,
                        endWeek = first.endWeek,
                        weekType = first.type
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
                        TextField(
                            value = courseName,
                            onValueChange = { courseName = it },
                            label = { Text(stringResource(R.string.course_name_required)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = fieldShape,
                            colors = fieldColors
                        )
                        TextField(
                            value = teacher,
                            onValueChange = { teacher = it },
                            label = { Text(stringResource(R.string.course_teacher)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = fieldShape,
                            colors = fieldColors
                        )
                        TextField(
                            value = room,
                            onValueChange = { room = it },
                            label = { Text(stringResource(R.string.course_room)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = fieldShape,
                            colors = fieldColors
                        )
                        TextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text(stringResource(R.string.course_note)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            shape = fieldShape,
                            colors = fieldColors
                        )
                        // 颜色选择器
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.course_color),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.onSurfaceVariant
                            )
                            // 自动色
                            AutoColorDot(
                                selected = courseColor.isBlank(),
                                onClick = { courseColor = "" }
                            )
                            // 自定义色圆点 — 点击弹出调色盘
                            CustomColorDot(
                                hex = courseColor.takeIf { it.isNotBlank() },
                                onClick = { showColorPicker = true }
                            )
                            if (courseColor.isNotBlank()) {
                                Text(
                                    text = courseColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
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
                    // 显式应用 — 不再隐式下发，由用户一键覆盖所有时段
                    Button(
                        onClick = {
                            meetingBlocks.forEach { b ->
                                b.startWeek = startWeek
                                b.endWeek = endWeek
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.regularHeight),
                        shape = SleepyTheme.Buttons.shape,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.secondaryContainer)
                    ) {
                        Text(stringResource(R.string.apply_to_all_slots), color = colors.onSecondaryContainer)
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
                    maxNode = maxNode,
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
                                endTime = "11:40",
                                startWeek = 1,
                                endWeek = 16,
                                weekType = 0
                            )
                        )
                        nextBlockId += 1
                    },
                    modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.regularHeight),
                    shape = SleepyTheme.Buttons.shape,
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

                        val draftTableId = state.selectedTableId  // 进入 scope 前取，drafts 需要
                        val drafts = meetingBlocks.flatMap { block ->
                            block.days.sorted().map { day ->
                                buildCourseEntity(
                                    tableId = draftTableId ?: 0L,
                                    groupId = "",
                                    courseName = courseName.trim(),
                                    teacher = teacher.trim(),
                                    room = room.trim(),
                                    note = note.trim(),
                                    day = day,
                                    block = block,
                                    courseColor = courseColor.ifBlank { "#FF6750A4" }
                                )
                            }
                        }
                        scope.launch {
                            val repo = SleepyApp.get().repository
                            // 没表就自动建一张，保证 selectedTableId 非空
                            val tableId = state.selectedTableId
                                ?: viewModel.createEmptyTable()
                            // 用真实 tableId 修正 drafts
                            val fixedDrafts = drafts.map { it.copy(tableId = tableId) }
                            if (editingCourse != null) {
                                // 编辑：删同 groupId 全部记录，插入所有新草稿
                                val gid = editingCourse.groupId
                                val toInsert = fixedDrafts.map { it.copy(groupId = gid) }
                                repo.updateCourseGroup(
                                    tableId = tableId,
                                    groupId = gid,
                                    newCourses = toInsert
                                )
                            } else {
                                // 新建：所有草稿共享同一个 groupId
                                val gid = java.util.UUID.randomUUID().toString()
                                repo.insertCourses(fixedDrafts.map { it.copy(groupId = gid) })
                            }
                            onSaved()
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SleepyTheme.Buttons.ctaHeight),
                    shape = SleepyTheme.Buttons.shape
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
                        modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.regularHeight),
                        shape = SleepyTheme.Buttons.shape,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.errorContainer)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = colors.onErrorContainer)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.delete_course), color = colors.onErrorContainer)
                    }

                    if (showDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            title = { Text(stringResource(R.string.confirm_delete), color = colors.onSurface) },
                            text = { Text(stringResource(R.string.delete_course_confirm, editingCourse.courseName), color = colors.onSurfaceVariant) },
                            confirmButton = {
                                TextButton(onClick = {
                                    showDeleteConfirm = false
                                    scope.launch {
                                        val repo = SleepyApp.get().repository
                                        val tid = state.selectedTableId
                                        if (tid != null) {
                                            repo.deleteCourseGroup(tid, editingCourse.groupId)
                                        }
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

        if (showColorPicker) {
            ColorPickerDialog(
                initialHex = courseColor,
                onConfirm = { hex ->
                    courseColor = hex
                    showColorPicker = false
                },
                onDismiss = { showColorPicker = false }
            )
        }
    }
}



/** 编辑回填：按完整时段特征分组。周次/单双周参与分组，
 *  保证「同节次不同周次」回填成两个 block 而不是被错误合并。 */
internal fun groupSlotsForEdit(courses: List<CourseEntity>): List<List<CourseEntity>> =
    courses.groupBy { c ->
        "${c.ownTime}|${c.startNode}|${c.step}|${c.startTime}|${c.endTime}|${c.startWeek}|${c.endWeek}|${c.type}"
    }.values.toList()

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
        endTime = course.endTime.ifBlank { "09:40" },
        startWeek = course.startWeek,
        endWeek = course.endWeek,
        weekType = course.type
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
    courseColor: String = "#FF6750A4"
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
        startWeek = block.startWeek,
        endWeek = block.endWeek,
        type = block.weekType,
        color = courseColor.ifBlank { "#FF6750A4" },
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
    // issue#9: 该课表最大节次(从 timeJson 解析), 用于判断 startNode+step-1 是否越界
    val maxNode = try {
        val arr = JSONArray(table?.timeJson ?: TimeTableUtils.DEFAULT_TIME_JSON)
        (0 until arr.length()).mapNotNull { arr.getJSONObject(it).optInt("node", 0) }
            .takeIf { it.isNotEmpty() }?.max() ?: 12
    } catch (_: Exception) { 12 }

    blocks.forEachIndexed { index, block ->
        if (block.days.isEmpty()) {
            issues += ValidationIssue(block.id, context.getString(R.string.slot_at_least_one_day, index + 1))
        }
        if (block.startWeek > block.endWeek) issues += ValidationIssue(block.id, context.getString(R.string.slot_week_order, index + 1))
        when (block.mode) {
            MeetingInputMode.ByNode -> {
                if (block.startNode <= 0) issues += ValidationIssue(block.id, context.getString(R.string.slot_start_node_positive, index + 1))
                if (block.step <= 0) issues += ValidationIssue(block.id, context.getString(R.string.slot_step_positive, index + 1))
                // issue#9: startNode+step-1 越过该课表实际最大节次时拒绝保存
                val endNode = block.startNode + block.step - 1
                if (endNode > maxNode) {
                    issues += ValidationIssue(
                        block.id,
                        context.getString(R.string.slot_step_exceeds_max, index + 1, block.startNode, endNode, maxNode)
                    )
                }
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
                if (!weekRangesOverlap(
                        first.startWeek, first.endWeek, first.weekType,
                        second.startWeek, second.endWeek, second.weekType
                    )
                ) continue
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
            .clip(SleepyTheme.shapes.large)
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
            .clip(SleepyTheme.shapes.extraLarge)
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
    fieldShape: CornerBasedShape,
    fieldColors: androidx.compose.material3.TextFieldColors,
    maxNode: Int,
    onRemove: () -> Unit
) {
    val colors = SleepyTheme.colors
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SleepyTheme.shapes.large)
            // 错误态: errorContainer 色块底替代 error 描边 (2026-08-25 色块统一)
            .background(if (issues.isNotEmpty()) colors.errorContainer else colors.surfaceContainerHigh)
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
                        max = maxNode,
                        modifier = Modifier.weight(1f)
                    , shape = fieldShape, colors = fieldColors) {
                        block.startNode = it
                        // startNode 上调 → step 上限缩到 (maxNode - startNode + 1), 防止越界
                        val stepCap = (maxNode - block.startNode + 1).coerceAtLeast(1)
                        if (block.step > stepCap) block.step = stepCap
                    }
                    NumberField(
                        label = stringResource(R.string.step_count),
                        value = block.step,
                        min = 1,
                        max = (maxNode - block.startNode + 1).coerceAtLeast(1),
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

        // 周次 — 每时段独立
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NumberField(
                label = stringResource(R.string.slot_week_range) + " " + stringResource(R.string.start_week),
                value = block.startWeek,
                min = 1,
                max = 30,
                modifier = Modifier.weight(1f),
                shape = fieldShape,
                colors = fieldColors
            ) { block.startWeek = it }
            NumberField(
                label = stringResource(R.string.slot_week_range) + " " + stringResource(R.string.end_week),
                value = block.endWeek,
                min = 1,
                max = 30,
                modifier = Modifier.weight(1f),
                shape = fieldShape,
                colors = fieldColors
            ) { block.endWeek = it }
        }
        // 单双周三态 — 项目统一 SegmentedSwitcher（色块选中，禁描边规则）
        SegmentedSwitcher(
            options = listOf(
                0 to stringResource(R.string.week_every),
                1 to stringResource(R.string.week_odd),
                2 to stringResource(R.string.week_even)
            ),
            selected = block.weekType,
            onSelect = { block.weekType = it },
            modifier = Modifier.fillMaxWidth()
        )

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
    // 2026-08-25 用户指令: 全 app 统一色块禁描线 — M3 SegmentedButton 是描边风格,
    // 换项目统一的 SegmentedSwitcher (主页周视图/网格同款)
    SegmentedSwitcher(
        options = listOf(
            MeetingInputMode.ByNode to stringResource(R.string.mode_by_node),
            MeetingInputMode.ByClock to stringResource(R.string.mode_by_time)
        ),
        selected = mode,
        onSelect = onChange,
        modifier = Modifier.fillMaxWidth()
    )
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
                            .height(40.dp)
                            .clip(SleepyTheme.shapes.medium)
                            .background(if (selected) colors.primary else colors.surfaceContainerHighest)
                            .noRippleClickable { onToggleDay(day) },
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
    shape: CornerBasedShape,
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

    TextField(
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
    shape: CornerBasedShape,
    colors: androidx.compose.material3.TextFieldColors,
    onChange: (String) -> Unit
) {
    var text by remember(value) { mutableStateOf(value) }

    TextField(
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

// ── 课程颜色选择器 ──

@Composable
private fun AutoColorDot(selected: Boolean, onClick: () -> Unit) {
    // IconButton 包裹 — 裸 32dp 圆点的涟漪半径过小且无 48dp 最小触达区
    IconButton(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(
                    if (selected) SleepyTheme.colors.primaryContainer
                    else SleepyTheme.colors.surfaceVariant
                )
        ) {
            Text(
                text = stringResource(R.string.label_from),
                fontSize = 11.sp,
                color = if (selected) SleepyTheme.colors.onPrimaryContainer else SleepyTheme.colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun CustomColorDot(hex: String?, onClick: () -> Unit) {
    val c = if (hex != null) {
        runCatching { Color(android.graphics.Color.parseColor(hex)) }
            .getOrDefault(SleepyTheme.colors.surfaceVariant)
    } else {
        SleepyTheme.colors.surfaceVariant
    }
    // IconButton 包裹 — 裸 32dp 圆点的涟漪半径过小且无 48dp 最小触达区
    IconButton(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(c)
        ) {
            if (hex == null) {
                Text(
                    text = "＋",
                    fontSize = 16.sp,
                    color = SleepyTheme.colors.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ColorPickerDialog(
    initialHex: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SleepyTheme.colors

    // 解析初始 HSV
    val initialHSV = remember {
        val hsv = FloatArray(3)
        val rgb = runCatching { android.graphics.Color.parseColor(initialHex) }
            .getOrDefault(0xFF6750A4.toInt())
        android.graphics.Color.colorToHSV(rgb, hsv)
        hsv
    }

    var hue by remember { mutableStateOf(initialHSV[0]) }
    var saturation by remember { mutableStateOf(initialHSV[1]) }
    var value by remember { mutableStateOf(initialHSV[2]) }

    val currentColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
    val currentHex = String.format("#%08X", android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.course_color), color = colors.onSurface)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // SV 面板 — 大方块，横向拖=饱和度，纵向拖=明度
                SVPanel(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onSVChange = { s, v ->
                        saturation = s
                        value = v
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(SleepyTheme.shapes.large)
                )

                // 色相滑条
                HueSlider(
                    hue = hue,
                    onHueChange = { hue = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(SleepyTheme.shapes.large)
                )

                // 预览 + Hex
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(currentColor)
                    )
                    Text(
                        text = currentHex,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentHex) }) {
                Text(stringResource(R.string.ok), color = colors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/** 饱和度-明度面板：横=饱和度(0→1)，纵=明度(1→0)，背景色=当前色相 */
@Composable
private fun SVPanel(
    hue: Float,
    saturation: Float,
    value: Float,
    onSVChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val pureHue = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        val x = (change.position.x / size.width).coerceIn(0f, 1f)
                        val y = (change.position.y / size.height).coerceIn(0f, 1f)
                        onSVChange(x, 1f - y)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val x = (offset.x / size.width).coerceIn(0f, 1f)
                        val y = (offset.y / size.height).coerceIn(0f, 1f)
                        onSVChange(x, 1f - y)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 底层：纯色相
            drawRect(pureHue)
            // 白色横向渐变（左→右 = 白→透明）
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = listOf(Color.White, Color.Transparent)
                )
            )
            // 黑色纵向渐变（上→下 = 透明→黑）
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black)
                )
            )
            // 指示器
            val cx = saturation * size.width
            val cy = (1f - value) * size.height
            drawCircle(Color.White, radius = 10f, center = androidx.compose.ui.geometry.Offset(cx, cy))
            drawCircle(Color.Black.copy(alpha = SleepyTheme.Alpha.hairline), radius = 10f, center = androidx.compose.ui.geometry.Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
        }
    }
}

/** 色相滑条：360°彩虹水平条 */
@Composable
private fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        val x = (change.position.x / size.width).coerceIn(0f, 1f)
                        onHueChange(x * 360f)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val x = (offset.x / size.width).coerceIn(0f, 1f)
                        onHueChange(x * 360f)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val hueColors = listOf(
                Color(android.graphics.Color.HSVToColor(floatArrayOf(0f, 1f, 1f))),
                Color(android.graphics.Color.HSVToColor(floatArrayOf(60f, 1f, 1f))),
                Color(android.graphics.Color.HSVToColor(floatArrayOf(120f, 1f, 1f))),
                Color(android.graphics.Color.HSVToColor(floatArrayOf(180f, 1f, 1f))),
                Color(android.graphics.Color.HSVToColor(floatArrayOf(240f, 1f, 1f))),
                Color(android.graphics.Color.HSVToColor(floatArrayOf(300f, 1f, 1f))),
                Color(android.graphics.Color.HSVToColor(floatArrayOf(360f, 1f, 1f)))
            )
            drawRect(brush = androidx.compose.ui.graphics.Brush.horizontalGradient(colors = hueColors))
            // 指示器
            val cx = (hue / 360f) * size.width
            val cy = size.height / 2f
            drawCircle(Color.White, radius = 10f, center = androidx.compose.ui.geometry.Offset(cx, cy))
            drawCircle(
                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))),
                radius = 8f,
                center = androidx.compose.ui.geometry.Offset(cx, cy)
            )
        }
    }
}
