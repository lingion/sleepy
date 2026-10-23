package com.lingion.sleepy.ui.screen.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.CourseColorMode
import com.lingion.sleepy.ui.component.ColorPickerDialog
import com.lingion.sleepy.ui.theme.SleepyTheme

/** A compact entry form sharing the full editor's draft and save/confirmation pipeline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SingleOccurrenceCourseForm(
    courseName: String,
    onCourseNameChange: (String) -> Unit,
    alias: String,
    onAliasChange: (String) -> Unit,
    block: MeetingBlockDraft,
    scheduleSummary: String,
    validationMessages: List<String>,
    saving: Boolean,
    canSave: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onMore: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    var showColorPicker by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.imePadding().testTag("single-occurrence-form"),
        containerColor = colors.background,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.occurrence_add_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !saving) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground
                ))
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { keyboard?.hide(); onSave() }, enabled = canSave && !saving,
                    modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.ctaHeight).testTag("single-occurrence-save"),
                    shape = SleepyTheme.Buttons.shape) {
                    Text(stringResource(if (saving) R.string.occurrence_busy else R.string.save_course))
                }
                FilledTonalButton(onClick = { keyboard?.hide(); onMore() }, enabled = !saving,
                    modifier = Modifier.fillMaxWidth().testTag("single-occurrence-more"),
                    shape = SleepyTheme.Buttons.shape) {
                    Text(stringResource(R.string.occurrence_add_more))
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(SleepyTheme.shapes.extraLarge)
                    .background(colors.surfaceContainer)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(scheduleSummary.ifBlank { " " }, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                if (validationMessages.isNotEmpty()) {
                    Text(validationMessages.joinToString("\n"), color = colors.error)
                }
                SingleOccurrenceField(courseName, onCourseNameChange, stringResource(R.string.course_name_required),
                    "single-occurrence-name", enabled = !saving)
                SingleOccurrenceField(alias, onAliasChange, stringResource(R.string.course_alias),
                    "single-occurrence-alias", enabled = !saving)
                SingleOccurrenceField(block.teacherState, { block.teacherState = it }, stringResource(R.string.course_teacher),
                    "single-occurrence-teacher", enabled = !saving)
                SingleOccurrenceField(block.roomState, { block.roomState = it }, stringResource(R.string.course_room),
                    "single-occurrence-room", enabled = !saving)
                SingleOccurrenceField(block.noteState, { block.noteState = it }, stringResource(R.string.course_note),
                    "single-occurrence-note", enabled = !saving, multiline = true)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.course_color), modifier = Modifier.weight(1f))
                    FilterChip(selected = block.colorModeState != CourseColorMode.CUSTOM, enabled = !saving,
                        onClick = { block.colorModeState = CourseColorMode.GROUP; block.colorState = "" },
                        label = { Text(stringResource(R.string.color_auto)) })
                    FilledTonalButton(onClick = { showColorPicker = true }, enabled = !saving,
                        modifier = Modifier.testTag("single-occurrence-color")) {
                        if (block.colorModeState == CourseColorMode.CUSTOM) {
                            val swatch = runCatching { Color(block.colorState.toColorInt()) }.getOrDefault(colors.primary)
                            Box(Modifier.size(18.dp).background(swatch, CircleShape))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.color_custom))
                    }
                }
            }
        }
    }
    if (showColorPicker) ColorPickerDialog(
        initialHex = block.colorState.ifBlank { "#FF6750A4" },
        onConfirm = { hex ->
            block.colorState = hex
            block.colorModeState = CourseColorMode.CUSTOM
            showColorPicker = false
        },
        onDismiss = { showColorPicker = false }
    )
}

@Composable
private fun SingleOccurrenceField(
    value: String, onValueChange: (String) -> Unit, label: String, tag: String,
    enabled: Boolean, multiline: Boolean = false,
) {
    TextField(value, onValueChange, label = { Text(label) }, enabled = enabled,
        singleLine = !multiline, minLines = if (multiline) 2 else 1,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        shape = SleepyTheme.fieldShape, colors = SleepyTheme.fieldColors())
}
