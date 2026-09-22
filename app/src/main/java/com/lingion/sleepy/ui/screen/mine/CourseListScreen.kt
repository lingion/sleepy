package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Class
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.SleepyTheme

/**
 * 2026-09-21 用户令: 我的页「课程数」卡 → 新页, 列出当前课表所有课程(按课程名聚合)。
 * 与管理页不同: 这里以「课程组」为单位, 一个名字出现多次(同一课程多次上课安排)聚合为
 * 一张卡显示, 副标题注明「X 个上课安排」; 字段: 课程名 / 老师 / 教室 / 全部周次段。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseListScreen(
    onBack: () -> Unit,
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme

    // 课程卡先按课程名和老师归组;同一张卡内部再按上课地点分组,
    // 避免不同地点混在同一行,也保留同名不同老师的独立课程卡。
    val grouped: List<CourseGroup> = remember(state.courses) {
        state.courses
            .groupBy { it.courseName.ifBlank { it.groupId } to it.teacher.trim() }
            .map { (key, rows) ->
                CourseGroup(
                    name = rows.first().courseName.ifBlank { key.first },
                    teacher = rows.first().teacher,
                    locations = rows.groupBy { it.room.trim() }
                        .map { (room, locationRows) -> CourseLocation(room, locationRows) }
                        .sortedBy { it.room }
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.course_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
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
        if (grouped.isEmpty()) {
            // 空态: 当前课表无任何课程
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.course_list_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.course_list_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                items(grouped) { group -> CourseGroupCard(group) }
            }
        }
    }
}

@Composable
private fun CourseGroupCard(group: CourseGroup) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SleepyTheme.shapes.large)
            .background(colors.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = group.name.ifBlank { stringResource(R.string.course_detail_title) },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface
        )
        if (group.teacher.isNotBlank()) {
            MetaRow(icon = Icons.Outlined.Person, text = group.teacher)
        }
        group.locations.forEach { location ->
            if (location.room.isNotBlank()) {
                MetaRow(icon = Icons.Outlined.LocationOn, text = location.room)
            }
            Text(
                text = stringResource(R.string.course_list_arrangements, location.rows.size),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
            // 每个地点单独列出该地点的全部上课安排。
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                location.rows.forEach { row -> ArrangementRow(row) }
            }
        }
    }
}

@Composable
private fun MetaRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurface
        )
    }
}

@Composable
private fun ArrangementRow(row: CourseEntity) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val dayLabel = stringResource(
        when (row.day) {
            1 -> R.string.day_short_1
            2 -> R.string.day_short_2
            3 -> R.string.day_short_3
            4 -> R.string.day_short_4
            5 -> R.string.day_short_5
            6 -> R.string.day_short_6
            7 -> R.string.day_short_7
            else -> R.string.day_short_1
        }
    )
    val nodeLabel = row.shortNodeString(context)
    val weekRangeLabel = stringResource(R.string.course_week_range, nodeLabel, row.startWeek, row.endWeek)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.Class,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = "${dayLabel} · $weekRangeLabel",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
    }
}

private data class CourseGroup(
    val name: String,
    val teacher: String,
    val locations: List<CourseLocation>
)

private data class CourseLocation(
    val room: String,
    val rows: List<CourseEntity>
)