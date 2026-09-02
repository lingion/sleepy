package com.lingion.sleepy.ui.component

import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.theme.SleepyTextStyle
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.ConflictCluster
import com.lingion.sleepy.util.ConflictLayoutEngine

/**
 * 课程详情 Bottom Sheet — 仿 switchable.html .modal-backdrop
 *
 * 结构:
 * ┌────────────────────────────┐
 * │ 课程详情              [×] │  ← modal-header (surface-container)
 * ├────────────────────────────┤
 * │ ⏰ 1-2节 · 08:00-09:35    │  ← modal-time (secondary-container pill)
 * │ 课程  高数                 │
 * │ 老师  张三                 │
 * │ 地点  21B4115中            │
 * │ ─── 选择默认置顶课程 ─── │ ← 仅当 course ∈ 冲突簇时显示
 * │ ( ) 工科数学分析          │
 * │ (•) 电路与电子            │
 * │ [   编辑课程   ]          │
 * └────────────────────────────┘
 */
@Composable
fun CourseDetailSheet(
    course: CourseEntity?,
    timeString: String? = null,
    allCourses: List<CourseEntity> = emptyList(),
    onDismiss: () -> Unit,
    onEdit: ((CourseEntity) -> Unit)? = null,
    onDefaultTopChanged: ((clusterKey: String, layerRepId: Long?) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (course != null) {
        // 找出 course 所在冲突簇(仅当 day 下 ≥2 课区间相交才有)。
        // findClusters 按 day 分桶, 簇键 = "${day}:${anchor.startNode}:${anchor.step}",
        // 与 ConflictClusterCard / topOverrides 用同一公式。
        val clusterInfo: ConflictCluster? = remember(course, allCourses) {
            val sameDay = allCourses.filter { it.day == course.day }
            ConflictLayoutEngine.findClusters(sameDay)
                .firstOrNull { it.courses.any { c -> c.id == course.id } }
                ?.takeIf { it.courses.size >= 2 }
        }

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                // Header
                SheetHeader(
                    title = course.courseName.ifBlank { stringResource(R.string.course_detail_title) }
                )

                // Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (timeString != null) {
                        TimeChip(text = timeString)
                    }

                    DetailRow(key = stringResource(R.string.course_field_name), value = course.courseName.ifBlank { "—" })
                    if (course.teacher.isNotBlank()) {
                        DetailRow(key = stringResource(R.string.course_field_teacher), value = course.teacher)
                    }
                    if (course.room.isNotBlank()) {
                        DetailRow(key = stringResource(R.string.course_field_room), value = course.room)
                    }
                    DetailRow(key = stringResource(R.string.course_field_week), value = stringResource(R.string.course_week_range, course.shortNodeString(LocalContext.current), course.startWeek, course.endWeek))
                    if (course.note.isNotBlank()) {
                        DetailRow(key = stringResource(R.string.course_field_note), value = course.note)
                    }

                    // 默认置顶选择区 — 仅冲突簇显示
                    if (clusterInfo != null) {
                        DefaultTopPickerSection(
                            cluster = clusterInfo,
                            onDefaultTopChanged = onDefaultTopChanged
                        )
                    }

                    if (onEdit != null) {
                        Button(
                            onClick = { onEdit(course) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = SleepyTheme.shapes.large,
                            colors = ButtonDefaults.buttonColors(containerColor = SleepyTheme.colors.primary)
                        ) {
                            Text(stringResource(R.string.course_detail_edit_course))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 默认置顶选择区 — v7.9 设计:
 * - 按图层(链式分组)一行;每行 = 单选 + 该图层全部课程名(顿号分隔)
 * - 默认无勾选(系统按 primaryComparator 自动)
 * - 选中 → 写入 AppPrefs.KEY_CONFLICT_DEFAULT_TOP, 持久化
 * - 行名过长 → 省略号
 */
@Composable
private fun DefaultTopPickerSection(
    cluster: ConflictCluster,
    onDefaultTopChanged: ((clusterKey: String, layerRepId: Long?) -> Unit)?
) {
    val context = LocalContext.current
    val colors = SleepyTheme.colors

    // 簇键:与 ConflictClusterCard / topOverrides 同公式
    val anchor = cluster.courses.first()
    val clusterKey = "${cluster.day}:${anchor.startNode}:${anchor.step}"

    // 图层列表(链式分组后的层)
    val layers = remember(cluster) { ConflictLayoutEngine.chainGroups(cluster.courses) }

    // 监听 prefs 变化:用户在其他入口改了也要同步刷新选中态
    val defaultTopMap by AppPrefs.conflictDefaultTopFlow(context).collectAsState(initial = AppPrefs.getConflictDefaultTop(context))
    val savedRepId: Long? = defaultTopMap[clusterKey]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // 标题行
        Text(
            text = stringResource(R.string.conflict_default_top_title),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )

        for (layer in layers) {
            val layerRepId = layer.first().id
            // 行文本 = 该图层全部课程名("工科数学分析、大学物理")
            val label = layer.joinToString("、") { it.courseName.ifBlank { "—" } }
            // 当前是否已存:已存 = 该 repId 即上次所选;未存 = 系统默认 = 无勾选
            val selected = savedRepId == layerRepId

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected,
                        onClick = {
                            val rep = if (selected) null else layerRepId
                            // v7.10.5: 回调优先 — 同帧驱动网格换层(会话级 override);
                            // 无回调宿主(小组件等)时退回纯持久化路径
                            if (onDefaultTopChanged != null) {
                                onDefaultTopChanged(clusterKey, rep)
                            } else {
                                AppPrefs.putConflictDefaultTop(context, clusterKey, rep)
                            }
                        },
                        role = Role.RadioButton
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RadioButton(
                    selected = selected,
                    onClick = null, // 整行 selectable 处理
                    colors = RadioButtonDefaults.colors(
                        selectedColor = colors.primary,
                        unselectedColor = colors.onSurfaceVariant
                    )
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SheetHeader(title: String) {
    val colors = SleepyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer)
            .padding(start = 20.dp, top = 16.dp, bottom = 12.dp, end = 20.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(),
            color = colors.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TimeChip(text: String) {
    val colors = SleepyTheme.colors
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = colors.onSecondaryContainer,
        modifier = Modifier
            .clip(SleepyTheme.shapes.medium)
            .background(colors.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun DetailRow(key: String, value: String) {
    val colors = SleepyTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.width(54.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

