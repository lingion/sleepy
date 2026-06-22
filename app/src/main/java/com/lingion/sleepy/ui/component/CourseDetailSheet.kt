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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.theme.SleepyTextStyle
import com.lingion.sleepy.ui.theme.SleepyTheme

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
 * └────────────────────────────┘
 */
@Composable
fun CourseDetailSheet(
    course: CourseEntity?,
    timeString: String? = null,
    onDismiss: () -> Unit,
    onEdit: ((CourseEntity) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (course != null) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = SleepyTheme.colors.surface,
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

                    if (onEdit != null) {
                        Button(
                            onClick = { onEdit(course) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
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
            .clip(RoundedCornerShape(12.dp))
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

