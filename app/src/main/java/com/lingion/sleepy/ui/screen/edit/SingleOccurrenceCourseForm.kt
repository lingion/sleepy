package com.lingion.sleepy.ui.screen.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import com.lingion.sleepy.ui.component.liquidglass.GlassTopAppBar as TopAppBar
import com.lingion.sleepy.ui.component.liquidglass.GlassIconButton as IconButton
import com.lingion.sleepy.ui.component.liquidglass.GlassButton as Button
import com.lingion.sleepy.ui.component.liquidglass.glassButtonShape
import com.lingion.sleepy.ui.component.liquidglass.GlassTonalButton as FilledTonalButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
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
    groupSourceColorHex: String,
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
                    shape = glassButtonShape(SleepyTheme.Buttons.shape)) {
                    Text(stringResource(if (saving) R.string.occurrence_busy else R.string.save_course))
                }
                FilledTonalButton(onClick = { keyboard?.hide(); onMore() }, enabled = !saving,
                    modifier = Modifier.fillMaxWidth().testTag("single-occurrence-more"),
                    shape = glassButtonShape(SleepyTheme.Buttons.shape)) {
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
                ColorSection(block, groupSourceColorHex, enabled = !saving)
            }
        }
    }
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
