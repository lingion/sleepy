package com.lingion.sleepy.ui.screen.imports

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.lingion.sleepy.R
import com.lingion.sleepy.data.jw.JwSgSourceMetadata

/** Source suggestions are visible as suggestions, without replacing explicit period clocks. */
@Composable
internal fun JwSourceSuggestions(importSourceJson: String) {
    val metadata = remember(importSourceJson) { JwSgSourceMetadata.read(importSourceJson) } ?: return
    val adapter = metadata.optJSONObject("adapter")
    val name = adapter?.optString("name").orEmpty().ifBlank { adapter?.optString("adapterId").orEmpty() }
    if (name.isNotBlank()) Text(stringResource(R.string.jw_source_entry, name), style = MaterialTheme.typography.bodySmall)
    val config = metadata.getJSONObject("config")
    if (listOf("defaultClassDuration", "defaultBreakDuration", "firstDayOfWeek").any(config::has)) {
        Text(stringResource(R.string.jw_source_suggestions), style = MaterialTheme.typography.bodySmall)
        if (config.has("defaultClassDuration")) Text(
            stringResource(R.string.jw_source_class_minutes, config.getLong("defaultClassDuration").toString()),
            style = MaterialTheme.typography.bodySmall)
        if (config.has("defaultBreakDuration")) Text(
            stringResource(R.string.jw_source_break_minutes, config.getLong("defaultBreakDuration").toString()),
            style = MaterialTheme.typography.bodySmall)
        if (config.has("firstDayOfWeek")) Text(
            stringResource(R.string.jw_source_first_weekday, stringArrayResource(R.array.day_names)[config.getInt("firstDayOfWeek") - 1]),
            style = MaterialTheme.typography.bodySmall)
    }
}
