package com.lingion.sleepy.ui.screen.imports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lingion.sleepy.R
import com.lingion.sleepy.console.ui.dbgTag
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.parser.ScheduleParser
import com.lingion.sleepy.ui.theme.SleepyTheme

/**
 * 2026-10-26 用户定令: 添加课程/创建课表的每条导入路径(从现有课表、教务导入、
 * 文本粘贴、文件导入)解析后先进入逐组勾选界面, 用户挑出要添加的课程再确认。
 *
 * 卡片复用课程清单(CourseListScreen)的聚合样式: 按课程组一张卡, 前置复选框;
 * 默认全选; 顶部「全选/取消全选」为开关(全部选中时点击 → 全不选, 否则 → 全选)。
 * 手动添加课程是现场填表新增一门课, 无批量可挑, 不走本页。
 */

/**
 * 导入勾选的分组键: groupId 权威非空时按 groupId; 空 groupId 按(课程名+教师)兜底,
 * 保证解析产物在勾选与过滤两侧得到同一稳定键(纯函数, 不依赖对象身份)。
 */
internal fun importCourseGroupKey(course: CourseEntity): String =
    course.groupId.ifBlank { "name:" + course.courseName.trim() + "|" + course.teacher.trim() }

internal data class ImportCourseGroup(
    val id: String,
    val courseName: String,
    val teacher: String,
    val rooms: List<String>,
    val rows: List<CourseEntity>,
)

internal fun groupCoursesForImportSelection(courses: List<CourseEntity>): List<ImportCourseGroup> =
    courses.groupBy(::importCourseGroupKey).map { (id, rows) ->
        ImportCourseGroup(
            id = id,
            courseName = rows.first().courseName,
            teacher = rows.first().teacher,
            rooms = rows.map { it.room.trim() }.filter { it.isNotEmpty() }.distinct().sorted(),
            rows = rows,
        )
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.courseName })

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseSelectionSheet(
    parseResult: ScheduleParser.ParseResult,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val groups = remember(parseResult) { groupCoursesForImportSelection(parseResult.courses) }
    val allIds = remember(groups) { groups.map { it.id } }
    // rememberSaveable 只存 List<String>(Bundle 原生), 进程恢复后集合一致。
    var selected by rememberSaveable(allIds) { mutableStateOf(allIds.toList()) }
    val selectedSet = remember(selected) { selected.toSet() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = colors.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.select_courses_title))
                            Text(
                                text = stringResource(
                                    R.string.select_courses_count,
                                    selectedSet.size, groups.size
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        // 全选开关: 当前已全选 → 一键取消全选; 否则 → 一键全选。
                        val toggleLabel = stringResource(
                            if (selectedSet.size == allIds.size) R.string.select_courses_none
                            else R.string.select_courses_all
                        )
                        TextButton(
                            onClick = {
                                selected = if (selectedSet.size == allIds.size) emptyList() else allIds
                            },
                            modifier = Modifier.dbgTag("import_select_all", text = toggleLabel, role = "button")
                        ) {
                            Text(toggleLabel)
                        }
                        TextButton(
                            enabled = selectedSet.isNotEmpty(),
                            onClick = { onConfirm(selectedSet) },
                            modifier = Modifier.dbgTag("import_select_confirm", text = stringResource(R.string.import_confirm), role = "button")
                        ) { Text(stringResource(R.string.import_confirm)) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.background,
                        titleContentColor = colors.onBackground
                    )
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(groups, key = { _, g -> g.id }) { index, group ->
                    val checked = group.id in selectedSet
                    val courseLabel = group.courseName.ifBlank { stringResource(R.string.course_detail_title) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SleepyTheme.shapes.large)
                            .background(colors.surfaceContainer)
                            .padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { on ->
                                selected = if (on) selected + group.id else selected - group.id
                            },
                            modifier = Modifier.dbgTag("select_course#$index", text = courseLabel, role = "checkbox")
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = group.courseName.ifBlank { stringResource(R.string.course_detail_title) },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.onSurface
                            )
                            if (group.teacher.isNotBlank()) {
                                MetaLine(icon = Icons.Outlined.Person, text = group.teacher)
                            }
                            group.rooms.forEach { room -> MetaLine(icon = Icons.Outlined.LocationOn, text = room) }
                            Text(
                                text = stringResource(R.string.course_list_arrangements, group.rows.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}
