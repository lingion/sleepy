package com.lingion.sleepy.ui.screen.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.SleepyTheme

/**
 * "Schedule" section — pick which schedule this widget displays.
 *
 * Selected state follows the project rule (memory `ui-blocks-no-border-rule`):
 * a `primaryContainer` color block + a Check icon. No outlined radio circles,
 * no border stroke. Empty tables are filtered out by [WidgetEditScope] so
 * we never offer to bind to a table that would render "请先创建课表".
 */
object WidgetEditScheduleSection : WidgetEditSection {
    override val titleRes: Int = R.string.widget_edit_section_schedule

    @Composable
    override fun Content(scope: WidgetEditScope) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = SleepyTheme.colors.onSurface
            )
            Spacer(modifier = Modifier.padding(top = 8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
                color = SleepyTheme.colors.surfaceContainer,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    DefaultRow(
                        selected = scope.currentBinding == null,
                        onClick = { scope.onSelectTable(null) }
                    )
                    scope.availableTables.forEach { table ->
                        TableRow(
                            tableName = table.name,
                            selected = scope.currentBinding == table.id,
                            onClick = { scope.onSelectTable(table.id) }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun DefaultRow(selected: Boolean, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(
                    if (selected) SleepyTheme.colors.primaryContainer
                    else SleepyTheme.colors.surfaceContainer
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.widget_edit_default_label),
                style = MaterialTheme.typography.bodyLarge,
                color = SleepyTheme.colors.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = stringResource(R.string.selected),
                    tint = SleepyTheme.colors.primary
                )
            }
        }
    }

    @Composable
    private fun TableRow(tableName: String, selected: Boolean, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(
                    if (selected) SleepyTheme.colors.primaryContainer
                    else SleepyTheme.colors.surfaceContainer
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tableName,
                style = MaterialTheme.typography.bodyLarge,
                color = SleepyTheme.colors.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = stringResource(R.string.selected),
                    tint = SleepyTheme.colors.primary
                )
            }
        }
    }
}
