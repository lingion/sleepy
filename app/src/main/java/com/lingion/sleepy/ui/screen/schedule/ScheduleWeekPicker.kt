package com.lingion.sleepy.ui.screen.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.console.ui.dbgTag

/** A window-centered dialog, independent of the week label's toolbar position. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScheduleWeekPicker(
    currentWeek: Int,
    maxWeek: Int,
    onDismiss: () -> Unit,
    onSelectWeek: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.dbgTag("schedule_week_dialog"),
        title = { Text(stringResource(R.string.schedule_jump_week)) },
        text = {
            FlowRow(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (1..maxWeek).forEach { week ->
                    val selected = week == currentWeek
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                            .background(if (selected) colors.primary else colors.surfaceContainerHigh)
                            .selectable(selected = selected, role = Role.RadioButton) { onSelectWeek(week) }
                            .dbgTag("schedule_week#$week", text = week.toString(), role = "button"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            week.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) colors.onPrimary else colors.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
