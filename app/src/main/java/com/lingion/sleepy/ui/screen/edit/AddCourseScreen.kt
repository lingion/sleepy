package com.lingion.sleepy.ui.screen.edit

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.DateUtils
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.LocalTime

enum class MeetingInputMode { ByNode, ByClock }

private data class MeetingBlockDraft(
    val id: Int,
    val days: androidx.compose.runtime.snapshots.SnapshotStateList<Int>,
    var mode: MeetingInputMode,
    var startNode: Int,
    var step: Int,
    var startTime: String,
    var endTime: String
)

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

    val canSave = courseName.isNotBlank() && meetingBlocks.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("手动创建课程") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground
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
                    title = "课程基础信息",
                    subtitle = "老师、地点统一填写；一门课可以挂多个时段块"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = courseName,
                            onValueChange = { courseName = it },
                            label = { Text("课程名 *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = fieldShape,
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = teacher,
                            onValueChange = { teacher = it },
                            label = { Text("老师") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = fieldShape,
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = room,
                            onValueChange = { room = it },
                            label = { Text("地点 / 教室") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = fieldShape,
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("备注（可选）") },
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
                    title = "周次范围",
                    subtitle = "整门课统一周次；同一课程内部时段会自动做冲突检查"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        NumberField(
                            label = "起始周",
                            value = startWeek,
                            min = 1,
                            max = 30,
                            modifier = Modifier.weight(1f),
                            shape = fieldShape,
                            colors = fieldColors
                        ) { startWeek = it }
                        NumberField(
                            label = "结束周",
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

            item {
                CardSection(
                    title = "上课时段",
                    subtitle = "一个课程可有任意多个时段；每个时段都可多选星期，并可按节次或按时间录入"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        meetingBlocks.forEachIndexed { index, block ->
                            val blockIssues = validationIssues.filter { it.blockId == block.id }.map { it.message }
                            MeetingBlockEditor(
                                title = "时段 ${index + 1}",
                                block = block,
                                canRemove = meetingBlocks.size > 1,
                                issues = blockIssues,
                                fieldShape = fieldShape,
                                fieldColors = fieldColors,
                                onRemove = { meetingBlocks.remove(block) }
                            )
                        }

                        Button(
                            onClick = {
                                meetingBlocks.add(
                                    MeetingBlockDraft(
                                        id = nextBlockId,
                                        days = mutableStateListOf(2),
                                        mode = MeetingInputMode.ByNode,
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
                            Text("新增一个上课时段", color = colors.onSecondaryContainer)
                        }
                    }
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
                            table = currentTable
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
                                repo.updateCourse(
                                    drafts.first().copy(id = editingCourse.id)
                                )
                            } else {
                                repo.insertCourses(drafts)
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
                    Text("创建课程")
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
            mode = MeetingInputMode.ByNode,
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
        mode = if (course.ownTime) MeetingInputMode.ByClock else MeetingInputMode.ByNode,
        startNode = course.startNode,
        step = course.step,
        startTime = course.startTime.ifBlank { "08:00" },
        endTime = course.endTime.ifBlank { "09:40" }
    )
}

private fun buildCourseEntity(
    tableId: Long,
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
    table: TimeTableEntity?
): List<ValidationIssue> {
    val issues = mutableListOf<ValidationIssue>()
    if (courseName.isBlank()) issues += ValidationIssue(null, "课程名不能为空")
    if (startWeek <= 0 || endWeek <= 0) issues += ValidationIssue(null, "周次必须大于 0")

    blocks.forEachIndexed { index, block ->
        if (block.days.isEmpty()) {
            issues += ValidationIssue(block.id, "时段 ${index + 1} 至少选择一天")
        }
        when (block.mode) {
            MeetingInputMode.ByNode -> {
                if (block.startNode <= 0) issues += ValidationIssue(block.id, "时段 ${index + 1} 的开始节必须大于 0")
                if (block.step <= 0) issues += ValidationIssue(block.id, "时段 ${index + 1} 的连上节数必须大于 0")
            }
            MeetingInputMode.ByClock -> {
                val start = parseHm(block.startTime)
                val end = parseHm(block.endTime)
                if (start == null || end == null) {
                    issues += ValidationIssue(block.id, "时段 ${index + 1} 的开始/结束时间格式应为 HH:mm")
                } else if (!start.isBefore(end)) {
                    issues += ValidationIssue(block.id, "时段 ${index + 1} 的开始时间必须早于结束时间")
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
                val dayText = overlapDays.sorted().joinToString(" / ") { DateUtils.chineseDay(it) }
                issues += ValidationIssue(
                    second.id,
                    "时段 ${i + 1} 与时段 ${j + 1} 在 $dayText 存在时间重叠"
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
            val timeJson = table?.timeJson ?: TimeTableEntity.DEFAULT_TIME_JSON
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
            text = "请先修正这些问题",
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
                text = "还有 ${issues.size - 4} 条未展开",
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
                    text = if (block.days.isEmpty()) "至少选一天" else "已选：${block.days.sorted().joinToString(" / ") { DateUtils.chineseDay(it) }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
            if (canRemove) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Close, contentDescription = "删除时段", tint = colors.onSurfaceVariant)
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
                        label = "开始第几节",
                        value = block.startNode,
                        min = 1,
                        max = 12,
                        modifier = Modifier.weight(1f)
                    , shape = fieldShape, colors = fieldColors) { block.startNode = it }
                    NumberField(
                        label = "连上几节",
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
                    TimeField(
                        label = "开始时间",
                        value = block.startTime,
                        modifier = Modifier.weight(1f)
                    , shape = fieldShape, colors = fieldColors) { block.startTime = it }
                    TimeField(
                        label = "结束时间",
                        value = block.endTime,
                        modifier = Modifier.weight(1f)
                    , shape = fieldShape, colors = fieldColors) { block.endTime = it }
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
            label = "按节次",
            selected = mode == MeetingInputMode.ByNode,
            modifier = Modifier.weight(1f),
            onClick = { onChange(MeetingInputMode.ByNode) }
        )
        ModeChip(
            label = "按时间",
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
                            text = DateUtils.chineseDay(day).removePrefix("周"),
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
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { txt -> txt.toIntOrNull()?.let { onChange(it.coerceIn(min, max)) } },
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
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(5)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        shape = shape,
        colors = colors,
        supportingText = { Text("格式 HH:mm") }
    )
}
